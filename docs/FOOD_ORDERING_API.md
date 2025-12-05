# 🍽️ El Cafe - Food Ordering System Documentation

Complete API documentation for the comprehensive food ordering system with full lifecycle management.

## 📋 Table of Contents

1. [System Overview](#system-overview)
2. [Order Flow](#order-flow)
3. [API Reference](#api-reference)
4. [Integration Guide](#integration-guide)
5. [Notification System](#notification-system)
6. [Security & Authentication](#security--authentication)
7. [Error Handling](#error-handling)
8. [Testing Examples](#testing-examples)

---

## 🎯 System Overview

The El Cafe Food Ordering System provides a complete end-to-end solution for food ordering, preparation, and delivery management.

### Key Features

- **Public Order Placement API** - Website and mobile app integration
- **Kitchen Management System** - Real-time food preparation tracking
- **Courier Delivery System** - Delivery assignment and tracking
- **Courier Status Management** - Real-time courier online/offline status and GPS tracking
- **Courier Map Dashboard** - Live visualization of courier fleet with status monitoring
- **Real-time Notifications** - Multi-channel notification system
- **Order Status Tracking** - Live order tracking for customers
- **Role-based Access Control** - Secure endpoints for different user roles
- **Courier Wallet System** - Automatic payment calculation and tracking

### System Components

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│   Customer  │─────►│  Restaurant  │─────►│   Kitchen   │
│ (Website/App)│      │   (Accept)   │      │  (Prepare)  │
└─────────────┘      └──────────────┘      └─────────────┘
                                                    │
                                                    ▼
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│  Delivered  │◄─────│   Courier    │◄─────│   Ready     │
│             │      │  (Deliver)   │      │             │
└─────────────┘      └──────────────┘      └─────────────┘
```

---

## 🔄 Order Flow

### Complete Lifecycle

```
1. NEW                  → Customer places order
2. ACCEPTED             → Restaurant accepts order
3. PREPARING            → Kitchen starts preparation
4. READY                → Food ready for pickup
5. COURIER_ASSIGNED     → Courier accepts delivery
6. ON_DELIVERY          → Courier picks up and delivers
7. DELIVERED            → Order completed
```

### Status Transitions

| From Status | To Status | Trigger | Who Can Change |
|-------------|-----------|---------|----------------|
| NEW | ACCEPTED | Accept order | Restaurant, Admin, Operator |
| ACCEPTED | PREPARING | Start preparation | Kitchen Staff, Admin |
| PREPARING | READY | Food ready | Kitchen Staff, Admin |
| READY | COURIER_ASSIGNED | Courier accepts | Courier, Admin, Operator |
| COURIER_ASSIGNED | ON_DELIVERY | Start delivery | Courier |
| ON_DELIVERY | DELIVERED | Complete delivery | Courier |
| Any (except PREPARING+) | CANCELLED | Cancel order | Customer, Admin |

---

## 📚 API Reference

### Base URL

```
Production: https://api.elcafe.com/api/v1
Development: http://localhost:8080/api/v1
```

---

## 1️⃣ Consumer Order API (Public)

### 1.1 Place Order

**Endpoint:** `POST /consumer/orders`

**Authentication:** None (Public API)

**Description:** Place a new food order from website or mobile app.

**Request Body:**

```json
{
  "restaurantId": 1,
  "orderSource": "WEBSITE",
  "customerInfo": {
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+998901234567",
    "email": "john.doe@example.com"
  },
  "items": [
    {
      "productId": 10,
      "quantity": 2,
      "specialInstructions": "No onions please"
    },
    {
      "productId": 15,
      "quantity": 1,
      "specialInstructions": "Extra cheese"
    }
  ],
  "deliveryInfo": {
    "address": "123 Main Street, Apt 4B",
    "city": "Tashkent",
    "state": "Tashkent Region",
    "zipCode": "100000",
    "latitude": 41.2995,
    "longitude": 69.2401,
    "deliveryInstructions": "Ring doorbell twice"
  },
  "customerNotes": "Please deliver as soon as possible",
  "scheduledFor": null,
  "paymentMethod": "CASH"
}
```

**Response:** `201 Created`

```json
{
  "success": true,
  "message": "Order placed successfully",
  "data": {
    "id": 1234,
    "orderNumber": "ORD-A7B3C4D5",
    "status": "NEW",
    "orderSource": "WEBSITE",
    "subtotal": 45000,
    "deliveryFee": 10000,
    "tax": 4500,
    "discount": 0,
    "total": 59500,
    "customerNotes": "Please deliver as soon as possible",
    "createdAt": "2025-11-25T10:30:00",
    "estimatedDeliveryTime": null,
    "restaurant": {
      "id": 1,
      "name": "El Cafe Downtown",
      "phone": "+998712345678",
      "address": "456 Restaurant Ave"
    },
    "customer": {
      "id": 789,
      "firstName": "John",
      "lastName": "Doe",
      "phone": "+998901234567",
      "email": "john.doe@example.com"
    },
    "items": [
      {
        "id": 5001,
        "productName": "Margherita Pizza",
        "quantity": 2,
        "price": 20000,
        "total": 40000,
        "specialInstructions": "No onions please"
      },
      {
        "id": 5002,
        "productName": "Caesar Salad",
        "quantity": 1,
        "price": 5000,
        "total": 5000,
        "specialInstructions": "Extra cheese"
      }
    ],
    "deliveryInfo": {
      "address": "123 Main Street, Apt 4B",
      "city": "Tashkent",
      "state": "Tashkent Region",
      "zipCode": "100000",
      "latitude": 41.2995,
      "longitude": 69.2401,
      "deliveryInstructions": "Ring doorbell twice",
      "courierName": null,
      "courierPhone": null
    },
    "paymentInfo": {
      "paymentMethod": "CASH",
      "paymentStatus": "PENDING",
      "amount": 59500
    }
  }
}
```

**Validation Rules:**

- Restaurant ID: Required, must exist and be active
- Customer phone: Required, format: `+[country_code][number]`
- Items: At least 1 item required, max 100 per item
- Delivery address: Required, max 500 characters
- Payment method: CASH, CARD, or ONLINE

---

### 1.2 Track Order

**Endpoint:** `GET /consumer/orders/{orderNumber}`

**Authentication:** None (Public API)

**Description:** Track order status and get real-time updates.

**Path Parameters:**
- `orderNumber` - The order number (e.g., ORD-A7B3C4D5)

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Order retrieved successfully",
  "data": {
    "id": 1234,
    "orderNumber": "ORD-A7B3C4D5",
    "status": "ON_DELIVERY",
    "estimatedDeliveryTime": "2025-11-25T11:30:00",
    "deliveryInfo": {
      "courierName": "Ali Karimov",
      "courierPhone": "+998909876543"
    }
    // ... full order details
  }
}
```

---

### 1.3 Cancel Order

**Endpoint:** `POST /consumer/orders/{orderNumber}/cancel`

**Authentication:** None (Public API)

**Description:** Cancel an order (only if not yet preparing).

**Query Parameters:**
- `reason` (optional) - Cancellation reason

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Order cancelled successfully",
  "data": {
    "orderNumber": "ORD-A7B3C4D5",
    "status": "CANCELLED"
  }
}
```

**Limitations:**
- Cannot cancel if status is PREPARING, READY, ON_DELIVERY, or DELIVERED
- Only NEW and ACCEPTED orders can be cancelled by customer

---

## 2️⃣ Kitchen Order API

**Base Path:** `/kitchen/orders`

**Authentication:** Required (ADMIN, OPERATOR, KITCHEN_STAFF roles)

---

### 2.1 Get Active Orders

**Endpoint:** `GET /kitchen/orders/active`

**Description:** Get all pending and preparing orders.

**Query Parameters:**
- `restaurantId` (optional) - Filter by restaurant

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Active orders retrieved",
  "data": [
    {
      "id": 101,
      "order": {
        "id": 1234,
        "orderNumber": "ORD-A7B3C4D5"
      },
      "status": "PREPARING",
      "priority": "HIGH",
      "assignedChef": "Chef Mario",
      "preparationStartedAt": "2025-11-25T10:35:00",
      "estimatedPreparationTimeMinutes": 30,
      "createdAt": "2025-11-25T10:30:00"
    }
  ]
}
```

---

### 2.2 Get Ready Orders

**Endpoint:** `GET /kitchen/orders/ready`

**Description:** Get all orders ready for courier pickup.

**Query Parameters:**
- `restaurantId` (optional) - Filter by restaurant

---

### 2.3 Start Preparation

**Endpoint:** `POST /kitchen/orders/{id}/start`

**Description:** Start preparing an order.

**Query Parameters:**
- `chefName` (required) - Name of the chef starting preparation

**Request Example:**
```
POST /kitchen/orders/101/start?chefName=Chef Mario
```

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Preparation started",
  "data": {
    "id": 101,
    "status": "PREPARING",
    "assignedChef": "Chef Mario",
    "preparationStartedAt": "2025-11-25T10:35:00"
  }
}
```

**Side Effects:**
- Main order status changes to PREPARING
- Customer receives notification
- Status history is updated

---

### 2.4 Mark as Ready

**Endpoint:** `POST /kitchen/orders/{id}/ready`

**Description:** Mark food as ready for pickup/delivery.

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Order marked as ready",
  "data": {
    "id": 101,
    "status": "READY",
    "preparationCompletedAt": "2025-11-25T11:05:00",
    "actualPreparationTimeMinutes": 30
  }
}
```

**Side Effects:**
- Main order status changes to READY
- Available couriers receive notification
- Customer receives notification
- Kitchen order completion time is calculated

---

### 2.5 Mark as Picked Up

**Endpoint:** `POST /kitchen/orders/{id}/picked-up`

**Description:** Mark order as picked up by courier.

**Roles:** ADMIN, KITCHEN_STAFF, COURIER

---

### 2.6 Update Priority

**Endpoint:** `PATCH /kitchen/orders/{id}/priority`

**Description:** Update order priority.

**Query Parameters:**
- `priority` (required) - LOW, NORMAL, HIGH, or URGENT

**Request Example:**
```
PATCH /kitchen/orders/101/priority?priority=URGENT
```

---

## 3️⃣ Courier Order API

**Base Path:** `/courier/orders`

**Authentication:** Required (COURIER role for most endpoints)

---

### 3.1 Get Available Orders

**Endpoint:** `GET /courier/orders/available`

**Description:** View orders ready for delivery assignment.

**Query Parameters:**
- `restaurantId` (optional) - Filter by restaurant

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Available orders retrieved",
  "data": [
    {
      "id": 1234,
      "orderNumber": "ORD-A7B3C4D5",
      "status": "READY",
      "restaurant": {
        "name": "El Cafe Downtown",
        "address": "456 Restaurant Ave"
      },
      "deliveryInfo": {
        "address": "123 Main Street, Apt 4B",
        "city": "Tashkent",
        "latitude": 41.2995,
        "longitude": 69.2401
      },
      "total": 59500,
      "createdAt": "2025-11-25T10:30:00"
    }
  ]
}
```

---

### 3.2 Get My Orders

**Endpoint:** `GET /courier/orders/my-orders`

**Description:** Get orders assigned to current courier.

**Query Parameters:**
- `courierId` (required) - Courier's ID

**Response:** Returns list of assigned orders with full details.

---

### 3.3 Accept Order

**Endpoint:** `POST /courier/orders/{orderId}/accept`

**Description:** Courier accepts an order for delivery.

**Query Parameters:**
- `courierId` (required) - Courier's ID

**Request Example:**
```
POST /courier/orders/1234/accept?courierId=456
```

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Order accepted",
  "data": {
    "id": 1234,
    "orderNumber": "ORD-A7B3C4D5",
    "status": "COURIER_ASSIGNED",
    "deliveryInfo": {
      "courierId": 456,
      "courierName": "Ali Karimov",
      "courierPhone": "+998909876543"
    }
  }
}
```

**Validations:**
- Order must be in READY status
- Order cannot already have a courier assigned
- Courier must exist in system

**Side Effects:**
- Order status changes to COURIER_ASSIGNED
- Customer receives notification with courier details
- Kitchen receives notification
- Courier info added to delivery info

---

### 3.4 Decline Order

**Endpoint:** `POST /courier/orders/{orderId}/decline`

**Description:** Courier declines an order.

**Query Parameters:**
- `courierId` (required) - Courier's ID
- `reason` (optional) - Reason for declining

**Request Example:**
```
POST /courier/orders/1234/decline?courierId=456&reason=Too far from current location
```

**Response:** `200 OK`

**Side Effects:**
- Restaurant and admin receive notification
- Order remains available for other couriers
- System may auto-assign to another courier

---

### 3.5 Manual Courier Assignment

**Endpoint:** `POST /courier/orders/assign`

**Description:** Admin/Operator manually assigns a courier.

**Roles:** ADMIN, OPERATOR

**Query Parameters:**
- `orderId` (required) - Order ID
- `courierId` (required) - Courier ID to assign

**Request Example:**
```
POST /courier/orders/assign?orderId=1234&courierId=456
```

---

### 3.6 Start Delivery

**Endpoint:** `POST /courier/orders/{orderId}/start-delivery`

**Description:** Courier starts delivery after picking up order.

**Query Parameters:**
- `courierId` (required) - Courier's ID

**Request Example:**
```
POST /courier/orders/1234/start-delivery?courierId=456
```

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Delivery started",
  "data": {
    "id": 1234,
    "status": "ON_DELIVERY",
    "deliveryInfo": {
      "pickupTime": "2025-11-25T11:10:00",
      "estimatedDeliveryTime": "2025-11-25T11:40:00"
    }
  }
}
```

**Validations:**
- Order must be in COURIER_ASSIGNED status
- Requesting courier must be the assigned courier

**Side Effects:**
- Order status changes to ON_DELIVERY
- Pickup time recorded
- Estimated delivery time calculated (pickup time + 30 min)
- Customer receives notification
- Kitchen order marked as picked up

---

### 3.7 Complete Delivery

**Endpoint:** `POST /courier/orders/{orderId}/complete`

**Description:** Mark order as delivered.

**Query Parameters:**
- `courierId` (required) - Courier's ID
- `notes` (optional) - Delivery notes

**Request Example:**
```
POST /courier/orders/1234/complete?courierId=456&notes=Delivered to reception desk
```

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Order delivered",
  "data": {
    "id": 1234,
    "status": "DELIVERED",
    "deliveryInfo": {
      "deliveryTime": "2025-11-25T11:35:00"
    }
  }
}
```

**Validations:**
- Order must be in ON_DELIVERY status
- Requesting courier must be the assigned courier

**Side Effects:**
- Order status changes to DELIVERED
- Delivery time recorded
- Customer receives notification
- Restaurant receives notification
- Admin dashboard updated
- Courier wallet credited with delivery fee

---

## 4️⃣ Courier Status Management

**Base Path:** `/couriers`

**Authentication:** Required (COURIER role for status updates)

---

### 4.1 Update Courier Status

**Endpoint:** `POST /couriers/{id}/status`

**Description:** Update courier online/offline status and location from courier mobile app.

**Authentication:** Required (COURIER role)

**Path Parameters:**
- `id` (required) - Courier ID

**Request Body:**

```json
{
  "status": "ONLINE",
  "latitude": 41.2995,
  "longitude": 69.2401
}
```

**Status Values:**
- `ONLINE` - Courier is online and available for orders
- `OFFLINE` - Courier is offline
- `ON_DELIVERY` - Courier is currently delivering an order
- `BUSY` - Courier is busy (on break, handling issue)

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Status updated successfully",
  "data": {
    "courierId": 456,
    "courierName": "Ali Karimov",
    "isOnline": true,
    "currentStatus": "ONLINE",
    "lastSeenAt": "2025-11-25T11:45:00",
    "lastLocationUpdateAt": "2025-11-25T11:45:00",
    "latitude": 41.2995,
    "longitude": 69.2401
  }
}
```

**Side Effects:**
- Courier status updated in database
- Last seen timestamp updated
- If location provided, GPS coordinates saved to courier_locations table
- Last location update timestamp recorded
- Available for order assignment if status is ONLINE

---

### 4.2 Get Courier Status

**Endpoint:** `GET /couriers/{id}/status`

**Description:** Get current courier status and last known location.

**Authentication:** Required (ADMIN, OPERATOR, or COURIER roles)

**Path Parameters:**
- `id` (required) - Courier ID

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Status retrieved successfully",
  "data": {
    "courierId": 456,
    "courierName": "Ali Karimov",
    "isOnline": true,
    "currentStatus": "ONLINE",
    "lastSeenAt": "2025-11-25T11:45:00",
    "lastLocationUpdateAt": "2025-11-25T11:45:00",
    "latitude": 41.2995,
    "longitude": 69.2401
  }
}
```

**Use Cases:**
- Admin dashboard courier map
- Real-time courier tracking
- Availability monitoring
- Order assignment decisions

---

## 📬 Notification System

### Notification Triggers

| Event | Recipients | Channels |
|-------|-----------|----------|
| **New Order** | Restaurant, Kitchen, Admin | WebSocket, Email |
| **Order Accepted** | Customer, Kitchen | SMS, WebSocket |
| **Preparation Started** | Customer, Couriers | SMS, Push |
| **Order Ready** | Customer, Available Couriers | SMS, WebSocket, Push |
| **Courier Assigned** | Customer, Kitchen | SMS, WebSocket |
| **Courier Accepted** | Customer, Restaurant | SMS, WebSocket |
| **Courier Declined** | Restaurant, Admin | WebSocket |
| **Out for Delivery** | Customer | SMS, Push |
| **Delivered** | Customer, Restaurant | SMS, WebSocket |
| **Order Cancelled** | All parties | SMS, WebSocket, Email |

### Notification Content

**New Order (to Restaurant):**
```
🔔 New Order: ORD-A7B3C4D5
Customer: John Doe
Items: 2x Margherita Pizza, 1x Caesar Salad
Total: 59,500 UZS
Delivery: 123 Main Street, Tashkent
```

**Courier Assigned (to Customer):**
```
🚗 Your courier is on the way!
Courier: Ali Karimov
Phone: +998909876543
Estimated delivery: 11:40 AM
Track: elcafe.com/track/ORD-A7B3C4D5
```

**Order Delivered (to Customer):**
```
✅ Your order has been delivered!
Order: ORD-A7B3C4D5
Thank you for choosing El Cafe!
Rate your experience: elcafe.com/rate/ORD-A7B3C4D5
```

---

## 🔐 Security & Authentication

### Authentication

Most endpoints require JWT bearer token authentication:

```
Authorization: Bearer <token>
```

### Public Endpoints (No Auth Required)

- `POST /consumer/orders` - Place order
- `GET /consumer/orders/{orderNumber}` - Track order
- `POST /consumer/orders/{orderNumber}/cancel` - Cancel order

### Role-Based Access Control

| Endpoint | Required Roles |
|----------|---------------|
| Kitchen Orders | ADMIN, OPERATOR, KITCHEN_STAFF |
| Courier Orders (view) | COURIER, ADMIN, OPERATOR |
| Courier Assignment | ADMIN, OPERATOR |
| Update Priority | ADMIN, OPERATOR |

### Security Best Practices

1. **Always validate input** - All requests are validated
2. **Verify ownership** - Couriers can only act on their assigned orders
3. **Status validation** - Transitions only allowed from valid statuses
4. **Rate limiting** - Implement rate limiting on public APIs
5. **HTTPS only** - All production traffic over HTTPS

---

## ⚠️ Error Handling

### Standard Error Response

```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

### Common Error Codes

| Status Code | Meaning | Example |
|-------------|---------|---------|
| 400 | Bad Request | Invalid input, validation failed |
| 401 | Unauthorized | Missing or invalid token |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Order/Resource not found |
| 409 | Conflict | Invalid status transition |
| 500 | Server Error | Internal server error |

### Validation Errors

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "customerInfo.phone",
      "message": "Invalid phone number format"
    },
    {
      "field": "items",
      "message": "Order must contain at least one item"
    }
  ]
}
```

### Business Logic Errors

```json
{
  "success": false,
  "message": "Restaurant is not accepting orders"
}
```

```json
{
  "success": false,
  "message": "Cannot cancel order in current status: PREPARING"
}
```

```json
{
  "success": false,
  "message": "This order is not assigned to you"
}
```

---

## 🧪 Testing Examples

### cURL Examples

**Place Order:**

```bash
curl -X POST http://localhost:8080/api/v1/consumer/orders \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": 1,
    "orderSource": "WEBSITE",
    "customerInfo": {
      "firstName": "John",
      "lastName": "Doe",
      "phone": "+998901234567",
      "email": "john@example.com"
    },
    "items": [
      {
        "productId": 10,
        "quantity": 2,
        "specialInstructions": "No onions"
      }
    ],
    "deliveryInfo": {
      "address": "123 Main St",
      "city": "Tashkent",
      "zipCode": "100000",
      "latitude": 41.2995,
      "longitude": 69.2401
    },
    "paymentMethod": "CASH"
  }'
```

**Track Order:**

```bash
curl http://localhost:8080/api/v1/consumer/orders/ORD-A7B3C4D5
```

**Start Preparation (with auth):**

```bash
curl -X POST "http://localhost:8080/api/v1/kitchen/orders/101/start?chefName=Chef%20Mario" \
  -H "Authorization: Bearer <token>"
```

**Courier Accept Order:**

```bash
curl -X POST "http://localhost:8080/api/v1/courier/orders/1234/accept?courierId=456" \
  -H "Authorization: Bearer <token>"
```

### Postman Collection

Import this collection for complete API testing:

**Environment Variables:**
```json
{
  "base_url": "http://localhost:8080/api/v1",
  "auth_token": "<your_token>",
  "order_number": "ORD-A7B3C4D5",
  "order_id": "1234",
  "courier_id": "456",
  "kitchen_order_id": "101"
}
```

---

## 🚀 Integration Guide

### Frontend Integration Steps

#### 1. Customer Website/Mobile App

**Place Order Flow:**

```javascript
// 1. Get restaurant menu
const menu = await fetch('/menu/public/{restaurantId}');

// 2. Build order
const order = {
  restaurantId: 1,
  orderSource: 'WEBSITE', // or 'MOBILE_APP'
  customerInfo: { /* ... */ },
  items: selectedItems,
  deliveryInfo: { /* ... */ },
  paymentMethod: 'CASH'
};

// 3. Place order
const response = await fetch('/consumer/orders', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(order)
});

const result = await response.json();
const orderNumber = result.data.orderNumber;

// 4. Track order
const trackingUrl = `/consumer/orders/${orderNumber}`;
```

**Real-time Tracking:**

```javascript
// Poll for updates every 10 seconds
setInterval(async () => {
  const response = await fetch(`/consumer/orders/${orderNumber}`);
  const order = await response.json();

  updateOrderStatus(order.data.status);

  if (order.data.deliveryInfo.courierName) {
    showCourierInfo(order.data.deliveryInfo);
  }
}, 10000);
```

#### 2. Kitchen Dashboard

**Active Orders Display:**

```javascript
// Get active orders
const response = await fetch('/kitchen/orders/active?restaurantId=1', {
  headers: { 'Authorization': `Bearer ${token}` }
});

const orders = await response.json();

// Start preparation
async function startOrder(kitchenOrderId, chefName) {
  await fetch(`/kitchen/orders/${kitchenOrderId}/start?chefName=${chefName}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
}

// Mark as ready
async function markReady(kitchenOrderId) {
  await fetch(`/kitchen/orders/${kitchenOrderId}/ready`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
}
```

#### 3. Courier Mobile App

**Available Orders:**

```javascript
// Get available orders near courier
const response = await fetch('/courier/orders/available', {
  headers: { 'Authorization': `Bearer ${courierToken}` }
});

// Accept order
async function acceptOrder(orderId, courierId) {
  await fetch(`/courier/orders/${orderId}/accept?courierId=${courierId}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${courierToken}` }
  });
}

// Complete delivery flow
async function startDelivery(orderId, courierId) {
  await fetch(`/courier/orders/${orderId}/start-delivery?courierId=${courierId}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${courierToken}` }
  });
}

