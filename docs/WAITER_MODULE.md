# Waiter Module Documentation

## Overview

The Waiter Module is a comprehensive restaurant table management system that enables waiters to manage tables, create orders, track order status, and communicate with the kitchen in real-time through WebSocket connections.

## Table of Contents

- [Architecture](#architecture)
- [Database Schema](#database-schema)
- [REST API Endpoints](#rest-api-endpoints)
- [WebSocket Communication](#websocket-communication)
- [Event System](#event-system)
- [Setup & Configuration](#setup--configuration)
- [Usage Examples](#usage-examples)

---

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                      Waiter Module                           │
├─────────────────────────────────────────────────────────────┤
│  Controllers:                                                │
│    ├─ WaiterController         (Waiter CRUD)                │
│    ├─ TableController          (Tables: restaurant module)  │
│    └─ WaiterOrderController    (Order Operations)           │
├─────────────────────────────────────────────────────────────┤
│  Services:                                                   │
│    ├─ WaiterService            (Waiter business logic)      │
│    ├─ TableService             (Table operations)           │
│    ├─ WaiterOrderService       (Order management)           │
│    └─ OrderEventService        (Event tracking)             │
├─────────────────────────────────────────────────────────────┤
│  Event System:                                               │
│    ├─ OrderEventPublisher      (Async event publishing)     │
│    └─ OrderEventListener       (Event handling)             │
├─────────────────────────────────────────────────────────────┤
│  WebSocket:                                                  │
│    ├─ WebSocketConfig          (STOMP/SockJS setup)         │
│    ├─ WaiterWebSocketController (Message handling)          │
│    └─ WebSocketEventHandler    (Event broadcasting)         │
└─────────────────────────────────────────────────────────────┘
```

### Key Features

- **Table Management**: Open, close, merge, and reassign tables
- **Order Creation**: Create orders with multiple items and add-ons
- **Real-time Updates**: WebSocket-based kitchen-waiter communication
- **Event-Driven**: Comprehensive audit trail of all operations
- **Role-Based Access**: Hierarchical waiter permissions
- **PIN Authentication**: Secure waiter login system

---

## Database Schema

### Tables

#### 1. `waiters`
Stores waiter information and credentials.

```sql
CREATE TABLE waiters (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    pin_code VARCHAR(10) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone_number VARCHAR(20),
    role VARCHAR(50) NOT NULL,              -- JUNIOR_WAITER, WAITER, SENIOR_WAITER, HEAD_WAITER, SUPERVISOR
    active BOOLEAN NOT NULL DEFAULT TRUE,
    permissions TEXT,                       -- JSON array of permissions
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 2. `tables`
Restaurant table information.

```sql
CREATE TABLE tables (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    number VARCHAR(20) NOT NULL,
    capacity INTEGER NOT NULL,
    floor INTEGER,
    section VARCHAR(50),
    status VARCHAR(50) NOT NULL,            -- AVAILABLE, OCCUPIED, RESERVED, CLEANING, OUT_OF_SERVICE
    current_waiter_id BIGINT REFERENCES waiters(id) ON DELETE SET NULL,
    merged_with_id BIGINT REFERENCES tables(id) ON DELETE SET NULL,
    opened_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(restaurant_id, number)
);
```

#### 3. `waiter_tables`
Tracks waiter-table assignments history.

```sql
CREATE TABLE waiter_tables (
    id BIGSERIAL PRIMARY KEY,
    waiter_id BIGINT NOT NULL REFERENCES waiters(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES tables(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 4. `order_events`
Audit trail for all order-related events.

```sql
CREATE TABLE order_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    table_id BIGINT REFERENCES tables(id),
    waiter_id BIGINT REFERENCES waiters(id),
    description TEXT,
    metadata JSONB,
    triggered_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 5. Extended `orders` table
Added waiter module foreign keys.

```sql
ALTER TABLE orders
ADD COLUMN table_id BIGINT REFERENCES tables(id),
ADD COLUMN waiter_id BIGINT REFERENCES waiters(id);
```

### Enums

#### TableStatus
```java
public enum TableStatus {
    AVAILABLE,      // Available for customers
    OCCUPIED,       // Customers seated / order in progress
    RESERVED,       // Reserved for an upcoming booking
    CLEANING,       // Being cleaned after customers leave
    OUT_OF_SERVICE  // Not usable (maintenance, etc.)
}
```

#### WaiterRole
```java
public enum WaiterRole {
    JUNIOR_WAITER,  // Basic service tasks
    WAITER,         // Full service capabilities
    SENIOR_WAITER,  // Can handle complex situations
    HEAD_WAITER,    // Supervisory role, can override
    SUPERVISOR      // Full management access
}
```

#### OrderEventType
```java
public enum OrderEventType {
    // Waiter → Kitchen
    ORDER_CREATED,
    ORDER_UPDATED,
    ORDER_SUBMITTED,
    ORDER_SUBMITTED_TO_KITCHEN,
    ITEM_ADDED,
    ITEM_REMOVED,
    ITEM_DELIVERED,

    // Kitchen → Waiter
    ORDER_COOKING,
    ORDER_READY,
    ORDER_DELAYED,
    ITEM_OUT_OF_STOCK,

    // Bill & Payment
    BILL_REQUESTED,
    PAYMENT_COMPLETED,
    ORDER_CLOSED,

    // Table Events
    TABLE_OPENED,
    TABLE_CLOSED,
    TABLE_MERGED,
    TABLE_STATUS_CHANGED,
    WAITER_ASSIGNED,
    WAITER_UNASSIGNED
}
```

---

## REST API Endpoints

### Waiter Management

#### Create Waiter

> Requires an `ADMIN` or `SUPERVISOR` token. The new waiter is created under the **caller's**
> restaurant (derived from the token), so there is no restaurant id in the path.

```http
POST /api/v1/waiters
Content-Type: application/json

{
  "name": "John Doe",
  "pinCode": "1234",
  "email": "john@example.com",
  "phoneNumber": "+998901234567",
  "role": "WAITER",
  "permissions": ["MANAGE_TABLES", "OVERRIDE_PRICES", "VOID_ITEMS", "MERGE_TABLES"]
}
```

**Response**: `201 Created`
```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com",
  "phoneNumber": "+998901234567",
  "role": "WAITER",
  "active": true,
  "permissions": ["MANAGE_TABLES", "OVERRIDE_PRICES", "VOID_ITEMS", "MERGE_TABLES"],
  "createdAt": "2025-12-02T10:00:00"
}
```

#### Authenticate Waiter

> Both `restaurantId` and `pinCode` (4–6 digits) are **required** — PIN codes are unique
> *per restaurant*, so the login must name the restaurant being signed into.

```http
POST /api/v1/waiters/auth
Content-Type: application/json

{
  "restaurantId": 1,
  "pinCode": "1234"
}
```

**Response**: `200 OK` — note there is no top-level `role`/`permissions`; the role lives on the
nested `waiter` object.
```json
{
  "waiterId": 1,
  "name": "John Doe",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "waiter": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "phoneNumber": "+998901234567",
    "role": "WAITER",
    "active": true,
    "permissions": ["MANAGE_TABLES", "OVERRIDE_PRICES", "VOID_ITEMS", "MERGE_TABLES"],
    "activeTablesCount": 0,
    "createdAt": "2025-12-02T10:00:00",
    "updatedAt": "2025-12-02T10:00:00"
  }
}
```

#### Get All Waiters

> Requires `ADMIN`, `OPERATOR`, or `SUPERVISOR`. Paged; scoped to the caller's restaurant.

```http
GET /api/v1/waiters?page=0&size=10&sortBy=name&sortDir=asc
```

Only active waiters:
```http
GET /api/v1/waiters/active
```

#### Get Waiter by ID
```http
GET /api/v1/waiters/{waiterId}
```

#### Update Waiter
```http
PUT /api/v1/waiters/{waiterId}
Content-Type: application/json

{
  "name": "John Smith",
  "email": "johnsmith@example.com",
  "role": "HEAD_WAITER"
}
```

#### Deactivate Waiter
```http
DELETE /api/v1/waiters/{waiterId}
```

---

### Table Management

#### Create Table
```http
POST /api/v1/tables
Content-Type: application/json

{
  "restaurantId": 1,
  "number": "A1",
  "capacity": 4,
  "floor": 1,
  "section": "Main Hall"
}
```

**Response**: `201 Created`
```json
{
  "id": 1,
  "restaurantId": 1,
  "number": "A1",
  "capacity": 4,
  "floor": 1,
  "section": "Main Hall",
  "status": "AVAILABLE",
  "currentWaiterId": null,
  "mergedWithId": null,
  "createdAt": "2025-12-02T10:00:00"
}
```

#### Get All Tables
```http
GET /api/v1/restaurants/{restaurantId}/tables?status=AVAILABLE&floor=1
```

#### Get Available Tables
```http
GET /api/v1/restaurants/{restaurantId}/tables/available
```

#### Change Table Status

> There is no separate "open" or "close" endpoint — a table's lifecycle is driven entirely by
> its status via `PATCH`. Use `OCCUPIED` when seating customers, `AVAILABLE` to free it, etc.

```http
PATCH /api/v1/tables/{tableId}/status
Content-Type: application/json

{
  "status": "OCCUPIED"
}
```

**Response**: `200 OK`
```json
{
  "id": 1,
  "number": "A1",
  "status": "OCCUPIED",
  "currentWaiterId": 1,
  "openedAt": "2025-12-02T10:00:00"
}
```

#### Free a Table
```http
PATCH /api/v1/tables/{tableId}/status
Content-Type: application/json

{
  "status": "AVAILABLE"
}
```

#### Merge Tables
```http
POST /api/v1/tables/merge
Content-Type: application/json

{
  "mainTableId": 1,
  "tableIdsToMerge": [2]
}
```

#### Unmerge Table
```http
POST /api/v1/tables/{tableId}/unmerge
```

#### Assign Waiter to Table
```http
POST /api/v1/waiters/{waiterId}/tables/{tableId}/assign
```

#### Get Table Orders
```http
GET /api/v1/waiter/orders/table/{tableId}
```

---

### Order Management

#### Create Order

> The acting waiter is identified by the `X-Waiter-Id` header (not a body field), and the target
> table is passed as `tableId` in the body.

```http
POST /api/v1/waiter/orders
Content-Type: application/json
X-Waiter-Id: 1

{
  "tableId": 1,
  "customerId": 5,
  "items": [
    {
      "productId": 10,
      "quantity": 2,
      "notes": "No onions",
      "addOns": [
        {
          "addOnId": 3,
          "quantity": 1
        }
      ]
    }
  ],
  "customerNotes": "Please serve quickly"
}
```

**Response**: `201 Created`
```json
{
  "orderId": 100,
  "orderNumber": "ORD-1733137200000-ABC123",
  "tableId": 1,
  "waiterId": 1,
  "status": "NEW",
  "total": 45.50,
  "createdAt": "2025-12-02T10:00:00"
}
```

#### Add Items to Order

> The request body is a JSON **array** — one or more items can be added in a single call.

```http
POST /api/v1/waiter/orders/{orderId}/items
Content-Type: application/json
X-Waiter-Id: 1

[
  {
    "productId": 12,
    "quantity": 1,
    "notes": "Extra spicy"
  }
]
```

#### Update Order Item
```http
PUT /api/v1/waiter/orders/{orderId}/items/{itemId}
Content-Type: application/json
X-Waiter-Id: 1

{
  "quantity": 3,
  "notes": "Medium spice"
}
```

#### Remove Order Item
```http
DELETE /api/v1/waiter/orders/{orderId}/items/{itemId}
X-Waiter-Id: 1
```

#### Submit Order to Kitchen
```http
POST /api/v1/waiter/orders/{orderId}/submit
X-Waiter-Id: 1
```

**Response**: `200 OK`
```json
{
  "orderId": 100,
  "orderNumber": "ORD-1733137200000-ABC123",
  "status": "ACCEPTED",
  "submittedAt": "2025-12-02T10:05:00",
  "message": "Order submitted to kitchen successfully"
}
```

#### Request Bill
```http
POST /api/v1/waiter/orders/{orderId}/bill
X-Waiter-Id: 1
```

**Response**: `200 OK`
```json
{
  "orderId": 100,
  "orderNumber": "ORD-1733137200000-ABC123",
  "subtotal": 40.00,
  "tax": 4.00,
  "total": 44.00,
  "status": "BILL_REQUESTED"
}
```

#### Close Order (after payment)

> There is **no** `/paid` endpoint. Once payment is settled, close the order with the endpoint
> below (identified by the `X-Waiter-Id` header).

```http
POST /api/v1/waiter/orders/{orderId}/close
X-Waiter-Id: 1
```

#### Get Order Details
```http
GET /api/v1/waiter/orders/{orderId}
```

#### Get Waiter's Ongoing Orders
```http
GET /api/v1/waiter/orders/waiter/{waiterId}/ongoing
```

Full order history for a waiter:
```http
GET /api/v1/waiter/orders/waiter/{waiterId}/history
```

---

### Order Events

#### Get Order Event History
```http
GET /api/v1/waiter/orders/{orderId}/history
```

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "orderId": 100,
    "eventType": "ORDER_CREATED",
    "tableId": 1,
    "waiterId": 1,
    "description": "Order created by waiter John Doe",
    "triggeredBy": "John Doe",
    "createdAt": "2025-12-02T10:00:00"
  },
  {
    "id": 2,
    "orderId": 100,
    "eventType": "ORDER_SUBMITTED",
    "description": "Order submitted to kitchen",
    "triggeredBy": "John Doe",
    "createdAt": "2025-12-02T10:05:00"
  },
  {
    "id": 3,
    "orderId": 100,
    "eventType": "BILL_REQUESTED",
    "description": "Bill requested",
    "triggeredBy": "John Doe",
    "createdAt": "2025-12-02T10:45:00"
  }
]
```

---

## WebSocket Communication

### Connection Setup

#### Endpoint
```
ws://localhost:8080/ws-waiter
```

With SockJS fallback:
```
http://localhost:8080/ws-waiter/sockjs
```

### JavaScript Client Example

```javascript
// Using SockJS and STOMP
const socket = new SockJS('http://localhost:8080/ws-waiter');
const stompClient = Stomp.over(socket);

// WebSocket auth defaults to 'shadow' mode (logs violations, does NOT block); it only blocks
// when app.websocket.auth.mode=enforce. Send the Bearer token on CONNECT anyway (a waiter/staff
// JWT) so the client keeps working once enforce is flipped on.
// The order/kitchen/table topics are tenant-scoped — subscribe under your own restaurantId
// (a waiter JWT carries a restaurantId claim; the login response returns it too).
const token = '<access token>';
const restaurantId = 1; // this client's restaurant

stompClient.connect({ Authorization: 'Bearer ' + token }, (frame) => {
  console.log('Connected: ' + frame);

  // Subscribe to waiter-specific notifications (user-scoped)
  stompClient.subscribe('/user/queue/notifications', (message) => {
    const notification = JSON.parse(message.body);
    console.log('Notification:', notification);
    // { type: "INFO", message: "Order submitted", timestamp: "..." }
  });

  // Subscribe to this restaurant's waiter orders
  stompClient.subscribe(`/topic/restaurant/${restaurantId}/waiter/orders`, (message) => {
    const orderUpdate = JSON.parse(message.body);
    console.log('Order update:', orderUpdate);
  });

  // Subscribe to this restaurant's kitchen updates
  stompClient.subscribe(`/topic/restaurant/${restaurantId}/kitchen`, (message) => {
    const kitchenUpdate = JSON.parse(message.body);
    console.log('Kitchen update:', kitchenUpdate);
  });

  // Subscribe to this restaurant's table status changes
  stompClient.subscribe(`/topic/restaurant/${restaurantId}/table`, (message) => {
    const tableUpdate = JSON.parse(message.body);
    console.log('Table update:', tableUpdate);
  });
});
```

> **Tenant scoping:** the previous bare topics `/topic/waiter/orders`, `/topic/kitchen`, `/topic/table`
> were shared across all restaurants and are now **retired** — subscribing to them is refused. Use the
> per-restaurant destinations shown above; a session may only subscribe to its own restaurant's topics.

### WebSocket Topics

#### 1. `/user/queue/notifications` (User-specific)
Personal notifications for a specific waiter.

**Message Format**:
```json
{
  "type": "INFO|SUCCESS|WARNING|ERROR",
  "message": "Order submitted to kitchen",
  "timestamp": "2025-12-02T10:05:00"
}
```

#### 2. `/topic/restaurant/{restaurantId}/waiter/orders` (Broadcast)
Order status updates for this restaurant's waiters.

**Message Format**:
```json
{
  "orderId": 100,
  "orderNumber": "ORD-1733137200000-ABC123",
  "status": "READY",
  "tableId": 1,
  "waiterId": 1,
  "message": "Order is ready for pickup",
  "timestamp": "2025-12-02T10:15:00"
}
```

#### 3. `/topic/restaurant/{restaurantId}/kitchen` (Broadcast)
Updates from/to this restaurant's kitchen.

**Message Format**:
```json
{
  "orderId": 100,
  "orderNumber": "ORD-1733137200000-ABC123",
  "status": "SUBMITTED",
  "tableId": 1,
  "waiterId": 1,
  "message": "New order submitted - Total: $44.00",
  "timestamp": "2025-12-02T10:05:00"
}
```

#### 4. `/topic/restaurant/{restaurantId}/table` (Broadcast)
Table status changes for this restaurant.

**Message Format**:
```json
{
  "tableId": 1,
  "tableNumber": "A1",
  "status": "OCCUPIED",
  "waiterId": 1,
  "timestamp": "2025-12-02T10:00:00"
}
```

### Client-to-Server Messages

#### Connect Waiter
```javascript
stompClient.send('/app/waiter/connect', {}, JSON.stringify({
  waiterId: 1,
  name: "John Doe"
}));
```

#### Update Waiter Status
```javascript
stompClient.send('/app/waiter/status', {}, JSON.stringify({
  waiterId: 1,
  status: "AVAILABLE|BUSY|BREAK"
}));
```

#### Call Waiter (From customer device)
```javascript
stompClient.send('/app/waiter/call', {}, JSON.stringify({
  tableId: 1,
  requestType: "ASSISTANCE|BILL|MENU",
  message: "Please bring water"
}));
```

---

## Event System

### Event Flow

```
┌──────────────┐
│   Service    │
│   Method     │
└──────┬───────┘
       │
       ├─ Business Logic Executes
       │
       ├─ Database Updated
       │
       └─► OrderEventPublisher.publish()
           │
           ├─ Creates Event Object
           │
           └─► ApplicationEventPublisher.publishEvent()
               │
               ├─► OrderEventListener.handle()
               │   │
               │   ├─ Saves to order_events table
               │   └─ Performs additional business logic
               │
               └─► WebSocketEventHandler.broadcast()
                   │
                   └─ Sends to WebSocket subscribers
```

### Available Events

#### 1. OrderCreatedEvent
Fired when a new order is created.

**Fields**:
- `orderId`, `orderNumber`
- `tableId`, `waiterId`
- `itemCount`
- `triggeredBy`

#### 2. OrderSubmittedEvent
Fired when order is submitted to kitchen.

**Fields**:
- `orderId`, `orderNumber`
- `tableId`, `waiterId`
- `totalAmount`
- `triggeredBy`

#### 3. BillRequestedEvent
Fired when customer requests the bill.

**Fields**:
- `orderId`, `orderNumber`
- `tableId`, `waiterId`
- `totalAmount`
- `triggeredBy`

#### 4. OrderPaidEvent
Fired when payment is completed.

**Fields**:
- `orderId`, `orderNumber`
- `tableId`, `waiterId`
- `amount`, `paymentMethod`
- `triggeredBy`

> A former `OrderReadyEvent` / `OrderItemAddedEvent` / `OrderItemRemovedEvent` /
> `TableStatusChangedEvent` cluster was removed as dead code: nothing ever published those events
> (the publisher methods had no callers), so their listeners — including the void-item performance
> KPI — could never fire. Re-introduce them only together with code paths that actually publish them.

---

## Setup & Configuration

### 1. Database Migration

The waiter module uses Flyway migration `V15__create_waiter_module_tables.sql` which:
- Creates `waiters`, `tables`, `waiter_tables`, `order_events` tables
- Adds `table_id` and `waiter_id` to existing `orders` table
- Creates necessary indexes for performance

Migration runs automatically on application startup.

### 2. Application Properties

Add WebSocket configuration to `application.yml`:

```yaml
spring:
  application:
    name: restaurant-delivery-service

  # Enable async processing for events
  task:
    execution:
      pool:
        core-size: 5
        max-size: 10
        queue-capacity: 100
```

### 3. Security Configuration

Only the waiter PIN auth and the WebSocket endpoint are public; every other request must be
authenticated, and fine-grained authorization is enforced **per-method** with `@PreAuthorize`
(`ADMIN` / `OPERATOR` / `SUPERVISOR` / `WAITER` / `HEAD_WAITER`) on the controllers. The real
`SecurityConfig.java` uses the `SecurityFilterChain` bean style (not the deprecated
`WebSecurityConfigurerAdapter`/`antMatchers`):

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            // Public: waiter PIN auth + the WebSocket endpoint
            .requestMatchers(
                "/api/v1/waiters/auth",
                "/ws-waiter/**"
            ).permitAll()
            // Everything else must be authenticated; roles are checked per-method via @PreAuthorize
            .anyRequest().authenticated()
        );
    return http.build();
}
```

### 4. CORS Configuration

Allow frontend to connect:

```java
@Configuration
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-waiter")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:5173")
                .withSockJS();
    }
}
```

---

## Usage Examples

### Complete Waiter Workflow

#### 1. Waiter Login
```bash
curl -X POST http://localhost:8080/api/v1/waiters/auth \
  -H "Content-Type: application/json" \
  -d '{"restaurantId": 1, "pinCode": "1234"}'
```

#### 2. Get Available Tables
```bash
curl -X GET "http://localhost:8080/api/v1/restaurants/1/tables/available" \
  -H "Authorization: Bearer {token}"
```

#### 3. Occupy the Table (status change)
```bash
curl -X PATCH http://localhost:8080/api/v1/tables/1/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"status": "OCCUPIED"}'
```

#### 4. Create Order
```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -H "X-Waiter-Id: 1" \
  -d '{
    "tableId": 1,
    "customerId": 5,
    "items": [
      {
        "productId": 10,
        "quantity": 2,
        "notes": "Medium rare"
      },
      {
        "productId": 15,
        "quantity": 1
      }
    ]
  }'
```

#### 5. Add More Items
```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -H "X-Waiter-Id: 1" \
  -d '[
    {
      "productId": 20,
      "quantity": 2,
      "notes": "Extra cheese"
    }
  ]'
```

#### 6. Submit to Kitchen
```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/submit \
  -H "Authorization: Bearer {token}" \
  -H "X-Waiter-Id: 1"
```

#### 7. Request Bill (when food delivered)
```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/bill \
  -H "Authorization: Bearer {token}" \
  -H "X-Waiter-Id: 1"
```

#### 8. Close the Order (after payment)
```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/close \
  -H "Authorization: Bearer {token}" \
  -H "X-Waiter-Id: 1"
```

#### 9. Free the Table (status change)
```bash
curl -X PATCH http://localhost:8080/api/v1/tables/1/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"status": "AVAILABLE"}'
```

---

## Permissions System

There is **no** `WaiterPermissions` class of constants and **no** fixed role→permission matrix.
A waiter's `permissions` column is a free-form JSON **text** field, and `Waiter.hasPermission(String)`
simply substring-matches the requested permission against that text (with two shortcuts: an inactive
waiter has no permissions, and a `SUPERVISOR` implicitly has all of them):

```java
public boolean hasPermission(String permission) {
    if (!active) return false;
    if (role == WaiterRole.SUPERVISOR) return true;      // supervisors: all permissions
    if (permissions == null || permissions.isEmpty()) return false;
    return permissions.contains(permission);             // substring match on the JSON text
}
```

The permission strings are whatever you choose to store. The in-code example set is:

```json
["MANAGE_TABLES", "OVERRIDE_PRICES", "VOID_ITEMS", "MERGE_TABLES"]
```

---

## Error Handling

### Common Error Codes

| Code | Message | Description |
|------|---------|-------------|
| 400 | Invalid request | Request validation failed |
| 401 | Unauthorized | Invalid or missing authentication |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not found | Resource doesn't exist |
| 409 | Conflict | Resource state conflict (e.g., table already open) |
| 500 | Internal error | Server error |

### Example Error Response

```json
{
  "timestamp": "2025-12-02T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Table is already occupied",
  "path": "/api/v1/tables/1/status"
}
```

---

## Performance Considerations

### Database Indexes

The module creates indexes for optimal query performance:

```sql
-- order_events table
CREATE INDEX idx_order_id ON order_events(order_id);
CREATE INDEX idx_event_type ON order_events(event_type);
CREATE INDEX idx_created_at ON order_events(created_at);

-- waiter_tables table
CREATE INDEX idx_waiter_active ON waiter_tables(waiter_id, active);
CREATE INDEX idx_table_active ON waiter_tables(table_id, active);

-- tables table
CREATE INDEX idx_restaurant_status ON tables(restaurant_id, status);
CREATE INDEX idx_waiter ON tables(current_waiter_id);
```

### Caching Recommendations

Consider caching:
- Active tables by restaurant
- Waiter permissions
- Menu items (referenced in orders)

### WebSocket Scalability

For production with multiple servers:
- Use Redis as a message broker
- Enable STOMP relay for distributed WebSocket

```yaml
spring:
  websocket:
    stomp:
      relay:
        enabled: true
        host: redis-server
        port: 6379
```

---

## Testing

### Unit Tests

```java
@SpringBootTest
class WaiterServiceTest {
    @Autowired
    private WaiterService waiterService;

    @Test
    void testCreateWaiter() {
        CreateWaiterRequest request = new CreateWaiterRequest();
        request.setName("Test Waiter");
        request.setPinCode("9999");

        WaiterResponse response = waiterService.createWaiter(request);

        assertNotNull(response.getId());
        assertEquals("Test Waiter", response.getName());
    }
}
```

### Integration Tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WaiterControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void testCreateAndAuthenticateWaiter() throws Exception {
        // Create waiter (restaurant derived from the caller's token)
        mockMvc.perform(post("/api/v1/waiters")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"pinCode\":\"1234\"}"))
                .andExpect(status().isCreated());

        // Authenticate
        mockMvc.perform(post("/api/v1/waiters/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\":1,\"pinCode\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiterId").exists());
    }
}
```

---

## Troubleshooting

### WebSocket Connection Issues

**Problem**: Can't connect to WebSocket
**Solution**:
1. Check CORS configuration
2. Verify endpoint URL (use `/ws-waiter`, not `/ws-waiter/websocket`)
3. Enable SockJS fallback for proxy environments

### Event Not Broadcasting

**Problem**: Events fire but WebSocket doesn't receive
**Solution**:
1. Verify `@Async` is enabled in configuration
2. Check `WebSocketEventHandler` is a `@Component`
3. Ensure client is subscribed to correct topic

### Table Already Open Error

**Problem**: `409 Conflict: Table is already open`
**Solution**:
1. Close the table first
2. Or reassign to a different waiter
3. Check `tables` table status in database

---

## Support

For issues or questions:
- **GitHub Issues**: [github.com/yourrepo/issues](https://github.com/yourrepo/issues)
- **Email**: support@qahvoon.uz
- **Documentation**: [docs.qahvoon.uz](https://docs.qahvoon.uz)

---

## Changelog

### Version 1.0.0 (2025-12-02)
- Initial release of Waiter Module
- Complete CRUD for waiters, tables, and orders
- WebSocket real-time communication
- Event-driven architecture
- Role-based permissions
- Comprehensive audit trail
