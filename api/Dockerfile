# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-24-alpine AS build

WORKDIR /app

COPY openapi openapi
COPY api api

WORKDIR /app/api

RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests dependency:go-offline

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package spring-boot:repackage

FROM eclipse-temurin:24-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

COPY --from=build --chown=app:app /app/api/target/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=20.0"

EXPOSE 8080
USER app

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]