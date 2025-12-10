import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * POS Store - Centralized state management for POS operations
 * Persists cart and order data to localStorage for session recovery
 */
const usePOSStore = create(
  persist(
    (set, get) => ({
      // Order State
      currentOrder: {
        id: null,
        orderNumber: null,
        type: null, // 'DELIVERY' | 'TAKEAWAY' | 'DINE_IN'
        items: [],
        subtotal: 0,
        tax: 0,
        deliveryFee: 0,
        total: 0,
        notes: '',
      },

      // Customer Information
      customer: {
        id: null,
        name: '',
        phone: '',
        email: '',
        // For delivery
        address: null,
        deliveryInstructions: '',
        // For dine-in
        tableNumber: null,
        guestCount: null,
      },

      // Payment State
      payment: {
        method: null, // 'CASH' | 'CARD' | 'MOBILE' | 'SPLIT'
        amountTendered: 0,
        changeDue: 0,
        status: 'PENDING', // 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
      },

      // UI State
      ui: {
        currentScreen: 'start', // 'start' | 'menu' | 'modifiers' | 'details' | 'cart' | 'payment' | 'confirmation'
        isLoading: false,
        error: null,
        selectedCategory: null,
        selectedProduct: null,
      },

      // Menu Cache
      menu: {
        categories: [],
        products: [],
        lastFetched: null,
      },

      // Actions: Order Management
      startNewOrder: (type) => set((state) => ({
        currentOrder: {
          ...state.currentOrder,
          id: `temp-${Date.now()}`,
          type,
          items: [],
          subtotal: 0,
          tax: 0,
          deliveryFee: type === 'DELIVERY' ? 5.00 : 0,
          total: 0,
          notes: '',
        },
        ui: { ...state.ui, currentScreen: 'menu' },
      })),

      addItemToCart: (product, modifiers = [], quantity = 1) => set((state) => {
        const itemPrice = product.price + modifiers.reduce((sum, mod) => sum + mod.price, 0);
        const item = {
          id: `${product.id}-${Date.now()}`,
          productId: product.id,
          name: product.name,
          basePrice: product.price,
          modifiers,
          quantity,
          itemTotal: itemPrice * quantity,
          notes: '',
        };

        const items = [...state.currentOrder.items, item];
        const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
        const tax = subtotal * 0.08; // 8% tax
        const deliveryFee = state.currentOrder.deliveryFee;
        const total = subtotal + tax + deliveryFee;

        return {
          currentOrder: {
            ...state.currentOrder,
            items,
            subtotal,
            tax,
            total,
          },
        };
      }),

      updateItemQuantity: (itemId, quantity) => set((state) => {
        const items = state.currentOrder.items.map(item => {
          if (item.id === itemId) {
            const basePrice = (item.basePrice + item.modifiers.reduce((sum, mod) => sum + mod.price, 0));
            return {
              ...item,
              quantity,
              itemTotal: basePrice * quantity,
            };
          }
          return item;
        });

        const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
        const tax = subtotal * 0.08;
        const deliveryFee = state.currentOrder.deliveryFee;
        const total = subtotal + tax + deliveryFee;

        return {
          currentOrder: {
            ...state.currentOrder,
            items,
            subtotal,
            tax,
            total,
          },
        };
      }),

      removeItemFromCart: (itemId) => set((state) => {
        const items = state.currentOrder.items.filter(item => item.id !== itemId);
        const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
        const tax = subtotal * 0.08;
        const deliveryFee = state.currentOrder.deliveryFee;
        const total = subtotal + tax + deliveryFee;

        return {
          currentOrder: {
            ...state.currentOrder,
            items,
            subtotal,
            tax,
            total,
          },
        };
      }),

      updateOrderNotes: (notes) => set((state) => ({
        currentOrder: {
          ...state.currentOrder,
          notes,
        },
      })),

      clearCart: () => set((state) => ({
        currentOrder: {
          ...state.currentOrder,
          items: [],
          subtotal: 0,
          tax: 0,
          total: state.currentOrder.deliveryFee,
        },
      })),

      // Actions: Customer Management
      setCustomerInfo: (customer) => set({ customer: { ...get().customer, ...customer } }),

      clearCustomerInfo: () => set({
        customer: {
          id: null,
          name: '',
          phone: '',
          email: '',
          address: null,
          deliveryInstructions: '',
          tableNumber: null,
          guestCount: null,
        },
      }),

      // Actions: Payment Management
      setPaymentMethod: (method) => set((state) => ({
        payment: { ...state.payment, method },
      })),

      setAmountTendered: (amount) => set((state) => {
        const changeDue = amount - state.currentOrder.total;
        return {
          payment: {
            ...state.payment,
            amountTendered: amount,
            changeDue: changeDue >= 0 ? changeDue : 0,
          },
        };
      }),

      setPaymentStatus: (status) => set((state) => ({
        payment: { ...state.payment, status },
      })),

      resetPayment: () => set({
        payment: {
          method: null,
          amountTendered: 0,
          changeDue: 0,
          status: 'PENDING',
        },
      }),

      // Actions: UI Management
      setCurrentScreen: (screen) => set((state) => ({
        ui: { ...state.ui, currentScreen: screen },
      })),

      setLoading: (isLoading) => set((state) => ({
        ui: { ...state.ui, isLoading },
      })),

      setError: (error) => set((state) => ({
        ui: { ...state.ui, error },
      })),

      clearError: () => set((state) => ({
        ui: { ...state.ui, error: null },
      })),

      setSelectedCategory: (categoryId) => set((state) => ({
        ui: { ...state.ui, selectedCategory: categoryId },
      })),

      setSelectedProduct: (product) => set((state) => ({
        ui: { ...state.ui, selectedProduct: product },
      })),

      // Actions: Menu Management
      setMenuData: (categories, products) => set({
        menu: {
          categories,
          products,
          lastFetched: new Date().toISOString(),
        },
      }),

      // Actions: Complete Order & Reset
      completeOrder: () => set((state) => {
        // Keep order number for confirmation screen
        const orderNumber = state.currentOrder.orderNumber;
        return {
          currentOrder: {
            id: null,
            orderNumber,
            type: null,
            items: [],
            subtotal: 0,
            tax: 0,
            deliveryFee: 0,
            total: 0,
            notes: '',
          },
          customer: {
            id: null,
            name: '',
            phone: '',
            email: '',
            address: null,
            deliveryInstructions: '',
            tableNumber: null,
            guestCount: null,
          },
          payment: {
            method: null,
            amountTendered: 0,
            changeDue: 0,
            status: 'PENDING',
          },
          ui: {
            ...state.ui,
            currentScreen: 'confirmation',
          },
        };
      }),

      resetPOS: () => set({
        currentOrder: {
          id: null,
          orderNumber: null,
          type: null,
          items: [],
          subtotal: 0,
          tax: 0,
          deliveryFee: 0,
          total: 0,
          notes: '',
        },
        customer: {
          id: null,
          name: '',
          phone: '',
          email: '',
          address: null,
          deliveryInstructions: '',
          tableNumber: null,
          guestCount: null,
        },
        payment: {
          method: null,
          amountTendered: 0,
          changeDue: 0,
          status: 'PENDING',
        },
        ui: {
          currentScreen: 'start',
          isLoading: false,
          error: null,
          selectedCategory: null,
          selectedProduct: null,
        },
      }),
    }),
    {
      name: 'pos-storage', // localStorage key
      partialPersist: (state) => ({
        // Only persist certain parts
        currentOrder: state.currentOrder,
        customer: state.customer,
        menu: state.menu,
      }),
    }
  )
);

export default usePOSStore;
