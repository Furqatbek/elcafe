/**
 * Push Notification Service
 * Handles web push notification subscription and management
 */
import { pushAPI } from './api';

// Check if push notifications are supported
export const isPushSupported = () => {
  return 'serviceWorker' in navigator && 'PushManager' in window;
};

// Check current permission status
export const getPermissionStatus = () => {
  if (!isPushSupported()) return 'unsupported';
  return Notification.permission;
};

// Request notification permission
export const requestPermission = async () => {
  if (!isPushSupported()) {
    throw new Error('Push notifications are not supported in this browser');
  }

  const permission = await Notification.requestPermission();
  return permission;
};

// Register service worker
export const registerServiceWorker = async () => {
  if (!('serviceWorker' in navigator)) {
    throw new Error('Service workers are not supported');
  }

  try {
    const registration = await navigator.serviceWorker.register('/sw.js');
    console.log('Service Worker registered:', registration);
    return registration;
  } catch (error) {
    console.error('Service Worker registration failed:', error);
    throw error;
  }
};

// Get existing service worker registration
export const getServiceWorkerRegistration = async () => {
  if (!('serviceWorker' in navigator)) {
    return null;
  }
  return await navigator.serviceWorker.ready;
};

// Convert VAPID key from base64 to Uint8Array
const urlBase64ToUint8Array = (base64String) => {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding)
    .replace(/-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
};

// Subscribe to push notifications
export const subscribeToPush = async (isAdmin = false) => {
  if (!isPushSupported()) {
    throw new Error('Push notifications are not supported');
  }

  // Check permission
  if (Notification.permission !== 'granted') {
    const permission = await requestPermission();
    if (permission !== 'granted') {
      throw new Error('Notification permission denied');
    }
  }

  // Get VAPID key from server
  const vapidResponse = await pushAPI.getVapidKey();
  const vapidPublicKey = vapidResponse.data.publicKey;

  if (!vapidPublicKey) {
    throw new Error('VAPID public key not configured on server');
  }

  // Get service worker registration
  const registration = await getServiceWorkerRegistration();
  if (!registration) {
    throw new Error('Service worker not registered');
  }

  // Subscribe to push manager
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(vapidPublicKey)
  });

  // Extract subscription data
  const subscriptionJson = subscription.toJSON();
  const subscriptionData = {
    endpoint: subscriptionJson.endpoint,
    p256dh: subscriptionJson.keys.p256dh,
    auth: subscriptionJson.keys.auth,
    deviceType: getDeviceType(),
    browser: getBrowserName(),
    userAgent: navigator.userAgent
  };

  // Send to server
  if (isAdmin) {
    await pushAPI.subscribeAdmin(subscriptionData);
  } else {
    await pushAPI.subscribeCustomer(subscriptionData);
  }

  // Store subscription in localStorage for reference
  localStorage.setItem('pushSubscription', JSON.stringify({
    endpoint: subscriptionJson.endpoint,
    subscribedAt: new Date().toISOString()
  }));

  return subscription;
};

// Unsubscribe from push notifications
export const unsubscribeFromPush = async () => {
  const registration = await getServiceWorkerRegistration();
  if (!registration) return;

  const subscription = await registration.pushManager.getSubscription();
  if (subscription) {
    // Notify server
    await pushAPI.unsubscribe(subscription.endpoint);
    // Unsubscribe locally
    await subscription.unsubscribe();
    // Clear local storage
    localStorage.removeItem('pushSubscription');
  }
};

// Check if currently subscribed
export const isSubscribed = async () => {
  const registration = await getServiceWorkerRegistration();
  if (!registration) return false;

  const subscription = await registration.pushManager.getSubscription();
  return !!subscription;
};

// Get current subscription
export const getCurrentSubscription = async () => {
  const registration = await getServiceWorkerRegistration();
  if (!registration) return null;

  return await registration.pushManager.getSubscription();
};

// Helper: Get device type
const getDeviceType = () => {
  const ua = navigator.userAgent;
  if (/tablet|ipad|playbook|silk/i.test(ua)) return 'tablet';
  if (/Mobile|iP(hone|od)|Android|BlackBerry|IEMobile|Kindle|Silk-Accelerated|(hpw|web)OS|Opera M(obi|ini)/.test(ua)) return 'mobile';
  return 'desktop';
};

// Helper: Get browser name
const getBrowserName = () => {
  const ua = navigator.userAgent;
  if (ua.includes('Chrome')) return 'Chrome';
  if (ua.includes('Firefox')) return 'Firefox';
  if (ua.includes('Safari')) return 'Safari';
  if (ua.includes('Edge')) return 'Edge';
  if (ua.includes('Opera')) return 'Opera';
  return 'Unknown';
};

export default {
  isPushSupported,
  getPermissionStatus,
  requestPermission,
  registerServiceWorker,
  subscribeToPush,
  unsubscribeFromPush,
  isSubscribed,
  getCurrentSubscription
};
