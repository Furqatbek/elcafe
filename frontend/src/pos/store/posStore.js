import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { posAPI, tablesAPI, bundleAPI, promotionAPI } from '../../services/api';

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
        entryFee: 0,
        discount: 0,
        discountType: null, // 'COUPON' | 'PROMOTION' | 'MANUAL' | 'HAPPY_HOUR'
        couponCode: null,
        promotionId: null,
        promotionName: null,
        discountReason: null,
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
        bundles: [],
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

      // Happy Hour State
      happyHour: {
        active: null, // Current active happy hour info
        discountPreview: null, // Preview of discount for current order
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
          serviceFeePercent: 0,
          serviceFee: 0,
          entryFee: 0,
          discount: 0,
          discountType: null,
          couponCode: null,
          promotionId: null,
          promotionName: null,
          discountReason: null,
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
          // For bundles, compare bundleId; for regular products, compare productId
          if (product.isBundle) {
            return item.bundleId === product.bundleId && existingModifierKey === modifierKey;
          }
          return item.productId === product.id && !item.isBundle && existingModifierKey === modifierKey;
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
            productId: product.isBundle ? product.bundleId : product.id,
            name: product.name,
            basePrice: product.price,
            modifiers,
            quantity,
            itemTotal: itemPrice * quantity,
            notes: '',
            // Bundle fields
            bundleId: product.bundleId || null,
            bundleName: product.isBundle ? product.name : null,
            isBundle: product.isBundle || false,
          };
          items = [...state.currentOrder.items, item];
        }

        const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
        const tax = 0; // No tax
        const deliveryFee = state.currentOrder.deliveryFee;
        const serviceFeePercent = state.currentOrder.serviceFeePercent || 0;
        const serviceFee = subtotal * (serviceFeePercent / 100);
        const entryFee = state.currentOrder.entryFee || 0;
        const discount = state.currentOrder.discount || 0;
        const total = Math.max(0, subtotal + tax + deliveryFee + serviceFee + entryFee - discount);

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

      // Add free item from coupon/promotion
      addFreeItem: async (freeItemData) => {
        const state = get();

        // Check if free item already exists
        const existingFreeItem = state.currentOrder.items.find(
          item => item.isFreeItem && item.productId === freeItemData.productId
        );

        if (existingFreeItem) {
          return {
            success: false,
            error: 'Free item already added',
          };
        }

        // Add free item with price 0
        const freeItem = {
          id: `free-${freeItemData.productId}-${Date.now()}`,
          productId: freeItemData.productId,
          name: `${freeItemData.productName} (FREE)`,
          basePrice: 0,
          originalPrice: freeItemData.price || 0,
          modifiers: [],
          quantity: 1,
          itemTotal: 0,
          notes: `Free item from coupon: ${freeItemData.couponCode}`,
          isFreeItem: true,
          couponCode: freeItemData.couponCode,
          promotionId: freeItemData.promotionId,
        };

        const items = [...state.currentOrder.items, freeItem];
        const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
        const tax = 0;
        const deliveryFee = state.currentOrder.deliveryFee;
        const serviceFeePercent = state.currentOrder.serviceFeePercent || 0;
        const serviceFee = subtotal * (serviceFeePercent / 100);
        const entryFee = state.currentOrder.entryFee || 0;
        const discount = state.currentOrder.discount || 0;
        const total = Math.max(0, subtotal + tax + deliveryFee + serviceFee + entryFee - discount);

        set({
          currentOrder: {
            ...state.currentOrder,
            items,
            subtotal,
            tax,
            serviceFee,
            total,
            couponCode: freeItemData.couponCode,
            promotionId: freeItemData.promotionId,
            promotionName: freeItemData.promotionName,
            discountType: 'FREE_ITEM',
          },
        });

        return { success: true, freeItem };
      },

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
        const entryFee = state.currentOrder.entryFee || 0;
        const discount = state.currentOrder.discount || 0;
        const total = Math.max(0, subtotal + tax + deliveryFee + serviceFee + entryFee - discount);

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
        const entryFee = state.currentOrder.entryFee || 0;
        const discount = state.currentOrder.discount || 0;
        const total = Math.max(0, subtotal + tax + deliveryFee + serviceFee + entryFee - discount);

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
          entryFee: 0,
          discount: 0,
          discountType: null,
          couponCode: null,
          promotionId: null,
          promotionName: null,
          discountReason: null,
          total: state.currentOrder.deliveryFee,
        },
      })),

      // Actions: Service Fee
      setServiceFee: (serviceFeePercent) => set((state) => {
        const subtotal = state.currentOrder.subtotal;
        const serviceFee = subtotal * (serviceFeePercent / 100);
        const entryFee = state.currentOrder.entryFee || 0;
        const discount = state.currentOrder.discount || 0;
        const total = Math.max(0, subtotal + state.currentOrder.tax + state.currentOrder.deliveryFee + serviceFee + entryFee - discount);
        return {
          currentOrder: {
            ...state.currentOrder,
            serviceFeePercent,
            serviceFee,
            total,
          },
        };
      }),

      // Actions: Entry Fee
      setEntryFee: (entryFee) => set((state) => {
        const subtotal = state.currentOrder.subtotal;
        const serviceFee = state.currentOrder.serviceFee || 0;
        const discount = state.currentOrder.discount || 0;
        const total = Math.max(0, subtotal + state.currentOrder.tax + state.currentOrder.deliveryFee + serviceFee + entryFee - discount);
        return {
          currentOrder: {
            ...state.currentOrder,
            entryFee,
            total,
          },
        };
      }),

      // Actions: Discount Management
      validateCoupon: async (couponCode) => {
        const state = get();
        const orderId = state.currentOrder.id;

        if (!orderId || orderId.toString().startsWith('temp-')) {
          // Order not yet submitted - use coupon validation endpoint
          try {
            const restaurantId = parseInt(localStorage.getItem('selectedRestaurantId')) || 1;
            const response = await promotionAPI.validateCoupon({
              code: couponCode,
              restaurantId: restaurantId,
              orderSubtotal: state.currentOrder.subtotal || 0,
              orderType: state.currentOrder.type || 'DINE_IN',
            });
            return response.data;
          } catch (error) {
            return {
              valid: false,
              errorMessage: error.response?.data?.message || 'Invalid coupon code',
            };
          }
        }

        try {
          const response = await posAPI.validateCoupon(orderId, couponCode);
          return response.data.data;
        } catch (error) {
          return {
            valid: false,
            errorMessage: error.response?.data?.message || 'Failed to validate coupon',
          };
        }
      },

      applyDiscount: async (discountData) => {
        const state = get();
        const orderId = state.currentOrder.id;

        // If order not yet submitted, apply discount locally
        if (!orderId || orderId.toString().startsWith('temp-')) {
          const subtotal = state.currentOrder.subtotal;
          let discountAmount = 0;

          if (discountData.discountType === 'MANUAL') {
            if (discountData.manualDiscountAmount) {
              discountAmount = Math.min(discountData.manualDiscountAmount, subtotal);
            } else if (discountData.manualDiscountPercent) {
              discountAmount = subtotal * (discountData.manualDiscountPercent / 100);
            }
          } else if (discountData.discountType === 'COUPON') {
            // Handle coupon discounts
            if (discountData.discountAmount) {
              // Fixed amount discount
              discountAmount = Math.min(discountData.discountAmount, subtotal);
            } else if (discountData.discountPercent) {
              // Percentage discount
              discountAmount = subtotal * (discountData.discountPercent / 100);
              // Apply max discount if specified
              if (discountData.maxDiscount && discountAmount > discountData.maxDiscount) {
                discountAmount = discountData.maxDiscount;
              }
            }
          } else if (discountData.discountType === 'PROMOTION') {
            // Handle promotion discounts
            if (discountData.discountAmount) {
              discountAmount = Math.min(discountData.discountAmount, subtotal);
            } else if (discountData.discountPercent) {
              discountAmount = subtotal * (discountData.discountPercent / 100);
            }
          }

          const serviceFee = state.currentOrder.serviceFee || 0;
          const entryFee = state.currentOrder.entryFee || 0;
          const total = Math.max(0, subtotal + state.currentOrder.tax + state.currentOrder.deliveryFee + serviceFee + entryFee - discountAmount);

          set({
            currentOrder: {
              ...state.currentOrder,
              discount: discountAmount,
              discountType: discountData.discountType,
              couponCode: discountData.couponCode || null,
              promotionId: discountData.promotionId || null,
              promotionName: discountData.promotionName || null,
              discountReason: discountData.discountReason || null,
              total,
            },
          });

          return { success: true, discount: discountAmount };
        }

        // Order already submitted - apply via API
        try {
          set((s) => ({ ui: { ...s.ui, isLoading: true } }));
          const response = await posAPI.applyDiscount(orderId, discountData);
          const updatedOrder = response.data.data;

          set({
            currentOrder: {
              ...state.currentOrder,
              discount: updatedOrder.discount || 0,
              discountType: discountData.discountType,
              couponCode: updatedOrder.couponCode || discountData.couponCode || null,
              promotionId: updatedOrder.promotionId || discountData.promotionId || null,
              discountReason: discountData.discountReason || null,
              total: updatedOrder.total,
            },
            ui: { ...get().ui, isLoading: false },
          });

          return { success: true, discount: updatedOrder.discount };
        } catch (error) {
          set((s) => ({ ui: { ...s.ui, isLoading: false } }));
          return {
            success: false,
            error: error.response?.data?.message || 'Failed to apply discount',
          };
        }
      },

      removeDiscount: async () => {
        const state = get();
        const orderId = state.currentOrder.id;

        // If order not yet submitted, remove discount locally
        if (!orderId || orderId.toString().startsWith('temp-')) {
          // Remove any free items that were added by coupon
          const items = state.currentOrder.items.filter(item => !item.isFreeItem);
          const subtotal = items.reduce((sum, item) => sum + item.itemTotal, 0);
          const serviceFeePercent = state.currentOrder.serviceFeePercent || 0;
          const serviceFee = subtotal * (serviceFeePercent / 100);
          const entryFee = state.currentOrder.entryFee || 0;
          const total = subtotal + state.currentOrder.tax + state.currentOrder.deliveryFee + serviceFee + entryFee;

          set({
            currentOrder: {
              ...state.currentOrder,
              items,
              subtotal,
              serviceFee,
              discount: 0,
              discountType: null,
              couponCode: null,
              promotionId: null,
              promotionName: null,
              discountReason: null,
              total,
            },
          });

          return { success: true };
        }

        // Order already submitted - remove via API
        try {
          set((s) => ({ ui: { ...s.ui, isLoading: true } }));
          const response = await posAPI.removeDiscount(orderId);
          const updatedOrder = response.data.data;

          set({
            currentOrder: {
              ...state.currentOrder,
              discount: 0,
              discountType: null,
              couponCode: null,
              promotionId: null,
              promotionName: null,
              discountReason: null,
              total: updatedOrder.total,
            },
            ui: { ...get().ui, isLoading: false },
          });

          return { success: true };
        } catch (error) {
          set((s) => ({ ui: { ...s.ui, isLoading: false } }));
          return {
            success: false,
            error: error.response?.data?.message || 'Failed to remove discount',
          };
        }
      },

      setManualDiscount: (amount, percent, reason) => set((state) => {
        const subtotal = state.currentOrder.subtotal;
        let discountAmount = 0;

        if (amount && amount > 0) {
          discountAmount = Math.min(amount, subtotal);
        } else if (percent && percent > 0) {
          discountAmount = subtotal * (percent / 100);
        }

        const serviceFee = state.currentOrder.serviceFee || 0;
        const entryFee = state.currentOrder.entryFee || 0;
        const total = Math.max(0, subtotal + state.currentOrder.tax + state.currentOrder.deliveryFee + serviceFee + entryFee - discountAmount);

        return {
          currentOrder: {
            ...state.currentOrder,
            discount: discountAmount,
            discountType: discountAmount > 0 ? 'MANUAL' : null,
            discountReason: reason || null,
            couponCode: null,
            promotionId: null,
            promotionName: null,
            total,
          },
        };
      }),

      // Actions: Happy Hour Management
      fetchActiveHappyHour: async (restaurantId) => {
        try {
          const response = await posAPI.getActiveHappyHour(restaurantId);
          const happyHourData = response.data?.data || null;

          set({
            happyHour: {
              active: happyHourData,
              discountPreview: null,
              lastFetched: new Date().toISOString(),
            },
          });

          return happyHourData;
        } catch (error) {
          console.error('Failed to fetch active happy hour:', error);
          set({
            happyHour: {
              active: null,
              discountPreview: null,
              lastFetched: new Date().toISOString(),
            },
          });
          return null;
        }
      },

      previewHappyHourDiscount: async (orderId) => {
        if (!orderId || orderId.toString().startsWith('temp-')) {
          // Can't preview for temp orders
          return null;
        }

        try {
          const response = await posAPI.previewHappyHourDiscount(orderId);
          const preview = response.data?.data || null;

          set((state) => ({
            happyHour: {
              ...state.happyHour,
              discountPreview: preview,
            },
          }));

          return preview;
        } catch (error) {
          console.error('Failed to preview happy hour discount:', error);
          return null;
        }
      },

      applyHappyHour: async () => {
        const state = get();
        const orderId = state.currentOrder.id;
        const happyHourData = state.happyHour.active;

        // Check if happy hour is active
        if (!happyHourData) {
          return {
            success: false,
            error: 'No active happy hour',
          };
        }

        // For temp orders, calculate discount locally
        if (!orderId || orderId.toString().startsWith('temp-')) {
          const items = state.currentOrder.items || [];
          const discountPercent = parseFloat(happyHourData.discountPercent) || 0;

          if (discountPercent <= 0) {
            return {
              success: false,
              error: 'Invalid discount percentage',
            };
          }

          // Calculate applicable amount based on product/category eligibility
          let applicableAmount = 0;

          items.forEach(item => {
            // Skip free items
            if (item.isFreeItem) return;

            let isEligible = false;

            // If applies to all, everything is eligible
            if (happyHourData.appliesToAll) {
              isEligible = true;
            } else {
              // Check if product is in applicable products
              if (happyHourData.applicableProductIds &&
                  happyHourData.applicableProductIds.includes(item.productId)) {
                isEligible = true;
              }
              // Check if product category is in applicable categories
              if (!isEligible && happyHourData.applicableCategoryIds &&
                  item.categoryId &&
                  happyHourData.applicableCategoryIds.includes(item.categoryId)) {
                isEligible = true;
              }
            }

            if (isEligible) {
              applicableAmount += item.itemTotal || 0;
            }
          });

          // Calculate discount
          const discountAmount = (applicableAmount * discountPercent) / 100;

          if (discountAmount <= 0) {
            return {
              success: false,
              error: 'No eligible items for happy hour discount',
            };
          }

          // Update order with discount
          const subtotal = state.currentOrder.subtotal || 0;
          const tax = state.currentOrder.tax || 0;
          const deliveryFee = state.currentOrder.deliveryFee || 0;
          const serviceFee = state.currentOrder.serviceFee || 0;
          const entryFee = state.currentOrder.entryFee || 0;
          const total = Math.max(0, subtotal + tax + deliveryFee + serviceFee + entryFee - discountAmount);

          set({
            currentOrder: {
              ...state.currentOrder,
              discount: discountAmount,
              discountType: 'HAPPY_HOUR',
              couponCode: null,
              promotionId: happyHourData.id,
              promotionName: happyHourData.name,
              discountReason: null,
              total,
            },
          });

          return { success: true, discount: discountAmount };
        }

        // For submitted orders, use API
        try {
          set((s) => ({ ui: { ...s.ui, isLoading: true } }));
          const response = await posAPI.applyHappyHourDiscount(orderId);
          const updatedOrder = response.data.data;

          set({
            currentOrder: {
              ...state.currentOrder,
              discount: updatedOrder.discount || 0,
              discountType: 'HAPPY_HOUR',
              couponCode: null,
              promotionId: happyHourData.id,
              promotionName: happyHourData.name,
              discountReason: null,
              total: updatedOrder.total,
            },
            ui: { ...get().ui, isLoading: false },
          });

          return { success: true, discount: updatedOrder.discount };
        } catch (error) {
          set((s) => ({ ui: { ...s.ui, isLoading: false } }));
          return {
            success: false,
            error: error.response?.data?.message || 'Failed to apply happy hour discount',
          };
        }
      },

      clearHappyHour: () => set({
        happyHour: {
          active: null,
          discountPreview: null,
          lastFetched: null,
        },
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
          // Fetch categories, products, and bundles in parallel
          const [categoriesRes, productsRes, bundlesRes] = await Promise.all([
            posAPI.getCategories(restaurantId),
            posAPI.getProducts(restaurantId),
            bundleAPI.getMenuBundles(restaurantId, true).catch(() => ({ data: { data: [] } })), // includeAll=true for POS
          ]);

          const categories = categoriesRes.data.data || categoriesRes.data || [];
          const products = productsRes.data.data || productsRes.data || [];
          const bundles = bundlesRes.data.data || bundlesRes.data || [];

          set({
            menu: {
              categories,
              products,
              bundles,
              lastFetched: new Date().toISOString(),
            },
            ui: { ...get().ui, isLoading: false },
          });

          return { success: true, categories, products, bundles };
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
              isFreeItem: item.isFreeItem || false,
              couponCode: item.couponCode || null,
              promotionId: item.promotionId || null,
              // Bundle/Combo fields
              bundleId: item.bundleId || null,
              bundleName: item.bundleName || null,
              isBundle: item.isBundle || false,
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
            entryFee: state.currentOrder.entryFee || 0,
            discount: state.currentOrder.discount || 0,
            discountType: state.currentOrder.discountType || null,
            couponCode: state.currentOrder.couponCode || null,
            promotionId: state.currentOrder.promotionId || null,
            promotionName: state.currentOrder.promotionName || null,
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
            entryFee: 0,
            discount: 0,
            discountType: null,
            couponCode: null,
            promotionId: null,
            promotionName: null,
            discountReason: null,
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
            entryFee: 0,
            discount: 0,
            discountType: null,
            couponCode: null,
            promotionId: null,
            promotionName: null,
            discountReason: null,
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
