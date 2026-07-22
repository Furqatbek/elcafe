# Client-Restaurant Interaction Flow
**Single Restaurant System - Complete Backend Documentation**

---

## Table of Contents
1. [System Overview](#system-overview)
2. [High-Level Architecture](#high-level-architecture)
3. [Sequence Diagrams](#sequence-diagrams)
4. [Database Schema](#database-schema)
5. [API Endpoints](#api-endpoints)
6. [Order Status State Machine](#order-status-state-machine)
7. [WebSocket Events](#websocket-events)
8. [Authentication & Security](#authentication--security)
9. [Business Logic & Rules](#business-logic--rules)
10. [Error Handling](#error-handling)
11. [Background Jobs](#background-jobs)
12. [Analytics](#analytics)

---

## 1. System Overview

### Architecture Type
**Monolithic Spring Boot Application** with:
- RESTful API for synchronous operations
- WebSocket/STOMP for real-time updates
- Redis for caching and session management
- PostgreSQL for persistent storage
- Background job scheduler for async tasks

### Key Actors
1. **Mobile Client (Customer)** - Orders food, tracks orders
2. **Restaurant Admin** - Manages orders, menu, settings
3. **Backend System** - Orchestrates all operations
4. **Payment Gateway** - Handles payment processing
5. **Notification Service** - SMS/Push notifications

### Core Features
- Single restaurant with multiple menu categories
- Customer authentication via OTP (phone number)
- Admin authentication via email/password
- Real-time order updates via WebSocket
- Complete order lifecycle management
- Payment integration
- Order analytics and reporting

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                             │
├─────────────────────────────────────────────────────────────────┤
│  Mobile App (React Native)    │    Admin Panel (React Web)      │
│  - Customer Interface          │    - Restaurant Management      │
│  - Order Tracking              │    - Order Management           │
│  - Menu Browsing               │    - Analytics Dashboard        │
└─────────────┬────────────────────────────────┬──────────────────┘
              │                                 │
              │ REST API + WebSocket            │ REST API + WebSocket
              │                                 │
┌─────────────▼─────────────────────────────────▼──────────────────┐
│                    BACKEND API LAYER (Spring Boot)               │
├──────────────────────────────────────────────────────────────────┤
│  Controllers:                                                     │
│  ├─ Consumer Auth Controller    (OTP-based authentication)       │
│  ├─ Admin Auth Controller       (Email/Password authentication)  │
│  ├─ Menu Controller             (Public menu access)             │
│  ├─ Order Controller            (Order CRUD operations)          │
│  ├─ Payment Controller          (Payment processing)             │
│  └─ Analytics Controller        (Reports & statistics)           │
│                                                                   │
│  WebSocket:                                                       │
│  └─ Order Status Updates        (Real-time notifications)        │
├──────────────────────────────────────────────────────────────────┤
│                    SERVICE LAYER                                  │
├──────────────────────────────────────────────────────────────────┤
│  ├─ ConsumerAuthService         (Customer authentication)        │
│  ├─ AuthService                 (Admin authentication)           │
│  ├─ MenuService                 (Menu management + caching)      │
│  ├─ OrderService                (Order lifecycle management)     │
│  ├─ PaymentService              (Payment integration)            │
│  ├─ NotificationService         (SMS/Push notifications)         │
│  └─ AnalyticsService            (Business intelligence)          │
├──────────────────────────────────────────────────────────────────┤
│                    DATA ACCESS LAYER                              │
├──────────────────────────────────────────────────────────────────┤
│  JPA Repositories + Custom Queries                                │
└──────────────┬───────────────────────────┬───────────────────────┘
               │                           │
               │                           │
┌──────────────▼─────────┐   ┌────────────▼────────────┐
│   PostgreSQL Database  │   │    Redis Cache          │
│   - Persistent Data    │   │    - Menu Cache         │
│   - Transactional      │   │    - Session Store      │
└────────────────────────┘   │    - Rate Limiting      │
                             └─────────────────────────┘
               │
┌──────────────▼─────────────────────────────────────────┐
│              EXTERNAL SERVICES                         │
├────────────────────────────────────────────────────────┤
│  - Payment Gateway (Stripe/PayPal)                     │
│  - SMS Service (Eskiz.uz)                              │
│  - Push Notification Service (FCM)                     │
└────────────────────────────────────────────────────────┘
```

---

## 3. Sequence Diagrams

### 3.1 Customer Registration & Login Flow

```
Mobile App          Backend API         SMS Service       Database
    │                   │                    │               │
    │  POST /api/v1/    │                    │               │
    │  consumers/       │                    │               │
    │  request-otp      │                    │               │
    ├──────────────────>│                    │               │
    │                   │ Generate 6-digit   │               │
    │                   │ OTP code           │               │
    │                   │ Store in DB        │               │
    │                   ├───────────────────────────────────>│
    │                   │                    │               │
    │                   │ Send SMS with OTP  │               │
    │                   ├───────────────────>│               │
    │                   │                    │ Send SMS      │
    │                   │                    ├──────────> Customer
    │                   │                    │               │
    │   200 OK          │                    │               │
    │   {otpSent:true}  │                    │               │
    │<──────────────────┤                    │               │
    │                   │                    │               │
Customer receives SMS  │                    │               │
    │                   │                    │               │
    │  POST /api/v1/    │                    │               │
    │  consumers/       │                    │               │
    │  verify-otp       │                    │               │
    │  {phone, otp}     │                    │               │
    ├──────────────────>│                    │               │
    │                   │ Verify OTP         │               │
    │                   │ Check expiration   │               │
    │                   │ Check attempts     │               │
    │                   ├───────────────────────────────────>│
    │                   │                    │               │
    │                   │ Create/Update      │               │
    │                   │ Consumer record    │               │
    │                   ├───────────────────────────────────>│
    │                   │                    │               │
    │                   │ Generate JWT       │               │
    │                   │ access + refresh   │               │
    │                   │                    │               │
    │   200 OK          │                    │               │
    │   {accessToken,   │                    │               │
    │    refreshToken,  │                    │               │
    │    consumer:{}}   │                    │               │
    │<──────────────────┤                    │               │
    │                   │                    │               │
    │ Store tokens in   │                    │               │
    │ local storage     │                    │               │
    │                   │                    │               │
```

### 3.2 Menu Browsing Flow

```
Mobile App          Backend API         Redis Cache       Database
    │                   │                    │               │
    │  GET /api/v1/     │                    │               │
    │  menu/public/1    │                    │               │
    ├──────────────────>│                    │               │
    │                   │ Check Redis cache  │               │
    │                   ├───────────────────>│               │
    │                   │                    │               │
    │                   │ Cache HIT?         │               │
    │                   │<───────────────────┤               │
    │                   │                    │               │
    │                   │ (If MISS)          │               │
    │                   │ Query database     │               │
    │                   ├───────────────────────────────────>│
    │                   │                    │               │
    │                   │ Get active         │               │
    │                   │ categories +       │               │
    │                   │ products           │               │
    │                   │<───────────────────────────────────┤
    │                   │                    │               │
    │                   │ Store in cache     │               │
    │                   │ (30 min TTL)       │               │
    │                   ├───────────────────>│               │
    │                   │                    │               │
    │   200 OK          │                    │               │
    │   [{category:     │                    │               │
    │     {products[]}} │                    │               │
    │   }]              │                    │               │
    │<──────────────────┤                    │               │
    │                   │                    │               │
    │ Display menu      │                    │               │
    │ Categories &      │                    │               │
    │ Products          │                    │               │
    │                   │                    │               │
```

### 3.3 Complete Order Placement Flow

```
Mobile App      Backend API     Database    Payment Gateway   WebSocket    Admin Panel
    │               │               │               │              │              │
    │ Add items     │               │               │              │              │
    │ to cart       │               │               │              │              │
    │ (local)       │               │               │              │              │
    │               │               │               │              │              │
    │ POST /api/v1/ │               │               │              │              │
    │ orders        │               │               │              │              │
    │ {items[],     │               │               │              │              │
    │  deliveryAddr,│               │               │              │              │
    │  paymentMethod│               │               │              │              │
    │ }             │               │               │              │              │
    ├──────────────>│               │               │              │              │
    │               │ Validate JWT  │               │              │              │
    │               │ Get consumer  │               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Validate:     │               │              │              │
    │               │ - Restaurant  │               │              │              │
    │               │   accepting   │               │              │              │
    │               │ - Products    │               │              │              │
    │               │   available   │               │              │              │
    │               │ - Prices      │               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Calculate:    │               │              │              │
    │               │ - Subtotal    │               │              │              │
    │               │ - Delivery    │               │              │              │
    │               │ - Tax         │               │              │              │
    │               │ - Total       │               │              │              │
    │               │               │               │              │              │
    │               │ Create Order  │               │              │              │
    │               │ status=PENDING│               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Create        │               │              │              │
    │               │ order_items   │               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Initiate      │               │              │              │
    │               │ Payment       │               │              │              │
    │               ├──────────────────────────────>│              │              │
    │               │               │               │              │              │
    │               │               │   Create      │              │              │
    │               │               │   payment     │              │              │
    │               │               │   intent      │              │              │
    │               │               │<──────────────┤              │              │
    │               │               │               │              │              │
    │               │ Update Order  │               │              │              │
    │               │ payment_intent│               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │   200 OK      │               │               │              │              │
    │   {order:{},  │               │               │              │              │
    │    paymentUrl}│               │               │              │              │
    │<──────────────┤               │               │              │              │
    │               │               │               │              │              │
    │ Redirect to   │               │               │              │              │
    │ payment page  │               │               │              │              │
    ├──────────────────────────────────────────────>│              │              │
    │               │               │               │              │              │
Customer completes  │               │               │              │              │
payment             │               │               │              │              │
    │               │               │               │              │              │
    │               │ Webhook       │               │              │              │
    │               │ payment.      │               │              │              │
    │               │ succeeded     │               │              │              │
    │               │<──────────────────────────────┤              │              │
    │               │               │               │              │              │
    │               │ Verify        │               │              │              │
    │               │ signature     │               │              │              │
    │               │               │               │              │              │
    │               │ Update Order  │               │              │              │
    │               │ status=PLACED │               │              │              │
    │               │ payment=      │               │              │              │
    │               │ COMPLETED     │               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Send WS event │               │              │              │
    │               │ "order.placed"│               │              │              │
    │               ├──────────────────────────────────────────────>│              │
    │               │               │               │              │              │
    │               │               │               │              │ NEW ORDER!   │
    │               │               │               │              ├─────────────>│
    │               │               │               │              │              │
    │   WS event    │               │               │              │              │
    │   "order.     │               │               │              │              │
    │   placed"     │               │               │              │              │
    │<──────────────┤               │               │              │              │
    │               │               │               │              │              │
    │ Show "Order   │               │               │              │              │
    │ Placed"       │               │               │              │              │
    │ notification  │               │               │              │              │
    │               │               │               │              │              │
```

### 3.4 Order Status Update Flow (Admin Actions)

```
Admin Panel     Backend API     Database    WebSocket    Mobile App
    │               │               │              │            │
    │ Click         │               │              │            │
    │ "Accept Order"│               │              │            │
    │               │               │              │            │
    │ PATCH /api/v1/│               │              │            │
    │ orders/123/   │               │              │            │
    │ status        │               │              │            │
    │ {status:      │               │              │            │
    │  ACCEPTED}    │               │              │            │
    ├──────────────>│               │              │            │
    │               │ Validate JWT  │              │            │
    │               │ Check role=   │              │            │
    │               │ ADMIN/OPERATOR│              │            │
    │               │               │              │            │
    │               │ Get Order     │              │            │
    │               ├──────────────>│              │            │
    │               │               │              │            │
    │               │ Validate      │              │            │
    │               │ state         │              │            │
    │               │ transition:   │              │            │
    │               │ PLACED →      │              │            │
    │               │ ACCEPTED ✓    │              │            │
    │               │               │              │            │
    │               │ Update status │              │            │
    │               │ + timestamp   │              │            │
    │               ├──────────────>│              │            │
    │               │               │              │            │
    │               │ Create event  │              │            │
    │               │ order_events  │              │            │
    │               ├──────────────>│              │            │
    │               │               │              │            │
    │               │ Publish WS    │              │            │
    │               │ "order.       │              │            │
    │               │ accepted"     │              │            │
    │               ├─────────────────────────────>│            │
    │               │               │              │            │
    │               │               │              │ Order      │
    │               │               │              │ Accepted!  │
    │               │               │              ├───────────>│
    │               │               │              │            │
    │   200 OK      │               │              │            │
    │   {order:{}}  │               │              │            │
    │<──────────────┤               │              │            │
    │               │               │              │            │
    │ Update UI     │               │              │            │
    │ Order status  │               │              │            │
    │ = Accepted    │               │              │            │
    │               │               │              │            │
```

### 3.5 Order Cancellation Flow

```
Mobile App      Backend API     Database    Payment Gateway   WebSocket    Admin Panel
    │               │               │               │              │              │
    │ Click         │               │               │              │              │
    │ "Cancel Order"│               │               │              │              │
    │               │               │               │              │              │
    │ POST /api/v1/ │               │               │              │              │
    │ orders/123/   │               │               │              │              │
    │ cancel        │               │               │              │              │
    │ {reason: ""}  │               │               │              │              │
    ├──────────────>│               │               │              │              │
    │               │ Validate JWT  │               │              │              │
    │               │               │               │              │              │
    │               │ Get Order     │               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Check if      │               │              │              │
    │               │ cancellable:  │               │              │              │
    │               │ - Status in   │               │              │              │
    │               │   [PLACED,    │               │              │              │
    │               │    ACCEPTED,  │               │              │              │
    │               │    PREPARING] │               │              │              │
    │               │ - Within 5min │               │              │              │
    │               │   of placement│               │              │              │
    │               │               │               │              │              │
    │               │ Update status │               │              │              │
    │               │ = CANCELLED   │               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Initiate      │               │              │              │
    │               │ Refund        │               │              │              │
    │               ├──────────────────────────────>│              │              │
    │               │               │               │              │              │
    │               │               │   Process     │              │              │
    │               │               │   refund      │              │              │
    │               │               │<──────────────┤              │              │
    │               │               │               │              │              │
    │               │ Update        │               │              │              │
    │               │ payment_status│               │              │              │
    │               │ = REFUNDED    │               │              │              │
    │               ├──────────────>│               │              │              │
    │               │               │               │              │              │
    │               │ Send WS event │               │              │              │
    │               │ "order.       │               │              │              │
    │               │ cancelled"    │               │              │              │
    │               ├──────────────────────────────────────────────>│              │
    │               │               │               │              │              │
    │               │               │               │              │ Order        │
    │               │               │               │              │ Cancelled    │
    │               │               │               │              ├─────────────>│
    │               │               │               │              │              │
    │   200 OK      │               │               │              │              │
    │   {cancelled: │               │              │              │              │
    │    true,      │               │               │              │              │
    │    refund:{}} │               │               │              │              │
    │<──────────────┤               │               │              │              │
    │               │               │               │              │              │
    │ Show refund   │               │               │              │              │
    │ confirmation  │               │               │              │              │
    │               │               │               │              │              │
```

---

## 4. Database Schema

### 4.1 Entity Relationship Diagram

```
┌─────────────────┐
│   restaurants   │
├─────────────────┤
│ id (PK)         │───────┐
│ name            │       │
│ description     │       │
│ address         │       │
│ phone           │       │
│ email           │       │
│ active          │       │
│ accepting_orders│       │
│ delivery_fee    │       │
│ created_at      │       │
└─────────────────┘       │
                          │
                          │ 1:N
                          │
┌─────────────────────────▼──┐
│      categories            │
├────────────────────────────┤
│ id (PK)                    │───────┐
│ restaurant_id (FK)         │       │
│ name                       │       │
│ description                │       │
│ image_url                  │       │
│ sort_order                 │       │
│ active                     │       │
└────────────────────────────┘       │
                                     │ 1:N
                                     │
┌────────────────────────────────────▼─┐
│           products                   │
├──────────────────────────────────────┤
│ id (PK)                              │
│ category_id (FK)                     │
│ restaurant_id (FK)                   │
│ name                                 │
│ description                          │
│ price                                │
│ price_with_margin                    │
│ image_url                            │
│ item_type                            │
│ status (AVAILABLE/OUT_OF_STOCK)      │
│ in_stock                             │
│ featured                             │
│ sort_order                           │
└──────────────────────────────────────┘


┌─────────────────────┐
│      consumers      │
├─────────────────────┤
│ id (PK)             │───────┐
│ phone_number        │       │
│ first_name          │       │
│ last_name           │       │
│ email               │       │
│ active              │       │
│ phone_verified      │       │
│ created_at          │       │
└─────────────────────┘       │
                              │
                              │ 1:N
                              │
┌─────────────────────────────▼──────┐
│      consumer_addresses            │
├────────────────────────────────────┤
│ id (PK)                            │
│ consumer_id (FK)                   │
│ label                              │
│ street_address                     │
│ building                           │
│ floor                              │
│ apartment                          │
│ latitude                           │
│ longitude                          │
│ city                               │
│ postal_code                        │
│ is_default                         │
└────────────────────────────────────┘


┌─────────────────────┐
│        users        │
├─────────────────────┤
│ id (PK)             │
│ email               │
│ password            │
│ first_name          │
│ last_name           │
│ role (ADMIN/OPERATOR│
│ active              │
│ email_verified      │
└─────────────────────┘


┌──────────────────────────────────────┐
│             orders                   │
├──────────────────────────────────────┤
│ id (PK)                              │
│ restaurant_id (FK)                   │
│ consumer_id (FK)                     │
│ order_number (unique)                │
│ status (enum)                        │
│ order_type (DELIVERY/PICKUP)         │
│ subtotal                             │
│ delivery_fee                         │
│ tax_amount                           │
│ total_amount                         │
│ payment_method                       │
│ payment_status                       │
│ payment_intent_id                    │
│ delivery_address_id (FK)             │
│ notes                                │
│ cancellation_reason                  │
│ placed_at                            │
│ accepted_at                          │
│ preparing_at                         │
│ ready_at                             │
│ picked_up_at                         │
│ completed_at                         │
│ cancelled_at                         │
│ created_at                           │
│ updated_at                           │
└──────────────┬───────────────────────┘
               │
               │ 1:N
               │
┌──────────────▼───────────────────────┐
│          order_items                 │
├──────────────────────────────────────┤
│ id (PK)                              │
│ order_id (FK)                        │
│ product_id (FK)                      │
│ product_name (snapshot)              │
│ quantity                             │
│ unit_price (snapshot)                │
│ total_price                          │
│ notes                                │
└──────────────────────────────────────┘


┌──────────────────────────────────────┐
│          order_events                │
├──────────────────────────────────────┤
│ id (PK)                              │
│ order_id (FK)                        │
│ event_type                           │
│ from_status                          │
│ to_status                            │
│ actor_type (CONSUMER/ADMIN/SYSTEM)   │
│ actor_id                             │
│ notes                                │
│ metadata (JSONB)                     │
│ created_at                           │
└──────────────────────────────────────┘


┌──────────────────────────────────────┐
│         consumer_otps                │
├──────────────────────────────────────┤
│ id (PK)                              │
│ phone_number                         │
│ otp_code                             │
│ expires_at                           │
│ verified                             │
│ attempts                             │
│ created_at                           │
└──────────────────────────────────────┘


┌──────────────────────────────────────┐
│      payment_transactions            │
├──────────────────────────────────────┤
│ id (PK)                              │
│ order_id (FK)                        │
│ payment_intent_id                    │
│ amount                               │
│ currency                             │
│ status                               │
│ payment_method                       │
│ gateway_response (JSONB)             │
│ created_at                           │
│ updated_at                           │
└──────────────────────────────────────┘


┌──────────────────────────────────────┐
│      refund_transactions             │
├──────────────────────────────────────┤
│ id (PK)                              │
│ order_id (FK)                        │
│ payment_transaction_id (FK)          │
│ refund_id                            │
│ amount                               │
│ reason                               │
│ status                               │
│ gateway_response (JSONB)             │
│ created_at                           │
│ updated_at                           │
└──────────────────────────────────────┘
```

### 4.2 Key Tables Definition

#### Orders Table
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    consumer_id BIGINT NOT NULL REFERENCES consumers(id),
    order_number VARCHAR(20) UNIQUE NOT NULL,

    -- Status tracking
    status VARCHAR(20) NOT NULL,
    order_type VARCHAR(20) NOT NULL DEFAULT 'DELIVERY',

    -- Financial
    subtotal DECIMAL(10,2) NOT NULL,
    delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(10,2) NOT NULL,

    -- Payment
    payment_method VARCHAR(50),
    payment_status VARCHAR(20),
    payment_intent_id VARCHAR(255),

    -- Delivery
    delivery_address_id BIGINT REFERENCES consumer_addresses(id),
    notes TEXT,

    -- Cancellation
    cancellation_reason TEXT,
    cancelled_by VARCHAR(20), -- CONSUMER, ADMIN, SYSTEM

    -- Timestamps
    placed_at TIMESTAMP,
    accepted_at TIMESTAMP,
    preparing_at TIMESTAMP,
    ready_at TIMESTAMP,
    picked_up_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_status CHECK (status IN (
        'PENDING', 'PLACED', 'ACCEPTED', 'PREPARING',
        'READY', 'PICKED_UP', 'COMPLETED', 'CANCELLED', 'REJECTED'
    )),
    CONSTRAINT chk_payment_status CHECK (payment_status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REFUNDED'
    ))
);

CREATE INDEX idx_orders_consumer ON orders(consumer_id);
CREATE INDEX idx_orders_restaurant ON orders(restaurant_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_placed_at ON orders(placed_at);
CREATE INDEX idx_orders_order_number ON orders(order_number);
```

#### Order Events Table
```sql
CREATE TABLE order_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    actor_type VARCHAR(20), -- CONSUMER, ADMIN, OPERATOR, SYSTEM
    actor_id BIGINT,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_events_order ON order_events(order_id);
CREATE INDEX idx_order_events_created ON order_events(created_at);
```

---

## 5. API Endpoints

### 5.1 Authentication Endpoints

#### Consumer Authentication

**Request OTP**
```http
POST /api/v1/consumers/request-otp
Content-Type: application/json

{
  "phoneNumber": "+998901234567"
}
```

Response:
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "data": {
    "otpSent": true,
    "expiresIn": 300,
    "canRetryAfter": 60
  }
}
```

**Verify OTP & Login**
```http
POST /api/v1/consumers/verify-otp
Content-Type: application/json

{
  "phoneNumber": "+998901234567",
  "otpCode": "123456"
}
```

Response:
```json
{
  "success": true,
  "message": "Authentication successful",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "accessTokenExpiry": 1733400000000,
    "refreshTokenExpiry": 1735992000000,
    "consumer": {
      "id": 1,
      "phoneNumber": "+998901234567",
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com",
      "phoneVerified": true
    }
  }
}
```

#### Admin Authentication

**Admin Login**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "admin@elcafe.com",
  "password": "$lL%UJxdnR$9G^$E"
}
```

Response:
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "user": {
      "id": 1,
      "email": "admin@elcafe.com",
      "firstName": "Admin",
      "lastName": "User",
      "role": "ADMIN"
    }
  }
}
```

**Refresh Token**
```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGci..."
}
```

Response:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "accessTokenExpiry": 1733400000000
  }
}
```

### 5.2 Menu Endpoints (Public)

**Get Restaurant Info**
```http
GET /api/v1/restaurants/1
```

Response:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "El Cafe",
    "description": "Best coffee and food in town",
    "address": "123 Main Street",
    "city": "New York",
    "phone": "+1234567890",
    "email": "info@elcafe.com",
    "active": true,
    "acceptingOrders": true,
    "deliveryFee": 5.00,
    "estimatedDeliveryTime": 30,
    "businessHours": [
      {
        "dayOfWeek": "MONDAY",
        "openTime": "09:00",
        "closeTime": "22:00",
        "closed": false
      }
    ]
  }
}
```

**Get Public Menu**
```http
GET /api/v1/menu/public/1
```

Response:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "Beverages",
      "description": "Hot and cold drinks",
      "imageUrl": "https://example.com/beverages.jpg",
      "sortOrder": 1,
      "active": true,
      "products": [
        {
          "id": 5,
          "name": "Cappuccino",
          "description": "Classic Italian coffee with steamed milk",
          "imageUrl": "https://example.com/cappuccino.jpg",
          "price": 4.50,
          "priceWithMargin": 5.00,
          "itemType": "BEVERAGE",
          "sortOrder": 1,
          "status": "AVAILABLE",
          "inStock": true,
          "featured": true,
          "hasVariants": false
        }
      ]
    },
    {
      "id": 2,
      "name": "Food",
      "description": "Breakfast, lunch, and snacks",
      "products": [...]
    }
  ]
}
```

**Get Active Categories**
```http
GET /api/v1/categories?restaurantId=1
```

Response:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "Beverages",
      "description": "Hot and cold drinks",
      "imageUrl": "https://example.com/beverages.jpg",
      "sortOrder": 1,
      "active": true
    },
    {
      "id": 2,
      "name": "Food",
      "description": "Breakfast, lunch, and snacks",
      "sortOrder": 2,
      "active": true
    }
  ]
}
```

### 5.3 Order Endpoints

**Create Order**
```http
POST /api/v1/orders
Authorization: Bearer {consumer_access_token}
Content-Type: application/json

{
  "restaurantId": 1,
  "orderType": "DELIVERY",
  "deliveryAddressId": 5,
  "paymentMethod": "CARD",
  "notes": "Please ring the doorbell",
  "items": [
    {
      "productId": 5,
      "quantity": 2,
      "notes": "Extra hot"
    },
    {
      "productId": 12,
      "quantity": 1
    }
  ]
}
```

Response:
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "order": {
      "id": 123,
      "orderNumber": "ORD-20251204-0123",
      "restaurantId": 1,
      "consumerId": 45,
      "status": "PENDING",
      "orderType": "DELIVERY",
      "subtotal": 15.00,
      "deliveryFee": 5.00,
      "taxAmount": 1.50,
      "totalAmount": 21.50,
      "paymentMethod": "CARD",
      "paymentStatus": "PENDING",
      "paymentIntentId": "pi_3Abc123...",
      "deliveryAddress": {
        "id": 5,
        "streetAddress": "456 Oak Avenue",
        "building": "Building A",
        "floor": "3",
        "apartment": "12",
        "city": "New York"
      },
      "notes": "Please ring the doorbell",
      "items": [
        {
          "id": 456,
          "productId": 5,
          "productName": "Cappuccino",
          "quantity": 2,
          "unitPrice": 5.00,
          "totalPrice": 10.00,
          "notes": "Extra hot"
        },
        {
          "id": 457,
          "productId": 12,
          "productName": "Croissant",
          "quantity": 1,
          "unitPrice": 5.00,
          "totalPrice": 5.00
        }
      ],
      "createdAt": "2025-12-04T12:00:00Z"
    },
    "paymentUrl": "https://payment-gateway.com/checkout/pi_3Abc123..."
  }
}
```

**Get Order by ID**
```http
GET /api/v1/orders/123
Authorization: Bearer {consumer_access_token}
```

Response:
```json
{
  "success": true,
  "data": {
    "id": 123,
    "orderNumber": "ORD-20251204-0123",
    "status": "ACCEPTED",
    "orderType": "DELIVERY",
    "subtotal": 15.00,
    "deliveryFee": 5.00,
    "taxAmount": 1.50,
    "totalAmount": 21.50,
    "paymentMethod": "CARD",
    "paymentStatus": "COMPLETED",
    "deliveryAddress": {...},
    "items": [...],
    "placedAt": "2025-12-04T12:05:00Z",
    "acceptedAt": "2025-12-04T12:06:30Z",
    "estimatedDeliveryTime": "2025-12-04T12:35:00Z",
    "createdAt": "2025-12-04T12:00:00Z",
    "updatedAt": "2025-12-04T12:06:30Z"
  }
}
```

**Get Consumer Orders (History)**
```http
GET /api/v1/orders?page=0&size=20&status=COMPLETED
Authorization: Bearer {consumer_access_token}
```

Response:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 123,
        "orderNumber": "ORD-20251204-0123",
        "status": "COMPLETED",
        "totalAmount": 21.50,
        "items": [...],
        "placedAt": "2025-12-04T12:00:00Z",
        "completedAt": "2025-12-04T12:45:00Z"
      }
    ],
    "totalElements": 15,
    "totalPages": 1,
    "currentPage": 0,
    "size": 20
  }
}
```

**Cancel Order**
```http
POST /api/v1/orders/123/cancel
Authorization: Bearer {consumer_access_token}
Content-Type: application/json

