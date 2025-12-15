import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore, isAccessTokenExpired, isRefreshTokenExpired, shouldRefreshToken } from '../store/authStore';

/**
 * SessionManager component
 * Monitors token expiration and automatically refreshes tokens
 * Redirects to login if session is completely expired
 */
export default function SessionManager() {
  const navigate = useNavigate();
  const { isAuthenticated, refreshToken, logout } = useAuthStore();
  const intervalRef = useRef(null);
  const isRefreshingRef = useRef(false);

  useEffect(() => {
    // Only run if user is authenticated
    if (!isAuthenticated) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }

    // Check token status immediately on mount
    checkTokenStatus();

    // Set up interval to check token status every 30 seconds
    intervalRef.current = setInterval(() => {
      checkTokenStatus();
    }, 30 * 1000); // Check every 30 seconds

    // Also check on visibility change (when user returns to the tab)
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && isAuthenticated) {
        checkTokenStatus();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    // Cleanup
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [isAuthenticated]);

  const checkTokenStatus = async () => {
    // Prevent multiple simultaneous refresh attempts
    if (isRefreshingRef.current) {
      return;
    }

    try {
      // Check if refresh token is expired
      if (isRefreshTokenExpired()) {
        console.log('Refresh token expired, logging out...');
        handleSessionExpired();
        return;
      }

      // Check if access token is expired
      if (isAccessTokenExpired()) {
        console.log('Access token expired, refreshing...');
        await handleTokenRefresh();
        return;
      }

      // Check if access token should be refreshed soon
      if (shouldRefreshToken()) {
        console.log('Access token expiring soon, proactively refreshing...');
        await handleTokenRefresh();
        return;
      }
    } catch (error) {
      console.error('Error checking token status:', error);
    }
  };

  const handleTokenRefresh = async () => {
    if (isRefreshingRef.current) {
      return;
    }

    isRefreshingRef.current = true;

    try {
      const result = await refreshToken();

      if (!result.success) {
        console.log('Token refresh failed, logging out...');
        handleSessionExpired();
      } else {
        console.log('Token refreshed successfully');
      }
    } catch (error) {
      console.error('Error refreshing token:', error);
      handleSessionExpired();
    } finally {
      isRefreshingRef.current = false;
    }
  };

  const handleSessionExpired = () => {
    // Clear interval
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    // Logout user
    logout();

    // Redirect to login
    navigate('/login', { replace: true });
  };

  // This component doesn't render anything
  return null;
}
