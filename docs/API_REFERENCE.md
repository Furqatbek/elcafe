# Qahvoon API Reference

**Version**: 1.0.0
**Base URL**: `http://localhost:8080/api/v1`
**Documentation**: http://localhost:8080/swagger-ui.html
**Last Updated**: 2026-02-02

## Table of Contents

1. [Roles and Permissions](#roles-and-permissions)
2. [Authentication](#authentication)
3. [Restaurant Management](#restaurant-management)
4. [Menu Management](#menu-management)
5. [Order Management](#order-management)
6. [Customer Management](#customer-management)
7. [Kitchen Operations](#kitchen-operations)
8. [Waiter Module](#waiter-module)
9. [Courier System](#courier-system)
10. [Analytics](#analytics)
11. [SMS Service](#sms-service)
12. [File Upload](#file-upload)
13. [Notifications](#notifications)
14. [Push Notifications](#push-notifications)
15. [Reviews](#reviews)
16. [Packaging Rules](#packaging-rules)
17. [Subscription & Billing](#subscription--billing)
18. [Platform Admin (Super-Admin)](#platform-admin-super-admin)

---

## Roles and Permissions

The API uses Role-Based Access Control (RBAC). Each endpoint requires specific roles for authorization.

### Available Roles

| Role | Description | Access Level |
|------|-------------|--------------|
| `ADMIN` | System administrator | Full access to all resources |
| `OWNER` | Restaurant owner | Management access to owned restaurants |
| `MANAGER` | Restaurant manager | Operational and financial access |
| `OPERATOR` | Back-office operator | POS and order management |
| `CASHIER` | POS cashier | Cash drawer and payment operations |
| `WAITER` | Front-of-house staff | Table service and order taking |
| `SUPERVISOR` | Waiter supervisor | Enhanced waiter permissions |
| `HEAD_WAITER` | Head waiter | Team management permissions |
| `KITCHEN_STAFF` | Kitchen personnel | Kitchen display and order preparation |
| `COURIER` | Delivery driver | Delivery order access |
| `CUSTOMER` | Registered customer | Customer-facing operations |

### Endpoint Permission Matrix

| Endpoint Category | Required Roles |
|-------------------|----------------|
| Financial Dashboard | ADMIN, OWNER, MANAGER |
| Cash Drawer Operations | ADMIN, OWNER, MANAGER, OPERATOR, CASHIER |
| Waiter Commissions | ADMIN, OWNER, MANAGER |
| Shift Management | ADMIN, OWNER, MANAGER, OPERATOR |
| Inventory Operations | ADMIN, OWNER, MANAGER, OPERATOR |
| Inventory Valuation | ADMIN, OWNER, MANAGER |
| Stock Counts | ADMIN, OWNER, MANAGER, OPERATOR |
| Financial Alerts | ADMIN, OWNER, MANAGER |
| Customer Addresses | Authenticated users |

### Authentication Header

All protected endpoints require the `Authorization` header:
```http
Authorization: Bearer {access_token}
```

### Error responses

Every error uses the standard envelope with a stable, machine-readable `error` code (the UI maps each
code to a localized message; the `message` text is a fallback). 5xx responses carry a fixed generic
message plus a `requestId` — internals never reach the client.

```json
{ "success": false, "error": "NOT_FOUND", "message": "Order not found", "requestId": "…" }
```

| `error` code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Body/params failed validation; per-field messages in `errors`. |
| `BAD_REQUEST` | 400 | Malformed or otherwise invalid request. |
| `UNAUTHENTICATED` | 401 | Missing or invalid authentication. |
| `TOKEN_EXPIRED` | 401 | Access token expired — refresh via `POST /auth/refresh`. |
| `INVALID_CREDENTIALS` | 401 | Wrong email or password. |
| `SUBSCRIPTION_INACTIVE` | 402 | Tenant suspended / subscription inactive. |
| `PAYMENT_FAILED` | 400–500 | Payment transaction failed; see `reason` in the body. |
| `FORBIDDEN` | 403 | Authenticated but not permitted — includes `plan.feature_required:<code>` when the plan lacks a module. |
| `TENANT_ACCESS_DENIED` | 403 | Attempt to access another restaurant's data. |
| `NOT_FOUND` | 404 | Resource does not exist. |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported for this endpoint. |
| `CONFLICT` | 409 | Conflicts with existing data (unique/FK/business rule). |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic-lock conflict — refresh and retry (`retryable: true`). |
| `FILE_TOO_LARGE` | 413 | Upload exceeds the size limit. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Wrong `Content-Type`. |
| `ACCOUNT_LOCKED` | 429 | Too many failed logins; temporarily locked. |
| `RATE_LIMITED` | 429 | Rate limit exceeded — honor the `Retry-After` header. |
| `ANALYTICS_FAILED` | 206 / 500 | Analytics computation failed (206 when partial data is returned). |
| `TIMEOUT` | 503 | The request timed out — retry. |
| `INTERNAL` | 500 | Unexpected server error — generic message + `requestId` only. |

Codes are append-only: never renamed or reused, so clients may switch on the literal. Source of
truth: `com.elcafe.exception.ErrorCode`.

---

## Authentication

### Admin/Operator Authentication

#### Register New User (Super-Admin)

Public self-registration is closed. Creating a user requires an authenticated `SUPER_ADMIN`.

```http
POST /api/v1/auth/register
Authorization: Bearer {super_admin_token}
Content-Type: application/json

{
  "email": "operator@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+998901234567",
  "role": "OPERATOR"
}
```

**Response**: 201 Created
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "email": "operator@example.com",
      "role": "OPERATOR"
    }
  }
}
```

#### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "admin@example.com",
  "password": "Admin123!"
}
```

**Response**: 200 OK
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

#### Refresh Token
```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGci..."
}
```

### Consumer OTP Authentication

#### Request OTP
```http
POST /api/v1/consumer/auth/login
Content-Type: application/json

{
  "phoneNumber": "+998901234567",
  "restaurantId": 1,
  "firstName": "John",
  "lastName": "Doe",
  "registrationSource": "MOBILE_APP"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `phoneNumber` | string | yes | Phone number (10-20 digits, may include `+`) |
| `restaurantId` | number | yes | Restaurant the customer is signing into. Customers are per-restaurant: a returning customer is matched by `(phoneNumber, restaurantId)`; a new one is created under it. |
| `firstName` | string | no | Customer first name |
| `lastName` | string | no | Customer last name |
| `registrationSource` | string | yes | One of: `MOBILE_APP`, `WEB`, `SELF_SERVICE`, `POS` |
| `language` | string | no | Preferred language: `uz`, `ru`, `en` |

**Response**: 200 OK
```json
{
  "success": true,
  "message": "OTP sent to +998901234567",
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "expiresAt": "2025-12-05T15:35:00Z"
  }
}
```

#### Verify OTP
```http
POST /api/v1/consumer/auth/verify
Content-Type: application/json

{
  "phoneNumber": "+998901234567",
  "restaurantId": 1,
  "otpCode": "123456"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `phoneNumber` | string | yes | Phone number used in the matching `/login` request |
| `restaurantId` | number | yes | Must match the `restaurantId` the OTP was requested for; the issued access token is bound to it |
| `otpCode` | string | yes | The 6-digit code sent via SMS |

**Response**: 200 OK
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "customer": {
      "id": 1,
      "phone": "+998901234567",
      "firstName": "John",
      "lastName": "Doe"
    }
  }
}
```

#### Refresh Consumer Token
```http
POST /api/v1/consumer/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGci..."
}
```

**Response**: 200 OK
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...(new)",
    "refreshToken": "eyJhbGci...(new)"
  }
}
```

#### Consumer Logout
```http
POST /api/v1/consumer/auth/logout
Authorization: Bearer eyJhbGci...
```

**Response**: 200 OK
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

## Restaurant Management

### Restaurants

#### Get All Restaurants (Public)
```http
GET /api/v1/restaurants?page=0&size=20
```

#### Get Active Restaurants (Public)
```http
GET /api/v1/restaurants/active
```

#### Get Restaurant by ID (Public)
```http
GET /api/v1/restaurants/{id}
```

#### Create Restaurant (Admin)
```http
POST /api/v1/restaurants
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Qahvoon Downtown",
  "description": "Best cafe in the city",
  "phone": "+998711234567",
  "email": "downtown@example.com",
  "address": "123 Main St",
  "city": "Tashkent",
  "zipCode": "100000",
  "latitude": 41.2995,
  "longitude": 69.2401,
  "deliveryFee": 10000,
  "minimumOrderAmount": 50000,
  "estimatedDeliveryTime": 30,
  "active": true,
  "acceptingOrders": true
}
```

#### Update Restaurant (Admin)
```http
PUT /api/v1/restaurants/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Qahvoon Downtown - Updated",
  "acceptingOrders": true
}
```

#### Delete Restaurant (Admin)
```http
DELETE /api/v1/restaurants/{id}
Authorization: Bearer {token}
```

### Business Hours

#### Get Business Hours (Public)
```http
GET /api/v1/restaurants/{restaurantId}/business-hours
```

#### Create Business Hours (Admin)
```http
POST /api/v1/restaurants/{restaurantId}/business-hours
Authorization: Bearer {token}
Content-Type: application/json

{
  "dayOfWeek": "MONDAY",
  "openTime": "09:00:00",
  "closeTime": "22:00:00",
  "closed": false
}
```

### Delivery Zones

#### Get Delivery Zones (Public)
```http
GET /api/v1/restaurants/{restaurantId}/delivery-zones
```

#### Create Delivery Zone (Admin)
```http
POST /api/v1/restaurants/{restaurantId}/delivery-zones
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Downtown Zone",
  "deliveryFee": 5000,
  "minimumOrderAmount": 30000,
  "estimatedDeliveryTime": 25,
  "active": true,
  "coordinates": [
    {"latitude": 41.2995, "longitude": 69.2401},
    {"latitude": 41.3000, "longitude": 69.2500},
    {"latitude": 41.2900, "longitude": 69.2450}
  ]
}
```

---

## Menu Management

### Public Menu

#### Get Public Menu (Cached - Public)
```http
GET /api/v1/menu/public/{restaurantId}
```

**Response**: 200 OK (Cached 30 minutes)
```json
{
  "success": true,
  "data": {
    "restaurant": {
      "id": 1,
      "name": "Qahvoon Downtown"
    },
    "categories": [
      {
        "id": 1,
        "name": "Beverages",
        "products": [
          {
            "id": 1,
            "name": "Cappuccino",
            "description": "Classic Italian coffee",
            "price": 15000,
            "images": ["https://..."],
            "available": true,
            "variants": [],
            "addOnGroups": []
          }
        ]
      }
    ]
  }
}
```

### Categories

#### Get Categories (Public)
```http
GET /api/v1/categories?restaurantId=1
```

#### Create Category (Admin)
```http
POST /api/v1/categories
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Beverages",
  "description": "Hot and cold drinks",
  "restaurantId": 1,
  "displayOrder": 1,
  "active": true
}
```

### Products

#### Create Product (Admin)
```http
POST /api/v1/products
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Cappuccino",
  "description": "Classic Italian coffee with steamed milk",
  "categoryId": 1,
  "price": 15000,
  "costPrice": 8000,
  "available": true,
  "featured": true,
  "images": ["https://example.com/cappuccino.jpg"],
  "preparationTime": 5
}
```

#### Get Product (Admin/Operator)
```http
GET /api/v1/products/{id}
Authorization: Bearer {token}
```

### Product Variants

#### Create Variant (Admin)
```http
POST /api/v1/products/{productId}/variants
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Large",
  "sku": "CAP-L-001",
  "priceAdjustment": 5000,
  "stockQuantity": 100,
  "inStock": true
}
```

### Add-On Groups

#### Get Add-On Groups (Admin/Operator)
```http
GET /api/v1/restaurants/{restaurantId}/addon-groups
Authorization: Bearer {token}
```

#### Create Add-On Group (Admin)
```http
POST /api/v1/restaurants/{restaurantId}/addon-groups
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Extra Toppings",
  "required": false,
  "multiSelect": true,
  "minSelections": 0,
  "maxSelections": 3,
  "active": true
}
```

### Add-Ons

#### Create Add-On (Admin)
```http
POST /api/v1/addon-groups/{addOnGroupId}/addons
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Extra Shot",
  "price": 3000,
  "available": true,
  "displayOrder": 1
}
```

### Ingredients

#### Get Ingredients (Admin/Operator)
```http
GET /api/v1/ingredients?page=0&size=20
Authorization: Bearer {token}
```

#### Create Ingredient (Admin)
```http
POST /api/v1/ingredients
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Coffee Beans",
  "unit": "KG",
  "costPerUnit": 50000,
  "stockQuantity": 100,
  "minimumStock": 20,
  "category": "BEVERAGES"
}
```

### Menu Collections

#### Get Active Collections (Public)
```http
GET /api/v1/menu-collections/active
```

#### Create Collection (Admin)
```http
POST /api/v1/menu-collections
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Featured Items",
  "description": "Our most popular items",
  "restaurantId": 1,
  "active": true,
  "displayOrder": 1
}
```

---

## Order Management

### Consumer Order API (Public)

#### Place Order (No Auth Required)
```http
POST /api/v1/consumer/orders
Content-Type: application/json

{
  "restaurantId": 1,
  "orderSource": "WEBSITE",
  "orderType": "DELIVERY",
  "customerInfo": {
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+998901234567",
    "email": "john@example.com"
  },
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "specialInstructions": "Extra hot",
      "addOns": [
        {
          "addOnId": 1,
          "quantity": 1
        }
      ]
    }
  ],
  "deliveryInfo": {
    "address": "123 Main Street",
    "city": "Tashkent",
    "zipCode": "100000",
    "latitude": 41.2995,
    "longitude": 69.2401,
    "deliveryInstructions": "Call on arrival"
  },
  "paymentMethod": "CASH",
  "customerNotes": "Please deliver quickly",
  "scheduledFor": null
}
```

**Response**: 201 Created
```json
{
  "success": true,
  "message": "Order placed successfully",
  "data": {
    "orderNumber": "ORD-1733412345678-A1B2C3D4",
    "status": "PLACED",
    "subtotal": 33000,
    "deliveryFee": 10000,
    "tax": 3300,
    "discount": 0,
    "total": 46300,
    "estimatedDeliveryTime": "2025-12-05T16:30:00Z",
    "restaurant": {
      "id": 1,
      "name": "Qahvoon Downtown",
      "phone": "+998711234567"
    },
    "items": [
      {
        "id": 1,
        "productName": "Cappuccino",
        "quantity": 2,
        "unitPrice": 15000,
        "totalPrice": 30000
      }
    ]
  }
}
```

#### Track Order (Public)
```http
GET /api/v1/consumer/orders/{orderNumber}
```

**Response**: 200 OK
```json
{
  "success": true,
  "data": {
    "orderNumber": "ORD-1733412345678-A1B2C3D4",
    "status": "PREPARING",
    "placedAt": "2025-12-05T15:30:00Z",
    "acceptedAt": "2025-12-05T15:32:00Z",
    "preparingAt": "2025-12-05T15:35:00Z",
    "estimatedDeliveryTime": "2025-12-05T16:30:00Z",
    "total": 46300,
    "restaurant": {
      "name": "Qahvoon Downtown",
      "phone": "+998711234567"
    },
    "deliveryInfo": {
      "address": "123 Main Street",
      "city": "Tashkent"
    },
    "items": [
      {
        "productName": "Cappuccino",
        "quantity": 2,
        "status": "PREPARING"
      }
    ],
    "statusHistory": [
      {
        "status": "PENDING",
        "changedAt": "2025-12-05T15:30:00Z",
        "notes": "Order created"
      },
      {
        "status": "PLACED",
        "changedAt": "2025-12-05T15:30:05Z",
        "notes": "Order placed - cash on delivery"
      },
      {
        "status": "ACCEPTED",
        "changedAt": "2025-12-05T15:32:00Z",
        "changedBy": "operator@example.com",
        "notes": "Order accepted by restaurant"
      },
      {
        "status": "PREPARING",
        "changedAt": "2025-12-05T15:35:00Z",
        "changedBy": "KITCHEN",
        "notes": "Kitchen started preparing order"
      }
    ]
  }
}
```

#### Cancel Order (Public)
```http
POST /api/v1/consumer/orders/{orderNumber}/cancel?reason=Changed%20my%20mind
```

`reason` is an optional **query parameter** (not a JSON body).

**Note**: Orders can only be cancelled within 5 minutes of placement.

### Admin Order Management

#### Get All Orders (Admin/Operator)
```http
GET /api/v1/admin/orders?status=PLACED&page=0&size=20
Authorization: Bearer {token}
```

#### Get Order Details (Admin/Operator)
```http
GET /api/v1/admin/orders/{orderId}
Authorization: Bearer {token}
```

#### Accept Order (Admin/Operator)
```http
POST /api/v1/admin/orders/{orderId}/accept
Authorization: Bearer {token}
Content-Type: application/json

{
  "notes": "Order confirmed. Estimated ready time: 20 minutes"
}
```

**Effects**:
- Status: PLACED → ACCEPTED
- Sets `acceptedAt` timestamp
- Broadcasts WebSocket event to customer
- Sends SMS notification to customer
- Creates kitchen order

#### Reject Order (Admin/Operator)
```http
POST /api/v1/admin/orders/{orderId}/reject
Authorization: Bearer {token}
Content-Type: application/json

{
  "reason": "Ingredients unavailable"
}
```

**Effects**:
- Status: PLACED → REJECTED
- Sets `rejectedAt` timestamp
- Broadcasts WebSocket event
- Sends SMS notification to customer

#### Cancel Order (Admin/Operator)
```http
POST /api/v1/admin/orders/{orderId}/cancel
Authorization: Bearer {token}
Content-Type: application/json

{
  "reason": "Customer requested cancellation by phone"
}
```

#### Update Order Status (Admin/Operator)
```http
PATCH /api/v1/admin/orders/{orderId}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "ACCEPTED",
  "notes": "Moving to next stage"
}
```

### Payment Management

#### Get Payment (Admin/Operator)
```http
GET /api/v1/orders/{orderId}/payments
Authorization: Bearer {token}
```

#### Get All Payments (Admin/Operator)
```http
GET /api/v1/orders/0/payments/all?page=0&size=20
Authorization: Bearer {token}
```

#### Get Payments by Status (Admin/Operator)
```http
GET /api/v1/orders/0/payments/by-status?status=COMPLETED
Authorization: Bearer {token}
```

---

## Customer Management

### Customers

#### Get All Customers (Admin/Manager)
```http
GET /api/v1/customers?page=0&size=20
Authorization: Bearer {token}
```

#### Get Customer (Admin/Manager)
```http
GET /api/v1/customers/{id}
Authorization: Bearer {token}
```

#### Get Customer Order History (Admin/Manager)
```http
GET /api/v1/customers/{id}/orders
Authorization: Bearer {token}
```

### Customer Activity (RFM Analysis)

Gated to `ADMIN`, `OWNER`, `MANAGER`, `OPERATOR`. These endpoints return a **raw JSON array** (`List`), not the standard `ApiResponse` envelope.

#### Get Customer Activity (Admin/Owner/Manager/Operator)
```http
GET /api/v1/customers/activity?page=0&size=20
Authorization: Bearer {token}
```

#### Filter Customers (Admin/Owner/Manager/Operator)
```http
GET /api/v1/customers/activity/filter?rfmSegment=CHAMPION&minTotalSpent=100000
Authorization: Bearer {token}
```

### Customer Addresses

Require authentication (`isAuthenticated()`). A consumer may only access **their own** addresses — the authenticated customer id must match the path `{customerId}`.

#### Get Customer Addresses (Authenticated)
```http
GET /api/v1/customers/{customerId}/addresses
Authorization: Bearer {token}
```

#### Create Address (Authenticated)
```http
POST /api/v1/customers/{customerId}/addresses
Authorization: Bearer {token}
Content-Type: application/json

{
  "label": "Home",
  "address": "123 Main Street",
  "city": "Tashkent",
  "zipCode": "100000",
  "latitude": 41.2995,
  "longitude": 69.2401,
  "isDefault": true
}
```

#### Set Default Address (Authenticated)
```http
PUT /api/v1/customers/{customerId}/addresses/{addressId}/default
Authorization: Bearer {token}
```

---

## Kitchen Operations

### Kitchen Orders

#### Get Active Orders (Kitchen/Admin)
```http
GET /api/v1/kitchen/orders/active
Authorization: Bearer {token}
```

**Response**: Returns orders with status PENDING or PREPARING
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "orderNumber": "ORD-1733412345678-A1B2C3D4",
      "status": "PENDING",
      "priority": 5,
      "createdAt": "2025-12-05T15:30:00Z",
      "estimatedReadyTime": "2025-12-05T15:50:00Z",
      "items": [
        {
          "productName": "Cappuccino",
          "quantity": 2,
          "specialInstructions": "Extra hot"
        }
      ]
    }
  ]
}
```

#### Get Ready Orders (Kitchen/Admin/Courier)
```http
GET /api/v1/kitchen/orders/ready
Authorization: Bearer {token}
```

#### Start Preparing (Kitchen/Admin)
```http
POST /api/v1/kitchen/orders/{id}/start?chefName=Chef%20Mario
Authorization: Bearer {token}
```

**Effects**:
- Status: ACCEPTED → PREPARING
- Sets `preparingAt` timestamp
- Assigns chef
- Broadcasts WebSocket event

#### Mark Ready (Kitchen/Admin)
```http
POST /api/v1/kitchen/orders/{id}/ready
Authorization: Bearer {token}
```

Takes no request body.

**Effects**:
- Status: PREPARING → READY
- Sets `readyAt` timestamp
- Broadcasts WebSocket event
- Sends SMS notification to customer
- Notifies available couriers

#### Mark Picked Up (Kitchen/Admin/Courier)
```http
POST /api/v1/kitchen/orders/{id}/picked-up
Authorization: Bearer {token}
```

Takes no request body.

**Effects**:
- Status: READY → PICKED_UP
- Sets `pickedUpAt` timestamp
- Broadcasts WebSocket event

#### Update Priority (Admin/Operator)
```http
PATCH /api/v1/kitchen/orders/{id}/priority?priority=URGENT
Authorization: Bearer {token}
```

`priority` is a `KitchenPriority` enum name — one of `LOW`, `NORMAL`, `HIGH`, `URGENT` (not an integer).

---

## Waiter Module

### Waiter Authentication

#### Waiter PIN Login (Public)
```http
POST /api/v1/waiters/auth
Content-Type: application/json

{
  "restaurantId": 1,
  "pinCode": "1234"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `restaurantId` | number | yes | Restaurant the device is signing into. PINs are per-restaurant (V151), so the waiter is resolved by `(restaurantId, pinCode)`. |
| `pinCode` | string | yes | The waiter's 4-6 digit PIN |

**Response**: 200 OK
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "waiter": {
      "id": 1,
      "firstName": "Maria",
      "lastName": "Garcia",
      "employeeCode": "W001",
      "active": true
    }
  }
}
```

### Waiter Management

#### Get All Waiters (Admin/Operator/Supervisor)
```http
GET /api/v1/waiters?page=0&size=20
Authorization: Bearer {token}
```

#### Get Active Waiters (Admin/Operator/Supervisor)
```http
GET /api/v1/waiters/active
Authorization: Bearer {token}
```

#### Create Waiter (Admin/Supervisor)
```http
POST /api/v1/waiters
Authorization: Bearer {token}
Content-Type: application/json

{
  "firstName": "Maria",
  "lastName": "Garcia",
  "phone": "+998901234567",
  "email": "maria@example.com",
  "employeeCode": "W001",
  "pinCode": "1234",
  "role": "WAITER",
  "active": true
}
```

### Tables

#### Get All Tables (Waiter/Admin)
```http
GET /api/v1/waiter/tables?page=0&size=20
Authorization: Bearer {token}
```

#### Get Available Tables (Waiter/Admin)
```http
GET /api/v1/waiter/tables/available
Authorization: Bearer {token}
```

#### Get Tables by Status (Waiter/Admin)
```http
GET /api/v1/waiter/tables/status/OCCUPIED
Authorization: Bearer {token}
```

#### Create Table (Admin/Supervisor)
```http
POST /api/v1/waiter/tables
Authorization: Bearer {token}
Content-Type: application/json

{
  "restaurantId": 1,
  "tableNumber": "T-01",
  "capacity": 4,
  "status": "AVAILABLE",
  "location": "Main hall"
}
```

#### Open Table (Waiter/Supervisor)
```http
POST /api/v1/waiter/tables/{id}/open
Authorization: Bearer {token}
Content-Type: application/json

{
  "numberOfGuests": 4
}
```

#### Close Table (Waiter/Supervisor)
```http
POST /api/v1/waiter/tables/{id}/close
Authorization: Bearer {token}
```

#### Merge Tables (Waiter/Supervisor)
```http
POST /api/v1/waiter/tables/{sourceId}/merge?targetTableId={targetId}
Authorization: Bearer {token}
```

### Waiter Orders

#### Create Order (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
Content-Type: application/json

{
  "tableId": 1,
  "customerId": 5,
  "customerNotes": "VIP guest",
  "items": [
    {
      "productId": 10,
      "variantId": 25,
      "quantity": 2,
      "addOns": "extra cheese, no onions",
      "specialInstructions": "Well done"
    },
    {
      "productId": 15,
      "quantity": 1,
      "specialInstructions": "Extra spicy"
    }
  ]
}
```

**Note**:
- `customerId` is **optional** - can be added later when customer is identified
- `items` are **optional** - can be included in create request or added later
- The `X-Waiter-Id` header is **required** for all waiter order operations
- Waiter order numbers use the `W%d%03d` format (e.g. `W651234567`); only consumer orders use the `ORD-...` format

**Table Status Changes:**
- When order is created: Table status changes from `AVAILABLE` → `OCCUPIED`
- When order is closed: Table status changes from `OCCUPIED` → `CLEANING`

**Response**: 201 Created
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": 123,
    "orderNumber": "W651234567",
    "status": "NEW",
    "table": {
      "id": 1,
      "number": "T-01",
      "status": "OCCUPIED"
    },
    "items": [
      {
        "id": 1,
        "productName": "Margherita Pizza",
        "variantName": "Large",
        "quantity": 2,
        "unitPrice": 12.50,
        "totalPrice": 25.00
      }
    ],
    "subtotal": 25.00,
    "total": 25.00
  }
}
```

#### Get Table Orders (Waiter/Admin)
```http
GET /api/v1/waiter/orders/table/{tableId}
Authorization: Bearer {token}
```

#### Add Items to Order (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/items
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
Content-Type: application/json

[
  {
    "productId": 2,
    "variantId": null,
    "quantity": 1,
    "addOns": null,
    "specialInstructions": "No sugar"
  }
]
```

#### Update Order Item (Waiter/Supervisor)
```http
PUT /api/v1/waiter/orders/{orderId}/items/{itemId}
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
Content-Type: application/json

{
  "quantity": 3,
  "addOns": "extra sauce",
  "specialInstructions": "Updated instructions"
}
```

#### Delete Order Item (Waiter/Supervisor)
```http
DELETE /api/v1/waiter/orders/{orderId}/items/{itemId}
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
```

#### Submit Order to Kitchen (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/submit
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
```

**Effects:**
- Order status changes to `PREPARING`
- Order is sent to kitchen queue
- Table status remains `OCCUPIED`

#### Mark Item Delivered (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/items/{itemId}/deliver
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
```

#### Request Bill (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/bill
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
```

**Effects:**
- Table status changes to `RESERVED`

#### Close Order (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/close
Authorization: Bearer {token}
X-Waiter-Id: {waiterId}
```

**Effects:**
- Order status changes to `COMPLETED`
- Table status changes to `CLEANING`
- Table's `closedAt` timestamp is set

---

## Courier System

### Courier Management

#### Get All Couriers (Admin/Operator)
```http
GET /api/v1/couriers?page=0&size=20
Authorization: Bearer {token}
```

#### Create Courier (Admin)
```http
POST /api/v1/couriers
Authorization: Bearer {token}
Content-Type: application/json

{
  "firstName": "Ahmed",
  "lastName": "Khan",
  "phone": "+998901234567",
  "email": "ahmed@example.com",
  "vehicleType": "MOTORCYCLE",
  "vehicleNumber": "01A123BC",
  "active": true,
  "initialWalletBalance": 0
}
```

#### Get Courier Wallet (Admin/Operator/Courier)
```http
GET /api/v1/couriers/{id}/wallet
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "balance": 150000,
    "totalEarnings": 500000,
    "totalWithdrawals": 350000,
    "recentTransactions": [
      {
        "id": 1,
        "type": "EARNING",
        "amount": 15000,
        "description": "Delivery fee for order ORD-123",
        "createdAt": "2025-12-05T15:30:00Z"
      }
    ]
  }
}
```

#### Update Courier Status (Courier)
```http
POST /api/v1/couriers/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "online": true,
  "latitude": 41.2995,
  "longitude": 69.2401
}
```

### Courier Orders

#### Get Available Orders (Courier)
```http
GET /api/v1/courier/orders/available
Authorization: Bearer {token}
```

#### Get My Orders (Courier)
```http
GET /api/v1/courier/orders/my-orders
Authorization: Bearer {token}
```

#### Accept Order (Courier)
```http
POST /api/v1/courier/orders/{orderId}/accept?courierId={courierId}
Authorization: Bearer {token}
```

**Effects**:
- Status: READY → COURIER_ASSIGNED
- Assigns courier to order
- Broadcasts WebSocket event
- Sends SMS to customer with courier info

#### Decline Order (Courier)
```http
POST /api/v1/courier/orders/{orderId}/decline?courierId={courierId}
Authorization: Bearer {token}
Content-Type: application/json

{
  "reason": "Too far from current location"
}
```

#### Manual Assignment (Admin/Operator)
```http
POST /api/v1/courier/orders/assign
Authorization: Bearer {token}
Content-Type: application/json

{
  "orderId": 1,
  "courierId": 5
}
```

#### Start Delivery (Courier)
```http
POST /api/v1/courier/orders/{orderId}/start-delivery?courierId={courierId}
Authorization: Bearer {token}
```

**Effects**:
- Status: PICKED_UP → OUT_FOR_DELIVERY
- Sets delivery start time
- Broadcasts WebSocket event

#### Complete Delivery (Courier)
```http
POST /api/v1/courier/orders/{orderId}/complete?courierId={courierId}
Authorization: Bearer {token}
Content-Type: application/json

{
  "notes": "Delivered successfully",
  "proofOfDelivery": "https://..."
}
```

**Effects**:
- Status: OUT_FOR_DELIVERY → COMPLETED
- Sets `completedAt` timestamp
- Updates courier wallet with delivery fee
- Broadcasts WebSocket event
- Sends SMS notification to customer

### Location Tracking

#### Update Location (Courier)
```http
POST /api/v1/courier/orders/location
Authorization: Bearer {token}
Content-Type: application/json

{
  "courierId": 1,
  "latitude": 41.2995,
  "longitude": 69.2401,
  "accuracy": 10.5,
  "speed": 25.0
}
```

#### Get Courier Location (Courier/Admin/Operator)
```http
GET /api/v1/courier/orders/location/{courierId}
Authorization: Bearer {token}
```

#### Get Order Delivery Location (Courier/Admin/Operator)
```http
GET /api/v1/courier/orders/{orderId}/location
Authorization: Bearer {token}
```

#### Get Active Couriers (Admin/Operator)
```http
GET /api/v1/courier/orders/location/active
Authorization: Bearer {token}
```

### Courier Tariffs

#### Get All Tariffs (Admin/Operator)
```http
GET /api/v1/couriers/tariffs?page=0&size=20
Authorization: Bearer {token}
```

#### Create Tariff (Admin)
```http
POST /api/v1/couriers/tariffs
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Standard Delivery",
  "baseRate": 10000,
  "perKmRate": 1000,
  "minDistance": 0,
  "maxDistance": 10,
  "active": true
}
```

---

## Analytics

### Analytics Summary

#### Get Comprehensive Dashboard (Admin/Operator)
```http
GET /api/v1/analytics/summary?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1&laborCosts=1000000&operatingExpenses=500000
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "financial": {
      "totalRevenue": 5000000,
      "totalOrders": 150,
      "averageOrderValue": 33333,
      "totalCOGS": 2000000,
      "grossProfit": 3000000,
      "netProfit": 1500000
    },
    "operational": {
      "averagePreparationTime": 15.5,
      "averageDeliveryTime": 25.0,
      "peakHours": ["12:00-13:00", "19:00-20:00"]
    },
    "customer": {
      "totalCustomers": 75,
      "newCustomers": 25,
      "returningCustomers": 50,
      "retentionRate": 66.67
    }
  }
}
```

### Financial Analytics

#### Daily Revenue (Admin Only)
```http
GET /api/v1/analytics/financial/daily-revenue?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Sales by Category (Admin Only)
```http
GET /api/v1/analytics/financial/sales-by-category?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### COGS Analytics (Admin Only)
```http
GET /api/v1/analytics/financial/cogs?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Profitability (Admin Only)
```http
GET /api/v1/analytics/financial/profitability?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1&laborCosts=1000000&operatingExpenses=500000
Authorization: Bearer {token}
```

#### Contribution Margins (Admin Only)
```http
GET /api/v1/analytics/financial/contribution-margins?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

### Operational Analytics

#### Sales Per Hour (Admin/Operator)
```http
GET /api/v1/analytics/operational/sales-per-hour?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Peak Hours (Admin/Operator)
```http
GET /api/v1/analytics/operational/peak-hours?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Table Turnover (Admin/Operator)
```http
GET /api/v1/analytics/operational/table-turnover?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1&totalTables=20&totalSeats=80&operatingHoursPerDay=12
Authorization: Bearer {token}
```

#### Order Timing (Admin/Operator)
```http
GET /api/v1/analytics/operational/order-timing?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Kitchen Analytics (Admin/Operator)
```http
GET /api/v1/analytics/operational/kitchen?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

### Customer Analytics

#### Customer Retention (Admin Only)
```http
GET /api/v1/analytics/customer/retention?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Customer LTV (Admin Only)
```http
GET /api/v1/analytics/customer/ltv?restaurantId=1
Authorization: Bearer {token}
```

#### Customer Satisfaction (Admin Only)
```http
GET /api/v1/analytics/customer/satisfaction?startDate=2025-12-01&endDate=2025-12-05
Authorization: Bearer {token}
```

### Inventory Analytics

#### Inventory Turnover (Admin/Operator)
```http
GET /api/v1/analytics/inventory/turnover?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

---

## SMS Service

### SMS Gateway Integration (Eskiz.uz)

All SMS endpoints are restricted to `SUPER_ADMIN`. The module sends through a single shared platform Eskiz account and its tables are not tenant-scoped, so it is not exposed to per-restaurant admins/operators.

#### Login to SMS Broker (Super-Admin)
```http
POST /api/v1/sms/auth/login
Authorization: Bearer {token}
```

Takes **no request body** — the broker credentials come from server configuration.

#### Send SMS (Super-Admin)
```http
POST /api/v1/sms/send
Authorization: Bearer {token}
Content-Type: application/json

{
  "mobilePhone": "+998901234567",
  "message": "Your order ORD-123 is ready for pickup!"
}
```

#### Get Message Status (Super-Admin)
```http
GET /api/v1/sms/message/{id}/status
Authorization: Bearer {token}
```

---

## File Upload

#### Upload File (Admin/Operator)
```http
POST /api/v1/files/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: [binary data]
```

**Response**: `ApiResponse<String>` — `data` is the uploaded file URL as a plain string.
```json
{
  "success": true,
  "data": "https://storage.example.com/uploads/product-123.jpg"
}
```

#### Delete File (Admin)
```http
DELETE /api/v1/files?fileUrl=https://storage.example.com/uploads/product-123.jpg
Authorization: Bearer {token}
```

---

## Error Responses

All responses use one `ApiResponse` envelope: `{success, message, data, errors, error, requestId, timestamp}`, serialized with `@JsonInclude(NON_NULL)` (null fields are omitted).

On error:
- `success` is `false`
- `error` is a stable machine-readable code (e.g. `NOT_FOUND`, `VALIDATION_ERROR`, `INTERNAL`) — clients branch on this, never on `message` text
- `requestId` is the request correlation id (matches server logs)
- `errors`, when present, is a **map of field name → message string** (not an array of `{field, message}` objects)

```json
{
  "success": false,
  "message": "Validation failed",
  "error": "VALIDATION_ERROR",
  "errors": {
    "email": "Email is required"
  },
  "requestId": "b1e7c0a2-7f3e-4c2a-9a1b-0d2f4e6a8c10",
  "timestamp": "2025-12-05T15:30:00Z"
}
```

A `500` returns `message` `"An unexpected error occurred"` with `error` `"INTERNAL"` (internal details are never leaked; use `requestId` to correlate with logs).

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK - Request successful |
| 201 | Created - Resource created successfully |
| 400 | Bad Request - Invalid request data |
| 401 | Unauthorized - Authentication required |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 409 | Conflict - Resource already exists |
| 422 | Unprocessable Entity - Validation failed |
| 500 | Internal Server Error - Server error |

---

## Rate Limiting

The API uses a token bucket algorithm (Bucket4j) for rate limiting:

### Standard Limits
| Limit Type | Rate | Refill Rate |
|------------|------|-------------|
| User API calls | 100 requests/minute | 100 tokens/minute |
| Analytics endpoints | 20 requests/minute | 20 tokens/minute |
| Expensive endpoints | 10 requests/minute | 10 tokens/minute |

### Specific Limits
- **OTP requests**: 3 requests per 15 minutes per phone number
- **Login attempts**: 5 attempts per 15 minutes per user

### Rate Limit Response
When rate limit is exceeded, the API returns:
```json
{
  "success": false,
  "message": "Rate limit exceeded. Please wait before making more requests.",
  "timestamp": "2025-12-05T15:30:00Z"
}
```
**HTTP Status**: 429 Too Many Requests

---

## Financial Alerts & Notifications

### Financial Alert Subscriptions

#### Get Subscriptions (Admin/Owner/Manager)
```http
GET /api/v1/notifications/financial-alerts/restaurant/{restaurantId}
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "restaurantId": 1,
      "telegramChatId": "123456789",
      "subscriberName": "Finance Team",
      "alertDailyRevenue": true,
      "alertDailyExpenses": true,
      "alertDailyProfit": true,
      "reportTime": "23:00:00",
      "active": true,
      "createdAt": "2025-12-01T10:00:00Z"
    }
  ]
}
```

#### Create Subscription (Admin/Owner/Manager)
```http
POST /api/v1/notifications/financial-alerts
Authorization: Bearer {token}
Content-Type: application/json

{
  "restaurantId": 1,
  "telegramChatId": "123456789",
  "subscriberName": "Finance Team",
  "alertDailyRevenue": true,
  "alertDailyExpenses": true,
  "alertDailyProfit": true,
  "reportTime": "23:00",
  "active": true
}
```

#### Update Subscription (Admin/Owner/Manager)
```http
PUT /api/v1/notifications/financial-alerts/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "subscriberName": "Updated Name",
  "alertDailyRevenue": true,
  "alertDailyExpenses": false,
  "alertDailyProfit": true,
  "reportTime": "22:00",
  "active": true
}
```

#### Delete Subscription (Admin/Owner/Manager)
```http
DELETE /api/v1/notifications/financial-alerts/{id}
Authorization: Bearer {token}
```

#### Toggle Subscription (Admin/Owner/Manager)
```http
POST /api/v1/notifications/financial-alerts/{id}/toggle
Authorization: Bearer {token}
```

#### Trigger Daily Report Manually (Admin/Owner/Manager)
```http
POST /api/v1/notifications/financial-alerts/trigger/{restaurantId}
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "message": "Daily financial report triggered successfully"
}
```

#### Get Daily Metrics (Admin/Owner/Manager)
```http
GET /api/v1/notifications/financial-alerts/metrics/{restaurantId}?startDate=2025-12-01&endDate=2025-12-05
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "restaurantId": 1,
    "startDate": "2025-12-01",
    "endDate": "2025-12-05",
    "totalRevenue": 5000000,
    "totalExpenses": 3000000,
    "netProfit": 2000000,
    "orderCount": 150
  }
}

---

## WebSocket

### Connection
WebSocket auth is enforced: send a Bearer token on CONNECT. Order/kitchen topics are tenant-scoped —
subscribe under your own `restaurantId` (a session may only read its own restaurant's topics).

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

const token = '<access token>';
const restaurantId = 1;

stompClient.connect({ Authorization: 'Bearer ' + token }, function(frame) {
  console.log('Connected: ' + frame);
});
```

### Subscribe to Order Updates
```javascript
stompClient.subscribe(`/topic/restaurant/${restaurantId}/orders`, function(message) {
  const orderUpdate = JSON.parse(message.body);
  console.log('Order Update:', orderUpdate);
});
```

### Subscribe to Kitchen Updates
```javascript
stompClient.subscribe(`/topic/restaurant/${restaurantId}/kitchen`, function(message) {
  const kitchenUpdate = JSON.parse(message.body);
  console.log('Kitchen Update:', kitchenUpdate);
});
```

---

**For complete interactive documentation, visit**: http://localhost:8080/swagger-ui.html

**API Version**: 1.0.0
**Last Updated**: 2025-12-05

---

## Notifications

Endpoints for managing user notifications. All require authentication.

### Get Notifications
```http
GET /api/v1/notifications?role=CUSTOMER&userId=123&page=0&size=20
Authorization: Bearer eyJhbGci...
```

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `role` | string | yes | `CUSTOMER`, `ADMIN`, `WAITER`, `COURIER` |
| `userId` | long | no | User ID (omit for broadcast-only) |
| `page` | int | no | Page number (default 0) |
| `size` | int | no | Page size (default 20) |

**Response**: 200 OK
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "title": "Order Ready",
        "message": "Your order #ORD-042 is ready for pickup",
        "userRole": "CUSTOMER",
        "userId": 123,
        "orderId": 42,
        "type": "ORDER_STATUS",
        "read": false,
        "createdAt": "2026-04-22T15:30:00"
      }
    ],
    "totalElements": 5,
    "totalPages": 1
  }
}
```

### Get Unread Notifications
```http
GET /api/v1/notifications/unread?role=CUSTOMER&userId=123
Authorization: Bearer eyJhbGci...
```

### Get Unread Count
```http
GET /api/v1/notifications/unread/count?role=CUSTOMER&userId=123
Authorization: Bearer eyJhbGci...
```

**Response**: 200 OK
```json
{
  "success": true,
  "data": { "count": 3 }
}
```

### Get Order Notifications
```http
GET /api/v1/notifications/order/{orderId}
Authorization: Bearer eyJhbGci...
```

### Mark Notification as Read
```http
PATCH /api/v1/notifications/{id}/read
Authorization: Bearer eyJhbGci...
```

### Mark All as Read
```http
PATCH /api/v1/notifications/mark-all-read?role=CUSTOMER&userId=123
Authorization: Bearer eyJhbGci...
```

### Archive Notification
```http
PATCH /api/v1/notifications/{id}/archive
Authorization: Bearer eyJhbGci...
```

### Delete Notification
```http
DELETE /api/v1/notifications/{id}
Authorization: Bearer eyJhbGci...
```

---

## Push Notifications

Web Push notification subscription and delivery.

### Get VAPID Public Key (No Auth)
```http
GET /api/v1/push/vapid-key
```

**Response**: 200 OK
```json
{
  "publicKey": "BEl62iUYgU..."
}
```

### Subscribe Customer (CUSTOMER role)
```http
POST /api/v1/push/subscribe/customer
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "keys": {
    "p256dh": "BNcRdreALRFXTkOOUHK1...",
    "auth": "tBHItJI5svbpC7htDNBe..."
  }
}
```

**Response**: 200 OK
```json
{
  "success": true,
  "subscriptionId": 42,
  "message": "Successfully subscribed to push notifications"
}
```

### Subscribe Admin User (ADMIN/MANAGER/WAITER/COURIER)
```http
POST /api/v1/push/subscribe/admin
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "keys": {
    "p256dh": "BNcRdreALRFXTkOOUHK1...",
    "auth": "tBHItJI5svbpC7htDNBe..."
  }
}
```

### Unsubscribe (Authenticated)
```http
POST /api/v1/push/unsubscribe
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "endpoint": "https://fcm.googleapis.com/fcm/send/..."
}
```

### Check Subscription Status (Authenticated)
```http
GET /api/v1/push/status?endpoint=https://fcm.googleapis.com/...
Authorization: Bearer eyJhbGci...
```

**Response**: 200 OK
```json
{
  "enabled": true,
  "totalSubscriptions": 45,
  "subscribed": true
}
```

### Send to Customer (ADMIN only)
```http
POST /api/v1/push/admin/send/customer/{customerId}
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "title": "Your order is ready!",
  "body": "Order #ORD-042 is ready for pickup",
  "type": "ORDER_STATUS",
  "url": "/orders/42"
}
```

### Broadcast to All (ADMIN only)
```http
POST /api/v1/push/admin/send/broadcast
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "title": "Happy Hour!",
  "body": "50% off all drinks until 6PM",
  "type": "PROMOTION",
  "url": "/menu"
}
```

### List Subscriptions (ADMIN only)
```http
GET /api/v1/push/admin/subscriptions
Authorization: Bearer eyJhbGci...
```

### Get Stats (ADMIN only)
```http
GET /api/v1/push/admin/stats
Authorization: Bearer eyJhbGci...
```

**Response**: 200 OK
```json
{
  "totalActiveSubscriptions": 45,
  "pushEnabled": true
}
```

---

## Reviews

Customer reviews and feedback system.

### Submit Review (No Auth)
```http
POST /api/v1/public/reviews
Content-Type: application/json

