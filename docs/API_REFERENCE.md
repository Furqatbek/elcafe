# El Cafe API Reference

**Version**: 1.0.0
**Base URL**: `http://localhost:8080/api/v1`
**Documentation**: http://localhost:8080/swagger-ui.html
**Last Updated**: 2025-12-05

## Table of Contents

1. [Authentication](#authentication)
2. [Restaurant Management](#restaurant-management)
3. [Menu Management](#menu-management)
4. [Order Management](#order-management)
5. [Customer Management](#customer-management)
6. [Kitchen Operations](#kitchen-operations)
7. [Waiter Module](#waiter-module)
8. [Courier System](#courier-system)
9. [Analytics](#analytics)
10. [SMS Service](#sms-service)
11. [File Upload](#file-upload)

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
Content-Type: application/json

{
  "tableId": 1,
  "numberOfGuests": 4,
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "specialInstructions": "Extra hot"
    }
  ]
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
Content-Type: application/json

{
  "items": [
    {
      "productId": 2,
      "quantity": 1,
      "specialInstructions": "No sugar"
    }
  ]
}
```

#### Update Order Item (Waiter/Supervisor)
```http
PUT /api/v1/waiter/orders/{orderId}/items/{itemId}
Authorization: Bearer {token}
Content-Type: application/json

{
  "quantity": 3,
  "specialInstructions": "Updated instructions"
}
```

#### Delete Order Item (Waiter/Supervisor)
```http
DELETE /api/v1/waiter/orders/{orderId}/items/{itemId}
Authorization: Bearer {token}
```

#### Submit Order to Kitchen (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/submit
Authorization: Bearer {token}
```

#### Mark Item Delivered (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/items/{itemId}/deliver
Authorization: Bearer {token}
```

#### Request Bill (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/bill
Authorization: Bearer {token}
```

#### Close Order (Waiter/Supervisor)
```http
POST /api/v1/waiter/orders/{orderId}/close
Authorization: Bearer {token}
Content-Type: application/json

{
  "paymentMethod": "CASH",
  "paidAmount": 50000,
  "tip": 5000
}
```

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

- **OTP requests**: 3 requests per 15 minutes per phone number
- **Login attempts**: 5 attempts per 15 minutes per user
- **Public API**: 100 requests per minute per IP

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
