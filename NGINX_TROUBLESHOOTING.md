# NGINX Production Troubleshooting Guide

## Quick Diagnostics

Run these commands on your server to diagnose the issue:

### 1. Check if Docker containers are running
```bash
docker ps
```

**Expected output:**
```
CONTAINER ID   IMAGE              STATUS         PORTS
xxxxx         elcafe-frontend    Up 5 minutes   0.0.0.0:9090->80/tcp
xxxxx         elcafe-backend     Up 5 minutes   0.0.0.0:8080->8080/tcp
xxxxx         postgres:15        Up 5 minutes   0.0.0.0:5432->5432/tcp
xxxxx         redis:7            Up 5 minutes   0.0.0.0:6379->6379/tcp
```

**If containers are not running:**
```bash
cd /opt/elcafe
docker-compose up -d
```

### 2. Check if NGINX is installed and running
```bash
sudo systemctl status nginx
```

**If NGINX is not installed:**
```bash
sudo apt update
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

**If NGINX is not running:**
```bash
sudo systemctl start nginx
```

### 3. Check if NGINX config is in the right place
```bash
ls -la /etc/nginx/sites-available/lacasa.uz
ls -la /etc/nginx/sites-enabled/lacasa.uz
```

**If files don't exist:**
```bash
# Copy the HTTP-only config first (for testing)
sudo cp /opt/elcafe/nginx-production-http.conf /etc/nginx/sites-available/lacasa.uz

# Enable the site
sudo ln -sf /etc/nginx/sites-available/lacasa.uz /etc/nginx/sites-enabled/

# Remove default site
sudo rm /etc/nginx/sites-enabled/default

# Test config
sudo nginx -t

# Reload NGINX
sudo systemctl reload nginx
```

### 4. Test NGINX configuration
```bash
sudo nginx -t
```

**Expected output:**
```
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

**If there are errors:**
- Check the error message
- Look for SSL certificate paths if using HTTPS config
- Verify syntax

### 5. Check if ports are accessible
```bash
# Test if backend is responding
curl http://localhost:8080/actuator/health

# Test if frontend is responding
curl http://localhost:9090

# Test via NGINX
curl http://localhost/api/actuator/health
```

### 6. Check firewall
```bash
sudo ufw status
```

**If ports are blocked:**
```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 3000/tcp
sudo ufw reload
```

### 7. View NGINX error logs
```bash
sudo tail -f /var/log/nginx/error.log
sudo tail -f /var/log/nginx/lacasa-error.log
```

---

## Common Issues & Solutions

### Issue 1: "Connection Refused" or "502 Bad Gateway"

**Cause:** Docker containers are not running

**Solution:**
```bash
cd /opt/elcafe
docker-compose ps  # Check status
docker-compose up -d  # Start containers
docker-compose logs -f  # View logs
```

### Issue 2: "404 Not Found" for all pages

**Cause:** NGINX config not loaded or incorrect

**Solution:**
```bash
# Use HTTP-only config first
sudo cp /opt/elcafe/nginx-production-http.conf /etc/nginx/sites-available/lacasa.uz
sudo nginx -t
sudo systemctl reload nginx
```

### Issue 3: "SSL Certificate Error" or HTTPS not working

**Cause:** SSL certificates not installed

**Solution:** Use HTTP-only config first (nginx-production-http.conf), then:
```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx -y

# Get SSL certificate
sudo certbot --nginx -d lacasa.uz -d www.lacasa.uz

# Certbot will automatically update your NGINX config
```

### Issue 4: Can access via IP but not domain

**Cause:** DNS not configured

**Solution:**
- Check DNS records with: `nslookup lacasa.uz`
- DNS A record should point to your server IP
- Wait for DNS propagation (can take up to 48 hours)

### Issue 5: "Unable to connect" or "This site can't be reached"

**Causes:**
1. Server is down
2. Firewall blocking
3. DNS not configured
4. NGINX not running

**Solution:**
```bash
# Check each layer:
ping lacasa.uz  # Test connectivity
nslookup lacasa.uz  # Test DNS
telnet lacasa.uz 80  # Test port 80
curl http://lacasa.uz  # Test HTTP

# Check if server is listening
sudo netstat -tulpn | grep :80
sudo netstat -tulpn | grep :9090
sudo netstat -tulpn | grep :8080
```

---

## Step-by-Step Setup (Fresh Server)

### Step 1: Install Docker & Docker Compose
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
newgrp docker

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### Step 2: Install NGINX
```bash
sudo apt update
sudo apt install nginx -y
sudo systemctl enable nginx
sudo systemctl start nginx
```

### Step 3: Clone and Deploy Application
```bash
cd /opt
sudo mkdir elcafe
sudo chown $USER:$USER elcafe
git clone https://github.com/Furqatbek/elcafe.git elcafe
cd elcafe

# Configure environment
cp .env.docker .env.docker
nano .env.docker  # Set DB_PASSWORD and JWT_SECRET

# Start Docker containers
docker-compose up -d

# Wait for services to start (30 seconds)
sleep 30

# Check status
docker-compose ps
```

### Step 4: Configure NGINX (HTTP first)
```bash
# Copy HTTP config
sudo cp /opt/elcafe/nginx-production-http.conf /etc/nginx/sites-available/lacasa.uz

# Enable site
sudo ln -sf /etc/nginx/sites-available/lacasa.uz /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# Test config
sudo nginx -t

# Reload NGINX
sudo systemctl reload nginx
```

