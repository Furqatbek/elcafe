# Inventory Categories & Search Filter — Implementation Plan

## Problem

The packaging rule dropdown shows all 1000+ inventory items in a flat list. Finding "Plastic Bag" among meat, vegetables, spices, and chemicals is impossible. The inventory system has no grouping mechanism at all.

## Solution

Two complementary features:
1. **Search filter** on all ingredient dropdowns — instant, frontend-only
2. **Category field** on ingredients — backend, enables filtering/grouping everywhere

---

## Part A: Search Filter on Dropdowns (Frontend Only)

### What Changes

Every ingredient dropdown (packaging rules, waste recording, production batch inputs, etc.) gets a search input that filters the list as you type.

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/pages/Products.jsx` | Packaging rule dropdown: add search input |
| `frontend/src/pages/inventory/ProductionBatches.jsx` | Add input dialog dropdown: add search |
| `frontend/src/pages/inventory/InventoryWaste.jsx` | Waste recording dropdown: add search |

### Implementation

Replace the native `<select>` with a searchable pattern:

```jsx
<div className="space-y-2">
  <Input
    placeholder="Search ingredients..."
    value={ingredientSearch}
    onChange={(e) => setIngredientSearch(e.target.value)}
  />
  <select className="w-full border rounded-md px-3 py-2 text-sm bg-background max-h-48 overflow-y-auto">
    {ingredients
      .filter(ing => ing.name.toLowerCase().includes(ingredientSearch.toLowerCase()))
      .map(ing => (
        <option key={ing.id} value={ing.id}>{ing.name} ({ing.currentStock} {ing.unit})</option>
      ))}
  </select>
</div>
```

Or create a reusable `<SearchableSelect>` component.

### Effort: ~30 minutes, no backend changes

---

## Part B: Ingredient Categories (Full Stack)

### Phase 1: Database & Entity

#### Migration: `V117__add_category_to_ingredients.sql`

```sql
ALTER TABLE inventory_ingredients
    ADD COLUMN category VARCHAR(50) DEFAULT 'OTHER';

CREATE INDEX idx_ingredients_category ON inventory_ingredients(category);

COMMENT ON COLUMN inventory_ingredients.category IS 'Ingredient category for filtering and grouping';
```

#### Enum: `IngredientCategory.java`

Location: `modules/inventory/enums/`

```java
public enum IngredientCategory {
    MEAT("Meat", "Beef, chicken, lamb, fish"),
    DAIRY("Dairy", "Milk, cheese, butter, cream"),
    VEGETABLE("Vegetables", "Fresh vegetables and greens"),
    FRUIT("Fruits", "Fresh fruits"),
    GRAIN("Grains", "Rice, flour, bread, pasta"),
    SPICE("Spices", "Spices, herbs, seasonings"),
    OIL("Oils & Fats", "Cooking oils, butter, margarine"),
    BEVERAGE("Beverages", "Drinks, juices, water, soda"),
    SAUCE("Sauces", "Sauces, dressings, condiments"),
    PACKAGING("Packaging", "Bags, bowls, cups, utensils, napkins"),
    CLEANING("Cleaning", "Cleaning supplies, chemicals"),
    OTHER("Other", "Uncategorized items");

    private final String label;
    private final String description;
}
```

#### Entity: `Ingredient.java` — add field

```java
@Enumerated(EnumType.STRING)
@Column(length = 50)
@Builder.Default
private IngredientCategory category = IngredientCategory.OTHER;
```

### Phase 2: Repository

#### `InventoryIngredientRepository.java` — add query

```java
List<Ingredient> findByRestaurantIdAndCategory(Long restaurantId, IngredientCategory category);
List<Ingredient> findByRestaurantIdAndCategoryAndActiveTrue(Long restaurantId, IngredientCategory category);
```

### Phase 3: API Updates

#### Modify: `InventoryIngredientController.java`

Add optional `category` query parameter to the list endpoint:

```java
@GetMapping
public ResponseEntity<...> getIngredients(
    @RequestParam Long restaurantId,
    @RequestParam(required = false) IngredientCategory category) {
    // If category provided, filter by it
}
```

Add endpoint to list available categories:

```java
@GetMapping("/categories")
public ResponseEntity<...> getCategories() {
    return Arrays.asList(IngredientCategory.values());
}
```

#### Modify: `IngredientRequest.java` (DTO)

Add `category` field so it can be set when creating/updating ingredients.

### Phase 4: Frontend — Ingredient Management

#### Modify: `InventoryIngredients.jsx`

1. **Category dropdown** in create/edit ingredient form
2. **Category filter tabs** above the ingredient list (All | Meat | Dairy | ... | Packaging)
3. **Category badge** on each ingredient row in the list
4. **Bulk categorize** — select multiple ingredients → assign category

```
┌─────────────────────────────────────────────────────┐
│ Ingredients                    [+ Add Ingredient]    │
├─────────────────────────────────────────────────────┤
│ [All] [Meat] [Dairy] [Veg] [Spice] [Packaging] ... │
├─────────────────────────────────────────────────────┤
│ Beef (Meat)        │ 50 kg  │ Low Stock │ 80,000/kg │
│ Flour (Grain)      │ 200 kg │ OK        │ 5,000/kg  │
│ Plastic Bowl (Pkg) │ 300 pc │ OK        │ 200/pc    │
│ Bag (Packaging)    │ 500 pc │ OK        │ 500/pc    │
└─────────────────────────────────────────────────────┘
```

### Phase 5: Frontend — Packaging Rule Dropdown

#### Modify: `Products.jsx` packaging dialog

Add category filter above the ingredient search:

```
┌────────────────────────────────┐
│ Add Packaging Item             │
│                                │
│ Category: [Packaging ▼]        │
│ Search: [bag______]            │
│ ┌────────────────────────────┐ │
│ │ Plastic Bag (500 pieces)   │ │
│ │ Paper Bag (200 pieces)     │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

