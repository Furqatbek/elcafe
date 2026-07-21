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
│  • pages/inventory/ - multi-page module              │
│    (InventoryIngredients, InventoryWaste,            │
│     InventoryValuation, ProductionBatches, ...)      │
│  • pages/InventoryAnalytics.jsx                      │
└──────────────┬───────────────────────────────────────┘
               │
               ▼ REST API
┌──────────────────────────────────────────────────────┐
│   InventoryIngredientController (+ siblings)         │
│  • GET  /api/v1/inventory/ingredients                │
│  • POST /api/v1/inventory/ingredients                │
│  • PUT  /api/v1/inventory/ingredients/{id}           │
│  • POST .../ingredients/{id}/add-stock               │
│  • POST .../ingredients/{id}/adjust-stock            │
│  • GET  .../ingredients/{id}/transactions            │
│  Siblings: Waste, Valuation, IngredientCategory,     │
│  InventoryBatch, ProductionBatch, StockCount,        │
│  Supplier, Recipe, POSuggestion controllers          │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│      InventoryService (+ related services)           │
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
  ┌─────────────────────┐ ┌──────────────────────┐ ┌────────────┐
  │ InventoryIngredient  │ │ InventoryTransaction  │ │ Restaurant │
  │ Repository           │ │ Repository            │ │ Repository │
  └─────────────────────┘ └──────────────────────┘ └────────────┘
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
┌────────▼───────────────────────┐
│  InventoryIngredient            │
│  (table: inventory_ingredients) │
│                                 │
│ - name                          │
│ - category (FK category_id →    │
│    IngredientCategory)          │
│ - unit                          │
│ - currentStock                  │
│ - minimumStock                  │
│ - reorderLevel                  │
│ - reorderQuantity               │
│ - costPerUnit                   │
│ - weightedAverageCost           │
│ - supplier / supplierId (FK)    │
│ - sku                           │
│ - active                        │
│ - trackExpiry / expiryAlertDays │
└──────┬──────────────────────────┘
       │ 1
       │
       │ N
