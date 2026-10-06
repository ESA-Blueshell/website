# syntax=docker/dockerfile:1.27
#
# Build context: services/pinger.

FROM alpine:3.24.2@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6 AS css
ARG TAILWIND_VERSION=4.3.3
ARG TARGETARCH
RUN apk add --no-cache curl libstdc++ libgcc \
 && arch="$([ "$TARGETARCH" = arm64 ] && echo arm64 || echo x64)" \
 && curl -fsSL -o /usr/local/bin/tailwindcss \
    "https://github.com/tailwindlabs/tailwindcss/releases/download/v${TAILWIND_VERSION}/tailwindcss-linux-${arch}-musl" \
 && chmod +x /usr/local/bin/tailwindcss
WORKDIR /src/internal/web
COPY internal/web/app.css ./
COPY internal/web/templates ./templates
RUN tailwindcss --input app.css --output /out/app.css --minify

FROM golang:1.26-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
COPY --from=css /out/app.css internal/web/static/app.css
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/pinger ./cmd/pinger

# Root, because a container gains NET_RAW only through its bounding set and Kubernetes cannot
# grant ambient capabilities to another user. The Deployment drops every other capability.
FROM gcr.io/distroless/static-debian12
COPY --from=build /out/pinger /pinger
EXPOSE 8090
ENTRYPOINT ["/pinger"]
