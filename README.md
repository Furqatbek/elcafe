# Restaurant Delivery Control Service

A complete full-stack production-ready system for restaurant management and delivery control.

**Backend**: Java 21 (LTS) + Spring Boot 3.x
**Frontend**: React 18 + Vite + Shadcn UI

## 🚀 Features

### Core Modules

- **Authentication & Authorization**: JWT-based security with role-based access control (Admin, Operator)
- **Restaurant Management**: Complete CRUD for restaurants, business hours, and delivery zones
- **Menu Management**: Categories, products, variants, and add-ons with Redis caching
- **Order Management**: Full order lifecycle from creation to delivery with status tracking
- **Courier Integration**: Pluggable courier provider system with webhook support
- **CRM**: Customer management with order history and RFM analytics
- **RFM Analysis**: Customer segmentation based on Recency, Frequency, and Monetary value with 11 customer segments

### Technical Features

- Multi-layer clean architecture
- PostgreSQL database with Flyway migrations
- Redis caching for menu data
- JWT authentication with refresh tokens
- OpenAPI/Swagger documentation
- Docker containerization
- Comprehensive exception handling
- Request validation
- Audit logging

## 📋 Prerequisites

- Java 21+ (LTS recommended)
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL 16 (if running locally)
- Redis 7 (if running locally)

## 🛠️ Tech Stack

### Backend
| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 (LTS) | Programming Language |
| Spring Boot | 3.3.0 | Framework |
| PostgreSQL | 16 | Database |
| Redis | 7 | Caching |
| Flyway | Latest | Database Migrations |
| JWT | 0.12.5 | Authentication |
| Springdoc | 2.5.0 | API Documentation |

### Frontend
| Technology | Version | Purpose |
|-----------|---------|---------|
| React | 18.3 | UI Library |
| Vite | 5.1 | Build Tool |
| Shadcn UI | Latest | Component Library |
| Tailwind CSS | 3.4 | Styling |
| Zustand | 4.5 | State Management |
| React Router | 6.22 | Navigation |

### DevOps
| Technology | Version | Purpose |
|-----------|---------|---------|
| Docker | Latest | Containerization |
| Nginx | Alpine | Web Server |

## 🚀 Quick Start

### Option 1: Run with Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone <repository-url>
cd elcafe
```

2. Build and start all services:
```bash
docker-compose up --build
```

3. Wait for all services to start (approximately 2-3 minutes)

4. Access the application:
- **Frontend UI**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/api-docs

### Option 2: Run Locally

1. Start PostgreSQL and Redis:
```bash
docker-compose up postgres redis
```

2. Build the application:
```bash
./mvnw clean package
```

3. Run the application:
```bash
java -jar target/restaurant-delivery-service-1.0.0.jar
```

## 📚 API Documentation

Once the application is running, access the interactive API documentation:

**Swagger UI**: http://localhost:8080/swagger-ui.html

### Default Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@elcafe.com | Admin123! |
| Operator | operator@elcafe.com | Operator123! |

## 🔐 Authentication Flow

1. **Login** - POST `/api/v1/auth/login`
```json
{
  "email": "admin@elcafe.com",
  "password": "Admin123!"
}
```

2. **Response** - Contains access token and refresh token:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "email": "admin@elcafe.com",
      "role": "ADMIN"
    }
  }
}
```

3. **Use Token** - Include in Authorization header:
```
Authorization: Bearer <access-token>
```

## 📁 Project Structure

```
src/main/java/com/elcafe/
├── config/                 # Configuration classes
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── OpenApiConfig.java
│   └── AsyncConfig.java
├── security/              # Security components
│   ├── JwtUtil.java
│   └── JwtAuthenticationFilter.java
├── exception/             # Exception handling
│   ├── GlobalExceptionHandler.java
│   └── [Custom exceptions]
├── utils/                 # Utility classes
│   └── ApiResponse.java
└── modules/              # Business modules
    ├── auth/             # Authentication
    ├── restaurant/       # Restaurant management
    ├── menu/            # Menu management
    ├── order/           # Order management
    ├── courier/         # Courier integration
    └── customer/        # CRM

src/main/resources/
├── application.yml
└── db/migration/         # Flyway migrations
    ├── V1__initial_schema.sql
    └── V2__seed_data.sql
```

## 🔄 Order Status Flow

Orders follow this status lifecycle:

```
NEW → ACCEPTED → PREPARING → READY → COURIER_ASSIGNED → ON_DELIVERY → DELIVERED
  ↓
CANCELLED
```

## 🎯 Key Endpoints

### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/forgot-password` - Request password reset
- `POST /api/v1/auth/reset-password` - Reset password

### Restaurants (Admin only for write operations)
- `GET /api/v1/restaurants` - List all restaurants
- `GET /api/v1/restaurants/{id}` - Get restaurant details
- `POST /api/v1/restaurants` - Create restaurant
- `PUT /api/v1/restaurants/{id}` - Update restaurant
- `DELETE /api/v1/restaurants/{id}` - Delete restaurant

