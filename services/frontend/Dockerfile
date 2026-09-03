# syntax=docker/dockerfile:1.7

FROM node:26.8.1-bookworm-slim@sha256:367679cf9792759492a486e4aa4b421764d71a9546a6dae8aab81a99eb797b3e AS deps
WORKDIR /app

COPY package.json yarn.lock .yarnrc.yml ./
COPY .yarn/ .yarn/
# Node 25+ no longer bundles Corepack (the nodejs TSC dropped it from the
# official distributions), so install it explicitly before enabling.
# `--mount=type=secret` rather than an ARG: the frontend now depends on
# @esa-blueshell/discord-client from GitHub Packages, which needs a token even
# though the package is public, and a token passed as a build argument is
# recorded in the image history for anyone who pulls it. The secret is mounted
# only for this layer and leaves no trace in the image.
#
# The build still works without it for anyone whose Yarn cache already has the
# package, which is why the read tolerates a missing file.
RUN --mount=type=secret,id=node_auth_token \
    npm install -g --force corepack@latest \
 && corepack enable \
 && yarn --version \
 && NODE_AUTH_TOKEN="$(cat /run/secrets/node_auth_token 2>/dev/null || true)" \
    yarn install --immutable

FROM deps AS build
WORKDIR /app
COPY . .
# Vite bakes VITE_* env vars into the bundle at build time. Production
# images leave this empty so the bundle falls back to
# `${window.location.origin}/api` (path-routed via Traefik). The CI
# image build passes VITE_APP_URL=http://localhost:8080 so the system
# tests can drive the SPA against the side-by-side api container.
ARG VITE_APP_URL=""
ENV NODE_ENV=production \
    VITE_APP_URL=${VITE_APP_URL}
RUN yarn build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:93722936b82ec8a1178d48448e619226680d2de3706a1640800e186cd5fa7fd3 AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist/ /usr/share/nginx/html/

EXPOSE 3000

HEALTHCHECK CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:3000/healthz || exit 1
