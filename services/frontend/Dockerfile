# syntax=docker/dockerfile:1.27

FROM node:26.9.0-bookworm-slim@sha256:582460f614631b59b824ac6020533b9bf339c7fdf3a6d7db31abb6b4065f0212 AS deps
WORKDIR /app

COPY package.json yarn.lock .yarnrc.yml ./
COPY .yarn/ .yarn/
# Node 25+ no longer bundles Corepack (the nodejs TSC dropped it from the
# official distributions), so install it explicitly before enabling.
RUN npm install -g --force corepack@latest \
 && corepack enable \
 && yarn --version \
 && yarn install --immutable

FROM deps AS build
WORKDIR /app
COPY . .
# Nothing tells the bundle where the api is: it always calls
# `${window.location.origin}/api`, and the runtime serves that (see nginx.conf).
ENV NODE_ENV=production
# The release version, which lives in the repo-root release-please manifest outside
# this context. build.yml reads it and passes it in; vite writes it to /version.json.
ARG APP_VERSION=""
RUN yarn build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:442753882674b49ae2c1de83ed67896131c0777f56df5005e356e62bc3f7e7ce AS runtime

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
