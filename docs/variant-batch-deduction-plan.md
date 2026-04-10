# Variant-Based Batch Deduction — Implementation Plan

> **Status: COMPLETED** — All 6 phases implemented and pushed.

## Problem

Manti is cooked in a batch (e.g., 100 pieces) but sold in multiple ways:
- **1 Piece** → deduct 1 from batch
- **1 Portion (5 pieces)** → deduct 5 from batch
- **By Weight (1 kg)** → deduct by weight

Previously, variants only changed price/name — they didn't affect how much was deducted from a production batch.

## Solution

Added `batchDeductionQuantity` to `ProductVariant` — a multiplier that tells the system how much to deduct from the production batch per unit ordered.

```
Product "Manti" (usesProductionBatch=true, batch unit: pieces)
├── Variant "1 Piece"    → price: 5000,  batchDeductionQuantity: 1
├── Variant "1 Portion"  → price: 20000, batchDeductionQuantity: 5
└── Variant "By Weight"  → price: 80000, batchDeductionQuantity: null (uses weightAmount)
```

Formula: `batchQuantityToDeduct = batchDeductionQuantity × orderQuantity`

---

## Data Flow

```
POS (user selects variant)
  → posStore.addItemToCart(product, modifiers, qty, { variantId, variantName })
  → submitOrder() includes variantId/variantName in API payload
  → CreatePOSOrderRequest / ModifyOrderItemRequest (variantId, variantName)
  → POSOrderItemService.createOrderItem() sets variant/weight/portion on OrderItem
  → Order confirmed
  → InventoryService.deductIngredientsForOrder()
      → reads item.variantId
      → looks up ProductVariant.batchDeductionQuantity via ProductVariantRepository
      → passes to ProductionBatchService.consumeForOrder()
      → deducts using priority rules
```

## Priority Rules for Batch Deduction

```
1. weightAmount (if set and > 0)    → use as-is (weight-based selling)
2. variant.batchDeductionQuantity   → multiply by order quantity
3. fallback                         → use order item quantity as-is
```

---

## Implementation Summary

### Phase 2: Database & Entity
- `V112__add_batch_deduction_qty_to_variants.sql` — `DECIMAL(10,4)` column, nullable
- `ProductVariant.java` — `batchDeductionQuantity` field

### Phase 3: Backend Logic
- `ModifyOrderItemRequest.java` — added `variantId`, `variantName`
- `CreatePOSOrderRequest.OrderItemRequest` — added `variantId`, `variantName`
- `POSOrderItemService.createOrderItem()` — populates variant, weight, portion fields + fixed price calculation for weight/portion-based items
- `InventoryService.deductIngredientsForOrder()` — looks up variant's `batchDeductionQuantity`, passes to batch service
- `ProductionBatchService.consumeForOrder()` — accepts `batchDeductionQty`, applies priority rules

### Phase 4: Frontend
- `Products.jsx` — `batchDeductionQuantity` input in variant create/edit forms (conditional on `usesProductionBatch`)
- `posStore.js` — `addItemToCart` tracks `variantId`/`variantName`, merge key includes variant, `submitOrder` sends to API
- `ProductModifiersScreen.jsx` — passes selected variant id/name to cart
- `OrderModificationScreen.jsx` — passes variant/weight/portion when adding items
- i18n keys added to en.json, ru.json, uz.json

### Phase 5: Testing
- `ProductionBatchServiceTest` — 6 new `consumeForOrder` tests (multiplier, weight override, fallback, zero, null, no batch)
- `InventoryServiceTest` — 2 new tests (with variant passes deduction qty, without variant passes null)

---

## API Examples

### Create product with variants for batch deduction

```
POST /api/v1/products
{ "name": "Manti", "usesProductionBatch": true, ... }

POST /api/v1/products/42/variants
{ "name": "1 Piece",   "price": 5000,  "batchDeductionQuantity": 1 }

POST /api/v1/products/42/variants
{ "name": "1 Portion", "price": 20000, "batchDeductionQuantity": 5 }

POST /api/v1/products/42/variants
{ "name": "By Weight", "price": 80000, "batchDeductionQuantity": null }
```

### Order with variant (POS creates order)

```
POST /api/v1/pos/orders
{
  "items": [
    {
      "productId": 42,
      "variantId": 101,
      "variantName": "1 Portion",
      "quantity": 2,
      "price": 20000
    }
  ]
}
→ System looks up variant 101 → batchDeductionQuantity = 5
→ Deducts 5 × 2 = 10 pieces from production batch
→ Cost recorded: 10 × costPerUnit
```

### Order by weight

```
POST /api/v1/pos/orders
{
  "items": [
    {
      "productId": 42,
      "variantId": 103,
      "variantName": "By Weight",
      "quantity": 1,
      "price": 80000,
      "weightAmount": 0.5
    }
  ]
}
→ weightAmount takes priority over batchDeductionQuantity
→ Deducts 0.5 from production batch
→ Cost recorded: 0.5 × costPerUnit
```

## Example Flow

```
1. Create Product "Manti" (usesProductionBatch=true)
2. Add Variants:
   - "1 Piece"   → price: 5000,  batchDeductionQuantity: 1
   - "1 Portion" → price: 20000, batchDeductionQuantity: 5
   - "By Weight" → price: 80000, batchDeductionQuantity: null

3. Create Production Batch: 100 pieces of Manti

4. Customer orders:
   - 2x "1 Piece"      → deducts 2   (1 × 2)  → remaining: 98
   - 1x "1 Portion"    → deducts 5   (5 × 1)  → remaining: 93
   - 0.5kg "By Weight" → deducts 0.5 (weight)  → remaining: 92.5
```
