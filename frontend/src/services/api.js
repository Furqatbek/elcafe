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

// Token refresh state - shared across all concurrent requests
let isRefreshing = false;
let refreshSubscribers = []; // queue of { resolve, reject, config } waiting for refresh

const onRefreshSuccess = (newToken) => {
  refreshSubscribers.forEach(({ resolve, config }) => {
    config.headers.Authorization = `Bearer ${newToken}`;
    resolve(api(config));
  });
  refreshSubscribers = [];
};

const onRefreshFailure = (error) => {
  refreshSubscribers.forEach(({ reject }) => reject(error));
  refreshSubscribers = [];
};

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

// Response interceptor to handle token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      // If a refresh is already in progress, queue this request instead of firing another refresh
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          refreshSubscribers.push({ resolve, reject, config: originalRequest });
        });
      }

      isRefreshing = true;

      try {
        const refreshToken = localStorage.getItem('refresh_token');

        if (!refreshToken || refreshToken === '' || refreshToken === 'null' || refreshToken === 'undefined') {
          throw new Error('No refresh token available');
        }

        const refreshTokenExpiry = localStorage.getItem('refresh_token_expiry');
        if (refreshTokenExpiry && Date.now() >= parseInt(refreshTokenExpiry)) {
          throw new Error('Refresh token expired');
        }

        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken,
        });

        const { accessToken, refreshToken: newRefreshToken } = response.data.data;
        const now = Date.now();

        const decodedAccess = decodeJWT(accessToken);
        const accessTokenExpiry = decodedAccess?.exp
          ? decodedAccess.exp * 1000
          : now + (15 * 60 * 1000);

        localStorage.setItem('access_token', accessToken);
        localStorage.setItem('access_token_expiry', accessTokenExpiry.toString());

        if (newRefreshToken) {
          const decodedRefresh = decodeJWT(newRefreshToken);
          const newRefreshExpiry = decodedRefresh?.exp
            ? decodedRefresh.exp * 1000
            : now + (7 * 24 * 60 * 60 * 1000);
          localStorage.setItem('refresh_token', newRefreshToken);
          localStorage.setItem('refresh_token_expiry', newRefreshExpiry.toString());
        }

        localStorage.setItem('token_set_time', now.toString());

        // Resolve all queued requests with the new token
        onRefreshSuccess(accessToken);

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        // Reject all queued requests
        onRefreshFailure(refreshError);

        // Clear all auth data
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('access_token_expiry');
        localStorage.removeItem('refresh_token_expiry');
        localStorage.removeItem('token_set_time');

        // Only redirect to login if we're on the admin app (not customer/order pages)
        const isCustomerApp = window.location.pathname.startsWith('/order');
        if (!isCustomerApp) {
          window.location.href = '/admin/login';
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
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
  changePassword: (currentPassword, newPassword) => api.post('/auth/change-password', { currentPassword, newPassword }),
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
  toggleProductStatus: (id) => api.patch(`/products/${id}/toggle-status`),
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
  getByShift: (shiftId) => api.get(`/orders/by-shift/${shiftId}`),
  getAll: (params) => api.get('/orders', { params }),
  getPending: () => api.get('/orders/pending'),
  getByRestaurant: (restaurantId) => api.get(`/orders/restaurant/${restaurantId}`),
  getSelfServiceOrders: (params) => api.get('/orders/self-service', { params }),
  getExternalOrders: (params) => api.get('/orders/external', { params }),
  updateStatus: (id, status, notes, changedBy = 'OPERATOR') =>
    api.patch(`/orders/${id}/status`, null, { params: { status, notes, changedBy } }),
  revertOrder: (id, { targetStatus, reason, revertedBy }) =>
    api.patch(`/orders/${id}/revert`, null, { params: { targetStatus, reason, revertedBy } }),
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

export const systemUserAPI = {
  getAll: () => api.get('/system-users'),
  create: (data) => api.post('/system-users', data),
  update: (id, data) => api.put(`/system-users/${id}`, data),
  delete: (id) => api.delete(`/system-users/${id}`),
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
  bulkCreate: (data) => api.post('/tables/bulk', data),
  bulkDelete: (ids) => api.delete('/tables/bulk', { data: { ids } }),
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

// Waiter Performance & KPI API
export const waiterPerformanceAPI = {
  // KPI Configuration
  getKPIConfigs: (restaurantId) => api.get(`/waiter-performance/restaurant/${restaurantId}/kpi-configs`),
  saveKPIConfig: (restaurantId, data) => api.post(`/waiter-performance/restaurant/${restaurantId}/kpi-config`, data),
  deleteKPIConfig: (configId) => api.delete(`/waiter-performance/kpi-config/${configId}`),
  getEffectiveKPI: (waiterId, restaurantId) =>
    api.get(`/waiter-performance/waiter/${waiterId}/kpi-config`, { params: { restaurantId } }),

  // Performance Data
  getTodayPerformance: (waiterId) => api.get(`/waiter-performance/waiter/${waiterId}/today`),
  getPerformanceByDate: (waiterId, date) => api.get(`/waiter-performance/waiter/${waiterId}/date/${date}`),
  getPerformanceHistory: (waiterId, params = {}) =>
    api.get(`/waiter-performance/waiter/${waiterId}/history`, { params }),
  getPerformanceSummary: (waiterId, startDate, endDate) =>
    api.get(`/waiter-performance/waiter/${waiterId}/summary`, { params: { startDate, endDate } }),
  getLeaderboard: (restaurantId, startDate, endDate) =>
    api.get(`/waiter-performance/restaurant/${restaurantId}/leaderboard`, { params: { startDate, endDate } }),

  // Manual Recording
  recordComplaint: (waiterId, restaurantId) =>
    api.post(`/waiter-performance/waiter/${waiterId}/record-complaint`, null, { params: { restaurantId } }),
  recordCompliment: (waiterId, restaurantId) =>
    api.post(`/waiter-performance/waiter/${waiterId}/record-compliment`, null, { params: { restaurantId } }),
  recordRating: (waiterId, restaurantId, rating) =>
    api.post(`/waiter-performance/waiter/${waiterId}/record-rating`, null, { params: { restaurantId, rating } }),
  recordTip: (waiterId, restaurantId, amount) =>
    api.post(`/waiter-performance/waiter/${waiterId}/record-tip`, null, { params: { restaurantId, amount } }),
  shiftStart: (waiterId, restaurantId) =>
    api.post(`/waiter-performance/waiter/${waiterId}/shift-start`, null, { params: { restaurantId } }),
  shiftEnd: (waiterId, restaurantId) =>
    api.post(`/waiter-performance/waiter/${waiterId}/shift-end`, null, { params: { restaurantId } }),
};

// Waiter Commission API
export const waiterCommissionAPI = {
  // Configuration
  configureCommission: (waiterId, data) => api.put(`/waiter-commissions/waiter/${waiterId}/config`, data),

  // Summary & History
  getCommissionSummary: (waiterId, startDate, endDate) =>
    api.get(`/waiter-commissions/waiter/${waiterId}/summary`, { params: { startDate, endDate } }),
  getCommissionHistory: (waiterId, params = {}) =>
    api.get(`/waiter-commissions/waiter/${waiterId}/history`, { params }),
  getQuickStats: (waiterId) => api.get(`/waiter-commissions/waiter/${waiterId}/quick-stats`),

  // Restaurant-level
  getRestaurantCommissions: (restaurantId, params = {}) =>
    api.get(`/waiter-commissions/restaurant/${restaurantId}`, { params }),
  getRestaurantReport: (restaurantId, startDate, endDate) =>
    api.get(`/waiter-commissions/restaurant/${restaurantId}/report`, { params: { startDate, endDate } }),

  // Actions
  approveCommissions: (commissionIds) => api.post('/waiter-commissions/approve', commissionIds),
};

export const shiftAPI = {
  getActive: (restaurantId) => api.get(`/restaurants/${restaurantId}/pos/shifts/active`),
  getByDate: (restaurantId, date) => api.get(`/restaurants/${restaurantId}/pos/shifts/date/${date}`),
  getByDateRange: (restaurantId, startDate, endDate) =>
    api.get(`/restaurants/${restaurantId}/pos/shifts/date-range`, { params: { startDate, endDate } }),
  clockIn: (restaurantId, data) => api.post(`/restaurants/${restaurantId}/pos/shifts/clock-in`, data),
  clockOut: (restaurantId, shiftId, data) => api.post(`/restaurants/${restaurantId}/pos/shifts/${shiftId}/clock-out`, data),
  startBreak: (restaurantId, shiftId) => api.post(`/restaurants/${restaurantId}/pos/shifts/${shiftId}/break/start`),
  endBreak: (restaurantId, shiftId) => api.post(`/restaurants/${restaurantId}/pos/shifts/${shiftId}/break/end`),
  approve: (restaurantId, shiftId) => api.post(`/restaurants/${restaurantId}/pos/shifts/${shiftId}/approve`),
  resendTelegram: (restaurantId, shiftId) => api.post(`/restaurants/${restaurantId}/pos/shifts/${shiftId}/resend-telegram`),
  getHistory: (restaurantId, employeeId) => api.get(`/restaurants/${restaurantId}/pos/shifts/employees/${employeeId}/history`),
  getEndOfDayReport: (restaurantId, date) => api.get(`/restaurants/${restaurantId}/pos/shifts/end-of-day/${date}`),
  getFinancialReport: (restaurantId, date, hourlyRate) => api.get(`/shift-reports/restaurant/${restaurantId}`, { params: { date, hourlyRate } }),
};

export const posAPI = {
  createOrder: (orderData) => api.post('/pos/orders', orderData),
  getCategories: (restaurantId) => api.get('/categories', { params: { restaurantId } }),
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
  changeTable: (orderId, newTableId) =>
    api.patch(`/pos/orders/${orderId}/change-table`, null, { params: { newTableId } }),
  // Entry Fee
  applyEntryFee: (orderId, entryFee) =>
    api.post(`/pos/orders/${orderId}/entry-fee`, null, { params: { entryFee } }),
  // Discount/Promotion
  applyDiscount: (orderId, discountData) => api.post(`/pos/orders/${orderId}/discount`, discountData),
  removeDiscount: (orderId) => api.delete(`/pos/orders/${orderId}/discount`),
  validateCoupon: (orderId, couponCode) =>
    api.post(`/pos/orders/${orderId}/validate-coupon`, null, { params: { couponCode } }),
  // Happy Hour
  getActiveHappyHour: (restaurantId) =>
    api.get('/pos/orders/happy-hour/active', { params: { restaurantId } }),
  previewHappyHourDiscount: (orderId) =>
    api.get(`/pos/orders/${orderId}/happy-hour/preview`),
  applyHappyHourDiscount: (orderId) =>
    api.post(`/pos/orders/${orderId}/happy-hour/apply`),
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
  // Discount/Promotion
  applyDiscount: (orderId, discountData, waiterId) => api.post(`/waiter/orders/${orderId}/discount`, discountData, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  removeDiscount: (orderId, waiterId) => api.delete(`/waiter/orders/${orderId}/discount`, {
    headers: { 'X-Waiter-Id': waiterId }
  }),
  validateCoupon: (orderId, couponCode) =>
    api.post(`/waiter/orders/${orderId}/validate-coupon`, null, { params: { couponCode } }),
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

  // Payroll
  getPayrollEmployees: () => api.get('/financial/payroll/employees'),
  getPayroll: (restaurantId) => api.get(`/financial/payroll/restaurant/${restaurantId}`),
  getPayrollByRange: (restaurantId, startDate, endDate) => api.get(`/financial/payroll/restaurant/${restaurantId}/range`, { params: { startDate, endDate } }),
  getPayrollByEmployee: (employeeId) => api.get(`/financial/payroll/employee/${employeeId}`),
  getPendingPayroll: (restaurantId) => api.get(`/financial/payroll/restaurant/${restaurantId}/pending`),
  createPayroll: (data) => api.post('/financial/payroll', data),
  updatePayroll: (id, data) => api.put(`/financial/payroll/${id}`, data),
  approvePayroll: (id, approvedBy) => api.post(`/financial/payroll/${id}/approve`, null, { params: { approvedBy } }),
  payPayroll: (id, paymentDate, paymentMethod, transactionRef) => api.post(`/financial/payroll/${id}/pay`, null, { params: { paymentDate, paymentMethod, transactionRef } }),
  deletePayroll: (id) => api.delete(`/financial/payroll/${id}`),
  getSalaryConfigs: (restaurantId) => api.get(`/financial/salary-config/restaurant/${restaurantId}`),
  createSalaryConfig: (data) => api.post('/financial/salary-config', data),
  updateSalaryConfig: (id, data) => api.put(`/financial/salary-config/${id}`, data),
  deleteSalaryConfig: (id) => api.delete(`/financial/salary-config/${id}`),
  paySalaryNow: (id) => api.post(`/financial/salary-config/${id}/pay-now`),

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
  getIngredients: (restaurantId, categoryId) => api.get('/inventory/ingredients', { params: { restaurantId, categoryId: categoryId || undefined } }),
  getIngredientCategories: (restaurantId) => api.get('/inventory/ingredient-categories', { params: { restaurantId } }),
  createIngredientCategory: (data) => api.post('/inventory/ingredient-categories', data),
  updateIngredientCategory: (id, data) => api.put(`/inventory/ingredient-categories/${id}`, data),
  deleteIngredientCategory: (id) => api.delete(`/inventory/ingredient-categories/${id}`),
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

export const productionBatchAPI = {
  create: (data) => api.post('/inventory/production-batches', data),
  getByRestaurant: (restaurantId, params) =>
    api.get(`/inventory/production-batches/restaurant/${restaurantId}`, { params }),
  getById: (id) => api.get(`/inventory/production-batches/${id}`),
  addInput: (id, data) => api.post(`/inventory/production-batches/${id}/inputs`, data),
  updateInput: (id, inputId, data) =>
    api.put(`/inventory/production-batches/${id}/inputs/${inputId}`, data),
  reloadRecipe: (id) => api.post(`/inventory/production-batches/${id}/reload-recipe`),
  start: (id) => api.post(`/inventory/production-batches/${id}/start`),
  complete: (id, data) => api.post(`/inventory/production-batches/${id}/complete`, data),
  recordWaste: (id, data) => api.post(`/inventory/production-batches/${id}/waste`, data),
  getAvailable: (restaurantId) =>
    api.get(`/inventory/production-batches/restaurant/${restaurantId}/available`),
  getReport: (restaurantId, params) =>
    api.get(`/inventory/production-batches/restaurant/${restaurantId}/report`, { params }),
  delete: (id) => api.delete(`/inventory/production-batches/${id}`),
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

export const receiptTemplateAPI = {
  getTemplate: (restaurantId) => api.get('/settings/receipt-template', { params: { restaurantId } }),
  saveTemplate: (restaurantId, data) => api.put('/settings/receipt-template', data, { params: { restaurantId } }),
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

export const kitchenStationAPI = {
  // Kitchen Station CRUD
  getStations: (restaurantId) => api.get('/kitchen/stations', { params: { restaurantId } }),
  getActiveStations: (restaurantId) => api.get('/kitchen/stations/active', { params: { restaurantId } }),
  getStation: (id) => api.get(`/kitchen/stations/${id}`),
  createStation: (data) => api.post('/kitchen/stations', data),
  updateStation: (id, data) => api.put(`/kitchen/stations/${id}`, data),
  deleteStation: (id) => api.delete(`/kitchen/stations/${id}`),
  toggleStation: (id) => api.patch(`/kitchen/stations/${id}/toggle`),
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

  // Analytics
  getDiscountAnalytics: (restaurantId, startDate, endDate) =>
    api.get(`/restaurants/${restaurantId}/promotions/analytics`, { params: { startDate, endDate } }),
  getPromotionPerformance: (promotionId) =>
    api.get(`/promotions/${promotionId}/analytics`),
  getAllPromotionsPerformance: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/promotions/analytics/all`),
  getDiscountTrends: (restaurantId, startDate, endDate) =>
    api.get(`/restaurants/${restaurantId}/promotions/analytics/trends`, { params: { startDate, endDate } }),
  getTopCoupons: (restaurantId, startDate, endDate, limit = 10) =>
    api.get(`/restaurants/${restaurantId}/promotions/analytics/top-coupons`, { params: { startDate, endDate, limit } }),
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
  getMenuBundles: (restaurantId, includeAll = false) =>
    api.get(`/restaurants/${restaurantId}/bundles/menu`, { params: { includeAll } }),
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

// Loyalty Milestones API
export const milestoneAPI = {
  // Milestones CRUD
  getMilestones: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/milestones`),
  getMilestone: (id) => api.get(`/milestones/${id}`),
  createMilestone: (restaurantId, data) =>
    api.post(`/restaurants/${restaurantId}/milestones`, data),
  updateMilestone: (id, data) => api.put(`/milestones/${id}`, data),
  deleteMilestone: (id) => api.delete(`/milestones/${id}`),

  // Customer progress
  getCustomerProgress: (restaurantId, customerId) =>
    api.get(`/restaurants/${restaurantId}/milestones/customers/${customerId}/progress`),
  getPendingRewards: (customerId) =>
    api.get(`/milestones/customers/${customerId}/pending-rewards`),
  redeemReward: (milestoneId, customerId) =>
    api.post(`/milestones/${milestoneId}/customers/${customerId}/redeem`),
};

// SMS Marketing API
export const smsAPI = {
  // Templates
  getTemplates: (params = {}) => api.get('/sms/templates', { params }),
  getActiveTemplates: () => api.get('/sms/templates/active'),
  getTemplate: (id) => api.get(`/sms/templates/${id}`),
  createTemplate: (data) => api.post('/sms/templates', data),
  updateTemplate: (id, data) => api.put(`/sms/templates/${id}`, data),
  deleteTemplate: (id) => api.delete(`/sms/templates/${id}`),
  toggleTemplate: (id) => api.patch(`/sms/templates/${id}/toggle`),
  previewTemplate: (id, data) => api.post(`/sms/templates/${id}/preview`, data),
  getTemplateTypes: () => api.get('/sms/templates/types'),

  // Campaigns
  getCampaigns: (params = {}) => api.get('/sms/campaigns', { params }),
  getCampaign: (id) => api.get(`/sms/campaigns/${id}`),
  getCampaignStats: (id) => api.get(`/sms/campaigns/${id}/stats`),
  getCampaignRecipients: (id, params = {}) => api.get(`/sms/campaigns/${id}/recipients`, { params }),
  createCampaign: (data) => api.post('/sms/campaigns', data),
  updateCampaign: (id, data) => api.put(`/sms/campaigns/${id}`, data),
  sendCampaign: (id) => api.post(`/sms/campaigns/${id}/send`),
  cancelCampaign: (id) => api.post(`/sms/campaigns/${id}/cancel`),
  deleteCampaign: (id) => api.delete(`/sms/campaigns/${id}`),

  // Automation
  getAutomationRules: (params = {}) => api.get('/sms/automation/rules', { params }),
  getActiveAutomationRules: () => api.get('/sms/automation/rules/active'),
  getAutomationRule: (id) => api.get(`/sms/automation/rules/${id}`),
  createAutomationRule: (data) => api.post('/sms/automation/rules', data),
  updateAutomationRule: (id, data) => api.put(`/sms/automation/rules/${id}`, data),
  deleteAutomationRule: (id) => api.delete(`/sms/automation/rules/${id}`),
  toggleAutomationRule: (id) => api.patch(`/sms/automation/rules/${id}/toggle`),
  getTriggerTypes: () => api.get('/sms/automation/triggers'),

  // Logs
  getLogs: (params = {}) => api.get('/sms/logs', { params }),
  getLogsByCustomer: (customerId) => api.get(`/sms/logs/customer/${customerId}`),
  getLogsByCampaign: (campaignId) => api.get(`/sms/logs/campaign/${campaignId}`),
  getStatistics: (days = 30) => api.get('/sms/logs/statistics', { params: { days } }),
};

// Push Notifications API
export const pushAPI = {
  // VAPID Key
  getVapidKey: () => api.get('/push/vapid-key'),

  // Customer subscription
  subscribeCustomer: (data) => api.post('/push/subscribe/customer', data),

  // Admin subscription
  subscribeAdmin: (data) => api.post('/push/subscribe/admin', data),

  // Unsubscribe
  unsubscribe: (endpoint) => api.post('/push/unsubscribe', { endpoint }),

  // Check status
  getStatus: (endpoint) => api.get('/push/status', { params: { endpoint } }),

  // Admin endpoints
  sendToCustomer: (customerId, data) => api.post(`/push/admin/send/customer/${customerId}`, data),
  broadcast: (data) => api.post('/push/admin/send/broadcast', data),
  getSubscriptions: () => api.get('/push/admin/subscriptions'),
  getStats: () => api.get('/push/admin/stats'),
};

// Telegram Marketing API
export const telegramAPI = {
  // Subscribers
  getSubscribers: (params = {}) => api.get('/telegram/subscribers', { params }),
  getActiveSubscribers: (params = {}) => api.get('/telegram/subscribers/active', { params }),
  getSubscriber: (id) => api.get(`/telegram/subscribers/${id}`),
  blockSubscriber: (id) => api.post(`/telegram/subscribers/${id}/block`),
  unblockSubscriber: (id) => api.post(`/telegram/subscribers/${id}/unblock`),
  searchSubscribers: (query, params = {}) => api.get('/telegram/subscribers/search', { params: { query, ...params } }),
  getSubscriberStats: () => api.get('/telegram/subscribers/statistics'),

  // Templates
  getTemplates: (params = {}) => api.get('/telegram/templates', { params }),
  getActiveTemplates: () => api.get('/telegram/templates/active'),
  getTemplate: (id) => api.get(`/telegram/templates/${id}`),
  createTemplate: (data) => api.post('/telegram/templates', data),
  updateTemplate: (id, data) => api.put(`/telegram/templates/${id}`, data),
  deleteTemplate: (id) => api.delete(`/telegram/templates/${id}`),
  toggleTemplate: (id) => api.patch(`/telegram/templates/${id}/toggle`),
  previewTemplate: (id, data) => api.post(`/telegram/templates/${id}/preview`, data),

  // Campaigns
  getCampaigns: (params = {}) => api.get('/telegram/campaigns', { params }),
  getCampaign: (id) => api.get(`/telegram/campaigns/${id}`),
  getCampaignStats: (id) => api.get(`/telegram/campaigns/${id}/stats`),
  createCampaign: (data) => api.post('/telegram/campaigns', data),
  updateCampaign: (id, data) => api.put(`/telegram/campaigns/${id}`, data),
  sendCampaign: (id) => api.post(`/telegram/campaigns/${id}/send`),
  cancelCampaign: (id) => api.post(`/telegram/campaigns/${id}/cancel`),
  deleteCampaign: (id) => api.delete(`/telegram/campaigns/${id}`),

  // Customer Bot Config
  getCustomerBotConfigs: () => api.get('/telegram/config'),
  getActiveCustomerBotConfig: () => api.get('/telegram/config/active'),
  getCustomerBotConfig: (id) => api.get(`/telegram/config/${id}`),
  createCustomerBotConfig: (data) => api.post('/telegram/config', data),
  updateCustomerBotConfig: (id, data) => api.put(`/telegram/config/${id}`, data),
  toggleCustomerBotConfig: (id) => api.patch(`/telegram/config/${id}/toggle`),
  deleteCustomerBotConfig: (id) => api.delete(`/telegram/config/${id}`),

  // Owner Bot Config
  getOwnerBotConfigs: () => api.get('/telegram/owner-config'),
  getActiveOwnerBotConfig: () => api.get('/telegram/owner-config/active'),
  getOwnerBotConfig: (id) => api.get(`/telegram/owner-config/${id}`),
  getOwnerBotConfigByRestaurant: (restaurantId) => api.get(`/telegram/owner-config/restaurant/${restaurantId}`),
  createOwnerBotConfig: (data, restaurantId) => api.post('/telegram/owner-config', data, { params: { restaurantId } }),
  updateOwnerBotConfig: (id, data) => api.put(`/telegram/owner-config/${id}`, data),
  toggleOwnerBotConfig: (id) => api.patch(`/telegram/owner-config/${id}/toggle`),
  deleteOwnerBotConfig: (id) => api.delete(`/telegram/owner-config/${id}`),
  generateOwnerBotCode: (userId, restaurantId) => api.post('/telegram/owner-config/generate-code', null, { params: { userId, restaurantId } }),

  // Owner-bot subscribers (the people receiving Telegram notifications)
  listOwnerSubscribers: (params) => api.get('/telegram/owner-subscribers', { params }),
  getOwnerSubscriber: (id) => api.get(`/telegram/owner-subscribers/${id}`),
  updateOwnerSubscriber: (id, data) => api.patch(`/telegram/owner-subscribers/${id}`, data),
  deleteOwnerSubscriber: (id) => api.delete(`/telegram/owner-subscribers/${id}`),
};

// QR Code / Self-Service API
export const qrCodeAPI = {
  // QR Code CRUD (Admin)
  getByRestaurant: (restaurantId, params = {}) =>
    api.get(`/qr-codes/restaurant/${restaurantId}`, { params }),
  getById: (id) => api.get(`/qr-codes/${id}`),
  create: (data) => api.post('/qr-codes', data),
  generateForAllTables: (restaurantId) =>
    api.post(`/qr-codes/restaurant/${restaurantId}/generate-all`),
  toggle: (id) => api.patch(`/qr-codes/${id}/toggle`),
  delete: (id) => api.delete(`/qr-codes/${id}`),
  getImage: (id, width = 300, height = 300) =>
    api.get(`/qr-codes/${id}/image`, { params: { width, height } }),
  getStats: (restaurantId) => api.get(`/qr-codes/restaurant/${restaurantId}/stats`),

  // Self-Service Settings (Admin)
  getSettings: (restaurantId) => api.get(`/qr-codes/restaurant/${restaurantId}/settings`),
  saveSettings: (restaurantId, data) =>
    api.post(`/qr-codes/restaurant/${restaurantId}/settings`, data),
};

// Self-Service Public API (no auth required)
export const selfServiceAPI = {
  // Session
  startSession: (code) => api.post('/self-service/session/start', null, { params: { code } }),
  getSession: (token) => api.get('/self-service/session', {
    headers: { 'X-Session-Token': token }
  }),

  // Restaurant & Menu
  getRestaurantInfo: (restaurantId) => api.get(`/self-service/restaurant/${restaurantId}`),
  getCategories: (restaurantId) => api.get(`/self-service/menu/${restaurantId}/categories`),
  getProducts: (restaurantId, categoryId) =>
    api.get(`/self-service/menu/${restaurantId}/products`, { params: { categoryId } }),
  getProductDetails: (productId) => api.get(`/self-service/menu/product/${productId}`),

  // Cart
  addToCart: (token, data) => api.post('/self-service/cart/add', data, {
    headers: { 'X-Session-Token': token }
  }),
  updateCartItem: (token, itemId, quantity) =>
    api.put(`/self-service/cart/item/${itemId}`, null, {
      headers: { 'X-Session-Token': token },
      params: { quantity }
    }),
  removeFromCart: (token, itemId) => api.delete(`/self-service/cart/item/${itemId}`, {
    headers: { 'X-Session-Token': token }
  }),
  getCart: (token) => api.get('/self-service/cart', {
    headers: { 'X-Session-Token': token }
  }),
  clearCart: (token) => api.delete('/self-service/cart', {
    headers: { 'X-Session-Token': token }
  }),

  // Orders
  submitOrder: (token, data) => api.post('/self-service/order/submit', data, {
    headers: { 'X-Session-Token': token }
  }),
  getOrderStatus: (orderId) => api.get(`/self-service/order/${orderId}/status`),

  // Coupons & Promotions (public self-service endpoints)
  validateCoupon: (restaurantId, couponCode, orderTotal, customerId) =>
    api.post('/consumer/orders/validate-coupon', null, {
      params: { restaurantId, couponCode, orderTotal, customerId }
    }),
  getActivePromotions: (restaurantId) =>
    api.get(`/self-service/promotions/${restaurantId}`),
  getActiveHappyHour: (restaurantId) =>
    api.get(`/self-service/happy-hour/${restaurantId}`),

  // Bundles/Combos (public self-service endpoints)
  getMenuBundles: (restaurantId) =>
    api.get(`/self-service/bundles/${restaurantId}`),
  getBundleDetails: (bundleId) => api.get(`/bundles/${bundleId}`),
};

// Reservation API (public endpoints for customers)
export const reservationPublicAPI = {
  // Get restaurants accepting reservations
  getReservableRestaurants: () =>
    api.get('/public/restaurants/reservable'),

  // Create a reservation
  createReservation: (restaurantId, data) =>
    api.post(`/public/restaurants/${restaurantId}/reservations`, data),

  // Get reservation by confirmation code
  getByCode: (confirmationCode) =>
    api.get(`/public/reservations/${confirmationCode}`),

  // Check availability for a date
  checkAvailability: (restaurantId, date, partySize = 2) =>
    api.get(`/public/restaurants/${restaurantId}/availability`, {
      params: { date, partySize }
    }),

  // Check availability for date range
  checkAvailabilityRange: (restaurantId, startDate, endDate, partySize = 2) =>
    api.get(`/public/restaurants/${restaurantId}/availability/range`, {
      params: { startDate, endDate, partySize }
    }),

  // Cancel reservation by code
  cancelByCode: (confirmationCode, reason) =>
    api.post(`/public/reservations/${confirmationCode}/cancel`, null, {
      params: { reason }
    }),

  // Get reservations by phone
  getByPhone: (phone) =>
    api.get(`/public/reservations/phone/${phone}`),

  // Get tables with availability for reservation
  getTables: (restaurantId, date, time, partySize = 2) =>
    api.get(`/public/restaurants/${restaurantId}/tables`, {
      params: { date, time, partySize }
    }),

  // Get table sections
  getTableSections: (restaurantId) =>
    api.get(`/public/restaurants/${restaurantId}/tables/sections`),
};

// Reservation API (admin endpoints)
export const reservationAPI = {
  // Get all reservations for a restaurant
  getAll: (restaurantId, params = {}) =>
    api.get(`/restaurants/${restaurantId}/reservations`, { params }),

  // Get reservations by date
  getByDate: (restaurantId, date) =>
    api.get(`/restaurants/${restaurantId}/reservations/date/${date}`),

  // Get reservations by date range (for calendar)
  getByDateRange: (restaurantId, startDate, endDate) =>
    api.get(`/restaurants/${restaurantId}/reservations/range`, {
      params: { startDate, endDate }
    }),

  // Get single reservation
  get: (id) => api.get(`/reservations/${id}`),

  // Create reservation (admin)
  create: (restaurantId, data) =>
    api.post(`/restaurants/${restaurantId}/reservations`, data),

  // Confirm reservation
  confirm: (id) => api.post(`/reservations/${id}/confirm`),

  // Cancel reservation
  cancel: (id, reason) =>
    api.post(`/reservations/${id}/cancel`, null, { params: { reason } }),

  // Check in guest
  checkIn: (id) => api.post(`/reservations/${id}/check-in`),

  // Complete reservation
  complete: (id) => api.post(`/reservations/${id}/complete`),

  // Mark as no-show
  markNoShow: (id) => api.post(`/reservations/${id}/no-show`),

  // Assign table
  assignTable: (reservationId, tableId) =>
    api.post(`/reservations/${reservationId}/assign-table/${tableId}`),

  // Get reservation settings
  getSettings: (restaurantId) =>
    api.get(`/restaurants/${restaurantId}/reservation-settings`),

  // Update reservation settings
  updateSettings: (restaurantId, data) =>
    api.put(`/restaurants/${restaurantId}/reservation-settings`, data),
};

// Order Tracking API (public endpoints)
export const orderTrackingAPI = {
  // Get order status by order number
  getStatus: (orderNumber) =>
    api.get(`/public/orders/${orderNumber}/status`),

  // Get ETA
  getETA: (orderNumber) =>
    api.get(`/public/orders/${orderNumber}/eta`),

  // Track orders by phone
  trackByPhone: (phone) =>
    api.get(`/public/orders/track`, { params: { phone } }),
};

export const packagingRuleAPI = {
  getByRestaurant: (restaurantId) => api.get(`/packaging-rules/restaurant/${restaurantId}`),
  getByProduct: (productId) => api.get(`/packaging-rules/product/${productId}`),
  create: (data) => api.post('/packaging-rules', data),
  update: (id, data) => api.put(`/packaging-rules/${id}`, data),
  delete: (id) => api.delete(`/packaging-rules/${id}`),
  toggle: (id) => api.post(`/packaging-rules/${id}/toggle`),
};

export const reviewAPI = {
  // Public — no auth
  submit: (data) => api.post('/public/reviews', data),
  getByRestaurant: (restaurantId) => api.get(`/public/reviews/restaurant/${restaurantId}`),
  getSummary: (restaurantId) => api.get(`/public/reviews/restaurant/${restaurantId}/summary`),
  // Admin — requires auth
  getAll: (restaurantId) => api.get(`/reviews/restaurant/${restaurantId}`),
  getAdminSummary: (restaurantId) => api.get(`/reviews/restaurant/${restaurantId}/summary`),
  reply: (id, data) => api.post(`/reviews/${id}/reply`, data),
  hide: (id) => api.post(`/reviews/${id}/hide`),
  publish: (id) => api.post(`/reviews/${id}/publish`),
};

// Instagram API
export const instagramAPI = {
  // Config
  getConfigs: () => api.get('/instagram/config'),
  getConfig: (id) => api.get(`/instagram/config/${id}`),
  createConfig: (data) => api.post('/instagram/config', data),
  updateConfig: (id, data) => api.put(`/instagram/config/${id}`, data),
  deleteConfig: (id) => api.delete(`/instagram/config/${id}`),
  clearCredentials: (id) => api.delete(`/instagram/config/${id}/credentials`),

  // Subscribers
  getSubscribers: (params = {}) => api.get('/instagram/subscribers', { params }),
  searchSubscribers: (q, params = {}) => api.get('/instagram/subscribers/search', { params: { q, ...params } }),
  getSubscriber: (id) => api.get(`/instagram/subscribers/${id}`),
  blockSubscriber: (id) => api.post(`/instagram/subscribers/${id}/block`),
  unblockSubscriber: (id) => api.post(`/instagram/subscribers/${id}/unblock`),
  sendDm: (id, text) => api.post(`/instagram/subscribers/${id}/send`, { text }),
  broadcast: (text, target) => api.post('/instagram/subscribers/broadcast', { text, target }),
};

export default api;
