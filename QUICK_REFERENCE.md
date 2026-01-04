# ElCafe Quick Reference Guide

## Quick Start Commands

### Initial Deployment
```bash
# 1. Clone repository
git clone https://github.com/Furqatbek/elcafe.git /opt/elcafe
cd /opt/elcafe

# 2. Create environment file
cp .env.example .env
nano .env  # Edit with your values

# 3. Run deployment script
chmod +x deploy.sh
./deploy.sh
```

---

## Service Management

### Backend Service
```bash
# Start
sudo systemctl start elcafe-backend

# Stop
sudo systemctl stop elcafe-backend

# Restart
sudo systemctl restart elcafe-backend

# Status
sudo systemctl status elcafe-backend

# Enable on boot
sudo systemctl enable elcafe-backend

# View logs (real-time)
sudo journalctl -u elcafe-backend -f

# View last 100 lines
sudo journalctl -u elcafe-backend -n 100
```

### NGINX
```bash
# Test configuration
sudo nginx -t

# Reload
sudo systemctl reload nginx

# Restart
sudo systemctl restart nginx

# Status
sudo systemctl status nginx

# Access logs
sudo tail -f /var/log/nginx/access.log

# Error logs
sudo tail -f /var/log/nginx/error.log
```

### PostgreSQL
```bash
# Status
sudo systemctl status postgresql

# Restart
sudo systemctl restart postgresql

# Connect to database
sudo -u postgres psql elcafe_prod

# View logs
sudo tail -f /var/log/postgresql/postgresql-14-main.log
```

---

## Application Updates

### Update Backend
```bash
cd /opt/elcafe
git pull
./mvnw clean package -DskipTests
sudo systemctl restart elcafe-backend
```

### Update Frontend
```bash
cd /opt/elcafe/frontend
git pull
npm install
npm run build
sudo cp -r dist/* /var/www/mayamicafe.uz/
```

### Full Update (Both)
```bash
cd /opt/elcafe
git pull
./deploy.sh
```

---

## Database Operations

### Backup Database
```bash
# Create backup
sudo -u postgres pg_dump elcafe_prod > backup_$(date +%Y%m%d_%H%M%S).sql

# Create compressed backup
sudo -u postgres pg_dump elcafe_prod | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

### Restore Database
```bash
# From SQL file
sudo -u postgres psql elcafe_prod < backup_20231220.sql

# From compressed file
gunzip -c backup_20231220.sql.gz | sudo -u postgres psql elcafe_prod
```

### Check Database Size
```bash
sudo -u postgres psql -c "SELECT pg_size_pretty(pg_database_size('elcafe_prod'));"
```

---

## Logs & Monitoring

### View Application Logs
```bash
# Backend application logs
tail -f /var/log/elcafe/application.log

# Backend service logs
sudo journalctl -u elcafe-backend -f

# NGINX access logs
sudo tail -f /var/log/nginx/access.log

# NGINX error logs
sudo tail -f /var/log/nginx/error.log
```

### System Monitoring
```bash
# System resources
htop

# Disk usage
df -h

# Memory usage
free -h

# Process list
ps aux | grep java
ps aux | grep nginx

# Port usage
sudo lsof -i :8080
sudo lsof -i :80
sudo lsof -i :443
```

---

## Health Checks

### Backend Health
```bash
# Health endpoint
curl http://localhost:8080/actuator/health

# API health (via NGINX)
curl https://mayamicafe.uz/api/v1/actuator/health
```

### Service Status
```bash
# All services at once
sudo systemctl status elcafe-backend nginx postgresql
```

---

## SSL Certificate Management

### Renew Certificate
```bash
# Manual renewal
sudo certbot renew

# Test renewal
sudo certbot renew --dry-run

# Force renewal
sudo certbot renew --force-renewal
```

### Check Certificate Expiry
```bash
# Check expiry date
sudo certbot certificates

# Or check via OpenSSL
echo | openssl s_client -servername mayamicafe.uz -connect mayamicafe.uz:443 2>/dev/null | openssl x509 -noout -dates
```

---

## Troubleshooting

### Backend Issues
```bash
# Check if backend is running
sudo systemctl status elcafe-backend

# View recent errors
sudo journalctl -u elcafe-backend -n 100 --no-pager

# Check Java process
ps aux | grep java

# Check port 8080
sudo lsof -i :8080
```

### Database Connection Issues
```bash
# Test database connection
psql -h localhost -U elcafe_user -d elcafe_prod

# Check PostgreSQL is running
sudo systemctl status postgresql

# Check PostgreSQL connections
sudo -u postgres psql -c "SELECT * FROM pg_stat_activity WHERE datname='elcafe_prod';"
```

### NGINX Issues
```bash
# Test configuration
sudo nginx -t

# Check if NGINX is running
sudo systemctl status nginx

# Check port 80/443
sudo lsof -i :80
sudo lsof -i :443