{
  "reason": "Changed my mind"
}
```

Response:
```json
{
  "success": true,
  "message": "Order cancelled successfully",
  "data": {
    "orderId": 123,
    "status": "CANCELLED",
    "refund": {
      "amount": 21.50,
      "status": "PROCESSING",
      "estimatedArrival": "2025-12-11T12:00:00Z"
    },
    "cancelledAt": "2025-12-04T12:08:00Z"
  }
}
```

### 5.4 Admin Order Management Endpoints

**Get All Orders (Admin)**
```http
GET /api/v1/admin/orders?page=0&size=50&status=PLACED&date=2025-12-04
Authorization: Bearer {admin_access_token}
```

Response:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 123,
        "orderNumber": "ORD-20251204-0123",
        "consumer": {
          "id": 45,
          "phoneNumber": "+998901234567",
          "firstName": "John",
          "lastName": "Doe"
        },
        "status": "PLACED",
        "orderType": "DELIVERY",
        "totalAmount": 21.50,
        "items": [...],
        "deliveryAddress": {...},
        "placedAt": "2025-12-04T12:00:00Z"
      }
    ],
    "totalElements": 8,
    "totalPages": 1
  }
}
```

**Update Order Status (Admin)**
```http
PATCH /api/v1/admin/orders/123/status
Authorization: Bearer {admin_access_token}
Content-Type: application/json

{
  "status": "ACCEPTED"
}
```

