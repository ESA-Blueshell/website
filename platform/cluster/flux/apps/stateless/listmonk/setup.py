#!/usr/bin/env python3
"""
Idempotent Listmonk first-time setup.

Performs in order:
  1. Admin account setup via the first-time setup wizard (if not yet configured)
  2. API user — creates a type=api user and persists its token for the api service
  3. SMTP    — configures an outbound SMTP server (skipped if LISTMONK_SMTP_HOST unset)
  4. Theme   — injects custom CSS from $THEME_CSS_PATH into app.custom_css
  5. Bounce  — enables bounce processing and optionally configures an IMAP mailbox

All steps are idempotent and safe to re-run.

Authentication note
-------------------
Listmonk v4.1.0 distinguishes two user types:
  - type=user  : web-UI admin (session-cookie auth only, basic auth NOT supported)
  - type=api   : API-only user (basic auth with auto-generated password)

This script uses session-cookie auth for all Listmonk API calls.
It creates a type=api user whose auto-generated password is persisted
either to LISTMONK_API_TOKEN_FILE (local compose) or to the
`listmonk-api-token` Kubernetes Secret (k3s / Flux).

Required environment variables
-------------------------------
LISTMONK_URL               Base URL (default: http://listmonk:9000)
LISTMONK_ADMIN_USERNAME    Admin username (must match LISTMONK_ADMIN_USER in the listmonk container)
LISTMONK_ADMIN_PASSWORD    Admin password (must match LISTMONK_ADMIN_PASSWORD in the listmonk container)
LISTMONK_ADMIN_EMAIL       Admin e-mail for first-time setup wizard (default: admin@listmonk.local)
LISTMONK_ADMIN_API_USER    API username to create (default: api)
LISTMONK_API_TOKEN_FILE    Where to write the API token (default: /secrets/api-token.env)

Optional (SMTP outbound)
LISTMONK_SMTP_HOST              SMTP host — step skipped if empty
LISTMONK_SMTP_PORT              default: 25
LISTMONK_SMTP_AUTH_PROTOCOL     none | plain | login | cram-md5 (default: none)
LISTMONK_SMTP_USERNAME          default: empty
LISTMONK_SMTP_PASSWORD          default: empty
LISTMONK_SMTP_HELLO_HOSTNAME    EHLO hostname (default: empty — Listmonk uses its own)
LISTMONK_SMTP_TLS_TYPE          none | starttls | tls (default: none)
LISTMONK_SMTP_TLS_SKIP_VERIFY   default: false

Optional (bounce mailbox)
LISTMONK_BOUNCE_MAILBOX_ENABLED         true to configure IMAP (default: false)
LISTMONK_BOUNCE_MAILBOX_HOST            IMAP host
LISTMONK_BOUNCE_MAILBOX_PORT            IMAP port (default: 993)
LISTMONK_BOUNCE_MAILBOX_USERNAME        IMAP username
LISTMONK_BOUNCE_MAILBOX_PASSWORD        IMAP password
LISTMONK_BOUNCE_MAILBOX_TLS_ENABLED     default: true
LISTMONK_BOUNCE_MAILBOX_TLS_SKIP_VERIFY default: false
LISTMONK_BOUNCE_MAILBOX_FOLDER          default: INBOX
LISTMONK_BOUNCE_MAILBOX_RETURN_PATH     default: empty
LISTMONK_BOUNCE_MAILBOX_SCAN_INTERVAL   default: 10m
"""
import http.cookiejar
import base64
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
LISTMONK_URL = os.environ.get("LISTMONK_URL", "http://listmonk:9000").rstrip("/")
ADMIN_USER = os.environ.get("LISTMONK_ADMIN_USERNAME", "listmonk")
ADMIN_PASS = os.environ.get("LISTMONK_ADMIN_PASSWORD", "listmonk")
ADMIN_EMAIL = os.environ.get("LISTMONK_ADMIN_EMAIL", "admin@listmonk.local")
API_USER = os.environ.get("LISTMONK_ADMIN_API_USER", "api")
API_TOKEN_FILE = os.environ.get("LISTMONK_API_TOKEN_FILE", "/secrets/api-token.env")
API_TOKEN_SECRET_NAME = os.environ.get("LISTMONK_API_TOKEN_SECRET_NAME", "listmonk-api-token")
API_TOKEN_SECRET_KEY = os.environ.get("LISTMONK_API_TOKEN_SECRET_KEY", "api-token.env")
API_DEPLOYMENT_NAME = os.environ.get("LISTMONK_API_DEPLOYMENT_NAME", "api")
THEME_CSS_PATH = os.environ.get("THEME_CSS_PATH", "/theme.css")
SMTP_HOST = os.environ.get("LISTMONK_SMTP_HOST", "")
K8S_HOST = os.environ.get("KUBERNETES_SERVICE_HOST", "")
K8S_PORT = os.environ.get("KUBERNETES_SERVICE_PORT_HTTPS", "443")
SERVICEACCOUNT_DIR = "/var/run/secrets/kubernetes.io/serviceaccount"
SERVICEACCOUNT_TOKEN_PATH = f"{SERVICEACCOUNT_DIR}/token"
SERVICEACCOUNT_CA_PATH = f"{SERVICEACCOUNT_DIR}/ca.crt"
SERVICEACCOUNT_NAMESPACE_PATH = f"{SERVICEACCOUNT_DIR}/namespace"

