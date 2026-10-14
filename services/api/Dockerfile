# syntax=docker/dockerfile:1.27
#
# Packages the boot jar Gradle has already built; nothing compiles in here.
# Build context: services/api/build/libs, after `./gradlew :services:api:bootJar`.

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61 AS extract

WORKDIR /x
COPY *-boot.jar app.jar
# The extracted form: app.jar names its dependencies under lib/ on its manifest
# Class-Path, so `java -jar /app/app.jar` still starts it.
RUN java -Djarmode=tools -jar app.jar extract --layers --destination layers

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61 AS runtime

RUN apk add --no-cache libwebp-tools

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

# Least to most often changed, so an ordinary release moves only the last layer.
COPY --from=extract --chown=app:app /x/layers/dependencies/ ./
COPY --from=extract --chown=app:app /x/layers/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /x/layers/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /x/layers/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
USER app

ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
