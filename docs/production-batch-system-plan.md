# Production Batch System — Implementation Plan

> **STATUS: SHIPPED** — fully implemented (migrations V109–V111; `inventory/ProductionBatch*` entities + service + controller, `usesProductionBatch` on Product, `ProductionBatches.jsx` at `/kitchen/production`). Kept as a historical design record.

## Context

The restaurant kitchen cooks in batches (e.g., 10L soup pot), not per-portion. The current system only supports per-portion recipes (`ProductIngredient`) that deduct raw ingredients directly when orders come in. This fails for:

- **Soups/stews** with mixed liquid+solid ingredients (kg + L) yielding a single output unit (L)
- **Dynamic portions** — same batch sold as 0.5L, 0.7L, or by weight
- **Cost accuracy** — actual cooking yield differs from recipe (meat shrinks 30%, water evaporates)
- **No visibility** into what was cooked vs what was sold vs what was wasted

The Production Batch system adds an intermediate "prepared inventory" layer between raw ingredients and customer orders.

## Architecture

```
CURRENT:  Raw Ingredients → [Recipe] → Order → Deduct Raw Stock
NEW:      Raw Ingredients → [Production Batch] → Prepared Inventory → Order → Deduct Prepared Stock
                                                                   ↘ End of Day → Waste/Carryover
```

Both systems coexist: steaks use direct recipe deduction, soups use production batches. A flag on Product determines which path.

---

## Phase 1: Database & Entities

### New Flyway Migration: `V109__create_production_batch_tables.sql`

**Table: `production_batches`**
```sql
id BIGSERIAL PRIMARY KEY,
restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
product_id BIGINT REFERENCES products(id),          -- optional link to menu product
batch_number VARCHAR(50) NOT NULL UNIQUE,
name VARCHAR(200) NOT NULL,                          -- "Shurva", "Osh Palov"
output_quantity DECIMAL(10,3) NOT NULL,              -- yield: 10.000
output_unit VARCHAR(20) NOT NULL,                    -- L, kg, pieces
remaining_quantity DECIMAL(10,3) NOT NULL,           -- decremented as sold
total_input_cost DECIMAL(15,4) NOT NULL DEFAULT 0,   -- sum of all ingredient costs
cost_per_unit DECIMAL(15,4),                          -- total_cost / output_quantity
status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',          -- DRAFT, IN_PROGRESS, READY, SERVING, DEPLETED, EXPIRED, WASTED
prepared_by VARCHAR(100),
started_at TIMESTAMP,
completed_at TIMESTAMP,
expires_at TIMESTAMP,                                -- when prepared food expires
notes TEXT,
version BIGINT NOT NULL DEFAULT 0,                   -- optimistic locking
created_at TIMESTAMP NOT NULL DEFAULT NOW(),
updated_at TIMESTAMP NOT NULL DEFAULT NOW()
```

**Table: `production_batch_inputs`**
```sql
id BIGSERIAL PRIMARY KEY,
production_batch_id BIGINT NOT NULL REFERENCES production_batches(id),
ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
planned_quantity DECIMAL(10,3),                       -- from recipe (optional)
actual_quantity DECIMAL(10,3) NOT NULL,               -- what was actually used
unit VARCHAR(20) NOT NULL,
cost_per_unit DECIMAL(15,4) NOT NULL,                 -- ingredient cost at time of use
total_cost DECIMAL(15,4) NOT NULL,                    -- actual_quantity * cost_per_unit
batch_consumption_id BIGINT REFERENCES batch_consumptions(id),  -- link to inventory consumption
notes TEXT,
created_at TIMESTAMP NOT NULL DEFAULT NOW()
```

**Table: `production_batch_consumptions`**
```sql
id BIGSERIAL PRIMARY KEY,
production_batch_id BIGINT NOT NULL REFERENCES production_batches(id),
order_id BIGINT REFERENCES orders(id),
order_item_id BIGINT,
quantity DECIMAL(10,3) NOT NULL,                      -- how much was consumed (0.5L, 0.7L)
cost_per_unit DECIMAL(15,4) NOT NULL,                 -- production_batch.cost_per_unit at time
total_cost DECIMAL(15,4) NOT NULL,
consumed_at TIMESTAMP NOT NULL DEFAULT NOW()
```

