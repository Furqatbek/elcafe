import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { inventoryAPI, restaurantAPI, supplierAPI } from '../services/api';

const InventoryContext = createContext(null);

export function InventoryProvider({ children }) {
  // Common state
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);

  // Ingredients state (shared across multiple pages)
  const [ingredients, setIngredients] = useState([]);

  // Suppliers state (shared across multiple pages)
  const [suppliers, setSuppliers] = useState([]);

  // Load restaurants on mount
  useEffect(() => {
    loadRestaurants();
  }, []);

  // Load data when restaurant changes
  useEffect(() => {
    if (selectedRestaurant) {
      loadIngredients();
      loadSuppliers();
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantList = response.data.data?.content || response.data.data || [];
      setRestaurants(restaurantList);
      if (restaurantList.length > 0) {
        setSelectedRestaurant(restaurantList[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadIngredients = useCallback(async () => {
    if (!selectedRestaurant) return;
    setLoading(true);
    try {
      const response = await inventoryAPI.getIngredients(selectedRestaurant);
      setIngredients(response.data.data || []);
    } catch (error) {
      console.error('Failed to load ingredients:', error);
    } finally {
      setLoading(false);
    }
  }, [selectedRestaurant]);

  const loadSuppliers = useCallback(async () => {
    if (!selectedRestaurant) return;
    try {
      const response = await supplierAPI.getAll(selectedRestaurant);
      setSuppliers(response.data.data || []);
    } catch (error) {
      console.error('Failed to load suppliers:', error);
    }
  }, [selectedRestaurant]);

  const value = {
    // State
    restaurants,
    selectedRestaurant,
    setSelectedRestaurant,
    loading,
    setLoading,
    ingredients,
    setIngredients,
    suppliers,
    setSuppliers,

    // Actions
    loadRestaurants,
    loadIngredients,
    loadSuppliers,
  };

  return (
    <InventoryContext.Provider value={value}>
      {children}
    </InventoryContext.Provider>
  );
}

export function useInventory() {
  const context = useContext(InventoryContext);
  if (!context) {
    throw new Error('useInventory must be used within an InventoryProvider');
  }
  return context;
}

export default InventoryContext;
