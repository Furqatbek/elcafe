# 🚀 Production Deployment with NGINX Reverse Proxy

This guide explains how to deploy ElCafe with Docker Compose behind NGINX reverse proxy.

## Architecture

```
Internet (HTTPS)
    ↓
NGINX (Host Server) - Handles SSL, Port 80/443/3000
    ├─→ https://lacasa.uz/          → Frontend (Docker: localhost:9090)
    ├─→ https://lacasa.uz/api       → Backend (Docker: localhost:8080)
    └─→ https://lacasa.uz:3000      → Admin Panel (Docker: localhost:3000)
```

### Services

| Service | Docker Port | Host Port | URL |
|---------|-------------|-----------|-----|
| Frontend | 80 (internal) | 9090 | https://lacasa.uz |
| Backend | 8080 (internal) | 8080 | https://lacasa.uz/api |
| Admin Panel | 3000 (internal) | 3000 | https://lacasa.uz:3000 |
| PostgreSQL | 5432 (internal) | 5432 | localhost only |

## Prerequisites

- Ubuntu Server 20.04+ or similar Linux
- Docker & Docker Compose installed
- Domain pointed to your server (lacasa.uz)
- Ports 80, 443, 3000 open in firewall

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

### 2. Install NGINX

```bash
sudo apt update
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

### 3. Clone Repository

```bash
cd /opt
sudo mkdir elcafe
sudo chown $USER:$USER elcafe
git clone https://github.com/Furqatbek/elcafe.git elcafe
cd elcafe
```

### 4. Configure Environment

```bash
# Copy environment template
cp .env.docker .env.docker

# Edit environment variables
nano .env.docker
```

**Required settings in .env.docker:**
```env
DB_PASSWORD=your_secure_database_password
JWT_SECRET=your_super_secret_jwt_key_here  # Generate: openssl rand -hex 32
CORS_ORIGINS=https://lacasa.uz,https://www.lacasa.uz
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

### 5. Configure NGINX Reverse Proxy

```bash
# Copy NGINX configuration
sudo cp nginx-production.conf /etc/nginx/sites-available/lacasa.uz

# Enable the site
sudo ln -s /etc/nginx/sites-available/lacasa.uz /etc/nginx/sites-enabled/

# Remove default site
sudo rm /etc/nginx/sites-enabled/default

# Test configuration
sudo nginx -t
```

### 6. Install SSL Certificate

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx -y

# Create certbot directory
sudo mkdir -p /var/www/certbot

# Get certificate
sudo certbot certonly --webroot -w /var/www/certbot -d lacasa.uz -d www.lacasa.uz

# Certificate will be saved at:
# /etc/letsencrypt/live/lacasa.uz/fullchain.pem
# /etc/letsencrypt/live/lacasa.uz/privkey.pem
```

### 7. Reload NGINX

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### 8. Deploy Docker Containers

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

### 9. Configure Firewall

```bash
# Install UFW
sudo apt install ufw -y

# Allow SSH, HTTP, HTTPS, Admin Panel
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 3000/tcp

# Enable firewall
sudo ufw enable
sudo ufw status
```

### 10. Verify Deployment

```bash
# Check Docker containers
docker-compose ps

# Check NGINX
sudo systemctl status nginx

# Test URLs
curl http://localhost:9090  # Frontend
curl http://localhost:8080/actuator/health  # Backend

# Test through NGINX
curl https://lacasa.uz
curl https://lacasa.uz/api/v1/actuator/health
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
# Docker containers
docker-compose ps

# NGINX
sudo systemctl status nginx

# Resource usage
docker stats
```

### View NGINX Logs
```bash
# Access logs
sudo tail -f /var/log/nginx/access.log

# Error logs
sudo tail -f /var/log/nginx/error.log
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

# Check if port 9090 is accessible
curl http://localhost:9090

# Check NGINX config
sudo nginx -t
```

### API Requests Failing
```bash
# Check backend container
docker-compose logs backend

# Check backend health
curl http://localhost:8080/actuator/health

# Check NGINX proxy
curl https://lacasa.uz/api/v1/actuator/health
```

### SSL Issues
```bash
# Check certificate
sudo certbot certificates

# Renew manually
sudo certbot renew

# Check NGINX SSL config
sudo nginx -t
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
sudo lsof -i :9090
sudo lsof -i :3000
```

## Security Checklist

- [ ] SSL certificates installed and auto-renewing
- [ ] Firewall configured (only 22, 80, 443, 3000 open)
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
Add to `nginx-production.conf`:
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
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Host NGINX (Port 80/443/3000)              │
│  - SSL Termination                                      │
│  - Reverse Proxy                                        │
│  - Load Balancing                                       │
└──────┬─────────────┬─────────────┬─────────────────────┘
       │             │             │
       │ :9090       │ :8080       │ :3000
       │             │             │
┌──────▼──────┐ ┌────▼──────┐ ┌────▼──────────┐
│  Frontend   │ │  Backend  │ │  Admin Panel  │
│  (Docker)   │ │  (Docker) │ │   (Docker)    │
│  React+NGINX│ │  Spring   │ │               │
└─────────────┘ └─────┬─────┘ └───────────────┘
                      │
                      │ :5432
                ┌─────▼──────┐
                │ PostgreSQL │
                │  (Docker)  │
                └────────────┘
```

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_NAME` | elcafe | Database name |
| `DB_USER` | elcafe | Database user |
| `DB_PASSWORD` | - | Database password (required) |
| `JWT_SECRET` | - | JWT signing key (required) |
| `REDIS_PASSWORD` | - | Redis password (required) |
| `SPRING_PROFILES_ACTIVE` | prod | Spring profile |
| `CORS_ORIGINS` | localhost | Allowed origins (HTTP + WebSocket) |
| `TENANT_ENFORCEMENT_MODE` | enforce | Tenant isolation: off/shadow/enforce |
| `WEBSOCKET_AUTH_MODE` | enforce | STOMP auth: off/shadow/enforce |
| `SUBSCRIPTION_ENFORCEMENT_MODE` | off | Suspended-tenant gate: off/shadow/enforce |
| `ORDER_COMPLETED_EVENTS_ENABLED` | false | Loyalty/SMS completion chain (product flip) |
| `LOG_FORMAT` | plain | `json` = structured console logs |
| `SENTRY_DSN` | - | Error tracking (dormant without) |
| `SPRING_JPA_OPEN_IN_VIEW` | false | Emergency OSIV rollback switch |
| `HIBERNATE_LAZY_LOAD_NO_TRANS` | false | Emergency lazy-load rollback switch |
| `VITE_API_URL` | /api | Frontend API URL |

## Support

For issues:
1. Check logs: `docker-compose logs -f`
2. Check NGINX: `sudo tail -f /var/log/nginx/error.log`
3. Verify services: `docker-compose ps`
4. Test connectivity: `curl http://localhost:9090`

---

**Last Updated:** 2026-07-11
