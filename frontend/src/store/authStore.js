import { create } from 'zustand';
import { authAPI } from '../services/api';

// JWT token expiration constants (in milliseconds)
const ACCESS_TOKEN_EXPIRY = 15 * 60 * 1000; // 15 minutes
const REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000; // 7 days

// Helper function to decode JWT and get expiration
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
  } catch (error) {
    return null;
  }
};

// Helper function to set token with expiration
const setTokenWithExpiry = (accessToken, refreshToken) => {
  const now = Date.now();

  // Try to decode JWT to get actual expiration, otherwise use default
  const decodedAccess = decodeJWT(accessToken);
  const decodedRefresh = decodeJWT(refreshToken);

  const accessTokenExpiry = decodedAccess?.exp
    ? decodedAccess.exp * 1000
    : now + ACCESS_TOKEN_EXPIRY;

  const refreshTokenExpiry = decodedRefresh?.exp
    ? decodedRefresh.exp * 1000
    : now + REFRESH_TOKEN_EXPIRY;

  localStorage.setItem('access_token', accessToken);
  localStorage.setItem('refresh_token', refreshToken);
  localStorage.setItem('access_token_expiry', accessTokenExpiry.toString());
  localStorage.setItem('refresh_token_expiry', refreshTokenExpiry.toString());
  localStorage.setItem('token_set_time', now.toString());
};

// Helper function to check if tokens are expired
export const isAccessTokenExpired = () => {
  const expiry = localStorage.getItem('access_token_expiry');
  if (!expiry) return true;
  return Date.now() >= parseInt(expiry);
};

export const isRefreshTokenExpired = () => {
  const expiry = localStorage.getItem('refresh_token_expiry');
  if (!expiry) return true;
  return Date.now() >= parseInt(expiry);
};

export const shouldRefreshToken = () => {
  const expiry = localStorage.getItem('access_token_expiry');
  if (!expiry) return false;

  // Refresh if token will expire in the next 2 minutes
  const timeUntilExpiry = parseInt(expiry) - Date.now();
  return timeUntilExpiry < 2 * 60 * 1000 && timeUntilExpiry > 0;
};

// Helper to validate and clean up tokens on initialization
const validateStoredTokens = () => {
  const accessToken = localStorage.getItem('access_token');
  const refreshToken = localStorage.getItem('refresh_token');

  // Clean up invalid access tokens
  if (!accessToken || accessToken === '' || accessToken === 'null' || accessToken === 'undefined') {
    localStorage.removeItem('access_token');
    localStorage.removeItem('access_token_expiry');
  }

  // Clean up invalid refresh tokens
  if (!refreshToken || refreshToken === '' || refreshToken === 'null' || refreshToken === 'undefined') {
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('refresh_token_expiry');
    // If refresh token is invalid, clear access token too
    localStorage.removeItem('access_token');
    localStorage.removeItem('access_token_expiry');
    localStorage.removeItem('token_set_time');
    return false;
  }

  // Check if tokens are expired
  if (isAccessTokenExpired() && isRefreshTokenExpired()) {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('access_token_expiry');
    localStorage.removeItem('refresh_token_expiry');
    localStorage.removeItem('token_set_time');
    return false;
  }

  return !!accessToken && !isAccessTokenExpired();
};

export const useAuthStore = create((set) => ({
  user: null,
  token: localStorage.getItem('access_token'),
  isAuthenticated: validateStoredTokens(),

  login: async (credentials) => {
    try {
      const response = await authAPI.login(credentials);
      const { accessToken, refreshToken, user } = response.data.data;

      setTokenWithExpiry(accessToken, refreshToken);

      set({ user, token: accessToken, isAuthenticated: true });
      return { success: true };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || 'Login failed'
      };
    }
  },

  register: async (data) => {
    try {
      const response = await authAPI.register(data);
      const { accessToken, refreshToken, user } = response.data.data;

      setTokenWithExpiry(accessToken, refreshToken);

      set({ user, token: accessToken, isAuthenticated: true });
      return { success: true };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || 'Registration failed'
      };
    }
  },

  refreshToken: async () => {
    try {
      const refreshToken = localStorage.getItem('refresh_token');

      // Check if refresh token exists and is valid
      if (!refreshToken || refreshToken === '' || refreshToken === 'null' || refreshToken === 'undefined') {
        throw new Error('No refresh token available');
      }

      if (isRefreshTokenExpired()) {
        throw new Error('Refresh token expired');
      }

      const response = await authAPI.refresh(refreshToken);
      const { accessToken, refreshToken: newRefreshToken } = response.data.data;

      setTokenWithExpiry(accessToken, newRefreshToken || refreshToken);

      set({ token: accessToken, isAuthenticated: true });
      return { success: true };
    } catch (error) {
      // If refresh fails, logout
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
      localStorage.removeItem('access_token_expiry');
      localStorage.removeItem('refresh_token_expiry');
      localStorage.removeItem('token_set_time');
      set({ user: null, token: null, isAuthenticated: false });
      return { success: false, error: 'Session expired' };
    }
  },

  logout: () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('access_token_expiry');
    localStorage.removeItem('refresh_token_expiry');
    localStorage.removeItem('token_set_time');
    set({ user: null, token: null, isAuthenticated: false });
  },

  // Method to update tokens (called from api interceptor)
  updateTokens: (accessToken, refreshToken) => {
    setTokenWithExpiry(accessToken, refreshToken || localStorage.getItem('refresh_token'));
    set({ token: accessToken, isAuthenticated: true });
  },
}));
