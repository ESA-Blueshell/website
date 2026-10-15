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

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

# Least to most often changed, so an ordinary release moves only the last layer.
COPY --from=extract --chown=app:app /x/layers/dependencies/ ./
COPY --from=extract --chown=app:app /x/layers/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /x/layers/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /x/layers/application/ ./

# The flags of the training run and of every start, which must match for the AOT cache to load
# (architecture ADR-013). G1 is named because below 1792 MB the JVM picks Serial on its own, and
# the migrate Job is given less.
ENV JDK_JAVA_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75 -XX:+UseG1GC"

# The AOT cache: one start as far as a refreshed context, under the training profile, which
# reaches nothing. MaxRAM stands in for a pod: a heap sized from a large build host turns
# compressed pointers off, and no pod would match. The keys satisfy the hardening guard and die
# with the run.
RUN SPRING_PROFILES_ACTIVE=training STORAGE_LOCATION=/tmp/training-storage \
    APP_JWT_SECRET="$(head -c 64 /dev/urandom | base64 -w0)" \
    APP_TWOFACTOR_KEY="$(head -c 32 /dev/urandom | base64 -w0)" \
    AUTH_CLIENTS_VAULT_SECRET="$(head -c 24 /dev/urandom | base64 -w0)" \
    java -XX:MaxRAM=2g -XX:AOTCacheOutput=/app/app.aot -Dspring.context.exit=onRefresh -jar /app/app.jar \
 && find /tmp -mindepth 1 -delete

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
USER app

ENTRYPOINT ["java", "-XX:AOTCache=/app/app.aot", "-jar", "/app/app.jar"]