### New Entities (in `modules/inventory/entity/`)

1. **`ProductionBatch.java`** — main entity, follows `InventoryBatch` patterns
   - Status enum: `DRAFT, IN_PROGRESS, READY, SERVING, DEPLETED, EXPIRED, WASTED`
   - Methods: `consume(quantity)`, `isAvailable()`, `getRemaining()`, `calculateCostPerUnit()`
   - `@Version` for optimistic locking (same pattern as `Ingredient`, `InventoryBatch`)

2. **`ProductionBatchInput.java`** — ingredient inputs for a batch
   - Links to `Ingredient` (inventory module)
   - Stores planned vs actual quantities
   - Links to `BatchConsumption` (reuses existing consumption tracking)

3. **`ProductionBatchConsumption.java`** — tracks sales from prepared inventory
   - Links to `Order` and `OrderItem` for COGS tracking
   - Follows `BatchConsumption` patterns

### Existing Entities to Modify

4. **`Product.java`** (`modules/menu/entity/`) — add field:
   ```java
   @Column(name = "uses_production_batch")
   @Builder.Default
   private Boolean usesProductionBatch = false;
   ```

5. **`TransactionType.java`** (`modules/inventory/enums/`) — add:
   ```java
   PRODUCTION_INPUT,    // Raw ingredients consumed for production
   PRODUCTION_OUTPUT    // Prepared item produced
   ```

6. **`WasteRecord.WasteReason`** — add: `OVER_PRODUCTION`

### Migration for Product field: `V110__add_production_batch_flag_to_products.sql`

---

## Phase 2: Repository Layer

### New Repositories (in `modules/inventory/repository/`)

1. **`ProductionBatchRepository.java`**
   - `findByRestaurantIdAndStatus(Long restaurantId, Status status)`
   - `findByProductIdAndStatusIn(Long productId, List<Status> statuses)` — find servable batches
   - `findAvailableByProduct(Long productId)` — READY/SERVING with remaining > 0
   - `@Query` for cost reports: total production cost by date range, avg cost per unit

2. **`ProductionBatchInputRepository.java`**
   - `findByProductionBatchId(Long batchId)`
   - `@Query` for ingredient usage reports

3. **`ProductionBatchConsumptionRepository.java`**
   - `findByOrderId(Long orderId)` — for COGS
   - `findByProductionBatchId(Long batchId)` — batch usage report
   - `@Query` sum consumption by date range for analytics

---

## Phase 3: Service Layer

### New Service: `ProductionBatchService.java`

**Location:** `modules/inventory/service/`

**Dependencies (inject):**
- `ProductionBatchRepository`
- `ProductionBatchInputRepository`
- `ProductionBatchConsumptionRepository`
- `InventoryValuationService` (reuse for raw ingredient consumption)
- `InventoryService` (reuse for stock checks)
- `ProductRepository`
- `ProductIngredientRepository` (for recipe-based planned quantities)
- `IngredientRepository`

**Public Methods:**

| Method | Description |
|--------|-------------|
| `createBatch(CreateProductionBatchRequest)` | Create DRAFT batch, optionally load recipe as planned inputs |
| `addInput(Long batchId, AddInputRequest)` | Add/update an ingredient input (actual quantity used) |
| `startBatch(Long batchId)` | Set status IN_PROGRESS, record startedAt |
| `completeBatch(Long batchId, CompleteBatchRequest)` | Set output qty/unit, deduct raw ingredients via `InventoryValuationService`, calculate cost_per_unit, set READY |
| `consumeFromBatch(Long batchId, BigDecimal qty, Long orderId, Long orderItemId)` | Deduct from remaining_quantity, create ProductionBatchConsumption, return cost |
| `findAvailableBatch(Long productId)` | Find best batch to serve from (FEFO by expires_at) |
| `recordWaste(Long batchId, BigDecimal qty, String reason)` | Record leftover as waste via existing `WasteService` |
| `getActiveBatches(Long restaurantId)` | List READY/SERVING batches with remaining qty |
| `getBatchCostReport(Long restaurantId, LocalDate from, LocalDate to)` | Production cost analytics |