{
  "restaurantId": 1,
  "orderNumber": "ORD-042",
  "customerName": "John",
  "rating": 5,
  "comment": "Amazing food and great service!"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `restaurantId` | long | yes | Restaurant ID |
| `orderId` | long | no | Order ID (prevents duplicate reviews) |
| `orderNumber` | string | no | Order number for display |
| `customerName` | string | no | Customer name |
| `rating` | int | yes | 1–5 stars |
| `comment` | string | no | Review text |

**Response**: 201 Created
```json
{
  "success": true,
  "data": {
    "id": 1,
    "orderId": null,
    "orderNumber": "ORD-042",
    "customerName": "John",
    "restaurantId": 1,
    "rating": 5,
    "comment": "Amazing food and great service!",
    "status": "PUBLISHED",
    "lowRating": false,
    "reply": null,
    "createdAt": "2026-04-22T15:30:00"
  }
}
```

### Get Restaurant Reviews (No Auth)
```http
GET /api/v1/public/reviews/restaurant/{restaurantId}
```

### Get Review Summary (No Auth)
```http
GET /api/v1/public/reviews/restaurant/{restaurantId}/summary
```

**Response**: 200 OK
```json
{
  "success": true,
  "data": {
    "averageRating": 4.2,
    "totalReviews": 89,
    "fiveStars": 40,
    "fourStars": 27,
    "threeStars": 12,
    "twoStars": 7,
    "oneStars": 3
  }
}
```

### Get All Reviews — Admin (ADMIN/OPERATOR)
```http
GET /api/v1/reviews/restaurant/{restaurantId}
Authorization: Bearer eyJhbGci...
```

### Reply to Review (ADMIN/OPERATOR)
```http
POST /api/v1/reviews/{id}/reply
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "reply": "Thank you for your feedback!",
  "repliedBy": "Manager"
}
```

### Hide Review (ADMIN/OPERATOR)
```http
POST /api/v1/reviews/{id}/hide
Authorization: Bearer eyJhbGci...
```

### Publish Review (ADMIN/OPERATOR)
```http
POST /api/v1/reviews/{id}/publish
Authorization: Bearer eyJhbGci...
```

---

## Packaging Rules

Auto-add packaging items (bags, bowls, spoons) from inventory for delivery/takeaway orders.

### List Rules by Restaurant (ADMIN/OPERATOR)
```http
GET /api/v1/packaging-rules/restaurant/{restaurantId}
Authorization: Bearer eyJhbGci...
```

**Response**: 200 OK
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "restaurantId": 1,
      "productId": 42,
      "productName": "Soup",
      "packagingIngredientId": 100,
      "packagingIngredientName": "Plastic Bowl",
      "packagingIngredientUnit": "pieces",
      "packagingIngredientCost": 200,
      "orderTypes": "DELIVERY,TAKEAWAY",
      "quantityMode": "PER_ITEM",
      "autoAddQuantity": 1,
      "chargeToCustomer": false,
      "active": true
    }
  ]
}
```

### List Rules by Product (ADMIN/OPERATOR)
```http
GET /api/v1/packaging-rules/product/{productId}
Authorization: Bearer eyJhbGci...
```

### Create Rule (ADMIN/OPERATOR)
```http
POST /api/v1/packaging-rules
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "restaurantId": 1,
  "productId": 42,
  "packagingIngredientId": 100,
  "orderTypes": "DELIVERY,TAKEAWAY",
  "quantityMode": "PER_ITEM",
  "autoAddQuantity": 1,
  "chargeToCustomer": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `restaurantId` | long | yes | Restaurant ID |
