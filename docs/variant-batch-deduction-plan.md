# Variant-Based Batch Deduction — Implementation Plan

## Problem

Manti is cooked in a batch (e.g., 100 pieces) but sold in multiple ways:
- **1 Piece** → deduct 1 from batch
- **1 Portion (5 pieces)** → deduct 5 from batch
- **By Weight (1 kg ≈ 20 pieces)** → deduct by weight

Currently, variants only change price/name — they don't affect how much is deducted from a production batch. The system deducts `quantity=1` regardless of variant.

## Solution

Add `batchDeductionQuantity` to `ProductVariant` — a multiplier that tells the system how much to deduct from the production batch per unit ordered.

```
Product "Manti" (usesProductionBatch=true, batch unit: pieces)
├── Variant "1 Piece"    → price: 5000,  batchDeductionQuantity: 1
├── Variant "1 Portion"  → price: 20000, batchDeductionQuantity: 5
└── Variant "By Weight"  → price: 80000, batchDeductionQuantity: null (uses weightAmount)
```

When ordering: `batchQuantityToDeduct = batchDeductionQuantity × orderQuantity`

---

## Phase 1: Planning & Design

### Data Flow (new)

```
POS → ModifyOrderItemRequest (variantId added)
    → POSOrderItemService.createOrderItem() (sets variantId, variantName on OrderItem)
    → Order confirmed
    → InventoryService.deductIngredientsForOrder()
        → reads item.variantId
        → looks up ProductVariant.batchDeductionQuantity
        → passes to ProductionBatchService.consumeForOrder()
        → deducts: batchDeductionQuantity × quantity (or weightAmount if weight-based)
```

### Priority rules for batch deduction quantity

```
1. weightAmount (if set)           → use as-is (weight-based selling)
2. variant.batchDeductionQuantity  → multiply by order quantity
3. fallback                        → use order item quantity as-is
```

### Files to modify

| # | File | Change |
|---|------|--------|
| 1 | `ProductVariant.java` | Add `batchDeductionQuantity` field |
| 2 | `V112__add_batch_deduction_qty_to_variants.sql` | Migration |
| 3 | `ModifyOrderItemRequest.java` | Add `variantId`, `variantName` |
| 4 | `CreatePOSOrderRequest.java` | Add `variantId`, `variantName` to inner DTO |
| 5 | `POSOrderItemService.java` | Populate variantId, variantName, weightAmount, portionMultiplier on OrderItem |
| 6 | `InventoryService.java` | Look up variant, pass batchDeductionQuantity |
| 7 | `ProductionBatchService.java` | Accept batchDeductionQuantity in consumeForOrder |
| 8 | `Products.jsx` | Add batchDeductionQuantity field in variant form |

---

## Phase 2: Database & Entity

### Migration: `V112__add_batch_deduction_qty_to_variants.sql`

```sql
ALTER TABLE product_variants
    ADD COLUMN batch_deduction_quantity DECIMAL(10,4);

COMMENT ON COLUMN product_variants.batch_deduction_quantity
    IS 'How much to deduct from production batch per unit ordered. NULL = use order quantity or weight.';
```

### Entity: `ProductVariant.java` — add field

```java
@Column(name = "batch_deduction_quantity", precision = 10, scale = 4)
private BigDecimal batchDeductionQuantity;
```

---

## Phase 3: Backend Logic

### 3a. Order DTOs — add variant fields

**`ModifyOrderItemRequest.java`** — add:
```java
private Long variantId;
private String variantName;
```

**`CreatePOSOrderRequest.OrderItemRequest`** — add same fields.

### 3b. POS Order Item Service — populate variant + weight fields

**`POSOrderItemService.createOrderItem()`** — fix missing field assignments:
```java
newItem.setVariantId(request.getVariantId());
newItem.setVariantName(request.getVariantName());
newItem.setWeightAmount(request.getWeightAmount());
newItem.setWeightUnit(product.getWeightUnit());
newItem.setPortionMultiplier(request.getPortionMultiplier());

// Fix price calculation for weight-based
if (request.getWeightAmount() != null) {
    newItem.setTotalPrice(price.multiply(request.getWeightAmount()).multiply(BigDecimal.valueOf(request.getQuantity())));
} else if (request.getPortionMultiplier() != null) {
    newItem.setTotalPrice(price.multiply(request.getPortionMultiplier()).multiply(BigDecimal.valueOf(request.getQuantity())));
}
```

