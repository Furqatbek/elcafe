# Qahvoon API Documentation

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication
Most endpoints require authentication using Bearer token in the Authorization header:
```
Authorization: Bearer <your-token>
```

---

## Waiter Orders API

### 1. Create Order
Create a new order for a table with optional items.

**Endpoint:** `POST /waiter/orders`

**Headers:**
- `Authorization: Bearer <token>`
- `X-Waiter-Id: <waiterId>`

**Request Body:**
```json
{
  "tableId": 1,
  "customerId": 1,  // Optional
  "customerNotes": "Customer request notes",
  "items": [  // Optional - can add items in same request
    {
      "productId": 1,
      "variantId": 3,  // Optional
      "quantity": 2,
      "addOns": "Extra cheese",  // Optional
      "specialInstructions": "No onions"  // Optional
    }
  ]
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": 1,
    "orderNumber": "W651234567",
    "status": "NEW",
    "subtotal": 25.00,
    "tax": 2.50,
    "total": 27.50,
    "items": [...],
    "createdAt": "2025-12-19T18:00:00"
  }
}
```

---

### 2. Get Order
Get order details by ID.

**Endpoint:** `GET /waiter/orders/{id}`

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Order retrieved successfully",
  "data": { /* Order object */ }
}
```

---

### 3. Get Table Orders
Get all active orders for a specific table.

**Endpoint:** `GET /waiter/orders/table/{tableId}`

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Table orders retrieved successfully",
  "data": [ /* Array of Order objects */ ]
}
```

---

### 4. Add Items to Order
Add one or more items to an existing order.

**Endpoint:** `POST /waiter/orders/{orderId}/items`

**Headers:**
- `X-Waiter-Id: <waiterId>`

**Request Body:**
```json
[
  {
    "productId": 2,
    "variantId": 4,
    "quantity": 1,
    "specialInstructions": "Well done"
  }
]
```

**Response:** `200 OK`

---

### 5. Update Order Item
Update an existing order item.

**Endpoint:** `PUT /waiter/orders/{orderId}/items/{itemId}`

**Request Body:**
```json
{
  "quantity": 3,
  "addOns": "Extra sauce",
  "specialInstructions": "Medium rare"
}
```

**Response:** `200 OK`

---

### 6. Remove Order Item
Remove an item from order.

**Endpoint:** `DELETE /waiter/orders/{orderId}/items/{itemId}`

**Response:** `200 OK`

---

### 7. Submit Order to Kitchen
Submit order to kitchen for preparation.

**Endpoint:** `POST /waiter/orders/{orderId}/submit`

**Response:** `200 OK`

---

### 8. Mark Item as Delivered
Mark an order item as delivered to customer.

**Endpoint:** `POST /waiter/orders/{orderId}/items/{itemId}/deliver`

**Response:** `200 OK`

---

### 9. Request Bill
Request bill for the order.

**Endpoint:** `POST /waiter/orders/{orderId}/bill`

**Response:** `200 OK`

---

### 10. Close Order
Close order after payment completion.

**Endpoint:** `POST /waiter/orders/{orderId}/close`

**Response:** `200 OK`

---

### 11. Get Order Event History
Get complete event history for a specific order.

**Endpoint:** `GET /waiter/orders/{orderId}/history`

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Order history retrieved successfully",
  "data": [
    {
      "eventType": "ORDER_CREATED",
      "triggeredBy": "John Doe",
      "timestamp": "2025-12-19T18:00:00",
      "metadata": {}
    }
  ]
}
```

---

### 12. Get Waiter Order History ⭐ NEW
Get all completed and cancelled orders for a specific waiter.

**Endpoint:** `GET /waiter/orders/waiter/{waiterId}/history`

**Description:** Returns all orders with status COMPLETED or CANCELLED, sorted by creation date (newest first).

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Waiter order history retrieved successfully",
  "data": [ /* Array of completed/cancelled Order objects */ ]
}
```

---

### 13. Get Waiter Ongoing Orders ⭐ NEW
Get all active orders for a specific waiter.

**Endpoint:** `GET /waiter/orders/waiter/{waiterId}/ongoing`

**Description:** Returns all orders with status NEW, PREPARING, READY, or ON_DELIVERY, sorted by creation date (newest first).

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Waiter ongoing orders retrieved successfully",
  "data": [ /* Array of active Order objects */ ]
}
```

---

## Table Management API

### 1. Create Table
Create a new table.

**Endpoint:** `POST /tables`

**Request Body:**
```json
{
  "restaurantId": 1,
  "tableNumber": "T-101",
  "tableName": "Window Table",
  "capacity": 4,
  "section": "Main Hall",
  "notes": "Near window",
  "active": true
}
```

**Response:** `201 Created`

---

### 2. Update Table
Update table information.

**Endpoint:** `PUT /tables/{id}`

**Request Body:** Same as Create Table

**Response:** `200 OK`

---

### 3. Get Table by ID
Get detailed table information.

**Endpoint:** `GET /tables/{id}`

**Response:** `200 OK`

---

### 4. Get Tables by Restaurant
Get all tables for a restaurant.

**Endpoint:** `GET /restaurants/{restaurantId}/tables`

**Response:** `200 OK`

---

### 5. Get Available Tables
Get all available tables.

**Endpoint:** `GET /restaurants/{restaurantId}/tables/available`

**Response:** `200 OK`

---

### 6. Update Table Status
Update table status.

**Endpoint:** `PATCH /tables/{id}/status`

**Request Body:**
```json
{
  "status": "OCCUPIED"
}
```

**Valid Statuses:**
- `AVAILABLE`
- `OCCUPIED`
- `RESERVED`
- `CLEANING`
- `OUT_OF_SERVICE`

**Response:** `200 OK`

---

### 7. Merge Tables ⭐ NEW
Merge multiple tables into one main table.

**Endpoint:** `POST /tables/merge`

**Description:** Combines multiple tables into a single logical table. The first table becomes the main table, and its capacity is updated to the sum of all merged tables. Other tables are marked as RESERVED.

**Request Body:**
```json
{
  "mainTableId": 1,
  "tableIdsToMerge": [2, 3, 4]
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Tables merged successfully",
  "data": [
    {
      "id": 1,
      "tableNumber": "T-1",
      "capacity": 12,  // Sum of all merged table capacities
      "originalCapacity": 4,
      "status": "AVAILABLE"
    },
    {
      "id": 2,
      "tableNumber": "T-2",
      "capacity": 4,
      "originalCapacity": 4,
      "mergedTable": { /* Reference to main table */ },
      "status": "RESERVED"
    }
  ]
}
```

**Validation Rules:**
- Main table must not already be merged
- Tables to merge must not already be part of another merge
- All tables must be in the same restaurant
- Cannot merge a table with itself

---

### 8. Unmerge Tables ⭐ NEW
Unmerge all tables in a merge group.

**Endpoint:** `POST /tables/{tableId}/unmerge`

**Description:** Separates all tables in a merge group back to individual tables. Can be called on any table in the merge group (main or merged). All tables return to their original capacities and statuses.

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Tables unmerged successfully",
  "data": [ /* Array of unmerged table objects */ ]
}
```