**Key Logic in `completeBatch()`:**
```
1. Load all ProductionBatchInput records
2. For each input: call InventoryValuationService.consumeWithValuation(ingredientId, actualQty, null)
3. Sum all input costs → total_input_cost
4. cost_per_unit = total_input_cost / output_quantity
5. remaining_quantity = output_quantity
6. status = READY
7. Record InventoryTransaction (type=PRODUCTION_INPUT) for each ingredient
```

### Modify: `InventoryService.java`

**In `deductIngredientsForOrder(Order order)`** — add branch:
```java
for (OrderItem item : order.getItems()) {
    Product product = productRepository.findById(item.getProductId());
    
    if (Boolean.TRUE.equals(product.getUsesProductionBatch())) {
        // Deduct from prepared inventory
        productionBatchService.consumeForOrder(product.getId(), item.getQuantity(), 
            item.getWeightAmount(), order.getId(), item.getId());
    } else {
        // Existing path: deduct raw ingredients via recipe
        deductRawIngredients(item, order);
    }
}
```

### Modify: `ProductCostService.java`

**In `recalculateProductCost(Long productId)`** — add:
```java
if (Boolean.TRUE.equals(product.getUsesProductionBatch())) {
    // Use latest production batch cost_per_unit
    BigDecimal avgCost = productionBatchRepository.getAverageCostPerUnit(productId);
    product.setCostPrice(avgCost != null ? avgCost : product.getCostPrice());
} else {
    // Existing recipe-based calculation
}
```

### Modify: `InventoryValuationService.java`

**In `restoreInventoryForOrder(Long orderId)`** — add:
```java
// Also restore production batch consumption
List<ProductionBatchConsumption> pbConsumptions = 
    productionBatchConsumptionRepository.findByOrderId(orderId);
for (ProductionBatchConsumption pbc : pbConsumptions) {
    ProductionBatch batch = pbc.getProductionBatch();
    batch.setRemainingQuantity(batch.getRemainingQuantity().add(pbc.getQuantity()));
    productionBatchRepository.save(batch);
}
productionBatchConsumptionRepository.deleteByOrderId(orderId);
```

---

## Phase 4: DTOs

### New DTOs (in `modules/inventory/dto/`)

1. **`CreateProductionBatchRequest.java`**
   - restaurantId, productId (optional), name, outputUnit, expiresAt, notes, preparedBy
   - loadRecipe (boolean) — auto-populate inputs from ProductIngredient recipe

2. **`AddInputRequest.java`**
   - ingredientId, actualQuantity, unit, notes

3. **`CompleteBatchRequest.java`**
   - outputQuantity, outputUnit, notes

4. **`ProductionBatchResponse.java`**
   - All batch fields + inputs list + cost breakdown

5. **`ProductionBatchSummary.java`**
   - id, name, product, outputQuantity, remainingQuantity, costPerUnit, status, expiresAt

---

## Phase 5: Controller Layer

### New Controller: `ProductionBatchController.java`

