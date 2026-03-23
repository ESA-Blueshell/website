#!/usr/bin/env bash
set -euo pipefail

# test-cloud-config.sh — validates the rendered cloud-init config in two phases:
#
#   Phase 1 (always): re-hash each plaintext password with the salt embedded in
#   the rendered cloud-config and verify it matches — confirms that render.sh
#   produced the correct SHA-512 crypt hashes for all three accounts.
#
#   Phase 2 (skipped with --hash-only): boots a temporary Debian 13 VM via QEMU
#   using the rendered cloud-config as user-data, waits for cloud-init to finish
#   (provision.sh moves SSH to port 2222), then tests:
#     - admin  : SSH key login  +  password accepted by sudo
#     - website: SSH key login  +  group membership
#     - all    : SSH password auth is disabled (ssh_pwauth: false enforced)
#
# Prerequisites — phase 2:
#   macOS : brew install qemu cdrtools
#   Linux : sudo apt install qemu-system-x86 qemu-utils cloud-image-utils
#
# Usage:
#   cd image
#   ./test-cloud-config.sh              # phase 1 + phase 2 (full VM test)
#   ./test-cloud-config.sh --hash-only  # phase 1 only (fast, no QEMU needed)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD_CONFIG="${SCRIPT_DIR}/cloud-init/cloud-config.yaml"
ENV_FILE="${SCRIPT_DIR}/.env"
CACHE_DIR="${SCRIPT_DIR}/.test-cache"
LOG_DIR="${SCRIPT_DIR}/vm-logs"   # persists after cleanup — uploaded as CI artifact
SSH_ADMIN_KEY="${HOME}/.ssh/blueshell-admin"
SSH_WEBSITE_KEY="${HOME}/.ssh/blueshell-website"
HOST_SSH_PORT=55022   # host port forwarded to guest port 2222
BOOT_TIMEOUT=900      # seconds to wait for SSH to appear (15 min)

HASH_ONLY=false
for arg in "$@"; do [[ "${arg}" == "--hash-only" ]] && HASH_ONLY=true; done

# ── Output helpers ────────────────────────────────────────────────────────────
GREEN='\033[0;32m' RED='\033[0;31m' YELLOW='\033[1;33m' NC='\033[0m'
pass() { printf "${GREEN}✓${NC} %s\n" "$*"; }
fail() { printf "${RED}✗${NC} %s\n" "$*"; FAILED=$(( FAILED + 1 )); }
info() { printf "${YELLOW}→${NC} %s\n" "$*"; }
FAILED=0

# ── Load credentials ──────────────────────────────────────────────────────────
# shellcheck source=/dev/null
[[ -f "${ENV_FILE}" ]] && { set -a; source "${ENV_FILE}"; set +a; }
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
ROOT_PASSWORD="${ROOT_PASSWORD:-}"
WEBSITE_PASSWORD="${WEBSITE_PASSWORD:-}"

if [[ -z "${ADMIN_PASSWORD}" || -z "${ROOT_PASSWORD}" || -z "${WEBSITE_PASSWORD}" ]]; then
  echo "Error: ADMIN_PASSWORD, ROOT_PASSWORD, WEBSITE_PASSWORD must be set." >&2
  echo "Set them in vps/.env or export them to the environment." >&2
  exit 1
fi

[[ -f "${CLOUD_CONFIG}" ]] || {
  echo "Error: ${CLOUD_CONFIG} not found." >&2
  echo "Run ./cloud-init/render.sh first." >&2
  exit 1
}

# ── Phase 1: verify password hashes ──────────────────────────────────────────
echo "==> Phase 1: verifying password hashes in ${CLOUD_CONFIG}..."
echo ""

verify_hash() {
  local name="$1" password="$2"
  local stored_hash salt rehashed

  # Extract hash from chpasswd list format: "    <user>:$6$salt$hash"
  stored_hash=$(grep -E "^[[:space:]]+${name}:\\$" "${CLOUD_CONFIG}" | sed "s/^[[:space:]]*${name}://")

  if [[ -z "${stored_hash}" ]]; then
    fail "${name}: hash not found in cloud-config"
    return
  fi

  if [[ "${stored_hash:0:3}" != '$6$' ]]; then
    fail "${name}: expected SHA-512 hash (\$6\$...) but found: '${stored_hash:0:20}...'"
    return
  fi

  # Extract the salt (field 3 of $6$<salt>$<hash>)
  salt=$(printf '%s' "${stored_hash}" | cut -d'$' -f3)

  if rehashed=$(openssl passwd -6 -salt "${salt}" "${password}" 2>/dev/null) \
     && [[ "${stored_hash}" == "${rehashed}" ]]; then
    pass "${name}: SHA-512 hash matches password"
  else
    fail "${name}: hash mismatch — the stored hash does not match the plaintext password"
  fi
}

