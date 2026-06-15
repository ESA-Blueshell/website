# stalwart-tools: bakes the pinned stalwart-cli plus the reconcile
# script, plan template and account manifest used by the apply sidecar
# on the production Stalwart Deployment. Build context is the repo root
# so the same Dockerfile slots into the standard build matrix.
FROM alpine:3.24.0

ARG STALWART_CLI_VERSION=1.0.7

RUN apk add --no-cache curl ca-certificates jq gettext tar xz \
 && arch="$(uname -m)" \
 && case "$arch" in \
      x86_64)  target=x86_64-unknown-linux-musl ;; \
      aarch64) target=aarch64-unknown-linux-musl ;; \
      *) echo "unsupported arch: $arch" >&2; exit 1 ;; \
    esac \
 && curl -fsSL "https://github.com/stalwartlabs/cli/releases/download/v${STALWART_CLI_VERSION}/stalwart-cli-${target}.tar.xz" -o /tmp/cli.tar.xz \
 && tar -xJf /tmp/cli.tar.xz -C /tmp \
 && find /tmp -type f -name stalwart-cli -exec mv {} /usr/local/bin/stalwart-cli \; \
 && chmod +x /usr/local/bin/stalwart-cli \
 && rm -rf /tmp/*

COPY infra/stalwart/apply.sh /opt/stalwart-tools/
COPY infra/stalwart/plan.ndjson.tmpl infra/stalwart/accounts.json /opt/stalwart-tools/
RUN chmod +x /opt/stalwart-tools/apply.sh

# No default CMD: the k8s sidecar sets `command: ["sh", "/opt/stalwart-tools/apply.sh"]`.
