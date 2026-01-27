import { create } from 'zustand';

export const useNotificationStore = create((set, _get) => ({
  // Unread counts for different notification types
  unreadReservations: 0,
  unreadOrders: 0,

  // Recent order notifications (for notification panel)
  recentOrderNotifications: [],

  // WebSocket connection status
  wsConnected: false,

  // Increment unread reservation count
  addUnreadReservation: () => {
    set((state) => ({ unreadReservations: state.unreadReservations + 1 }));
  },

  // Clear unread reservations (when user views the page)
  clearUnreadReservations: () => {
    set({ unreadReservations: 0 });
  },

  // Set specific count
  setUnreadReservations: (count) => {
    set({ unreadReservations: count });
  },

  // Order notifications
  addOrderNotification: (notification) => {
    set((state) => ({
      unreadOrders: state.unreadOrders + 1,
      recentOrderNotifications: [
        { ...notification, id: Date.now(), timestamp: new Date() },
        ...state.recentOrderNotifications,
      ].slice(0, 20), // Keep only last 20 notifications
    }));
  },

  clearUnreadOrders: () => {
    set({ unreadOrders: 0 });
  },

  setUnreadOrders: (count) => {
    set({ unreadOrders: count });
  },

  clearOrderNotifications: () => {
    set({ recentOrderNotifications: [], unreadOrders: 0 });
  },

  markOrderNotificationRead: (id) => {
    set((state) => ({
      recentOrderNotifications: state.recentOrderNotifications.map((n) =>
        n.id === id ? { ...n, read: true } : n
      ),
    }));
  },

  // WebSocket status
  setWsConnected: (connected) => {
    set({ wsConnected: connected });
  },
}));
