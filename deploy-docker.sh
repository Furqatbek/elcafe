#!/bin/bash

# ElCafe Docker Deployment Script
# One-command deployment for production

set -e

echo "🚀 Deploying ElCafe with Docker Compose..."
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
docker-compose down

# Pull latest code (if in git repo)
if [ -d .git ]; then
    echo "📥 Pulling latest code..."
    git pull || echo "⚠️  Failed to pull, using local code"
fi

# Build and start services
echo "🏗️  Building and starting services..."
docker-compose up -d --build

# Wait for services to be healthy
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check service status
echo ""
echo "📊 Service Status:"
docker-compose ps

# Check backend health
echo ""
echo "🔍 Checking backend health..."
for i in {1..30}; do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ Backend is healthy!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Backend health check failed after 30 attempts"
        echo "Check logs: docker-compose logs backend"
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
echo "   Frontend: http://localhost"
echo "   Backend:  http://localhost:8080"
echo "   API:      http://localhost/api"
echo ""
echo "📋 Useful commands:"
echo "   View logs:        docker-compose logs -f"
echo "   Stop services:    docker-compose down"
echo "   Restart services: docker-compose restart"
echo "   View status:      docker-compose ps"
echo ""
