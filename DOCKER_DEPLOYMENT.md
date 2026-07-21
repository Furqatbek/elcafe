# 🐳 Docker Deployment Guide

Deploy the entire Qahvoon application stack with **one command** using Docker Compose.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- Git

## Quick Start (Production)

### 1. Clone Repository
```bash
git clone https://github.com/Furqatbek/elcafe.git
cd elcafe
```

### 2. Configure Environment
```bash
# Copy environment template
cp .env.docker.example .env.docker

# Edit with your values
nano .env.docker
```

**Required changes:**
- `DB_PASSWORD` - Set a strong database password
- `REDIS_PASSWORD` - Set a strong Redis password (Compose refuses to start without it — `${REDIS_PASSWORD:?...}`)
- `JWT_SECRET` - Generate with: `openssl rand -hex 32`
- `CORS_ORIGINS` - Set your production domain(s); a blank or `*` value is rejected at startup in the prod profile
- `ADMIN_EMAIL` / `ADMIN_PASSWORD` - First-boot SUPER_ADMIN account, created only while the users table is empty (remove `ADMIN_PASSWORD` after your first login)

### 3. Deploy Everything
```bash
chmod +x deploy-docker.sh
./deploy-docker.sh
```

That's it! 🎉

## What Gets Deployed

The stack includes:

- **PostgreSQL 16** (`postgres:16-alpine`) - Database, stored in a host bind mount at `./data/postgres`
- **Automatic DB Backups** (`db-backup` sidecar) - Daily `pg_dump` to `./backups`, rotated automatically (7 daily / 4 weekly / 6 monthly)
- **Redis 7** - Cache and session store (password-protected)
- **Spring Boot Backend** - REST API, published on loopback only (`127.0.0.1:8080`)
- **React Frontend** - Admin dashboard + customer site served by NGINX on internal port 80 (no host port; reached only through the proxy)
- **nginx-proxy** - Reverse proxy that publishes 80/443, terminates TLS, and routes `/api` → backend and `/`, `/admin/`, `/order` → frontend

## Access Your Application

- **Frontend / customer site**: http://localhost (or https://your-domain.com in production)
- **Admin dashboard**: http://localhost/admin
- **API via nginx-proxy**: http://localhost/api
- **Backend (host-local debugging only)**: http://127.0.0.1:8080

## Common Commands

### Start Services
```bash
docker compose up -d
```

### Stop Services
```bash
docker compose down
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f db
```

### Restart Services
```bash
# All services
docker compose restart

# Specific service
docker compose restart backend
```

### View Status
```bash
docker compose ps
```

### Update Application
```bash
git pull
./deploy-docker.sh
```

### Rebuild Without Cache
```bash
docker compose build --no-cache
docker compose up -d
```

## Database Management

A **`db-backup` sidecar runs automatic daily backups** — `pg_dump` output is written to `./backups` on the host and rotated automatically (7 daily, 4 weekly, 6 monthly). No host cron is required.

### Restore From an Automatic Backup
```bash
gunzip -c ./backups/daily/<file>.sql.gz | docker exec -i elcafe-db psql -U elcafe -d elcafe
```

### Manual Backup
```bash
docker compose exec db pg_dump -U elcafe elcafe > backup_$(date +%Y%m%d).sql
```

### Restore a Manual Backup
```bash
cat backup_20231220.sql | docker compose exec -T db psql -U elcafe elcafe
```

### Access Database Shell
```bash
docker compose exec db psql -U elcafe elcafe
```

## Production Deployment (with Domain)

In production, TLS is terminated by the dedicated **`nginx-proxy`** service (it publishes ports 80/443), **not** by the `frontend` container. You switch the proxy to its HTTPS config with the `NGINX_CONF` variable and mount the host's Let's Encrypt certificates. The `frontend` service publishes **no host ports** — it is reachable only through `nginx-proxy`.

### 1. Point Your Domain
Create DNS A records for `qahvoon.uz` and `www.qahvoon.uz` pointing at your server.

### 2. Update Environment
Edit `.env.docker`:
```env
CORS_ORIGINS=https://qahvoon.uz,https://www.qahvoon.uz

# Select the HTTPS proxy config (the default, nginx-local.conf, is HTTP-only)
NGINX_CONF=./nginx-proxy/nginx.conf
```

### 3. Get SSL Certificates
`nginx-proxy` mounts the host's `/etc/letsencrypt` read-only, and `nginx-proxy/nginx.conf` expects certificates at `/etc/letsencrypt/live/qahvoon.uz/`.

```bash
# Install certbot
sudo apt install certbot

# Obtain the certificate (free up port 80 first, or use the webroot method)
sudo certbot certonly --standalone -d qahvoon.uz -d www.qahvoon.uz
```

