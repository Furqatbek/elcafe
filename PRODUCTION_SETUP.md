# 🚀 Production Deployment with NGINX Reverse Proxy

This guide explains how to deploy ElCafe with Docker Compose behind NGINX reverse proxy.

> **Just launching?** The short checklist is [`docs/LAUNCH.md`](docs/LAUNCH.md) — this document is the
> full reference behind it.

## Architecture

TLS is terminated by the **`nginx-proxy`** service that ships with the Docker Compose stack
(select the HTTPS config with `NGINX_CONF=./nginx-proxy/nginx.conf`) — there is no separate
host NGINX. `nginx-proxy` is the only service that publishes ports (80/443); the frontend and
backend are reached only through it.

```
Internet (HTTPS)
    ↓
nginx-proxy service (Docker) - Handles SSL, publishes 80/443
    ├─→ https://qahvoon.uz/          → Frontend (customer site)
    ├─→ https://qahvoon.uz/admin/    → Admin dashboard (same frontend, served at /admin)
    └─→ https://qahvoon.uz/api       → Backend (127.0.0.1:8080, loopback only)
```

### Services

| Service | Docker Port | Host Port | URL |
|---------|-------------|-----------|-----|
| nginx-proxy | 80 / 443 | 80 / 443 | https://qahvoon.uz |
| Frontend | 80 (internal) | none (via nginx-proxy) | https://qahvoon.uz and https://qahvoon.uz/admin/ |
| Backend | 8080 (internal) | 127.0.0.1:8080 | https://qahvoon.uz/api |
| PostgreSQL | 5432 (internal) | 127.0.0.1:5432 | localhost only |
| Redis | 6379 (internal) | 127.0.0.1:6379 | localhost only |

## Prerequisites

- Ubuntu Server 20.04+ or similar Linux
- Docker & Docker Compose installed
- Domain pointed to your server (qahvoon.uz)
- Ports 80, 443 open in firewall

## Step-by-Step Setup

### 1. Install Docker & Docker Compose

```bash
# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
docker-compose --version
```

### 2. Clone Repository

```bash
cd /opt
sudo mkdir elcafe
sudo chown $USER:$USER elcafe
git clone https://github.com/Furqatbek/elcafe.git elcafe
cd elcafe
```

### 3. Configure Environment

```bash
# Copy environment template
cp .env.docker.example .env.docker

# Edit environment variables
nano .env.docker
```

**Required settings in .env.docker:**
```env
DB_PASSWORD=your_secure_database_password    # Generate: openssl rand -base64 24
REDIS_PASSWORD=your_secure_redis_password    # Generate: openssl rand -base64 24
JWT_SECRET=your_super_secret_jwt_key_here    # Generate: openssl rand -hex 32
CORS_ORIGINS=https://qahvoon.uz,https://www.qahvoon.uz
# First boot only — creates the SUPER_ADMIN account while the users table is empty (migrations
# seed no users). Change the password after first login, then remove ADMIN_PASSWORD.
ADMIN_EMAIL=you@example.com
ADMIN_PASSWORD=a_strong_one_time_password
```

> **`JWT_SECRET` is required** — the app fails to start without it (no insecure default) and rejects a
> too-short or the old committed value.

**Enforcement flags** (tenant isolation and WebSocket auth now DEFAULT TO ENFORCE after their
shadow soaks; each env var is the one-line rollback):
```env
# Cross-tenant isolation (Phase 0): off | shadow | enforce. DEFAULT ENFORCE — cross-tenant access
# returns 403. Rollback: set to shadow. Runbook: docs/TENANT_ENFORCE_FLIP_RUNBOOK.md.
TENANT_ENFORCEMENT_MODE=enforce
# STOMP WebSocket auth: off | shadow | enforce. DEFAULT ENFORCE — CONNECT requires a Bearer token
# (the print agent sends its AGENT_TOKEN). Rollback: set to shadow.
WEBSOCKET_AUTH_MODE=enforce
# Subscription access gate (Phase 2): off | shadow | enforce. Default off (dark). In 'enforce', a
# SUPER_ADMIN-suspended tenant's staff and waiters get 402 SUBSCRIPTION_INACTIVE. Flip off -> shadow
# (watch [subscription-shadow] logs) -> enforce. Runbook: docs/SUBSCRIPTION_ENFORCE_FLIP_RUNBOOK.md.
SUBSCRIPTION_ENFORCEMENT_MODE=off
# Loyalty/marketing order-completion chain (points, milestones, thank-you SMS): default false.
# Setting true starts granting customer bonuses — a product decision.
ORDER_COMPLETED_EVENTS_ENABLED=false
```

**Observability switches** (both dormant by default):
```env
# json = one JSON object per log line (for Loki/ELK/CloudWatch); unset = plain text.
LOG_FORMAT=json
# Error tracking: dormant without a DSN; with one, unhandled 500s report with request/tenant context.
SENTRY_DSN=
SENTRY_ENVIRONMENT=production
```

### 4. Install SSL Certificate

