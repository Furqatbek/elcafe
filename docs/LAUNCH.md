# How to launch

Prerequisites: a server with Docker, a domain pointing at it, and (for HTTPS) a Let's Encrypt
certificate in `/etc/letsencrypt`.

## 1. Configure

```bash
cp .env.docker.example .env.docker
nano .env.docker
```

Fill in the six required values — `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`,
`ADMIN_EMAIL`, `ADMIN_PASSWORD` (generate commands are in the file). For HTTPS also uncomment
`NGINX_CONF=./nginx-proxy/nginx.conf`.

## 2. Start

```bash
docker compose --env-file .env.docker up -d --build
```

The database starts empty: migrations create the schema (no demo data), and on first boot the app
creates your `SUPER_ADMIN` account from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

## 3. Verify

```bash
curl -s http://localhost:8080/actuator/health   # → {"status":"UP",...}
```

Log in at `https://<your-domain>` with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

## 4. After first login

1. Change the admin password in the admin panel.
2. Remove `ADMIN_PASSWORD` from `.env.docker`.
3. Create your restaurant, then its admin: **System Users → Add User**, role `ADMIN`, and pick the
   restaurant in the selector (visible to you as SUPER_ADMIN). That admin manages their own staff
   from then on.

## Day 2

| What | How |
|---|---|
| Logs | `docker compose logs -f backend` |
| Deploy an update | `git pull && docker compose --env-file .env.docker up -d --build` |
| Backups | automatic daily `pg_dump` to `./backups/` (7 daily / 4 weekly / 6 monthly) |
| Incident rollback levers | the commented `*_MODE` switches in `.env.docker` |

Full reference (env table, runbooks, restore procedure): `PRODUCTION_SETUP.md`.