verify_hash "admin"   "${ADMIN_PASSWORD}"
verify_hash "root"    "${ROOT_PASSWORD}"
verify_hash "website" "${WEBSITE_PASSWORD}"

echo ""

if ${HASH_ONLY}; then
  if [[ "${FAILED}" -eq 0 ]]; then
    echo -e "${GREEN}All hash checks passed.${NC}"
  else
    echo -e "${RED}${FAILED} hash check(s) failed.${NC}"
    exit 1
  fi
  exit 0
fi

# ── Phase 2: boot VM and test SSH on port 2222 ────────────────────────────────
echo "==> Phase 2: full VM test (~10–15 min, cloud-init must run to completion)..."
echo ""

# Prerequisites
[[ -f "${SSH_ADMIN_KEY}" ]] || {
  echo "Error: ${SSH_ADMIN_KEY} not found. Run ./cloud-init/render.sh first." >&2; exit 1
}
[[ -f "${SSH_WEBSITE_KEY}" ]] || {
  echo "Error: ${SSH_WEBSITE_KEY} not found. Run ./cloud-init/render.sh first." >&2; exit 1
}
for cmd in qemu-system-x86_64 qemu-img; do
  command -v "${cmd}" >/dev/null 2>&1 || {
    echo "Error: '${cmd}' not found." >&2
    echo "  macOS: brew install qemu  |  Linux: apt install qemu-system-x86 qemu-utils" >&2
    exit 1
  }
done
SEED_TOOL=""
for t in cloud-localds genisoimage mkisofs; do
  command -v "${t}" >/dev/null 2>&1 && { SEED_TOOL="${t}"; break; }
done
[[ -n "${SEED_TOOL}" ]] || {
  echo "Error: need cloud-localds, genisoimage, or mkisofs to create seed ISO." >&2
  echo "  Linux: apt install cloud-image-utils  |  macOS: brew install cdrtools" >&2
  exit 1
}

# Working dir under cache so the Debian image survives between runs
WORK_DIR="${CACHE_DIR}/run-$$"
mkdir -p "${CACHE_DIR}" "${WORK_DIR}"

# Collect cloud-init and system logs from the VM into LOG_DIR (best-effort).
# Called both at end of tests and in cleanup trap so logs survive failures.
collect_vm_logs() {
  mkdir -p "${LOG_DIR}"

  # Copy QEMU console log (in WORK_DIR — must happen before it's deleted)
  [[ -f "${WORK_DIR}/console.log" ]] && cp "${WORK_DIR}/console.log" "${LOG_DIR}/console.log" || true

  # Pull logs from the VM via SSH; silently skip files that don't exist yet
  local vm_logs=(
    "/var/log/cloud-init.log"          # detailed cloud-init execution trace
    "/var/log/cloud-init-output.log"   # stdout/stderr of every runcmd command
    "/run/cloud-init/result.json"      # final pass/fail status
  )
  for remote_path in "${vm_logs[@]}"; do
    local filename; filename="$(basename "${remote_path}")"
    ssh "${SSH_OPTS[@]}" -p "${HOST_SSH_PORT}" -i "${SSH_ADMIN_KEY}" \
      admin@127.0.0.1 \
      "printf '%s\n' '${ADMIN_PASSWORD}' | sudo -S cat '${remote_path}' 2>/dev/null" \
      > "${LOG_DIR}/${filename}" 2>/dev/null || true
    # Remove empty files so the artifact listing isn't cluttered
    [[ -s "${LOG_DIR}/${filename}" ]] || rm -f "${LOG_DIR}/${filename}"
  done
}