Response:
```json
{
  "success": true,
  "message": "Order status updated successfully",
  "data": {
    "id": 123,
    "status": "ACCEPTED",
    "acceptedAt": "2025-12-04T12:06:30Z",
    "estimatedDeliveryTime": "2025-12-04T12:35:00Z"
  }
}
```

**Reject Order (Admin)**
```http
POST /api/v1/admin/orders/123/reject
Authorization: Bearer {admin_access_token}
Content-Type: application/json

{
  "reason": "Items out of stock"
}
```

Response:
```json
{
  "success": true,
  "message": "Order rejected and refund initiated",
  "data": {
    "orderId": 123,
    "status": "REJECTED",
    "refund": {
      "amount": 21.50,
      "status": "PROCESSING"
    },
    "rejectedAt": "2025-12-04T12:07:00Z"
  }
}
```

### 5.5 Payment Endpoints

**Payment Webhook (External)**
```http
POST /api/v1/payments/webhook
Content-Type: application/json
Stripe-Signature: t=1733320000,v1=abc123...

{
  "type": "payment_intent.succeeded",
  "data": {
    "object": {
      "id": "pi_3Abc123...",
      "amount": 2150,
      "currency": "usd",
      "metadata": {
        "orderId": "123"
      }
    }
  }
}
```