---

### 9. Get Merged Tables ⭐ NEW
Get all tables in a merge group.

**Endpoint:** `GET /tables/{tableId}/merged`

**Description:** Returns all tables that are part of the same merge group as the specified table.

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [ /* Array of tables in the merge group */ ]
}
```

---

### 10. Delete Table
Delete a table.

**Endpoint:** `DELETE /tables/{id}`

**Response:** `200 OK`

---

### 11. Get Table Statistics
Get statistics about tables.

**Endpoint:** `GET /restaurants/{restaurantId}/tables/stats`

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "totalTables": 20,
    "availableTables": 12,
    "occupiedTables": 5,
    "reservedTables": 3
  }
}
```

---

## Order Statuses

### Order Lifecycle
```
NEW → PREPARING → READY → ON_DELIVERY → COMPLETED
                           ↓
                      CANCELLED
```

### Status Descriptions
- **NEW**: Order created, waiting to be submitted
- **PREPARING**: Order submitted to kitchen, being prepared
- **READY**: Order ready for pickup/delivery
- **ON_DELIVERY**: Order is being delivered (for delivery orders)
- **COMPLETED**: Order successfully completed
- **CANCELLED**: Order cancelled

---

## Table Merge Workflow

### Merging Tables
1. Select 2 or more tables to merge
2. Call `POST /tables/merge` with mainTableId and tableIdsToMerge
3. First table becomes main table with combined capacity
4. Other tables marked as RESERVED and linked to main table
5. Original capacities saved for restoration

### Using Merged Tables
- Orders can only be placed on the main table
- Main table shows combined capacity
- Merged tables appear as RESERVED

### Unmerging Tables
1. Call `POST /tables/{tableId}/unmerge` on any table in the group
2. All tables return to original capacities
3. All tables return to AVAILABLE status
4. Merge relationships removed

---

## Error Responses

All errors use the shared `ApiResponse` envelope: `{success, message, data, errors, error, requestId, timestamp}` with `@JsonInclude(NON_NULL)` (null fields are omitted). `error` is a stable, machine-readable code (e.g. `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `INTERNAL`) that clients branch on instead of the `message` text; `requestId` correlates the response with server logs. When present, `errors` is a **map of field name → message string** (values are strings, not arrays).

### 400 Bad Request
```json
{
  "success": false,
  "message": "Validation failed",
  "error": "VALIDATION_ERROR",
  "errors": {
    "field": "Error details"
  },
  "requestId": "b1e7c0a2-..."
}
```

### 401 Unauthorized
```json
{
  "success": false,
  "message": "Unauthorized access",
  "error": "UNAUTHENTICATED",
  "requestId": "b1e7c0a2-..."
}
```

### 403 Forbidden
```json
{
  "success": false,
  "message": "Access denied",
  "error": "FORBIDDEN",
  "requestId": "b1e7c0a2-..."
}
```

### 404 Not Found
```json
{
  "success": false,
  "message": "Resource not found",
  "error": "NOT_FOUND",
  "requestId": "b1e7c0a2-..."
}
```

### 500 Internal Server Error
Always returns the fixed generic message with `error` `INTERNAL` — internal details are never leaked; use `requestId` to correlate with logs.
```json
{
  "success": false,
  "message": "An unexpected error occurred",
  "error": "INTERNAL",
  "requestId": "b1e7c0a2-..."
}
```

---

## Rate Limiting
- Rate limiting **is** implemented. The sensitive auth flows — admin/operator login, consumer OTP request (`/consumer/auth/login`), OTP verify (`/consumer/auth/verify`), and waiter PIN authentication (`/waiters/auth`) — are annotated `@RateLimited`.
- When a limit is exceeded, a dedicated handler returns **429 Too Many Requests** with `error` code `RATE_LIMITED`.
- Expensive analytics endpoints are additionally rate-limited.

## Pagination
- Default page size: 20 items
- Max page size: 100 items

## Notes
- All timestamps are in ISO 8601 format
- Monetary values are **not** uniformly 2-decimal: consumer and menu amounts are **integer UZS** (e.g. `15000`), whereas the waiter-order examples in this document use a 2-decimal format. Refer to each endpoint's response shape.
- Order numbers follow format: W{timestamp}{random} (e.g., W651234567)