┌──────▼──────────────────────────┐
│ InventoryTransaction            │
│  (table: inventory_transactions)│
│                                 │
│ - transactionType               │
│ - quantity                      │
│ - stockBefore                   │
│ - stockAfter                    │
│ - notes                         │
│ - performedBy                   │
│ - transactionDate               │
└─────────────────────────────────┘
```

### Table: `inventory_ingredients`

Stores all ingredient and supply items for each restaurant. (Entity class: `Ingredient`, mapped as JPA entity name `InventoryIngredient`.)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| restaurant_id | BIGINT | FK to restaurants |
| category_id | BIGINT | FK to `ingredient_categories` (nullable) |
| name | VARCHAR(200) | Ingredient name |
| description | TEXT | Description / notes |
| unit | VARCHAR(50) | kg, g, L, ml, pieces, etc. |
| current_stock | DECIMAL(10,3) | Current quantity in stock |
| minimum_stock | DECIMAL(10,3) | Minimum acceptable stock level |
| reorder_level | DECIMAL(10,3) | Stock level to trigger reorder |
| reorder_quantity | DECIMAL(10,2) | Suggested reorder amount |
| cost_per_unit | DECIMAL(10,2) | Cost per unit in local currency |
| weighted_average_cost | DECIMAL(15,4) | Weighted-average cost (recalculated on each purchase) |
| supplier | VARCHAR(100) | Supplier name (free text) |
| supplier_id | BIGINT | FK to inventory `suppliers` (nullable) |
| sku | VARCHAR(50) | Stock keeping unit |
| active | BOOLEAN | Active status |
| track_inventory | BOOLEAN | Whether stock is tracked |
| track_expiry | BOOLEAN | Whether batch expiry is tracked |
| default_shelf_life_days | INTEGER | Default shelf life for new batches |
| expiry_alert_days | INTEGER | Days before expiry to trigger alert |
| version | BIGINT | Optimistic-lock version |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |

> **Category:** `category` is a `@ManyToOne` to `IngredientCategory` (FK `category_id`), managed via the ingredient-categories API — not an inline `VARCHAR` enum.
> **Expiry:** there is no `expiration_date` column on the ingredient. Expiry is controlled by `track_expiry` / `default_shelf_life_days` / `expiry_alert_days` and tracked per batch (see `inventory_batches`).

**Categories:**

Ingredient categories are **not** a hard-coded enum. They are stored in their own table (`ingredient_categories`, entity `IngredientCategory`) and referenced from an ingredient via `category_id` (`@ManyToOne`). Categories are created and managed dynamically through the ingredient-categories API:

- `GET /api/v1/inventory/ingredient-categories` - list categories
- `POST /api/v1/inventory/ingredient-categories` - create a category
- `PUT /api/v1/inventory/ingredient-categories/{id}` - update a category
- `DELETE /api/v1/inventory/ingredient-categories/{id}` - delete a category

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

### Table: `inventory_transactions`

Records all stock movements for audit trail and usage analysis. (Entity: `InventoryTransaction`.)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| ingredient_id | BIGINT | FK to inventory_ingredients |
| transaction_type | VARCHAR(50) | PURCHASE, ORDER_DEDUCTION, ADJUSTMENT, WASTE, etc. |
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

**Transaction Types** (enum `TransactionType`):
- `PURCHASE` - Stock added from supplier
- `ORDER_DEDUCTION` - Stock deducted for a customer order
- `ADJUSTMENT` - Manual stock adjustment
- `WASTE` - Stock wasted/spoiled
- `RETURN` - Stock returned
- `RESTOCK` - Stock replenished
- `INITIAL_STOCK` - Initial stock entry
- `TRANSFER` - Stock transferred between locations
- `PRODUCTION_INPUT` - Raw ingredients consumed for a production batch
- `PRODUCTION_OUTPUT` - Prepared item produced from a production batch

---

## Ingredient Management

### Creating an Ingredient

**API Endpoint:**
```http
POST /api/v1/inventory/ingredients
Content-Type: application/json

