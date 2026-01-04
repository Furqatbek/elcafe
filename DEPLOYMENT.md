# ElCafe Production Deployment Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Server Setup](#server-setup)
3. [Database Setup](#database-setup)
4. [Backend Deployment](#backend-deployment)
5. [Frontend Deployment](#frontend-deployment)
6. [NGINX Configuration](#nginx-configuration)
7. [SSL Certificate Setup](#ssl-certificate-setup)
8. [Environment Variables](#environment-variables)
9. [Running the Application](#running-the-application)
10. [Monitoring & Logs](#monitoring--logs)

---

## Prerequisites

### Required Software
- **Ubuntu Server 20.04 LTS or newer** (recommended)
- **Java 17** or newer
- **PostgreSQL 14+**
- **Node.js 18+** and npm
- **NGINX** (as reverse proxy)
- **Git**
- **Certbot** (for SSL certificates)

### Domain Setup
Ensure your domain is configured:
- `mayamicafe.uz` → Your server IP
- `www.mayamicafe.uz` → Your server IP
- `api.mayamicafe.uz` → Your server IP (optional, for API subdomain)

---

## Server Setup

### 1. Update System
```bash
sudo apt update
sudo apt upgrade -y
```

### 2. Install Java 17
```bash
sudo apt install openjdk-17-jdk -y
java -version
```

### 3. Install PostgreSQL
```bash
sudo apt install postgresql postgresql-contrib -y
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### 4. Install Node.js & npm
```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs
node --version
npm --version
```

### 5. Install NGINX
```bash
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

### 6. Install Git
```bash
sudo apt install git -y
```

---

## Database Setup

### 1. Create Database and User
```bash
sudo -u postgres psql
```

```sql
-- Create database
CREATE DATABASE elcafe_prod;

-- Create user with password
CREATE USER elcafe_user WITH PASSWORD 'your_secure_password_here';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE elcafe_prod TO elcafe_user;

-- Grant schema privileges (PostgreSQL 15+)
\c elcafe_prod
GRANT ALL ON SCHEMA public TO elcafe_user;

-- Exit
\q
```

### 2. Configure PostgreSQL for Remote Access (if needed)
Edit `/etc/postgresql/14/main/postgresql.conf`:
```bash
sudo nano /etc/postgresql/14/main/postgresql.conf
```

Change:
```
listen_addresses = 'localhost'
```

Edit `/etc/postgresql/14/main/pg_hba.conf`:
```bash
sudo nano /etc/postgresql/14/main/pg_hba.conf
```

Add:
```
host    elcafe_prod    elcafe_user    127.0.0.1/32    md5
```

Restart PostgreSQL:
```bash
sudo systemctl restart postgresql
```

---

## Backend Deployment

### 1. Clone Repository
```bash
cd /opt
sudo mkdir elcafe
sudo chown $USER:$USER elcafe
cd elcafe
git clone https://github.com/Furqatbek/elcafe.git .
```

### 2. Create Production Configuration
Create `/opt/elcafe/application-prod.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/elcafe_prod
    username: elcafe_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true

server:
  port: 8080

app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      access-token-expiration: 3600000
      refresh-token-expiration: 86400000
    cors:
      allowed-origins: https://mayamicafe.uz,https://www.mayamicafe.uz
      allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
      allowed-headers: '*'
      allow-credentials: true

logging:
  level:
    root: INFO
    com.elcafe: INFO
  file:
    name: /var/log/elcafe/application.log
```

### 3. Create Environment File
Create `/opt/elcafe/.env`:
```bash
# Database
DB_PASSWORD=your_secure_database_password

# JWT Secret (generate with: openssl rand -hex 32)
JWT_SECRET=your_super_secret_jwt_key_minimum_256_bits

# Optional: Override CORS origins
# CORS_ORIGINS=https://mayamicafe.uz,https://www.mayamicafe.uz

# Spring Profile
SPRING_PROFILES_ACTIVE=prod
```

### 4. Create Log Directory
```bash
sudo mkdir -p /var/log/elcafe
sudo chown $USER:$USER /var/log/elcafe
```

### 5. Build Backend
```bash
cd /opt/elcafe
./mvnw clean package -DskipTests
```

### 6. Create Systemd Service
Create `/etc/systemd/system/elcafe-backend.service`:
```bash
sudo nano /etc/systemd/system/elcafe-backend.service
```

```ini
[Unit]
Description=ElCafe Backend Service
After=postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=elcafe
Group=elcafe
WorkingDirectory=/opt/elcafe
EnvironmentFile=/opt/elcafe/.env
ExecStart=/usr/bin/java -jar /opt/elcafe/target/restaurant-delivery-service-1.0.0.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=elcafe-backend

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/log/elcafe /opt/elcafe/uploads

[Install]
WantedBy=multi-user.target
```

### 7. Create Service User
```bash
sudo useradd -r -s /bin/false elcafe
sudo chown -R elcafe:elcafe /opt/elcafe
sudo chown -R elcafe:elcafe /var/log/elcafe
```

### 8. Enable and Start Service
```bash
sudo systemctl daemon-reload
sudo systemctl enable elcafe-backend
sudo systemctl start elcafe-backend
sudo systemctl status elcafe-backend
```

---

## Frontend Deployment

### 1. Build Frontend
```bash
cd /opt/elcafe/frontend
```

Create `.env.production`:
```env
VITE_API_URL=https://mayamicafe.uz/api
# or if using subdomain:
# VITE_API_URL=https://api.mayamicafe.uz/api
```

Build:
```bash
npm install
npm run build
```

### 2. Deploy Built Files
```bash
sudo mkdir -p /var/www/mayamicafe.uz
sudo cp -r dist/* /var/www/mayamicafe.uz/
sudo chown -R www-data:www-data /var/www/mayamicafe.uz
```

---

## NGINX Configuration

### 1. Create NGINX Configuration
Create `/etc/nginx/sites-available/mayamicafe.uz`:
```bash
sudo nano /etc/nginx/sites-available/mayamicafe.uz
```

```nginx
# HTTP to HTTPS redirect
server {
    listen 80;
    listen [::]:80;
    server_name mayamicafe.uz www.mayamicafe.uz;

    # Allow Let's Encrypt verification
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # Redirect all HTTP to HTTPS
    location / {
        return 301 https://$server_name$request_uri;
    }
}

# HTTPS - Main Application
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name mayamicafe.uz www.mayamicafe.uz;

    # SSL certificates (will be configured by certbot)
    ssl_certificate /etc/letsencrypt/live/mayamicafe.uz/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mayamicafe.uz/privkey.pem;

    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/xml text/javascript application/x-javascript application/xml+rss application/json application/javascript;

    # Frontend - React SPA
    location / {
        root /var/www/mayamicafe.uz;
        try_files $uri $uri/ /index.html;
        expires 1h;
        add_header Cache-Control "public, must-revalidate, proxy-revalidate";
    }

    # Static assets caching
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        root /var/www/mayamicafe.uz;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Backend API proxy
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_http_version 1.1;

        # Headers
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;

        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;

        # WebSocket support (if needed)
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Backend uploads
    location /uploads/ {
        proxy_pass http://localhost:8080/uploads/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Backend actuator (restrict in production)
    location /actuator/ {
        # Uncomment to restrict access
        # allow 10.0.0.0/8;  # Your internal network
        # deny all;

        proxy_pass http://localhost:8080/actuator/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # API docs (optional, remove in production if not needed)
    location ~ ^/(swagger-ui|api-docs)/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Max upload size
    client_max_body_size 10M;
}
```

### 2. Enable Site
```bash
sudo ln -s /etc/nginx/sites-available/mayamicafe.uz /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## SSL Certificate Setup

### 1. Install Certbot
```bash
sudo apt install certbot python3-certbot-nginx -y
```

### 2. Create Certbot Directory
```bash
sudo mkdir -p /var/www/certbot
```

### 3. Obtain SSL Certificate
```bash
sudo certbot --nginx -d mayamicafe.uz -d www.mayamicafe.uz
```

Follow the prompts:
- Enter email address
- Agree to terms of service
- Choose whether to redirect HTTP to HTTPS (recommended: Yes)

### 4. Auto-Renewal
Certbot automatically sets up renewal. Test it:
```bash
sudo certbot renew --dry-run
```

---

## Environment Variables

### Backend Environment Variables (`/opt/elcafe/.env`)
```bash
# Required
DB_PASSWORD=your_secure_database_password
JWT_SECRET=your_super_secret_jwt_key_minimum_256_bits

# Optional
SPRING_PROFILES_ACTIVE=prod
CORS_ORIGINS=https://mayamicafe.uz,https://www.mayamicafe.uz

# Database (if not using default)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=elcafe_prod
DB_USERNAME=elcafe_user

# Server
SERVER_PORT=8080

# File upload settings
MAX_FILE_SIZE=10MB
MAX_REQUEST_SIZE=10MB
```

### Generate Secure JWT Secret
```bash
openssl rand -hex 32
```

---

## Running the Application

### 1. Start Backend
```bash
sudo systemctl start elcafe-backend
sudo systemctl status elcafe-backend
```

### 2. Verify Backend is Running
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

### 3. Check Frontend
Open browser: `https://mayamicafe.uz`

### 4. Test API
```bash
curl https://mayamicafe.uz/api/v1/actuator/health
```

---

## Monitoring & Logs

### Backend Logs
```bash
# View logs
sudo journalctl -u elcafe-backend -f

# View application logs
tail -f /var/log/elcafe/application.log

# View last 100 lines
sudo journalctl -u elcafe-backend -n 100
```

### NGINX Logs
```bash
# Access logs
sudo tail -f /var/log/nginx/access.log

# Error logs
sudo tail -f /var/log/nginx/error.log
```

### PostgreSQL Logs
```bash
sudo tail -f /var/log/postgresql/postgresql-14-main.log
```

### System Resources
```bash
# CPU and Memory
htop

# Disk usage
df -h

# Service status
sudo systemctl status elcafe-backend nginx postgresql
```

---

## Maintenance Commands

### Update Application
```bash
# Pull latest code
cd /opt/elcafe
git pull

# Rebuild backend
./mvnw clean package -DskipTests

# Restart service
sudo systemctl restart elcafe-backend

# Rebuild frontend
cd frontend
npm install
npm run build
sudo cp -r dist/* /var/www/mayamicafe.uz/
```

### Database Backup
```bash
# Create backup
sudo -u postgres pg_dump elcafe_prod > backup_$(date +%Y%m%d_%H%M%S).sql

# Restore backup
sudo -u postgres psql elcafe_prod < backup_20231220_120000.sql
```

### Restart Services
```bash
sudo systemctl restart elcafe-backend
sudo systemctl restart nginx
sudo systemctl restart postgresql
```

---

## Troubleshooting

### Backend Won't Start
```bash
# Check logs
sudo journalctl -u elcafe-backend -n 50

# Check if port 8080 is in use
sudo lsof -i :8080

# Verify Java version
java -version
```

### Database Connection Issues
```bash
# Test database connection
psql -h localhost -U elcafe_user -d elcafe_prod

# Check PostgreSQL status
sudo systemctl status postgresql
```

### NGINX Issues
```bash
# Test configuration
sudo nginx -t

# Check error logs
sudo tail -f /var/log/nginx/error.log
```

### Permission Issues
```bash
# Fix ownership
sudo chown -R elcafe:elcafe /opt/elcafe
sudo chown -R elcafe:elcafe /var/log/elcafe
sudo chown -R www-data:www-data /var/www/mayamicafe.uz
```

---

## Security Checklist

- [ ] Strong database password set
- [ ] JWT secret is unique and secure (32+ characters)
- [ ] PostgreSQL only listens on localhost
- [ ] Firewall configured (UFW)
- [ ] SSL certificates installed and auto-renewing
- [ ] CORS origins restricted to production domains
- [ ] File upload size limits configured
- [ ] Actuator endpoints restricted
- [ ] Regular backups scheduled
- [ ] System updates automated
- [ ] Application logs monitored
- [ ] Swagger UI disabled or restricted in production

### Configure Firewall (UFW)
```bash
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw enable
sudo ufw status
```

---

## Production Checklist

Before going live:
- [ ] All environment variables configured
- [ ] Database migrations run successfully
- [ ] SSL certificates installed
- [ ] CORS origins set to production domains only
- [ ] File upload directory permissions correct
- [ ] Logs directory exists and writable
- [ ] Backend service starts on boot
- [ ] NGINX starts on boot
- [ ] Health check endpoints responding
- [ ] API accessible via HTTPS
- [ ] Frontend loads correctly
- [ ] Static assets cached properly
- [ ] Database backups scheduled
- [ ] Monitoring set up
- [ ] Error tracking configured
- [ ] Documentation updated

---

## Additional Recommendations

### 1. Set up Monitoring
Consider using:
- **Prometheus + Grafana** for metrics
- **ELK Stack** (Elasticsearch, Logstash, Kibana) for logs
- **Sentry** for error tracking
- **UptimeRobot** or **Pingdom** for uptime monitoring

### 2. Database Backups
Set up automated daily backups:
```bash
sudo crontab -e
```

Add:
```
0 2 * * * /usr/bin/pg_dump -U postgres elcafe_prod > /backups/elcafe_$(date +\%Y\%m\%d).sql
```

### 3. Log Rotation
NGINX and systemd journals rotate automatically, but configure application logs:
```bash
sudo nano /etc/logrotate.d/elcafe
```

```
/var/log/elcafe/*.log {
    daily
    rotate 14
    compress
    delaycompress
    notifempty
    create 0640 elcafe elcafe
    sharedscripts
}
```

---

## Support

For issues or questions:
- Check logs first
- Review this deployment guide
- Consult Spring Boot documentation
- Check PostgreSQL logs
- Review NGINX error logs

---

**Last Updated:** 2025-12-20