# View error logs
sudo tail -f /var/log/nginx/error.log
```

### Permission Issues
```bash
# Fix backend ownership
sudo chown -R elcafe:elcafe /opt/elcafe
sudo chown -R elcafe:elcafe /var/log/elcafe

# Fix frontend ownership
sudo chown -R www-data:www-data /var/www/mayamicafe.uz

# Fix upload directory
sudo chown -R elcafe:elcafe /opt/elcafe/uploads
```

---

## Environment Variables

### View Current Environment
```bash
# View .env file
cat /opt/elcafe/.env

# Check what backend sees (while running)
sudo systemctl show elcafe-backend --property=Environment
```

### Update Environment Variables
```bash
# Edit .env file
sudo nano /opt/elcafe/.env

# Restart backend to apply changes
sudo systemctl restart elcafe-backend
```

---

## Security

### Firewall (UFW)
```bash
# Check status
sudo ufw status

# Allow port
sudo ufw allow 443/tcp

# Deny port
sudo ufw deny 8080/tcp

# Enable
sudo ufw enable
```

### Check Open Ports
```bash
# All open ports
sudo netstat -tuln

# Specific port
sudo lsof -i :8080
```

---

## Performance Tuning

### Check JVM Memory
```bash
# Current usage
ps aux | grep java

# Detailed memory info
jcmd $(pgrep -f elcafe) VM.native_memory summary
```

### Database Performance
```bash
# Active queries
sudo -u postgres psql elcafe_prod -c "SELECT pid, query, state FROM pg_stat_activity WHERE state = 'active';"

# Slow queries
sudo -u postgres psql elcafe_prod -c "SELECT query, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;"
```

---

## Maintenance

### Clean Old Logs
```bash
# Clean journal logs older than 7 days
sudo journalctl --vacuum-time=7d

# Clean NGINX logs
sudo find /var/log/nginx -name "*.gz" -mtime +30 -delete
```

### Clean Old Backups
```bash
# Delete backups older than 30 days
find /backups -name "*.sql" -mtime +30 -delete
```

### Update System
```bash
# Update packages
sudo apt update
sudo apt upgrade -y

# Restart if kernel updated
sudo reboot
```

---

## Common Tasks

### Change Database Password
```bash
# 1. Change in PostgreSQL
sudo -u postgres psql
ALTER USER elcafe_user WITH PASSWORD 'new_password';
\q

# 2. Update .env file
sudo nano /opt/elcafe/.env
# Change DB_PASSWORD=new_password

# 3. Restart backend
sudo systemctl restart elcafe-backend
```

### Add New CORS Origin
```bash
# Edit application.yml or .env
sudo nano /opt/elcafe/src/main/resources/application.yml
# or
sudo nano /opt/elcafe/.env

# Rebuild and restart
cd /opt/elcafe
./mvnw clean package -DskipTests
sudo systemctl restart elcafe-backend
```

### Check Application Version
```bash
# Backend version
grep "version" /opt/elcafe/pom.xml | head -1

# Frontend version
grep "version" /opt/elcafe/frontend/package.json | head -1
```

---

## Emergency Procedures

### Rollback Backend
```bash
# 1. Stop service
sudo systemctl stop elcafe-backend

# 2. Restore old JAR file
sudo cp /opt/elcafe/target/restaurant-delivery-service-1.0.0.jar.backup \
       /opt/elcafe/target/restaurant-delivery-service-1.0.0.jar

# 3. Start service
sudo systemctl start elcafe-backend
```

### Rollback Frontend
```bash
# Restore from backup
sudo rm -rf /var/www/mayamicafe.uz
sudo cp -r /var/www/mayamicafe.uz.backup.* /var/www/mayamicafe.uz
sudo chown -R www-data:www-data /var/www/mayamicafe.uz
```

### Restore Database
```bash
# 1. Stop backend
sudo systemctl stop elcafe-backend

# 2. Restore database
sudo -u postgres psql elcafe_prod < /backups/backup_latest.sql

# 3. Start backend
sudo systemctl start elcafe-backend
```

---

## Support Contacts

- **System Administrator**: [your-email@mayamicafe.uz]
- **Database Admin**: [db-admin@mayamicafe.uz]
- **Developer**: [dev@mayamicafe.uz]

---

## Important Files & Directories

| Path | Description |
|------|-------------|
| `/opt/elcafe` | Application directory |
| `/opt/elcafe/.env` | Environment variables |
| `/opt/elcafe/target/*.jar` | Backend JAR file |
| `/var/www/mayamicafe.uz` | Frontend files |
| `/var/log/elcafe` | Application logs |
| `/etc/nginx/sites-available/mayamicafe.uz` | NGINX config |
| `/etc/systemd/system/elcafe-backend.service` | Backend service |
| `/etc/letsencrypt/live/mayamicafe.uz` | SSL certificates |
| `/backups` | Database backups |

---

**Last Updated:** 2025-12-20
