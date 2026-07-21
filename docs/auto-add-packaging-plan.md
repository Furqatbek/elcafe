# Auto-Add Packaging Items — Implementation Plan

> **STATUS: SHIPPED** — fully implemented (migrations V114–V116/V127; `menu/PackagingRule` + `PackagingService`, wired into POS & self-service ordering). Kept as a historical design record.

## Problem

When a customer orders food for takeaway or delivery, the restaurant needs to add packaging items: bags, plastic bowls, spoons, napkins, etc. Currently, operators must remember to add these manually — items get forgotten, costs go untracked, and inventory doesn't deduct packaging materials.

## Solution

Products can be linked to packaging items that auto-add to the order when the order type is DELIVERY or TAKEAWAY. Packaging rules are per-product and per-order-type.

```
Customer orders: 1x Soup (TAKEAWAY)
System auto-adds: 1x Plastic Bowl, 1x Spoon, 1x Bag
───────────────────────────────────────────────
Customer orders: 2x Burger (DELIVERY)
System auto-adds: 2x Burger Box, 1x Bag, 2x Napkin Set
───────────────────────────────────────────────
Customer orders: 1x Soup (DINE_IN)
System auto-adds: nothing (dine-in uses real dishes)
```

---

## Phase 1: Planning & Design

### Data Model

```
Product "Soup"
  └── PackagingRule
        ├── packagingIngredient: "Plastic Bowl"
        ├── orderTypes: [DELIVERY, TAKEAWAY]
        ├── quantityMode: PER_ITEM          ← 1 bowl per soup ordered
        ├── autoAddQuantity: 1
        └── chargeToCustomer: false         ← cost absorbed by restaurant

Product "Soup"
  └── PackagingRule
        ├── packagingIngredient: "Bag"
        ├── orderTypes: [DELIVERY, TAKEAWAY]
        ├── quantityMode: PER_ORDER         ← 1 bag regardless of qty
        ├── autoAddQuantity: 1
        └── chargeToCustomer: true          ← customer pays for bag
```

### Quantity Modes

| Mode | Behavior | Example |
|------|----------|---------|
| `PER_ITEM` | Multiply by item quantity | 3 soups → 3 bowls |
| `PER_ORDER` | Add once regardless of quantity | 3 soups → 1 bag |
| `FIXED` | Always add this exact quantity | 3 soups → 2 napkin sets |

### Auto-Add Flow

```
Order submitted (POS or Self-Service)
  ↓
Backend: POSOrderService.createOrder()
  ↓
Check orderType == DELIVERY or TAKEAWAY?
  ↓ YES
For each OrderItem:
  → Query PackagingRule for this product + orderType
  → Calculate packaging quantity based on quantityMode
  → Create OrderItem for each packaging ingredient
  → Mark as isPackagingItem = true
  ↓
Packaging items appear in order with:
  - Price = 0 (restaurant absorbs) or configured price
  - isPackagingItem = true (for display/reporting)
  - Deducted from inventory if tracked
  ↓
Receipt shows packaging items separately
```

### Where Auto-Add Happens

**Backend-side** (not frontend) — this ensures packaging is added regardless of order source (POS, self-service, API, future mobile app). The backend is the single source of truth.

---

## Phase 2: Database & Entities

### Migration: `V114__create_packaging_rules_table.sql`

```sql
CREATE TABLE packaging_rules (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    packaging_ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    order_types VARCHAR(50) NOT NULL DEFAULT 'DELIVERY,TAKEAWAY',
    quantity_mode VARCHAR(20) NOT NULL DEFAULT 'PER_ITEM',
    auto_add_quantity INTEGER NOT NULL DEFAULT 1,
    charge_to_customer BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_packaging_rules_product ON packaging_rules(product_id);
CREATE INDEX idx_packaging_rules_restaurant ON packaging_rules(restaurant_id);
```

### Migration: `V115__add_is_packaging_item_to_order_items.sql`

```sql
ALTER TABLE order_items
    ADD COLUMN is_packaging_item BOOLEAN NOT NULL DEFAULT FALSE;
```

### New Entity: `PackagingRule.java`

**Location:** `modules/menu/entity/`

```java
@Entity
@Table(name = "packaging_rules")
public class PackagingRule {
    Long id;
    @ManyToOne Restaurant restaurant;
    @ManyToOne Product product;             // trigger product (Soup)
    @ManyToOne Ingredient packagingIngredient;  // inventory item (Bowl)
    String orderTypes;                      // "DELIVERY,TAKEAWAY"
    QuantityMode quantityMode;              // PER_ITEM, PER_ORDER, FIXED
    Integer autoAddQuantity;                // default 1
    Boolean chargeToCustomer;               // false = restaurant absorbs cost
    Boolean active;
    Integer sortOrder;
}

enum QuantityMode { PER_ITEM, PER_ORDER, FIXED }
```

### Modify Entity: `OrderItem.java`

Add field:
```java
@Column(name = "is_packaging_item")
@Builder.Default
private Boolean isPackagingItem = false;
```

---