### Menu
- `GET /api/v1/menu/public/{restaurantId}` - Get public menu (cached)
- `GET /api/v1/menu/restaurants/{restaurantId}/categories` - Get categories

### Orders
- `POST /api/v1/orders` - Create order
- `GET /api/v1/orders/{id}` - Get order details
- `PATCH /api/v1/orders/{id}/status` - Update order status
- `GET /api/v1/orders/pending` - Get pending orders
- `GET /api/v1/orders/restaurant/{restaurantId}` - Get restaurant orders

### Customers (CRM)
- `GET /api/v1/customers` - List customers
- `GET /api/v1/customers/{id}` - Get customer details
- `POST /api/v1/customers` - Create customer
- `PUT /api/v1/customers/{id}` - Update customer
- `GET /api/v1/customers/{id}/orders` - Get customer order history

### Customer Activity & RFM Analysis
- `GET /api/v1/customers/activity` - Get all customers with RFM metrics
- `GET /api/v1/customers/activity/filter` - Filter customers with query parameters
- `POST /api/v1/customers/activity/filter` - Advanced filtering with request body

### Courier
- `POST /api/v1/courier/webhook/delivery-status` - Receive delivery status updates

## 🗄️ Database Schema

The application uses PostgreSQL with the following main tables:

- **users** - System users with authentication
- **restaurants** - Restaurant information
- **business_hours** - Operating hours
- **delivery_zones** - Delivery coverage areas
- **categories** - Menu categories
- **products** - Menu items
- **product_variants** - Product variations (size, type, etc.)
- **addon_groups** - Add-on categories
- **addons** - Individual add-ons
- **customers** - Customer information with registration source tracking
- **orders** - Order records with order source tracking
- **order_items** - Order line items
- **delivery_info** - Delivery details
- **payments** - Payment records
- **order_status_history** - Order status audit trail

## ⚙️ Configuration

Key configuration properties in `application.yml`:

### Database
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/elcafe_db
    username: postgres
    password: postgres
```

### Redis
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### JWT
```yaml
app:
  security:
    jwt:
      secret: your-secret-key
      access-token-expiration: 3600000  # 1 hour
      refresh-token-expiration: 86400000  # 24 hours
```

### Cache TTL
```yaml
app:
  cache:
    menu-ttl: 1800  # 30 minutes
    restaurant-ttl: 3600  # 1 hour
```

## 🧪 Testing

Run tests:
```bash
./mvnw test
```

## 📊 Monitoring

The application exposes Actuator endpoints:

- Health: `GET /actuator/health`
- Info: `GET /actuator/info`
- Metrics: `GET /actuator/metrics`
- Prometheus: `GET /actuator/prometheus`

## 🔍 Logging

Application uses SLF4J with Logback. Log levels:
- `com.elcafe`: DEBUG
- `org.springframework.web`: INFO
- `org.springframework.security`: DEBUG
- `org.hibernate.SQL`: DEBUG

## 🚢 Deployment

### Docker Build

Build the Docker image:
```bash
docker build -t elcafe-api:latest .
```

Run the container:
```bash
docker run -p 8080:8080 \
  -e DB_HOST=your-db-host \
  -e DB_PASSWORD=your-db-password \
  -e REDIS_HOST=your-redis-host \
  elcafe-api:latest
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| DB_HOST | PostgreSQL host | localhost |
| DB_PORT | PostgreSQL port | 5432 |
| DB_NAME | Database name | elcafe_db |
| DB_USER | Database user | postgres |
| DB_PASSWORD | Database password | postgres |
| REDIS_HOST | Redis host | localhost |
| REDIS_PORT | Redis port | 6379 |
| JWT_SECRET | JWT signing secret | (see config) |
| SERVER_PORT | Application port | 8080 |

## 🛡️ Security Best Practices

1. **Change default credentials** in production
2. **Update JWT secret** with a strong random value
3. **Use HTTPS** in production
4. **Configure CORS** properly for your frontend domain
5. **Enable rate limiting** for public endpoints
6. **Implement API key validation** for courier webhooks
7. **Use environment variables** for sensitive configuration

## 📈 Performance Optimization

- **Redis caching** for frequently accessed menu data
- **Database indexes** on commonly queried fields
- **Connection pooling** (HikariCP)
- **Lazy loading** for JPA relationships
- **Async processing** for non-blocking operations

## 📋 Changelog

See [CHANGELOG.md](./CHANGELOG.md) for a detailed list of changes, new features, and fixes.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📝 License

This project is licensed under the Apache License 2.0.

## 📧 Support

For support, email support@elcafe.com or open an issue in the repository.

## 🎉 Acknowledgments

Built with Spring Boot, PostgreSQL, Redis, and modern Java best practices.

---

**Version**: 1.1.0
**Last Updated**: 2025-11-24
**Built with**: ☕ Java 21 LTS + 🍃 Spring Boot 3.x
