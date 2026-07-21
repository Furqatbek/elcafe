# Inventory Categories & Search Filter — Implementation Plan

> **STATUS: SHIPPED** — fully implemented (migration V117; `inventory/IngredientCategory` entity + `IngredientCategoryController` at `/api/v1/inventory/ingredient-categories`, wired into `InventoryIngredients.jsx`). Kept as a historical design record.

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

#### New Table: `ingredient_categories`

User-defined categories — no predefined enum. Restaurants create their own
categories (e.g., "Packaging", "Meat", "Spices", "Cleaning Supplies").

```sql
CREATE TABLE ingredient_categories (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_ingredient_cat_name ON ingredient_categories(restaurant_id, name);
```

#### New Entity: `IngredientCategory.java`

Location: `modules/inventory/entity/`

```java
@Entity
@Table(name = "ingredient_categories")
public class IngredientCategory {
    Long id;
    @ManyToOne Restaurant restaurant;
    String name;
    Integer sortOrder;
}
```

#### Entity: `Ingredient.java` — add field

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
private IngredientCategory category;
```

#### Migration column on ingredients:

```sql
ALTER TABLE inventory_ingredients
    ADD COLUMN category_id BIGINT REFERENCES ingredient_categories(id);
```

### Phase 2: Repository

#### `IngredientCategoryRepository.java` (new)

```java
List<IngredientCategory> findByRestaurantIdOrderBySortOrder(Long restaurantId);
```

#### `InventoryIngredientRepository.java` — add query

```java
List<Ingredient> findByRestaurantIdAndCategoryId(Long restaurantId, Long categoryId);
List<Ingredient> findByRestaurantIdAndCategoryIdAndActiveTrue(Long restaurantId, Long categoryId);
```

### Phase 3: API Updates

#### New: `IngredientCategoryController.java`

CRUD for user-defined categories:

```
GET    /api/v1/inventory/ingredient-categories?restaurantId={id}
POST   /api/v1/inventory/ingredient-categories
PUT    /api/v1/inventory/ingredient-categories/{id}
DELETE /api/v1/inventory/ingredient-categories/{id}
```

#### Modify: `InventoryIngredientController.java`

Add optional `categoryId` query parameter to the list endpoint:

```java
@GetMapping
public ResponseEntity<...> getIngredients(
    @RequestParam Long restaurantId,
    @RequestParam(required = false) Long categoryId) {
    // If categoryId provided, filter by it
}
```

#### Modify: `IngredientRequest.java` (DTO)

Add `categoryId` field so it can be set when creating/updating ingredients.

### Phase 4: Frontend — Ingredient Management

#### Modify: `InventoryIngredients.jsx`

1. **Category dropdown** in create/edit ingredient form (select from user-created categories)
2. **"Manage Categories" button** — opens dialog to create/edit/delete categories
3. **Category filter tabs** above the ingredient list (dynamically built from user's categories)
4. **Category badge** on each ingredient row in the list

```
┌─────────────────────────────────────────────────────────────┐
│ Ingredients            [Manage Categories] [+ Add Ingredient]│
├─────────────────────────────────────────────────────────────┤
│ [All] [Meat] [Spices] [Packaging] [Cleaning]  ← user-defined│
├─────────────────────────────────────────────────────────────┤
│ Beef (Meat)        │ 50 kg  │ Low Stock │ 80,000/kg         │
│ Flour              │ 200 kg │ OK        │ 5,000/kg          │
│ Plastic Bowl (Pkg) │ 300 pc │ OK        │ 200/pc            │
│ Bag (Packaging)    │ 500 pc │ OK        │ 500/pc            │
└─────────────────────────────────────────────────────────────┘

┌──────────────────────────────┐
│ Manage Categories            │
│──────────────────────────────│
│ Meat          [Edit] [Delete]│
│ Spices        [Edit] [Delete]│
│ Packaging     [Edit] [Delete]│
│ Cleaning      [Edit] [Delete]│
│                              │
│ [+ Add Category]             │
└──────────────────────────────┘
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
  "inventory.allCategories": "All Categories",
  "inventory.manageCategories": "Manage Categories",
  "inventory.addCategory": "Add Category",
  "inventory.editCategory": "Edit Category",
  "inventory.categoryName": "Category Name",
  "inventory.categoryNamePlaceholder": "e.g. Packaging, Meat, Spices",
  "inventory.noCategories": "No categories created yet",
  "inventory.deleteCategory": "Delete Category",
  "inventory.deleteCategoryConfirm": "Ingredients in this category will become uncategorized",
  "inventory.uncategorized": "Uncategorized",
  "inventory.filterByCategory": "Filter by category",
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

## User Flow

1. Admin goes to **Inventory → Ingredients** page
2. Clicks **"Manage Categories"** → creates: "Packaging", "Meat", "Spices", etc.
3. When creating/editing an ingredient, selects category from dropdown
4. Filter tabs update dynamically from user's categories
5. In **Products → Packaging Rules → Add**, dropdown pre-filters by category
6. Existing ingredients with no category show as "Uncategorized"