> **The reverse proxy is built into the Compose stack.** TLS is terminated by the **`nginx-proxy`**
> service, not a host NGINX — do **not** install `nginx-production.conf` on the host (it would
> collide with `nginx-proxy` on ports 80/443). Select the HTTPS proxy config by setting `NGINX_CONF`
> in `.env.docker`:
>
> ```env
> NGINX_CONF=./nginx-proxy/nginx.conf
> ```
>
> `nginx-proxy` mounts the host's `/etc/letsencrypt` read-only and expects certificates at
> `/etc/letsencrypt/live/qahvoon.uz/`.

```bash
# Install Certbot
sudo apt install certbot -y

# Get the certificate (free up port 80 first, or use the webroot method)
sudo certbot certonly --standalone -d qahvoon.uz -d www.qahvoon.uz

# Certificates are saved at (read straight through the nginx-proxy mount — no copying needed):
# /etc/letsencrypt/live/qahvoon.uz/fullchain.pem
# /etc/letsencrypt/live/qahvoon.uz/privkey.pem
```

### 5. Deploy Docker Containers

```bash
# Make deploy script executable
chmod +x deploy-docker.sh

# Deploy all services
./deploy-docker.sh
```

Or manually:
```bash
docker-compose up -d --build
```

### 6. Configure Firewall

```bash
# Install UFW
sudo apt install ufw -y

# Allow SSH, HTTP, HTTPS
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Enable firewall
sudo ufw enable
sudo ufw status
```

### 7. Verify Deployment

```bash
# Check Docker containers (includes nginx-proxy)
docker-compose ps

# Backend health — correct actuator path, via the loopback-published backend port
# (actuator is NOT under /api/v1 and is not exposed through nginx-proxy)
curl http://127.0.0.1:8080/actuator/health

# Test the site + admin dashboard through nginx-proxy
curl -I https://qahvoon.uz          # Frontend (customer site)
curl -I https://qahvoon.uz/admin/   # Admin dashboard (same frontend)
```

## Post-Deployment

### Set Up Auto-Renewal for SSL

```bash
# Test renewal
sudo certbot renew --dry-run

# Auto-renewal is automatic with certbot, but you can check:
sudo systemctl status certbot.timer
```

### Set Up Database Backups

```bash
# Create backup directory
sudo mkdir -p /backups/elcafe

# Create backup script
sudo nano /usr/local/bin/backup-elcafe.sh
```

Add:
```bash
#!/bin/bash
BACKUP_DIR="/backups/elcafe"
DATE=$(date +%Y%m%d_%H%M%S)
cd /opt/elcafe
docker-compose exec -T db pg_dump -U elcafe elcafe > "$BACKUP_DIR/backup_$DATE.sql"

# Keep only last 30 days
find "$BACKUP_DIR" -name "backup_*.sql" -mtime +30 -delete
```

Make executable:
```bash
sudo chmod +x /usr/local/bin/backup-elcafe.sh
```

Schedule daily backups:
```bash
sudo crontab -e
```

Add:
```
0 2 * * * /usr/local/bin/backup-elcafe.sh
```

## Common Operations

### Start Services
```bash
cd /opt/elcafe
docker-compose up -d
```

### Stop Services
```bash
cd /opt/elcafe
docker-compose down
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f db
```

### Restart Services
```bash
# All services
docker-compose restart

# Specific service
docker-compose restart backend
```

### Update Application
```bash
cd /opt/elcafe
git pull
./deploy-docker.sh
```

### Rebuild from Scratch
```bash
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

## Monitoring

### Check Service Status
```bash
# Docker containers (includes nginx-proxy)
docker-compose ps

# Resource usage
docker stats
```

### View nginx-proxy Logs
```bash
# nginx-proxy runs as a Compose service — read its access/error logs via Docker
docker-compose logs -f nginx-proxy
```

### Check SSL Certificate Expiry
```bash
sudo certbot certificates
```

## Troubleshooting

### Frontend Not Loading
```bash
# Check frontend container
docker-compose logs frontend

# The frontend has no host port — reach it through nginx-proxy
curl -I http://localhost/          # customer site
curl -I http://localhost/admin/    # admin dashboard

# Check the nginx-proxy container and validate its config
docker-compose logs nginx-proxy
docker-compose exec nginx-proxy nginx -t
```

### API Requests Failing
```bash
# Check backend container
docker-compose logs backend

# Check backend health (correct actuator path; loopback-published port)
curl http://localhost:8080/actuator/health

# Check that nginx-proxy forwards /api to the backend (actuator is NOT under /api/v1)
curl -I https://qahvoon.uz/api/v1/
```

### SSL Issues
```bash
# Check certificate
sudo certbot certificates

# Renew manually
sudo certbot renew

# Validate and reload the nginx-proxy config after renewal
docker-compose exec nginx-proxy nginx -t
docker-compose restart nginx-proxy
```

### Database Connection Issues
```bash
# Check database container
docker-compose logs db

