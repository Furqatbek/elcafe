import { create } from 'zustand';

/**
 * App-level "this tenant is suspended" flag. Flipped by the axios interceptor when the Phase 2 backend
 * access gate returns 402 SUBSCRIPTION_INACTIVE, and read by SuspensionGate to take over the UI instead
 * of surfacing a toast on every blocked request. Kept separate from authStore so services/api.js can
 * set it without an import cycle (authStore imports api).
 */
export const useSubscriptionStore = create((set) => ({
  suspended: false,
  setSuspended: (suspended) => set({ suspended }),
}));
