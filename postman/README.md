# Postman API Collection

Complete Postman collection for the Restaurant Delivery Control Service API.

## 📦 Files

- **Restaurant_Delivery_API.postman_collection.json** - Main API collection with all endpoints
- **Local_Environment.postman_environment.json** - Local development environment configuration

## 🚀 Quick Start

### Import to Postman

1. Open Postman
2. Click **Import** button (top left)
3. Drag and drop both JSON files or select them using the file picker
4. The collection and environment will be imported

### Setup

1. **Select Environment**
   - Click the environment dropdown (top right)
   - Select "Local Environment"

2. **Start the Application**
   ```bash
   docker-compose up
   ```

3. **Login to Get Access Token**
   - Open the collection: `Authentication → Login`
   - Click **Send**
   - The access token will be automatically saved to the environment

## 📚 Collection Structure

### 1. Authentication
- **Login** - Authenticate as the bootstrap `SUPER_ADMIN` and get tokens (auto-saves tokens). This is the entry path.
- **Register** - Create a new user account. **Requires** an authenticated `SUPER_ADMIN` (`POST /api/v1/auth/register` is no longer open self-registration).
- **Refresh Token** - Get new access token
- **Forgot Password** - Request password reset
- **Reset Password** - Reset password with token

### 2. Restaurants
- **Create Restaurant** - Add new restaurant (Admin only)
- **Get All Restaurants** - List with pagination
- **Get Restaurant by ID** - Get details
- **Get Active Restaurants** - List active only
- **Get Accepting Orders** - List restaurants accepting orders
- **Update Restaurant** - Update details (Admin only)
- **Delete Restaurant** - Remove restaurant (Admin only)

### 3. Menu
- **Get Public Menu** - Public menu endpoint (no auth)
- **Get Categories** - Get restaurant categories

### 4. Orders
- **Create Order** - Place new order (auto-saves order_id)
- **Get Order by ID** - Get order details
- **Get Order by Number** - Get by order number
- **Get All Orders** - List with pagination
- **Get Restaurant Orders** - Orders for specific restaurant
- **Get Pending Orders** - List pending orders
- **Update Status** - Multiple requests for different statuses
  - Accept
  - Preparing
  - Ready
  - Delivered

### 5. Customers (CRM)
- **Create Customer** - Add new customer (auto-saves customer_id)
- **Get All Customers** - List with pagination
- **Get Customer by ID** - Get details
- **Get Order History** - Customer's order history
- **Update Customer** - Update information
- **Delete Customer** - Remove customer

### 6. Courier Integration
- **Delivery Status Webhook** - Receive courier updates

### 7. Health & Monitoring
- **Health Check** - Application health
- **Info** - Application info
- **Metrics** - Application metrics

## 🔐 Authentication

The collection uses **Bearer Token** authentication automatically.

### Auto-Token Management

The Login request has a **test script** that automatically:
1. Extracts the `accessToken` from the response
2. Saves it to the environment variable `access_token`
3. Saves the `refreshToken` to `refresh_token`

All authenticated requests automatically use `{{access_token}}` from the environment.

### Manual Token Usage

If needed, you can manually set the token:
1. Go to Environment (top right)
2. Find `access_token` variable
3. Paste your token in the **Current Value** column

## 🎯 Bootstrap Admin

