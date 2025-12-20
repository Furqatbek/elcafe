# 🐳 Docker Deployment Guide

Deploy the entire ElCafe application stack with **one command** using Docker Compose.

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
cp .env.docker .env.docker

# Edit with your values
nano .env.docker
```

**Required changes:**
- `DB_PASSWORD` - Set a strong database password
- `JWT_SECRET` - Generate with: `openssl rand -hex 32`
- `CORS_ORIGINS` - Set your production domain(s)

### 3. Deploy Everything
```bash
chmod +x deploy-docker.sh
./deploy-docker.sh
```

That's it! 🎉

## What Gets Deployed

The stack includes:

- **PostgreSQL 15** - Database with persistent storage
- **Redis 7** - Cache and session management
- **Spring Boot Backend** - REST API on port 8080
- **React Frontend** - SPA served by NGINX on port 80
- **NGINX Reverse Proxy** - Routes /api to backend

## Access Your Application

- **Frontend**: http://localhost or http://your-domain.com
- **Backend API**: http://localhost:8080
- **API via NGINX**: http://localhost/api

## Common Commands

### Start Services
```bash
docker-compose up -d
```

### Stop Services
```bash
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

### View Status
```bash
docker-compose ps
```

### Update Application
```bash
git pull
./deploy-docker.sh
```

### Rebuild Without Cache
```bash
docker-compose build --no-cache
docker-compose up -d
```

## Database Management

### Backup Database
```bash
docker-compose exec db pg_dump -U elcafe elcafe > backup_$(date +%Y%m%d).sql
```

### Restore Database
```bash
cat backup_20231220.sql | docker-compose exec -T db psql -U elcafe elcafe
```

### Access Database Shell
```bash
docker-compose exec db psql -U elcafe elcafe
```

## Production Deployment (with Domain)

### 1. Update Environment
Edit `.env.docker`:
```env
CORS_ORIGINS=https://lacasa.uz,https://www.lacasa.uz
```

### 2. Update NGINX for HTTPS

Create `frontend/nginx-ssl.conf`:
```nginx
server {
    listen 80;
    server_name lacasa.uz www.lacasa.uz;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name lacasa.uz www.lacasa.uz;

    ssl_certificate /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /uploads {
        proxy_pass http://backend:8080/uploads;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
}
```

### 3. Get SSL Certificates

Using Certbot:
```bash
# Install certbot
sudo apt install certbot

# Get certificate
sudo certbot certonly --standalone -d lacasa.uz -d www.lacasa.uz

# Copy certificates
sudo mkdir -p ./ssl
sudo cp /etc/letsencrypt/live/lacasa.uz/fullchain.pem ./ssl/
sudo cp /etc/letsencrypt/live/lacasa.uz/privkey.pem ./ssl/
sudo chmod -R 755 ./ssl
```

### 4. Update docker-compose.yml

Change frontend ports:
```yaml
frontend:
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./frontend/nginx-ssl.conf:/etc/nginx/conf.d/default.conf:ro
    - ./ssl:/etc/nginx/ssl:ro
```

### 5. Deploy
```bash
./deploy-docker.sh
```

## Troubleshooting

### Backend Won't Start
```bash
# Check logs
docker-compose logs backend

# Check if database is ready
docker-compose logs db

# Restart backend
docker-compose restart backend
```

### Database Connection Issues
```bash
# Verify database is running
docker-compose ps db

# Check database logs
docker-compose logs db

# Test connection
docker-compose exec backend curl db:5432
```

### Frontend Can't Reach Backend
```bash
# Verify backend is running
docker-compose ps backend

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
| `JWT_SECRET` | - | JWT signing secret (required) |
| `SPRING_PROFILES_ACTIVE` | prod | Spring profile |
| `CORS_ORIGINS` | localhost | Allowed CORS origins |
| `VITE_API_URL` | /api | Frontend API endpoint |

## Data Persistence

Data is persisted in Docker volumes:
- `postgres_data` - Database files
- `uploads` - File uploads

To backup volumes:
```bash
# Create backup
docker run --rm -v elcafe_postgres_data:/data -v $(pwd):/backup ubuntu tar czf /backup/postgres_backup.tar.gz /data

# Restore backup
docker run --rm -v elcafe_postgres_data:/data -v $(pwd):/backup ubuntu tar xzf /backup/postgres_backup.tar.gz -C /
```

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
- [ ] Generated secure JWT_SECRET (32+ chars)
- [ ] Set CORS_ORIGINS to production domains only
- [ ] SSL certificates installed (for production)
- [ ] Firewall configured (only 80, 443 open)
- [ ] Regular backups scheduled
- [ ] Database password rotated regularly

## Monitoring

### View Resource Usage
```bash
docker stats
```

### Check Container Health
```bash
docker-compose ps
```

### Export Logs
```bash
docker-compose logs > logs_$(date +%Y%m%d).txt
```

## Scaling (Future)

To run multiple backend instances:
```yaml
backend:
  deploy:
    replicas: 3
```

Add load balancer in frontend nginx config.

---

## Support

For issues:
1. Check logs: `docker-compose logs -f`
2. Verify all services running: `docker-compose ps`
3. Check environment variables: `cat .env.docker`
4. Review this guide

---

**Last Updated:** 2025-12-20
