import { createContext, useContext, useState, useCallback } from 'react';
import { inventoryAPI } from '../services/api';

const InventoryContext = createContext(null);

export function InventoryProvider({ children }) {
  const [ingredients, setIngredients] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);

  const loadIngredients = useCallback(async (restaurantId) => {
    if (!restaurantId) return;
    setLoading(true);
    setError(null);
    try {
      const response = await inventoryAPI.getIngredients(restaurantId);
      setIngredients(response.data.data || []);
    } catch (err) {
      console.error('Failed to load ingredients:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshIngredients = useCallback(() => {
    if (selectedRestaurant) {
      loadIngredients(selectedRestaurant);
    }
  }, [selectedRestaurant, loadIngredients]);

  const value = {
    ingredients,
    loading,
    error,
    selectedRestaurant,
    setSelectedRestaurant,
    loadIngredients,
    refreshIngredients,
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
