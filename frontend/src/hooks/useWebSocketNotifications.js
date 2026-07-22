import { useEffect, useRef, useCallback, useState } from 'react';
import websocketService from '../services/websocket';
import { toast } from './useToast';

// Audio notification for new orders
const playNotificationSound = () => {
  try {
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();

    // First beep
    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(audioContext.destination);

    oscillator.frequency.value = 800;
    oscillator.type = 'sine';

    gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.5);

    oscillator.start(audioContext.currentTime);
    oscillator.stop(audioContext.currentTime + 0.5);

    // Second beep
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
    console.log('[Notification] Audio not available:', e);
  }
};

// Show browser notification
const showBrowserNotification = (title, body, onClick) => {
  if ('Notification' in window && Notification.permission === 'granted') {
    const notification = new Notification(title, {
      body,
      icon: '/favicon.ico',
      tag: 'order-notification',
      requireInteraction: true,
    });

    if (onClick) {
      notification.onclick = () => {
        window.focus();
        onClick();
        notification.close();
      };
    }
  }
};

// Request notification permission
export const requestNotificationPermission = async () => {
  if ('Notification' in window && Notification.permission === 'default') {
    return await Notification.requestPermission();
  }
  return Notification.permission;
};

// Format order type for display
const formatOrderType = (type) => {
  switch (type) {
    case 'TAKEAWAY':
      return 'Takeaway';
    case 'DINE_IN':
      return 'Dine In';
    case 'DELIVERY':
      return 'Delivery';
    default:
      return type?.replace('_', ' ') || 'Order';
  }
};

// Format order source for display
const formatOrderSource = (source) => {
  switch (source) {
    case 'SELF_SERVICE':
      return '📱 Self-Service (QR)';
    case 'WEBSITE':
      return '🌐 Website';
    case 'MOBILE_APP':
      return '📲 Mobile App';
    case 'TELEGRAM_BOT':
      return '✈️ Telegram';
    case 'WAITER':
      return '🧑‍🍳 Waiter';
    case 'ADMIN_PANEL':
      return '🖥️ Admin Panel';
    default:
      return source?.replace('_', ' ') || '';
  }
};

// Get toast variant based on event type
const getToastVariant = (eventType) => {
  switch (eventType) {
    case 'order.placed':
    case 'order.accepted':
      return 'success';
    case 'order.cancelled':
    case 'order.rejected':
      return 'destructive';
    case 'order.ready':
      return 'default';
    default:
      return 'default';
  }
};

/**
 * Hook to connect to WebSocket and receive real-time order notifications
 * @param {Object} options - Configuration options
 * @returns {Object} - Hook state and methods
 */
export function useWebSocketNotifications(options = {}) {
  const {
    restaurantId = null,
    enabled = true,
    soundEnabled = true,
    toastEnabled = true,
    browserNotificationEnabled = true,
    onOrderEvent = null,
  } = options;

  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState(null);
  const subscribedRef = useRef(false);
  const currentRestaurantIdRef = useRef(restaurantId);

  // Handle incoming order event
  const handleOrderEvent = useCallback((event) => {
    console.log('[WebSocket] Received order event:', event);
    setLastEvent(event);

    const { eventType, data } = event;
    const orderNumber = data?.orderNumber || data?.orderId;
    const orderType = formatOrderType(data?.orderType);
    const total = data?.totalAmount || data?.total || 0;
    const tableNumber = data?.tableNumber;
    const source = formatOrderSource(data?.orderSource);

    // Build notification message
    let title = '';
    let description = '';

    switch (eventType) {
      case 'order.placed':
        title = `New Order #${orderNumber}`;
        description = [
          source,
          tableNumber ? `Table ${tableNumber}` : orderType,
          `${Number(total).toLocaleString()} UZS`,
        ].filter(Boolean).join(' · ');
        break;
      case 'order.accepted':
        title = `✅ Order #${orderNumber} Accepted`;
        description = [
          source,
          tableNumber ? `Table ${tableNumber}` : orderType,
          `${Number(total).toLocaleString()} UZS`,
        ].filter(Boolean).join(' · ');
        break;
      case 'order.cancelled':
        title = `Order #${orderNumber} Cancelled`;
        description = data?.reason || 'Order has been cancelled';
        break;
      case 'order.rejected':
        title = `Order #${orderNumber} Rejected`;
        description = data?.reason || 'Order has been rejected';
        break;
      case 'order.ready':
        title = `Order #${orderNumber} Ready`;
        description = `Order is ready for ${orderType === 'Delivery' ? 'pickup by courier' : 'serving'}`;
        break;
      default:
        title = `Order #${orderNumber} Update`;
        description = `Status: ${eventType?.replace('order.', '')?.replace('_', ' ')}`;
    }

    // Play sound for new and accepted orders
    if (soundEnabled && (eventType === 'order.placed' || eventType === 'order.accepted')) {
      playNotificationSound();
    }

    // Show toast notification
    if (toastEnabled) {
      toast({
        title,
        description,
        variant: getToastVariant(eventType),
        duration: (eventType === 'order.placed' || eventType === 'order.accepted') ? 10000 : 5000,
      });
    }

    // Show browser notification for new and accepted orders
    if (browserNotificationEnabled && (eventType === 'order.placed' || eventType === 'order.accepted')) {
      showBrowserNotification(title, description, () => {
        window.location.href = '/admin/orders';
      });
    }

    // Call custom handler if provided
    if (onOrderEvent) {
      onOrderEvent(event);
    }
  }, [soundEnabled, toastEnabled, browserNotificationEnabled, onOrderEvent]);

  // Connect and subscribe
  useEffect(() => {
    if (!enabled || !restaurantId) {
      return;
    }

    // Capture restaurant ID for this effect instance (for cleanup)
    const effectRestaurantId = restaurantId;
    currentRestaurantIdRef.current = restaurantId;

    let mounted = true;

    const connectAndSubscribe = async () => {
      try {
        await websocketService.connect();

        // Check if still mounted and restaurant ID hasn't changed
        if (!mounted || effectRestaurantId !== currentRestaurantIdRef.current) {
          return;
        }

        setConnected(true);

        // Subscribe to restaurant-specific order events
        const destination = `/topic/restaurant/${effectRestaurantId}/orders`;
        websocketService.subscribe(destination, handleOrderEvent);
        subscribedRef.current = true;
      } catch (error) {
        console.error('[WebSocket] Connection failed:', error);
        if (mounted) {
          setConnected(false);
        }
      }
    };

    connectAndSubscribe();

    // Connection status listener
    const handleConnect = () => {
      if (mounted) setConnected(true);
    };
    const handleDisconnect = () => {
      if (mounted) setConnected(false);
    };

    websocketService.addListener('connect', handleConnect);
    websocketService.addListener('disconnect', handleDisconnect);

    return () => {
      mounted = false;
      // Unsubscribe using the captured restaurant ID from this effect
      if (subscribedRef.current) {
        websocketService.unsubscribe(`/topic/restaurant/${effectRestaurantId}/orders`);
        subscribedRef.current = false;
      }
      websocketService.removeListener('connect', handleConnect);
      websocketService.removeListener('disconnect', handleDisconnect);
    };
  }, [enabled, restaurantId, handleOrderEvent]);

  return {
    connected,
    lastEvent,
    requestPermission: requestNotificationPermission,
  };
}

export default useWebSocketNotifications;
