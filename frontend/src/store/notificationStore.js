import { create } from 'zustand';

export const useNotificationStore = create((set, get) => ({
  // Unread counts for different notification types
  unreadReservations: 0,

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
}));
