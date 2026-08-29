# syntax=docker/dockerfile:1.7
#
# Build context: repo root (see services/api/docker-compose.yml).

FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS build

WORKDIR /src

COPY . /src/

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :services:api:bootJar -x test

FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0 AS runtime

RUN apk add --no-cache libwebp-tools

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

COPY --from=build --chown=app:app /src/services/api/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=20.0 -Dspring.aot.enabled=true"

EXPOSE 8080
USER app

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
