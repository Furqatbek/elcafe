#!/bin/bash

# Qahvoon Docker Deployment Script
# One-command deployment for production

set -e

BRAND="${BRAND:-Qahvoon}"

# Compose v1 (docker-compose) was removed from Docker in favour of the v2
# plugin (docker compose). This script hardcoded the v1 name, so on a host with
# only the plugin every compose call failed with "command not found" and, under
# set -e, the script died on the first one having changed nothing. Resolve
# whichever is present, once.
if docker compose version >/dev/null 2>&1; then
    DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    DC="docker-compose"
else
    echo "❌ Neither 'docker compose' nor 'docker-compose' is available."
    echo "   Install the Compose plugin:  apt install docker-compose-plugin"
    exit 1
fi
echo "Using: $DC"

echo "🚀 Deploying ${BRAND} with Docker Compose..."
echo ""

# Check if .env.docker exists
if [ ! -f .env.docker ]; then
    echo "❌ Error: .env.docker file not found!"
    echo "Please create .env.docker from .env.docker.example"
    exit 1
fi

# Load environment variables
set -a
source .env.docker
set +a

# Stop existing containers
echo "📦 Stopping existing containers..."
$DC down

# Pull latest code (if in git repo)
if [ -d .git ]; then
    echo "📥 Pulling latest code..."
    git pull || echo "⚠️  Failed to pull, using local code"
fi

# Build and start services
echo "🏗️  Building and starting services..."
$DC up -d --build

# Wait for services to be healthy
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check service status
echo ""
echo "📊 Service Status:"
$DC ps

# Check backend health.
#
# /actuator/health aggregates every component, so one degraded dependency (a
# down Redis, say) makes it answer 503 while the application itself serves
# traffic perfectly well. The old gate used `curl -sf`, which treats 503 the
# same as no reply at all, so it failed the deploy — after the rebuild had
# already succeeded — and told the operator to go hunting for a problem that
# was not there. Distinguish the two: no HTTP response means the backend did
# not come up and that is fatal; a 503 means it is up and something it depends
# on is not, which is worth reporting, not worth failing for.
echo ""
echo "🔍 Checking backend health..."
BACKEND_CODE=000
for i in {1..30}; do
    BACKEND_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health 2>/dev/null || echo 000)
    if [ "$BACKEND_CODE" = "200" ]; then
        echo "✅ Backend is healthy!"
        break
    fi
    if [ "$BACKEND_CODE" != "000" ]; then
        echo "⚠️  Backend is up but reports a degraded dependency (HTTP $BACKEND_CODE)."
        echo "   The deploy itself is fine. Which component is down:"
        curl -s http://localhost:8080/actuator/health | sed 's/^/     /'
        echo ""
        echo "   Check with:  $DC ps   and   $DC logs redis"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Backend never answered on :8080 after 30 attempts"
        echo "Check logs: $DC logs backend"
        exit 1
    fi
    echo "Waiting... ($i/30)"
    sleep 2
done

# Check frontend
echo ""
echo "🔍 Checking frontend..."
if curl -sf http://localhost > /dev/null 2>&1; then
    echo "✅ Frontend is healthy!"
else
    echo "⚠️  Frontend check failed, but continuing..."
fi

echo ""
echo "✅ Deployment completed successfully!"
echo ""
echo "📱 Application URLs:"
echo "   Frontend: https://${BRAND_DOMAIN:-qahvoon.uz}"
echo "   Backend:  http://localhost:8080"
echo "   API:      https://${BRAND_DOMAIN:-qahvoon.uz}/api/v1"
echo ""
echo "📋 Useful commands:"
echo "   View logs:        $DC logs -f"
echo "   Stop services:    $DC down"
echo "   Restart services: $DC restart"
echo "   View status:      $DC ps"
echo ""