_collected=false
cleanup() {
  if [[ "${_collected}" == false ]] && [[ -f "${WORK_DIR}/qemu.pid" ]]; then
    collect_vm_logs || true
    _collected=true
  fi
  if [[ -f "${WORK_DIR}/qemu.pid" ]]; then
    local pid; pid="$(cat "${WORK_DIR}/qemu.pid")"
    kill "${pid}" 2>/dev/null || true
  fi
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

# Download Debian 13 generic cloud image (cached across runs)
DEBIAN_IMAGE="${CACHE_DIR}/debian-13-generic-amd64.qcow2"
if [[ ! -f "${DEBIAN_IMAGE}" ]]; then
  info "Downloading Debian 13 cloud image (~300 MB)..."
  curl -L --progress-bar \
    -o "${DEBIAN_IMAGE}.tmp" \
    "https://cloud.debian.org/images/cloud/trixie/latest/debian-13-generic-amd64.qcow2"
  mv "${DEBIAN_IMAGE}.tmp" "${DEBIAN_IMAGE}"
  info "Saved to ${DEBIAN_IMAGE}"
fi

# Create a thin overlay — leaves the cached base image untouched
info "Creating VM disk overlay..."
qemu-img create -q -f qcow2 -F qcow2 -b "${DEBIAN_IMAGE}" "${WORK_DIR}/disk.qcow2" 20G

# Create NoCloud seed ISO (cloud-init reads user-data + meta-data from it)
info "Creating cloud-init seed ISO..."
printf 'instance-id: test-%s\nlocal-hostname: cloud-config-test\n' "$$" \
  > "${WORK_DIR}/meta-data"
if [[ "${SEED_TOOL}" == "cloud-localds" ]]; then
  cloud-localds "${WORK_DIR}/seed.iso" "${CLOUD_CONFIG}" "${WORK_DIR}/meta-data"
else
  cp "${CLOUD_CONFIG}" "${WORK_DIR}/user-data"
  "${SEED_TOOL}" -output "${WORK_DIR}/seed.iso" \
    -volid cidata -joliet -rock \
    "${WORK_DIR}/user-data" "${WORK_DIR}/meta-data"
fi

# Select hardware accelerator
case "$(uname -s)" in
  Darwin) QEMU_ACCEL=(-accel hvf) ;;
  Linux)  QEMU_ACCEL=(-enable-kvm) ;;
  *)      QEMU_ACCEL=() ;;
esac

# Boot the VM in the background; console output goes to a log file
info "Launching QEMU VM (host:${HOST_SSH_PORT} → guest:2222)..."
info "Console log: ${WORK_DIR}/console.log"
qemu-system-x86_64 \
  "${QEMU_ACCEL[@]}" \
  -m 2048 \
  -cpu host \
  -smp 2 \
  -nographic \
  -drive "file=${WORK_DIR}/disk.qcow2,format=qcow2,if=virtio" \
  -drive "file=${WORK_DIR}/seed.iso,format=raw,media=cdrom" \
  -device virtio-net-pci,netdev=net0 \
  -netdev "user,id=net0,hostfwd=tcp::${HOST_SSH_PORT}-:2222" \
  > "${WORK_DIR}/console.log" 2>&1 &
echo $! > "${WORK_DIR}/qemu.pid"

# Poll for SSH on port 2222 (appears after provision.sh hardens and restarts SSH)
info "Waiting for SSH on port ${HOST_SSH_PORT} (up to ${BOOT_TIMEOUT}s)..."
info "Port 2222 only opens after provision.sh has finished — this takes a while."
echo ""
SSH_OPTS=(-o StrictHostKeyChecking=no -o ConnectTimeout=5 -o BatchMode=yes -o LogLevel=ERROR)
deadline=$(( $(date +%s) + BOOT_TIMEOUT ))
while (( $(date +%s) < deadline )); do
  if ssh "${SSH_OPTS[@]}" -p "${HOST_SSH_PORT}" -i "${SSH_ADMIN_KEY}" \
       admin@127.0.0.1 true 2>/dev/null; then
    break
  fi
  sleep 15
done
if (( $(date +%s) >= deadline )); then
  fail "Timed out waiting for SSH on port ${HOST_SSH_PORT}"
  echo "Last 50 lines of console log:"
  tail -50 "${WORK_DIR}/console.log" || true
  exit 1
fi
pass "SSH is up on port ${HOST_SSH_PORT}"
echo ""

