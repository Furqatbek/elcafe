# Semi-Finished Ingredients & Nested Recipes — Implementation Plan

## Context

A kitchen makes things that are not on the menu and are not bought either: a house sauce, a dough, a
broth, a marinade. They are made from raw ingredients and then go *into* dishes. Today the system has
no way to say so.

What exists:

- **Recipes** are strictly `Product → Ingredient` (`inventory_product_ingredients`). No recipe line can
  point at another recipe.
- **Production batches** cook a menu product ahead (a pot of soup) from raw ingredients and sell it by
  the portion. A batch's output is always a **menu product**, consumed only by **orders**. Nothing can
  produce an ingredient that another recipe then uses.

So "Burger = bun + patty + 30 g house sauce", where "house sauce = mayo + garlic + dill", has to be
flattened by hand into raw ingredients on every dish that uses the sauce. Change the sauce and every
one of those dishes has to be edited; any that are missed keep the old cost and the old stock deduction
without anyone being told.

### Problems found while preparing this plan

These exist today, with no nesting involved, and nesting makes every one of them worse. Phase 0 fixes them
first.

1. **No unit conversion anywhere.** A recipe line's `unit` is stored but only ever displayed; the
   quantity is silently treated as being in the ingredient's stock unit. The recipe editor accepts any
   unit (`RecipeController` create sets `recipe.setUnit(request.getUnit())` with no check), so "30 g" of
   an ingredient stocked in kg deducts **30 kg**. With nesting, the case becomes normal rather than
   rare: sauces are made per kg and used per gram.
2. **Two cost bases.** The ledger's COGS (`RevenueService.calculateItemCogs`) prices ingredients at
   `getCostPerUnit()`, the list price. The cost price the owner sees (`ProductCostService`) uses
   `getEffectiveCost()`, the weighted average. The same plate gets two different costs.
3. **Two recipe systems.** Pricing recommendations (`PricingStrategyService`) and the ingredient-usage
   part of inventory analytics (`InventoryAnalyticsService`) read the legacy `product_ingredients` table
   through `Product.getIngredients()`. Nothing has written to that table since recipes moved to the
   inventory module. Those two reports cannot see any recipe entered in the recipe editor, and could not
   see a nested one either.
4. **Waiter add-ons are deducted through an order with no id** (`WaiterOrderService`, the `tempOrder`
   built when items are added to an existing order). Their consumption rows are not linked to the real
   order, so cancelling that order does not put the stock back. Exploding nested recipes multiplies the
   number of rows affected.

---

## Decisions

**D1 — Nesting lives on the ingredient side, not product → product.**
A semi-finished item is an *ingredient* with a recipe of its own. Dishes keep pointing only at
ingredients, so every existing recipe, every reader and every report keeps its `product → ingredient`
shape. The alternative, products nested inside products, would put hidden "products" into the menu, the
POS grid and the partner feed, and a product cannot hold stock.

**D2 — A prepared ingredient is an ordinary ingredient.**
It has the same row in `inventory_ingredients`, stock, lots (`InventoryBatch`), valuation method,
expiry, waste, stock counts and low-stock alerts. The only difference is where its lots come from: the
kitchen instead of a supplier. Every existing stock feature works on it without changes. This rests on
two things already in the code. Lots are generic: supplier and PO reference are optional. And
`InventoryBatchService.createBatch` creates a costed lot, raises stock and sets the lot's expiry from
the ingredient's `defaultShelfLifeDays`. That is exactly what a purchase-order receipt does.

**D3 — Each prepared ingredient picks one of two stock modes.**

| Mode | Example | At sale time | Availability |
|---|---|---|---|
| `MADE_AHEAD` | House sauce, made 2 kg every morning | Deducts the **sauce's own stock** | Only as much as is made |
| `MADE_TO_ORDER` | Garlic butter, whipped per plate | Breaks it down: deducts **its components** | Whatever its components allow |

`MADE_AHEAD` acts as a firewall. It holds its own stock and its own cost, so a garlic shortage does not
sell out a burger while sauce is still in the tub.

