import { useEffect, useState, useRef, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useWebSocketNotifications, requestNotificationPermission } from '../hooks/useWebSocketNotifications';
import { useNotificationStore } from '../store/notificationStore';
import { Bell, Wifi, WifiOff, X, ShoppingCart, Clock, GripHorizontal } from 'lucide-react';
import { Button } from './ui/button';
import { formatDistanceToNow } from 'date-fns';

// Notification bell with draggable centered panel
export function NotificationBell() {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  const panelRef = useRef(null);
  const navigate = useNavigate();
  const location = useLocation();
  const {
    unreadOrders,
    recentOrderNotifications,
    clearUnreadOrders,
    clearOrderNotifications,
  } = useNotificationStore();

  // Get the selected restaurant ID from localStorage
  const [restaurantId, setRestaurantId] = useState(() => {
    return Number(localStorage.getItem('selectedRestaurantId')) || 1;
  });

  // Listen for restaurant changes
  useEffect(() => {
    const handleStorageChange = () => {
      const newId = Number(localStorage.getItem('selectedRestaurantId')) || 1;
      setRestaurantId(newId);
    };

    window.addEventListener('storage', handleStorageChange);
    // Also check periodically for same-tab changes
    const interval = setInterval(handleStorageChange, 1000);

    return () => {
      window.removeEventListener('storage', handleStorageChange);
      clearInterval(interval);
    };
  }, []);

  // Handle order event for notification store
  const handleOrderEvent = (event) => {
    const { eventType, data } = event;
    if (eventType === 'order.placed') {
      useNotificationStore.getState().addOrderNotification({
        type: eventType,
        orderNumber: data?.orderNumber,
        orderType: data?.orderType,
        total: data?.totalAmount || data?.total,
        tableNumber: data?.tableNumber,
      });
    }
  };

  // Initialize WebSocket connection
  const { connected } = useWebSocketNotifications({
    restaurantId,
    enabled: true,
    soundEnabled: true,
    toastEnabled: true,
    browserNotificationEnabled: true,
    onOrderEvent: handleOrderEvent,
  });

  // Update store with connection status
  useEffect(() => {
    useNotificationStore.getState().setWsConnected(connected);
  }, [connected]);

  // Request notification permission on mount
  useEffect(() => {
    requestNotificationPermission();
  }, []);

  // Clear unread count when on orders page
  useEffect(() => {
    if (location.pathname === '/orders') {
      clearUnreadOrders();
    }
  }, [location.pathname, clearUnreadOrders]);

  // Reset position to center when panel opens
  useEffect(() => {
    if (isOpen) {
      setPosition({ x: 0, y: 0 });
    }
  }, [isOpen]);

  // Handle drag start
  const handleDragStart = useCallback((e) => {
    if (panelRef.current) {
      const rect = panelRef.current.getBoundingClientRect();
      setDragOffset({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
      setIsDragging(true);
    }
  }, []);

  // Handle drag move
  const handleDragMove = useCallback((e) => {
    if (isDragging && panelRef.current) {
      const panelWidth = panelRef.current.offsetWidth;
      const panelHeight = panelRef.current.offsetHeight;

      // Calculate new position relative to center
      const centerX = window.innerWidth / 2;
      const centerY = window.innerHeight / 2;

      const newX = e.clientX - dragOffset.x + panelWidth / 2 - centerX;
      const newY = e.clientY - dragOffset.y + panelHeight / 2 - centerY;

      setPosition({ x: newX, y: newY });
    }
  }, [isDragging, dragOffset]);

  // Handle drag end
  const handleDragEnd = useCallback(() => {
    setIsDragging(false);
  }, []);

  // Add/remove global mouse event listeners for dragging
  useEffect(() => {
    if (isDragging) {
      window.addEventListener('mousemove', handleDragMove);
      window.addEventListener('mouseup', handleDragEnd);
      return () => {
        window.removeEventListener('mousemove', handleDragMove);
        window.removeEventListener('mouseup', handleDragEnd);
      };
    }
  }, [isDragging, handleDragMove, handleDragEnd]);

  // Handle clicking a notification
  const handleNotificationClick = (_notification) => {
    setIsOpen(false);
    clearUnreadOrders();
    navigate('/orders');
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

  return (
    <div className="relative">
      {/* Bell Button */}
      <Button
        variant="ghost"
        size="sm"
        className="relative p-2"
        onClick={() => setIsOpen(!isOpen)}
      >
        <Bell className="h-5 w-5" />
        {unreadOrders > 0 && (
          <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs font-bold w-5 h-5 rounded-full flex items-center justify-center animate-pulse">
            {unreadOrders > 9 ? '9+' : unreadOrders}
          </span>
        )}
        {/* Connection status indicator */}
        <span
          className={`absolute bottom-0 right-0 w-2 h-2 rounded-full ${
            connected ? 'bg-green-500' : 'bg-gray-400'
          }`}
          title={connected ? 'Connected' : 'Disconnected'}
        />
      </Button>

      {/* Centered Draggable Panel */}
      {isOpen && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40 bg-black/20"
            onClick={() => setIsOpen(false)}
          />

          {/* Panel - Centered and Draggable */}
          <div
            ref={panelRef}
            className="fixed w-80 bg-white rounded-lg shadow-xl border z-50 overflow-hidden"
            style={{
              left: `calc(50% - 160px + ${position.x}px)`,
              top: `calc(50% - 200px + ${position.y}px)`,
              cursor: isDragging ? 'grabbing' : 'default',
            }}
          >
            {/* Drag Handle */}
            <div
              className="flex items-center justify-center py-1 bg-gray-100 border-b cursor-grab active:cursor-grabbing hover:bg-gray-200 transition-colors"
              onMouseDown={handleDragStart}
            >
              <GripHorizontal className="h-4 w-4 text-gray-400" />
            </div>

            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b bg-gray-50">
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-gray-900">Notifications</h3>
                {connected ? (
                  <span className="flex items-center gap-1 text-xs text-green-600">
                    <Wifi className="h-3 w-3" />
                    Live
                  </span>
                ) : (
                  <span className="flex items-center gap-1 text-xs text-gray-500">
                    <WifiOff className="h-3 w-3" />
                    Offline
                  </span>
                )}
              </div>
              <div className="flex items-center gap-1">
                {recentOrderNotifications.length > 0 && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-xs text-gray-500 hover:text-gray-700"
                    onClick={clearOrderNotifications}
                  >
                    Clear all
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  className="p-1"
                  onClick={() => setIsOpen(false)}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </div>

            {/* Notifications List */}
            <div className="max-h-96 overflow-y-auto">
              {recentOrderNotifications.length === 0 ? (
                <div className="px-4 py-8 text-center text-gray-500">
                  <Bell className="h-8 w-8 mx-auto mb-2 opacity-50" />
                  <p className="text-sm">No new notifications</p>
                  <p className="text-xs mt-1">New orders will appear here</p>
                </div>
              ) : (
                <div className="divide-y">
                  {recentOrderNotifications.map((notification) => (
                    <button
                      key={notification.id}
                      className={`w-full px-4 py-3 text-left hover:bg-gray-50 transition-colors ${
                        !notification.read ? 'bg-blue-50' : ''
                      }`}
                      onClick={() => handleNotificationClick(notification)}
                    >
                      <div className="flex items-start gap-3">
                        <div className={`p-2 rounded-full ${
                          notification.type === 'order.placed'
                            ? 'bg-green-100 text-green-600'
                            : 'bg-gray-100 text-gray-600'
                        }`}>
                          <ShoppingCart className="h-4 w-4" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-gray-900">
                            New Order #{notification.orderNumber}
                          </p>
                          <p className="text-xs text-gray-500 mt-0.5">
                            {formatOrderType(notification.orderType)}
                            {notification.tableNumber && ` - Table ${notification.tableNumber}`}
                          </p>
                          <p className="text-xs font-medium text-gray-700 mt-0.5">
                            {Number(notification.total || 0).toLocaleString()} UZS
                          </p>
                          <p className="text-xs text-gray-400 mt-1 flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {notification.timestamp
                              ? formatDistanceToNow(new Date(notification.timestamp), { addSuffix: true })
                              : 'Just now'}
                          </p>
                        </div>
                        {!notification.read && (
                          <span className="w-2 h-2 bg-blue-500 rounded-full flex-shrink-0 mt-2" />
                        )}
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Footer */}
            {recentOrderNotifications.length > 0 && (
              <div className="px-4 py-2 border-t bg-gray-50">
                <Button
                  variant="ghost"
                  size="sm"
                  className="w-full text-sm text-blue-600 hover:text-blue-700"
                  onClick={() => {
                    setIsOpen(false);
                    clearUnreadOrders();
                    navigate('/orders');
                  }}
                >
                  View all orders
                </Button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

export default NotificationBell;
