import { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';

/**
 * Resolves the current restaurant id, in order:
 *   1. localStorage('selectedRestaurantId')  — admin's manual selection
 *   2. authenticated user's restaurantId      — operator's own restaurant
 * Returns null if neither is available so callers fail loudly instead of
 * silently operating on restaurant 1.
 */
export function getCurrentRestaurantId() {
  const stored = localStorage.getItem('selectedRestaurantId');
  if (stored != null && stored !== '') {
    const n = Number(stored);
    if (Number.isFinite(n) && n > 0) return n;
  }
  const user = useAuthStore.getState().user;
  const fromUser = user?.restaurantId;
  if (fromUser != null) {
    const n = Number(fromUser);
    if (Number.isFinite(n) && n > 0) return n;
  }
  return null;
}

/**
 * Reactive variant for components: re-renders when the user logs in / out
 * or when another tab changes selectedRestaurantId.
 */
export function useCurrentRestaurantId() {
  const userRestaurantId = useAuthStore((s) => s.user?.restaurantId);
  const [stored, setStored] = useState(() =>
    localStorage.getItem('selectedRestaurantId') || ''
  );

  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === 'selectedRestaurantId') {
        setStored(e.newValue || '');
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const fromStorage = stored !== '' ? Number(stored) : NaN;
  if (Number.isFinite(fromStorage) && fromStorage > 0) return fromStorage;
  if (userRestaurantId != null) {
    const n = Number(userRestaurantId);
    if (Number.isFinite(n) && n > 0) return n;
  }
  return null;
}
