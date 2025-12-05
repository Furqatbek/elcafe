# El Cafe - API Integration Guide

## 🚀 Quick Start

### Complete Food Ordering System

The El Cafe platform provides a comprehensive API for food ordering, kitchen management, and delivery tracking.

```
┌──────────────┐
│   Customer   │──► Places Order ──► Restaurant Accepts ──► Kitchen Prepares
└──────────────┘                                                    │
                                                                    ▼
                  Delivered ◄── Courier Delivers ◄── Courier Accepts ◄── Food Ready
```

---

## 📚 Documentation

**Complete API Documentation:** [FOOD_ORDERING_API.md](./docs/FOOD_ORDERING_API.md)

**Quick Links:**
- [Order Flow](#order-flow)
- [Consumer API](#consumer-api-public)
- [Kitchen API](#kitchen-api)
- [Courier API](#courier-api)
- [Testing Examples](#testing-examples)

---

## 🎯 Key Features

### ✅ Implemented

- **Consumer Order Placement** - Public API for website/mobile apps
- **Order Tracking** - Real-time order status updates
- **Kitchen Management** - Food preparation workflow
- **Courier Assignment** - Delivery management system
- **Notification System** - Multi-channel notifications
- **Status History** - Complete audit trail
- **Role-Based Access** - Secure endpoints

---

## 🔌 API Endpoints Overview

### Consumer API (Public - No Auth)

```http
POST   /api/v1/consumer/orders                    # Place new order
GET    /api/v1/consumer/orders/{orderNumber}      # Track order
POST   /api/v1/consumer/orders/{orderNumber}/cancel # Cancel order
```

### Kitchen API (Auth Required)

```http
GET    /api/v1/kitchen/orders/active              # Get active orders
GET    /api/v1/kitchen/orders/ready               # Get ready orders
POST   /api/v1/kitchen/orders/{id}/start          # Start preparation
POST   /api/v1/kitchen/orders/{id}/ready          # Mark as ready
POST   /api/v1/kitchen/orders/{id}/picked-up      # Mark picked up
PATCH  /api/v1/kitchen/orders/{id}/priority       # Update priority
```

### Courier API (Auth Required)

```http
GET    /api/v1/courier/orders/available           # View available orders
GET    /api/v1/courier/orders/my-orders           # Get assigned orders
POST   /api/v1/courier/orders/{id}/accept         # Accept order
POST   /api/v1/courier/orders/{id}/decline        # Decline order
POST   /api/v1/courier/orders/assign              # Manual assignment (Admin)
POST   /api/v1/courier/orders/{id}/start-delivery # Start delivery
POST   /api/v1/courier/orders/{id}/complete       # Complete delivery
```

---

## 🔄 Order Flow

### Order Status Lifecycle

```
NEW
  ↓ Restaurant accepts
ACCEPTED
  ↓ Kitchen starts preparation
PREPARING
  ↓ Food ready
READY
  ↓ Courier accepts
COURIER_ASSIGNED
  ↓ Courier picks up
ON_DELIVERY
  ↓ Delivered to customer
DELIVERED
```

### Notifications at Each Step

| Status | Customer | Restaurant | Kitchen | Courier | Admin |
|--------|----------|------------|---------|---------|-------|
| NEW | ✅ | ✅ | ✅ | ⚪ | ✅ |
| ACCEPTED | ✅ | ⚪ | ✅ | ⚪ | ✅ |
| PREPARING | ✅ | ⚪ | ⚪ | ⚪ | ⚪ |
| READY | ✅ | ⚪ | ⚪ | ✅ | ✅ |
| COURIER_ASSIGNED | ✅ | ✅ | ✅ | ✅ | ✅ |
| ON_DELIVERY | ✅ | ⚪ | ⚪ | ⚪ | ✅ |
| DELIVERED | ✅ | ✅ | ⚪ | ⚪ | ✅ |

---

## 🚀 Getting Started

### 1. Place Your First Order

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
        "productId": 1,
        "quantity": 2
      }
    ],
    "deliveryInfo": {
      "address": "123 Main Street",
      "city": "Tashkent",
      "zipCode": "100000"
    },
    "paymentMethod": "CASH"
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "Order placed successfully",
  "data": {
    "orderNumber": "ORD-A7B3C4D5",
    "status": "NEW",
    "total": 59500
  }
}
```

### 2. Track the Order

```bash
curl http://localhost:8080/api/v1/consumer/orders/ORD-A7B3C4D5
```

### 3. Kitchen Preparation

```bash
# Start preparation
curl -X POST "http://localhost:8080/api/v1/kitchen/orders/1/start?chefName=Chef%20Mario" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Mark as ready
curl -X POST http://localhost:8080/api/v1/kitchen/orders/1/ready \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 4. Courier Delivery

```bash
# Courier accepts order
curl -X POST "http://localhost:8080/api/v1/courier/orders/1/accept?courierId=123" \
  -H "Authorization: Bearer COURIER_TOKEN"

# Start delivery
curl -X POST "http://localhost:8080/api/v1/courier/orders/1/start-delivery?courierId=123" \
  -H "Authorization: Bearer COURIER_TOKEN"

# Complete delivery
curl -X POST "http://localhost:8080/api/v1/courier/orders/1/complete?courierId=123" \
  -H "Authorization: Bearer COURIER_TOKEN"
```

---

## 🎨 Frontend Integration Examples

### React Example - Place Order

```javascript
const placeOrder = async (orderData) => {
  try {
    const response = await fetch('http://localhost:8080/api/v1/consumer/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(orderData)
    });

    const result = await response.json();

    if (result.success) {
      // Save order number for tracking
      localStorage.setItem('currentOrder', result.data.orderNumber);

      // Redirect to tracking page
      window.location.href = `/track/${result.data.orderNumber}`;
    }
  } catch (error) {
    console.error('Order placement failed:', error);
  }
};
```

### React Example - Track Order

```javascript
const TrackOrder = ({ orderNumber }) => {
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchOrder = async () => {
      const response = await fetch(
        `http://localhost:8080/api/v1/consumer/orders/${orderNumber}`
      );
      const result = await response.json();
      setOrder(result.data);
      setLoading(false);
    };

    fetchOrder();

    // Poll for updates every 10 seconds
    const interval = setInterval(fetchOrder, 10000);
    return () => clearInterval(interval);
  }, [orderNumber]);

  if (loading) return <div>Loading...</div>;

  return (
    <div>
      <h2>Order: {order.orderNumber}</h2>
      <OrderStatusTimeline status={order.status} />

      {order.status === 'COURIER_ASSIGNED' && (
        <CourierInfo
          name={order.deliveryInfo.courierName}
          phone={order.deliveryInfo.courierPhone}
        />
      )}

      {order.estimatedDeliveryTime && (
        <p>Estimated delivery: {formatTime(order.estimatedDeliveryTime)}</p>
      )}
    </div>
  );
};
```

### Kitchen Dashboard Example

```javascript
const KitchenDashboard = () => {
  const [activeOrders, setActiveOrders] = useState([]);

  const startPreparation = async (kitchenOrderId) => {
    const chefName = prompt('Enter chef name:');

    await fetch(
      `http://localhost:8080/api/v1/kitchen/orders/${kitchenOrderId}/start?chefName=${chefName}`,
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      }
    );

    refreshOrders();
  };

  const markReady = async (kitchenOrderId) => {
    await fetch(
      `http://localhost:8080/api/v1/kitchen/orders/${kitchenOrderId}/ready`,
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      }
    );

    refreshOrders();
  };

  return (
    <div className="kitchen-dashboard">
      <h1>Active Orders</h1>
      {activeOrders.map(order => (
        <OrderCard
          key={order.id}
          order={order}
          onStart={() => startPreparation(order.id)}
          onReady={() => markReady(order.id)}
        />
      ))}
    </div>
  );
};
```

---

## 🔐 Authentication

### Getting a Token

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "chef@elcafe.com",
    "password": "password123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4=",
  "expiresIn": 3600
}
```