**D4 — One resolver. No reader walks a recipe itself.**
Eight services read recipes today: Phase 3 switches the six that compute from them, Phase 4 the batch
pre-fill, and Phases 0a and 5 the recipe editor. Teaching each one about nesting would give eight
slightly different implementations. Instead a single `RecipeResolver` answers the three questions they
all ask:

- what has to leave stock to make *N* of a product,
- what that costs,
- which products depend on a given ingredient, at any depth.

**D5 — Recipe quantities are always stored in the component's stock unit.**
Conversion happens once, when a line is saved, and only within a dimension (g ↔ kg, ml ↔ L). A line
that crosses dimensions (pieces ↔ kg) is refused with a message naming both units. Readers never
convert. This also fixes problem 1.

**D6 — One cost rule, owned by the resolver.**
Raw ingredients are priced at `getEffectiveCost()` (weighted average, falling back to list price). A
prepared ingredient made ahead is priced at its own lots' weighted average, which is the actual cost of
the batches that made it, and falls back to the rolled-up recipe cost until it has lots. A prepared
ingredient made to order is priced at its rolled-up recipe cost. The ledger and the cost price use the
same number. This fixes problem 2, and it **changes posted COGS** for existing restaurants from list price
to weighted average. That change ships as its own reviewed step.

**D7 — Cycles are refused when saved, and guarded against when read.**
A line is refused if it would make an ingredient depend on itself at any depth, and depth is capped at
5. The resolver also tracks the path it is walking, so a cycle that slipped in anyway fails loudly
instead of recursing until the stack overflows.

**D8 — Every existing ingredient defaults to `RAW`.**
On a RAW-only recipe the resolver must return exactly what each reader computes today. A parity test
proves that before any reader is switched over (Phase 7). Existing data changes behaviour only where
someone marks an ingredient as prepared, with one deliberate exception: the COGS cost basis (D6), which
ships as its own step.

---

## Architecture

```
TODAY     Product ──recipe──▶ Raw ingredient ──▶ stock          (a sale deducts raw)

NEW       Product ──recipe──▶ Raw ingredient ─────────────────────▶ stock
                    │
                    └───────▶ Prepared ingredient (MADE_AHEAD) ───▶ its own stock ◀── prep batch
                    │                                                (lots at actual cost)   ▲
                    │                                                                         │
                    └───────▶ Prepared ingredient (MADE_TO_ORDER)                     raw ingredients
                                    │  no stock of its own
                                    └──recipe──▶ raw or prepared ingredients (≤ 5 levels)

          Every reader ──▶ RecipeResolver.explode / productCost / whereUsed
```

---

## Phase 0: Prerequisites

These fix live problems and stand on their own. Each is small and ships separately.

**0a. Unit normalisation in the recipe editor.** Add `UnitConversion` (mass: g, kg; volume: ml, L;
count: pcs), with the aliases the codebase already uses ("L", "liter", "piece", "pieces", "gram").
`RecipeController` create and update convert the entered quantity into the ingredient's stock unit and
refuse lines that cross dimensions. Existing rows are **reported, not converted**:
`GET /api/v1/inventory/recipes/unit-audit` lists lines whose `unit` differs from the ingredient's. There
is no way to know whether a stored "30" was meant as grams or kilos, so a person decides.

**0b. Route the two legacy readers to the live table.** `PricingStrategyService` and
`InventoryAnalyticsService` stop reading `Product.getIngredients()`. Once they go through the resolver
in Phase 3 they see nested recipes too. Dropping the legacy tables waits for a release after this (see
Follow-ups).

**0c. Link waiter add-on consumption to the real order.** Pass the order's id into the deduction instead
of building an id-less `tempOrder`, without making that throwaway order something that could ever be
saved over the real one. With a test that cancels an order with add-ons and sees the stock restored.

---

## Phase 1: Database & Entities

### Migration `V195__prepared_ingredients.sql`

