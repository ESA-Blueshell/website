# syntax=docker/dockerfile:1.7
#
# Build context: repo root (see services/api/docker-compose.yml).

FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /src

COPY . /src/

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :services:api:bootJar -x test

FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

COPY --from=build --chown=app:app /src/services/api/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=20.0 -Dspring.aot.enabled=true"

EXPOSE 8080
USER app

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