No users are seeded. On first boot **against an empty database**, if both `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables are set, the platform creates a **single `SUPER_ADMIN`** account from them. It is inert on every subsequent boot (remove `ADMIN_PASSWORD` from the environment once the account exists). There are **no** `admin@elcafe.com` / `operator@elcafe.com` accounts.

| Role | Email | Password |
|------|-------|----------|
| SUPER_ADMIN | value of `ADMIN_EMAIL` | value of `ADMIN_PASSWORD` |

Log in with these credentials to get a token, then create any further users via the (SUPER_ADMIN-only) **Register** endpoint.

## 📝 Environment Variables

### Automatic Variables (Set by scripts)
- `access_token` - JWT access token (auto-set on login)
- `refresh_token` - JWT refresh token (auto-set on login)
- `order_id` - Last created order ID (auto-set on create)
- `order_number` - Last created order number (auto-set on create)
- `customer_id` - Last created customer ID (auto-set on create)

### Manual Variables
- `base_url` - API base URL (default: http://localhost:8080)
- `restaurant_id` - Default restaurant ID (default: 1)

## 🔄 Typical Workflow

### Complete Order Flow

1. **Login as Admin**
   ```
   Authentication → Login
   ```

2. **Create/Get Restaurant**
   ```
   Restaurants → Get Restaurant by ID (ID: 1)
   ```

3. **View Menu**
   ```
   Menu → Get Public Menu
   ```

4. **Create Customer**
   ```
   Customers → Create Customer
   ```

5. **Place Order**
   ```
   Orders → Create Order
   ```

6. **Update Order Status**
   ```
   Orders → Update Status - Accept
   Orders → Update Status - Preparing
   Orders → Update Status - Ready
   Orders → Update Status - Delivered
   ```

7. **Check Customer History**
   ```
   Customers → Get Customer Order History
   ```

## 🛠️ Tips

### Pagination
Most list endpoints support pagination:
```
?page=0&size=10
```

### Order Status Flow
```
NEW → ACCEPTED → PREPARING → READY →
COURIER_ASSIGNED → ON_DELIVERY → DELIVERED

Or at any point → CANCELLED
```

### Testing Webhooks
Use the **Courier → Delivery Status Webhook** to simulate courier updates.

### Token Expiry
- Access tokens expire after 1 hour
- Use **Refresh Token** request to get a new access token
- Refresh tokens expire after 24 hours

## 🌍 Multiple Environments

You can create additional environments for different stages:

### Production Environment
1. Duplicate "Local Environment"
2. Rename to "Production"
3. Update `base_url` to your production URL
4. Login again to get production tokens

### Staging Environment
1. Duplicate "Local Environment"
2. Rename to "Staging"
3. Update `base_url` to your staging URL
4. Login again to get staging tokens

## 📊 Pre-request Scripts

Some requests include pre-request scripts for dynamic data:
- **Login** - Auto-saves tokens to environment
- **Create Order** - Auto-saves order ID and number
- **Create Customer** - Auto-saves customer ID

## 🐛 Troubleshooting

### 401 Unauthorized
- Token expired - use **Refresh Token** request
- Not logged in - use **Login** request
- Check environment is selected (top right)

### 403 Forbidden
- Insufficient permissions
- Admin-only endpoints require an admin (or `SUPER_ADMIN`) account
- Login with correct user role

### 404 Not Found
- Check resource ID in URL
- Verify resource exists in database
- Check `base_url` is correct

### Connection Refused
- Application not running
- Run `docker-compose up`
- Check application is on port 8080

## 📖 API Documentation

For detailed API documentation, visit:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

## 🎓 Examples

### Create Restaurant with Full Data
```json
{
  "name": "New Restaurant",
  "description": "Best food in town",
  "address": "456 Oak Street",
  "city": "New York",
  "state": "NY",
  "active": true,
  "businessHours": [
    {
      "dayOfWeek": "MONDAY",
      "openTime": "09:00",
      "closeTime": "22:00",
      "closed": false
    }
  ],
  "deliveryZones": [
    {
      "name": "Downtown",
      "city": "New York",
      "deliveryFee": 5.00,
      "active": true
    }
  ]
}
```

### Create Order
```json
{
  "restaurant": { "id": 1 },
  "customer": { "id": 1 },
  "subtotal": 25.00,
  "deliveryFee": 5.00,
  "tax": 2.50,
  "total": 32.50,
  "deliveryInfo": {
    "address": "123 Main St",
    "city": "New York",
    "contactPhone": "+1234567890"
  }
}
```

## 📞 Support

For issues or questions:
- Check application logs: `docker-compose logs app`
- Review Swagger documentation
- Check README.md in project root

---

**Version**: 1.0.0
**Last Updated**: 2025-01-23
**Compatible with**: Restaurant Delivery Service API v1.0.0
