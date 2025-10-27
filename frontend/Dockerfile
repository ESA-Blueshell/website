FROM node:24-bookworm-slim AS deps
WORKDIR /app

# Use Corepack/Yarn exactly as defined in package.json ("packageManager")
RUN corepack enable && corepack install

# Copy only what Yarn needs first for better layer caching
COPY package.json yarn.lock .yarnrc.yml ./
COPY .yarn/ .yarn/

# Install dependencies strictly against lockfile
RUN yarn install --immutable

# Build stage
FROM deps AS build
# Copy the rest of the project
COPY . .

# Create production build
RUN yarn build

FROM nginx:stable-alpine AS runtime
# Keep nginx config you already have (expects root /app)
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Serve the built app
COPY --from=build /app/dist /app

EXPOSE 8080

# Optional: simple healthcheck
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8080/ >/dev/null 2>&1 || exit 1
