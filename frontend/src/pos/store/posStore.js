import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { posAPI } from '../../services/api';

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

      // Product Availability Cache (productId -> availability info)
      productAvailability: {},

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
        const tax = 0; // No tax
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
        const tax = 0; // No tax
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
        const tax = 0; // No tax
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

      fetchMenuData: async (restaurantId) => {
        set((s) => ({ ui: { ...s.ui, isLoading: true, error: null } }));

        try {
          // Fetch categories and products in parallel
          const [categoriesRes, productsRes] = await Promise.all([
            posAPI.getCategories(restaurantId),
            posAPI.getProducts(restaurantId),
          ]);

          const categories = categoriesRes.data.data || categoriesRes.data || [];
          const products = productsRes.data.data || productsRes.data || [];

          set({
            menu: {
              categories,
              products,
              lastFetched: new Date().toISOString(),
            },
            ui: { ...get().ui, isLoading: false },
          });

          return { success: true, categories, products };
        } catch (error) {
          const errorMessage = error.response?.data?.message || error.message || 'Failed to load menu';
          set((s) => ({
            ui: { ...s.ui, isLoading: false, error: errorMessage },
          }));
          return { success: false, error: errorMessage };
        }
      },

      // Actions: Product Availability
      checkProductAvailability: async (productId, restaurantId) => {
        try {
          const response = await posAPI.checkProductAvailability(productId, restaurantId);
          const availability = response.data.data;

          set((state) => ({
            productAvailability: {
              ...state.productAvailability,
              [productId]: availability,
            },
          }));

          return availability;
        } catch (error) {
          console.error('Failed to check product availability:', error);
          return null;
        }
      },

      checkAllProductsAvailability: async (products, restaurantId) => {
        try {
          // Check availability for all products in parallel
          const availabilityPromises = products.map((product) =>
            posAPI.checkProductAvailability(product.id, restaurantId)
              .then((res) => ({ productId: product.id, ...res.data.data }))
              .catch(() => ({ productId: product.id, available: true, stockStatus: 'UNKNOWN' }))
          );

          const results = await Promise.all(availabilityPromises);

          const availabilityMap = {};
          results.forEach((result) => {
            availabilityMap[result.productId] = result;
          });

          set({ productAvailability: availabilityMap });

          return availabilityMap;
        } catch (error) {
          console.error('Failed to check products availability:', error);
          return {};
        }
      },

      clearProductAvailability: () => set({ productAvailability: {} }),

      // Actions: Submit Order to Backend
      submitOrder: async (restaurantId) => {
        const state = get();
        set((s) => ({ ui: { ...s.ui, isLoading: true, error: null } }));

        try {
          // Map POS store data to backend API format
          const orderData = {
            restaurantId,
            orderType: state.currentOrder.type, // DELIVERY, TAKEAWAY, DINE_IN
            orderSource: 'WALK_IN',
            customerInfo: {
              name: state.customer.name,
              phone: state.customer.phone,
              email: state.customer.email || null,
            },
            items: state.currentOrder.items.map(item => ({
              productId: item.productId,
              quantity: item.quantity,
              price: item.basePrice,
              modifiers: item.modifiers?.map(mod => ({
                name: mod.name,
                price: mod.price,
              })) || [],
              notes: item.notes || null,
            })),
            deliveryInfo: state.currentOrder.type === 'DELIVERY' && state.customer.address ? {
              street: state.customer.address.street,
              city: state.customer.address.city,
              state: state.customer.address.state || null,
              zipCode: state.customer.address.zipCode || null,
              deliveryInstructions: state.customer.deliveryInstructions || null,
            } : null,
            dineInInfo: state.currentOrder.type === 'DINE_IN' ? {
              tableNumber: state.customer.tableNumber,
              guestCount: state.customer.guestCount,
            } : null,
            orderNotes: state.currentOrder.notes || null,
            paymentMethod: state.payment.method, // CASH, CARD, MOBILE
            subtotal: state.currentOrder.subtotal,
            tax: state.currentOrder.tax,
            deliveryFee: state.currentOrder.deliveryFee,
            total: state.currentOrder.total,
            amountTendered: state.payment.amountTendered || null,
            changeDue: state.payment.changeDue || null,
          };

          const response = await posAPI.createOrder(orderData);
          const orderNumber = response.data.data.orderNumber;

          set((s) => ({
            currentOrder: { ...s.currentOrder, orderNumber },
            ui: { ...s.ui, isLoading: false },
          }));

          return { success: true, orderNumber };
        } catch (error) {
          const errorMessage = error.response?.data?.message || error.message || 'Failed to submit order';
          set((s) => ({
            ui: { ...s.ui, isLoading: false, error: errorMessage },
          }));
          return { success: false, error: errorMessage };
        }
      },

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
