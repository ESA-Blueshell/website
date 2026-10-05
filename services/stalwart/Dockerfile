# syntax=docker/dockerfile:1.27
#
# Dev Stalwart: the server with stalwart-cli beside it, so the entrypoint can
# seed the datastore on the first start. Build context: services/stalwart.

FROM alpine:3.24.2@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6 AS cli

# The version infra/stalwart/Dockerfile bakes into the production apply sidecar.
ARG STALWART_CLI_VERSION=1.0.7

RUN apk add --no-cache curl tar xz \
 && curl -fsSL "https://github.com/stalwartlabs/cli/releases/download/v${STALWART_CLI_VERSION}/stalwart-cli-$(uname -m)-unknown-linux-musl.tar.xz" \
    | tar -xJ -C /tmp \
 && find /tmp -type f -name stalwart-cli -exec mv {} /usr/local/bin/stalwart-cli \;

FROM stalwartlabs/stalwart:v0.16.24

COPY --from=cli /usr/local/bin/stalwart-cli /usr/local/bin/stalwart-cli
COPY config.json /etc/stalwart/config.json
COPY entrypoint.sh settings.ndjson seed.ndjson /opt/stalwart-dev/

ENTRYPOINT ["bash", "/opt/stalwart-dev/entrypoint.sh"]
