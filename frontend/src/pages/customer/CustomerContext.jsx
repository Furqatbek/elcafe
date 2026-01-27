import { createContext, useContext, useState, useEffect } from 'react';
import { selfServiceAPI } from '../../services/api';

const CustomerContext = createContext(null);

export function CustomerProvider({ children }) {
  const [session, setSession] = useState(null);
  const [cart, setCart] = useState({ items: [], itemCount: 0, total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Load session from localStorage on mount
  useEffect(() => {
    const savedToken = localStorage.getItem('self_service_token');
    if (savedToken) {
      validateSession(savedToken);
    } else {
      setLoading(false);
    }
  }, []);

  const validateSession = async (token) => {
    try {
      const response = await selfServiceAPI.getSession(token);
      setSession({ ...response.data, sessionToken: token });
      await loadCart(token);
    } catch (err) {
      console.error('Session validation failed:', err);
      // Only clear the session if the token we tried to validate is still the one in localStorage
      // This prevents a race condition where a new session was already created
      const currentToken = localStorage.getItem('self_service_token');
      if (currentToken === token) {
        localStorage.removeItem('self_service_token');
        setSession(null);
      }
    } finally {
      setLoading(false);
    }
  };

  const startSession = async (code) => {
    setLoading(true);
    setError(null);
    try {
      const response = await selfServiceAPI.startSession(code);
      const { sessionToken } = response.data;
      localStorage.setItem('self_service_token', sessionToken);
      setSession(response.data);
      setCart({ items: [], itemCount: 0, total: 0 });
      return response.data;
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to start session');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const loadCart = async (token = session?.sessionToken) => {
    if (!token) return;
    try {
      const response = await selfServiceAPI.getCart(token);
      setCart(response.data);
    } catch (err) {
      console.error('Failed to load cart:', err);
    }
  };

  const addToCart = async (data) => {
    if (!session?.sessionToken) throw new Error('No active session');
    try {
      await selfServiceAPI.addToCart(session.sessionToken, data);
      await loadCart();
    } catch (err) {
      throw err;
    }
  };

  const updateCartItem = async (itemId, quantity) => {
    if (!session?.sessionToken) throw new Error('No active session');
    try {
      await selfServiceAPI.updateCartItem(session.sessionToken, itemId, quantity);
      await loadCart();
    } catch (err) {
      throw err;
    }
  };

  const removeFromCart = async (itemId) => {
    if (!session?.sessionToken) throw new Error('No active session');
    try {
      await selfServiceAPI.removeFromCart(session.sessionToken, itemId);
      await loadCart();
    } catch (err) {
      throw err;
    }
  };

  const clearCart = async () => {
    if (!session?.sessionToken) throw new Error('No active session');
    try {
      await selfServiceAPI.clearCart(session.sessionToken);
      setCart({ items: [], itemCount: 0, total: 0 });
    } catch (err) {
      throw err;
    }
  };

  const submitOrder = async (orderData) => {
    if (!session?.sessionToken) throw new Error('No active session');
    try {
      const response = await selfServiceAPI.submitOrder(session.sessionToken, orderData);
      setCart({ items: [], itemCount: 0, total: 0 });
      return response.data;
    } catch (err) {
      throw err;
    }
  };

  const endSession = () => {
    localStorage.removeItem('self_service_token');
    setSession(null);
    setCart({ items: [], itemCount: 0, total: 0 });
  };

  return (
    <CustomerContext.Provider value={{
      session,
      cart,
      loading,
      error,
      startSession,
      loadCart,
      addToCart,
      updateCartItem,
      removeFromCart,
      clearCart,
      submitOrder,
      endSession,
    }}>
      {children}
    </CustomerContext.Provider>
  );
}

export function useCustomer() {
  const context = useContext(CustomerContext);
  if (!context) {
    throw new Error('useCustomer must be used within CustomerProvider');
  }
  return context;
}