Response:
```json
{
  "received": true
}
```

### 5.6 Analytics Endpoints (Admin)

**Daily Summary**
```http
GET /api/v1/admin/analytics/daily?date=2025-12-04
Authorization: Bearer {admin_access_token}
```

Response:
```json
{
  "success": true,
  "data": {
    "date": "2025-12-04",
    "totalOrders": 42,
    "completedOrders": 38,
    "cancelledOrders": 3,
    "rejectedOrders": 1,
    "totalRevenue": 1250.50,
    "averageOrderValue": 32.91,
    "averagePreparationTime": 18.5,
    "topProducts": [
      {
        "productId": 5,
        "productName": "Cappuccino",
        "quantitySold": 87,
        "revenue": 435.00
      }
    ]
  }
}
```

**Revenue Report**
```http
GET /api/v1/admin/analytics/revenue?startDate=2025-12-01&endDate=2025-12-07
Authorization: Bearer {admin_access_token}
```

Response:
```json
{
  "success": true,
  "data": {
    "period": {
      "start": "2025-12-01",
      "end": "2025-12-07"
    },
    "totalRevenue": 8750.00,
    "totalOrders": 275,
    "averageOrderValue": 31.82,
    "dailyBreakdown": [
      {
        "date": "2025-12-01",
        "revenue": 1200.00,
        "orders": 38
      },
      {
        "date": "2025-12-02",
        "revenue": 1350.50,
        "orders": 42
      }
    ]
  }
}
```