```sql
ALTER TABLE inventory_ingredients
    ADD COLUMN kind           VARCHAR(20)   NOT NULL DEFAULT 'RAW',   -- RAW | PREPARED
    ADD COLUMN stock_mode     VARCHAR(20),                            -- MADE_AHEAD | MADE_TO_ORDER
    ADD COLUMN yield_quantity NUMERIC(12,4);                          -- in this ingredient's own unit

ALTER TABLE inventory_ingredients ADD CONSTRAINT chk_prepared_ingredient_shape CHECK (
    (kind = 'RAW'      AND stock_mode IS NULL     AND yield_quantity IS NULL) OR
    (kind = 'PREPARED' AND stock_mode IS NOT NULL AND yield_quantity > 0)
);

CREATE TABLE ingredient_recipe_items (
    id                       BIGSERIAL PRIMARY KEY,
    restaurant_id            BIGINT        NOT NULL REFERENCES restaurants(id),
    prepared_ingredient_id   BIGINT        NOT NULL REFERENCES inventory_ingredients(id) ON DELETE CASCADE,
    component_ingredient_id  BIGINT        NOT NULL REFERENCES inventory_ingredients(id),
    quantity                 NUMERIC(12,4) NOT NULL CHECK (quantity > 0),  -- component's stock unit
    optional                 BOOLEAN       NOT NULL DEFAULT FALSE,
    notes                    TEXT,
    created_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ingredient_recipe_item UNIQUE (prepared_ingredient_id, component_ingredient_id),
    CONSTRAINT chk_not_self CHECK (prepared_ingredient_id <> component_ingredient_id)
);

CREATE INDEX idx_ingredient_recipe_items_component ON ingredient_recipe_items(component_ingredient_id);
CREATE INDEX idx_ingredient_recipe_items_restaurant ON ingredient_recipe_items(restaurant_id);
```

The component index is what makes `whereUsed` cheap: it walks upward one level per query.

### Migration `V196__production_batch_output_ingredient.sql`

```sql
ALTER TABLE production_batches
    ADD COLUMN output_ingredient_id BIGINT REFERENCES inventory_ingredients(id);

ALTER TABLE production_batches ADD CONSTRAINT chk_batch_single_output
    CHECK (product_id IS NULL OR output_ingredient_id IS NULL);
```

`status` is already `VARCHAR`, so the new `STOCKED` value (Phase 4) needs no migration.

### Entities

1. **`Ingredient`**: add `kind` (`IngredientKind { RAW, PREPARED }`, default `RAW`), `stockMode`
   (`StockMode { MADE_AHEAD, MADE_TO_ORDER }`), `yieldQuantity`, and `isPrepared()` /
   `isMadeToOrder()` helpers. A made-to-order ingredient is saved with `trackInventory = false`,
   because it never holds stock of its own (see `explode`, Phase 2).
2. **`IngredientRecipeItem`** (new): prepared ingredient, component, quantity, optional,
   `restaurantId`, and `@Filter(name = "restaurantFilter")` like `Ingredient` and `ProductionBatch`.
3. **`ProductionBatch`**: add `outputIngredient` and the `STOCKED` status (the output moved into
   ingredient stock).

---

## Phase 2: The Resolver

New `RecipeResolver` in `modules/inventory/service/`. It is the only class that walks a recipe.

```java
/** What has to leave stock to make `quantity` of a product: ingredientId → quantity, stock units. */
Map<Long, BigDecimal> explode(Long productId, BigDecimal quantity);

/** Cost of one unit of an ingredient, raw or prepared. */
BigDecimal unitCost(Long ingredientId);

/** Cost of one portion of a product: Σ recipe line × unitCost. */
BigDecimal productCost(Long productId);

/** Every product depending on an ingredient, at any depth. */
Set<Long> whereUsed(Long ingredientId, Scope scope);   // Scope.COST | Scope.AVAILABILITY
```

### `explode`

```
for line in recipe(product), skipping optional:
    expand(line.ingredient, line.quantityRequired × quantity, path = [])

expand(ingredient, need, path):
    if ingredient ∈ path           → fail: cycle (D7 — should be impossible after save-time checks)
    if path.size ≥ MAX_DEPTH (5)   → fail: too deep
    if RAW or MADE_AHEAD           → result[ingredient] += need ; return
    // MADE_TO_ORDER: no stock of its own, so take its components instead
    scale = need / ingredient.yieldQuantity
    for item in recipeItems(ingredient), skipping optional:
        expand(item.component, item.quantity × scale, path + ingredient)
```