**Location:** `modules/inventory/controller/`
**Base path:** `/api/v1/inventory/production-batches`
**Security:** `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'KITCHEN_STAFF')")`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/` | Create new production batch |
| GET | `/restaurant/{restaurantId}` | List batches (filter by status, date) |
| GET | `/{id}` | Get batch details with inputs |
| POST | `/{id}/inputs` | Add ingredient input |
| PUT | `/{id}/inputs/{inputId}` | Update ingredient input |
| POST | `/{id}/start` | Start production |
| POST | `/{id}/complete` | Complete with yield |
| POST | `/{id}/waste` | Record waste/leftover |
| GET | `/restaurant/{restaurantId}/available` | List servable batches |
| GET | `/restaurant/{restaurantId}/report` | Cost report by date range |
| DELETE | `/{id}` | Cancel DRAFT batch |

---

## Phase 6: Integration Points

### Files to Modify (existing)

| # | File | Change |
|---|------|--------|
| 1 | `modules/menu/entity/Product.java` | Add `usesProductionBatch` field |
| 2 | `modules/inventory/enums/TransactionType.java` | Add `PRODUCTION_INPUT`, `PRODUCTION_OUTPUT` |
| 3 | `modules/inventory/service/InventoryService.java` | Branch on `usesProductionBatch` in `deductIngredientsForOrder` |
| 4 | `modules/inventory/service/ProductCostService.java` | Use production batch avg cost when applicable |
| 5 | `modules/inventory/service/InventoryValuationService.java` | Restore production batch consumption on order cancel |
| 6 | `modules/analytics/service/FinancialAnalyticsService.java` | Include production batch COGS in reports |
| 7 | `frontend/src/services/api.js` | Add `productionBatchAPI` endpoints |
| 8 | `frontend/src/pages/inventory/InventoryLayout.jsx` | Add "Production" nav tab |
| 9 | `frontend/src/pages/inventory/index.js` | Export `ProductionBatches` |
| 10 | `frontend/src/App.jsx` | Add `/kitchen/production` route |
| 11 | `frontend/src/pages/Products.jsx` | Add "Uses Production Batch" toggle |

### Files to Create (new)

| # | File | Type |
|---|------|------|
| 1 | `V109__create_production_batch_tables.sql` | Migration |
| 2 | `V110__add_production_batch_flag_to_products.sql` | Migration |
| 3 | `modules/inventory/entity/ProductionBatch.java` | Entity |
| 4 | `modules/inventory/entity/ProductionBatchInput.java` | Entity |
| 5 | `modules/inventory/entity/ProductionBatchConsumption.java` | Entity |
| 6 | `modules/inventory/repository/ProductionBatchRepository.java` | Repository |
| 7 | `modules/inventory/repository/ProductionBatchInputRepository.java` | Repository |
| 8 | `modules/inventory/repository/ProductionBatchConsumptionRepository.java` | Repository |
| 9 | `modules/inventory/service/ProductionBatchService.java` | Service |
| 10 | `modules/inventory/controller/ProductionBatchController.java` | Controller |
| 11 | `modules/inventory/dto/CreateProductionBatchRequest.java` | DTO |
| 12 | `modules/inventory/dto/AddInputRequest.java` | DTO |
| 13 | `modules/inventory/dto/CompleteBatchRequest.java` | DTO |
| 14 | `modules/inventory/dto/ProductionBatchResponse.java` | DTO |
| 15 | `modules/inventory/dto/ProductionBatchSummary.java` | DTO |
| 16 | `frontend/src/pages/inventory/ProductionBatches.jsx` | Frontend page |

---

## Phase 7: Tests

### Unit Tests (Mockito)

1. **`ProductionBatchServiceTest.java`** (~20 tests)
   - createBatch with/without recipe loading
   - addInput validates ingredient exists
   - completeBatch deducts raw ingredients, calculates cost_per_unit
   - consumeFromBatch decrements remaining, creates consumption record
   - consumeFromBatch insufficient remaining throws exception
   - findAvailableBatch returns READY/SERVING with remaining > 0
   - recordWaste delegates to WasteService
   - Batch status transitions (DRAFT→IN_PROGRESS→READY→SERVING→DEPLETED)

2. **`ProductionBatchControllerTest.java`** (~12 tests)
   - All 11 endpoints + validation error case

### Integration Tests (@DataJpaTest)

3. **`ProductionBatchRepositoryTest.java`** (~5 tests)
   - findAvailableByProduct returns correct batches
   - findByRestaurantIdAndStatus filters correctly
   - getAverageCostPerUnit calculates across batches

4. **`ProductionBatchConsumptionRepositoryTest.java`** (~3 tests)
   - findByOrderId returns consumptions
   - sum by date range for analytics

### Modified Test Updates

5. Update `InventoryServiceTest` — add test for production batch path in deductIngredientsForOrder
6. Update `ProductCostServiceTest` — add test for production batch cost source

---

## Phase 8: Frontend

### 8a. API Definition (modify `frontend/src/services/api.js`)

Add new API module:
```javascript
export const productionBatchAPI = {
  create: (data) => api.post('/inventory/production-batches', data),
  getByRestaurant: (restaurantId, params) => api.get(`/inventory/production-batches/restaurant/${restaurantId}`, { params }),
  getById: (id) => api.get(`/inventory/production-batches/${id}`),
  addInput: (id, data) => api.post(`/inventory/production-batches/${id}/inputs`, data),
  updateInput: (id, inputId, data) => api.put(`/inventory/production-batches/${id}/inputs/${inputId}`, data),
  start: (id) => api.post(`/inventory/production-batches/${id}/start`),
  complete: (id, data) => api.post(`/inventory/production-batches/${id}/complete`, data),
  recordWaste: (id, data) => api.post(`/inventory/production-batches/${id}/waste`),
  getAvailable: (restaurantId) => api.get(`/inventory/production-batches/restaurant/${restaurantId}/available`),
  getReport: (restaurantId, params) => api.get(`/inventory/production-batches/restaurant/${restaurantId}/report`, { params }),
  delete: (id) => api.delete(`/inventory/production-batches/${id}`),
};
```

### 8b. New Frontend Page: `frontend/src/pages/inventory/ProductionBatches.jsx`

**Route:** `/kitchen/production` (follows existing pattern: `/kitchen/inventory`, `/kitchen/recipes`, etc.)
**Wraps in:** `<InventoryWrapper>` (uses `InventoryContext` for restaurant selector)

**Page Layout:**
```
┌─────────────────────────────────────────────────────┐
│ [Inventory Nav Tabs: ... | Production | ...]        │
├─────────────────────────────────────────────────────┤
│ Restaurant: [dropdown]        [+ New Batch] button  │
├─────────────────────────────────────────────────────┤
│ Filter: [All | Active | Completed | Expired]        │
├─────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────┐ │
│ │ Batch List (table)                              │ │
│ │ Name | Product | Output | Remaining | Cost/Unit │ │
│ │ Status | Prepared By | Date | Actions           │ │
│ └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