# ---------------------------------------------------------------------------
# Session-based auth (type=user admin can only auth via session cookie)
# ---------------------------------------------------------------------------
_cookie_jar = http.cookiejar.CookieJar()
_opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(_cookie_jar))
_logged_in = False


def _ensure_login() -> None:
    global _logged_in
    if _logged_in:
        return
    data = urllib.parse.urlencode({"username": ADMIN_USER, "password": ADMIN_PASS}).encode()
    req = urllib.request.Request(
        f"{LISTMONK_URL}/admin/login",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with _opener.open(req):
            pass
    except urllib.error.HTTPError as exc:
        if exc.code not in (200, 302):
            raise RuntimeError(f"Admin login failed: HTTP {exc.code}")
    _logged_in = True


def _request(method: str, path: str, body=None) -> dict:
    _ensure_login()
    url = f"{LISTMONK_URL}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method=method)
    with _opener.open(req) as resp:
        content = resp.read()
        return json.loads(content) if content else {}


def api_get(path: str) -> dict:
    return _request("GET", path)


def api_post(path: str, body: dict) -> dict:
    return _request("POST", path, body=body)


def api_put(path: str, body: dict) -> dict:
    return _request("PUT", path, body=body)


def api_delete(path: str) -> None:
    _request("DELETE", path)


def _bool_env(name: str, default: bool) -> bool:
    val = os.environ.get(name, "").lower()
    if val in ("true", "1", "yes"):
        return True
    if val in ("false", "0", "no"):
        return False
    return default


def _parse_api_token(token_file_contents: str) -> str | None:
    for line in token_file_contents.splitlines():
        if line.startswith("LISTMONK_ADMIN_API_TOKEN="):
            token = line.removeprefix("LISTMONK_ADMIN_API_TOKEN=").strip().strip('"')
            if token:
                return token
    return None


def _in_cluster() -> bool:
    return bool(K8S_HOST) and os.path.exists(SERVICEACCOUNT_TOKEN_PATH) and os.path.exists(SERVICEACCOUNT_CA_PATH)


def _k8s_namespace() -> str:
    if not os.path.exists(SERVICEACCOUNT_NAMESPACE_PATH):
        return "default"
    with open(SERVICEACCOUNT_NAMESPACE_PATH) as f:
        return f.read().strip() or "default"


def _k8s_request(method: str, path: str, body=None, content_type: str = "application/json") -> dict:
    with open(SERVICEACCOUNT_TOKEN_PATH) as f:
        token = f.read().strip()

    data = json.dumps(body).encode() if body is not None else None
    headers = {"Authorization": f"Bearer {token}"}
    if body is not None:
        headers["Content-Type"] = content_type

    req = urllib.request.Request(
        f"https://{K8S_HOST}:{K8S_PORT}{path}",
        data=data,
        headers=headers,
        method=method,
    )
    context = ssl.create_default_context(cafile=SERVICEACCOUNT_CA_PATH)
    with urllib.request.urlopen(req, context=context) as resp:
        content = resp.read()
        return json.loads(content) if content else {}


def _k8s_secret_path(secret_name: str) -> str:
    namespace = urllib.parse.quote(_k8s_namespace(), safe="")
    name = urllib.parse.quote(secret_name, safe="")
    return f"/api/v1/namespaces/{namespace}/secrets/{name}"


def _read_k8s_api_token_file() -> str | None:
    if not _in_cluster():
        return None

    try:
        secret = _k8s_request("GET", _k8s_secret_path(API_TOKEN_SECRET_NAME))
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise

    encoded = secret.get("data", {}).get(API_TOKEN_SECRET_KEY)
    if not encoded:
        return None
    return base64.b64decode(encoded).decode()


def _write_api_token_file(token_file_contents: str) -> bool:
    directory = os.path.dirname(API_TOKEN_FILE)
    if directory and not os.path.isdir(directory):
        return False

    try:
        with open(API_TOKEN_FILE, "w") as f:
            f.write(token_file_contents)
        print(f"API token written to {API_TOKEN_FILE}.")
        return True
    except OSError as exc:
        print(f"WARNING: Could not write token file '{API_TOKEN_FILE}': {exc}", file=sys.stderr)
        return False


def _sync_k8s_api_token_secret(token_file_contents: str) -> bool | None:
    if not _in_cluster():
        return None

    encoded = base64.b64encode(token_file_contents.encode()).decode()
    patch = {
        "metadata": {
            "labels": {
                "app.kubernetes.io/name": "listmonk-api-token",
                "app.kubernetes.io/managed-by": "listmonk-setup",
            },
        },
        "type": "Opaque",
        "data": {
            API_TOKEN_SECRET_KEY: encoded,
        },
    }

    try:
        existing = _k8s_request("GET", _k8s_secret_path(API_TOKEN_SECRET_NAME))
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            raise
        body = {
            "apiVersion": "v1",
            "kind": "Secret",
            "metadata": {
                "name": API_TOKEN_SECRET_NAME,
                "namespace": _k8s_namespace(),
                "labels": patch["metadata"]["labels"],
            },
            "type": "Opaque",
            "data": patch["data"],
        }
        namespace = urllib.parse.quote(_k8s_namespace(), safe="")
        _k8s_request("POST", f"/api/v1/namespaces/{namespace}/secrets", body)
        print(f"API token synced to Kubernetes Secret '{API_TOKEN_SECRET_NAME}'.")
        return True

    if existing.get("data", {}).get(API_TOKEN_SECRET_KEY) == encoded:
        print(f"Kubernetes Secret '{API_TOKEN_SECRET_NAME}' already contains the current API token.")
        return False

    _k8s_request(
        "PATCH",
        _k8s_secret_path(API_TOKEN_SECRET_NAME),
        patch,
        content_type="application/merge-patch+json",
    )
    print(f"API token synced to Kubernetes Secret '{API_TOKEN_SECRET_NAME}'.")
    return True


def _rollout_restart_api() -> None:
    """Bump the api Deployment's restart annotation so pods reload the token Secret.

    Matches `kubectl rollout restart`: a single patch on one named Deployment
    is a much smaller RBAC surface than list+delete on arbitrary pods, and
    the rolling-update strategy stays in charge of graceful restart order.
    """
    if not _in_cluster():
        return

    namespace = urllib.parse.quote(_k8s_namespace(), safe="")
    name = urllib.parse.quote(API_DEPLOYMENT_NAME, safe="")
    path = f"/apis/apps/v1/namespaces/{namespace}/deployments/{name}"
    patch = {
        "spec": {
            "template": {
                "metadata": {
                    "annotations": {
                        "kubectl.kubernetes.io/restartedAt": datetime.now(timezone.utc).isoformat(),
                    }
                }
            }
        }
    }
    try:
        _k8s_request("PATCH", path, patch, content_type="application/strategic-merge-patch+json")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            print(f"Deployment '{API_DEPLOYMENT_NAME}' not found; skipping rollout.", file=sys.stderr)
            return
        raise
    print(f"Triggered rolling restart of deployment '{API_DEPLOYMENT_NAME}'.")


def _read_persisted_api_token() -> str | None:
    token = _read_token_file()
    if token:
        return token

    token_file_contents = _read_k8s_api_token_file()
    if not token_file_contents:
        return None
    return _parse_api_token(token_file_contents)


# ---------------------------------------------------------------------------
# Step 1: Admin setup (first-time wizard)
# ---------------------------------------------------------------------------

def setup_admin() -> None:
    global _logged_in
    print("Checking Listmonk first-time setup status…")
    try:
        with urllib.request.urlopen(f"{LISTMONK_URL}/admin/login") as resp:
            body = resp.read().decode(errors="replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")

    if 'name="password2"' not in body:
        print("Admin already configured — skipping first-time setup.")
        return

    print(f"Performing first-time admin setup (username: {ADMIN_USER})…")
    data = urllib.parse.urlencode({
        "email": ADMIN_EMAIL,
        "username": ADMIN_USER,
        "password": ADMIN_PASS,
        "password2": ADMIN_PASS,
    }).encode()
    req = urllib.request.Request(
        f"{LISTMONK_URL}/admin/login",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with _opener.open(req) as resp:
            status = resp.status
    except urllib.error.HTTPError as exc:
        status = exc.code

    if status in (200, 302):
        print("Admin setup complete.")
        _logged_in = True  # session cookie captured by _opener
    else:
        print(f"Admin setup failed: HTTP {status}", file=sys.stderr)
        sys.exit(1)


# ---------------------------------------------------------------------------
# Step 2: API user
# ---------------------------------------------------------------------------

def _read_token_file() -> str | None:
    """Return the API token string from the token file, or None."""
    try:
        with open(API_TOKEN_FILE) as f:
            return _parse_api_token(f.read())
    except FileNotFoundError:
        pass
    return None


def ensure_api_user() -> None:
    """
    Create a type=api user for the Spring Boot API service.

    On first run (fresh install): creates the user and persists its token.
    On subsequent runs: skips if the persisted token is present AND the user exists.
    If the user is gone but a persisted token exists (e.g. DB was reset), recreates the user.
    """
    existing_token = _read_persisted_api_token()

    try:
        users = api_get("/api/users").get("data", [])
    except urllib.error.HTTPError as exc:
        print(f"WARNING: Could not list users: HTTP {exc.code} — skipping API user step.", file=sys.stderr)
        return

    api_user = next((u for u in users if u["username"] == API_USER and u["type"] == "api"), None)

    if api_user and existing_token:
        print(f"API user '{API_USER}' exists and token file is valid — skipping.")
        return

    if api_user and not existing_token:
        print(f"API user '{API_USER}' exists but token file is missing — recreating user.")
        try:
            api_delete(f"/api/users/{api_user['id']}")
        except urllib.error.HTTPError as exc:
            print(f"WARNING: Could not delete stale API user: HTTP {exc.code}", file=sys.stderr)

    print(f"Creating API user '{API_USER}'…")
    try:
        # Listmonk auto-generates the password for type=api users and returns it
        # in the creation response — this is the only time the plaintext is available.
        result = api_post("/api/users", {
            "username": API_USER,
            "type": "api",
            "status": "enabled",
            "name": "API User",
            "user_role_id": 1,  # Super Admin role — always id=1 after --install
        })
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"WARNING: Failed to create API user: HTTP {exc.code} — {body}", file=sys.stderr)
        return

    token = result.get("data", {}).get("password")
    if not token:
        print("WARNING: Listmonk did not return a password for the new API user.", file=sys.stderr)
        return

    token_file_contents = f"LISTMONK_ADMIN_API_TOKEN={token}\n"
    wrote_local = _write_api_token_file(token_file_contents)
    secret_changed = _sync_k8s_api_token_secret(token_file_contents)
    if secret_changed:
        _rollout_restart_api()
    if not wrote_local and secret_changed is None:
        print(
            "WARNING: API token was generated but could not be persisted locally and no in-cluster Secret is available.",
            file=sys.stderr,
        )


# ---------------------------------------------------------------------------
# Step 3: SMTP configuration
# ---------------------------------------------------------------------------

def configure_smtp() -> None:
    if not SMTP_HOST:
        print("LISTMONK_SMTP_HOST not set — skipping SMTP configuration.")
        return

    print(f"Configuring Listmonk SMTP ({SMTP_HOST})…")
    try:
        response = api_get("/api/settings")
    except urllib.error.HTTPError as exc:
        print(f"WARNING: Failed to fetch settings for SMTP config: HTTP {exc.code} — {exc.reason}", file=sys.stderr)
        return

    settings = response.get("data", response)
    smtp_list = settings.get("smtp") or []

    if any(s.get("enabled") for s in smtp_list):
        print("SMTP already configured — skipping.")
        return

    smtp_entry = {
        "enabled": True,
        "host": SMTP_HOST,
        "port": int(os.environ.get("LISTMONK_SMTP_PORT", "25")),
        "auth_protocol": os.environ.get("LISTMONK_SMTP_AUTH_PROTOCOL", "none"),
        "username": os.environ.get("LISTMONK_SMTP_USERNAME", ""),
        "password": os.environ.get("LISTMONK_SMTP_PASSWORD", ""),
        "hello_hostname": os.environ.get("LISTMONK_SMTP_HELLO_HOSTNAME", ""),
        "tls_type": os.environ.get("LISTMONK_SMTP_TLS_TYPE", "none"),
        "tls_skip_verify": _bool_env("LISTMONK_SMTP_TLS_SKIP_VERIFY", False),
        "max_conns": int(os.environ.get("LISTMONK_SMTP_MAX_CONNS", "10")),
        "idle_timeout": os.environ.get("LISTMONK_SMTP_IDLE_TIMEOUT", "15s"),
        "wait_timeout": os.environ.get("LISTMONK_SMTP_WAIT_TIMEOUT", "5s"),
        "retries": int(os.environ.get("LISTMONK_SMTP_RETRIES", "2")),
    }

    settings["smtp"] = [smtp_entry]

    try:
        api_put("/api/settings", settings)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"WARNING: Failed to configure SMTP: HTTP {exc.code} — {body}", file=sys.stderr)
        return

    print(f"SMTP configured: {SMTP_HOST}:{smtp_entry['port']}")


