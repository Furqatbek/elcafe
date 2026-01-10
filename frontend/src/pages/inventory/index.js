// Re-export inventory components
// These are aliases pointing to the main Inventory page for now
// Can be split into separate components later if needed

import Inventory from '../Inventory';

// Export the same component under different names for route compatibility
export const InventoryIngredients = Inventory;
export const InventoryRecipes = Inventory;
export const InventoryExpiry = Inventory;
export const InventoryStockCounts = Inventory;
export const InventoryWaste = Inventory;
export const InventorySuppliers = Inventory;
export const InventoryAlerts = Inventory;
export const InventoryValuation = Inventory;