---

## 6. Order Status State Machine

### 6.1 State Diagram

```
                    ┌──────────────┐
                    │   PENDING    │ (Payment pending)
                    └──────┬───────┘
                           │
                  Payment completed
                           │
                    ┌──────▼───────┐
           ┌────────│    PLACED    │────────┐
           │        └──────┬───────┘        │
           │               │                │
    Admin rejects    Admin accepts    Consumer cancels
           │               │           (within 5 min)
           │        ┌──────▼───────┐        │
           │        │   ACCEPTED   │        │
           │        └──────┬───────┘        │
           │               │                │
           │      Kitchen starts            │
           │               │                │
           │        ┌──────▼───────┐        │
           │        │  PREPARING   │        │
           │        └──────┬───────┘        │
           │               │                │
           │      Food ready                │
           │               │                │
           │        ┌──────▼───────┐        │
           │        │    READY     │        │
           │        └──────┬───────┘        │
           │               │                │
           │    Driver picks up             │
           │               │                │
           │        ┌──────▼───────┐        │
           │        │  PICKED_UP   │        │
           │        └──────┬───────┘        │
           │               │                │
           │    Delivery completed          │
           │               │                │
           │        ┌──────▼───────┐        │
           │        │  COMPLETED   │        │
           │        └──────────────┘        │
           │                                │
           │        ┌──────────────┐        │
           └───────>│   REJECTED   │<───────┘
                    └──────────────┘
                           ▲
                           │
                    ┌──────┴───────┐
                    │  CANCELLED   │
                    └──────────────┘
```