# Helper: run a command on the VM
vm_ssh() {
  local user="$1" key="$2"; shift 2
  ssh "${SSH_OPTS[@]}" -p "${HOST_SSH_PORT}" -i "${key}" "${user}@127.0.0.1" "$@" 2>/dev/null
}

# Wait for cloud-init to finish all runcmd steps before running assertions.
# SSH appears after provision.sh completes (first runcmd), but Docker stack
# deployment and other steps may still be running.
info "Waiting for cloud-init to finish all runcmd steps..."
if vm_ssh admin "${SSH_ADMIN_KEY}" \
     "printf '%s\n' '${ADMIN_PASSWORD}' | sudo -S cloud-init status --wait 2>/dev/null"; then
  pass "cloud-init: completed successfully"
else
  fail "cloud-init: exited with error — check vm-logs/cloud-init-output.log"
fi
echo ""

echo "==> Testing SSH key login..."
echo ""

# admin: SSH key login
if result=$(vm_ssh admin "${SSH_ADMIN_KEY}" whoami) && [[ "${result}" == "admin" ]]; then
  pass "admin: SSH key login works"
else
  fail "admin: SSH key login failed"
fi

# website: SSH key login
if result=$(vm_ssh website "${SSH_WEBSITE_KEY}" whoami) && [[ "${result}" == "website" ]]; then
  pass "website: SSH key login works"
else
  fail "website: SSH key login failed"
fi

# website: group membership
if vm_ssh website "${SSH_WEBSITE_KEY}" 'id -nG | grep -qw website'; then
  pass "website: member of 'website' group"
else
  fail "website: not in 'website' group"
fi

# ── Password login tests ──────────────────────────────────────────────────────
# Open a temporary window where password auth + root login are enabled on the
# VM's sshd, use sshpass to actually log in with each plaintext password, then
# restore the hardened config.
#
# The test drop-in is named 00-test-pwauth.conf so it is processed before
# 10-keys-only.conf; sshd uses the first occurrence of each directive, so the
# test values win for the duration of the window.
echo ""
echo "==> Testing password login (temporary password-auth window)..."
echo ""

if ! command -v sshpass >/dev/null 2>&1; then
  info "Installing sshpass on test runner..."
  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get install -y -q sshpass >/dev/null 2>&1
  elif command -v brew >/dev/null 2>&1; then
    brew install hudochenkov/sshpass/sshpass >/dev/null 2>&1
  else
    fail "sshpass not available and could not be installed — skipping password login tests"
  fi
fi

if command -v sshpass >/dev/null 2>&1; then
  # Enable password auth and root login on the VM.
  # 00-test-pwauth.conf is processed before 10-keys-only.conf (lexicographic
  # order; first occurrence wins for each directive), so every directive that
  # 10-keys-only.conf would restrict must be explicitly overridden here:
  #   AuthenticationMethods any         — removes the publickey-only restriction
  #   KbdInteractiveAuthentication yes  — PAM keyboard-interactive (needed by sshpass)
  #   PasswordAuthentication yes        — plain password auth
  #   PermitRootLogin yes               — allow root password login for the test
  vm_ssh admin "${SSH_ADMIN_KEY}" \
    "printf '%s\n' '${ADMIN_PASSWORD}' | sudo -S bash -c \
      'printf \"AuthenticationMethods any\nKbdInteractiveAuthentication yes\nPasswordAuthentication yes\nPermitRootLogin yes\n\" \
         > /etc/ssh/sshd_config.d/00-test-pwauth.conf \
       && systemctl reload ssh'" 2>/dev/null
  sleep 2   # give sshd time to reload

  SPASS_OPTS=(-o StrictHostKeyChecking=no -o ConnectTimeout=5
              -o PasswordAuthentication=yes -o PubkeyAuthentication=no
              -o LogLevel=ERROR)

  # admin password login
  if result=$(sshpass -p "${ADMIN_PASSWORD}" \
                ssh "${SPASS_OPTS[@]}" -p "${HOST_SSH_PORT}" admin@127.0.0.1 whoami 2>/dev/null) \
     && [[ "${result}" == "admin" ]]; then
    pass "admin: SSH login with password works"
  else
    fail "admin: SSH login with password failed"
  fi

  # website password login
  if result=$(sshpass -p "${WEBSITE_PASSWORD}" \
                ssh "${SPASS_OPTS[@]}" -p "${HOST_SSH_PORT}" website@127.0.0.1 whoami 2>/dev/null) \
     && [[ "${result}" == "website" ]]; then
    pass "website: SSH login with password works"
  else
    fail "website: SSH login with password failed"
  fi

  # root password login (requires PermitRootLogin yes set above)
  if result=$(sshpass -p "${ROOT_PASSWORD}" \
                ssh "${SPASS_OPTS[@]}" -p "${HOST_SSH_PORT}" root@127.0.0.1 whoami 2>/dev/null) \
     && [[ "${result}" == "root" ]]; then
    pass "root: SSH login with password works"
  else
    fail "root: SSH login with password failed"
  fi

  # Restore hardened SSH config
  vm_ssh admin "${SSH_ADMIN_KEY}" \
    "printf '%s\n' '${ADMIN_PASSWORD}' | sudo -S bash -c \
      'rm /etc/ssh/sshd_config.d/00-test-pwauth.conf && systemctl reload ssh'" 2>/dev/null
  sleep 1
