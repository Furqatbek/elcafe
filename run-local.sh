#!/bin/bash

# Local development launcher — zero setup.
#
# Runs the full stack (db, redis, backend, frontend, nginx) with the dev overrides in
# docker-compose.local.yml: dev Spring profile, a dev-only JWT secret, HTTP-only nginx and
# localhost URLs. Supplies throwaway localhost DB/Redis passwords so the base compose's required
# secret guards are satisfied without any .env file.
#
# NEVER use this for production — production runs docker-compose.yml alone with real secrets from
# .env.docker (see ./deploy-docker.sh and docs/LAUNCH.md).
#
# Any extra args are passed through to `docker compose up`, e.g.:
#   ./run-local.sh            # foreground
#   ./run-local.sh -d         # detached
#   ./run-local.sh backend    # just the backend service (+ its deps)

set -e

# Throwaway, localhost-only. Overridable from the shell if you want specific values.
export DB_PASSWORD="${DB_PASSWORD:-dev}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-dev}"

exec docker compose \
    -f docker-compose.yml \
    -f docker-compose.local.yml \
    up --build "$@"