### 6.2 State Transitions Table

| From Status | To Status   | Trigger             | Actor          | Conditions                                    |
|-------------|-------------|---------------------|----------------|-----------------------------------------------|
| PENDING     | PLACED      | Payment succeeded   | System         | Payment completed successfully                |
| PENDING     | CANCELLED   | Payment timeout     | System         | Payment not completed within 15 minutes       |
| PLACED      | ACCEPTED    | Admin accepts       | Admin/Operator | Restaurant is accepting orders                |
| PLACED      | REJECTED    | Admin rejects       | Admin/Operator | Items unavailable, other issues               |
| PLACED      | CANCELLED   | Consumer cancels    | Consumer       | Within 5 minutes of placement                 |
| ACCEPTED    | PREPARING   | Kitchen starts      | Admin/Operator | Order handed to kitchen                       |
| ACCEPTED    | CANCELLED   | Admin cancels       | Admin/Operator | Emergency situations only                     |
| PREPARING   | READY       | Food prepared       | Admin/Operator | All items completed                           |
| READY       | PICKED_UP   | Driver collects     | Admin/Operator | For delivery orders                           |
| READY       | COMPLETED   | Customer collects   | Admin/Operator | For pickup orders                             |
| PICKED_UP   | COMPLETED   | Delivery done       | Admin/Operator | Customer received order                       |
| ANY         | REJECTED    | Critical issue      | Admin          | Payment failed, force majeure                 |

### 6.3 Business Rules

**Cancellation Rules:**
- Consumer can cancel only if status is PLACED or ACCEPTED and within 5 minutes of placement
- Admin can cancel PLACED or ACCEPTED orders anytime with reason
- Orders in PREPARING, READY, or PICKED_UP cannot be cancelled by consumer
- All cancellations trigger automatic refund

**Acceptance Rules:**
- Orders must be accepted within 10 minutes of placement
- Auto-rejection after 10 minutes if not accepted
- Acceptance sets estimated delivery/pickup time

**Status Update Rules:**
- Each status change creates an order_event record
- Timestamps are recorded for each status change
- WebSocket notification sent on every status change
- SMS notification sent for key milestones (ACCEPTED, READY, COMPLETED)

---

## 7. WebSocket Events

### 7.1 Connection Setup

**Client Connection (Consumer)**
```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect(
  { Authorization: `Bearer ${accessToken}` },
  (frame) => {
    console.log('Connected:', frame);

    // Subscribe to personal order updates
    stompClient.subscribe('/user/topic/orders', (message) => {
      const event = JSON.parse(message.body);
      handleOrderUpdate(event);
    });
  }
);
```

**Admin Connection**
```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect(
  { Authorization: `Bearer ${adminAccessToken}` },
  (frame) => {
    console.log('Connected:', frame);

    // Subscribe to all restaurant orders
    stompClient.subscribe('/topic/restaurant/1/orders', (message) => {
      const event = JSON.parse(message.body);
      handleNewOrder(event);
    });
  }
);
```

### 7.2 Event Types

#### order.placed
**Sent to:** Admin panel
**Trigger:** Consumer completes payment
```json
{
  "eventType": "order.placed",
  "timestamp": "2025-12-04T12:05:00Z",
  "data": {
    "orderId": 123,
    "orderNumber": "ORD-20251204-0123",
    "consumer": {
      "id": 45,
      "phoneNumber": "+998901234567",
      "firstName": "John"
    },
    "orderType": "DELIVERY",
    "totalAmount": 21.50,
    "itemCount": 3,
    "deliveryAddress": {
      "streetAddress": "456 Oak Avenue",
      "building": "Building A"
    },
    "placedAt": "2025-12-04T12:05:00Z"
  }
}
```

#### order.accepted
**Sent to:** Consumer
**Trigger:** Admin accepts order
```json
{
  "eventType": "order.accepted",
  "timestamp": "2025-12-04T12:06:30Z",
  "data": {
    "orderId": 123,
    "orderNumber": "ORD-20251204-0123",
    "status": "ACCEPTED",
    "acceptedAt": "2025-12-04T12:06:30Z",
    "estimatedDeliveryTime": "2025-12-04T12:35:00Z"
  }
}
```

#### order.preparing
**Sent to:** Consumer
**Trigger:** Kitchen starts preparation
```json
{
  "eventType": "order.preparing",
  "timestamp": "2025-12-04T12:10:00Z",
  "data": {
    "orderId": 123,
    "status": "PREPARING",
    "preparingAt": "2025-12-04T12:10:00Z",
    "estimatedReadyTime": "2025-12-04T12:25:00Z"
  }
}
```

#### order.ready
**Sent to:** Consumer
**Trigger:** Order preparation completed
```json
{
  "eventType": "order.ready",
  "timestamp": "2025-12-04T12:25:00Z",
  "data": {
    "orderId": 123,
    "status": "READY",
    "readyAt": "2025-12-04T12:25:00Z",
    "message": "Your order is ready for pickup/delivery"
  }
}
```

#### order.picked_up
**Sent to:** Consumer
**Trigger:** Driver picks up order
```json
{
  "eventType": "order.picked_up",
  "timestamp": "2025-12-04T12:27:00Z",
  "data": {
    "orderId": 123,
    "status": "PICKED_UP",
    "pickedUpAt": "2025-12-04T12:27:00Z",
    "estimatedDeliveryTime": "2025-12-04T12:45:00Z"
  }
}
```

#### order.completed
**Sent to:** Consumer
**Trigger:** Order delivered/picked up by customer
```json
{
  "eventType": "order.completed",
  "timestamp": "2025-12-04T12:45:00Z",
  "data": {
    "orderId": 123,
    "status": "COMPLETED",
    "completedAt": "2025-12-04T12:45:00Z",
    "message": "Thank you for your order!"
  }
}
```