`hasStock` is never called on a made-to-order ingredient. That matters because made-to-order
ingredients do not track stock, and `Ingredient.hasStock` returns `true` for anything that does not
track stock. Asking it would mark every made-to-order sauce as always available.

### `unitCost`

```
RAW                         → ingredient.getEffectiveCost()
PREPARED, MADE_AHEAD        → its lots' weighted average if it has stock cost, else rolledUp(ingredient)
PREPARED, MADE_TO_ORDER     → rolledUp(ingredient)

rolledUp(i) = Σ (item.quantity × unitCost(item.component)) / i.yieldQuantity    (optional items skipped)
```

Results are memoised for the duration of one call, so a sauce used on three lines is costed once.

### `whereUsed`

Breadth-first upward through `ingredient_recipe_items` (one query per level, using the component
index), collecting products from `findByIngredientIdWithProduct` at each level.

- `Scope.COST` passes through every parent. Recomputing a dish whose cost turns out not to change is
  harmless.
- `Scope.AVAILABILITY` passes only through `MADE_TO_ORDER` parents. A made-ahead sauce in the tub keeps
  its dishes on sale whatever happens to garlic (D3).

### Saving recipe lines

`IngredientRecipeService` owns writes to `ingredient_recipe_items`:

- the component belongs to the same restaurant;
- the quantity is normalised (D5);
- the line would not create a cycle or exceed the depth limit (D7).

The cycle check runs under a per-restaurant transaction lock, so that two managers each adding half
of a cycle at the same moment cannot both pass. Adding A→B and B→A touch different rows, so
`Ingredient`'s `@Version` would not catch it.

---

## Phase 3: Switch the Readers

Each reader is switched with its own tests, one at a time, after the parity test (Phase 7) is green.

| Reader | Today | After |
|---|---|---|
| `InventoryService.calculateRequiredIngredients` (sale deduction) | recipe × qty | `explode` |
| `InventoryService.canMakeProduct`, `getMissingIngredients` | recipe | `explode`, then `hasStock` per entry |
| `InventoryService.deductIngredientsForProduct` | recipe | `explode` |
| `InventoryReservationService` (reserve, canReserve, max quantity) | recipe | `explode` |
| `POSOrderService.getProductAvailability` | recipe | `explode` |
| `ProductAvailabilityRecomputer` (partner sold-out) | recipe + one-level reverse lookup | `explode` + `whereUsed(AVAILABILITY)` |
| `ProductCostService` (cost price, cascade on cost change) | recipe + one-level reverse lookup | `productCost` + `whereUsed(COST)` |
| `RevenueService.calculateItemCogs` (ledger COGS) | recipe × `getCostPerUnit` | `productCost` (D6) |
| `PricingStrategyService` (legacy table) | `Product.getIngredients()` | `productCost` |
| `InventoryAnalyticsService` ingredient usage (legacy table) | `Product.getIngredients()` | `explode` |

`RevenueService` posts COGS from an `@Async` thread holding a detached order with no session. The
resolver takes product ids and quantities only and loads everything itself, so it never touches the
order's lazy state. The batch-product COGS path (`ProductionBatchConsumption`) is unchanged.

The switch to `getEffectiveCost` in `RevenueService` is the one deliberate behaviour change (D6). It
is its own commit, and the release notes say that COGS moves from list price to weighted average.

---

## Phase 4: Production Batches That Make a Prepared Ingredient

A batch now has one of two outputs: a **menu product**, which works exactly as today, or a **prepared
ingredient**, which is new.

- **Create:** with `outputIngredientId`, the inputs are pre-filled from the ingredient's recipe items
  **scaled by planned output ÷ yield**. "Make 3 kg of a 1 kg recipe" pre-fills three times the
  quantities. Product batches keep their current unscaled pre-fill (see Follow-ups).
