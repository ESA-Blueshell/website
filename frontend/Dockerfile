# syntax=docker/dockerfile:1.7

FROM node:22-bookworm-slim AS deps
WORKDIR /app

COPY package.json yarn.lock .yarnrc.yml ./
COPY .yarn/ .yarn/

RUN test -f .yarn/releases/yarn-4.10.3.cjs

RUN node .yarn/releases/yarn-4.10.3.cjs --version \
 && node .yarn/releases/yarn-4.10.3.cjs install --immutable

FROM deps AS build
WORKDIR /app
COPY . .
ENV NODE_ENV=production
RUN node .yarn/releases/yarn-4.10.3.cjs build

FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist/ /usr/share/nginx/html/

EXPOSE 3000

HEALTHCHECK CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:3000/healthz || exit 1