### Using the Token

```bash
curl http://localhost:8080/api/v1/kitchen/orders/active \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

## 📱 Testing with Postman

### Import Collection

1. Import the Postman collection from `/docs/postman/`
2. Set environment variables:
   - `base_url`: http://localhost:8080/api/v1
   - `auth_token`: Your JWT token
   - `order_number`: An order number for testing

### Test Scenarios

**Scenario 1: Complete Order Flow**
1. Place Order (Consumer) → Save order number
2. Accept Order (Restaurant)
3. Start Preparation (Kitchen)
4. Mark Ready (Kitchen)
5. Accept Order (Courier)
6. Start Delivery (Courier)
7. Complete Delivery (Courier)
8. Verify Final Status

**Scenario 2: Order Cancellation**
1. Place Order
2. Cancel Order (Customer)
3. Verify status is CANCELLED

**Scenario 3: Courier Decline**
1. Place Order
2. Accept & Prepare
3. Mark Ready
4. Courier Declines
5. Verify order still available

---

## 🐛 Common Issues & Solutions

### Issue: "Restaurant not found"
**Solution:** Ensure the restaurantId exists and the restaurant is active.

```sql
SELECT id, name, active FROM restaurants WHERE active = true;
```

### Issue: "Product not available"
**Solution:** Check product availability in the menu.

```sql
SELECT id, name, available FROM products WHERE restaurant_id = 1;
```

### Issue: "Cannot cancel order in current status"
**Solution:** Orders can only be cancelled if status is NEW or ACCEPTED.

### Issue: "This order is not assigned to you"
**Solution:** Courier can only act on orders assigned to them. Check the courierId.

---

## 📊 Monitoring & Logs

### Check Order Status

```sql
SELECT
  o.order_number,
  o.status,
  o.created_at,
  c.first_name || ' ' || c.last_name as customer,
  r.name as restaurant