# ---------------------------------------------------------------------------
# Step 4: Theme
# ---------------------------------------------------------------------------

def apply_theme() -> None:
    if not os.path.exists(THEME_CSS_PATH):
        print(f"No theme CSS found at {THEME_CSS_PATH} — skipping theme.")
        return

    with open(THEME_CSS_PATH) as f:
        css = f.read()

    print("Fetching Listmonk settings to apply theme…")
    try:
        response = api_get("/api/settings")
    except urllib.error.HTTPError as exc:
        print(f"WARNING: Failed to fetch settings for theme: HTTP {exc.code} — {exc.reason}", file=sys.stderr)
        return

    settings = response.get("data", response)
    # Listmonk v4.1.0 returns flat dotted keys (e.g. "appearance.admin.custom_css")
    settings["appearance.admin.custom_css"] = css
    settings["appearance.public.custom_css"] = css

    try:
        api_put("/api/settings", settings)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"WARNING: Failed to apply theme: HTTP {exc.code} — {body}", file=sys.stderr)
        return

    print("Theme applied.")


# ---------------------------------------------------------------------------
# Step 5: Bounce configuration
# ---------------------------------------------------------------------------

def configure_bounce() -> None:
    print("Configuring Listmonk bounce settings…")
    try:
        response = api_get("/api/settings")
    except urllib.error.HTTPError as exc:
        print(f"WARNING: Failed to fetch settings for bounce config: HTTP {exc.code} — {exc.reason}", file=sys.stderr)
        return

    settings = response.get("data", response)
    changed = False

    if settings.get("bounce.enabled") is not True:
        settings["bounce.enabled"] = True
        changed = True
        print("Enabling bounce processing.")

    if _bool_env("LISTMONK_BOUNCE_MAILBOX_ENABLED", False):
        host = os.environ.get("LISTMONK_BOUNCE_MAILBOX_HOST", "")
        username = os.environ.get("LISTMONK_BOUNCE_MAILBOX_USERNAME", "")
        if not host or not username:
            print("LISTMONK_BOUNCE_MAILBOX_ENABLED=true but HOST/USERNAME not set — skipping.", file=sys.stderr)
        elif settings.get("bounce.mailboxes"):
            print("Bounce mailbox already configured — not overwriting.")
        else:
            settings["bounce.mailboxes"] = [{
                "enabled": True,
                "type": "imap",
                "host": host,
                "port": int(os.environ.get("LISTMONK_BOUNCE_MAILBOX_PORT", "993")),
                "auth_protocol": "userpass",
                "username": username,
                "password": os.environ.get("LISTMONK_BOUNCE_MAILBOX_PASSWORD", ""),
                "tls_enabled": _bool_env("LISTMONK_BOUNCE_MAILBOX_TLS_ENABLED", True),
                "tls_skip_verify": _bool_env("LISTMONK_BOUNCE_MAILBOX_TLS_SKIP_VERIFY", False),
                "folder": os.environ.get("LISTMONK_BOUNCE_MAILBOX_FOLDER", "INBOX"),
                "return_path": os.environ.get("LISTMONK_BOUNCE_MAILBOX_RETURN_PATH", ""),
                "scan_interval": os.environ.get("LISTMONK_BOUNCE_MAILBOX_SCAN_INTERVAL", "10m"),
            }]
            changed = True
            print(f"Configuring IMAP bounce mailbox: {host}")

    if not changed:
        print("Bounce settings already up to date.")
        return

    try:
        api_put("/api/settings", settings)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"WARNING: Failed to update bounce settings: HTTP {exc.code} — {body}", file=sys.stderr)
        return

    print("Bounce settings updated.")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    setup_admin()
    ensure_api_user()
    configure_smtp()
    apply_theme()
    configure_bounce()
    print("Listmonk setup complete.")
