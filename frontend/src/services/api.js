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
        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken,
        });

        const { accessToken } = response.data.data;
        localStorage.setItem('access_token', accessToken);

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
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
  getAll: (params) => api.get('/couriers', { params }),
  getById: (id) => api.get(`/couriers/${id}`),
  getWallet: (id) => api.get(`/couriers/${id}/wallet`),
  create: (data) => api.post('/couriers', data),
  update: (id, data) => api.put(`/couriers/${id}`, data),
  delete: (id) => api.delete(`/couriers/${id}`),
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

export default api;