async function completeDelivery(orderId, courierId, notes) {
  await fetch(`/courier/orders/${orderId}/complete?courierId=${courierId}&notes=${notes}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${courierToken}` }
  });
}
```

### WebSocket Integration (Real-time Updates)

```javascript
// Connect to WebSocket for real-time notifications
const ws = new WebSocket('ws://localhost:8080/ws');

ws.onmessage = (event) => {
  const notification = JSON.parse(event.data);

  switch(notification.type) {
    case 'NEW_ORDER':
      playSound('new-order.mp3');
      showNotification('New order received!');
      refreshOrderList();
      break;

    case 'ORDER_READY':
      notifyCouriers(notification.orderId);
      break;

    case 'COURIER_ASSIGNED':
      updateCustomerApp(notification.orderNumber);
      break;
  }
};
```

---

## 📊 Database Schema

### Key Entities

**Order:**
- id, orderNumber, restaurantId, customerId
- status, orderSource
- subtotal, deliveryFee, tax, discount, total
- customerNotes, scheduledFor
- timestamps

**KitchenOrder:**
- id, orderId
- status (PENDING, PREPARING, READY, PICKED_UP)
- priority (LOW, NORMAL, HIGH, URGENT)
- assignedChef
- preparationStartedAt, preparationCompletedAt
- actualPreparationTimeMinutes

**DeliveryInfo:**
- id, orderId
- address, city, state, zipCode
- latitude, longitude
- courierId, courierName, courierPhone
- pickupTime, deliveryTime
- estimatedDeliveryTime

---

## 🎯 Best Practices

### For Frontend Developers

1. **Always handle loading states** - Show spinners during API calls
2. **Implement error handling** - Display user-friendly error messages
3. **Use polling for real-time updates** - Poll every 10-30 seconds
4. **Cache restaurant menus** - Reduce unnecessary API calls
5. **Validate before submission** - Client-side validation first
6. **Store order number** - Save locally for tracking

### For Backend Developers

1. **Use transactions** - All order operations are transactional
2. **Log all actions** - Complete audit trail of order changes
3. **Send notifications** - Never forget to notify stakeholders
4. **Validate state transitions** - Prevent invalid status changes
5. **Handle concurrency** - Prevent race conditions
6. **Monitor performance** - Log slow queries

---

## 📞 Support & Resources

**API Base URL:** `http://localhost:8080/api/v1`

**Swagger Documentation:** `http://localhost:8080/swagger-ui.html`

**OpenAPI Spec:** `http://localhost:8080/v3/api-docs`

---

## 🔄 Version History

**v1.0.0** (2025-11-25)
- Initial release
- Complete order lifecycle management
- Kitchen module
- Courier management
- Notification system
- Public consumer API

---

**Last Updated:** November 25, 2025
**API Version:** v1
**Documentation Version:** 1.0.0