| `productId` | long | yes | Trigger product (e.g., Soup) |
| `packagingIngredientId` | long | yes | Inventory ingredient to auto-add (e.g., Plastic Bowl) |
| `orderTypes` | string | no | `"DELIVERY,TAKEAWAY"` (default) |
| `quantityMode` | string | no | `PER_ITEM` (default), `PER_ORDER`, `FIXED` |
| `autoAddQuantity` | int | no | Quantity to add (default 1) |
| `chargeToCustomer` | bool | no | Charge customer for packaging (default false) |

### Update Rule (ADMIN/OPERATOR)
```http
PUT /api/v1/packaging-rules/{id}
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{ "quantityMode": "PER_ORDER", "autoAddQuantity": 1, "chargeToCustomer": true }
```

### Delete Rule (ADMIN/OPERATOR)
```http
DELETE /api/v1/packaging-rules/{id}
Authorization: Bearer eyJhbGci...
```

### Toggle Rule (ADMIN/OPERATOR)
```http
POST /api/v1/packaging-rules/{id}/toggle
Authorization: Bearer eyJhbGci...
```

---

## Loyalty & Bonus Points System

### Get Customer Loyalty Info
```http
GET /api/v1/loyalty/customers/{customerId}
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "customerId": 123,
    "customerName": "John Doe",
    "customerEmail": "john@example.com",
    "currentBalance": 1500.00,
    "lifetimeEarned": 5000.00,
    "lifetimeSpent": 3500.00,
    "tier": {
      "id": 2,
      "name": "Regular",
      "level": 2,
      "bonusMultiplier": 1.2,
      "color": "#3B82F6",
      "icon": "award",
      "benefitsDescription": "20% bonus multiplier, priority support"
    },
    "totalSpent": 12000.00,
    "orderCount": 25,
    "lastOrderDate": "2025-12-10T14:30:00Z",
    "firstOrderBonusClaimed": true,
    "birthdayBonusClaimedYear": 2025
  }
}
```

