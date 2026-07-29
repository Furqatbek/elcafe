# Qahvoon - Restaurant Delivery Control Service

A complete production-ready backend system for restaurant management and delivery control built with Java 21 + Spring Boot 3.3.0.

## 🎉 **100% Implementation Complete**

✅ All features implemented and production-ready
✅ 250+ REST API endpoints
✅ ~116 modular controllers across ~30 feature modules
✅ Complete order lifecycle management
✅ Real-time WebSocket notifications
✅ SMS integration (Eskiz.uz)
✅ Comprehensive analytics suite

## 🚀 Features

### Core Modules

- **Authentication & Authorization**
  - JWT-based security with role-based access control
  - Roles: SUPER_ADMIN (cross-tenant platform operator), ADMIN, OWNER, MANAGER, OPERATOR, WAITER, HEAD_WAITER, SUPERVISOR, KITCHEN_STAFF, CASHIER, COURIER, CUSTOMER
  - Consumer OTP authentication via SMS
  - Admin/Operator email-password authentication

- **Restaurant Management**
  - Complete CRUD for restaurants
  - Business hours configuration with day-specific schedules
  - Delivery zones with geographic boundaries
  - Restaurant status control (accepting orders, active/inactive)

- **Menu Management**
  - Categories, products, variants, and add-ons
  - Ingredient tracking with cost management
  - Linked items (recommended products, upsells, cross-sells)
  - Menu collections (featured items, combos)
  - Redis caching for public menu (30-minute TTL)

- **Order Management**
  - Consumer order placement (authenticated via consumer OTP/JWT)
  - Full order lifecycle: NEW → ACCEPTED → PREPARING → READY → (COURIER_ASSIGNED → ON_DELIVERY → DELIVERED for delivery) → COMPLETED
  - Admin order acceptance/rejection (no automated refund path — payments are handled out of band)
  - Consumer cancellation (5-minute window)
  - Order validation against a per-restaurant configurable minimum order amount (UZS)
  - WebSocket real-time order updates
  - SMS notifications at key milestones

- **Kitchen Operations**
  - Kitchen order dashboard
  - Chef assignment and tracking
  - Preparation time monitoring
  - Priority management
  - Real-time status updates

- **Waiter Module**
  - Table management (open, close, merge, unmerge)
  - Dine-in order creation and management
  - Item-level status tracking (preparing, ready, delivered)
  - Bill generation and order closing
  - PIN-based authentication
  - WebSocket real-time updates

- **Courier System**
  - Courier management with wallet integration
  - GPS location tracking
  - Order assignment (manual and automatic)
  - Delivery route tracking
  - Tariff configuration
  - Real-time status updates
  - Webhook integration for external courier providers

- **Customer Management (CRM)**
  - Customer profiles with order history
  - Multiple delivery addresses
  - RFM analysis (Recency, Frequency, Monetary)
  - Customer activity tracking
  - Lifetime value calculation

- **Analytics & Reporting**
  - Financial analytics (daily revenue, COGS, profitability)
  - Sales analytics (by category, by hour, contribution margins)
  - Operational metrics (peak hours, table turnover, order timing)
  - Kitchen performance analytics
  - Customer analytics (retention, LTV, satisfaction)
  - Inventory turnover analysis

- **SMS Notifications**
  - Order confirmation (consumer)
  - Order accepted (consumer)
  - Order ready for pickup (consumer)
  - Order completed (consumer)
  - Order cancelled/rejected (consumer)
  - New order alerts (restaurant)
  - Integration with Eskiz.uz SMS gateway

- **Payment Integration**
  - Multiple payment methods (CARD, CASH, WALLET)
  - Payment status tracking
  - Automated refund processing
  - Payment reports by method and status

### Technical Features