#### order.cancelled
**Sent to:** Consumer & Admin
**Trigger:** Order cancelled by consumer or admin
```json
{
  "eventType": "order.cancelled",
  "timestamp": "2025-12-04T12:08:00Z",
  "data": {
    "orderId": 123,
    "status": "CANCELLED",
    "cancelledBy": "CONSUMER",
    "reason": "Changed my mind",
    "cancelledAt": "2025-12-04T12:08:00Z",
    "refund": {
      "amount": 21.50,
      "status": "PROCESSING"
    }
  }
}
```

#### order.rejected
**Sent to:** Consumer
**Trigger:** Admin rejects order
```json
{
  "eventType": "order.rejected",
  "timestamp": "2025-12-04T12:07:00Z",
  "data": {
    "orderId": 123,
    "status": "REJECTED",
    "reason": "Items out of stock",
    "rejectedAt": "2025-12-04T12:07:00Z",
    "refund": {
      "amount": 21.50,
      "status": "PROCESSING",
      "estimatedArrival": "2025-12-11T12:07:00Z"
    }
  }
}
```

---

## 8. Authentication & Security

### 8.1 Consumer Authentication Flow

**OTP-Based Authentication:**
1. Consumer enters phone number
2. Backend generates 6-digit OTP code
3. OTP sent via SMS (expires in 5 minutes)
4. Consumer enters OTP code
5. Backend verifies OTP (max 3 attempts)
6. JWT access token (1 hour) and refresh token (30 days) issued
7. Consumer record created/updated

**Security Measures:**
- Rate limiting: Max 3 OTP requests per phone number per hour
- OTP expiration: 5 minutes
- Max attempts: 3 per OTP
- Phone number validation (E.164 format)
- Bcrypt hashing for sensitive data storage

### 8.2 Admin Authentication

**Email/Password Authentication:**
- BCrypt password hashing (cost factor: 10)
- JWT access token (1 hour expiration)
- JWT refresh token (24 hours expiration)
- Password reset via email
- Account lockout after 5 failed attempts

### 8.3 JWT Configuration

```yaml
JWT_SECRET: f54a0f3634b3fb7083d03dfe8f54d090a18be3517a0560bab3eb7c192c56edd1
ACCESS_TOKEN_EXPIRATION: 3600000  # 1 hour
REFRESH_TOKEN_EXPIRATION: 86400000  # 24 hours (admin)
CONSUMER_ACCESS_TOKEN_EXPIRATION: 3600000  # 1 hour
CONSUMER_REFRESH_TOKEN_EXPIRATION: 2592000000  # 30 days
```

**JWT Token Structure:**
```json
{
  "sub": "admin@elcafe.com",
  "iat": 1733316000,
  "exp": 1733319600,
  "role": "ADMIN"
}
```

### 8.4 API Security

**Rate Limiting:**
- Public endpoints: 100 requests/minute per IP
- Authenticated endpoints: 1000 requests/minute per user
- OTP requests: 3 requests/hour per phone number
- Payment webhooks: Signature verification required

**CORS Configuration:**
```yaml
CORS_ORIGINS: http://localhost:3000,https://app.elcafe.com
ALLOWED_METHODS: GET,POST,PUT,PATCH,DELETE,OPTIONS
ALLOWED_HEADERS: *
ALLOW_CREDENTIALS: true
```

**Protected Endpoints:**
- All `/api/v1/orders/**` require authentication
- `/api/v1/admin/**` require ADMIN or OPERATOR role
- `/api/v1/menu/**` (POST/PUT/DELETE) require ADMIN role
- Public endpoints: `/api/v1/menu/public/**`, `/api/v1/categories`, `/api/v1/restaurants/**`

---

## 9. Business Logic & Rules

### 9.1 Order Creation Rules

**Validation:**
1. Restaurant must be active and accepting orders
2. All products must be available and in stock
3. Delivery address required for DELIVERY orders
4. Minimum order amount: $10.00
5. Maximum order amount: $500.00
6. Valid payment method required

**Pricing Calculation:**
```
Subtotal = Sum(product.priceWithMargin × quantity)
Delivery Fee = restaurant.deliveryFee (if DELIVERY)
Tax = Subtotal × 0.075 (7.5%)
Total = Subtotal + Delivery Fee + Tax
```

**Order Number Generation:**
```
Format: ORD-YYYYMMDD-XXXX
Example: ORD-20251204-0123

XXXX = Sequential number for the day
```

### 9.2 Payment Processing

**Payment Flow:**
1. Order created with status PENDING
2. Payment intent created with payment gateway
3. Customer redirected to payment page
4. Payment completed by customer
5. Webhook received from payment gateway
6. Order status updated to PLACED
7. WebSocket event sent to admin panel

**Payment Methods:**
- CARD (Credit/Debit)
- CASH (Pay on delivery - for future implementation)

**Refund Rules:**
- Automatic refund for rejected orders
- Automatic refund for cancelled orders (if eligible)
- Refund amount = total_amount
- Refund processing time: 5-10 business days
- Refund status tracked in refund_transactions table

### 9.3 Delivery Time Estimation

```
Base Time = restaurant.estimatedDeliveryTime (e.g., 30 minutes)
Preparation Time = Average of last 10 orders (e.g., 15 minutes)
Delivery Time = Base Time + Preparation Time
Estimated Delivery = AcceptedAt + Delivery Time
```

### 9.4 Auto-Rejection Rules

**Automatic order rejection if:**
- Not accepted within 10 minutes of placement
- Payment verification fails
- Restaurant goes offline/inactive
- All ordered items become unavailable

**Auto-rejection triggers:**
- Scheduled job checks every minute
- Immediate refund initiated
- Customer notified via SMS and WebSocket
- Order status set to REJECTED

---

## 10. Error Handling