### Get Bonus Transaction History
```http
GET /api/v1/loyalty/customers/{customerId}/transactions?page=0&size=20
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "transactionType": "EARNED",
        "amount": 150.00,
        "balanceAfter": 1500.00,
        "orderId": 456,
        "orderNumber": "ORD-2025-001",
        "description": "Bonus earned from order #ORD-2025-001",
        "metadata": {
          "orderId": 456,
          "orderAmount": 3000.00,
          "tierMultiplier": 1.2
        },
        "createdAt": "2025-12-10T14:30:00Z"
      }
    ],
    "totalPages": 5,
    "totalElements": 100,
    "size": 20,
    "number": 0
  }
}
```

### Get All Tiers
```http
GET /api/v1/loyalty/tiers
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "New",
      "level": 1,
      "bonusMultiplier": 1.0,
      "color": "#94A3B8",
      "icon": "star",
      "benefitsDescription": "Welcome bonus on first order"
    },
    {
      "id": 2,
      "name": "Regular",
      "level": 2,
      "bonusMultiplier": 1.2,
      "color": "#3B82F6",
      "icon": "award",
      "benefitsDescription": "20% bonus multiplier, priority support"
    },
    {
      "id": 3,
      "name": "Gold",
      "level": 3,
      "bonusMultiplier": 1.5,
      "color": "#F59E0B",
      "icon": "crown",
      "benefitsDescription": "50% bonus multiplier, free delivery"
    },
    {
      "id": 4,
      "name": "VIP",
      "level": 4,
      "bonusMultiplier": 2.0,
      "color": "#8B5CF6",
      "icon": "gem",
      "benefitsDescription": "2x bonus multiplier, exclusive offers"
    }
  ]
}
```

