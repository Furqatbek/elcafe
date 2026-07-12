# Local development launcher (Windows PowerShell) — zero setup.
#
# Windows equivalent of run-local.sh: runs the full stack (db, redis, backend, frontend, nginx)
# with the dev overrides in docker-compose.local.yml, supplying throwaway localhost DB/Redis
# passwords so the base compose's required secret guards are satisfied without any .env file.
#
# NEVER use this for production — production runs docker-compose.yml alone with real secrets from
# .env.docker (see deploy-docker.sh and docs/LAUNCH.md).
#
# Usage (from the repo root):
#   ./run-local.ps1            # foreground
#   ./run-local.ps1 -d         # detached
#   ./run-local.ps1 backend    # just the backend service (+ its deps)
#
# If PowerShell blocks the script ("running scripts is disabled"), either run it as
#   powershell -ExecutionPolicy Bypass -File .\run-local.ps1
# or allow local scripts once:  Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

# Throwaway, localhost-only. Overridable from the shell if you want specific values.
if (-not $env:DB_PASSWORD)    { $env:DB_PASSWORD = "dev" }
if (-not $env:REDIS_PASSWORD) { $env:REDIS_PASSWORD = "dev" }

docker compose -f docker-compose.yml -f docker-compose.local.yml up --build @args
