# Waiter Module - Quick Start Guide

Get started with the Waiter Module in 5 minutes!

## Prerequisites

- ElCafe backend running on `http://localhost:8080`
- Database migrations completed (V15)
- WebSocket dependency installed

## Step 1: Create a Waiter

```bash
curl -X POST http://localhost:8080/api/v1/waiters \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "name": "John Doe",
    "pinCode": "1234",
    "email": "john@restaurant.com",
    "role": "WAITER"
  }'
```

**Response:**
```json
{
  "id": 1,
  "name": "John Doe",
  "role": "WAITER",
  "active": true
}
```

## Step 2: Create Tables

```bash
# Create Table A1
curl -X POST http://localhost:8080/api/v1/tables \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "restaurantId": 1,
    "number": "A1",
    "capacity": 4,
    "floor": 1,
    "section": "Main Hall"
  }'

# Create Table A2
curl -X POST http://localhost:8080/api/v1/tables \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "restaurantId": 1,
    "number": "A2",
    "capacity": 2,
    "floor": 1,
    "section": "Main Hall"
  }'
```

## Step 3: Waiter Login

```bash
curl -X POST http://localhost:8080/api/v1/waiters/auth \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": 1,
    "pinCode": "1234"
  }'
```

**Response:**
```json
{
  "waiterId": 1,
  "name": "John Doe",
  "token": "eyJhbGc...",
  "waiter": {
    "id": 1,
    "name": "John Doe",
    "role": "WAITER",
    "active": true
  }
}
```

Save the token for subsequent requests!

## Step 4: Occupy a Table (status change)

There is no "open"/"close" endpoint — a table's lifecycle is driven by its status.

```bash
curl -X PATCH http://localhost:8080/api/v1/tables/1/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer WAITER_TOKEN" \
  -d '{
    "status": "OCCUPIED"
  }'
```

**Response:**
```json
{
  "id": 1,
  "number": "A1",
  "status": "OCCUPIED",
  "currentWaiterId": 1,
  "openedAt": "2025-12-02T10:00:00"
}
```

## Step 5: Create an Order

```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer WAITER_TOKEN" \
  -H "X-Waiter-Id: 1" \
  -d '{
    "tableId": 1,
    "customerId": 1,
    "items": [
      {
        "productId": 5,
        "quantity": 2,
        "notes": "No onions"
      },
      {
        "productId": 10,
        "quantity": 1
      }
    ]
  }'
```

**Response:**
```json
{
  "orderId": 100,
  "orderNumber": "ORD-1733137200000-ABC123",
  "tableId": 1,
  "waiterId": 1,
  "status": "NEW",
  "total": 45.50
}
```

## Step 6: Submit Order to Kitchen

```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/submit \
  -H "Authorization: Bearer WAITER_TOKEN" \
  -H "X-Waiter-Id: 1"
```

**Response:**
```json
{
  "orderId": 100,
  "status": "ACCEPTED",
  "message": "Order submitted to kitchen successfully"
}
```

## Step 7: Connect to WebSocket (Optional)

```javascript
// In your frontend JavaScript
const socket = new SockJS('http://localhost:8080/ws-waiter');
const stompClient = Stomp.over(socket);

// WebSocket auth defaults to 'shadow' mode (logs violations, does NOT block); it only
// blocks when app.websocket.auth.mode=enforce. Send the Bearer token on CONNECT anyway.
// Topics are tenant-scoped: subscribe under your own restaurantId.
const token = '<access token>';
const restaurantId = 1;

stompClient.connect({ Authorization: 'Bearer ' + token }, (frame) => {
  console.log('Connected!');

  // Subscribe to waiter notifications (user-scoped)
  stompClient.subscribe('/user/queue/notifications', (message) => {
    const notification = JSON.parse(message.body);
    console.log('New notification:', notification);
  });

  // Subscribe to this restaurant's order updates
  stompClient.subscribe(`/topic/restaurant/${restaurantId}/waiter/orders`, (message) => {
    const orderUpdate = JSON.parse(message.body);
    console.log('Order update:', orderUpdate);
  });
});
```

## Step 8: Complete the Order Flow

### Request Bill
```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/bill \
  -H "Authorization: Bearer WAITER_TOKEN" \
  -H "X-Waiter-Id: 1"
```

### Close Order (after payment)

There is no `/paid` endpoint — close the order once payment is settled.

```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/close \
  -H "Authorization: Bearer WAITER_TOKEN" \
  -H "X-Waiter-Id: 1"
```

### Free the Table (status change)
```bash
curl -X PATCH http://localhost:8080/api/v1/tables/1/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer WAITER_TOKEN" \
  -d '{"status": "AVAILABLE"}'
```

## Complete Workflow Summary

```
1. Waiter Login → Get Token
2. Get Available Tables → Find free table
3. Open Table → Assign to waiter
4. Create Order → Add items
5. [Optional] Add/Remove Items → Modify order
6. Submit to Kitchen → Kitchen starts preparing
7. [Wait for kitchen] → Order ready notification via WebSocket
8. Request Bill → Customer ready to pay
9. Mark as Paid → Complete payment
10. Close Table → Ready for next customer
```

## Testing with Postman

Import the Postman collection from `/postman/` directory for ready-to-use API requests.

## Common Operations

### Get All Available Tables
```bash
curl http://localhost:8080/api/v1/restaurants/1/tables/available \
  -H "Authorization: Bearer WAITER_TOKEN"
```

### Get Waiter's Active Orders
```bash
curl http://localhost:8080/api/v1/waiter/orders/waiter/1/ongoing \
  -H "Authorization: Bearer WAITER_TOKEN"
```

### Add Item to Existing Order
```bash
curl -X POST http://localhost:8080/api/v1/waiter/orders/100/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer WAITER_TOKEN" \
  -H "X-Waiter-Id: 1" \
  -d '[
    {
      "productId": 15,
      "quantity": 1,
      "notes": "Extra spicy"
    }
  ]'
```

### Get Order Event History
```bash
curl http://localhost:8080/api/v1/waiter/orders/100/history \
  -H "Authorization: Bearer WAITER_TOKEN"
```

## Troubleshooting

### Can't login as waiter
- Verify PIN code is correct
- Check waiter is active: `GET /api/v1/waiters/1`

### Can't open table
- Table might already be open
- Check table status: `GET /api/v1/tables/1`
- Free the table first if needed: `PATCH /api/v1/tables/1/status` with `{"status":"AVAILABLE"}`

### WebSocket not connecting
- Verify endpoint URL: `http://localhost:8080/ws-waiter`
- Send `Authorization: Bearer <token>` on CONNECT — WS auth defaults to `shadow` (logs, does not block); it blocks only when `app.websocket.auth.mode=enforce`
- Check CORS settings for your frontend origin
- Try SockJS fallback if WebSocket fails

### Order not appearing in kitchen
- Make sure to call `/waiter/orders/{id}/submit` after creating order
- Check WebSocket subscription to `/topic/restaurant/{restaurantId}/kitchen` (the bare `/topic/kitchen` is retired)
- Verify order status is not CANCELLED

## Next Steps

- Read full documentation: [WAITER_MODULE.md](./WAITER_MODULE.md)
- Explore WebSocket real-time features
- Implement table merging for large groups
- Set up role-based permissions
- Configure event listeners for custom business logic

## Support

Questions? Check:
- [Full Documentation](./WAITER_MODULE.md)
- [API Documentation](./FOOD_ORDERING_API.md)
- GitHub Issues