### Create Tier (Admin)
```http
POST /api/v1/loyalty/tiers
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Bronze",
  "level": 2,
  "minTotalSpend": 50000,
  "minOrderCount": 10,
  "bonusMultiplier": 1.3,
  "color": "#cd7f32",
  "icon": "award",
  "benefitsDescription": "30% bonus multiplier"
}
```

**Required role**: `ADMIN`, `OWNER`.

### Update Tier (Admin)
```http
PUT /api/v1/loyalty/tiers/{id}
Authorization: Bearer {token}
```

Same body shape as create. **Required role**: `ADMIN`, `OWNER`.

### Delete Tier (Admin)
```http
DELETE /api/v1/loyalty/tiers/{id}
Authorization: Bearer {token}
```

**Required role**: `ADMIN`, `OWNER`.

Refuses (`IllegalStateException`) when customers are still assigned to the tier; the error message includes the count so the caller can ask the admin to reassign first. The `GET /loyalty/tiers` list includes `customerCount` on each row so the UI can disable the delete button inline.

### Get Loyalty Config
```http
GET /api/v1/loyalty/config?restaurantId=1
Authorization: Bearer {token}
```

Returns the per-restaurant config when one exists, falling back to the global row. **Required role**: `ADMIN`, `OWNER`, `MANAGER`.

