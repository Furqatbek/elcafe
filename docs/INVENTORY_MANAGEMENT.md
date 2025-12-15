# Inventory Management System

**Version:** 1.0
**Last Updated:** 2025-12-15
**Author:** ElCafe Development Team

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Database Schema](#database-schema)
4. [Ingredient Management](#ingredient-management)
5. [Stock Operations](#stock-operations)
6. [Transaction Tracking](#transaction-tracking)
7. [Low Stock Alerts](#low-stock-alerts)
8. [Reorder Management](#reorder-management)
9. [API Reference](#api-reference)
10. [Frontend Integration](#frontend-integration)
11. [Best Practices](#best-practices)
12. [Troubleshooting](#troubleshooting)

---

## Overview

The ElCafe Inventory Management System provides comprehensive tracking and management of restaurant ingredients and supplies. The system helps restaurants:

- **Track Stock Levels**: Real-time visibility into current inventory
- **Prevent Stockouts**: Automatic low stock alerts and reorder suggestions
- **Audit Trail**: Complete history of all stock movements
- **Cost Control**: Track ingredient costs and supplier information
- **Waste Reduction**: Monitor expiration dates and minimize waste
- **Multi-Restaurant**: Separate inventory for each restaurant location

### Key Features

✅ **Real-time Stock Tracking** - Current stock levels updated instantly
✅ **Transaction History** - Complete audit trail of all movements
✅ **Low Stock Alerts** - Automatic notifications when stock is low
✅ **Reorder Management** - Track reorder points and preferred suppliers
✅ **Unit Management** - Support for various units (kg, L, pieces, etc.)
✅ **Category Organization** - Group ingredients by type
✅ **Expiration Tracking** - Monitor expiration dates
✅ **Multi-Restaurant Support** - Separate inventory per location
✅ **Stock Adjustments** - Manual corrections with reason tracking
✅ **Usage Tracking** - Monitor consumption patterns

---

## System Architecture

### Component Diagram

```
┌──────────────────────────────────────────────────────┐
│              Frontend (React)                        │
│  • Inventory.jsx - Main inventory page               │
│  • Stock modals - Add/Adjust stock                   │
│  • Transaction viewer                                │
└──────────────┬───────────────────────────────────────┘
               │
               ▼ REST API
┌──────────────────────────────────────────────────────┐
│         InventoryController                          │
│  • GET /api/v1/inventory/ingredients                 │
│  • POST /api/v1/inventory/ingredients                │
│  • PUT /api/v1/inventory/ingredients/{id}            │
│  • POST /api/v1/inventory/{id}/add-stock             │
│  • POST /api/v1/inventory/{id}/adjust-stock          │
│  • GET /api/v1/inventory/{id}/transactions           │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│           InventoryService                           │
│  • createIngredient()                                │
│  • updateIngredient()                                │
│  • addStock()                                        │
│  • adjustStock()                                     │
│  • getLowStockIngredients()                          │
│  • getReorderList()                                  │
└──────────────┬───────────────────────────────────────┘
               │
               ├────────────────┬─────────────────┐
               ▼                ▼                 ▼
      ┌────────────┐   ┌────────────┐   ┌────────────┐
      │ Ingredient │   │ Stock      │   │Restaurant  │
      │ Repository │   │Transaction │   │Repository  │
      │            │   │ Repository │   │            │
      └────────────┘   └────────────┘   └────────────┘
```

### Technology Stack

- **Backend**: Spring Boot 3.x, Spring Data JPA
- **Database**: PostgreSQL 14+
- **Frontend**: React, shadcn/ui components
- **Icons**: Lucide React
- **API Client**: Axios
- **State Management**: React hooks (useState, useEffect)

---

## Database Schema

### Entity Relationship Diagram

```
┌─────────────────┐
│  Restaurant     │
└────────┬────────┘
         │ 1
         │
         │ N
┌────────▼──────────────┐
│  Ingredient           │
│                       │
│ - name                │
│ - category            │
│ - unit                │
│ - currentStock        │
│ - minStockLevel       │
│ - reorderPoint        │
│ - reorderQuantity     │
│ - costPerUnit         │
│ - supplier            │
│ - expirationDate      │
└──────┬────────────────┘
       │ 1
       │
       │ N
┌──────▼────────────────┐
│ StockTransaction      │
│                       │
│ - transactionType     │
│ - quantity            │
│ - stockBefore         │
│ - stockAfter          │
│ - notes               │
│ - performedBy         │
│ - transactionDate     │
└───────────────────────┘
```

### Table: `ingredients`

Stores all ingredient and supply items for each restaurant.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| restaurant_id | BIGINT | FK to restaurants |
| name | VARCHAR(100) | Ingredient name |
| category | VARCHAR(50) | MEAT, DAIRY, VEGETABLE, etc. |
| unit | VARCHAR(20) | kg, L, pieces, boxes, etc. |
| current_stock | DECIMAL(10,2) | Current quantity in stock |
| min_stock_level | DECIMAL(10,2) | Minimum acceptable stock level |
| reorder_point | DECIMAL(10,2) | Stock level to trigger reorder |
| reorder_quantity | DECIMAL(10,2) | Suggested reorder amount |
| cost_per_unit | DECIMAL(10,2) | Cost per unit in local currency |
| supplier | VARCHAR(100) | Preferred supplier name |
| supplier_contact | VARCHAR(100) | Supplier phone/email |
| expiration_date | DATE | Expiration date (if applicable) |
| notes | TEXT | Additional notes |
| is_active | BOOLEAN | Active status |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |

**Categories:**
- `MEAT` - Meats and poultry
- `DAIRY` - Milk, cheese, butter, etc.
- `VEGETABLE` - Fresh vegetables
- `FRUIT` - Fresh fruits
- `GRAIN` - Rice, pasta, flour, etc.
- `SPICE` - Spices and seasonings
- `BEVERAGE` - Drinks and liquids
- `SAUCE` - Sauces and condiments
- `OTHER` - Miscellaneous items

**Units:**
- `kg` - Kilograms
- `g` - Grams
- `L` - Liters
- `mL` - Milliliters
- `pieces` - Individual pieces
- `boxes` - Boxes or packages
- `bottles` - Bottles
- `cans` - Cans
- `bags` - Bags

### Table: `stock_transactions`

Records all stock movements for audit trail and usage analysis.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| ingredient_id | BIGINT | FK to ingredients |
| transaction_type | VARCHAR(30) | PURCHASE, USAGE, ADJUSTMENT, WASTE, etc. |
| quantity | DECIMAL(10,2) | Quantity added/removed |
| stock_before | DECIMAL(10,2) | Stock level before transaction |
| stock_after | DECIMAL(10,2) | Stock level after transaction |
| cost_per_unit | DECIMAL(10,2) | Cost at time of transaction |
| total_cost | DECIMAL(10,2) | Total transaction cost |
| supplier | VARCHAR(100) | Supplier (for purchases) |
| notes | TEXT | Transaction notes/reason |
| performed_by | VARCHAR(100) | User who performed transaction |
| transaction_date | TIMESTAMP | When transaction occurred |
| created_at | TIMESTAMP | Record creation timestamp |

**Transaction Types:**
- `PURCHASE` - Stock purchased from supplier
- `USAGE` - Stock used in production
- `ADJUSTMENT` - Manual stock correction
- `WASTE` - Stock discarded (spoilage, damage)
- `TRANSFER` - Transferred to another location
- `RETURN` - Returned to supplier
- `INITIAL` - Initial stock entry

---

## Ingredient Management

### Creating an Ingredient

**API Endpoint:**
```http
POST /api/v1/inventory/ingredients
Content-Type: application/json

{
  "restaurantId": 1,
  "name": "Chicken Breast",
  "category": "MEAT",
  "unit": "kg",
  "currentStock": 50.0,
  "minStockLevel": 10.0,
  "reorderPoint": 15.0,
  "reorderQuantity": 30.0,
  "costPerUnit": 8.50,
  "supplier": "Fresh Poultry Co.",
  "supplierContact": "+1-555-1234",
  "expirationDate": "2025-12-25",
  "notes": "Store in refrigerator at 4°C"
}
```

**Response:**
```json
{
  "id": 42,
  "restaurantId": 1,
  "name": "Chicken Breast",
  "category": "MEAT",
  "unit": "kg",
  "currentStock": 50.0,
  "minStockLevel": 10.0,
  "reorderPoint": 15.0,
  "reorderQuantity": 30.0,
  "costPerUnit": 8.50,
  "supplier": "Fresh Poultry Co.",
  "supplierContact": "+1-555-1234",
  "expirationDate": "2025-12-25",
  "notes": "Store in refrigerator at 4°C",
  "isActive": true,
  "createdAt": "2025-12-15T10:30:00",
  "updatedAt": "2025-12-15T10:30:00"
}
```

### Updating an Ingredient

**API Endpoint:**
```http
PUT /api/v1/inventory/ingredients/{id}
Content-Type: application/json

{
  "name": "Chicken Breast (Organic)",
  "costPerUnit": 10.50,
  "supplier": "Organic Farms Ltd."
}
```

### Deleting an Ingredient

**API Endpoint:**
```http
DELETE /api/v1/inventory/ingredients/{id}
```

**Soft Delete:**
Instead of actual deletion, sets `is_active = false` to preserve historical data.

### Listing Ingredients

**API Endpoint:**
```http
GET /api/v1/inventory/ingredients?restaurantId=1&page=0&size=20
```

**Response:**
```json
{
  "content": [
    {
      "id": 42,
      "name": "Chicken Breast",
      "category": "MEAT",
      "currentStock": 50.0,
      "unit": "kg",
      "minStockLevel": 10.0,
      "status": "ADEQUATE"
    }
  ],
  "totalElements": 45,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

**Stock Status:**
- `CRITICAL` - Stock below minimum level
- `LOW` - Stock at or below reorder point
- `ADEQUATE` - Stock above reorder point

---

## Stock Operations

### Adding Stock (Purchase)

Used when receiving new inventory from suppliers.

**API Endpoint:**
```http
POST /api/v1/inventory/ingredients/{id}/add-stock
Content-Type: application/json

{
  "quantity": 25.0,
  "costPerUnit": 8.50,
  "supplier": "Fresh Poultry Co.",
  "notes": "Weekly delivery - Invoice #12345",
  "performedBy": "John Doe"
}
```

**Process:**
1. Validates quantity is positive
2. Calculates new stock level: `currentStock + quantity`
3. Creates `PURCHASE` transaction record
4. Updates ingredient's `current_stock`
5. Records stock before/after values

**Response:**
```json
{
  "success": true,
  "message": "Stock added successfully",
  "ingredient": {
    "id": 42,
    "name": "Chicken Breast",
    "currentStock": 75.0,
    "previousStock": 50.0
  },
  "transaction": {
    "id": 156,
    "transactionType": "PURCHASE",
    "quantity": 25.0,
    "stockBefore": 50.0,
    "stockAfter": 75.0,
    "totalCost": 212.50,
    "transactionDate": "2025-12-15T14:30:00"
  }
}
```

### Adjusting Stock

Used for manual corrections, waste, or discrepancies.

**API Endpoint:**
```http
POST /api/v1/inventory/ingredients/{id}/adjust-stock
Content-Type: application/json

{
  "newQuantity": 45.0,
  "notes": "Inventory count adjustment - found 5kg spoiled",
  "performedBy": "Jane Smith"
}
```

**Process:**
1. Calculates difference: `newQuantity - currentStock`
2. Creates `ADJUSTMENT` transaction (can be positive or negative)
3. Updates ingredient's `current_stock` to exact value
4. Records reason in notes

**Response:**
```json
{
  "success": true,
  "message": "Stock adjusted successfully",
  "ingredient": {
    "id": 42,
    "name": "Chicken Breast",
    "currentStock": 45.0,
    "previousStock": 50.0
  },
  "transaction": {
    "id": 157,
    "transactionType": "ADJUSTMENT",
    "quantity": -5.0,
    "stockBefore": 50.0,
    "stockAfter": 45.0,
    "notes": "Inventory count adjustment - found 5kg spoiled"
  }
}
```

### Recording Usage

Typically called automatically when orders are completed or recipes are prepared.

**API Endpoint:**
```http
POST /api/v1/inventory/ingredients/{id}/use-stock
Content-Type: application/json

{
  "quantity": 2.5,
  "notes": "Used in Order #3421 - Grilled Chicken",
  "performedBy": "Kitchen Staff"
}
```

**Process:**
1. Validates sufficient stock available
2. Deducts quantity from current stock
3. Creates `USAGE` transaction
4. Checks if stock falls below reorder point

### Recording Waste

**API Endpoint:**
```http
POST /api/v1/inventory/ingredients/{id}/record-waste
Content-Type: application/json

{
  "quantity": 3.0,
  "reason": "Expired on 2025-12-14",
  "performedBy": "Manager"
}
```

**Process:**
1. Deducts quantity from stock
2. Creates `WASTE` transaction
3. Tracks waste patterns for analysis

---

## Transaction Tracking

### Viewing Transaction History

**API Endpoint:**
```http
GET /api/v1/inventory/ingredients/{id}/transactions?page=0&size=50
```

**Response:**
```json
{
  "content": [
    {
      "id": 157,
      "transactionType": "ADJUSTMENT",
      "quantity": -5.0,
      "stockBefore": 50.0,
      "stockAfter": 45.0,
      "notes": "Inventory count adjustment",
      "performedBy": "Jane Smith",
      "transactionDate": "2025-12-15T16:00:00"
    },
    {
      "id": 156,
      "transactionType": "PURCHASE",
      "quantity": 25.0,
      "stockBefore": 50.0,
      "stockAfter": 75.0,
      "supplier": "Fresh Poultry Co.",
      "totalCost": 212.50,
      "performedBy": "John Doe",
      "transactionDate": "2025-12-15T14:30:00"
    }
  ],
  "totalElements": 28,
  "totalPages": 1,
  "size": 50,
  "number": 0
}
```

### Usage Analysis

Get usage patterns for forecasting:

```sql
SELECT
    DATE(transaction_date) AS usage_date,
    SUM(ABS(quantity)) AS total_used,
    COUNT(*) AS transaction_count
FROM stock_transactions
WHERE ingredient_id = 42
  AND transaction_type = 'USAGE'
  AND transaction_date >= NOW() - INTERVAL '30 days'
GROUP BY DATE(transaction_date)
ORDER BY usage_date DESC;
```

### Cost Tracking

Calculate total inventory value:

```sql
SELECT
    i.category,
    SUM(i.current_stock * i.cost_per_unit) AS total_value,
    COUNT(*) AS item_count
FROM ingredients i
WHERE i.restaurant_id = 1
  AND i.is_active = true
GROUP BY i.category
ORDER BY total_value DESC;
```

---

## Low Stock Alerts

### Getting Low Stock Items

**API Endpoint:**
```http
GET /api/v1/inventory/ingredients/low-stock?restaurantId=1
```

**Response:**
```json
{
  "items": [
    {
      "id": 42,
      "name": "Chicken Breast",
      "currentStock": 8.0,
      "minStockLevel": 10.0,
      "reorderPoint": 15.0,
      "unit": "kg",
      "status": "CRITICAL",
      "daysUntilStockout": 2
    },
    {
      "id": 43,
      "name": "Olive Oil",
      "currentStock": 12.0,
      "minStockLevel": 5.0,
      "reorderPoint": 10.0,
      "unit": "L",
      "status": "LOW",
      "daysUntilStockout": 5
    }
  ],
  "criticalCount": 1,
  "lowCount": 1
}
```

### Automatic Notifications

Configure automated alerts when stock is low:

```java
@Scheduled(cron = "0 0 8 * * *") // Daily at 8 AM
public void sendLowStockAlerts() {
    List<Ingredient> lowStock = inventoryService.getLowStockIngredients();

    if (!lowStock.isEmpty()) {
        String message = buildLowStockMessage(lowStock);
        notificationService.sendEmail(
            "manager@elcafe.com",
            "Low Stock Alert",
            message
        );

        // Also send SMS for critical items
        List<Ingredient> critical = lowStock.stream()
            .filter(i -> i.getCurrentStock().compareTo(i.getMinStockLevel()) < 0)
            .toList();

        if (!critical.isEmpty()) {
            notificationService.sendSMS(
                "+1-555-9999",
                "CRITICAL: " + critical.size() + " items below minimum stock"
            );
        }
    }
}
```

---

## Reorder Management

### Getting Reorder List

**API Endpoint:**
```http
GET /api/v1/inventory/ingredients/reorder?restaurantId=1
```

**Response:**
```json
{
  "items": [
    {
      "id": 42,
      "name": "Chicken Breast",
      "currentStock": 8.0,
      "reorderPoint": 15.0,
      "reorderQuantity": 30.0,
      "unit": "kg",
      "supplier": "Fresh Poultry Co.",
      "supplierContact": "+1-555-1234",
      "costPerUnit": 8.50,
      "estimatedCost": 255.00,
      "priority": "HIGH"
    }
  ],
  "totalEstimatedCost": 1250.00,
  "itemCount": 8
}
```

### Generating Purchase Orders

```java
public PurchaseOrder generatePurchaseOrder(Long restaurantId) {
    List<Ingredient> reorderList = inventoryService.getReorderList(restaurantId);

    PurchaseOrder po = new PurchaseOrder();
    po.setRestaurantId(restaurantId);
    po.setOrderDate(LocalDateTime.now());

    for (Ingredient ingredient : reorderList) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setIngredientId(ingredient.getId());
        item.setQuantity(ingredient.getReorderQuantity());
        item.setCostPerUnit(ingredient.getCostPerUnit());
        item.setSupplier(ingredient.getSupplier());

        po.addItem(item);
    }

    return purchaseOrderRepository.save(po);
}
```

---

## API Reference

### Complete Endpoint List

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/inventory/ingredients` | List all ingredients |
| GET | `/api/v1/inventory/ingredients/{id}` | Get ingredient details |
| POST | `/api/v1/inventory/ingredients` | Create new ingredient |
| PUT | `/api/v1/inventory/ingredients/{id}` | Update ingredient |
| DELETE | `/api/v1/inventory/ingredients/{id}` | Delete ingredient |
| POST | `/api/v1/inventory/ingredients/{id}/add-stock` | Add stock (purchase) |
| POST | `/api/v1/inventory/ingredients/{id}/adjust-stock` | Adjust stock level |
| POST | `/api/v1/inventory/ingredients/{id}/use-stock` | Record usage |
| POST | `/api/v1/inventory/ingredients/{id}/record-waste` | Record waste |
| GET | `/api/v1/inventory/ingredients/{id}/transactions` | Get transaction history |
| GET | `/api/v1/inventory/ingredients/low-stock` | Get low stock items |
| GET | `/api/v1/inventory/ingredients/reorder` | Get reorder list |
| GET | `/api/v1/inventory/ingredients/expiring` | Get expiring items |
| GET | `/api/v1/inventory/categories` | List all categories |
| GET | `/api/v1/inventory/reports/usage` | Usage report |
| GET | `/api/v1/inventory/reports/waste` | Waste report |

### Query Parameters

**Pagination:**
- `page` - Page number (0-indexed)
- `size` - Items per page (default: 20)

**Filtering:**
- `restaurantId` - Filter by restaurant
- `category` - Filter by category
- `isActive` - Filter active/inactive (default: true)
- `search` - Search by name

**Sorting:**
- `sort` - Sort field and direction (e.g., `name,asc` or `currentStock,desc`)

---

## Frontend Integration

### Inventory Page Component

The main inventory management interface is in `/frontend/src/pages/Inventory.jsx`.

**Key Features:**
1. Restaurant selector
2. Ingredient list with current stock
3. Stock status indicators (Critical/Low/Adequate)
4. Action buttons (Add Stock, Adjust, View History, Edit, Delete)
5. Add/Adjust stock modals
6. Transaction history modal

### Stock Management Modal

```javascript
const handleStockAction = (ingredient, action) => {
  setSelectedIngredient(ingredient);
  setStockAction(action); // 'add' or 'adjust'
  setStockFormData({
    quantity: '',
    newQuantity: action === 'adjust' ? ingredient.currentStock.toString() : '',
    notes: '',
    performedBy: 'Admin',
  });
  setStockModalOpen(true);
};

const handleStockSubmit = async () => {
  try {
    if (stockAction === 'add') {
      await inventoryAPI.addStock(selectedIngredient.id, {
        quantity: parseFloat(stockFormData.quantity),
        notes: stockFormData.notes,
        performedBy: stockFormData.performedBy,
      });
    } else {
      await inventoryAPI.adjustStock(selectedIngredient.id, {
        newQuantity: parseFloat(stockFormData.newQuantity),
        notes: stockFormData.notes,
        performedBy: stockFormData.performedBy,
      });
    }

    setStockModalOpen(false);
    loadIngredients(); // Refresh list
    toast.success('Stock updated successfully');
  } catch (error) {
    toast.error('Failed to update stock');
  }
};
```

### Stock Status Indicator

```javascript
const getStockStatus = (ingredient) => {
  if (ingredient.currentStock < ingredient.minStockLevel) {
    return { status: 'CRITICAL', color: 'red', icon: AlertTriangle };
  } else if (ingredient.currentStock <= ingredient.reorderPoint) {
    return { status: 'LOW', color: 'orange', icon: AlertCircle };
  } else {
    return { status: 'ADEQUATE', color: 'green', icon: CheckCircle };
  }
};

// Render
<Badge variant={status.color}>
  <status.icon className="h-3 w-3 mr-1" />
  {status.status}
</Badge>
```

### Transaction History Viewer

```javascript
const handleViewTransactions = async (ingredient) => {
  setSelectedIngredient(ingredient);
  try {
    const response = await inventoryAPI.getTransactions(ingredient.id, {
      page: 0,
      size: 50
    });
    setTransactions(response.data.data?.content || []);
    setTransactionModalOpen(true);
  } catch (error) {
    toast.error('Failed to load transaction history');
  }
};

// Modal content
<Table>
  <TableHeader>
    <TableRow>
      <TableHead>Date</TableHead>
      <TableHead>Type</TableHead>
      <TableHead>Quantity</TableHead>
      <TableHead>Stock After</TableHead>
      <TableHead>Performed By</TableHead>
      <TableHead>Notes</TableHead>
    </TableRow>
  </TableHeader>
  <TableBody>
    {transactions.map(transaction => (
      <TableRow key={transaction.id}>
        <TableCell>{formatDate(transaction.transactionDate)}</TableCell>
        <TableCell>
          <Badge variant={getTransactionColor(transaction.transactionType)}>
            {transaction.transactionType}
          </Badge>
        </TableCell>
        <TableCell className={transaction.quantity < 0 ? 'text-red-600' : 'text-green-600'}>
          {transaction.quantity > 0 ? '+' : ''}{transaction.quantity}
        </TableCell>
        <TableCell>{transaction.stockAfter}</TableCell>
        <TableCell>{transaction.performedBy}</TableCell>
        <TableCell>{transaction.notes}</TableCell>
      </TableRow>
    ))}
  </TableBody>
</Table>
```

---

## Best Practices

### 1. Always Record Reasons

```java
// GOOD
adjustStock(ingredientId, newQuantity, "Physical inventory count - found discrepancy", "Manager");

// BAD
adjustStock(ingredientId, newQuantity, "", ""); // No context for audit
```

### 2. Validate Before Stock Operations

```java
// GOOD
public void useStock(Long ingredientId, BigDecimal quantity) {
    Ingredient ingredient = ingredientRepository.findById(ingredientId);

    if (ingredient.getCurrentStock().compareTo(quantity) < 0) {
        throw new InsufficientStockException(
            "Not enough stock. Available: " + ingredient.getCurrentStock()
        );
    }

    ingredient.setCurrentStock(
        ingredient.getCurrentStock().subtract(quantity)
    );
}

// BAD
ingredient.setCurrentStock(
    ingredient.getCurrentStock().subtract(quantity)
); // Could go negative
```

### 3. Set Appropriate Reorder Points

```
Reorder Point = (Daily Usage × Lead Time) + Safety Stock

Example:
- Daily Usage: 5 kg
- Supplier Lead Time: 3 days
- Safety Stock: 5 kg
- Reorder Point = (5 × 3) + 5 = 20 kg
```

### 4. Regular Stock Audits

```java
@Scheduled(cron = "0 0 0 1 * *") // First day of each month
public void performMonthlyAudit() {
    List<Ingredient> allIngredients = ingredientRepository.findAll();

    for (Ingredient ingredient : allIngredients) {
        // Prompt physical count
        auditService.createAuditTask(ingredient);
    }
}
```

### 5. Track Waste Patterns

```sql
-- Identify high-waste ingredients
SELECT
    i.name,
    i.category,
    SUM(ABS(st.quantity)) AS total_waste,
    COUNT(*) AS waste_events,
    SUM(ABS(st.quantity) * st.cost_per_unit) AS total_cost
FROM stock_transactions st
JOIN ingredients i ON st.ingredient_id = i.id
WHERE st.transaction_type = 'WASTE'
  AND st.transaction_date >= NOW() - INTERVAL '90 days'
GROUP BY i.id, i.name, i.category
ORDER BY total_cost DESC
LIMIT 10;
```

---

## Troubleshooting

### Issue: Stock Count Mismatch

**Symptoms:**
- System stock doesn't match physical count
- Unexplained stock differences

**Solution:**
```java
// Perform adjustment with detailed notes
adjustStock(
    ingredientId,
    physicalCount,
    "Monthly inventory audit - system showed " + systemCount +
    " but physical count was " + physicalCount,
    "Inventory Manager"
);
```

### Issue: Negative Stock Levels

**Symptoms:**
- Stock goes below zero
- "Insufficient stock" errors

**Root Cause:**
- Usage recorded without stock validation
- Concurrent transactions

**Solution:**
```java
// Use pessimistic locking
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Ingredient i WHERE i.id = :id")
Ingredient findByIdWithLock(@Param("id") Long id);

// Service method
@Transactional
public void useStock(Long ingredientId, BigDecimal quantity) {
    Ingredient ingredient = ingredientRepository.findByIdWithLock(ingredientId);

    if (ingredient.getCurrentStock().compareTo(quantity) < 0) {
        throw new InsufficientStockException();
    }

    ingredient.setCurrentStock(
        ingredient.getCurrentStock().subtract(quantity)
    );
}
```

### Issue: Missing Transaction History

**Symptoms:**
- Stock changed but no transaction record
- Audit trail incomplete

**Root Cause:**
- Direct stock updates without creating transactions
- Transaction creation failed silently

**Solution:**
```java
// Always use service methods that create transactions
// GOOD
inventoryService.addStock(ingredientId, quantity, notes);

// BAD
ingredient.setCurrentStock(newValue);
ingredientRepository.save(ingredient); // No transaction created
```

### Issue: Expiration Alerts Not Working

**Symptoms:**
- Expired items not flagged
- No notifications sent

**Solution:**
```java
@Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
public void checkExpiringItems() {
    LocalDate today = LocalDate.now();
    LocalDate weekFromNow = today.plusDays(7);

    List<Ingredient> expiring = ingredientRepository
        .findByExpirationDateBetween(today, weekFromNow);

    if (!expiring.isEmpty()) {
        notificationService.sendExpirationAlert(expiring);
    }
}
```

---

## Reports and Analytics

### Stock Value Report

```java
public InventoryValueReport generateValueReport(Long restaurantId) {
    List<Ingredient> ingredients = ingredientRepository
        .findByRestaurantIdAndIsActiveTrue(restaurantId);

    BigDecimal totalValue = ingredients.stream()
        .map(i -> i.getCurrentStock().multiply(i.getCostPerUnit()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, BigDecimal> byCategory = ingredients.stream()
        .collect(Collectors.groupingBy(
            Ingredient::getCategory,
            Collectors.reducing(
                BigDecimal.ZERO,
                i -> i.getCurrentStock().multiply(i.getCostPerUnit()),
                BigDecimal::add
            )
        ));

    return new InventoryValueReport(totalValue, byCategory);
}
```

### Usage Trends

```java
public UsageTrendReport getUsageTrends(Long ingredientId, int days) {
    LocalDateTime startDate = LocalDateTime.now().minusDays(days);

    List<StockTransaction> transactions = transactionRepository
        .findByIngredientIdAndTransactionTypeAndTransactionDateAfter(
            ingredientId,
            TransactionType.USAGE,
            startDate
        );

    Map<LocalDate, BigDecimal> dailyUsage = transactions.stream()
        .collect(Collectors.groupingBy(
            t -> t.getTransactionDate().toLocalDate(),
            Collectors.reducing(
                BigDecimal.ZERO,
                t -> t.getQuantity().abs(),
                BigDecimal::add
            )
        ));

    BigDecimal averageDaily = dailyUsage.values().stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

    return new UsageTrendReport(dailyUsage, averageDaily);
}
```

---

## Integration Examples

### Auto-Deduct on Order Completion

```java
@Component
public class OrderInventoryListener {

    @Autowired
    private InventoryService inventoryService;

    @EventListener
    public void onOrderCompleted(OrderCompletedEvent event) {
        Order order = event.getOrder();

        for (OrderItem item : order.getItems()) {
            MenuItem menuItem = item.getMenuItem();

            // Get recipe and deduct ingredients
            for (RecipeIngredient recipeIng : menuItem.getRecipe()) {
                BigDecimal quantityNeeded = recipeIng.getQuantity()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));

                inventoryService.useStock(
                    recipeIng.getIngredientId(),
                    quantityNeeded,
                    "Order #" + order.getId() + " - " + menuItem.getName(),
                    "System"
                );
            }
        }
    }
}
```

---

## Future Enhancements

### Planned Features

1. **Barcode Scanning** - Scan barcodes for quick stock updates
2. **Supplier Integration** - Automated purchase orders via API
3. **Predictive Reordering** - ML-based demand forecasting
4. **Batch Tracking** - Track specific batches for recalls
5. **Mobile App** - Stock management on mobile devices
6. **Multi-Location Transfers** - Transfer stock between restaurants
7. **Cost Variance Analysis** - Track supplier price changes
8. **Waste Reduction Tips** - AI-powered suggestions

---

## Support

For questions or issues:

- **Documentation**: `/docs/API_REFERENCE.md`
- **Database Schema**: `/src/main/resources/db/migration/V*__inventory*.sql`
- **Source Code**: `/src/main/java/com/elcafe/modules/inventory/`
- **Frontend**: `/frontend/src/pages/Inventory.jsx`

**Version:** 1.0
**Last Updated:** 2025-12-15