### 10.1 Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "ORDER_NOT_CANCELLABLE",
    "message": "Order cannot be cancelled at this stage",
    "details": "Orders in PREPARING status cannot be cancelled by customers",
    "timestamp": "2025-12-04T12:15:00Z",
    "path": "/api/v1/orders/123/cancel"
  }
}
```

### 10.2 Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| VALIDATION_ERROR | 400 | Invalid request data |
| UNAUTHORIZED | 401 | Missing or invalid token |
| FORBIDDEN | 403 | Insufficient permissions |
| RESOURCE_NOT_FOUND | 404 | Order/Product not found |
| ORDER_NOT_CANCELLABLE | 400 | Order status doesn't allow cancellation |
| PAYMENT_FAILED | 402 | Payment processing failed |
| RESTAURANT_CLOSED | 400 | Restaurant not accepting orders |
| PRODUCT_UNAVAILABLE | 400 | One or more products unavailable |
| OTP_EXPIRED | 400 | OTP code expired |
| OTP_INVALID | 400 | Invalid OTP code |
| MAX_ATTEMPTS_EXCEEDED | 429 | Too many OTP attempts |
| RATE_LIMIT_EXCEEDED | 429 | Too many requests |
| INTERNAL_SERVER_ERROR | 500 | Unexpected server error |

### 10.3 Edge Cases

**Payment Webhook Delays:**
- Retry payment verification after 5 minutes
- Auto-cancel order after 15 minutes if payment not confirmed
- Notify customer of payment status

**WebSocket Connection Loss:**
- Client auto-reconnects with exponential backoff
- Fetch order status on reconnection
- Show cached data until connection restored

**Concurrent Status Updates:**
- Optimistic locking with version field
- Last-write-wins for conflicting updates
- Event log maintains audit trail

**Restaurant Goes Offline:**
- Stop accepting new orders
- Complete in-flight orders
- Notify customers of delays
- Auto-reject pending orders after 10 minutes

**Product Stock Changes:**
- Check stock at order creation
- Admin can mark products out of stock
- Existing orders not affected
- New orders blocked for out-of-stock items

---

## 11. Background Jobs

### 11.1 Job Schedule

**Auto-Rejection Job**
```
Schedule: Every 1 minute
Purpose: Reject orders not accepted within 10 minutes
Logic:
  - Find orders with status=PLACED
  - Check if (NOW - placedAt) > 10 minutes
  - Update status to REJECTED
  - Initiate refund
  - Send notifications
```

**Payment Verification Job**
```
Schedule: Every 5 minutes
Purpose: Verify pending payments
Logic:
  - Find orders with status=PENDING
  - Check payment status with gateway
  - Update order status accordingly
  - Cancel if payment failed after 15 minutes
```

**Notification Retry Job**
```
Schedule: Every 2 minutes
Purpose: Retry failed SMS notifications
Logic:
  - Find failed notification records
  - Retry up to 3 times
  - Exponential backoff (2min, 4min, 8min)
  - Mark as permanently failed after 3 attempts
```

**Order Metrics Job**
```
Schedule: Every hour
Purpose: Calculate and cache order metrics
Logic:
  - Calculate daily totals
  - Update analytics tables
  - Cache popular products
  - Generate summary reports
```

**Cache Cleanup Job**
```
Schedule: Every 6 hours
Purpose: Clean up expired cache entries
Logic:
  - Remove expired Redis keys
  - Clean up old session data
  - Archive old order events (> 90 days)
```

### 11.2 Job Implementation (Spring @Scheduled)

```java
@Component
public class OrderBackgroundJobs {

    @Scheduled(cron = "0 * * * * *") // Every minute
    public void autoRejectExpiredOrders() {
        // Implementation
    }

    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    public void verifyPendingPayments() {
        // Implementation
    }

    @Scheduled(cron = "0 */2 * * * *") // Every 2 minutes
    public void retryFailedNotifications() {
        // Implementation
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void calculateOrderMetrics() {
        // Implementation
    }
}
```

---

## 12. Analytics

### 12.1 Key Metrics

**Real-time Metrics (Cached):**
- Orders today (by status)
- Revenue today
- Active orders count
- Average order value today
- Orders per hour (last 24 hours)

**Daily Metrics:**
- Total orders
- Completed orders
- Cancelled orders percentage
- Rejected orders percentage
- Total revenue
- Average order value
- Average preparation time
- Peak hours
- Top-selling products

**Historical Metrics:**
- Weekly/Monthly revenue trends
- Customer retention rate
- Order completion rate
- Popular order times
- Product performance
- Cancellation reasons analysis

### 12.2 Analytics Endpoints Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/admin/analytics/daily` | GET | Daily summary |
| `/api/v1/admin/analytics/revenue` | GET | Revenue report |
| `/api/v1/admin/analytics/products` | GET | Product performance |
| `/api/v1/admin/analytics/customers` | GET | Customer insights |
| `/api/v1/admin/analytics/trends` | GET | Historical trends |

---

## 13. Implementation Checklist

### Phase 1: Core Foundation
- [x] Database schema design
- [x] User authentication (Admin & Consumer)
- [x] JWT implementation
- [x] Menu management
- [x] Public menu API
- [x] Redis caching

### Phase 2: Order Management
- [x] Order creation
- [x] Order status updates
- [x] Order history
- [ ] Payment integration
- [ ] WebSocket implementation
- [ ] Order cancellation
- [ ] Refund logic

### Phase 3: Real-time Features
- [ ] WebSocket server setup
- [ ] Order status broadcasting
- [ ] Admin panel real-time updates
- [ ] Mobile app real-time updates
- [ ] Notification service (SMS)

### Phase 4: Background Jobs
- [ ] Auto-rejection job
- [ ] Payment verification job
- [ ] Notification retry job
- [ ] Analytics calculation job
- [ ] Cache cleanup job

### Phase 5: Analytics & Reporting
- [ ] Daily analytics
- [ ] Revenue reports
- [ ] Product performance
- [ ] Customer insights
- [ ] Export functionality

### Phase 6: Polish & Optimization
- [ ] Rate limiting
- [ ] Error handling improvements
- [ ] Performance optimization
- [ ] Security audit
- [ ] Load testing
- [ ] Documentation completion

---

## Appendix A: Technology Stack

**Backend:**
- Java 21
- Spring Boot 3.3.0
- Spring Security
- Spring Data JPA
- Spring WebSocket
- PostgreSQL 16
- Redis 7
- Flyway (Migrations)

**Authentication:**
- JWT (JSON Web Tokens)
- BCrypt (Password hashing)
- OTP (SMS-based)

**External Services:**
- Payment Gateway (Stripe/PayPal)
- SMS Service (Eskiz.uz)
- Push Notifications (FCM)

**Tools:**
- Maven (Build)
- Docker (Containerization)
- Postman (API Testing)
- Git (Version Control)

---

## Appendix B: Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=elcafe_db
DB_USER=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=f54a0f3634b3fb7083d03dfe8f54d090a18be3517a0560bab3eb7c192c56edd1

# CORS
CORS_ORIGINS=http://localhost:3000,http://localhost:8080

# SMS Service
ESKIZ_SMS_EMAIL=your-email@example.com
ESKIZ_SMS_PASSWORD=your-password
ESKIZ_SMS_ENABLED=true

# Payment Gateway
PAYMENT_GATEWAY_API_KEY=sk_test_...
PAYMENT_GATEWAY_WEBHOOK_SECRET=whsec_...

# Server
SERVER_PORT=8080
```

---

**Document Version:** 1.0
**Last Updated:** 2025-12-04
**Author:** Claude Code Assistant
**Status:** Complete Implementation Guide
