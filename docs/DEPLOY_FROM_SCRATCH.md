# Deploying from zero

A complete first deployment onto a bare Linux server with Docker already
installed: fill in `.env`, build the containers, issue certificates with
certbot, go live.

Supersedes `DEPLOYMENT.md` and `PRODUCTION_SETUP.md`, which describe a
systemd + `/var/www` layout this project no longer uses and a `lacasa.uz`
domain that predates two rebrands. Everything runs in containers.

## What is actually deployed

Six containers, one network, defined in `docker-compose.yml`:

| Container | Image | Role |
|---|---|---|
| `elcafe-nginx-proxy` | `nginx:alpine` | the only thing on ports 80/443; terminates TLS, routes everything |
| `elcafe-frontend` | built from `./frontend` | admin dashboard + QR ordering (React) |
| `elcafe-backend` | built from `./Dockerfile` | Spring Boot API on 8080 |
| `elcafe-db` | `postgres:16-alpine` | data in `./data/postgres` on the host |
| `elcafe-db-backup` | `postgres-backup-local` | nightly `pg_dump` into `./backups` |
| `elcafe-redis` | `redis:7-alpine` | cache only; nothing durable |

Routing, from `nginx-proxy/nginx.conf`:

```
/api, /uploads, /ws-waiter, /ws-print-agent  ->  backend:8080
/admin/  (prefix stripped)                   ->  frontend:80
/order                                       ->  frontend:80
/                                            ->  frontend:80
/health                                      ->  nginx itself
```

## 0. Before you touch the server

- An **A record** for your domain (and `www`) pointing at the server's IPv4
  address. Add an **AAAA record only if the server genuinely has working
  IPv6** — a published AAAA with no route makes clients hang rather than fail.
- **Ports 80 and 443 reachable** from the internet. Let's Encrypt validates
  over port 80; without it no certificate is issued.
- `docker` and the **Compose v2 plugin**. Check with `docker compose version`.
  The old `docker-compose` binary is gone from current Docker and the v1 name
  is not used anywhere in this project.

### Verify DNS resolves A records on the server itself

Do not skip this. A resolver that answers AAAA but not A queries produces
every symptom of a slow network and none of its causes: Maven fails with
`Unknown host repo.maven.apache.org`, BuildKit fails with `server
misbehaving`, image pulls take twenty minutes, and containers cannot resolve
each other.

```bash
getent ahostsv4 repo.maven.apache.org
getent ahostsv4 registry-1.docker.io
curl -4 -s -m 10 -o /dev/null -w 'IPv4: %{http_code}\n' https://repo.maven.apache.org/maven2/
```

Both `getent` calls must print IPv4 addresses and the curl must print 200. If
they do not, override the resolver before going any further:

```bash
mkdir -p /etc/systemd/resolved.conf.d
printf '[Resolve]\nDNS=\nDNS=1.1.1.1 8.8.8.8\nFallbackDNS=9.9.9.9 1.0.0.1\nDomains=~.\n' \
  > /etc/systemd/resolved.conf.d/dns.conf
systemctl restart systemd-resolved && resolvectl flush-caches
resolvectl status | head -5
```

The empty `DNS=` matters: systemd **appends** list values across drop-ins, so
without it the broken server stays first in the list. `Domains=~.` is also
required, because DNS servers handed out per-link by DHCP otherwise take
precedence over the global setting.

Containers get their resolvers from the Docker daemon, not from the host's
`/etc/resolv.conf`, so set those too:

```bash
printf '{\n  "dns": ["1.1.1.1", "8.8.8.8"]\n}\n' > /etc/docker/daemon.json
systemctl restart docker
```

## 1. Clone

```bash
mkdir -p /home/user && cd /home/user
git clone https://github.com/Furqatbek/elcafe.git
cd elcafe
```

## 2. Write `.env`

Compose reads `./.env` automatically and interpolates it into
`docker-compose.yml`. It is gitignored. **This is the only configuration file
that matters** — ignore `.env.docker`, which is a committed leftover that no
longer reflects anything.

Generate the secrets rather than inventing them:

```bash
cd /home/user/elcafe
cat > .env <<'EOF'
# --- database ---
DB_NAME=elcafe
DB_USER=elcafe
DB_PASSWORD=REPLACE_ME

# --- application ---
JWT_SECRET=REPLACE_ME
SPRING_PROFILES_ACTIVE=prod
CORS_ORIGINS=https://qahvoon.uz,https://www.qahvoon.uz
SELFSERVICE_BASE_URL=https://qahvoon.uz/order
FILE_BASE_URL=https://qahvoon.uz/uploads

# --- which nginx config the proxy mounts ---
# Start on bootstrap (HTTP only) because no certificate exists yet.
# Switch to ./nginx-proxy/nginx.conf in step 5.
NGINX_CONF=./nginx-proxy/nginx-bootstrap.conf
EOF

sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 24)|" .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env
chmod 600 .env
grep -oE '^[A-Z_]+=' .env
```

`openssl rand -hex` is deliberate: it produces only `[0-9a-f]`, which avoids
the trap below.

> **Never put `$` in a value.** Compose interpolates `.env` values, so
> `DB_PASSWORD=abc$xyz` silently becomes `abc` — compose warns
> `The "xyz" variable is not set. Defaulting to a blank string.` and the
> container receives a **truncated** secret. It is self-consistent, so it
> appears to work, and then quoting it "properly" later breaks the database
> connection, because `POSTGRES_PASSWORD` only applies at initdb and never to
> an existing data directory. If you must use `$`, write it `$$`.

### Deploying on a domain other than qahvoon.uz

The domain is hardcoded in `nginx-proxy/nginx.conf` in three places — the two
`server_name` lines and the two `ssl_certificate*` paths. Change them to match
before step 5, and use your domain throughout `.env`.

## 3. First boot, over HTTP

```bash
cd /home/user/elcafe
docker compose up -d --build
```

The first build is slow: it compiles ~1100 Java files and runs a Vite build
inside containers. On a small VPS allow 20–40 minutes. Build serially if the
box is memory-constrained:

```bash
docker compose build backend
docker compose build frontend
docker compose up -d
```

Watch it, and survive an SSH drop, with:

```bash
nohup docker compose build backend > /tmp/build-backend.log 2>&1 &
tail -f /tmp/build-backend.log      # Ctrl-C only stops watching
```

Then check:

```bash
docker compose ps
curl -s -o /dev/null -w 'backend: %{http_code}\n' http://localhost:8080/actuator/health
curl -s http://YOUR_SERVER_IP/health
```

The backend's health is **503 until Redis is healthy** — it aggregates every
component. `docker compose ps` should show all six `healthy`. Flyway creates
the entire schema on this first start; confirm it:

```bash
docker exec elcafe-db sh -c 'psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT max(version::int) FROM flyway_schema_history;"'
```

## 4. Issue the certificate

certbot runs on the **host**, not in a container, using the webroot that nginx
already serves:

```bash
apt update && apt install -y certbot
mkdir -p /var/www/certbot

certbot certonly --webroot -w /var/www/certbot \
  -d qahvoon.uz -d www.qahvoon.uz \
  --email you@example.com --agree-tos --no-eff-email
```

If it fails, the challenge is not reachable. Test the path directly:

```bash
echo ok > /var/www/certbot/test.txt
curl -s http://qahvoon.uz/.well-known/acme-challenge/test.txt   # must print: ok
rm /var/www/certbot/test.txt
```

A 404 means `NGINX_CONF` is not the bootstrap config; a connection failure
means port 80 is not open or DNS does not point here yet.

## 5. Switch on HTTPS

```bash
cd /home/user/elcafe
sed -i 's|^NGINX_CONF=.*|NGINX_CONF=./nginx-proxy/nginx.conf|' .env
grep NGINX_CONF .env
docker compose up -d nginx-proxy
```

Only the proxy is recreated; nothing else restarts.

```bash
docker inspect elcafe-nginx-proxy \
  --format '{{range .Mounts}}{{.Source}}{{"\n"}}{{end}}' | grep nginx
curl -sI https://qahvoon.uz | head -3
curl -s -o /dev/null -w 'http->https: %{http_code}\n' http://qahvoon.uz
```

The mount must be `nginx.conf`, not `nginx-bootstrap.conf`. HTTP should answer
**301**.

If nginx fails to start here, it is almost always because the certificate
paths in `nginx-proxy/nginx.conf` do not match what certbot created:

```bash
ls /etc/letsencrypt/live/
docker logs --tail 20 elcafe-nginx-proxy
```

## 6. Renewal

certbot installs its own timer, so renewal is automatic — but nginx runs in a
container and will keep serving the old certificate until it reloads. Add a
deploy hook once:

```bash
mkdir -p /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\ndocker exec elcafe-nginx-proxy nginx -s reload\n' \
  > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

certbot renew --dry-run
systemctl list-timers | grep certbot
```

`--dry-run` is worth running now rather than discovering a problem in 60 days.
Renewal works without downtime because `nginx-proxy/nginx.conf` serves
`/.well-known/acme-challenge/` over HTTP before redirecting everything else to
HTTPS, and `docker-compose.yml` mounts `/var/www/certbot` and
`/etc/letsencrypt` into the container read-only.

## 7. Backups

The `elcafe-db-backup` sidecar dumps nightly into `./backups` with no setup:
7 daily, 4 weekly, 6 monthly, gzipped, plus a `last/` symlink.

```bash
ls -lht backups/last/
```

On demand, reading the credentials from the container so they cannot drift:

```bash
docker exec elcafe-db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
  > ~/qahvoon_$(date +%Y%m%d_%H%M%S).sql
tail -1 ~/qahvoon_*.sql     # must end: -- PostgreSQL database dump complete
```

Restore:

```bash
gunzip -c backups/daily/elcafe-YYYYMMDD-000000.sql.gz \
  | docker exec -i elcafe-db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Postgres data lives in `./data/postgres`, a host bind mount rather than a named
volume, specifically so it survives `docker compose down -v`, volume prunes and
project renames. `docker compose down` never destroys it.

## 8. Lock down the data ports

`docker-compose.yml` publishes Postgres on `0.0.0.0:5432` and Redis on
`0.0.0.0:6379`, and Redis has no password. An internet-reachable Redis is a
well-known remote-code-execution path. Nothing in the stack needs either port
published — the containers reach each other over `elcafe-network` by service
name. Unless you connect from outside the box, bind them to localhost:

```yaml
  db:
    ports:
      - "127.0.0.1:5432:5432"
  redis:
    ports:
      - "127.0.0.1:6379:6379"
```

```bash
docker compose up -d db redis
ss -tlnp | grep -E '5432|6379'      # expect 127.0.0.1, not 0.0.0.0
```

## Routine operations

```bash
# deploy new code
git pull && docker compose up -d --build

# logs (always bound them — these get large)
docker logs --tail 100 -f elcafe-backend
docker compose logs --tail 50 nginx-proxy

# what version is actually running
docker ps --format '{{.Names}}  created={{.CreatedAt}}'
docker exec elcafe-backend ls -l /app/app.jar

# migrations
docker exec elcafe-db sh -c 'psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT version, success, installed_on FROM flyway_schema_history \
   ORDER BY installed_rank DESC LIMIT 5;"'

# promotion scope enforcement — each hit is a client over-claiming a discount
docker logs --since 1h elcafe-backend 2>&1 | grep -i clamping
```

`docker compose up -d --build` recreates only the containers whose image or
config changed, so Postgres keeps running and downtime is the backend swap
alone. Prefer it to `down` + `up`.

## Things that will bite you

| Symptom | Cause |
|---|---|
| "app is up" but new code is not live | old containers never replaced; check `docker ps` creation times, not whether the site loads |
| `Unknown host repo.maven.apache.org`, `server misbehaving` | resolver answers AAAA but not A — see step 0 |
| image pulls take 20 minutes, Maven at 52 kB/s | the same DNS fault; addresses resolve to unroutable IPv6 |
| HTTPS dies after a redeploy | `NGINX_CONF` unset, so compose fell back to `nginx-local.conf`, which has no 443 listener |
| backend 503, everything else fine | Redis unhealthy; `/actuator/health` aggregates components |
| Redis "Up" but refusing connections | process wedged; `docker restart elcafe-redis` before suspecting data corruption |
| `certbot renew` cannot validate | the port-80 block must serve `/.well-known/acme-challenge/` before redirecting |
| compose warns a secret "variable is not set" | a `$` in a `.env` value; the container is getting a truncated secret |
| `docker-compose: command not found` | use `docker compose` — v1 is gone |
| build dies at exit 137 | OOM; build the two images serially |