- **Architecture**: Multi-layer clean architecture with modular design
- **Database**: PostgreSQL 16 with Flyway migrations (V1–V186)
- **Caching**: Redis 7 for menu data and session management
- **Security**: JWT authentication with access and refresh tokens
- **Real-time**: WebSocket (STOMP) for order and table updates
- **Documentation**: OpenAPI 3.0/Swagger with comprehensive endpoint documentation
- **Containerization**: Docker and Docker Compose support
- **Exception Handling**: Global exception handler with consistent error responses
- **Validation**: Request validation with Jakarta Bean Validation
- **Audit Logging**: Complete order status history and event tracking
- **File Upload**: Image upload support for menu items
- **Background Jobs**: Scheduled tasks for order lifecycle management

## 📋 Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL 16 (if running locally)
- Redis 7 (if running locally)

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Programming Language |
| Spring Boot | 3.3.0 | Application Framework |
| PostgreSQL | 16 | Primary Database |
| Redis | 7 | Caching & Sessions |
| Flyway | 10.10.0 | Database Migrations |
| JWT (JJWT) | 0.12.5 | Authentication |
| Springdoc OpenAPI | 2.5.0 | API Documentation |
| WebSocket (STOMP) | Latest | Real-time Communication |
| Lombok | Latest | Code Generation |
| Docker | Latest | Containerization |

## 🚀 Quick Start

### Option 1: Local dev with Docker Compose (Recommended)

> Full local guide: **[docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md)**. For a real
> production deployment, see **[docs/LAUNCH.md](docs/LAUNCH.md)** instead.

1. Clone the repository:
```bash
git clone <repository-url>
cd elcafe
```

2. Build and start the full stack (db, redis, backend, frontend, nginx) with dev overrides.

   macOS/Linux:
   ```bash
   ./run-local.sh
   ```
   Windows (PowerShell):
   ```powershell
   ./run-local.ps1
   ```
   These use `docker-compose.local.yml` (dev profile, dev-only JWT secret, HTTP-only nginx) and
   supply throwaway `dev` DB/Redis passwords, so no `.env` is needed. Equivalent to:
   ```bash
   # bash
   DB_PASSWORD=dev REDIS_PASSWORD=dev \
     docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
   ```
   ```powershell
   # PowerShell
   $env:DB_PASSWORD="dev"; $env:REDIS_PASSWORD="dev"
   docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
   ```

3. Wait for all services to start (approximately 2-3 minutes)

4. Access the application:
- App: http://localhost
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/api-docs

### Option 2: Backend on host, datastores in Docker

1. Start just PostgreSQL and Redis (the service is named `db`, not `postgres`):
```bash
DB_PASSWORD=dev REDIS_PASSWORD=dev docker compose up db redis
```

2. Run the backend (needs a JWT secret and the datasource pointing at the containers):
```bash
export JWT_SECRET=local-dev-only-secret-not-for-production-0123456789abcdef
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/elcafe
export SPRING_DATASOURCE_USERNAME=elcafe SPRING_DATASOURCE_PASSWORD=dev
export SPRING_DATA_REDIS_PASSWORD=dev
mvn spring-boot:run
```

