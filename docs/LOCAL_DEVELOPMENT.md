# Run locally (development)

Run the whole stack — Postgres, Redis, backend, frontend, nginx — on your machine with one command
and zero configuration. This is for local development only. For a real deployment, see
[LAUNCH.md](./LAUNCH.md) (production).

## Prerequisites

- Docker Desktop with Compose **v2.24+** (any recent Docker Desktop). Nothing else — no JDK, Node,
  or `.env` file needed.
- Host ports **80** (the app) and **8080** (the backend API) free.

## Start

macOS / Linux:

```bash
./run-local.sh
```

Windows (PowerShell, from the repo root):

```powershell
./run-local.ps1
```

Either launcher runs `docker compose -f docker-compose.yml -f docker-compose.local.yml up --build`
with throwaway `DB_PASSWORD`/`REDIS_PASSWORD` (`dev`), the `dev` Spring profile, a dev-only JWT
secret, and an HTTP-only nginx. Extra arguments pass straight through:

```bash
./run-local.sh -d          # detached (background)
./run-local.sh backend     # just the backend + its dependencies
```

The database starts empty on a fresh named volume (`pgdata_local`); migrations build the schema —
no demo data.

## Create a login (first boot)

Local runs don't seed any user. To get a `SUPER_ADMIN` you can sign in with, pass `ADMIN_EMAIL` and
`ADMIN_PASSWORD` on the first run — the app creates the account while the users table is empty:

macOS / Linux:

```bash
ADMIN_EMAIL=admin@local.test ADMIN_PASSWORD='LocalAdmin123!' ./run-local.sh
```

Windows (PowerShell):

```powershell
$env:ADMIN_EMAIL='admin@local.test'; $env:ADMIN_PASSWORD='LocalAdmin123!'; ./run-local.ps1
```

Then open the admin panel and sign in. (The account is created only when the DB is empty; after a
`down -v` reset you'll set it again.)

## Access

| What | URL |
|---|---|
| Admin dashboard | http://localhost/admin/ (login at http://localhost/admin/login) |
| QR / self-service ordering | http://localhost/order |
| API (through nginx) | http://localhost/api/v1/… |
| Backend health | http://localhost:8080/actuator/health |

The frontend is served only through nginx (no separate host port). The backend is also directly
reachable at `127.0.0.1:8080`. Postgres and Redis are **not** published to the host in local mode
(so they don't clash with a locally installed Postgres/Redis) — reach them via `docker compose exec`
if needed.

## Everyday commands

The launcher uses both compose files, so pass both to any follow-up command:

```bash
# tail backend logs
docker compose -f docker-compose.yml -f docker-compose.local.yml logs -f backend

# stop (if started with -d)
docker compose -f docker-compose.yml -f docker-compose.local.yml down

# reset the database (drops the pgdata_local volume — matches the always-empty-DB workflow)
docker compose -f docker-compose.yml -f docker-compose.local.yml down -v
```

Rebuild after code changes by simply re-running the launcher — it always `--build`s.

## Optional: test consumer OTP without SMS

Consumer login uses an SMS OTP. To develop that flow without a real SMS provider, set
`CONSUMER_OTP_DEVELOPMENT_MODE=true` for the run (it must stay `false` in production):

```bash
CONSUMER_OTP_DEVELOPMENT_MODE=true ./run-local.sh
```

## Troubleshooting

- **Port 80 already in use** — free it (or the app is unreachable). The launcher publishes only
  port 80; the datastore ports are deliberately not published, so they won't clash with a local
  Postgres/Redis.
- **Windows: "running scripts is disabled"** — run
  `powershell -ExecutionPolicy Bypass -File .\run-local.ps1`, or allow local scripts once with
  `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.
- **`password authentication failed for user elcafe`** — a stale DB volume from an earlier run with a
  different password. Reset: `docker compose -f docker-compose.yml -f docker-compose.local.yml down -v`.

## This is not production

`run-local.*` supply throwaway secrets and a dev JWT key and never enable the production config
validation. Production runs `docker-compose.yml` **alone** with real secrets from `.env.docker` — see
[LAUNCH.md](./LAUNCH.md) and [PRODUCTION_SETUP.md](../PRODUCTION_SETUP.md).