**Features:**
1. **Batch list** — table with status badges, remaining quantity bar, cost per unit
2. **Create Batch dialog** — select product (optional), name, output unit, load recipe toggle
3. **Batch Detail view** (modal or expandable row):
   - Input ingredients table (planned vs actual, cost per ingredient)
   - Total cost breakdown pie chart
   - Consumption history (which orders consumed from this batch)
   - Actions: Start, Complete (enter yield), Record Waste
4. **Complete Batch dialog** — enter actual output quantity, see calculated cost/unit
5. **Waste dialog** — enter remaining quantity to waste, reason dropdown
6. **Status flow visualization**: DRAFT → IN_PROGRESS → READY → SERVING → DEPLETED

### 8c. Modify Existing Frontend Files

| File | Change |
|------|--------|
| `frontend/src/pages/inventory/InventoryLayout.jsx` | Add nav item: `{ path: '/kitchen/production', label: 'inventory.tabs.production', icon: ChefHat }` |
| `frontend/src/pages/inventory/index.js` | Export `ProductionBatches` component |
| `frontend/src/App.jsx` | Add route: `<Route path="kitchen/production" element={<InventoryWrapper><ProductionBatches /></InventoryWrapper>} />` |
| `frontend/src/pages/Products.jsx` | Add "Uses Production Batch" toggle in product edit form |
| `frontend/src/pages/Recipes.jsx` | Show "linked to production batch" indicator on recipes that use this flow |

### 8d. Translation Keys (add to i18n files)

```json
{
  "inventory.tabs.production": "Production",
  "production.title": "Production Batches",
  "production.createBatch": "New Batch",
  "production.name": "Batch Name",
  "production.product": "Product",
  "production.outputQuantity": "Yield",
  "production.outputUnit": "Unit",
  "production.remainingQuantity": "Remaining",
  "production.costPerUnit": "Cost/Unit",
  "production.totalCost": "Total Cost",
  "production.status": "Status",
  "production.preparedBy": "Prepared By",
  "production.loadRecipe": "Load from Recipe",
  "production.complete": "Complete Batch",
  "production.recordWaste": "Record Waste",
  "production.actualQuantity": "Actual Used",
  "production.plannedQuantity": "Planned",
  "production.statuses.DRAFT": "Draft",
  "production.statuses.IN_PROGRESS": "In Progress",
  "production.statuses.READY": "Ready",
  "production.statuses.SERVING": "Serving",
  "production.statuses.DEPLETED": "Depleted",
  "production.statuses.EXPIRED": "Expired"
}
```

