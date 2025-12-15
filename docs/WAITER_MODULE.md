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
│    ├─ WaiterTableController    (Table Management)           │
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
    role VARCHAR(50) NOT NULL,              -- WAITER, HEAD_WAITER, SUPERVISOR
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
    status VARCHAR(50) NOT NULL,            -- FREE, ORDERING, WAITING, SERVED, etc.
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
    FREE,           // Available for customers
    ORDERING,       // Customers seated, taking order
    WAITING,        // Order submitted, waiting for food
    SERVED,         // Food delivered, customers eating
    BILL_REQUESTED, // Customer requested bill
    PAYING,         // Processing payment
    CLEANING        // Being cleaned after customers leave
}
```

#### WaiterRole
```java
public enum WaiterRole {
    WAITER,         // Standard waiter
    HEAD_WAITER,    // Senior waiter with additional permissions
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
```http
POST /api/v1/restaurants/{restaurantId}/waiters
Content-Type: application/json

{
  "name": "John Doe",
  "pinCode": "1234",
  "email": "john@example.com",
  "phoneNumber": "+998901234567",
  "role": "WAITER",
  "permissions": ["take_orders", "view_menu", "request_bill"]
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
  "permissions": ["take_orders", "view_menu", "request_bill"],
  "createdAt": "2025-12-02T10:00:00"
}
```

#### Authenticate Waiter
```http
POST /api/v1/waiters/auth
Content-Type: application/json

{
  "pinCode": "1234"
}
```

**Response**: `200 OK`
```json
{
  "waiterId": 1,
  "name": "John Doe",
  "role": "WAITER",
  "permissions": ["take_orders", "view_menu", "request_bill"],
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### Get All Waiters
```http
GET /api/v1/restaurants/{restaurantId}/waiters?active=true
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
POST /api/v1/restaurants/{restaurantId}/tables
Content-Type: application/json

{
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
  "status": "FREE",
  "currentWaiterId": null,
  "mergedWithId": null,
  "createdAt": "2025-12-02T10:00:00"
}
```

#### Get All Tables
```http
GET /api/v1/restaurants/{restaurantId}/tables?status=FREE&floor=1
```

#### Get Available Tables
```http
GET /api/v1/restaurants/{restaurantId}/tables/available
```

#### Open Table
```http
POST /api/v1/tables/{tableId}/open
Content-Type: application/json

{
  "waiterId": 1
}
```

**Response**: `200 OK`
```json
{
  "id": 1,
  "number": "A1",
  "status": "ORDERING",
  "currentWaiterId": 1,
  "openedAt": "2025-12-02T10:00:00"
}
```

#### Close Table
```http
POST /api/v1/tables/{tableId}/close
```

#### Merge Tables
```http
POST /api/v1/tables/{tableId}/merge
Content-Type: application/json

{
  "mergeWithTableId": 2
}
```

#### Unmerge Table
```http
POST /api/v1/tables/{tableId}/unmerge
```

#### Assign Waiter to Table
```http
POST /api/v1/tables/{tableId}/assign-waiter
Content-Type: application/json

{
  "waiterId": 2
}
```

#### Get Table Orders
```http
GET /api/v1/tables/{tableId}/orders?status=ACTIVE
```

---

### Order Management

#### Create Order for Table
```http
POST /api/v1/tables/{tableId}/orders
Content-Type: application/json

{
  "waiterId": 1,
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

#### Add Item to Order
```http
POST /api/v1/orders/{orderId}/items
Content-Type: application/json

{
  "productId": 12,
  "quantity": 1,
  "notes": "Extra spicy"
}
```

#### Update Order Item
```http
PUT /api/v1/orders/{orderId}/items/{itemId}
Content-Type: application/json

{
  "quantity": 3,
  "notes": "Medium spice"
}
```

#### Remove Order Item
```http
DELETE /api/v1/orders/{orderId}/items/{itemId}?reason=Customer%20changed%20mind
```

#### Submit Order to Kitchen
```http
POST /api/v1/orders/{orderId}/submit
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
POST /api/v1/orders/{orderId}/bill
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

#### Mark Order as Paid
```http
POST /api/v1/orders/{orderId}/paid
Content-Type: application/json

{
  "paymentMethod": "CARD",
  "amount": 44.00,
  "transactionId": "TXN-12345"
}
```

#### Get Order Details
```http
GET /api/v1/orders/{orderId}
```

#### Get Waiter's Orders
```http
GET /api/v1/waiters/{waiterId}/orders?status=ACTIVE&date=2025-12-02
```

---

### Order Events

#### Get Order Event History
```http
GET /api/v1/orders/{orderId}/events
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
    "eventType": "ITEM_ADDED",
    "description": "Added item: Margherita Pizza x2",
    "triggeredBy": "John Doe",
    "createdAt": "2025-12-02T10:01:00"
  },
  {
    "id": 3,
    "orderId": 100,
    "eventType": "ORDER_SUBMITTED",
    "description": "Order submitted to kitchen",
    "triggeredBy": "John Doe",
    "createdAt": "2025-12-02T10:05:00"
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

stompClient.connect({}, (frame) => {
  console.log('Connected: ' + frame);

  // Subscribe to waiter-specific notifications
  stompClient.subscribe('/user/queue/notifications', (message) => {
    const notification = JSON.parse(message.body);
    console.log('Notification:', notification);
    // { type: "INFO", message: "Order submitted", timestamp: "..." }
  });

  // Subscribe to all waiter orders
  stompClient.subscribe('/topic/waiter/orders', (message) => {
    const orderUpdate = JSON.parse(message.body);
    console.log('Order update:', orderUpdate);
  });

  // Subscribe to kitchen updates
  stompClient.subscribe('/topic/kitchen', (message) => {
    const kitchenUpdate = JSON.parse(message.body);
    console.log('Kitchen update:', kitchenUpdate);
  });

  // Subscribe to table status changes
  stompClient.subscribe('/topic/table', (message) => {
    const tableUpdate = JSON.parse(message.body);
    console.log('Table update:', tableUpdate);
  });
});
```

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

#### 2. `/topic/waiter/orders` (Broadcast)
Order status updates for all waiters.

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

#### 3. `/topic/kitchen` (Broadcast)
Updates from/to kitchen.

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

#### 4. `/topic/table` (Broadcast)
Table status changes.

**Message Format**:
```json
{
  "tableId": 1,
  "tableNumber": "A1",
  "status": "ORDERING",
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

#### 3. OrderReadyEvent
Fired when kitchen marks order as ready.

**Fields**:
- `orderId`, `orderNumber`
- `tableId`, `waiterId`
- `triggeredBy`

#### 4. BillRequestedEvent
Fired when customer requests the bill.

**Fields**:
- `orderId`, `orderNumber`
- `tableId`, `waiterId`
- `totalAmount`
- `triggeredBy`

#### 5. OrderPaidEvent
Fired when payment is completed.

**Fields**:
- `orderId`, `orderNumber`
- `tableId`, `waiterId`
- `amount`, `paymentMethod`
- `triggeredBy`

#### 6. OrderItemAddedEvent
Fired when item is added to order.

**Fields**:
- `orderId`, `orderNumber`
- `itemName`, `quantity`
- `waiterId`
- `triggeredBy`

#### 7. OrderItemRemovedEvent
Fired when item is removed from order.

**Fields**:
- `orderId`, `orderNumber`
- `itemName`, `reason`
- `waiterId`
- `triggeredBy`

#### 8. TableStatusChangedEvent
Fired when table status changes.

**Fields**:
- `tableId`, `tableNumber`
- `oldStatus`, `newStatus`
- `waiterId`
- `triggeredBy`

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

Waiter endpoints require authentication. Configure in `SecurityConfig.java`:

```java
@Override
protected void configure(HttpSecurity http) throws Exception {
    http
        .authorizeRequests()
            // Public waiter auth endpoint
            .antMatchers("/api/v1/waiters/auth").permitAll()

            // Waiter endpoints require WAITER role
            .antMatchers("/api/v1/waiters/**").hasAnyRole("WAITER", "HEAD_WAITER", "SUPERVISOR")
            .antMatchers("/api/v1/tables/**").hasAnyRole("WAITER", "HEAD_WAITER", "SUPERVISOR")

            // WebSocket endpoint
            .antMatchers("/ws-waiter/**").permitAll()
        .and()
        .csrf().disable();
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
  -d '{"pinCode": "1234"}'
```

#### 2. Get Available Tables
```bash
curl -X GET "http://localhost:8080/api/v1/restaurants/1/tables/available" \
  -H "Authorization: Bearer {token}"
```

#### 3. Open Table
```bash
curl -X POST http://localhost:8080/api/v1/tables/1/open \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"waiterId": 1}'
```

#### 4. Create Order
```bash
curl -X POST http://localhost:8080/api/v1/tables/1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "waiterId": 1,
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
curl -X POST http://localhost:8080/api/v1/orders/100/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "productId": 20,
    "quantity": 2,
    "notes": "Extra cheese"
  }'
```

#### 6. Submit to Kitchen
```bash
curl -X POST http://localhost:8080/api/v1/orders/100/submit \
  -H "Authorization: Bearer {token}"
```

#### 7. Request Bill (when food delivered)
```bash
curl -X POST http://localhost:8080/api/v1/orders/100/bill \
  -H "Authorization: Bearer {token}"
```

#### 8. Mark as Paid
```bash
curl -X POST http://localhost:8080/api/v1/orders/100/paid \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "paymentMethod": "CARD",
    "amount": 44.00,
    "transactionId": "TXN-12345"
  }'
```

#### 9. Close Table
```bash
curl -X POST http://localhost:8080/api/v1/tables/1/close \
  -H "Authorization: Bearer {token}"
```

---

## Permissions System

### Available Permissions

```java
public class WaiterPermissions {
    // Order Management
    public static final String CREATE_ORDER = "create_order";
    public static final String MODIFY_ORDER = "modify_order";
    public static final String CANCEL_ORDER = "cancel_order";
    public static final String VIEW_ALL_ORDERS = "view_all_orders";

    // Table Management
    public static final String OPEN_TABLE = "open_table";
    public static final String CLOSE_TABLE = "close_table";
    public static final String MERGE_TABLES = "merge_tables";
    public static final String REASSIGN_TABLE = "reassign_table";

    // Payment
    public static final String REQUEST_BILL = "request_bill";
    public static final String PROCESS_PAYMENT = "process_payment";

    // Management
    public static final String VIEW_REPORTS = "view_reports";
    public static final String MANAGE_WAITERS = "manage_waiters";
}
```

### Role-Permission Matrix

| Permission | WAITER | HEAD_WAITER | SUPERVISOR |
|-----------|--------|-------------|------------|
| create_order | ✓ | ✓ | ✓ |
| modify_order | ✓ | ✓ | ✓ |
| cancel_order | ✗ | ✓ | ✓ |
| view_all_orders | ✗ | ✓ | ✓ |
| open_table | ✓ | ✓ | ✓ |
| close_table | ✓ | ✓ | ✓ |
| merge_tables | ✗ | ✓ | ✓ |
| reassign_table | ✗ | ✓ | ✓ |
| request_bill | ✓ | ✓ | ✓ |
| process_payment | ✓ | ✓ | ✓ |
| view_reports | ✗ | ✗ | ✓ |
| manage_waiters | ✗ | ✗ | ✓ |

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
  "message": "Table is already open",
  "path": "/api/v1/tables/1/open"
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

        WaiterResponse response = waiterService.createWaiter(1L, request);

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
        // Create waiter
        mockMvc.perform(post("/api/v1/restaurants/1/waiters")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"pinCode\":\"1234\"}"))
                .andExpect(status().isCreated());

        // Authenticate
        mockMvc.perform(post("/api/v1/waiters/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinCode\":\"1234\"}"))
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
- **Email**: support@elcafe.com
- **Documentation**: [docs.elcafe.com](https://docs.elcafe.com)

---

## Changelog

### Version 1.0.0 (2025-12-02)
- Initial release of Waiter Module
- Complete CRUD for waiters, tables, and orders
- WebSocket real-time communication
- Event-driven architecture
- Role-based permissions
- Comprehensive audit trail