{
  "restaurantId": 1,
  "categoryId": 7,
  "name": "Chicken Breast",
  "description": "Store in refrigerator at 4°C",
  "unit": "kg",
  "currentStock": 50.0,
  "minimumStock": 10.0,
  "reorderLevel": 15.0,
  "reorderQuantity": 30.0,
  "costPerUnit": 8.50,
  "supplier": "Fresh Poultry Co.",
  "supplierId": 3,
  "sku": "CHK-BRST-01",
  "active": true,
  "trackInventory": true,
  "trackExpiry": true,
  "defaultShelfLifeDays": 5,
  "expiryAlertDays": 2
}
```

**Response:**

Returns `ApiResponse<IngredientResponse>`.

```json
{
  "success": true,
  "message": "Ingredient created successfully",
  "data": {
    "id": 42,
    "restaurantId": 1,
    "restaurantName": "Downtown Branch",
    "categoryId": 7,
    "categoryName": "Meat",
    "name": "Chicken Breast",
    "description": "Store in refrigerator at 4°C",
    "unit": "kg",
    "currentStock": 50.0,
    "minimumStock": 10.0,
    "reorderLevel": 15.0,
    "reorderQuantity": 30.0,
    "costPerUnit": 8.50,
    "supplier": "Fresh Poultry Co.",
    "supplierId": 3,
    "supplierName": "Fresh Poultry Co.",
    "sku": "CHK-BRST-01",
    "active": true,
    "trackInventory": true,
    "trackExpiry": true,
    "defaultShelfLifeDays": 5,
    "expiryAlertDays": 2,
    "activeBatchCount": 1,
    "expiringBatchCount": 0,
    "expiredBatchCount": 0,
    "createdAt": "2025-12-15T10:30:00",
    "updatedAt": "2025-12-15T10:30:00"
  },
  "timestamp": "2025-12-15T10:30:00"
}
```

> **Note:** When both `currentStock` and `costPerUnit` are greater than zero, the create call also seeds the initial stock through the financial purchase-order pipeline (create → receive → pay), producing an inventory batch and cost history; the returned ingredient then reflects the received stock and weighted-average cost. (`weightedAverageCost` lives on the entity and the valuation views — it is **not** a field of `IngredientResponse`, so it does not appear in this JSON.)

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

> `PUT` replaces the ingredient from a full `IngredientRequest`, so the required fields (`restaurantId`, `name`, `unit`, `currentStock`, `minimumStock`, `reorderLevel`) must be present, along with any optional fields (`categoryId`, `supplierId`, `sku`, `description`, `active`, `trackInventory`, `trackExpiry`, `defaultShelfLifeDays`, `expiryAlertDays`).

### Deleting an Ingredient

**API Endpoint:**
```http
DELETE /api/v1/inventory/ingredients/{id}
```

**Hard Delete:**
This endpoint performs an actual delete (`ingredientRepository.deleteById(id)`); there is no
`@SQLDelete` on the ingredient entity. (Soft-delete via `active = false` applies to suppliers, not
ingredients.) To retire an ingredient without deleting it, set `active = false` through an update.

### Listing Ingredients

**API Endpoint:**
```http
GET /api/v1/inventory/ingredients?restaurantId=1&categoryId=7
```

- `restaurantId` (required)
- `categoryId` (optional)

**Response:**

Returns `ApiResponse<List<IngredientResponse>>` — a plain list in `data`. This is **not** a Spring `Page` (there is no `content` / `totalElements` / `totalPages`), and list items have **no** `status` field.

```json
{
  "success": true,
  "message": "Ingredients retrieved successfully",
  "data": [
    {
      "id": 42,
      "name": "Chicken Breast",
      "categoryId": 7,
      "categoryName": "Meat",
      "currentStock": 50.0,
      "unit": "kg",
      "minimumStock": 10.0,
      "reorderLevel": 15.0,
      "sku": "CHK-BRST-01",
      "active": true
    }
  ],
  "timestamp": "2025-12-15T10:30:00"
}
```

**Stock Status:**

`IngredientResponse` does not include a `status` field. Low-stock / reorder conditions are exposed through dedicated endpoints (`GET .../ingredients/low-stock`, `GET .../ingredients/reorder`) or computed on the client from `currentStock` vs `minimumStock` / `reorderLevel`:
- Below `minimumStock` → critical
- At or below `reorderLevel` → low / needs reorder
- Otherwise → adequate

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
  "notes": "Weekly delivery - Invoice #12345"
}
```

> `performedBy` is `@Deprecated` and **ignored** — the server records the authenticated user. Do not send it.

**Process:**
1. Validates quantity is positive
2. Calculates new stock level: `currentStock + quantity`
3. Creates `PURCHASE` transaction record
4. Updates ingredient's `current_stock`
5. Records balance before/after on the transaction

**Response:**

Returns `ApiResponse<IngredientResponse>` — the reloaded ingredient only. There is **no** `previousStock` and **no** nested `transaction` object (the `PURCHASE` transaction is still recorded internally and is visible via the transactions endpoint).

```json
{
  "success": true,
  "message": "Stock added successfully",
  "data": {
    "id": 42,
    "name": "Chicken Breast",
    "currentStock": 75.0,
    "minimumStock": 10.0,
    "reorderLevel": 15.0,
    "unit": "kg",
    "active": true
  },
  "timestamp": "2025-12-15T14:30:00"
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
  "notes": "Inventory count adjustment - found 5kg spoiled"
}
```

> This endpoint requires the `ADMIN`, `OWNER`, or `MANAGER` role. `performedBy` is `@Deprecated` and **ignored** — the server records the authenticated user. Do not send it.

**Process:**
1. Calculates difference: `newQuantity - currentStock`
2. Creates `ADJUSTMENT` transaction (can be positive or negative)
3. Updates ingredient's `current_stock` to exact value
4. Records reason in notes

**Response:**

