# Template Smith Forge Setup Instructions

## Manual Setup Steps

Since the repository cannot be cloned automatically, please follow these steps:

### 1. Clone the Repository

```bash
cd /home/user/elcafe
git clone https://github.com/Furqatbek/template-smith-forge.git
```

### 2. Verify the Repository Structure

Make sure the cloned repository has a `Dockerfile`. If it doesn't exist, create one based on the technology stack used.

#### For Node.js Applications:

Create `template-smith-forge/Dockerfile`:

```dockerfile
FROM node:18-alpine

WORKDIR /app

COPY package*.json ./

RUN npm ci --only=production

COPY . .

EXPOSE 5000

CMD ["npm", "start"]
```

#### For Python Applications:

Create `template-smith-forge/Dockerfile`:

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 5000

CMD ["python", "app.py"]
```

### 3. Configure Environment Variables

If the application requires specific environment variables, update the `docker-compose.yaml` file:

```yaml
template-smith-forge:
  environment:
    NODE_ENV: production
    PORT: 5000
    DATABASE_URL: postgresql://postgres:postgres@postgres:5432/elcafe_db
    REDIS_URL: redis://redis:6379
    # Add any other required variables
```

### 4. Start All Services

```bash
docker-compose down
docker-compose up --build
```

## Services Overview

After running `docker-compose up --build`, the following services will be available:

- **PostgreSQL Database**: `localhost:5432`
- **Redis Cache**: `localhost:6379`
- **ElCafe Backend API**: `localhost:8080`
- **ElCafe Frontend**: `localhost:3000`
- **Template Smith Forge**: `localhost:5000`

## Troubleshooting

### If template-smith-forge fails to start:

1. **Check the Dockerfile exists**:
   ```bash
   ls -la /home/user/elcafe/template-smith-forge/Dockerfile
   ```

2. **Check the logs**:
   ```bash
   docker-compose logs template-smith-forge
   ```

3. **Verify the port is not in use**:
   ```bash
   lsof -i :5000
   ```

4. **Rebuild the specific service**:
   ```bash
   docker-compose build template-smith-forge
   docker-compose up template-smith-forge
   ```

### If you want to run without template-smith-forge:

Comment out the service in `docker-compose.yaml`:

```yaml
# template-smith-forge:
#   build:
#     context: ./template-smith-forge
#   ...
```

Or exclude it when starting:

```bash
docker-compose up --build postgres redis app frontend
```

## Customization

Update the following in `docker-compose.yaml` based on your needs:

- **Port**: Change `5000:5000` to map to a different port
- **Dependencies**: Add/remove services in `depends_on`
- **Environment Variables**: Add application-specific variables
- **Volumes**: Mount additional directories as needed
