# syntax=docker/dockerfile:1
#
# Kontinuance web UI image. Built from the repository root as build context
# (see deploy/docker-compose.yml). Multi-stage: build the static SPA, then serve it with nginx.

# --- Build stage: build the static SPA ------------------------------------------
FROM node:22 AS build
WORKDIR /web
RUN corepack enable
# Install with the lockfile first (better layer caching), then build.
COPY web/package.json web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
# adapter-static -> web/build (client-rendered SPA with an index.html fallback).
RUN pnpm build

# --- Runtime stage: nginx serves the SPA and fronts the API on one origin --------
FROM nginx:stable AS runtime
COPY --from=build /web/build/ /usr/share/nginx/html/
# Rendered to /etc/nginx/conf.d/default.conf by the image's envsubst entrypoint at start.
COPY deploy/web/default.conf.template /etc/nginx/templates/default.conf.template
# Default proxy target (compose service DNS); override at run time without rebuilding.
ENV KONTINUANCE_BACKEND=http://server:8077
EXPOSE 80
