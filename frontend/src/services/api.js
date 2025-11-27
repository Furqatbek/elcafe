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

export default api;