When "Packaging" category is selected, only packaging items show. The search further narrows results.

### Phase 6: i18n

```json
{
  "inventory.category": "Category",
  "inventory.categories.MEAT": "Meat",
  "inventory.categories.DAIRY": "Dairy",
  "inventory.categories.VEGETABLE": "Vegetables",
  "inventory.categories.FRUIT": "Fruits",
  "inventory.categories.GRAIN": "Grains",
  "inventory.categories.SPICE": "Spices",
  "inventory.categories.OIL": "Oils & Fats",
  "inventory.categories.BEVERAGE": "Beverages",
  "inventory.categories.SAUCE": "Sauces",
  "inventory.categories.PACKAGING": "Packaging",
  "inventory.categories.CLEANING": "Cleaning",
  "inventory.categories.OTHER": "Other",
  "inventory.allCategories": "All Categories",
  "inventory.filterByCategory": "Filter by category",
  "packaging.filterCategory": "Filter by category",
  "packaging.searchIngredient": "Search ingredients..."
}
```

### Phase 7: Testing

- `InventoryIngredientRepositoryTest` — findByCategory queries
- `PackagingServiceTest` — verify filtering works (existing tests still pass)

---

## Implementation Order

| Step | Part | Files | Description |
|------|------|-------|-------------|
| 1 | A | Products.jsx, ProductionBatches.jsx, InventoryWaste.jsx | Search filter on dropdowns (frontend only) |
| 2 | B.1 | V117 migration + IngredientCategory enum + Ingredient.java | Database + entity |
| 3 | B.2 | InventoryIngredientRepository.java | Category queries |
| 4 | B.3 | InventoryIngredientController.java + IngredientRequest.java | API updates |
| 5 | B.4 | InventoryIngredients.jsx | Category in create/edit + filter tabs + badges |
| 6 | B.5 | Products.jsx packaging dialog | Category filter in packaging dropdown |
| 7 | B.6 | en.json, ru.json, uz.json | i18n keys |
| 8 | B.7 | Test files | Repository + service tests |

**Part A: 3 files modified, ~30 min**
**Part B: ~6 new/modified files + 1 migration + ~10 tests**
**Total: ~10 files + 1 migration + ~10 tests**

---

## Data Migration (Optional)

After deploying, the admin can bulk-categorize existing ingredients. Or create a one-time migration:

```sql
-- Auto-categorize common packaging items by name pattern
UPDATE inventory_ingredients SET category = 'PACKAGING'
WHERE LOWER(name) LIKE '%bag%' OR LOWER(name) LIKE '%bowl%'
   OR LOWER(name) LIKE '%cup%' OR LOWER(name) LIKE '%spoon%'
   OR LOWER(name) LIKE '%fork%' OR LOWER(name) LIKE '%napkin%'
   OR LOWER(name) LIKE '%straw%' OR LOWER(name) LIKE '%container%'
   OR LOWER(name) LIKE '%lid%' OR LOWER(name) LIKE '%box%';

-- Auto-categorize meats
UPDATE inventory_ingredients SET category = 'MEAT'
WHERE LOWER(name) LIKE '%beef%' OR LOWER(name) LIKE '%chicken%'
   OR LOWER(name) LIKE '%lamb%' OR LOWER(name) LIKE '%fish%'
   OR LOWER(name) LIKE '%meat%';

-- Auto-categorize spices
UPDATE inventory_ingredients SET category = 'SPICE'
WHERE LOWER(name) LIKE '%salt%' OR LOWER(name) LIKE '%pepper%'
   OR LOWER(name) LIKE '%spice%' OR LOWER(name) LIKE '%herb%';
```

This is optional — can also be done manually via the admin UI.