### Step 5: Test Everything
```bash
# Test Docker services
curl http://localhost:8080/actuator/health  # Backend
curl http://localhost:9090  # Frontend

# Test via NGINX
curl http://localhost/api/actuator/health  # API via NGINX
curl http://localhost/  # Frontend via NGINX

# Test from outside (replace with your domain/IP)
curl http://lacasa.uz/api/actuator/health
```

### Step 6: Configure Firewall
```bash
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS (for later)
sudo ufw allow 3000/tcp # Admin panel
sudo ufw enable
```

### Step 7: Add SSL (Optional, after HTTP works)
```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx -y

# Get certificate
sudo certbot --nginx -d lacasa.uz -d www.lacasa.uz

# Test auto-renewal
sudo certbot renew --dry-run
```

---

## Testing URLs

After setup, test these URLs in your browser:

### Frontend
```
http://lacasa.uz/
http://www.lacasa.uz/
```

### Backend API
```
http://lacasa.uz/api/actuator/health
```

### Admin Panel (if configured)
```
http://lacasa.uz:3000/
```

---

## Verification Checklist

- [ ] Docker containers running: `docker-compose ps`
- [ ] NGINX installed: `sudo systemctl status nginx`
- [ ] NGINX config correct: `sudo nginx -t`
- [ ] Config enabled: `ls /etc/nginx/sites-enabled/lacasa.uz`
- [ ] Firewall configured: `sudo ufw status`
- [ ] Backend healthy: `curl http://localhost:8080/actuator/health`
- [ ] Frontend accessible: `curl http://localhost:9090`
- [ ] NGINX proxying works: `curl http://localhost/api/actuator/health`
- [ ] Domain resolves: `nslookup lacasa.uz`
- [ ] Website accessible: Open http://lacasa.uz in browser

---

## Emergency Quick Fix

If nothing works, try this minimal setup:

```bash
# 1. Start Docker
cd /opt/elcafe
docker-compose down
docker-compose up -d

# 2. Wait 30 seconds
sleep 30

# 3. Test direct access (bypass NGINX)
curl http://YOUR_SERVER_IP:9090  # Should show frontend
curl http://YOUR_SERVER_IP:8080/actuator/health  # Should show {"status":"UP"}

# If above works, the issue is with NGINX
# 4. Reset NGINX config to basic
sudo rm /etc/nginx/sites-enabled/*
sudo cp /opt/elcafe/nginx-production-http.conf /etc/nginx/sites-available/lacasa.uz
sudo ln -s /etc/nginx/sites-available/lacasa.uz /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx

# 5. Test again
curl http://localhost/
curl http://localhost/api/actuator/health
```

---

## Support Commands

### View all logs together
```bash
# Docker logs
docker-compose logs -f --tail=50

# NGINX logs
sudo tail -f /var/log/nginx/error.log

# System logs
sudo journalctl -u nginx -f
```

### Full system status
```bash
echo "=== Docker Containers ==="
docker-compose ps

echo "=== NGINX Status ==="
sudo systemctl status nginx

echo "=== NGINX Test ==="
sudo nginx -t

echo "=== Ports Listening ==="
sudo netstat -tulpn | grep -E ':(80|443|3000|8080|9090)'

echo "=== Firewall Status ==="
sudo ufw status

echo "=== Health Checks ==="
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:9090 | head -c 100
```

---

## TLS certificate renewal (Let's Encrypt)

The site's cert lives at `/etc/letsencrypt/live/qahvoon.uz/` on the host; the
`elcafe-nginx-proxy` container mounts it read-only. Renewal runs via certbot on
the host using the **webroot** method, so it happens with nginx running (no
downtime).

### How it works
- The production nginx config serves the ACME challenge from `/var/www/certbot`
  (`location /.well-known/acme-challenge/`).
- `docker-compose.yml` mounts the host's `/var/www/certbot` into the nginx
  container, so challenge files certbot writes on the host are served by nginx.
- A deploy hook reloads nginx after each renewal so it serves the new cert.

### One-time setup on the host
```bash
# 1. Webroot dir certbot writes challenges into (served by nginx)
sudo mkdir -p /var/www/certbot

# 2. Make sure the container mounts it (redeploy after pulling this change)
docker compose up -d nginx-proxy

# 3. Point the cert's renewal config at the webroot authenticator
#    (replaces the standalone method, which needs port 80 free and causes
#    downtime). Run while nginx is up and serving :80.
sudo certbot certonly --webroot -w /var/www/certbot \
    -d qahvoon.uz -d www.qahvoon.uz

# 4. Install the reload-on-renew deploy hook
sudo install -m 0755 scripts/certbot-deploy-hook.sh \
    /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

# 5. Verify the whole renewal path end-to-end (no changes made)
sudo certbot renew --dry-run
sudo systemctl status certbot.timer   # confirm the auto-renew timer is active
```

After this, certbot's scheduled timer renews ~30 days before expiry and reloads
nginx automatically — no manual steps.

### Emergency manual renewal
If the cert has already expired and you just need the site back now:
```bash
docker stop elcafe-nginx-proxy
sudo certbot certonly --standalone -d qahvoon.uz -d www.qahvoon.uz --force-renewal
docker start elcafe-nginx-proxy
```
Then do the one-time setup above so it doesn't recur (standalone leaves the
renewal config needing port 80, which nginx holds — auto-renewal will fail).

---

**Last Updated:** 2026-07-24