## Phase 3: Repository

### `PackagingRuleRepository.java`

**Location:** `modules/menu/repository/`

```java
List<PackagingRule> findByProductIdAndActiveTrue(Long productId);
List<PackagingRule> findByRestaurantIdAndActiveTrue(Long restaurantId);
List<PackagingRule> findByRestaurantId(Long restaurantId);
```

---

## Phase 4: Service Layer

### `PackagingService.java`

**Location:** `modules/menu/service/`

| Method | Description |
|--------|-------------|
| `getPackagingItems(List<OrderItem> items, OrderType type)` | Returns list of packaging OrderItems to auto-add |
| `getRulesForProduct(Long productId)` | Get all packaging rules for a product |
| `getRulesForRestaurant(Long restaurantId)` | Get all rules for admin management |
| `createRule(CreatePackagingRuleRequest)` | Create a new packaging rule |
| `updateRule(Long ruleId, UpdatePackagingRuleRequest)` | Update rule |
| `deleteRule(Long ruleId)` | Delete rule |
| `toggleRule(Long ruleId)` | Enable/disable rule |

**Key logic in `getPackagingItems()`:**

```
Input: list of order items + order type (DELIVERY/TAKEAWAY)
Output: list of additional OrderItem objects for packaging

For each order item:
  1. Find active PackagingRules where:
     - product_id = item.productId
     - orderTypes contains current orderType
     - active = true
  2. For each matching rule:
     - Calculate quantity:
       PER_ITEM  → rule.autoAddQuantity × item.quantity
       PER_ORDER → rule.autoAddQuantity (add once, skip if already added)
       FIXED     → rule.autoAddQuantity
     - Create OrderItem:
       productId = rule.packagingIngredient.id
       productName = rule.packagingIngredient.name
       quantity = calculated quantity
       unitPrice = chargeToCustomer ? packagingIngredient.price : 0
       isPackagingItem = true

Deduplicate: if multiple products trigger the same PER_ORDER packaging
(e.g., both Soup and Burger add "Bag"), only add Bag once.
```

### Modify: `POSOrderService.java`

In `createOrder()`, after creating order items but before saving:

```java
if (order.getOrderType() == OrderType.DELIVERY || order.getOrderType() == OrderType.TAKEAWAY) {
    List<OrderItem> packagingItems = packagingService.getPackagingItems(orderItems, order.getOrderType());
    for (OrderItem pi : packagingItems) {
        pi.setOrder(order);
        order.addItem(pi);
    }
}
```

### Modify: `SelfServiceOrderService.java`

Same logic in `submitOrder()` for self-service orders.

---

## Phase 5: DTOs

**Location:** `modules/menu/dto/`

1. **`CreatePackagingRuleRequest.java`**
   - restaurantId, productId, packagingIngredientId
   - orderTypes (string: "DELIVERY,TAKEAWAY")
   - quantityMode, autoAddQuantity, chargeToCustomer

2. **`PackagingRuleResponse.java`**
   - All rule fields + productName + packagingIngredientName

---

## Phase 6: Controller

### `PackagingRuleController.java`

**Location:** `modules/menu/controller/`
**Base path:** `/api/v1/packaging-rules`
**Security:** `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/restaurant/{restaurantId}` | List all rules for restaurant |
| GET | `/product/{productId}` | List rules for a product |
| POST | `/` | Create rule |
| PUT | `/{id}` | Update rule |
| DELETE | `/{id}` | Delete rule |
| POST | `/{id}/toggle` | Enable/disable rule |

---

## Phase 7: Frontend — Admin Management Page

### Route: `/admin/products` (add "Packaging" tab) or standalone `/admin/packaging-rules`

**Recommended: Add as a section in the Products page** — when editing a product, show a "Packaging Rules" section.

### Product Edit — Packaging Rules Section

```
┌──────────────────────────────────────────────────┐
│ Packaging Rules (Delivery & Takeaway)            │
│                                                  │
│ When this product is ordered for delivery/        │
│ takeaway, these items are auto-added:            │
│                                                  │
│ ┌──────────────────────────────────────────────┐ │
│ │ Plastic Bowl  │ Per Item │ Qty: 1 │ Free  │ ✕│ │
│ │ Spoon         │ Per Item │ Qty: 1 │ Free  │ ✕│ │
│ │ Bag           │ Per Order│ Qty: 1 │ 500   │ ✕│ │
│ └──────────────────────────────────────────────┘ │
│                                                  │
│ [+ Add Packaging Item]                           │
└──────────────────────────────────────────────────┘
```

### Add Packaging Rule Dialog

```
┌────────────────────────────────────┐
│ Add Packaging Item                 │
│                                    │
│ Product: [Select product ▼]        │
│ Order Types: [✓ Delivery] [✓ Take] │
│ Mode: [Per Item ▼]                 │
│ Quantity: [1]                      │
│ Charge: [○ Free  ● Paid]          │
│ Price: [500] (if paid)             │
│                                    │
│ [Cancel] [Add]                     │
└────────────────────────────────────┘
```

