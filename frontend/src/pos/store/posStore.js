import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { posAPI, tablesAPI } from '../../services/api';

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
        serviceFeePercent: 0,
        serviceFee: 0,
        total: 0,
        notes: '',
      },

      // Kitchen Status (for confirmation screen polling)
      kitchenStatus: {
        orderId: null,
        kitchenOrderId: null,
        status: null, // PENDING, PREPARING, READY, PICKED_UP
        priority: null,
        assignedChef: null,
        estimatedMinutes: null,
        lastUpdated: null,
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
        tableIds: null,
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

      // Selected Tables (for dine-in orders - supports multiple tables)
      selectedTables: [],

      // Floor Plan Data
      floorPlan: {
        tables: [],
        sections: [],
        lastFetched: null,
      },

      // Active Order for modification/split (existing order from backend)
      activeOrder: null,

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
        selectedTables: [],
        // For DINE_IN, go to table selection first; otherwise, go to menu
        ui: { ...state.ui, currentScreen: type === 'DINE_IN' ? 'tables' : 'menu' },
      })),

      addItemToCart: (product, modifiers = [], quantity = 1) => set((state) => {
        const itemPrice = product.price + modifiers.reduce((sum, mod) => sum + mod.price, 0);

        // Create a unique key based on product ID and modifiers to identify duplicates
        const modifierKey = modifiers.map(m => `${m.id || m.name}`).sort().join(',');

        // Check if this exact product + modifier combination already exists
        const existingItemIndex = state.currentOrder.items.findIndex(item => {
          const existingModifierKey = item.modifiers.map(m => `${m.id || m.name}`).sort().join(',');
          return item.productId === product.id && existingModifierKey === modifierKey;
        });

        let items;
        if (existingItemIndex >= 0) {
          // Increase quantity of existing item
          items = state.currentOrder.items.map((item, index) => {
            if (index === existingItemIndex) {
              const newQuantity = item.quantity + quantity;
              const basePrice = item.basePrice + item.modifiers.reduce((sum, mod) => sum + mod.price, 0);
              return {
                ...item,
                quantity: newQuantity,
                itemTotal: basePrice * newQuantity,
              };
            }
            return item;
          });
        } else {
          // Add new item
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
          items = [...state.currentOrder.items, item];
        }

        const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
        const tax = 0; // No tax
        const deliveryFee = state.currentOrder.deliveryFee;
        const serviceFeePercent = state.currentOrder.serviceFeePercent || 0;
        const serviceFee = subtotal * (serviceFeePercent / 100);
        const total = subtotal + tax + deliveryFee + serviceFee;

        return {
          currentOrder: {
            ...state.currentOrder,
            items,
            subtotal,
            tax,
            serviceFee,
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
        const serviceFeePercent = state.currentOrder.serviceFeePercent || 0;
        const serviceFee = subtotal * (serviceFeePercent / 100);
        const total = subtotal + tax + deliveryFee + serviceFee;

        return {
          currentOrder: {
            ...state.currentOrder,
            items,
            subtotal,
            tax,
            serviceFee,
            total,
          },
        };
      }),

      removeItemFromCart: (itemId) => set((state) => {
        const items = state.currentOrder.items.filter(item => item.id !== itemId);
        const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
        const tax = 0; // No tax
        const deliveryFee = state.currentOrder.deliveryFee;
        const serviceFeePercent = state.currentOrder.serviceFeePercent || 0;
        const serviceFee = subtotal * (serviceFeePercent / 100);
        const total = subtotal + tax + deliveryFee + serviceFee;

        return {
          currentOrder: {
            ...state.currentOrder,
            items,
            subtotal,
            tax,
            serviceFee,
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
          serviceFeePercent: 0,
          serviceFee: 0,
          total: state.currentOrder.deliveryFee,
        },
      })),

      // Actions: Service Fee
      setServiceFee: (serviceFeePercent) => set((state) => {
        const subtotal = state.currentOrder.subtotal;
        const serviceFee = subtotal * (serviceFeePercent / 100);
        const total = subtotal + state.currentOrder.tax + state.currentOrder.deliveryFee + serviceFee;
        return {
          currentOrder: {
            ...state.currentOrder,
            serviceFeePercent,
            serviceFee,
            total,
          },
        };
      }),

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
          tableIds: null,
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

      // Actions: Table Selection (supports multiple tables)
      toggleTableSelection: (table) => set((state) => {
        const isSelected = state.selectedTables.some((t) => t.id === table.id);
        if (isSelected) {
          // Remove table from selection
          return {
            selectedTables: state.selectedTables.filter((t) => t.id !== table.id),
          };
        } else {
          // Add table to selection
          return {
            selectedTables: [...state.selectedTables, table],
          };
        }
      }),

      // Legacy single table selection (for backwards compatibility)
      selectTable: (table) => set((state) => ({
        selectedTables: [table],
        customer: {
          ...state.customer,
          tableNumber: table.tableNumber,
        },
        ui: { ...state.ui, currentScreen: 'menu' },
      })),

      // Confirm selected tables and proceed to menu or modify-order if order exists
      confirmTableSelection: async (guestCount) => {
        const state = get();
        const tables = state.selectedTables;
        if (tables.length === 0) return;

        // Create combined table number (e.g., "1, 2, 3" or "1-2-3")
        const tableNumbers = tables.map((t) => t.tableNumber).join(', ');
        const totalCapacity = tables.reduce((sum, t) => sum + (t.capacity || 0), 0);
        const tableIds = tables.map((t) => t.id);

        // Check if any selected table has an active order
        const occupiedTable = tables.find(t => t.status === 'OCCUPIED' && t.currentOrderId);

        if (occupiedTable && occupiedTable.currentOrderId) {
          // Table has an active order - load it and go to modify screen
          try {
            set((s) => ({ ui: { ...s.ui, isLoading: true } }));
            const response = await posAPI.getOrderById(occupiedTable.currentOrderId);
            const existingOrder = response.data.data;

            // Map order items to POS format
            const posItems = (existingOrder.items || []).map((item, index) => ({
              id: item.id || `${item.productId}-${index}`,
              productId: item.productId,
              name: item.productName || item.name,
              basePrice: item.unitPrice || item.price || 0,
              modifiers: item.modifiers || [],
              quantity: item.quantity,
              itemTotal: item.totalPrice || (item.unitPrice || item.price || 0) * item.quantity,
              notes: item.notes || item.specialInstructions || '',
            }));

            set({
              currentOrder: {
                id: existingOrder.id,
                orderNumber: existingOrder.orderNumber,
                type: 'DINE_IN',
                items: posItems,
                subtotal: existingOrder.subtotal || 0,
                tax: existingOrder.tax || 0,
                deliveryFee: 0,
                serviceFeePercent: existingOrder.serviceFeePercent || 0,
                serviceFee: existingOrder.serviceFee || 0,
                total: existingOrder.total || 0,
                notes: existingOrder.orderNotes || '',
              },
              activeOrder: existingOrder,
              customer: {
                ...state.customer,
                tableNumber: tableNumbers,
                guestCount: guestCount || totalCapacity,
                tableIds: tableIds,
              },
              ui: { ...state.ui, isLoading: false, currentScreen: 'modify-order' },
            });
          } catch (error) {
            console.error('Failed to load existing order:', error);
            // Fall back to creating new order if loading fails
            set({
              customer: {
                ...state.customer,
                tableNumber: tableNumbers,
                guestCount: guestCount || totalCapacity,
                tableIds: tableIds,
              },
              ui: { ...state.ui, isLoading: false, currentScreen: 'menu' },
            });
          }
        } else {
          // No active order - proceed to menu for new order
          set({
            customer: {
              ...state.customer,
              tableNumber: tableNumbers,
              guestCount: guestCount || totalCapacity,
              tableIds: tableIds,
            },
            ui: { ...state.ui, currentScreen: 'menu' },
          });
        }
      },

      clearSelectedTables: () => set({
        selectedTables: [],
      }),

      // Legacy clear function
      clearSelectedTable: () => set({
        selectedTables: [],
      }),

      fetchFloorPlan: async (restaurantId) => {
        set((s) => ({ ui: { ...s.ui, isLoading: true, error: null } }));

        try {
          const response = await tablesAPI.getFloorPlan(restaurantId);
          const data = response.data.data;

          set({
            floorPlan: {
              tables: data.tables || [],
              sections: data.sections || [],
              lastFetched: new Date().toISOString(),
            },
            ui: { ...get().ui, isLoading: false },
          });

          return { success: true, tables: data.tables };
        } catch (error) {
          const errorMessage = error.response?.data?.message || error.message || 'Failed to load floor plan';
          set((s) => ({
            ui: { ...s.ui, isLoading: false, error: errorMessage },
          }));
          return { success: false, error: errorMessage };
        }
      },

      updateTableStatus: async (tableId, status) => {
        try {
          await tablesAPI.updateStatus(tableId, status);

          // Update local state
          set((state) => ({
            floorPlan: {
              ...state.floorPlan,
              tables: state.floorPlan.tables.map((table) =>
                table.id === tableId ? { ...table, status } : table
              ),
            },
          }));

          return { success: true };
        } catch (error) {
          console.error('Failed to update table status:', error);
          return { success: false, error: error.message };
        }
      },

      // Actions: Kitchen Status
      fetchKitchenStatus: async (orderId) => {
        try {
          const response = await posAPI.getKitchenStatus(orderId);
          const statusData = response.data.data;

          set({
            kitchenStatus: {
              orderId: statusData.orderId,
              kitchenOrderId: statusData.kitchenOrderId,
              status: statusData.kitchenStatus,
              priority: statusData.priority,
              assignedChef: statusData.assignedChef,
              estimatedMinutes: statusData.estimatedMinutes,
              lastUpdated: new Date().toISOString(),
            },
          });

          return statusData;
        } catch (error) {
          console.error('Failed to fetch kitchen status:', error);
          return null;
        }
      },

      clearKitchenStatus: () => set({
        kitchenStatus: {
          orderId: null,
          kitchenOrderId: null,
          status: null,
          priority: null,
          assignedChef: null,
          estimatedMinutes: null,
          lastUpdated: null,
        },
      }),

      // Actions: Active Order Management (for modifications)
      setActiveOrder: (order) => set({ activeOrder: order }),

      clearActiveOrder: () => set({ activeOrder: null }),

      // Fetch an existing order by ID
      fetchOrderById: async (orderId) => {
        set((s) => ({ ui: { ...s.ui, isLoading: true, error: null } }));

        try {
          const response = await posAPI.getOrderById(orderId);
          const order = response.data.data;

          set({
            activeOrder: order,
            ui: { ...get().ui, isLoading: false },
          });

          return { success: true, order };
        } catch (error) {
          const errorMessage = error.response?.data?.message || error.message || 'Failed to load order';
          set((s) => ({
            ui: { ...s.ui, isLoading: false, error: errorMessage },
          }));
          return { success: false, error: errorMessage };
        }
      },

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
              tableIds: state.customer.tableIds || [],
              guestCount: state.customer.guestCount,
            } : null,
            orderNotes: state.currentOrder.notes || null,
            paymentMethod: state.payment.method || null, // Optional - CASH, CARD, MOBILE or null
            subtotal: state.currentOrder.subtotal,
            tax: state.currentOrder.tax,
            deliveryFee: state.currentOrder.deliveryFee,
            serviceFeePercent: state.currentOrder.serviceFeePercent || 0,
            serviceFee: state.currentOrder.serviceFee || 0,
            total: state.currentOrder.total,
            amountTendered: state.payment.amountTendered || null,
            changeDue: state.payment.changeDue || null,
          };

          const response = await posAPI.createOrder(orderData);
          const orderData2 = response.data.data;
          const orderId = orderData2.id;
          const orderNumber = orderData2.orderNumber;

          set((s) => ({
            currentOrder: { ...s.currentOrder, id: orderId, orderNumber },
            ui: { ...s.ui, isLoading: false },
          }));

          // Refresh floor plan to show updated table status (for DINE_IN orders)
          if (state.currentOrder.type === 'DINE_IN') {
            try {
              await get().fetchFloorPlan(restaurantId);
            } catch (e) {
              console.error('Failed to refresh floor plan:', e);
            }
          }

          return { success: true, orderId, orderNumber };
        } catch (error) {
          const errorMessage = error.response?.data?.message || error.message || 'Failed to submit order';
          set((s) => ({
            ui: { ...s.ui, isLoading: false, error: errorMessage },
          }));
          return { success: false, error: errorMessage };
        }
      },

      // Actions: Complete Order & Reset
      completeOrder: () => {
        const state = get();

        // Explicitly clear the persisted storage first
        localStorage.removeItem('pos-storage');

        // Then set the cleared state
        set({
          currentOrder: {
            id: null,
            orderNumber: null,
            type: null,
            items: [],
            subtotal: 0,
            tax: 0,
            deliveryFee: 0,
            serviceFeePercent: 0,
            serviceFee: 0,
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
            tableIds: null,
            guestCount: null,
          },
          payment: {
            method: null,
            amountTendered: 0,
            changeDue: 0,
            status: 'PENDING',
          },
          kitchenStatus: {
            orderId: null,
            kitchenOrderId: null,
            status: null,
            priority: null,
            assignedChef: null,
            estimatedMinutes: null,
            lastUpdated: null,
          },
          selectedTables: [],
          activeOrder: null,
          // Preserve menu data
          menu: state.menu,
          floorPlan: state.floorPlan,
          productAvailability: state.productAvailability,
          ui: {
            currentScreen: 'start',
            isLoading: false,
            error: null,
            selectedCategory: null,
            selectedProduct: null,
          },
        });
      },

      resetPOS: () => {
        const state = get();

        // Explicitly clear the persisted storage first
        localStorage.removeItem('pos-storage');

        // Then set the cleared state
        set({
          currentOrder: {
            id: null,
            orderNumber: null,
            type: null,
            items: [],
            subtotal: 0,
            tax: 0,
            deliveryFee: 0,
            serviceFeePercent: 0,
            serviceFee: 0,
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
            tableIds: null,
            guestCount: null,
          },
          payment: {
            method: null,
            amountTendered: 0,
            changeDue: 0,
            status: 'PENDING',
          },
          kitchenStatus: {
            orderId: null,
            kitchenOrderId: null,
            status: null,
            priority: null,
            assignedChef: null,
            estimatedMinutes: null,
            lastUpdated: null,
          },
          selectedTables: [],
          activeOrder: null,
          // Preserve menu data
          menu: state.menu,
          floorPlan: state.floorPlan,
          productAvailability: state.productAvailability,
          ui: {
            currentScreen: 'start',
            isLoading: false,
            error: null,
            selectedCategory: null,
            selectedProduct: null,
          },
        });
      },
    }),
    {
      name: 'pos-storage', // localStorage key
      partialize: (state) => ({
        // Persist these parts for cross-page navigation
        currentOrder: state.currentOrder,
        customer: state.customer,
        menu: state.menu,
        ui: state.ui,
      }),
      // Merge persisted state with initial state
      merge: (persistedState, currentState) => {
        // If persisted state has a cleared order (empty items), use it
        if (persistedState?.currentOrder?.items?.length === 0) {
          return {
            ...currentState,
            ...persistedState,
          };
        }
        return {
          ...currentState,
          ...persistedState,
        };
      },
    }
  )
);

export default usePOSStore;