- **Complete:** the inputs are consumed through `consumeWithValuation`, as now. Then the output is
  *received* the way `PurchaseOrderService.receivePurchaseOrder` receives a delivery:
  `InventoryBatchService.createBatch` with the batch's **actual** cost per unit (real yield included),
  no supplier, and the batch number as the reference; then `valuationService.recalculateWAC`. The
  batch's `remainingQuantity` goes to 0 and its status to `STOCKED`.
  - **Not `InventoryService.addStock`.** It raises the stock number and the weighted average but
    creates **no lot**. A purchase order uses it only as a fallback when lot creation fails. For a
    restaurant on FIFO or FEFO, prepared stock added that way would be invisible to valuation.
- **Expiry:** `createBatch` sets the lot's expiry from the ingredient's existing `defaultShelfLifeDays`,
  so FEFO, expiry alerts and waste recording work on prepared stock with no new code.
- **No ledger entry:** turning raw stock into prepared stock moves value from one inventory asset to
  another, so nothing is posted.

---

## Phase 5: API

```
PUT    /api/v1/inventory/ingredients/{id}                  + kind, stockMode, yieldQuantity
GET    /api/v1/inventory/ingredients/{id}/recipe           its recipe items, unit cost, depth
POST   /api/v1/inventory/ingredients/{id}/recipe           add a component (normalised, cycle-checked)
PUT    /api/v1/inventory/ingredients/{id}/recipe/{itemId}
DELETE /api/v1/inventory/ingredients/{id}/recipe/{itemId}
GET    /api/v1/inventory/ingredients/{id}/where-used       dishes and prepared items using it, any depth
GET    /api/v1/inventory/recipes/unit-audit                lines whose unit differs from stock unit (0a)
POST   /api/v1/inventory/production-batches                + outputIngredientId
```

Errors use the existing envelope, with messages that say what to do: "House sauce already uses Aioli,
and Aioli uses House sauce — a recipe can't contain itself", and "Garlic is stocked in pcs — a recipe
can't use it by weight".

---

## Phase 6: Frontend

- **`pages/inventory/InventoryIngredients.jsx`**: a Raw / Prepared choice; for Prepared, yield and
  stock mode, with a one-line explanation of each mode.
- **Prepared ingredient detail** (new): its recipe card, where a component can itself be a prepared
  ingredient; the rolled-up unit cost; "used in" (where-used).
- **`pages/inventory/InventoryRecipes.jsx`**: the component picker includes prepared ingredients,
  marked "Prep". The unit picker offers only units of the component's dimension and shows the
  conversion ("30 g = 0.030 kg"). The stored quantity is normalised, and the UI converts back for
  display, so no extra columns are needed.
- **`pages/inventory/ProductionBatches.jsx`**: the output is a dish or a prepared ingredient.
- **i18n**: every new string in `en`, `ru` and `uz`, enforced by `locales.test.js`.

---

## Phase 7: Tests

**Parity test, first and most important.** Seed a set of flat recipes (all RAW) and assert that
`explode` and `productCost` match what each reader computes today, for every product. This proves D8.
Nothing is switched until it passes.

**Resolver unit tests:**

- A flat recipe returns exactly what it does today.
- One level and two levels of nesting.
- `MADE_AHEAD` stops the explosion; `MADE_TO_ORDER` expands it.
- Yield scaling.
- Optional items are skipped at every level.
- A cycle is refused at save, and fails loudly if it is present at read time.
- The depth limit applies.
- g → kg converts; pcs → kg is refused.
- Cost rolls up across levels; made-ahead cost comes from its lots.
- `whereUsed(COST)` passes through everything; `whereUsed(AVAILABILITY)` stops at `MADE_AHEAD`.
- A component from another restaurant is refused.

**Integration tests (`@DataJpaTest` / service):**

- Selling a burger with a made-to-order sauce deducts garlic and mayo.
- With a made-ahead sauce, it deducts the sauce.
- Completing a prep batch creates a sauce lot at the actual cost.
- The partner sold-out flips when garlic runs out for a made-to-order sauce, and does not for a
  made-ahead sauce with stock.
- Cancelling an order puts back exactly what was taken, at every level.

