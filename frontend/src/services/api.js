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
  getAvailable: (restaurantId) => api.get(`/restaurants/${restaurantId}/tables/available`),
  getSections: (restaurantId) => api.get(`/restaurants/${restaurantId}/tables/sections`),
  getBySection: (restaurantId, section) => api.get(`/restaurants/${restaurantId}/tables/section/${section}`),
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
};

export const inventoryAPI = {
  getIngredients: (restaurantId) => api.get('/inventory/ingredients', { params: { restaurantId } }),
  getIngredientById: (id) => api.get(`/inventory/ingredients/${id}`),
  createIngredient: (data) => api.post('/inventory/ingredients', data),
  updateIngredient: (id, data) => api.put(`/inventory/ingredients/${id}`, data),
  deleteIngredient: (id) => api.delete(`/inventory/ingredients/${id}`),

  // Stock management
  getStock: (restaurantId) => api.get('/inventory/stock', { params: { restaurantId } }),
  updateStock: (id, data) => api.put(`/inventory/stock/${id}`, data),
  recordStockMovement: (data) => api.post('/inventory/stock/movement', data),
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

export const promotionAPI = {
  // Promotions CRUD
  getPromotions: (restaurantId, params = {}) =>
    api.get(`/restaurants/${restaurantId}/promotions`, { params }),
  getActivePromotions: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/promotions/active`),
  getPromotion: (id) => api.get(`/promotions/${id}`),
  createPromotion: (restaurantId, data) =>
    api.post(`/restaurants/${restaurantId}/promotions`, data),
  updatePromotion: (id, data) => api.put(`/promotions/${id}`, data),
  deletePromotion: (id) => api.delete(`/promotions/${id}`),
  togglePromotion: (id) => api.post(`/promotions/${id}/toggle`),

  // Coupons CRUD
  getCoupons: (restaurantId, params = {}) =>
    api.get(`/restaurants/${restaurantId}/coupons`, { params }),
  getCouponsByPromotion: (promotionId, params = {}) =>
    api.get(`/promotions/${promotionId}/coupons`, { params }),
  getCoupon: (id) => api.get(`/coupons/${id}`),
  getCouponByCode: (code) => api.get(`/coupons/by-code/${code}`),
  createCoupon: (data) => api.post('/coupons', data),
  generateCoupons: (data) => api.post('/coupons/generate-batch', data),
  updateCoupon: (id, data) => api.put(`/coupons/${id}`, data),
  deleteCoupon: (id) => api.delete(`/coupons/${id}`),
  toggleCoupon: (id) => api.post(`/coupons/${id}/toggle`),

  // Validation
  validateCoupon: (data) => api.post('/coupons/validate', data),

  // Customer coupons
  getCustomerCoupons: (customerId) => api.get(`/customers/${customerId}/coupons`),
};

// Happy Hour API
export const happyHourAPI = {
  // Happy Hours CRUD
  getHappyHours: (restaurantId, params = {}) =>
    api.get(`/restaurants/${restaurantId}/happy-hours`, { params }),
  getHappyHour: (id) => api.get(`/happy-hours/${id}`),
  createHappyHour: (restaurantId, data) =>
    api.post(`/restaurants/${restaurantId}/happy-hours`, data),
  updateHappyHour: (id, data) => api.put(`/happy-hours/${id}`, data),
  deleteHappyHour: (id) => api.delete(`/happy-hours/${id}`),
  toggleHappyHour: (id) => api.patch(`/happy-hours/${id}/toggle`),

  // Active status
  getActiveHappyHour: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/happy-hours/active`),
  isHappyHourActive: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/happy-hours/is-active`),
};

// Bundle API
export const bundleAPI = {
  // Bundles CRUD
  getBundles: (restaurantId, params = {}) =>
    api.get(`/restaurants/${restaurantId}/bundles`, { params }),
  getMenuBundles: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/bundles/menu`),
  getBundle: (id) => api.get(`/bundles/${id}`),
  createBundle: (restaurantId, data) =>
    api.post(`/restaurants/${restaurantId}/bundles`, data),
  updateBundle: (id, data) => api.put(`/bundles/${id}`, data),
  deleteBundle: (id) => api.delete(`/bundles/${id}`),
  toggleBundle: (id) => api.patch(`/bundles/${id}/toggle`),

  // Price calculation and validation
  calculatePrice: (id, selectedOptionIds) =>
    api.post(`/bundles/${id}/calculate-price`, selectedOptionIds),
  validateOrder: (id, selectedOptionIds) =>
    api.post(`/bundles/${id}/validate`, selectedOptionIds),
};

// Referral Program API
export const referralAPI = {
  // Settings
  getSettings: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/referrals/settings`),
  saveSettings: (restaurantId, data) =>
    api.post(`/restaurants/${restaurantId}/referrals/settings`, data),
  toggleProgram: (restaurantId) =>
    api.post(`/restaurants/${restaurantId}/referrals/settings/toggle`),

  // Referral codes
  generateCode: (restaurantId, customerId) =>
    api.post(`/restaurants/${restaurantId}/referrals/codes/generate`, null, { params: { customerId } }),
  getCustomerCode: (restaurantId, customerId) =>
    api.get(`/restaurants/${restaurantId}/referrals/codes/customer/${customerId}`),
  getCodes: (restaurantId, params = {}) =>
    api.get(`/restaurants/${restaurantId}/referrals/codes`, { params }),
  validateCode: (restaurantId, code) =>
    api.get(`/restaurants/${restaurantId}/referrals/codes/validate`, { params: { code } }),

  // Referrals
  applyReferral: (restaurantId, data) =>
    api.post(`/restaurants/${restaurantId}/referrals/apply`, data),
  getReferrals: (restaurantId, params = {}) =>
    api.get(`/restaurants/${restaurantId}/referrals`, { params }),
  getCustomerReferrals: (restaurantId, customerId) =>
    api.get(`/restaurants/${restaurantId}/referrals/customer/${customerId}`),
  getStats: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/referrals/stats`),
};

export default api;