fi

# ── Security checks ───────────────────────────────────────────────────────────
echo ""
echo "==> Security checks..."
echo ""

# sshd_config: password auth disabled
if vm_ssh admin "${SSH_ADMIN_KEY}" \
     'grep -Eriq "^PasswordAuthentication[[:space:]]+no" /etc/ssh/sshd_config /etc/ssh/sshd_config.d/ 2>/dev/null'; then
  pass "sshd_config: PasswordAuthentication no"
else
  fail "sshd_config: PasswordAuthentication is not explicitly set to 'no'"
fi

# sshd_config: port directive
if vm_ssh admin "${SSH_ADMIN_KEY}" \
     'grep -Eriq "^Port[[:space:]]+2222" /etc/ssh/sshd_config /etc/ssh/sshd_config.d/ 2>/dev/null'; then
  pass "sshd_config: Port 2222"
else
  fail "sshd_config: Port is not set to 2222"
fi

# Port 2222 open: already proven by the successful SSH connection above, but
# also verify sshd is actually listening on 2222 (not 22) inside the VM.
if vm_ssh admin "${SSH_ADMIN_KEY}" \
     'ss -tlnp | grep -q ":2222"'; then
  pass "port 2222: sshd is listening"
else
  fail "port 2222: sshd is NOT listening on 2222"
fi

# Port 22 closed: sshd must not be listening on 22 inside the VM.
# Anchor to :22 followed by a space or end-of-field to avoid matching :2222.
if vm_ssh admin "${SSH_ADMIN_KEY}" \
     '! ss -tlnp | grep -Eq ":22(\s|$)"'; then
  pass "port 22: sshd is NOT listening (moved to 2222)"
else
  fail "port 22: sshd is still listening on port 22"
fi

# UFW: active
if vm_ssh admin "${SSH_ADMIN_KEY}" \
     "printf '%s\n' '${ADMIN_PASSWORD}' | sudo -S ufw status 2>/dev/null | grep -qi 'status: active'"; then
  pass "UFW: active"
else
  fail "UFW: not active"
fi

# UFW: 2222 allowed (output format varies: '2222/tcp' or '2222' depending on Debian version)
if vm_ssh admin "${SSH_ADMIN_KEY}" \
     "printf '%s\n' '${ADMIN_PASSWORD}' | sudo -S ufw status 2>/dev/null | grep -Eq '^2222'"; then
  pass "UFW: port 2222 allowed"
else
  fail "UFW: port 2222 not found in ufw status"
fi

# UFW: 22 not allowed (grep anchored to ^22 so it does not match 2222)
if ! vm_ssh admin "${SSH_ADMIN_KEY}" \
     "printf '%s\n' '${ADMIN_PASSWORD}' | sudo -S ufw status 2>/dev/null | grep -Eq '^22[^2]'"; then
  pass "UFW: port 22/tcp not allowed"
else
  fail "UFW: port 22/tcp is explicitly allowed — it should not be"
fi

echo ""
echo "==> Collecting VM logs..."
collect_vm_logs || true
_collected=true
info "Logs saved to ${LOG_DIR}/"
echo ""

if [[ "${FAILED}" -eq 0 ]]; then
  echo -e "${GREEN}All tests passed.${NC}"
else
  echo -e "${RED}${FAILED} test(s) failed.${NC}"
  exit 1
fi
