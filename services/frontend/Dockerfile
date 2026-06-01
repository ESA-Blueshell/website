# syntax=docker/dockerfile:1.7

FROM node:26-bookworm-slim AS deps
WORKDIR /app

COPY package.json yarn.lock .yarnrc.yml ./
COPY .yarn/ .yarn/
# Node 26 dropped the bundled corepack; install it explicitly before
# letting it provision the pinned Yarn from package.json#packageManager.
RUN npm install --global corepack@latest \
 && corepack enable \
 && yarn --version \
 && yarn install --immutable

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

FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist/ /usr/share/nginx/html/

EXPOSE 3000

HEALTHCHECK CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:3000/healthz || exit 1