---

## Phase 8: Frontend — POS & Self-Service Display

### POS Cart / Order Details

Show packaging items in the cart with a visual indicator:

```
Cart:
  1x Soup                    25,000
  1x Burger                  35,000
  ─── Packaging ───────────────────
  📦 1x Plastic Bowl           Free
  📦 1x Spoon                  Free
  📦 1x Burger Box             Free
  📦 1x Bag                      500
  ─────────────────────────────────
  Subtotal:                  60,500
```

### Packaging items should be:
- Visually distinct (icon, lighter color, grouped)
- Non-editable (auto-added, removed only by removing trigger product)
- Labeled as "Free" or showing price if charged

---

## Phase 9: i18n

```json
{
  "packaging.title": "Packaging Rules",
  "packaging.subtitle": "Auto-add items for delivery and takeaway orders",
  "packaging.addRule": "Add Packaging Item",
  "packaging.product": "Packaging Product",
  "packaging.selectProduct": "Select packaging item",
  "packaging.orderTypes": "Order Types",
  "packaging.delivery": "Delivery",
  "packaging.takeaway": "Takeaway",
  "packaging.quantityMode": "Quantity Mode",
  "packaging.perItem": "Per Item",
  "packaging.perOrder": "Per Order",
  "packaging.fixed": "Fixed",
  "packaging.quantity": "Quantity",
  "packaging.chargeToCustomer": "Charge to Customer",
  "packaging.free": "Free",
  "packaging.paid": "Paid",
  "packaging.noRules": "No packaging rules configured",
  "packaging.autoAdded": "Auto-added packaging",
  "packaging.perItemHint": "Multiplied by order quantity (3 soups → 3 bowls)",
  "packaging.perOrderHint": "Added once per order regardless of quantity",
  "packaging.fixedHint": "Always add this exact quantity"
}
```

---

## Phase 10: Testing

### Unit Tests (~10)
- `PackagingServiceTest`:
  - PER_ITEM mode: 3 soups → 3 bowls
  - PER_ORDER mode: 3 soups → 1 bag (not 3)
  - FIXED mode: always adds configured quantity
  - DINE_IN: no packaging added
  - Deduplication: 2 products both add "Bag" → only 1 bag
  - chargeToCustomer: price set correctly
  - Inactive rules: skipped

### Integration Tests (~5)
- `PackagingRuleRepositoryTest`: queries by product, restaurant, active
- `POSOrderService` integration: full flow with packaging auto-add

---

## Phase 11: Inventory Integration

If packaging ingredients (bags, bowls, spoons) are also tracked in inventory:
- They are regular `Product` entries with linked `ProductIngredient` recipes
- When auto-added to orders, `InventoryService.deductIngredientsForOrder()` deducts them like any other product
- This means packaging material costs flow into COGS automatically

**No extra work needed** — the existing inventory deduction handles it because packaging items are regular products.

---

## Implementation Order

| Step | Phase | Files | Description |
|------|-------|-------|-------------|
| 1 | Phase 2 | 2 migrations + 1 entity + modify OrderItem | Database + entities |
| 2 | Phase 3 | 1 repository | Data access |
| 3 | Phase 5 | 2 DTOs | Request/response |
| 4 | Phase 4 | 1 new service + modify 2 existing services | Core logic + integration |
| 5 | Phase 6 | 1 controller | REST API |
| 6 | Phase 7 | Modify Products.jsx | Admin packaging rules UI |
| 7 | Phase 8 | Modify POS cart display | Packaging items display |
| 8 | Phase 9 | 3 i18n files | Translations |
| 9 | Phase 10 | 2 test files (~15 tests) | Tests |

**Total: ~8 new files + ~5 modified files + ~15 tests**

---

## Example Flows

### Flow 1: Restaurant Setup

```
Admin opens Products → selects "Soup" → Packaging Rules tab
  → Add: "Plastic Bowl" (Per Item, qty 1, Free)
  → Add: "Spoon" (Per Item, qty 1, Free)
  → Add: "Bag" (Per Order, qty 1, charged 500)
```

### Flow 2: POS Takeaway Order

```
Cashier: New Order → Takeaway
  → Adds 2x Soup, 1x Salad
  → System auto-adds:
      2x Plastic Bowl (Per Item × 2 soups)
      2x Spoon (Per Item × 2 soups)
      1x Salad Container (Per Item × 1 salad)
      1x Bag (Per Order × 1 — shared)
  → Total includes bag cost (500) but bowls/spoons are free
```

### Flow 3: Self-Service Takeaway QR Order

```
Customer scans QR → selects Takeaway
  → Adds 1x Soup to cart
  → Submits order
  → Backend auto-adds packaging before saving
  → Customer sees: Soup + (Packaging: Bowl, Spoon, Bag)
  → Receipt shows all items
```

### Flow 4: Dine-In Order (No Packaging)

```
Waiter: New Order → Dine-In → Table 5
  → Adds 2x Soup
  → No packaging rules triggered (DINE_IN excluded)
  → Order has only: 2x Soup
```