### Upsert Loyalty Config
```http
PUT /api/v1/loyalty/config
Authorization: Bearer {token}
Content-Type: application/json

{
  "restaurantId": 1,
  "bonusRateType": "PERCENTAGE",
  "bonusRateValue": 5.0,
  "maxBonusPaymentPercentage": 50,
  "minOrderAmountForBonus": 10000,
  "firstOrderBonusAmount": 5000,
  "birthdayBonusAmount": 10000,
  "reactivationBonusAmount": 5000,
  "reactivationDaysThreshold": 90,
  "bonusExpiryDays": 365,
  "enabled": true
}
```

`restaurantId: null` targets the global config row. All scalar fields are optional — only supplied fields overwrite. **Required role**: `ADMIN`, `OWNER`.

### Grant Birthday Bonus (Admin)
```http
POST /api/v1/loyalty/customers/{customerId}/birthday-bonus
Authorization: Bearer {token}
```

**Response**: 200 OK

### Grant Reactivation Bonus (Admin)
```http
POST /api/v1/loyalty/customers/{customerId}/reactivation-bonus
Authorization: Bearer {token}
```

**Response**: 200 OK

---

## Customer Wallet (Consumer)

Customer-facing wallet API. All endpoints require the customer's OTP-issued Bearer token (`CustomerPrincipal`).

