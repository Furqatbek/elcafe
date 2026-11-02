#!/bin/bash

# Qahvoon Production Deployment Script
# This script automates the deployment process for the Qahvoon application.
#
# The brand moved from ElCafe/Jangirovs to Qahvoon (see V128) but this script
# was never updated: it announced ElCafe and published the frontend to
# /var/www/lacasa.uz, a domain that appears nowhere else in the project. Every
# nginx config serves qahvoon.uz. A deploy therefore copied the new build into a
# directory nginx does not read and reported success, leaving the live site on
# the old files.
#
# BRAND / DOMAIN are cosmetic. WEB_DIR is not: it is the directory nginx serves
# the frontend from, and the deploy verifies it against the running nginx config
# before touching anything. Override any of them from the environment, e.g.
#   WEB_DIR=/srv/qahvoon ./deploy.sh
#
# The systemd unit stays elcafe-backend — that is the unit name installed on the
# server, and renaming it here would simply fail to find the service.

set -e  # Exit on error

BRAND="${BRAND:-Qahvoon}"
DOMAIN="${DOMAIN:-qahvoon.uz}"
WEB_DIR="${WEB_DIR:-/var/www/${DOMAIN}}"
BACKEND_SERVICE="${BACKEND_SERVICE:-elcafe-backend}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  ${BRAND} Production Deployment Script  ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if running as root
if [[ $EUID -eq 0 ]]; then
   echo -e "${RED}This script should NOT be run as root${NC}"
   exit 1
fi