**Mutation testing**, as in recent work: break each rule above, confirm a test fails, then restore from
file backups (never `git checkout`). Apply a mutation only if its target string occurs exactly once.

---

## Verification

On the demo box:

1. Create **House sauce**: prepared, made ahead, yield 1 kg = 700 g mayo + 50 g garlic + 30 g dill.
   Check that its rolled-up unit cost is right.
2. Add 30 g of House sauce to **Burger**. Check that the burger's cost price includes the sauce's 30 g.
3. Run a prep batch of 2 kg. A sauce lot appears at the batch's actual cost, and mayo, garlic and dill
   go down.
4. Sell 3 burgers. Sauce stock goes down by 90 g, and raw garlic does not move.
5. Switch the sauce to made to order, then sell a burger. Now garlic moves, and the sauce does not.
6. Run garlic out. The burger goes sold-out to partners in made-to-order mode, and stays on sale in
   made-ahead mode while sauce remains.
7. Try to add House sauce to its own components, through another prepared ingredient. The save is
   refused with the cycle message.
8. Open the unit audit and check that it lists any recipe lines entered in the wrong unit before 0a.

---

## Implementation Order

| Step | What | Size |
|---|---|---|
| 1 | Phase 0a: unit normalisation and audit (fixes a live bug) | S |
| 2 | Phase 0b and 0c: legacy readers, waiter add-on linkage | S |
| 3 | Phase 1: migrations and entities (everything still `RAW`, no behaviour change) | S |
| 4 | Phase 2: resolver, saving rules, unit tests and the parity test | M |
| 5 | Phase 3: switch readers one at a time, `RevenueService`'s cost basis last and alone | M |
| 6 | Phase 4: prep batches that produce a prepared ingredient | M |
| 7 | Phase 5 and 6: API and frontend | M |
| 8 | Phase 7 integration tests, verification on the demo box | S |

Steps 1 and 2 are worth shipping even if nothing after them is built.

---

## Open Questions (owner decisions)

1. **A made-ahead item runs out mid-service.** Should the dish go sold-out, or should it fall back to
   making the item to order? The proposed default is sold-out: stock stays honest, and nobody silently
   makes sauce at the pass.
2. **Can a prepared ingredient also be bought?** For example, ready-made sauce on a busy day. The model
   allows supplier lots on a prepared ingredient. Proposed default: yes.
3. **Purchase suggestions.** For a prepared ingredient below its reorder level, should the system
   suggest a prep batch ("make 2 kg of House sauce") instead of a purchase order? Proposed default:
   yes.
4. **Nesting depth limit.** Proposed: 5.
5. **COGS basis change** from list price to weighted average (D6). Confirm before step 5 ships.

---

## Follow-ups (out of scope here)

- **COGS from what stock actually cost when it left.** Use the valuation consumption rows that sales
  already write, with the resolver's cost only as the fallback when an order is costed before it is
  deducted. This is the same approach the batch-product COGS fix took.
- **Scaled pre-fill for product batches.** Today they copy the product's recipe unscaled, which is why
  a batch product's recipe ends up describing a whole pot.
- **Soups as prepared broth plus a portion.** A broth ingredient made ahead, with a dish recipe of
  0.4 L of broth, is a cleaner model than the batch-product path and has no pot-sized recipe in it.
  Existing batch products keep working; this would be an option, not a migration.
- **Drop the legacy `ingredients` and `product_ingredients` tables**, one release after 0b, once nothing
  reads them. Check first that no foreign keys still point at them.
- **Post waste and stock adjustments to the ledger.** The inventory module currently never touches it.

---

## Risks

- **Hot-path cost.** `explode` runs on every sale and the partner recomputer on every stock movement.
  Load all the recipe items an order needs in one query, memoise within a call, and walk `whereUsed`
  one query per level rather than one per row.
- **Tenant isolation.** A component from another restaurant must be impossible. The `@Filter`, a check
  in `IngredientRecipeService` and a test enforce it together.
- **Unit audit fallout.** Rows that 0a reports may have been "fixed" by hand already. An operator entering
  0.030 with unit "g" to get 30 g deducted would be double-converted by an automatic migration. That is
  why existing rows are reported, not converted.
