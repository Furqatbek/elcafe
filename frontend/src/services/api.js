import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem('refresh_token');

        // If no refresh token exists, redirect to login immediately
        if (!refreshToken || refreshToken === '' || refreshToken === 'null' || refreshToken === 'undefined') {
          throw new Error('No refresh token available');
        }

        // Check if refresh token is expired
        const refreshTokenExpiry = localStorage.getItem('refresh_token_expiry');
        if (refreshTokenExpiry && Date.now() >= parseInt(refreshTokenExpiry)) {
          throw new Error('Refresh token expired');
        }

        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken,
        });

        const { accessToken, refreshToken: newRefreshToken } = response.data.data;

        // Update tokens with expiration tracking
        const now = Date.now();

        // Try to decode JWT to get actual expiration
        const decodeJWT = (token) => {
          try {
            const base64Url = token.split('.')[1];
            const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
            const jsonPayload = decodeURIComponent(
              atob(base64)
                .split('')
                .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
            );
            return JSON.parse(jsonPayload);
          } catch {
            return null;
          }
        };

        const decodedAccess = decodeJWT(accessToken);
        const accessTokenExpiry = decodedAccess?.exp
          ? decodedAccess.exp * 1000
          : now + (15 * 60 * 1000); // 15 minutes default

        localStorage.setItem('access_token', accessToken);
        localStorage.setItem('access_token_expiry', accessTokenExpiry.toString());

        if (newRefreshToken) {
          const decodedRefresh = decodeJWT(newRefreshToken);
          const refreshTokenExpiry = decodedRefresh?.exp
            ? decodedRefresh.exp * 1000
            : now + (7 * 24 * 60 * 60 * 1000); // 7 days default

          localStorage.setItem('refresh_token', newRefreshToken);
          localStorage.setItem('refresh_token_expiry', refreshTokenExpiry.toString());
        }

        localStorage.setItem('token_set_time', now.toString());

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        // Clear all auth data
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('access_token_expiry');
        localStorage.removeItem('refresh_token_expiry');
        localStorage.removeItem('token_set_time');

        // Redirect to login
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export const authAPI = {
  login: (credentials) => api.post('/auth/login', credentials),
  register: (data) => api.post('/auth/register', data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  forgotPassword: (email) => api.post('/auth/forgot-password', { email }),
  resetPassword: (token, newPassword) => api.post('/auth/reset-password', { token, newPassword }),
};

export const restaurantAPI = {
  getAll: (params) => api.get('/restaurants', { params }),
  getById: (id) => api.get(`/restaurants/${id}`),
  create: (data) => api.post('/restaurants', data),
  update: (id, data) => api.put(`/restaurants/${id}`, data),
  delete: (id) => api.delete(`/restaurants/${id}`),
  getActive: () => api.get('/restaurants/active'),
  getAcceptingOrders: () => api.get('/restaurants/accepting-orders'),
  getBusinessHours: (restaurantId) => api.get(`/restaurants/${restaurantId}/business-hours`),
  updateBusinessHours: (restaurantId, data) => api.put(`/restaurants/${restaurantId}/business-hours/bulk`, data),
};

export const menuAPI = {
  getPublicMenu: (restaurantId) => api.get(`/menu/public/${restaurantId}`),
  getCategories: (restaurantId) => api.get(`/menu/restaurants/${restaurantId}/categories`),
  // Category management
  createCategory: (data) => api.post('/categories', data),
  updateCategory: (id, data) => api.put(`/categories/${id}`, data),
  deleteCategory: (id) => api.delete(`/categories/${id}`),
  getCategoryById: (id) => api.get(`/categories/${id}`),
  // Product management
  createProduct: (data) => api.post('/products', data),
  updateProduct: (id, data) => api.put(`/products/${id}`, data),
  getProductById: (id) => api.get(`/products/${id}`),
  getProductsByRestaurant: (restaurantId) => api.get(`/products/restaurant/${restaurantId}`),
  getProductsByCategory: (categoryId) => api.get(`/products/category/${categoryId}`),
  deleteProduct: (id) => api.delete(`/products/${id}`),
};

export const menuCollectionAPI = {
  getAll: (restaurantId, params) => api.get('/menu-collections', { params: { restaurantId, ...params } }),
  getById: (id) => api.get(`/menu-collections/${id}`),
  getActive: (restaurantId) => api.get('/menu-collections/active', { params: { restaurantId } }),
  create: (data) => api.post('/menu-collections', data),
  update: (id, data) => api.put(`/menu-collections/${id}`, data),
  addProducts: (id, productIds) => api.post(`/menu-collections/${id}/products`, productIds),
  delete: (id) => api.delete(`/menu-collections/${id}`),
};

export const linkedItemAPI = {
  getLinkedItems: (productId) => api.get(`/products/${productId}/linked-items`),
  getLinkedItemsByType: (productId, linkType) => api.get(`/products/${productId}/linked-items/by-type`, { params: { linkType } }),
  addLinkedItem: (productId, data) => api.post(`/products/${productId}/linked-items`, data),
  deleteLinkedItem: (productId, id) => api.delete(`/products/${productId}/linked-items/${id}`),
};

export const productVariantAPI = {
  getAll: (productId, params) => api.get(`/products/${productId}/variants`, { params }),
  getAllNoPaging: (productId) => api.get(`/products/${productId}/variants/all`),
  getById: (productId, variantId) => api.get(`/products/${productId}/variants/${variantId}`),
  create: (productId, data) => api.post(`/products/${productId}/variants`, data),
  update: (productId, variantId, data) => api.put(`/products/${productId}/variants/${variantId}`, data),
  delete: (productId, variantId) => api.delete(`/products/${productId}/variants/${variantId}`),
  search: (productId, query, params) => api.get(`/products/${productId}/variants/search`, { params: { query, ...params } }),
  getInStock: (productId) => api.get(`/products/${productId}/variants/in-stock`),
};

export const orderAPI = {
  create: (data) => api.post('/orders', data),
  getById: (id) => api.get(`/orders/${id}`),
  getByNumber: (orderNumber) => api.get(`/orders/number/${orderNumber}`),
  getAll: (params) => api.get('/orders', { params }),
  getPending: () => api.get('/orders/pending'),
  getByRestaurant: (restaurantId) => api.get(`/orders/restaurant/${restaurantId}`),
  updateStatus: (id, status, notes, changedBy = 'OPERATOR') =>
    api.patch(`/orders/${id}/status`, null, { params: { status, notes, changedBy } }),
};

export const customerAPI = {
  getAll: (params) => api.get('/customers', { params }),
  getById: (id) => api.get(`/customers/${id}`),
  getByPhone: (phone) => api.get('/customers/search/phone', { params: { phone } }),
  suggestByPhone: (phone) => api.get('/customers/suggest/phone', { params: { phone } }),
  create: (data) => api.post('/customers', data),
  update: (id, data) => api.put(`/customers/${id}`, data),
  delete: (id) => api.delete(`/customers/${id}`),
  getOrders: (id) => api.get(`/customers/${id}/orders`),
  getAllActivity: () => api.get('/customers/activity'),
  getFilteredActivity: (params) => api.get('/customers/activity/filter', { params }),
  filterActivity: (filterData) => api.post('/customers/activity/filter', filterData),
};

export const operatorAPI = {
  getAll: (params) => api.get('/operators', { params }),
  getById: (id) => api.get(`/operators/${id}`),
  create: (data) => api.post('/operators', data),
  update: (id, data) => api.put(`/operators/${id}`, data),
  delete: (id) => api.delete(`/operators/${id}`),
};

export const courierAPI = {
  getAll: (page = 0, size = 10) => api.get('/couriers', { params: { page, size } }),
  getById: (id) => api.get(`/couriers/${id}`),
  getWallet: (id) => api.get(`/couriers/${id}/wallet`),
  create: (data) => api.post('/couriers', data),
  update: (id, data) => api.put(`/couriers/${id}`, data),
  delete: (id) => api.delete(`/couriers/${id}`),
  // Status management
  updateStatus: (id, data) => api.post(`/couriers/${id}/status`, data),
  getStatus: (id) => api.get(`/couriers/${id}/status`),
};

export const analyticsAPI = {
  // Summary
  getSummary: (params) => api.get('/analytics/summary', { params }),

  // Financial Analytics
  getDailyRevenue: (params) => api.get('/analytics/financial/daily-revenue', { params }),
  getSalesByCategory: (params) => api.get('/analytics/financial/sales-by-category', { params }),
  getCOGS: (params) => api.get('/analytics/financial/cogs', { params }),
  getProfitability: (params) => api.get('/analytics/financial/profitability', { params }),
  getContributionMargins: (params) => api.get('/analytics/financial/contribution-margins', { params }),

  // Operational Analytics
  getSalesPerHour: (params) => api.get('/analytics/operational/sales-per-hour', { params }),
  getPeakHours: (params) => api.get('/analytics/operational/peak-hours', { params }),
  getTableTurnover: (params) => api.get('/analytics/operational/table-turnover', { params }),
  getOrderTiming: (params) => api.get('/analytics/operational/order-timing', { params }),

  // Customer Analytics
  getCustomerRetention: (params) => api.get('/analytics/customer/retention', { params }),
  getCustomerLTV: (params) => api.get('/analytics/customer/ltv', { params }),
  getCustomerSatisfaction: (params) => api.get('/analytics/customer/satisfaction', { params }),

  // Inventory Analytics
  getInventoryTurnover: (params) => api.get('/analytics/inventory/turnover', { params }),
};

export const kitchenAPI = {
  getActiveOrders: (restaurantId) => api.get('/kitchen/orders/active', { params: { restaurantId } }),
  getReadyOrders: (restaurantId) => api.get('/kitchen/orders/ready', { params: { restaurantId } }),
  startPreparation: (id, chefName) => api.post(`/kitchen/orders/${id}/start`, null, { params: { chefName } }),
  markReady: (id) => api.post(`/kitchen/orders/${id}/ready`),
  markPickedUp: (id) => api.post(`/kitchen/orders/${id}/picked-up`),
  updatePriority: (id, priority) => api.patch(`/kitchen/orders/${id}/priority`, null, { params: { priority } }),
};

export const uploadAPI = {
  uploadImage: (file, folder = 'images') => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);
    return api.post('/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
  deleteImage: (url) => api.delete('/files', { params: { fileUrl: url } }),
};

export const tablesAPI = {
  getAll: (restaurantId) => api.get(`/restaurants/${restaurantId}/tables`),
  getStats: (restaurantId) => api.get(`/restaurants/${restaurantId}/tables/stats`),
  getById: (id) => api.get(`/tables/${id}`),
  create: (data) => api.post('/tables', data),
  update: (id, data) => api.put(`/tables/${id}`, data),
  delete: (id) => api.delete(`/tables/${id}`),
  updateStatus: (id, status) => api.patch(`/tables/${id}/status`, { status }),
  updatePosition: (id, positionData) => api.put(`/tables/${id}/position`, positionData),
  getAvailable: (restaurantId) => api.get(`/restaurants/${restaurantId}/tables/available`),
  getSections: (restaurantId) => api.get(`/restaurants/${restaurantId}/tables/sections`),
  getBySection: (restaurantId, section) => api.get(`/restaurants/${restaurantId}/tables/section/${section}`),
  getFloorPlan: (restaurantId) => api.get(`/restaurants/${restaurantId}/floor-plan`),
  merge: (data) => api.post('/tables/merge', data),
  unmerge: (tableId) => api.post(`/tables/${tableId}/unmerge`),
  getMerged: (tableId) => api.get(`/tables/${tableId}/merged`),
};

export const waiterAPI = {
  getAll: (params) => api.get('/waiters', { params }),
  getActive: () => api.get('/waiters/active'),
  getById: (id) => api.get(`/waiters/${id}`),
  create: (data) => api.post('/waiters', data),
  update: (id, data) => api.put(`/waiters/${id}`, data),
  delete: (id) => api.delete(`/waiters/${id}`),
  auth: (pinCode) => api.post('/waiters/auth', { pinCode }),
  assignToTable: (waiterId, tableId) => api.post(`/waiters/${waiterId}/tables/${tableId}/assign`),
  unassignFromTable: (waiterId, tableId) => api.post(`/waiters/${waiterId}/tables/${tableId}/unassign`),
  getMyProfile: () => api.get('/waiters/me'),
  getMyTables: () => api.get('/waiters/me/tables'),
};

export const posAPI = {
  createOrder: (orderData) => api.post('/pos/orders', orderData),
  getCategories: (restaurantId) => api.get(`/menu/restaurants/${restaurantId}/categories`),
  getProducts: (restaurantId) => api.get(`/products/restaurant/${restaurantId}`),
  checkProductAvailability: (productId, restaurantId) =>
    api.get(`/pos/orders/products/${productId}/availability`, { params: { restaurantId } }),
  getKitchenStatus: (orderId) => api.get(`/pos/orders/${orderId}/kitchen-status`),
  // Order Management
  getOpenDineInOrders: (restaurantId) => api.get(`/pos/orders/open/${restaurantId}`),
  getOrderById: (orderId) => api.get(`/pos/orders/${orderId}`),
  addItemToOrder: (orderId, item) => api.post(`/pos/orders/${orderId}/items`, item),
  removeItemFromOrder: (orderId, itemId) => api.delete(`/pos/orders/${orderId}/items/${itemId}`),
  updateItemQuantity: (orderId, itemId, quantity) =>
    api.patch(`/pos/orders/${orderId}/items/${itemId}/quantity`, null, { params: { quantity } }),
  // Split Bill
  splitBill: (orderId, splitData) => api.post(`/pos/orders/${orderId}/split`, splitData),
  // Payment Processing
  processPayment: (orderId, paymentData) => api.post(`/pos/orders/${orderId}/payments`, paymentData),
  getPayments: (orderId) => api.get(`/pos/orders/${orderId}/payments`),
  processRefund: (orderId, refundData) => api.post(`/pos/orders/${orderId}/refund`, refundData),
  voidOrder: (orderId, reason, voidedBy) =>
    api.post(`/pos/orders/${orderId}/void`, null, { params: { reason, voidedBy } }),
  addTip: (orderId, tipAmount) =>
    api.post(`/pos/orders/${orderId}/tip`, null, { params: { tipAmount } }),
  closeOrder: (orderId) => api.post(`/pos/orders/${orderId}/close`),
  applyServiceFee: (orderId, serviceFeePercent) =>
    api.post(`/pos/orders/${orderId}/service-fee`, null, { params: { serviceFeePercent } }),
  applyServiceFeeAmount: (orderId, serviceFeeAmount) =>
    api.post(`/pos/orders/${orderId}/service-fee-amount`, null, { params: { serviceFeeAmount } }),
  updateGuestCount: (orderId, guestCount) =>
    api.patch(`/pos/orders/${orderId}/guest-count`, null, { params: { guestCount } }),
};

export const waiterOrderAPI = {
  createOrder: (orderData, waiterId) => api.post('/waiter/orders', orderData, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  getOrder: (orderId) => api.get(`/waiter/orders/${orderId}`),
  getTableOrders: (tableId) => api.get(`/waiter/orders/table/${tableId}`),
  addItems: (orderId, items, waiterId) => api.post(`/waiter/orders/${orderId}/items`, items, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  updateItem: (orderId, itemId, data, waiterId) => api.put(`/waiter/orders/${orderId}/items/${itemId}`, data, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  removeItem: (orderId, itemId, waiterId) => api.delete(`/waiter/orders/${orderId}/items/${itemId}`, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  submitToKitchen: (orderId, waiterId) => api.post(`/waiter/orders/${orderId}/submit`, {}, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  markItemDelivered: (orderId, itemId, waiterId) => api.post(`/waiter/orders/${orderId}/items/${itemId}/deliver`, {}, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  requestBill: (orderId, waiterId) => api.post(`/waiter/orders/${orderId}/bill`, {}, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  closeOrder: (orderId, waiterId) => api.post(`/waiter/orders/${orderId}/close`, {}, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  getOrderHistory: (orderId) => api.get(`/waiter/orders/${orderId}/history`),
};

export const financialAPI = {
  // Purchase Orders
  getPurchaseOrders: (restaurantId) => api.get('/financial/purchase-orders', { params: { restaurantId } }),
  getPurchaseOrderById: (id) => api.get(`/financial/purchase-orders/${id}`),
  createPurchaseOrder: (data) => api.post('/financial/purchase-orders', data),
  approvePurchaseOrder: (id, approvedBy) => api.post(`/financial/purchase-orders/${id}/approve`, null, { params: { approvedBy } }),
  receivePurchaseOrder: (id, data) => api.post(`/financial/purchase-orders/${id}/receive`, data),
  recordPOPayment: (id, data) => api.post(`/financial/purchase-orders/${id}/payment`, data),

  // Expenses
  getExpenses: (restaurantId, startDate = null, endDate = null) => {
    const params = { restaurantId };
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    return api.get('/financial/expenses', { params });
  },
  getExpenseById: (id) => api.get(`/financial/expenses/${id}`),
  getUnpaidExpenses: (restaurantId) => api.get('/financial/expenses/unpaid', { params: { restaurantId } }),
  createExpense: (data) => api.post('/financial/expenses', data),
  approveExpense: (id, approvedBy) => api.post(`/financial/expenses/${id}/approve`, null, { params: { approvedBy } }),
  recordExpensePayment: (id, paymentDate, recordedBy) => api.post(`/financial/expenses/${id}/pay`, null, { params: { paymentDate, recordedBy } }),
  deleteExpense: (id) => api.delete(`/financial/expenses/${id}`),

  // Financial Reports
  getProfitLossReport: (restaurantId, startDate, endDate) =>
    api.get('/financial/reports/profit-loss', { params: { restaurantId, startDate, endDate } }),
  getBalanceSheet: (restaurantId, asOfDate) =>
    api.get('/financial/reports/balance-sheet', { params: { restaurantId, asOfDate } }),
  getCashFlowReport: (restaurantId, startDate, endDate) =>
    api.get('/financial/reports/cash-flow', { params: { restaurantId, startDate, endDate } }),
  getCogsReport: (restaurantId, startDate, endDate) =>
    api.get('/financial/reports/cogs', { params: { restaurantId, startDate, endDate } }),

  // Chart of Accounts
  getAccounts: (restaurantId) =>
    api.get('/financial/accounts', { params: { restaurantId } }),
  initializeAccounts: (restaurantId) =>
    api.post(`/financial/accounts/initialize/${restaurantId}`),

  // Dashboard
  getDashboard: (restaurantId, startDate, endDate) =>
    api.get('/dashboard', { params: { restaurantId, startDate, endDate } }),
  getTodaySummary: (restaurantId) =>
    api.get('/dashboard/today', { params: { restaurantId } }),
  getWeekSummary: (restaurantId) =>
    api.get('/dashboard/week', { params: { restaurantId } }),
  getMonthSummary: (restaurantId) =>
    api.get('/dashboard/month', { params: { restaurantId } }),
  getCurrentBusinessDay: (restaurantId) =>
    api.get('/dashboard/current-business-day', { params: { restaurantId } }),
};

export const inventoryAPI = {
  // Ingredients CRUD
  getIngredients: (restaurantId) => api.get('/inventory/ingredients', { params: { restaurantId } }),
  getIngredientById: (id) => api.get(`/inventory/ingredients/${id}`),
  createIngredient: (data) => api.post('/inventory/ingredients', data),
  updateIngredient: (id, data) => api.put(`/inventory/ingredients/${id}`, data),
  deleteIngredient: (id) => api.delete(`/inventory/ingredients/${id}`),

  // Stock alerts
  getLowStock: (restaurantId) => api.get('/inventory/ingredients/low-stock', { params: { restaurantId } }),
  getReorderIngredients: (restaurantId) => api.get('/inventory/ingredients/reorder', { params: { restaurantId } }),

  // Stock management
  addStock: (id, data) => api.post(`/inventory/ingredients/${id}/add-stock`, data),
  adjustStock: (id, data) => api.post(`/inventory/ingredients/${id}/adjust-stock`, data),

  // Transaction history
  getTransactions: (id) => api.get(`/inventory/ingredients/${id}/transactions`),

  // Recipes
  getRecipesByProduct: (productId) => api.get(`/inventory/recipes/product/${productId}`),
  getRecipesByIngredient: (ingredientId) => api.get(`/inventory/recipes/ingredient/${ingredientId}`),
  createRecipe: (data) => api.post('/inventory/recipes', data),
  updateRecipe: (id, data) => api.put(`/inventory/recipes/${id}`, data),
  deleteRecipe: (id) => api.delete(`/inventory/recipes/${id}`),
  checkProductAvailability: (productId, quantity = 1) =>
    api.get(`/inventory/recipes/product/${productId}/check-availability`, { params: { quantity } }),
};

// Recipes API (separate export for convenience)
export const recipesAPI = {
  getProductRecipe: (productId) => api.get(`/inventory/recipes/product/${productId}`),
  getIngredientUsage: (ingredientId) => api.get(`/inventory/recipes/ingredient/${ingredientId}`),
  createRecipe: (data) => api.post('/inventory/recipes', data),
  updateRecipe: (id, data) => api.put(`/inventory/recipes/${id}`, data),
  deleteRecipe: (id) => api.delete(`/inventory/recipes/${id}`),
  checkAvailability: (productId, quantity = 1) =>
    api.get(`/inventory/recipes/product/${productId}/check-availability`, { params: { quantity } }),
  recalculateCost: (productId) => api.post(`/inventory/recipes/product/${productId}/recalculate-cost`),
  recalculateAllCosts: () => api.post('/inventory/recipes/recalculate-all-costs'),
  getCostBreakdown: (productId) => api.get(`/inventory/recipes/product/${productId}/cost-breakdown`),
};

export const inventoryBatchAPI = {
  // Batch CRUD
  createBatch: (data) => api.post('/inventory/batches', data),
  getBatchesByIngredient: (ingredientId) => api.get(`/inventory/batches/ingredient/${ingredientId}`),
  updateBatchExpiry: (batchId, expiryDate) =>
    api.patch(`/inventory/batches/${batchId}/expiry`, { expiryDate }),
  writeOffBatch: (batchId, reason) =>
    api.post(`/inventory/batches/${batchId}/write-off`, { reason }),

  // Expiry management
  getExpiringBatches: (restaurantId, withinDays = 7) =>
    api.get('/inventory/batches/expiring', { params: { restaurantId, withinDays } }),
  getExpiredBatches: (restaurantId) =>
    api.get('/inventory/batches/expired', { params: { restaurantId } }),
  getExpirySummary: (restaurantId, alertDays = 7) =>
    api.get('/inventory/batches/summary', { params: { restaurantId, alertDays } }),
  markExpiredBatches: (restaurantId) =>
    api.post('/inventory/batches/mark-expired', null, { params: { restaurantId } }),
};

export const stockCountAPI = {
  // Stock Count CRUD
  getAll: (restaurantId) => api.get('/inventory/stock-counts', { params: { restaurantId } }),
  getActive: (restaurantId) => api.get('/inventory/stock-counts/active', { params: { restaurantId } }),
  getById: (id) => api.get(`/inventory/stock-counts/${id}`),
  create: (data) => api.post('/inventory/stock-counts', data),

  // Stock Count workflow
  start: (id, countedBy) => api.post(`/inventory/stock-counts/${id}/start`, null, { params: { countedBy } }),
  submitForReview: (id, reviewedBy) => api.post(`/inventory/stock-counts/${id}/submit-review`, null, { params: { reviewedBy } }),
  approve: (id, data) => api.post(`/inventory/stock-counts/${id}/approve`, data),
  cancel: (id, reason, cancelledBy) => api.post(`/inventory/stock-counts/${id}/cancel`, null, { params: { reason, cancelledBy } }),

  // Stock Count Items
  recordCount: (data) => api.post('/inventory/stock-counts/items/record-count', data),
  setVarianceReason: (data) => api.post('/inventory/stock-counts/items/variance-reason', data),
  getItemsWithVariance: (id) => api.get(`/inventory/stock-counts/${id}/variances`),

  // Variance Reports
  getVarianceReport: (restaurantId, startDate, endDate) =>
    api.get('/inventory/stock-counts/variance-report', { params: { restaurantId, startDate, endDate } }),
};

export const wasteAPI = {
  // Waste Records CRUD
  getAll: (restaurantId, params = {}) =>
    api.get('/inventory/waste', { params: { restaurantId, ...params } }),
  getById: (id) => api.get(`/inventory/waste/${id}`),
  record: (data) => api.post('/inventory/waste', data),
  delete: (id) => api.delete(`/inventory/waste/${id}`),

  // Waste Reports
  getReport: (restaurantId, startDate, endDate) =>
    api.get('/inventory/waste/report', { params: { restaurantId, startDate, endDate } }),

  // Get waste reasons
  getReasons: () => api.get('/inventory/waste/reasons'),
};

export const stockAlertAPI = {
  // Subscriptions
  getSubscriptions: (restaurantId) => api.get('/stock-alerts/subscriptions', { params: { restaurantId } }),
  getSubscriptionById: (id) => api.get(`/stock-alerts/subscriptions/${id}`),
  createSubscription: (data) => api.post('/stock-alerts/subscriptions', data),
  updateSubscription: (id, data) => api.put(`/stock-alerts/subscriptions/${id}`, data),
  toggleSubscription: (id) => api.patch(`/stock-alerts/subscriptions/${id}/toggle`),
  deleteSubscription: (id) => api.delete(`/stock-alerts/subscriptions/${id}`),

  // Alerts
  trigger: (restaurantId) => api.post(`/stock-alerts/trigger/${restaurantId}`),
  getSummary: (restaurantId) => api.get(`/stock-alerts/summary/${restaurantId}`),
};

export const supplierAPI = {
  getAll: (restaurantId, activeOnly = false) =>
    api.get('/inventory/suppliers', { params: { restaurantId, activeOnly } }),
  getById: (id) => api.get(`/inventory/suppliers/${id}`),
  create: (data) => api.post('/inventory/suppliers', data),
  update: (id, data) => api.put(`/inventory/suppliers/${id}`, data),
  delete: (id) => api.delete(`/inventory/suppliers/${id}`),
  toggle: (id) => api.post(`/inventory/suppliers/${id}/toggle`),
};

export const poSuggestionAPI = {
  getSuggestions: (restaurantId) =>
    api.get('/inventory/po-suggestions', { params: { restaurantId } }),
  getCount: (restaurantId) =>
    api.get('/inventory/po-suggestions/count', { params: { restaurantId } }),
  generate: (data) => api.post('/inventory/po-suggestions/generate', data),
  generateAll: (restaurantId) =>
    api.post('/inventory/po-suggestions/generate-all', null, { params: { restaurantId } }),
};

export const valuationAPI = {
  // Valuation Settings
  getValuationMethod: (restaurantId) =>
    api.get('/inventory/valuation/settings', { params: { restaurantId } }),
  setValuationMethod: (data) => api.post('/inventory/valuation/settings', data),

  // Inventory Valuation
  calculateInventoryValue: (restaurantId, method = null) => {
    const params = { restaurantId };
    if (method) params.method = method;
    return api.get('/inventory/valuation/calculate', { params });
  },
  compareValuationMethods: (restaurantId) =>
    api.get('/inventory/valuation/compare', { params: { restaurantId } }),
  getIngredientValuation: (ingredientId, method = null) => {
    const params = method ? { method } : {};
    return api.get(`/inventory/valuation/ingredient/${ingredientId}`, { params });
  },
  recalculateWAC: (ingredientId) =>
    api.post(`/inventory/valuation/ingredient/${ingredientId}/recalculate-wac`),

  // Cost History
  getCostHistory: (ingredientId) =>
    api.get(`/inventory/valuation/cost-history/${ingredientId}`),
  getCostHistoryPaginated: (ingredientId, page = 0, size = 20) =>
    api.get(`/inventory/valuation/cost-history/${ingredientId}/paginated`, {
      params: { page, size }
    }),
  getCostHistoryInRange: (ingredientId, startDate, endDate) =>
    api.get(`/inventory/valuation/cost-history/${ingredientId}/range`, {
      params: { startDate, endDate }
    }),
  recordCostChange: (ingredientId, data) =>
    api.post(`/inventory/valuation/cost-history/${ingredientId}`, data),
  getCostVariance: (ingredientId, startDate, endDate) =>
    api.get(`/inventory/valuation/cost-variance/${ingredientId}`, {
      params: { startDate, endDate }
    }),

  // Consumption History
  getConsumptionHistory: (ingredientId) =>
    api.get(`/inventory/valuation/consumption/${ingredientId}`),
  getConsumptionSummary: (restaurantId, startDate, endDate) =>
    api.get('/inventory/valuation/consumption/summary', {
      params: { restaurantId, startDate, endDate }
    }),
  getConsumptionStats: (ingredientId, startDate, endDate) =>
    api.get(`/inventory/valuation/consumption/${ingredientId}/stats`, {
      params: { startDate, endDate }
    }),

  // COGS
  getOrderCOGS: (orderId) => api.get(`/inventory/valuation/cogs/order/${orderId}`),
  getTotalCOGS: (restaurantId, startDate, endDate) =>
    api.get('/inventory/valuation/cogs/total', {
      params: { restaurantId, startDate, endDate }
    }),

  // Valuation Reports
  getComparisonReport: (restaurantId) =>
    api.get('/inventory/valuation/reports/comparison', { params: { restaurantId } }),
  getInventoryValuationReport: (restaurantId, method = null) => {
    const params = { restaurantId };
    if (method) params.method = method;
    return api.get('/inventory/valuation/reports/inventory', { params });
  },
  getCostVarianceReport: (restaurantId, startDate, endDate) =>
    api.get('/inventory/valuation/reports/variance', {
      params: { restaurantId, startDate, endDate }
    }),
};

export const financialAlertAPI = {
  // Subscriptions
  getSubscriptions: (restaurantId) =>
    api.get('/notifications/financial-alerts/restaurant/' + restaurantId),
  createSubscription: (data) =>
    api.post('/notifications/financial-alerts', data),
  updateSubscription: (id, data) =>
    api.put('/notifications/financial-alerts/' + id, data),
  toggleSubscription: (id) =>
    api.post('/notifications/financial-alerts/' + id + '/toggle'),
  deleteSubscription: (id) =>
    api.delete('/notifications/financial-alerts/' + id),

  // Reports
  trigger: (restaurantId) =>
    api.post('/notifications/financial-alerts/trigger/' + restaurantId),
  getMetrics: (restaurantId, date = null) => {
    const params = date ? { date } : {};
    return api.get('/notifications/financial-alerts/metrics/' + restaurantId, { params });
  },
};

export const pricingAPI = {
  // Pricing Analytics
  getAnalytics: (restaurantId, params = {}) =>
    api.get(`/pricing/analytics/${restaurantId}`, { params }),
  getRecommendations: (restaurantId, targetMargin = null) => {
    const params = targetMargin ? { targetMargin } : {};
    return api.get(`/pricing/recommendations/${restaurantId}`, { params });
  },
  getProfitability: (restaurantId, params = {}) =>
    api.get(`/pricing/profitability/${restaurantId}`, { params }),
  getProductProfitability: (restaurantId, productId, params = {}) =>
    api.get(`/pricing/profitability/${restaurantId}/product/${productId}`, { params }),
  calculateCostPlus: (costPrice, targetMarginPercentage) =>
    api.get('/pricing/calculate/cost-plus', { params: { costPrice, targetMarginPercentage } }),
  applyPsychologicalPricing: (price) =>
    api.get('/pricing/calculate/psychological', { params: { price } }),
};

export const printerAPI = {
  // Printer CRUD
  getPrinters: (restaurantId) => api.get('/settings/printers', { params: { restaurantId } }),
  getPrinterById: (id) => api.get(`/settings/printers/${id}`),
  createPrinter: (data) => api.post('/settings/printers', data),
  updatePrinter: (id, data) => api.put(`/settings/printers/${id}`, data),
  deletePrinter: (id) => api.delete(`/settings/printers/${id}`),

  // Utility functions
  getAvailablePrinters: () => api.get('/settings/printers/available'),
  testPrinter: (id) => api.post(`/settings/printers/${id}/test`),
};

export default api;