### 3c. Inventory Service — pass variant deduction quantity

**`InventoryService.deductIngredientsForOrder()`** — update production batch block:
```java
if (product != null && Boolean.TRUE.equals(product.getUsesProductionBatch())) {
    // Look up variant's batch deduction quantity
    BigDecimal batchDeductionQty = null;
    if (item.getVariantId() != null) {
        ProductVariant variant = productVariantRepository.findById(item.getVariantId()).orElse(null);
        if (variant != null && variant.getBatchDeductionQuantity() != null) {
            batchDeductionQty = variant.getBatchDeductionQuantity();
        }
    }

    productionBatchService.consumeForOrder(
            product.getId(), item.getQuantity(),
            item.getWeightAmount(), batchDeductionQty,
            order.getId(), item.getId());
}
```

### 3d. Production Batch Service — use variant deduction quantity

**`ProductionBatchService.consumeForOrder()`** — updated signature and logic:
```java
public BigDecimal consumeForOrder(Long productId, Integer itemQuantity,
                                  BigDecimal weightAmount, BigDecimal batchDeductionQty,
                                  Long orderId, Long orderItemId) {
    BigDecimal quantity;
    if (weightAmount != null && weightAmount.compareTo(BigDecimal.ZERO) > 0) {
        // Weight-based: use weight directly
        quantity = weightAmount;
    } else if (batchDeductionQty != null && batchDeductionQty.compareTo(BigDecimal.ZERO) > 0) {
        // Variant with batch deduction: multiply by order quantity
        quantity = batchDeductionQty.multiply(BigDecimal.valueOf(itemQuantity != null ? itemQuantity : 1));
    } else {
        // Fallback: use order quantity
        quantity = BigDecimal.valueOf(itemQuantity != null ? itemQuantity : 1);
    }
    // ... rest unchanged
}
```

---

## Phase 4: Frontend

### 4a. Variant form — add batchDeductionQuantity field

**`Products.jsx`** — in `variantFormData` state:
```javascript
batchDeductionQuantity: ''
```

In the variant form, conditionally show when product has `usesProductionBatch`:
```jsx
{selectedProduct?.usesProductionBatch && (
  <div className="space-y-2">
    <Label>Batch Deduction Qty</Label>
    <Input type="number" step="0.01" ... />
    <p className="text-xs text-muted-foreground">
      How many units to deduct from the production batch per order.
      e.g., 5 for a 5-piece portion.
    </p>
  </div>
)}
```

### 4b. POS — pass variantId in order requests

Ensure the POS frontend passes `variantId` and `variantName` when adding items with variants to the order.

---

## Phase 5: Testing

### Unit Tests
1. **ProductionBatchServiceTest** — add tests:
   - `consumeForOrder` with batchDeductionQty=5, quantity=2 → deducts 10
   - `consumeForOrder` with batchDeductionQty=null → falls back to quantity
   - `consumeForOrder` with weightAmount → ignores batchDeductionQty

2. **InventoryServiceTest** — add test:
   - Production batch item with variant → passes correct deduction qty

### Integration Tests
3. **End-to-end flow**: create batch → order with variant → verify correct deduction

---

## Phase 6: Documentation

Update `docs/production-batch-system-plan.md` with:
- Variant-based deduction section
- Updated API examples showing variant ordering
- Priority rules for deduction quantity

---

## Example Flow After Implementation

```
1. Create Product "Manti" (usesProductionBatch=true)
2. Add Variants:
   - "1 Piece"   → price: 5000,  batchDeductionQuantity: 1
   - "1 Portion" → price: 20000, batchDeductionQuantity: 5
   - "By Weight"  → price: 80000/kg, batchDeductionQuantity: null

3. Create Production Batch: 100 pieces of Manti

4. Customer orders:
   - 2x "1 Piece"   → deducts 2  (1 × 2)  → remaining: 98
   - 1x "1 Portion"  → deducts 5  (5 × 1)  → remaining: 93
   - 0.5kg "By Weight" → deducts 0.5 (weight) → remaining: 92.5

Total: 16 files modified/created
```