Returns `ApiResponse<IngredientResponse>` — the reloaded ingredient only. There is **no** `previousStock` and **no** nested `transaction` object.

```json
{
  "success": true,
  "message": "Stock adjusted successfully",
  "data": {
    "id": 42,
    "name": "Chicken Breast",
    "currentStock": 45.0,
    "minimumStock": 10.0,
    "reorderLevel": 15.0,
    "unit": "kg",
    "active": true
  },
  "timestamp": "2025-12-15T16:00:00"
}
```

### Stock Deduction (Orders & Production)

There is **no** `use-stock` endpoint. Stock is deducted **internally** by the system:

- Completing/handling a customer order creates `ORDER_DEDUCTION` transactions for the recipe ingredients.
- Running a production batch creates `PRODUCTION_INPUT` (raw ingredients consumed) and `PRODUCTION_OUTPUT` (item produced) transactions.

**Process:**
1. Validates sufficient stock is available (`Ingredient.deductStock` throws on shortfall)
2. Deducts the quantity from `currentStock` (guarded by `@Version` optimistic locking)
3. Records an `ORDER_DEDUCTION` / `PRODUCTION_INPUT` transaction
4. Reorder/low-stock status is derived from the new `currentStock`

Consumption can be reviewed via the valuation/consumption endpoints (see [API Reference](#api-reference)).

### Recording Waste

Waste is recorded through the **Waste** controller (`WasteController`), not the ingredient controller. There is no `.../ingredients/{id}/record-waste` endpoint.

**API Endpoint:**
```http
POST /api/v1/inventory/waste
Content-Type: application/json

{
  "restaurantId": 1,
  "ingredientId": 42,
  "batchId": 15,
  "wasteDate": "2025-12-14",
  "quantity": 3.0,
  "unitCost": 8.50,
  "wasteReason": "EXPIRED",
  "notes": "Expired stock"
}
```

- `batchId` and `unitCost` are optional (`unitCost` defaults to the ingredient's cost).
- `wasteReason` is a `WasteRecord.WasteReason` enum value; allowed values are available from `GET /api/v1/inventory/waste/reasons`.
- Returns `ApiResponse<WasteRecordResponse>`.

**Process:**
1. Deducts the quantity from stock and creates a `WASTE` transaction
2. Records a `WasteRecord` for reporting
3. Waste totals are available via `GET /api/v1/inventory/waste/report?restaurantId=&startDate=&endDate=`

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
FROM inventory_transactions
WHERE ingredient_id = 42
  AND transaction_type = 'ORDER_DEDUCTION'
  AND transaction_date >= NOW() - INTERVAL '30 days'
GROUP BY DATE(transaction_date)
ORDER BY usage_date DESC;
```

### Cost Tracking

Calculate total inventory value:

```sql
SELECT
    i.category_id,
    SUM(i.current_stock * i.cost_per_unit) AS total_value,
    COUNT(*) AS item_count
FROM inventory_ingredients i
WHERE i.restaurant_id = 1
  AND i.active = true
GROUP BY i.category_id
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
      "minimumStock": 10.0,
      "reorderLevel": 15.0,
      "unit": "kg",
      "status": "CRITICAL",
      "daysUntilStockout": 2
    },
    {
      "id": 43,
      "name": "Olive Oil",
      "currentStock": 12.0,
      "minimumStock": 5.0,
      "reorderLevel": 10.0,
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
            .filter(i -> i.getCurrentStock().compareTo(i.getMinimumStock()) < 0)
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
      "reorderLevel": 15.0,
      "reorderQuantity": 30.0,
      "unit": "kg",
      "supplier": "Fresh Poultry Co.",
      "supplierId": 3,
      "costPerUnit": 8.50,
      "estimatedCost": 255.00,
      "priority": "HIGH"
    }
  ],
  "totalEstimatedCost": 1250.00,
  "itemCount": 8
}
```

### Purchase-Order Suggestions

> **Important:** `PurchaseOrder` is **not** part of the inventory module. It lives in the **financial** module (`com.elcafe.modules.financial`, entity `PurchaseOrder`, table `financial_purchase_orders`) and is created by the financial `PurchaseOrderService`.

The inventory module only exposes **PO suggestions** derived from reorder levels (`POSuggestionController`):

```http
GET  /api/v1/inventory/po-suggestions?restaurantId=1
GET  /api/v1/inventory/po-suggestions/count?restaurantId=1
POST /api/v1/inventory/po-suggestions/generate
POST /api/v1/inventory/po-suggestions/generate-all
```

The following is **illustrative only** — the actual purchase order is built and persisted by the financial module, not the inventory module:

```java
// Illustrative: real PurchaseOrder creation lives in the financial module's
// PurchaseOrderService (create -> receive -> pay), not in inventory.
PurchaseOrder po = purchaseOrderService.createAndFinalize(
        order, items, "CASH", LocalDate.now(), performedBy);
