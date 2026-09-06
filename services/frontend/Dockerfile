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
# Nothing tells the bundle where the api is: it always calls
# `${window.location.origin}/api`, and the runtime serves that (see nginx.conf).
ENV NODE_ENV=production
RUN yarn build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:9b87ad3dd9f431c733f19dfb278c7eb3dba9dca381942c79818bb42f1a566a83 AS runtime

# Shipped as a template, not as conf.d/default.conf: the image entrypoint runs
# envsubst over /etc/nginx/templates and writes the result into conf.d. That is
# what makes the api upstream configurable -- it is `api:8080` in the compose
# stack and the `api` Service in the cluster.
#
# NGINX_ENTRYPOINT_LOCAL_RESOLVERS makes the image export the resolvers from
# resolv.conf; nginx.conf's /api location needs one to look the upstream up lazily.
ENV API_UPSTREAM=http://api:8080 \
    NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY nginx.conf /etc/nginx/templates/default.conf.template

COPY --from=build /app/dist/ /usr/share/nginx/html/

EXPOSE 3000

HEALTHCHECK CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:3000/healthz || exit 1
