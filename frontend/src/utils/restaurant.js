import { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { restaurantAPI } from '../services/api';

const STORAGE_KEY = 'selectedRestaurantId';

// Module-level cache for the /restaurants/active fallback so we hit the
// endpoint at most once per page load and share the result across hooks.
let activeFallbackPromise = null;
let activeFallbackResult = null;

function toPositiveNumber(value) {
  if (value == null) return null;
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : null;
}

function fromUser() {
  const user = useAuthStore.getState().user;
  return toPositiveNumber(user?.restaurantId);
}

function fromStorage() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw == null || raw === '') return null;
  return toPositiveNumber(raw);
}

async function fetchActiveFallback() {
  if (activeFallbackResult != null) return activeFallbackResult;
  if (!activeFallbackPromise) {
    activeFallbackPromise = restaurantAPI
      .getActive()
      .then((res) => {
        const body = res.data;
        const list = Array.isArray(body)
          ? body
          : Array.isArray(body?.data)
            ? body.data
            : [];
        const id = toPositiveNumber(list[0]?.id);
        activeFallbackResult = id;
        return id;
      })
      .catch(() => {
        activeFallbackPromise = null;
        return null;
      });
  }
  return activeFallbackPromise;
}

/**
 * Synchronously resolves the current restaurant id, in order:
 *   1. authenticated user's restaurantId      — operator pinned to a restaurant
 *   2. localStorage('selectedRestaurantId')   — manual admin selection
 * Returns null when neither is available; callers should either await
 * `resolveCurrentRestaurantId()` or use the hook below to also try the
 * /restaurants/active fallback.
 */
export function getCurrentRestaurantId() {
  return fromUser() ?? fromStorage();
}

/**
 * Async resolver that adds a /restaurants/active fallback to the synchronous
 * lookup, in order: user → localStorage → first active restaurant.
 * Caches the API result in module scope so repeated callers share one
 * request.
 */
export async function resolveCurrentRestaurantId() {
  const sync = getCurrentRestaurantId();
  if (sync != null) return sync;
  return await fetchActiveFallback();
}

/**
 * Reactive variant for components. Initially returns whatever the
 * synchronous lookup finds, then asynchronously resolves the
 * /restaurants/active fallback if user + localStorage are both empty.
 * Re-renders when the user logs in/out or when another tab changes
 * selectedRestaurantId.
 */
export function useCurrentRestaurantId() {
  const userRestaurantId = useAuthStore((s) => s.user?.restaurantId);
  const [stored, setStored] = useState(() => localStorage.getItem(STORAGE_KEY) || '');
  const [apiFallback, setApiFallback] = useState(activeFallbackResult);

  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === STORAGE_KEY) {
        setStored(e.newValue || '');
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const fromUserId = toPositiveNumber(userRestaurantId);
  const fromStorageId = toPositiveNumber(stored);
  const resolved = fromUserId ?? fromStorageId ?? apiFallback;

  useEffect(() => {
    if (fromUserId != null || fromStorageId != null) return;
    if (apiFallback != null) return;
    let cancelled = false;
    fetchActiveFallback().then((id) => {
      if (!cancelled) setApiFallback(id);
    });
    return () => { cancelled = true; };
  }, [fromUserId, fromStorageId, apiFallback]);

  return resolved ?? null;
}