```

---

## API Reference

### Complete Endpoint List

Ingredient endpoints (`InventoryIngredientController`):

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/inventory/ingredients` | List ingredients (`restaurantId` required, `categoryId` optional; plain list) |
| GET | `/api/v1/inventory/ingredients/{id}` | Get ingredient details |
| POST | `/api/v1/inventory/ingredients` | Create new ingredient |
| PUT | `/api/v1/inventory/ingredients/{id}` | Update ingredient |
| DELETE | `/api/v1/inventory/ingredients/{id}` | Delete ingredient |
| POST | `/api/v1/inventory/ingredients/{id}/add-stock` | Add stock |
| POST | `/api/v1/inventory/ingredients/{id}/adjust-stock` | Adjust stock level (ADMIN/OWNER/MANAGER) |
| GET | `/api/v1/inventory/ingredients/{id}/transactions` | Get transaction history |
| GET | `/api/v1/inventory/ingredients/{id}/reconcile` | Reconcile one ingredient's stock |
| GET | `/api/v1/inventory/ingredients/low-stock` | Get low stock items |
| GET | `/api/v1/inventory/ingredients/reorder` | Get reorder list |
| GET | `/api/v1/inventory/ingredients/reconcile` | Reconcile all ingredients |

Sibling controllers:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST/PUT/DELETE | `/api/v1/inventory/ingredient-categories` | Manage ingredient categories |
| POST | `/api/v1/inventory/waste` | Record waste |
| GET | `/api/v1/inventory/waste/report` | Waste report |
| GET | `/api/v1/inventory/waste/reasons` | Allowed waste reasons |
| GET | `/api/v1/inventory/batches/expiring` | Expiring batches |
| GET | `/api/v1/inventory/batches/expired` | Expired batches |
| GET | `/api/v1/inventory/valuation/calculate` | Inventory valuation |
| GET | `/api/v1/inventory/valuation/consumption/{ingredientId}` | Ingredient consumption |
| GET | `/api/v1/inventory/valuation/consumption/summary` | Consumption summary |
| GET | `/api/v1/inventory/po-suggestions` | Purchase-order suggestions |
| GET/POST | `/api/v1/inventory/suppliers` | Manage suppliers |
| GET/POST | `/api/v1/inventory/recipes` | Manage recipes |
| GET/POST | `/api/v1/inventory/stock-counts` | Manage stock counts |
| GET/POST | `/api/v1/inventory/production-batches` | Manage production batches |

**Previously documented but do NOT exist (corrected):**
- `POST .../ingredients/{id}/use-stock` — stock deducts internally via orders/production
- `POST .../ingredients/{id}/record-waste` — use `POST /api/v1/inventory/waste`
- `GET .../ingredients/expiring` — use `GET /api/v1/inventory/batches/expiring`
- `GET /api/v1/inventory/categories` — use `GET /api/v1/inventory/ingredient-categories`
- `GET /api/v1/inventory/reports/usage` — use `GET /api/v1/inventory/valuation/consumption/*`
- `GET /api/v1/inventory/reports/waste` — use `GET /api/v1/inventory/waste/report`

