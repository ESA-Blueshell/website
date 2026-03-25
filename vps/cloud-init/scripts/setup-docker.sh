#!/usr/bin/env bash
# =============================================================================
# setup-docker.sh — configure Docker proxy (if needed) and enable the daemon.
#
# If http_proxy / https_proxy environment variables are set, writes a systemd
# drop-in so the Docker daemon inherits them (needed in proxied environments).
#
# Run by provision.sh on first boot.
# =============================================================================
set -euxo pipefail

# ── Proxy configuration ──────────────────────────────────────────────────────
configure_docker_proxy() {
  local vars=(http_proxy https_proxy HTTP_PROXY HTTPS_PROXY no_proxy NO_PROXY)
  local has_proxy=0
  local var value

  for var in "${vars[@]}"; do
    value="${!var:-}"
    if [[ -n "${value}" ]]; then
      has_proxy=1
      break
    fi
  done

  if [[ "${has_proxy}" -eq 0 ]]; then
    return
  fi

  install -d -m 0755 /etc/systemd/system/docker.service.d
  {
    echo "[Service]"
    for var in "${vars[@]}"; do
      value="${!var:-}"
      if [[ -n "${value}" ]]; then
        printf 'Environment="%s=%s"\n' "${var}" "${value}"
      fi
    done
  } > /etc/systemd/system/docker.service.d/proxy.conf
}

configure_docker_proxy

# ── Enable and start ─────────────────────────────────────────────────────────
systemctl daemon-reload
systemctl enable --now docker.service