FROM orders o
JOIN customers c ON o.customer_id = c.id
JOIN restaurants r ON o.restaurant_id = r.id
WHERE o.created_at > NOW() - INTERVAL '1 day'
ORDER BY o.created_at DESC;
```

### Check Kitchen Orders

```sql
SELECT
  ko.id,
  o.order_number,
  ko.status,
  ko.assigned_chef,
  ko.created_at
FROM kitchen_orders ko
JOIN orders o ON ko.order_id = o.id
WHERE ko.status IN ('PENDING', 'PREPARING')
ORDER BY ko.priority DESC, ko.created_at ASC;
```

### Check Courier Assignments

```sql
SELECT
  o.order_number,
  o.status,
  di.courier_name,
  di.courier_phone,
  di.pickup_time,
  di.delivery_time
FROM orders o
JOIN delivery_info di ON o.id = di.order_id
WHERE di.courier_id IS NOT NULL
ORDER BY o.created_at DESC;
```

---

## 🎯 Performance Tips

### For High Volume

1. **Use Connection Pooling** - Configure HikariCP properly
2. **Enable Caching** - Cache restaurant menus
3. **Use Pagination** - Limit results in list endpoints
4. **Index Database** - Index on status, restaurantId, courierId
5. **Batch Notifications** - Group notifications when possible

### Database Indexes

```sql
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_restaurant ON orders(restaurant_id, status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_delivery_courier ON delivery_info(courier_id);
CREATE INDEX idx_kitchen_status ON kitchen_orders(status, priority, created_at);
```

---

## 🔗 Related Documentation

- **[Complete API Documentation](./docs/FOOD_ORDERING_API.md)** - Full API reference
- **[Menu Management API](./docs/MENU_API.md)** - Restaurant menu APIs
- **[Analytics API](./docs/ANALYTICS_API.md)** - Business analytics
- **[Authentication Guide](./docs/AUTH_API.md)** - User authentication

---

## 🤝 Contributing

When adding new features:

1. Follow the existing pattern
2. Add proper validation
3. Include notifications
4. Update status history
5. Add tests
6. Update documentation

---

## 📞 Support

**Development Server:** http://localhost:8080

**API Documentation:** http://localhost:8080/swagger-ui.html

**Health Check:** http://localhost:8080/actuator/health

---

**Last Updated:** November 25, 2025
**API Version:** v1.0.0
