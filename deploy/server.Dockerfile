# syntax=docker/dockerfile:1
#
# Kontinuance API server image. Built from the repository root as build context
# (see deploy/docker-compose.yml). Multi-stage: compile the distribution, then run it on a JRE.

# --- Build stage: compile the server distribution from source -------------------
# Gradle 8.14.x + JDK 21 (the version the project builds with; the pinned 9.5.1 wrapper is not used here).
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
# The server uses the `application` plugin (no bootJar); `installDist` ships the full runnable image.
# `-Pdependency.env=public` matches CI's public resolution; dependency verification stays ENABLED.
RUN gradle :server:installDist -Pdependency.env=public --no-daemon

# --- Runtime stage: JRE only, non-root ------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd --system kontinuance \
 && useradd --system --gid kontinuance --home-dir /app --shell /usr/sbin/nologin kontinuance
WORKDIR /app
# The application-plugin distribution: bin/kontinuance-api + lib/*.jar
COPY --from=build /workspace/server/build/install/kontinuance-api/ /app/
# Listen on all interfaces *inside* the container; the host only exposes what compose publishes.
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8077 \
    KONTINUANCE_STORE=/data/runs
RUN mkdir -p /data/runs && chown -R kontinuance:kontinuance /data /app
USER kontinuance
EXPOSE 8077
ENTRYPOINT ["/app/bin/kontinuance-api"]