See the [Customer Wallet Top-Ups](LOYALTY_SYSTEM.md#customer-wallet-top-ups) section of `LOYALTY_SYSTEM.md` for the full lifecycle, idempotency model, and provider config.

### Get Own Wallet
```http
GET /api/v1/consumer/wallet
Authorization: Bearer {customer_token}
```

Returns the authenticated customer's `CustomerLoyaltyResponse`.

### Create Top-Up
```http
POST /api/v1/consumer/wallet/top-ups
Authorization: Bearer {customer_token}
Content-Type: application/json

{
  "amount": 50000,
  "provider": "CLICK"
}
```

`amount >= 1000`. `provider` ∈ {`CLICK`, `PAYME`, `MANUAL`}.

**Response** (201):
```json
{
  "success": true,
  "message": "Top-up created",
  "data": {
    "id": 42,
    "customerId": 7,
    "amount": 50000.00,
    "status": "PENDING",
    "provider": "CLICK",
    "paymentUrl": "https://my.click.uz/services/pay?...",
    "createdAt": "2026-06-08T07:42:00Z",
    "completedAt": null
  }
}
```

The mobile / web client redirects the user to `paymentUrl`. Wallet credit happens via the provider webhook after the customer pays.

### Get Top-Up Status
```http
GET /api/v1/consumer/wallet/top-ups/{id}
Authorization: Bearer {customer_token}
```

Ownership-enforced — returns 404 if the top-up belongs to another customer.

### List Own Top-Ups
```http
GET /api/v1/consumer/wallet/top-ups?page=0&size=20
Authorization: Bearer {customer_token}
```

### Cancel Pending Top-Up
```http
POST /api/v1/consumer/wallet/top-ups/{id}/cancel
Authorization: Bearer {customer_token}
```

Only valid while `status == PENDING`.

---

## Customer Wallet (Admin)

Under `/api/v1/loyalty/wallet/`, role-gated.

### List All Top-Ups
```http
GET /api/v1/loyalty/wallet/top-ups?status=PENDING&page=0&size=20
Authorization: Bearer {token}
```

`status` filter is optional. **Required role**: `ADMIN`, `OWNER`, `MANAGER`.

### Manually Confirm Top-Up
```http
POST /api/v1/loyalty/wallet/top-ups/{id}/confirm?reference=cash-receipt-1234
Authorization: Bearer {token}
```

Credits the wallet via the same idempotent path the provider webhooks use. Use for `MANUAL` (cash-at-counter) provider top-ups, or to unstick a `PENDING` row whose webhook never arrived. **Required role**: `ADMIN`, `OWNER`, `MANAGER`, `CASHIER`.

### Mark Top-Up Failed
```http
POST /api/v1/loyalty/wallet/top-ups/{id}/fail?reason=Card%20declined
Authorization: Bearer {token}
```

No wallet movement. **Required role**: `ADMIN`, `OWNER`, `MANAGER`.

---

## Wallet Top-Up Webhooks

Under `/api/v1/webhook/wallet/`, **no Bearer token required** — protected by provider signature verification.

See [Customer Wallet Top-Ups → Provider webhooks](LOYALTY_SYSTEM.md#provider-webhooks) for protocol details, signature schemes, and configuration properties.

| Endpoint | Provider | Auth |
|----------|----------|------|
| `POST /api/v1/webhook/wallet/click/prepare` | Click | MD5 `sign_string` |
| `POST /api/v1/webhook/wallet/click/complete` | Click | MD5 `sign_string` |
| `POST /api/v1/webhook/wallet/payme` | Payme | HTTP Basic |

---

## Loyalty QR & POS Attach

### Resolve Customer by QR Code
```http
GET /api/v1/customers/by-qr/{qrCode}
Authorization: Bearer {token}
```

Scans of a `CST-XXXXXXXXXXXX` loyalty QR resolve to the full customer record with marketing data (bonus balance, tier, etc.). **Required role**: `ADMIN`, `MANAGER`, `OPERATOR`, `WAITER`, `CASHIER`.

### Regenerate Customer QR Code
```http
POST /api/v1/customers/{id}/qr-code/regenerate
Authorization: Bearer {token}
```

Rotates the QR code (e.g. lost card, suspected fraud). The old code is permanently invalidated. **Required role**: `ADMIN`, `OWNER`, `MANAGER`.

### Attach Customer to POS Order
```http
PATCH /api/v1/pos/orders/{orderId}/customer
Authorization: Bearer {token}
Content-Type: application/json

{ "qrCode": "CST-ABCDEF123456" }
```

Links a customer to an in-flight POS order so the loyalty wallet credit fires on payment-commit. Exactly **one** of `customerId`, `qrCode`, `phone` must be supplied (DTO `@AssertTrue` validation).

Rejected when the order is in a terminal status (`DELIVERED`, `COMPLETED`, `CANCELLED`).

**Required role**: `ADMIN`, `OPERATOR`, `WAITER`, `CASHIER`, `MANAGER`.

---

## Inventory Management

### Ingredient Categories (ADMIN/OPERATOR)

User-defined categories for grouping ingredients (e.g., Packaging, Meat, Spices).

#### List Categories
```http
GET /api/v1/inventory/ingredient-categories?restaurantId=1
Authorization: Bearer eyJhbGci...
```

**Response**: 200 OK
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "Packaging", "sortOrder": 0 },
    { "id": 2, "name": "Meat", "sortOrder": 1 },
    { "id": 3, "name": "Spices", "sortOrder": 2 }
  ]
}
```

#### Create Category
```http
POST /api/v1/inventory/ingredient-categories
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{ "restaurantId": 1, "name": "Packaging" }
```

#### Update Category
```http
PUT /api/v1/inventory/ingredient-categories/{id}
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{ "name": "Packaging & Utensils" }
```

#### Delete Category
```http
DELETE /api/v1/inventory/ingredient-categories/{id}
Authorization: Bearer eyJhbGci...
```

### Get All Ingredients
```http
GET /api/v1/inventory/ingredients?restaurantId=1&categoryId=1
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "message": "Ingredients retrieved successfully",
  "data": [
    {
      "id": 1,
      "restaurantId": 1,
      "restaurantName": "Qahvoon Downtown",
      "categoryId": 2,
      "categoryName": "Vegetables",
      "name": "Tomatoes",
      "description": "Fresh organic tomatoes",
      "unit": "kg",
      "currentStock": 50.5,
      "minimumStock": 20.0,
      "reorderLevel": 30.0,
      "costPerUnit": 2.50,
      "supplier": "Fresh Foods Inc.",
      "sku": "TOM-001",
      "active": true,
      "trackInventory": true,
      "createdAt": "2025-12-01T10:00:00Z",
      "updatedAt": "2025-12-10T15:30:00Z"
    }
  ]
}
```

### Create Ingredient
```http
POST /api/v1/inventory/ingredients
Authorization: Bearer {token}
Content-Type: application/json

{
  "restaurantId": 1,
  "name": "Cheese",
  "description": "Mozzarella cheese",
  "unit": "kg",
  "currentStock": 25.0,
  "minimumStock": 10.0,
  "reorderLevel": 15.0,
  "costPerUnit": 8.50,
  "supplier": "Dairy Co.",
  "sku": "CHE-001",
  "active": true,
  "trackInventory": true
}
```

**Response**: 201 Created

### Update Ingredient
```http
PUT /api/v1/inventory/ingredients/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Cheese",
  "description": "Premium Mozzarella",
  "unit": "kg",
  "currentStock": 30.0,
  "minimumStock": 10.0,
  "reorderLevel": 15.0,
  "costPerUnit": 9.00,
  "supplier": "Dairy Co.",
  "sku": "CHE-001",
  "active": true,
  "trackInventory": true
}
```

**Response**: 200 OK

### Add Stock
```http
POST /api/v1/inventory/ingredients/{id}/add-stock
Authorization: Bearer {token}
Content-Type: application/json