### 8e. API Examples

**Create a production batch:**
```
POST /api/v1/inventory/production-batches
{
  "restaurantId": 1,
  "productId": 42,
  "name": "Shurva Morning Batch",
  "outputUnit": "L",
  "loadRecipe": true,
  "preparedBy": "Chef Aziz",
  "expiresAt": "2026-04-08T23:00:00"
}
```

**Add actual ingredient used:**
```
POST /api/v1/inventory/production-batches/1/inputs
{
  "ingredientId": 15,
  "actualQuantity": 3.0,
  "unit": "kg",
  "notes": "Used bone-in cuts"
}
```

**Complete batch with yield:**
```
POST /api/v1/inventory/production-batches/1/complete
{
  "outputQuantity": 8.5,
  "outputUnit": "L",
  "notes": "Evaporation reduced from expected 10L"
}
→ Response: { costPerUnit: 34235.29, totalInputCost: 291000 }
```

**Order deduction (automatic):**
When customer orders 0.5L Shurva:
```
System auto-deducts 0.5L from production batch
Cost recorded: 0.5 × 34235.29 = 17117.65
```

---

## Verification

1. **Unit tests:** `mvn test -Dtest="ProductionBatchServiceTest,ProductionBatchControllerTest"`
2. **Integration tests:** `mvn test -Dtest="ProductionBatchRepositoryTest,ProductionBatchConsumptionRepositoryTest"`
3. **Full suite regression:** `mvn test` — verify no existing tests break
4. **Manual flow test:**
   - Create batch → add inputs → complete → verify ingredient stock decreased
   - Create order for production-batch product → verify prepared inventory decreased
   - Cancel order → verify prepared inventory restored
   - Check analytics COGS → verify production batch costs included

---

## Implementation Order

| Step | Phase | Files | Description |
|------|-------|-------|-------------|
| 1 | Phase 1 | 2 migrations + 3 entities + modify Product + modify TransactionType | Database schema + entity classes |
| 2 | Phase 2 | 3 repositories | Repository interfaces with @Query methods |
| 3 | Phase 4 | 5 DTOs | Request/response DTOs |
| 4 | Phase 3 | 1 new service + modify 3 existing services | Core logic + integration |
| 5 | Phase 5 | 1 controller | REST API endpoints |
| 6 | Phase 7 | 4 test files (~40 tests) | Unit + integration tests |
| 7 | Phase 8 | 1 new page + modify 4 existing frontend files + i18n | Frontend UI + routing + API |

**Total: 16 new files + 11 modified files + ~40 tests**

---

## Variant-Based Batch Deduction (Extension)

> See full plan: `docs/variant-batch-deduction-plan.md`

Products like Manti are batch-cooked but sold in multiple ways (1 piece, 1 portion of 5 pieces, or by weight). The `batchDeductionQuantity` field on `ProductVariant` tells the system how many batch units to deduct per order unit.

### Priority Rules

```
1. weightAmount (if set)           → use as-is (weight-based)
2. variant.batchDeductionQuantity  → multiply by order quantity
3. fallback                        → use order quantity as-is
```

### Files Added/Modified

| File | Change |
|------|--------|
| `V112__add_batch_deduction_qty_to_variants.sql` | Migration: `batch_deduction_quantity DECIMAL(10,4)` |
| `ProductVariant.java` | Added `batchDeductionQuantity` field |
| `ModifyOrderItemRequest.java` | Added `variantId`, `variantName` |
| `CreatePOSOrderRequest.java` | Added `variantId`, `variantName` |
| `POSOrderItemService.java` | Populates variant/weight/portion on OrderItem, fixed price calc |
| `InventoryService.java` | Looks up variant deduction qty, passes to batch service |
| `ProductionBatchService.java` | `consumeForOrder()` accepts and uses `batchDeductionQty` |
| `Products.jsx` | Batch deduction qty field in variant form (conditional) |
| `posStore.js` | Tracks variantId/variantName in cart and order submission |
| `ProductModifiersScreen.jsx` | Passes variant to cart |
| `OrderModificationScreen.jsx` | Passes variant/weight/portion on item add |
| `en.json`, `ru.json`, `uz.json` | i18n keys for batch deduction field |
