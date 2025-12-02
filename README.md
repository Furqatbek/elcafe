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
- **Waiter Module**: Complete table management, dine-in orders, and real-time kitchen communication via WebSocket
- **Courier Integration**: Pluggable courier provider system with webhook support
- **CRM**: Customer management with order history and RFM analytics
- **RFM Analysis**: Customer segmentation based on Recency, Frequency, and Monetary value with 11 customer segments

### Technical Features

- Multi-layer clean architecture
- PostgreSQL database with Flyway migrations
- Redis caching for menu data
- JWT authentication with refresh tokens
- WebSocket real-time communication (STOMP/SockJS)
- Event-driven architecture with async processing
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
| WebSocket | STOMP/SockJS | Real-time Communication |
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
    ├── waiter/          # Waiter & table management
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
NEW → ACCEPTED → PREPARING → READY → COURIER_ASSIGNED → ON_DELIVERY → DELIVERED → COMPLETED
  ↓                                     ↓
CANCELLED                           COMPLETED (dine-in)
```

**Dine-in orders** (via Waiter Module): `NEW → ACCEPTED → PREPARING → READY → COMPLETED`

## 🎯 Key Endpoints

### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/forgot-password` - Request password reset
- `POST /api/v1/auth/reset-password` - Reset password

### Consumer Authentication (OTP-based for Mobile/Web)
- `POST /api/v1/consumer/auth/login` - Request OTP code via SMS
- `POST /api/v1/consumer/auth/verify` - Verify OTP and get tokens
- `POST /api/v1/consumer/auth/refresh` - Refresh consumer access token
- `POST /api/v1/consumer/auth/logout` - Logout and invalidate session

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

### Operators (Admin only)
- `GET /api/v1/operators` - Get paginated list of operators
- `GET /api/v1/operators/{id}` - Get operator details
- `POST /api/v1/operators` - Create new operator
- `PUT /api/v1/operators/{id}` - Update operator
- `DELETE /api/v1/operators/{id}` - Delete operator

### Couriers (Admin only for write operations)
- `GET /api/v1/couriers` - Get paginated list of couriers
- `GET /api/v1/couriers/{id}` - Get courier details
- `GET /api/v1/couriers/{id}/wallet` - Get courier wallet balance
- `POST /api/v1/couriers` - Create new courier with wallet
- `PUT /api/v1/couriers/{id}` - Update courier
- `DELETE /api/v1/couriers/{id}` - Delete courier

### Courier Webhooks
- `POST /api/v1/courier/webhook/delivery-status` - Receive delivery status updates

### SMS (Admin/Operator only)
- `POST /api/v1/sms/auth/login` - Authenticate with SMS broker
- `PATCH /api/v1/sms/auth/refresh` - Refresh SMS broker token
- `GET /api/v1/sms/auth/user` - Get SMS broker user info
- `GET /api/v1/sms/user/limit` - Get user SMS limit
- `GET /api/v1/sms/templates` - Get SMS templates
- `POST /api/v1/sms/send` - Send single SMS
- `POST /api/v1/sms/send-batch` - Send batch SMS
- `POST /api/v1/sms/send-global` - Send global SMS to multiple recipients
- `GET /api/v1/sms/message/{id}/status` - Get message status
- `GET /api/v1/sms/messages` - Get user messages (paginated)
- `GET /api/v1/sms/dispatch/{dispatchId}/messages` - Get messages by dispatch
- `GET /api/v1/sms/dispatch/{dispatchId}/status` - Get dispatch status

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
- **waiters** - Waiter accounts with PIN authentication
- **tables** - Restaurant tables with status tracking
- **waiter_tables** - Waiter-table assignment history
- **order_events** - Comprehensive audit trail for all order operations

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

### SMS Integration (Eskiz.uz)
```yaml
eskiz:
  sms:
    base-url: https://notify.eskiz.uz/api
    email: your-email@example.com
    password: your-password
    enabled: true  # Set to false to disable SMS sending (mock mode)
    connection-timeout: 30000
    read-timeout: 30000
    token-expiration-ms: 2505600000  # 29 days
    callback-url: https://your-domain.com/api/v1/sms/callback
```

Environment variables:
- `ESKIZ_SMS_EMAIL` - Eskiz.uz account email
- `ESKIZ_SMS_PASSWORD` - Eskiz.uz account password
- `ESKIZ_SMS_ENABLED` - Enable/disable SMS sending (default: true)
- `ESKIZ_SMS_CALLBACK_URL` - Callback URL for delivery reports

### Consumer Authentication (OTP)
```yaml
app:
  consumer:
    otp:
      expiration-minutes: 5  # OTP validity duration
      max-attempts: 3  # Maximum verification attempts
      rate-limit-minutes: 1  # Rate limit time window
      rate-limit-count: 3  # Maximum OTP requests within window
      include-in-response: false  # Include OTP in response (dev/test only)
    session:
      access-token-expiration: 3600000  # 1 hour
      refresh-token-expiration: 2592000000  # 30 days
```

Environment variables:
- `CONSUMER_OTP_INCLUDE_IN_RESPONSE` - Include OTP in API response for testing (default: false)

Features:
- Phone-based authentication with SMS OTP
- 6-digit OTP codes with 5-minute expiration
- Rate limiting to prevent abuse (3 requests per minute)
- Automatic customer creation on first login
- JWT-based session management with refresh tokens
- Automatic cleanup of expired OTPs and sessions
- IP address and user agent tracking

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

## 📚 Documentation

### Module Documentation
- **[Waiter Module](./docs/WAITER_MODULE.md)** - Complete guide for table management, dine-in orders, and WebSocket communication
- **[Food Ordering API](./docs/FOOD_ORDERING_API.md)** - Complete API documentation for food ordering system

### Guides
- **[API Integration Guide](./README_API_INTEGRATION.md)** - Integration guide for third-party services
- **[Setup Template Smith Forge](./SETUP_TEMPLATE_SMITH_FORGE.md)** - Frontend template setup

### WebSocket Documentation
For real-time communication between waiters and kitchen:
- Endpoint: `ws://localhost:8080/ws-waiter`
- Topics: `/topic/waiter/orders`, `/topic/kitchen`, `/topic/table`
- See [Waiter Module Documentation](./docs/WAITER_MODULE.md#websocket-communication) for details

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
