# El Cafe API Reference

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

---

## Authentication

### Admin/Operator Authentication

#### Register New User
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "operator@elcafe.com",
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
      "email": "operator@elcafe.com",
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
  "email": "admin@elcafe.com",
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
  "phone": "+998901234567",
  "firstName": "John",
  "lastName": "Doe"
}
```

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
  "phone": "+998901234567",
  "otp": "123456"
}
```

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
  "name": "El Cafe Downtown",
  "description": "Best cafe in the city",
  "phone": "+998711234567",
  "email": "downtown@elcafe.com",
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
  "name": "El Cafe Downtown - Updated",
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
      "name": "El Cafe Downtown"
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
      "name": "El Cafe Downtown",
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
      "name": "El Cafe Downtown",
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
        "changedBy": "operator@elcafe.com",
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
POST /api/v1/consumer/orders/{orderNumber}/cancel
Content-Type: application/json

{
  "reason": "Changed my mind"
}
```

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
- Initiates automatic refund if payment completed
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

#### Get All Customers (Admin/Operator)
```http
GET /api/v1/customers?page=0&size=20
Authorization: Bearer {token}
```

#### Get Customer (Admin/Operator)
```http
GET /api/v1/customers/{id}
Authorization: Bearer {token}
```

#### Get Customer Order History (Admin/Operator)
```http
GET /api/v1/customers/{id}/orders
Authorization: Bearer {token}
```

### Customer Activity (RFM Analysis)

#### Get Customer Activity (Public)
```http
GET /api/v1/customers/activity?page=0&size=20
```

#### Filter Customers (Public)
```http
GET /api/v1/customers/activity/filter?rfmSegment=CHAMPION&minTotalSpent=100000
```

### Customer Addresses

#### Get Customer Addresses (Public)
```http
GET /api/v1/customers/{customerId}/addresses
```

#### Create Address (Public)
```http
POST /api/v1/customers/{customerId}/addresses
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

#### Set Default Address (Public)
```http
PUT /api/v1/customers/{customerId}/addresses/{addressId}/default
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
Content-Type: application/json

{
  "notes": "Order is ready for pickup"
}
```

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
Content-Type: application/json

{
  "notes": "Picked up by courier"
}
```

**Effects**:
- Status: READY → PICKED_UP
- Sets `pickedUpAt` timestamp
- Broadcasts WebSocket event

#### Update Priority (Admin/Operator)
```http
PATCH /api/v1/kitchen/orders/{id}/priority?priority=10
Authorization: Bearer {token}
```

---

## Waiter Module

### Waiter Authentication

#### Waiter PIN Login (Public)
```http
POST /api/v1/waiters/auth
Content-Type: application/json

{
  "pinCode": "1234"
}
```

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
  "email": "maria@elcafe.com",
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

**Table Status Changes:**
- When order is created: Table status changes from `FREE` → `OCCUPIED`
- When order is closed: Table status changes from `OCCUPIED` → `CLEANING`

**Response**: 201 Created
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": 123,
    "orderNumber": "ORD-1733412345678-A1B2C3D4",
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
- Order status changes to `ACCEPTED`
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
- Table status changes to `BILL_REQUESTED`

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
  "email": "ahmed@elcafe.com",
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

#### Daily Revenue (Admin/Operator)
```http
GET /api/v1/analytics/financial/daily-revenue?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Sales by Category (Admin/Operator)
```http
GET /api/v1/analytics/financial/sales-by-category?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### COGS Analytics (Admin/Operator)
```http
GET /api/v1/analytics/financial/cogs?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Profitability (Admin Only)
```http
GET /api/v1/analytics/financial/profitability?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1&laborCosts=1000000&operatingExpenses=500000
Authorization: Bearer {token}
```

#### Contribution Margins (Admin/Operator)
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

#### Kitchen Analytics (Admin/Operator/Kitchen Staff)
```http
GET /api/v1/analytics/operational/kitchen?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

### Customer Analytics

#### Customer Retention (Admin/Operator)
```http
GET /api/v1/analytics/customer/retention?startDate=2025-12-01&endDate=2025-12-05&restaurantId=1
Authorization: Bearer {token}
```

#### Customer LTV (Admin/Operator)
```http
GET /api/v1/analytics/customer/ltv?restaurantId=1
Authorization: Bearer {token}
```

#### Customer Satisfaction (Admin/Operator)
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

#### Login to SMS Broker (Admin/Operator)
```http
POST /api/v1/sms/auth/login
Authorization: Bearer {token}
Content-Type: application/json

{
  "email": "your-email@example.com",
  "password": "your-password"
}
```

#### Send SMS (Admin/Operator)
```http
POST /api/v1/sms/send
Authorization: Bearer {token}
Content-Type: application/json

{
  "phoneNumber": "+998901234567",
  "message": "Your order ORD-123 is ready for pickup!"
}
```

#### Get Message Status (Admin/Operator)
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

**Response**:
```json
{
  "success": true,
  "data": {
    "url": "https://storage.example.com/uploads/product-123.jpg",
    "fileName": "product-123.jpg",
    "fileSize": 245678,
    "contentType": "image/jpeg"
  }
}
```

#### Delete File (Admin)
```http
DELETE /api/v1/files?fileUrl=https://storage.example.com/uploads/product-123.jpg
Authorization: Bearer {token}
```

---

## Error Responses

All error responses follow this format:

```json
{
  "success": false,
  "message": "Error description",
  "errors": [
    {
      "field": "email",
      "message": "Email is required"
    }
  ],
  "timestamp": "2025-12-05T15:30:00Z"
}
```

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
```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
  console.log('Connected: ' + frame);
});
```

### Subscribe to Order Updates
```javascript
stompClient.subscribe('/topic/orders', function(message) {
  const orderUpdate = JSON.parse(message.body);
  console.log('Order Update:', orderUpdate);
});
```

### Subscribe to Kitchen Updates
```javascript
stompClient.subscribe('/topic/kitchen', function(message) {
  const kitchenUpdate = JSON.parse(message.body);
  console.log('Kitchen Update:', kitchenUpdate);
});
```

---

**For complete interactive documentation, visit**: http://localhost:8080/swagger-ui.html

**API Version**: 1.0.0
**Last Updated**: 2025-12-05

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

## Inventory Management

### Get All Ingredients
```http
GET /api/v1/inventory/ingredients?restaurantId=1
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
      "restaurantName": "El Cafe Downtown",
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
      "restaurantName": "El Cafe Downtown",
      "tableNumber": "T-01",
      "tableName": "Window Table 1",
      "status": "AVAILABLE",
      "capacity": 4,
      "section": "Main Hall",
      "active": true,
      "notes": "Near the window",
      "qrCode": "https://elcafe.com/qr/table-1",
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
      "restaurantName": "El Cafe Downtown",
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

**API Version**: 1.0.0
**Last Updated**: 2026-02-08