{
  "quantity": 50.0,
  "notes": "New shipment from supplier",
  "performedBy": "Admin"
}
```

**Response**:
```json
{
  "success": true,
  "message": "Stock added successfully",
  "data": {
    "id": 1,
    "currentStock": 100.5,
    ...
  }
}
```

### Adjust Stock
```http
POST /api/v1/inventory/ingredients/{id}/adjust-stock
Authorization: Bearer {token}
Content-Type: application/json

{
  "newQuantity": 45.0,
  "notes": "Inventory correction",
  "performedBy": "Manager"
}
```

**Response**: 200 OK

### Get Transaction History
```http
GET /api/v1/inventory/ingredients/{id}/transactions
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "message": "Transactions retrieved successfully",
  "data": [
    {
      "id": 1,
      "ingredientId": 1,
      "transactionType": "PURCHASE",
      "quantityChange": 50.0,
      "balanceAfter": 100.5,
      "notes": "New shipment from supplier",
      "performedBy": "Admin",
      "createdAt": "2025-12-10T10:00:00Z"
    },
    {
      "id": 2,
      "transactionType": "USAGE",
      "quantityChange": -5.5,
      "balanceAfter": 95.0,
      "notes": "Used for order #ORD-123",
      "performedBy": "Kitchen Staff",
      "createdAt": "2025-12-10T14:30:00Z"
    }
  ]
}
```

### Get Low Stock Ingredients
```http
GET /api/v1/inventory/ingredients/low-stock?restaurantId=1
Authorization: Bearer {token}
```

**Response**: List of ingredients where `currentStock <= minimumStock`

### Get Reorder Ingredients
```http
GET /api/v1/inventory/ingredients/reorder?restaurantId=1
Authorization: Bearer {token}
```

**Response**: List of ingredients where `currentStock <= reorderLevel`

### Delete Ingredient
```http
DELETE /api/v1/inventory/ingredients/{id}
Authorization: Bearer {token}
```

**Response**: 200 OK

---

## Tables Management

### Get All Tables
```http
GET /api/v1/restaurants/{restaurantId}/tables
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "restaurantId": 1,
      "restaurantName": "Qahvoon Downtown",
      "tableNumber": "T-01",
      "tableName": "Window Table 1",
      "status": "AVAILABLE",
      "capacity": 4,
      "section": "Main Hall",
      "active": true,
      "notes": "Near the window",
      "qrCode": "https://qahvoon.uz/qr/table-1",
      "createdAt": "2025-12-01T10:00:00Z",
      "updatedAt": "2025-12-14T15:00:00Z"
    }
  ]
}
```

### Get Available Tables
```http
GET /api/v1/restaurants/{restaurantId}/tables/available
Authorization: Bearer {token}
```

**Response**: List of tables with status "AVAILABLE"

### Create Table
```http
POST /api/v1/tables
Authorization: Bearer {token}
Content-Type: application/json

{
  "restaurantId": 1,
  "tableNumber": "T-10",
  "tableName": "Patio Table 1",
  "capacity": 6,
  "section": "Outdoor",
  "notes": "Umbrella available",
  "active": true
}
```

**Response**: 201 Created

### Update Table
```http
PUT /api/v1/tables/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "tableNumber": "T-10",
  "tableName": "VIP Table 1",
  "status": "RESERVED",
  "capacity": 6,
  "section": "VIP",
  "notes": "Premium seating",
  "active": true
}
```

**Response**: 200 OK

### Update Table Status
```http
PATCH /api/v1/tables/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "OCCUPIED"
}
```

**Response**: 200 OK

**Table Statuses**:
- `AVAILABLE` - Table is free
- `OCCUPIED` - Currently in use
- `RESERVED` - Reserved for future booking
- `CLEANING` - Being cleaned
- `OUT_OF_SERVICE` - Not available

### Get Tables by Section
```http
GET /api/v1/restaurants/{restaurantId}/tables/section/{sectionName}
Authorization: Bearer {token}
```

**Response**: List of tables in the specified section

### Get Table Sections
```http
GET /api/v1/restaurants/{restaurantId}/tables/sections
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": ["Main Hall", "Outdoor", "VIP", "Patio"]
}
```

### Get Table Statistics
```http
GET /api/v1/restaurants/{restaurantId}/tables/stats
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "totalTables": 25,
    "availableTables": 15,
    "occupiedTables": 7,
    "reservedTables": 3
  }
}
```

### Delete Table
```http
DELETE /api/v1/tables/{id}
Authorization: Bearer {token}
```

**Response**: 200 OK

---

## Working Hours Management

### Get Restaurant Working Hours
```http
GET /api/v1/restaurants/{restaurantId}/working-hours
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "restaurantId": 1,
      "restaurantName": "Qahvoon Downtown",
      "dayOfWeek": "MONDAY",
      "openTime": "09:00:00",
      "closeTime": "22:00:00",
      "closed": false,
      "createdAt": "2025-12-01T10:00:00Z",
      "updatedAt": "2025-12-14T10:00:00Z"
    }
  ]
}
```

### Create Working Hours
```http
POST /api/v1/working-hours
Authorization: Bearer {token}
Content-Type: application/json

{
  "restaurantId": 1,
  "dayOfWeek": "MONDAY",
  "openTime": "09:00",
  "closeTime": "22:00",
  "closed": false
}
```

**Response**: 201 Created

### Update Working Hours
```http
PUT /api/v1/working-hours/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "openTime": "08:00",
  "closeTime": "23:00",
  "closed": false
}
```

**Response**: 200 OK

### Get Working Hours by Day
```http
GET /api/v1/restaurants/{restaurantId}/working-hours/day/{dayOfWeek}
Authorization: Bearer {token}
```

**Response**: Working hours for the specified day

**Days of Week**: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY

---

## Subscription & Billing

Plan-tier info for the current tenant, plus admin plan management. When the Phase 2 access gate is on
(`app.subscription.enforcement.mode=enforce`), a **suspended** tenant's staff and waiter requests get
`402 SUBSCRIPTION_INACTIVE` on every non-allowlisted endpoint (auth/billing/platform/health pass).

### Current Plan (Authenticated)
```http
GET /api/v1/billing/me
Authorization: Bearer {token}
```
**Response**: `BillingStatusDto` — the caller's restaurant plan and state.
```json
{
  "restaurantId": 1,
  "planCode": "pro",
  "planName": "Pro",
  "featureCodes": ["inventory", "payroll", "marketing.sms"],
  "planExpiresAt": "2026-08-01T00:00:00",
  "isTrial": false,
  "daysUntilExpiry": 31,
  "inGracePeriod": false,
  "readOnly": false
}
```

### Available Plans (Authenticated)
```http
GET /api/v1/billing/plans
Authorization: Bearer {token}
```
**Response**: array of `PlanSummaryDto` (`code`, `name`, `monthlyPrice`, `featureCodes`, `sortOrder`) — active plans ordered for display.

### Set Plan (Admin)
```http
POST /api/v1/billing/admin/set-plan
Authorization: Bearer {token}
Content-Type: application/json

{
  "restaurantId": 1,
  "planCode": "advance",
  "planExpiresAt": "2026-09-01T00:00:00",
  "isTrial": false
}
```
Points the restaurant at a plan. `ADMIN` only; audited (`PLAN_CHANGED`). Returns the updated `BillingStatusDto`.

---

## Platform Admin (Super-Admin)

Cross-tenant subscription management. Every endpoint requires `SUPER_ADMIN` (the only cross-tenant role).

### List Tenants
```http
GET /api/v1/platform/tenants?search={name}&page=0&size=20
Authorization: Bearer {token}
```
**Response**: a page of `TenantSummaryDto` (`restaurantId`, `name`, `active`, `planCode`, `planName`, `isTrial`, `planExpiresAt`, `daysUntilExpiry`, `inGracePeriod`, `readOnly`). `search` is an optional case-insensitive name filter.

### Change a Tenant's Plan
```http
POST /api/v1/platform/tenants/{id}/plan
Authorization: Bearer {token}
Content-Type: application/json

{
  "planCode": "pro",
  "planExpiresAt": "2026-09-01T00:00:00",
  "isTrial": false
}
```

### Extend Expiry
```http
POST /api/v1/platform/tenants/{id}/extend
Authorization: Bearer {token}
Content-Type: application/json

{
  "days": 30
}
```
Extends from the current expiry when it's still in the future, otherwise from now (reviving a lapsed plan).

### Suspend / Reactivate
```http
POST /api/v1/platform/tenants/{id}/suspend
POST /api/v1/platform/tenants/{id}/reactivate
Authorization: Bearer {token}
```
Toggles `Restaurant.active`. Suspending cuts the tenant off when the Phase 2 gate is in `enforce` (402 for its staff/waiters). Audited (`RESTAURANT_SUSPENDED` / `RESTAURANT_REACTIVATED`).

---

**API Version**: 1.0.0
**Last Updated**: 2026-07-01