# Function to print section headers
print_section() {
    echo -e "\n${YELLOW}>>> $1${NC}\n"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
print_section "Checking Prerequisites"

if ! command_exists java; then
    echo -e "${RED}Java is not installed. Please install Java 17+${NC}"
    exit 1
fi

if ! command_exists psql; then
    echo -e "${RED}PostgreSQL is not installed. Please install PostgreSQL 14+${NC}"
    exit 1
fi

if ! command_exists node; then
    echo -e "${RED}Node.js is not installed. Please install Node.js 18+${NC}"
    exit 1
fi

if ! command_exists nginx; then
    echo -e "${RED}NGINX is not installed. Please install NGINX${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All prerequisites met${NC}"

# Get deployment type
echo ""
read -p "Deploy (1) Backend only, (2) Frontend only, or (3) Both? [1/2/3]: " DEPLOY_TYPE

# Backend deployment
if [ "$DEPLOY_TYPE" == "1" ] || [ "$DEPLOY_TYPE" == "3" ]; then
    print_section "Building Backend"

    # Clean and build
    echo "Running Maven build..."
    ./mvnw clean package -DskipTests

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Backend build successful${NC}"
    else
        echo -e "${RED}✗ Backend build failed${NC}"
        exit 1
    fi

    # Restart service
    print_section "Restarting Backend Service"

    if sudo systemctl is-active --quiet ${BACKEND_SERVICE}; then
        echo "Stopping backend service..."
        sudo systemctl stop ${BACKEND_SERVICE}
    fi

    echo "Starting backend service..."
    sudo systemctl start ${BACKEND_SERVICE}

    # Wait for service to start
    sleep 5

    # Check status
    if sudo systemctl is-active --quiet ${BACKEND_SERVICE}; then
        echo -e "${GREEN}✓ Backend service started successfully${NC}"

        # Test health endpoint
        sleep 2
        HEALTH=$(curl -s http://localhost:8080/actuator/health | grep -o '"status":"UP"' || echo "")
        if [ -n "$HEALTH" ]; then
            echo -e "${GREEN}✓ Backend health check passed${NC}"
        else
            echo -e "${YELLOW}⚠ Backend is running but health check failed${NC}"
        fi
    else
        echo -e "${RED}✗ Backend service failed to start${NC}"
        echo "Check logs: sudo journalctl -u ${BACKEND_SERVICE} -n 50"
        exit 1
    fi
fi

# Frontend deployment
if [ "$DEPLOY_TYPE" == "2" ] || [ "$DEPLOY_TYPE" == "3" ]; then
    print_section "Building Frontend"

    cd frontend

    # Install dependencies
    echo "Installing npm dependencies..."
    npm install

    # Build
    echo "Building frontend..."
    npm run build

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Frontend build successful${NC}"
    else
        echo -e "${RED}✗ Frontend build failed${NC}"
        exit 1
    fi

    # Deploy to web directory
    print_section "Deploying Frontend Files"

    # Confirm nginx actually serves from WEB_DIR before moving anything aside.
    # Publishing to the wrong directory is silent — the build lands somewhere
    # unread, the script reports success and the live site never changes — and
    # the mv below would shunt away whatever happened to be sitting there.
    if command_exists nginx && sudo nginx -T >/dev/null 2>&1; then
        if sudo nginx -T 2>/dev/null | grep -qF "$WEB_DIR"; then
            echo -e "${GREEN}✓ nginx serves ${WEB_DIR}${NC}"
        else
            echo -e "${RED}✗ nginx has no reference to ${WEB_DIR}${NC}"
            echo ""
            echo "Publishing there would not change the live site. Document roots"
            echo "nginx is currently configured with:"
            sudo nginx -T 2>/dev/null | grep -E "^\s*(root|server_name)" | sed 's/^/    /' | sort -u
            echo ""
            echo "Re-run with the right one, e.g.  WEB_DIR=/var/www/${DOMAIN} ./deploy.sh"
            echo "If the frontend is served by a container rather than from disk"
            echo "(look for proxy_pass above), use ./deploy-docker.sh instead."
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠ Could not read the nginx config to confirm ${WEB_DIR}${NC}"
        echo "  Publishing there anyway. Verify the site afterwards."
    fi

    if [ -d "$WEB_DIR" ]; then
        echo "Backing up current frontend..."
        sudo mv "$WEB_DIR" "${WEB_DIR}.backup.$(date +%Y%m%d_%H%M%S)" || true
    fi

    echo "Copying new frontend files..."
    sudo mkdir -p "$WEB_DIR"
    sudo cp -r dist/* "$WEB_DIR/"
    sudo chown -R www-data:www-data "$WEB_DIR"

    echo -e "${GREEN}✓ Frontend deployed successfully${NC}"

    cd ..
fi

# Reload NGINX
if [ "$DEPLOY_TYPE" == "2" ] || [ "$DEPLOY_TYPE" == "3" ]; then
    print_section "Reloading NGINX"

    # Test NGINX config
    if sudo nginx -t 2>&1 | grep -q "successful"; then
        sudo systemctl reload nginx
        echo -e "${GREEN}✓ NGINX reloaded successfully${NC}"
    else
        echo -e "${RED}✗ NGINX configuration test failed${NC}"
        exit 1
    fi
fi

# Summary
print_section "Deployment Summary"
echo -e "${GREEN}========================================${NC}"

if [ "$DEPLOY_TYPE" == "1" ] || [ "$DEPLOY_TYPE" == "3" ]; then
    echo -e "Backend:  ${GREEN}✓ Deployed${NC}"
    echo -e "Service:  ${GREEN}✓ Running${NC}"
    echo -e "Health:   Check at http://localhost:8080/actuator/health"
fi

if [ "$DEPLOY_TYPE" == "2" ] || [ "$DEPLOY_TYPE" == "3" ]; then
    echo -e "Frontend: ${GREEN}✓ Deployed${NC}"
    echo -e "URL:      https://${DOMAIN}"
fi

echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${GREEN}Deployment completed successfully!${NC}"
echo ""

# Show helpful commands
echo -e "${YELLOW}Useful commands:${NC}"
if [ "$DEPLOY_TYPE" == "1" ] || [ "$DEPLOY_TYPE" == "3" ]; then
    echo "  View backend logs:  sudo journalctl -u ${BACKEND_SERVICE} -f"
    echo "  Backend status:     sudo systemctl status ${BACKEND_SERVICE}"
    echo "  Restart backend:    sudo systemctl restart ${BACKEND_SERVICE}"
fi
if [ "$DEPLOY_TYPE" == "2" ] || [ "$DEPLOY_TYPE" == "3" ]; then
    echo "  NGINX access logs:  sudo tail -f /var/log/nginx/access.log"
    echo "  NGINX error logs:   sudo tail -f /var/log/nginx/error.log"
    echo "  NGINX status:       sudo systemctl status nginx"
fi
echo ""