### Query Parameters

The ingredient list and transaction endpoints return **plain lists** (no Spring pagination — no `page` / `size` / `sort`).

**Ingredient list** (`GET /api/v1/inventory/ingredients`):
- `restaurantId` (required) - restaurant to list ingredients for
- `categoryId` (optional) - filter by ingredient category

Other inventory endpoints use their own parameters, e.g. the waste report (`GET /api/v1/inventory/waste/report`) takes `restaurantId`, `startDate`, and `endDate`.

---

## Frontend Integration

### Inventory Pages

There is no single `Inventory.jsx`. The inventory UI is a multi-page module under `frontend/src/pages/inventory/`:

- `InventoryIngredients.jsx`, `InventoryRecipes.jsx`, `InventoryStockCounts.jsx`, `InventorySuppliers.jsx`, `InventoryValuation.jsx`, `InventoryWaste.jsx`, `ProductionBatches.jsx`, `InventoryAlerts.jsx`, `InventoryExpiry.jsx`, and `InventoryLayout.jsx` (shared layout; exported from `index.js`)
- Plus `frontend/src/pages/InventoryAnalytics.jsx` at the pages root

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
  });
  setStockModalOpen(true);
};

const handleStockSubmit = async () => {
  try {
    if (stockAction === 'add') {
      // performedBy is set server-side from the authenticated user; do not send it
      await inventoryAPI.addStock(selectedIngredient.id, {
        quantity: parseFloat(stockFormData.quantity),
        notes: stockFormData.notes,
      });
    } else {
      await inventoryAPI.adjustStock(selectedIngredient.id, {
        newQuantity: parseFloat(stockFormData.newQuantity),
        notes: stockFormData.notes,
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
  if (ingredient.currentStock < ingredient.minimumStock) {
    return { status: 'CRITICAL', color: 'red', icon: AlertTriangle };
  } else if (ingredient.currentStock <= ingredient.reorderLevel) {
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
    i.category_id,
    SUM(ABS(st.quantity)) AS total_waste,
    COUNT(*) AS waste_events,
    SUM(ABS(st.quantity) * st.cost_per_unit) AS total_cost
FROM inventory_transactions st
JOIN inventory_ingredients i ON st.ingredient_id = i.id
WHERE st.transaction_type = 'WASTE'
  AND st.transaction_date >= NOW() - INTERVAL '90 days'
GROUP BY i.id, i.name, i.category_id
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

**Root Cause:**
- Expiry is tracked per **batch**, not on the ingredient. There is no `expirationDate` field on `Ingredient`; instead the ingredient carries `trackExpiry` / `defaultShelfLifeDays` / `expiryAlertDays`, and each `InventoryBatch` holds its own expiry date.

**Solution:**
Query expiring/expired batches via the batch endpoints or repository (`GET /api/v1/inventory/batches/expiring`):
```java
@Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
public void checkExpiringItems() {
    LocalDate today = LocalDate.now();
    LocalDate weekFromNow = today.plusDays(7);

    List<InventoryBatch> expiring = inventoryBatchRepository
        .findExpiringBatches(today, weekFromNow);

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
        .findByRestaurantIdAndActiveTrue(restaurantId);

    BigDecimal totalValue = ingredients.stream()
        .map(i -> i.getCurrentStock().multiply(i.getCostPerUnit()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, BigDecimal> byCategory = ingredients.stream()
        .collect(Collectors.groupingBy(
            i -> i.getCategory() != null ? i.getCategory().getName() : "Uncategorized",
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

    List<InventoryTransaction> transactions = transactionRepository
        .findByIngredientIdAndTransactionTypeAndTransactionDateAfter(
            ingredientId,
            TransactionType.ORDER_DEDUCTION,
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
- **Frontend**: `/frontend/src/pages/inventory/` (multi-page module) and `/frontend/src/pages/InventoryAnalytics.jsx`

**Version:** 1.0
**Last Updated:** 2025-12-15
