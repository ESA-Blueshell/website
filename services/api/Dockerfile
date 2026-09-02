# syntax=docker/dockerfile:1.7
#
# Build context: repo root (see services/api/docker-compose.yml).

FROM eclipse-temurin:25-jdk-alpine@sha256:09349d79941fd53bb3d487b393ca118d8853c08c09193f416fe6a8718df9e732 AS build

WORKDIR /src

COPY . /src/

RUN chmod +x gradlew

# The build resolves net.blueshell.clients:{brevo,discord}-client from GitHub
# Packages, which needs a token even though the packages are public. Mounted as
# a BuildKit secret rather than passed as a build argument, which would record
# it in the image history. GITHUB_ACTOR is not sensitive and rides along as an
# argument; the registry wants a username beside the token.
ARG GITHUB_ACTOR=""
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=github_token \
    GITHUB_TOKEN="$(cat /run/secrets/github_token 2>/dev/null || true)" \
    GITHUB_ACTOR="${GITHUB_ACTOR}" \
    ./gradlew --no-daemon :services:api:bootJar -x test

FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682 AS runtime

RUN apk add --no-cache libwebp-tools

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

COPY --from=build --chown=app:app /src/services/api/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=20.0 -Dspring.aot.enabled=true"

EXPOSE 8080
USER app

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