# Test database connection
docker-compose exec db psql -U elcafe elcafe
```

### Port Conflicts
```bash
# Check what's using ports
sudo lsof -i :80
sudo lsof -i :443
sudo lsof -i :8080
```

## Security Checklist

- [ ] SSL certificates installed and auto-renewing
- [ ] Firewall configured (only 22, 80, 443 open)
- [ ] Strong database password set in .env.docker
- [ ] Secure JWT secret generated (32+ characters)
- [ ] CORS origins restricted to production domains
- [ ] PostgreSQL not exposed to internet (only localhost)
- [ ] Regular backups scheduled
- [ ] Actuator endpoints restricted (optional)
- [ ] System updates automated
- [ ] Monitoring set up

## Performance Tuning

### Increase Backend Memory
Edit `Dockerfile`:
```dockerfile
ENV JAVA_OPTS="-Xmx1024m -Xms512m"
```

Rebuild:
```bash
docker-compose up -d --build backend
```

### PostgreSQL Optimization
Edit `docker-compose.yml`:
```yaml
db:
  command: postgres -c shared_buffers=256MB -c max_connections=200
```

### NGINX Caching
Add to `nginx-proxy/nginx.conf`:
```nginx
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=my_cache:10m max_size=1g;

location /api {
    proxy_cache my_cache;
    proxy_cache_valid 200 5m;
    # ... rest of config
}
```

## Maintenance

### System Updates
```bash
# Update packages
sudo apt update
sudo apt upgrade -y

# Update Docker images
cd /opt/elcafe
docker-compose pull
docker-compose up -d
```

### Clean Docker Resources
```bash
# Remove unused images
docker image prune -a

# Remove unused volumes (⚠️ BE CAREFUL)
docker volume prune

# Clean everything except volumes
docker system prune -a
```

## Rollback Procedure

If deployment fails:

```bash
# 1. Stop containers
docker-compose down

# 2. Checkout previous version
git log --oneline -5  # Find previous commit
git checkout <previous-commit-hash>

# 3. Rebuild and start
docker-compose up -d --build

# 4. If database issues, restore backup
cat /backups/elcafe/backup_YYYYMMDD_HHMMSS.sql | docker-compose exec -T db psql -U elcafe elcafe
```

## Complete Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Internet (HTTPS)                      │
└────────────────────┬──────────────────────────────────┘
                     │ 80 / 443 (published)
┌────────────────────▼──────────────────────────────────┐
│            nginx-proxy service (Docker)                 │
│  - SSL termination (NGINX_CONF=./nginx-proxy/nginx.conf)│
│  - Reverse proxy: / and /admin/ -> frontend             │
│                   /api -> backend                       │
└──────────────┬───────────────────────┬─────────────────┘
               │ frontend:80            │ backend:8080
               │ (no host port)         │ (also 127.0.0.1:8080)
        ┌──────▼───────────┐     ┌──────▼──────┐
        │ Frontend (Docker)│     │   Backend   │
        │ React + NGINX    │     │  (Docker)   │
        │ site + /admin    │     │   Spring    │
        └──────────────────┘     └──────┬──────┘
                                        │ db:5432 / redis:6379
                          ┌─────────────┴─────────────┐
                    ┌─────▼──────┐             ┌───────▼─────┐
                    │ PostgreSQL │             │    Redis    │
                    │  (Docker)  │             │  (Docker)   │
                    │ ./data/    │             │ redis_data  │
                    │  postgres  │             │             │
                    └────────────┘             └─────────────┘
```

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_NAME` | elcafe | Database name |
| `DB_USER` | elcafe | Database user |
| `DB_PASSWORD` | - | Database password (required) |
| `JWT_SECRET` | - | JWT signing key (required) |
| `REDIS_PASSWORD` | - | Redis password (required) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | - | First-boot SUPER_ADMIN bootstrap (empty users table only) |
| `SPRING_PROFILES_ACTIVE` | prod | Spring profile |
| `CORS_ORIGINS` | (required) | Exact allowed origins, comma-separated (e.g. `https://qahvoon.uz,https://www.qahvoon.uz`); blank or `*` is rejected at startup in prod |
| `TENANT_ENFORCEMENT_MODE` | enforce | Tenant isolation: off/shadow/enforce |
| `WEBSOCKET_AUTH_MODE` | enforce | STOMP auth: off/shadow/enforce |
| `SUBSCRIPTION_ENFORCEMENT_MODE` | off | Suspended-tenant gate: off/shadow/enforce |
| `ORDER_COMPLETED_EVENTS_ENABLED` | false | Loyalty/SMS completion chain (product flip) |
| `LOG_FORMAT` | plain | `json` = structured console logs |
| `SENTRY_DSN` | - | Error tracking (dormant without) |
| `SPRING_JPA_OPEN_IN_VIEW` | false | Emergency OSIV rollback switch |
| `HIBERNATE_LAZY_LOAD_NO_TRANS` | false | Emergency lazy-load rollback switch |
| `VITE_API_URL` | /api/v1 | Frontend API base URL (build arg) |

## Support

For issues:
1. Check logs: `docker-compose logs -f`
2. Check nginx-proxy: `docker-compose logs -f nginx-proxy`
3. Verify services: `docker-compose ps`
4. Test connectivity: `curl -I http://localhost/`

---

**Last Updated:** 2026-07-21