Certificates are written to `/etc/letsencrypt/live/qahvoon.uz/{fullchain,privkey}.pem` and are read straight through the mount — nothing needs to be copied into the repo.

### 4. Deploy
```bash
./deploy-docker.sh
```

`nginx-proxy` now serves HTTPS on 443 (redirecting 80 → 443), proxying `/api` to the backend and `/`, `/admin/` to the frontend.

## Troubleshooting

### Backend Won't Start
```bash
# Check logs
docker compose logs backend

# Check if database is ready
docker compose logs db

# Restart backend
docker compose restart backend
```

### Database Connection Issues
```bash
# Verify database is running
docker compose ps db

# Check database logs
docker compose logs db

# Test connection
docker compose exec backend curl db:5432
```

### Frontend Can't Reach Backend
```bash
# Verify backend is running
docker compose ps backend

# Check backend health
curl http://localhost:8080/actuator/health

# Verify network
docker network inspect elcafe_elcafe-network
```

### Port Already in Use
```bash
# Find process using port 80
sudo lsof -i :80

# Find process using port 8080
sudo lsof -i :8080

# Change ports in docker-compose.yml if needed
```

### Out of Disk Space
```bash
# Clean unused images
docker image prune -a

# Clean volumes (⚠️ This deletes data!)
docker volume prune

# Clean everything except volumes
docker system prune -a
```

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_NAME` | elcafe | PostgreSQL database name |
| `DB_USER` | elcafe | PostgreSQL username |
| `DB_PASSWORD` | - | PostgreSQL password (required) |
| `REDIS_PASSWORD` | - | Redis password (required — Compose won't start without it) |
| `JWT_SECRET` | - | JWT signing secret (required) |
| `SPRING_PROFILES_ACTIVE` | prod | Spring profile |
| `CORS_ORIGINS` | - | Allowed CORS origins (required in prod; blank or `*` is rejected at startup) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | - | First-boot SUPER_ADMIN bootstrap (only while the users table is empty) |
| `TENANT_ENFORCEMENT_MODE` | enforce | Cross-tenant isolation: off / shadow / enforce |
| `WEBSOCKET_AUTH_MODE` | enforce | STOMP WebSocket auth: off / shadow / enforce |
| `SUBSCRIPTION_ENFORCEMENT_MODE` | off | Suspended-tenant access gate: off / shadow / enforce |
| `VITE_API_URL` | /api/v1 | Frontend API endpoint |

## Data Persistence

- **PostgreSQL** data lives in a **host bind mount at `./data/postgres`** (not a named volume), so it survives `docker compose down -v`, `docker volume prune`, and project-name changes.
- **Automatic backups** are written to `./backups` (host bind mount) by the `db-backup` sidecar.
- **Named volumes**: `redis_data` (Redis), `uploads` (file uploads), `logs` (backend rolling logs).

To archive the Postgres data directory directly, copy the host path (stop the DB first for a consistent snapshot):
```bash
docker compose stop db
tar czf postgres_backup_$(date +%Y%m%d).tar.gz ./data/postgres
docker compose start db
```
For logical dumps, prefer the automatic `db-backup` output in `./backups` (see Database Management above).

## Performance Tuning

### Increase Backend Memory
Edit `Dockerfile`:
```dockerfile
ENV JAVA_OPTS="-Xmx1024m -Xms512m"
```

### Postgres Performance
Add to `docker-compose.yml`:
```yaml
db:
  command: postgres -c shared_buffers=256MB -c max_connections=200
```

## Security Checklist

- [ ] Changed default DB_PASSWORD
- [ ] Set a strong REDIS_PASSWORD (required)
- [ ] Generated secure JWT_SECRET (32+ chars)
- [ ] Set CORS_ORIGINS to production domains only (never blank or `*`)
- [ ] SSL certificates installed (for production)
- [ ] Firewall configured (only 80, 443 open)
- [ ] Backups verified (the `db-backup` sidecar runs daily)
- [ ] Database password rotated regularly

## Monitoring

### View Resource Usage
```bash
docker stats
```

### Check Container Health
```bash
docker compose ps
```

### Export Logs
```bash
docker compose logs > logs_$(date +%Y%m%d).txt
```

## Scaling (Future)

To run multiple backend instances:
```yaml
backend:
  deploy:
    replicas: 3
```

Add a load balancer in the nginx-proxy config.

---

## Support

For issues:
1. Check logs: `docker compose logs -f`
2. Verify all services running: `docker compose ps`
3. Check environment variables: `cat .env.docker`
4. Review this guide

---

**Last Updated:** 2026-07-21
