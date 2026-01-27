import { useEffect, useRef, useCallback } from 'react';
import { toast } from './useToast';

// Audio notification for new orders
const playNotificationSound = () => {
  try {
    // Create an audio context for the notification sound
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();

    // Create a simple beep sound
    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(audioContext.destination);

    oscillator.frequency.value = 800; // Hz
    oscillator.type = 'sine';

    gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.5);

    oscillator.start(audioContext.currentTime);
    oscillator.stop(audioContext.currentTime + 0.5);

    // Play a second beep
    setTimeout(() => {
      const osc2 = audioContext.createOscillator();
      const gain2 = audioContext.createGain();
      osc2.connect(gain2);
      gain2.connect(audioContext.destination);
      osc2.frequency.value = 1000;
      osc2.type = 'sine';
      gain2.gain.setValueAtTime(0.3, audioContext.currentTime);
      gain2.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.5);
      osc2.start(audioContext.currentTime);
      osc2.stop(audioContext.currentTime + 0.5);
    }, 200);
  } catch (e) {
    console.log('Audio notification not available:', e);
  }
};

// Show browser notification if permitted
const showBrowserNotification = (title, body) => {
  if ('Notification' in window && Notification.permission === 'granted') {
    new Notification(title, {
      body,
      icon: '/favicon.ico',
      tag: 'new-order',
      requireInteraction: true,
    });
  }
};

// Request notification permission
export const requestNotificationPermission = async () => {
  if ('Notification' in window && Notification.permission === 'default') {
    await Notification.requestPermission();
  }
};

/**
 * Hook to track and notify about new orders
 * @param {Array} orders - Current list of orders
 * @param {Object} options - Configuration options
 */
export function useOrderNotifications(orders, options = {}) {
  const {
    enabled = true,
    soundEnabled = true,
    toastEnabled = true,
    browserNotificationEnabled = true,
  } = options;

  const previousOrderIdsRef = useRef(new Set());
  const isFirstLoadRef = useRef(true);

  const notifyNewOrder = useCallback((order) => {
    const orderNumber = order.orderNumber || order.id;
    const orderType = order.orderType || 'DINE_IN';
    const total = order.total || order.totalAmount || 0;

    // Format order type for display
    const orderTypeDisplay = orderType === 'TAKEAWAY' ? 'Takeaway' : 'Dine In';

    // Play sound
    if (soundEnabled) {
      playNotificationSound();
    }

    // Show toast
    if (toastEnabled) {
      toast({
        title: `New Order #${orderNumber}`,
        description: `${orderTypeDisplay} order received. Total: ${total.toLocaleString()} UZS`,
        variant: 'success',
        duration: 10000,
      });
    }

    // Show browser notification
    if (browserNotificationEnabled) {
      showBrowserNotification(
        `New Order #${orderNumber}`,
        `${orderTypeDisplay} order received. Total: ${total.toLocaleString()} UZS`
      );
    }
  }, [soundEnabled, toastEnabled, browserNotificationEnabled]);

  useEffect(() => {
    if (!enabled || !orders || orders.length === 0) {
      return;
    }

    // Get current order IDs
    const currentOrderIds = new Set(orders.map(o => o.id || o.orderId));

    // Skip notification on first load
    if (isFirstLoadRef.current) {
      previousOrderIdsRef.current = currentOrderIds;
      isFirstLoadRef.current = false;
      return;
    }

    // Find new orders (IDs that weren't in the previous set)
    const newOrders = orders.filter(order => {
      const orderId = order.id || order.orderId;
      return !previousOrderIdsRef.current.has(orderId);
    });

    // Notify for each new order
    newOrders.forEach(order => {
      notifyNewOrder(order);
    });

    // Update the reference
    previousOrderIdsRef.current = currentOrderIds;
  }, [orders, enabled, notifyNewOrder]);

  return {
    requestPermission: requestNotificationPermission,
  };
}

export default useOrderNotifications;