3. Run the frontend dev server (Vite, on http://localhost:3000/admin/ — the app is served under the `/admin` base):
```bash
cd frontend && npm install && npm run dev
```

### Production

Production runs `docker-compose.yml` alone with real secrets from `.env.docker`. See
[`docs/LAUNCH.md`](docs/LAUNCH.md) for the full checklist, or:
```bash
cp .env.docker.example .env.docker   # fill in the required values
./deploy-docker.sh
```

## 📚 API Documentation

Once the application is running, access the interactive API documentation:

**Swagger UI**: http://localhost:8080/swagger-ui.html

### First login

The database ships empty — there are no seeded accounts. On first boot with an empty users table,
the app creates a single `SUPER_ADMIN` from the `ADMIN_EMAIL` / `ADMIN_PASSWORD` environment
variables (see [`docs/LAUNCH.md`](docs/LAUNCH.md)). Log in with those, change the password, then
remove `ADMIN_PASSWORD`. For local dev, set them in your shell before the launcher:

```bash
# bash
ADMIN_EMAIL=admin@your-domain.example ADMIN_PASSWORD='ChangeMe123!' ./run-local.sh
```
```powershell
# PowerShell
$env:ADMIN_EMAIL="admin@your-domain.example"; $env:ADMIN_PASSWORD="ChangeMe123!"; ./run-local.ps1
```

> **`ADMIN_EMAIL` / `ADMIN_PASSWORD` take effect only on the first boot against an empty database.**
> On later boots they're ignored — so if you re-run with different values, or an earlier boot already
> created an admin, the new credentials silently won't work. Reset the database first
> (`docker compose … down -v`) or change the account from inside the app.

## 🔐 Authentication Flow

1. **Login** - POST `/api/v1/auth/login`
```json
{
  "email": "admin@your-domain.example",
  "password": "ChangeMe123!"
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
      "email": "admin@your-domain.example",
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

Orders follow this comprehensive status lifecycle:

```
NEW (Awaiting restaurant acceptance)
    ↓
ACCEPTED (Restaurant confirms order)
    ↓
PREPARING (Kitchen is cooking)
    ↓
READY (Food ready)
    ↓  (delivery orders)
COURIER_ASSIGNED → ON_DELIVERY → DELIVERED
    ↓
COMPLETED (Settled)

Alternative flows:
NEW/ACCEPTED → REJECTED (Restaurant rejects)
NEW/ACCEPTED → CANCELLED (Customer within 5 min, or Admin)
(No automated refund path — payment settlement is handled out of band.)
```

**State Machine Validation**: All status transitions are validated to prevent invalid state changes.

**Notifications**: SMS and WebSocket notifications sent at key milestones (ACCEPTED, READY, COMPLETED, REJECTED, CANCELLED).

## 🎯 Key API Endpoints (250+ total)

### Authentication (Public)
- `POST /api/v1/auth/register` - Register new user (Admin/Operator)
- `POST /api/v1/auth/login` - User login with email/password
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/consumer/auth/login` - Consumer OTP login (send SMS code); requires `restaurantId` (customers are per-restaurant)
- `POST /api/v1/consumer/auth/verify` - Verify OTP code and get tokens (requires matching `restaurantId`)

### Restaurants
- `GET /api/v1/restaurants` - List all restaurants (Public)
- `GET /api/v1/restaurants/active` - Get active restaurants (Public)
- `POST /api/v1/restaurants` - Create restaurant (Admin)
- `PUT /api/v1/restaurants/{id}` - Update restaurant (Admin)
- `GET /api/v1/restaurants/{restaurantId}/business-hours` - Get business hours
- `POST /api/v1/restaurants/{restaurantId}/delivery-zones` - Create delivery zone (Admin)

### Menu Management
- `GET /api/v1/menu/public/{restaurantId}` - Get public menu with caching (Public)
- `GET /api/v1/categories` - Get active categories (Public)
- `POST /api/v1/categories` - Create category (Admin)
- `POST /api/v1/products` - Create product (Admin)
- `POST /api/v1/products/{productId}/variants` - Create product variant (Admin)
- `GET /api/v1/menu-collections/active` - Get active menu collections (Public)

### Consumer Order API (Public)
- `POST /api/v1/consumer/orders` - Place order (No auth required)
- `GET /api/v1/consumer/orders/{orderNumber}` - Track order by order number
- `POST /api/v1/consumer/orders/{orderNumber}/cancel` - Cancel order (5-min window)

### Admin Order Management
- `GET /api/v1/admin/orders` - Get all orders with filters (Admin/Operator)
- `POST /api/v1/admin/orders/{orderId}/accept` - Accept order (Admin/Operator)
- `POST /api/v1/admin/orders/{orderId}/reject` - Reject order with auto-refund (Admin/Operator)
- `POST /api/v1/admin/orders/{orderId}/cancel` - Cancel order (Admin/Operator)

### Kitchen Operations
- `GET /api/v1/kitchen/orders/active` - Get active orders (Kitchen/Admin)
- `POST /api/v1/kitchen/orders/{id}/start` - Start preparing order
- `POST /api/v1/kitchen/orders/{id}/ready` - Mark order ready for pickup
- `POST /api/v1/kitchen/orders/{id}/picked-up` - Mark as picked up by courier

### Waiter Module
- `POST /api/v1/waiters/auth` - Waiter PIN authentication (Public); requires `restaurantId` (PINs are per-restaurant)
- `GET /api/v1/waiter/tables` - Get all tables (Waiter)
- `POST /api/v1/waiter/orders` - Create dine-in order (Waiter)
- `POST /api/v1/waiter/orders/{orderId}/submit` - Submit order to kitchen (Waiter)
- `POST /api/v1/waiter/orders/{orderId}/bill` - Request bill (Waiter)

### Courier System
- `GET /api/v1/courier/orders/available` - Get available delivery orders (Courier)
- `POST /api/v1/courier/orders/{orderId}/accept` - Accept order for delivery (Courier)
- `POST /api/v1/courier/orders/{orderId}/start-delivery` - Start delivery (Courier)
- `POST /api/v1/courier/orders/location` - Update GPS location (Courier)
- `POST /api/v1/couriers/{id}/status` - Update online/offline status (Courier)

### Analytics
- `GET /api/v1/analytics/summary` - Comprehensive dashboard metrics (Admin/Operator)
- `GET /api/v1/analytics/financial/daily-revenue` - Daily revenue breakdown
- `GET /api/v1/analytics/operational/peak-hours` - Peak business hours analysis
- `GET /api/v1/analytics/customer/retention` - Customer retention metrics
- `GET /api/v1/analytics/operational/kitchen` - Kitchen performance metrics

### Customer Management
- `GET /api/v1/customers` - List customers (Admin/Operator)
- `GET /api/v1/customers/activity` - Get customer RFM analysis
- `GET /api/v1/customers/{customerId}/addresses` - Get customer addresses (Public)
- `POST /api/v1/customers/{customerId}/addresses` - Create address (Public)

### SMS & File Upload
- `POST /api/v1/sms/send` - Send SMS notification (Admin/Operator)
- `POST /api/v1/files/upload` - Upload file/image (Admin/Operator)

## 🗄️ Database Schema

The application uses PostgreSQL with **186 Flyway migrations** (V1–V186) defining the full schema (auth, restaurant, menu, order, kitchen, waiter, courier, customer, inventory, financial, loyalty, shifts, billing/subscription, marketing, and more):

### Core Tables
- **users** - System users (Admin, Operator)
- **operators** - Operator-specific data
- **customers** - Customer profiles with RFM tracking
- **customer_addresses** - Multiple delivery addresses per customer
- **consumer_sessions** - OTP session management
- **otp_codes** - SMS verification codes

### Restaurant Tables
- **restaurants** - Restaurant configuration
- **business_hours** - Day-specific operating hours
- **delivery_zones** - Geographic delivery boundaries

### Menu Tables
- **categories** - Menu categories
- **products** - Menu items with pricing
- **product_variants** - Size/type variations
- **addon_groups** - Add-on categories (required/optional)
- **addons** - Individual add-ons with pricing
- **ingredients** - Ingredient inventory with COGS
- **product_ingredients** - Product-ingredient relationships
- **linked_items** - Recommended/upsell/cross-sell products
- **menu_collections** - Featured collections
- **menu_collection_items** - Collection-product mapping

### Order Tables
- **orders** - Order master records
- **order_items** - Order line items
- **order_item_addons** - Selected add-ons per item
- **delivery_info** - Delivery address and courier info
- **payments** - Payment transactions
- **order_status_history** - Complete audit trail

### Kitchen Tables
- **kitchen_orders** - Kitchen workflow tracking
- **kitchen_order_items** - Item-level preparation status

### Waiter Tables
- **waiters** - Waiter profiles with PIN
- **tables** - Restaurant tables
- **waiter_tables** - Table assignments
- **waiter_orders** - Dine-in orders
- **waiter_order_items** - Dine-in order items
- **waiter_order_events** - Event history

### Courier Tables
- **couriers** - Courier profiles
- **courier_wallets** - Wallet balances
- **courier_wallet_transactions** - Transaction history
- **courier_locations** - GPS tracking
- **courier_tariffs** - Delivery pricing

**Database Migrations**: V1 through V186
**Migration Management**: Flyway (`validate-on-migrate: true`, `baseline-on-migrate: false`); deployments always start from a clean, empty database. The full V1→V186 chain is verified against real PostgreSQL in CI on every push.

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
      access-token-expiration: 900000  # 15 minutes
      refresh-token-expiration: 2592000000  # 30 days
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
mvn test
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
  -e JWT_SECRET=$(openssl rand -hex 32) \
  -e CORS_ORIGINS=https://your-domain \
  elcafe-api:latest
```
> `JWT_SECRET` is required — the app fails closed at boot without it. In the `prod` profile `CORS_ORIGINS` must also be an explicit origin list (no `*`).

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
| JWT_SECRET | JWT signing secret (≥32 bytes; app fails to start if unset or a known committed value) | (none — required) |
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

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📝 License

This repository does not currently include a license file; all rights are reserved by the repository owner unless stated otherwise.

## 📧 Support

For support, email support@qahvoon.uz or open an issue in the repository.

## 🎉 Acknowledgments

Built with Spring Boot, PostgreSQL, Redis, and modern Java best practices.

---

## 📖 Documentation index

Quick orientation: **new dev** → this README → [Local Development](./docs/LOCAL_DEVELOPMENT.md) ·
**deploying** → [Launch (production)](./docs/LAUNCH.md) · **integrating** →
[API Reference](./docs/API_REFERENCE.md) or the live Swagger at `/swagger-ui.html`.

### Getting started & deployment
| Doc | Purpose |
|---|---|
| [docs/LOCAL_DEVELOPMENT.md](./docs/LOCAL_DEVELOPMENT.md) | Run the whole stack locally with one command (`run-local.sh`/`.ps1`) |
| [docs/LAUNCH.md](./docs/LAUNCH.md) | Shortest production launch checklist (Docker + domain) |
| [DOCKER_DEPLOYMENT.md](./DOCKER_DEPLOYMENT.md) | Detailed Docker Compose deployment (env, HTTPS via nginx-proxy, backups) |
| [PRODUCTION_SETUP.md](./PRODUCTION_SETUP.md) | Full production reference (env table, monitoring, SSL, troubleshooting) |
| [docs/DEPLOYMENT_TOPOLOGY.md](./docs/DEPLOYMENT_TOPOLOGY.md) | Single-node architecture, ShedLock/scheduling, scaling guidance |
| [frontend/README.md](./frontend/README.md) | Frontend (React) dev setup, scripts, admin/customer apps |
| [print-agent/README.md](./print-agent/README.md) | Thermal-print agent — install, config, `AGENT_TOKEN` auth |
| [print-agent/QUICK-START.md](./print-agent/QUICK-START.md) | Print agent condensed setup + token minting |

### API reference
| Doc | Purpose |
|---|---|
| [docs/API_REFERENCE.md](./docs/API_REFERENCE.md) | Full REST endpoint catalog (paths, roles, error envelope) |
| [docs/API_DOCUMENTATION.md](./docs/API_DOCUMENTATION.md) | Waiter/table API + response/error/rate-limit conventions |
| [docs/FOOD_ORDERING_API.md](./docs/FOOD_ORDERING_API.md) | Ordering, kitchen, courier, token-gated public tracking |
| [README_API_INTEGRATION.md](./README_API_INTEGRATION.md) | Integration guide with React/fetch examples |
| [postman/README.md](./postman/README.md) | Postman collection walkthrough |
| Live API | Swagger UI at `/swagger-ui.html`, OpenAPI at `/api-docs` |

### Feature guides
| Doc | Purpose |
|---|---|
| [docs/WAITER_MODULE.md](./docs/WAITER_MODULE.md) | Waiter system — PIN auth, orders, tables, WebSocket events |
| [docs/WAITER_QUICKSTART.md](./docs/WAITER_QUICKSTART.md) | Waiter setup/API cheat-sheet |
| [docs/INVENTORY_MANAGEMENT.md](./docs/INVENTORY_MANAGEMENT.md) | Inventory — ingredients, stock, transactions, valuation, batches |
| [docs/LOYALTY_SYSTEM.md](./docs/LOYALTY_SYSTEM.md) | Loyalty tiers, bonuses, wallet, milestones, QR |
| [docs/CUSTOMER_PROFILE.md](./docs/CUSTOMER_PROFILE.md) | Customer 360: preferences, purchases, cross-channel conversation history |
| [docs/FLOOR_MAP.md](./docs/FLOOR_MAP.md) | Live floor map — drawn room, derived occupancy, layout editor, split role gate |
| [frontend/src/pos/README.md](./frontend/src/pos/README.md) | POS frontend (SinglePagePOS, customer display) |
| [BRANDING.md](./BRANDING.md) | White-label branding config (`BRAND_*` / `VITE_BRAND_*`) |

### Subscription & billing
| Doc | Purpose |
|---|---|
| [SUBSCRIPTION_IMPLEMENTATION_PLAN.md](./SUBSCRIPTION_IMPLEMENTATION_PLAN.md) | Billing/subscription design + landed-vs-deferred status |
| [docs/subscription-tiers-plan.md](./docs/subscription-tiers-plan.md) | Plan tiers + feature-gating design |
| [docs/SUBSCRIPTION_ENFORCE_FLIP_RUNBOOK.md](./docs/SUBSCRIPTION_ENFORCE_FLIP_RUNBOOK.md) | Runbook for the subscription enforcement gate |

### Security, hardening & ops runbooks
| Doc | Purpose |
|---|---|
| [docs/PRODUCTION_READINESS_AUDIT.md](./docs/PRODUCTION_READINESS_AUDIT.md) | Current production-hardening status (live reference) |
| [docs/RBAC_AUDIT.md](./docs/RBAC_AUDIT.md) | RBAC audit & the 24 remediated defects |
| [docs/ERROR_HANDLING_PLAN.md](./docs/ERROR_HANDLING_PLAN.md) | Cross-stack error-handling plan + status |
| [docs/TENANT_ENFORCE_FLIP_RUNBOOK.md](./docs/TENANT_ENFORCE_FLIP_RUNBOOK.md) | Tenant-isolation enforce runbook (enforce is default) |
| [MEMORY_OPTIMIZATION_PLAN.md](./MEMORY_OPTIMIZATION_PLAN.md) | JVM/DB memory tuning analysis (historical; items shipped) |

### Shipped feature plans (historical design records)
[auto-add-packaging](./docs/auto-add-packaging-plan.md) ·
[customer-facing-display](./docs/customer-facing-display-plan.md) ·
[customer-reviews](./docs/customer-reviews-plan.md) ·
[inventory-categories](./docs/inventory-categories-plan.md) ·
[production-batch-system](./docs/production-batch-system-plan.md) ·
[shift-based-operations](./docs/shift-based-operations-plan.md) ·
[variant-batch-deduction](./docs/variant-batch-deduction-plan.md)

### History
- [CHANGELOG.md](./CHANGELOG.md) — append-only release/change history

---

**Version**: 1.0.0
**Last Updated**: 2026-07-21
**Built with**: ☕ Java 21 + 🍃 Spring Boot 3.3.0
**API Endpoints**: 250+
**Migrations**: V1–V186
**Modules**: ~30 (auth, restaurant, menu, order, kitchen, waiter, courier, customer, analytics, inventory, financial, loyalty, billing, pos, shift, reservation, review, promotion, referral, sms/telegram/instagram marketing, push, ownerbot, selfservice, files, …)
