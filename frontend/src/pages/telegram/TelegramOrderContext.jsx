import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { consumerAuthAPI, consumerOrderAPI } from '../../services/api';
import { getInitData } from '../../services/telegram';

/**
 * State + actions for the Telegram Mini App ordering flow (Option B): a real consumer session
 * (authenticated from signed initData), a client-side cart, and one-shot order placement against
 * /consumer/orders tagged TELEGRAM_BOT. The access token lives here and is passed explicitly to the
 * order API, so it never enters the staff `access_token` refresh path.
 */
const TelegramOrderContext = createContext(null);

export const useTelegramOrder = () => {
  const ctx = useContext(TelegramOrderContext);
  if (!ctx) throw new Error('useTelegramOrder must be used within TelegramOrderProvider');
  return ctx;
};

const TOKEN_KEY = 'tg_consumer_token';
const RID_KEY = 'tg_restaurant_id';

const readSession = (key) => {
  try { return sessionStorage.getItem(key) || null; } catch { return null; }
};
const writeSession = (key, value) => {
  try { if (value != null) sessionStorage.setItem(key, String(value)); } catch { /* storage off */ }
};

// ApiResponse envelopes are { success, message, data }; some endpoints return the payload raw.
const unwrap = (res) => res?.data?.data ?? res?.data;

/**
 * Build the /consumer/orders request body. Pure and exported so the mapping (source tag, item shape,
 * delivery-only address, contact from the verified prefill) can be unit-tested without React.
 */
export function buildOrderPayload({ restaurantId, prefill, cart, orderType, deliveryInfo, paymentMethod, customerNotes }) {
  const payload = {
    restaurantId: Number(restaurantId),
    orderSource: 'TELEGRAM_BOT',
    orderType,
    items: cart.map((c) => ({ productId: c.product.id, quantity: c.quantity })),
    paymentMethod,
    customerNotes: customerNotes || undefined,
  };
  if (prefill && (prefill.phone || prefill.first_name)) {
    payload.customerInfo = {
      firstName: prefill.first_name || undefined,
      lastName: prefill.last_name || undefined,
      phone: prefill.phone || undefined,
    };
  }
  // Delivery details only ride along for delivery orders; pickup carries none (and skips the fee server-side).
  if (orderType === 'DELIVERY' && deliveryInfo) {
    payload.deliveryInfo = deliveryInfo;
  }
  return payload;
}

export function TelegramOrderProvider({ children }) {
  const [token, setToken] = useState(() => readSession(TOKEN_KEY));
  // Persisted so a WebView reload can recover the session (token + restaurant) and reload the menu.
  const [restaurantId, setRestaurantId] = useState(() => readSession(RID_KEY));
  const [prefill, setPrefill] = useState(null);
  const [customerId, setCustomerId] = useState(null);
  const [cart, setCart] = useState([]); // [{ product, quantity }]

  /**
   * Log in from the Telegram launch. Returns { ok } or { ok:false, reason } where reason is
   * NOT_IN_TELEGRAM (opened outside a bot) or REGISTRATION_REQUIRED (no shared contact yet).
   */
  const login = useCallback(async (rid) => {
    setRestaurantId(rid);
    writeSession(RID_KEY, rid);
    const initData = getInitData();
    if (!initData) return { ok: false, reason: 'NOT_IN_TELEGRAM' };

    const body = unwrap(await consumerAuthAPI.telegramLogin({
      restaurant_id: Number(rid),
      init_data: initData,
    }));
    if (!body || body.registration_required) return { ok: false, reason: 'REGISTRATION_REQUIRED' };

    const accessToken = body.auth?.access_token || null;
    setToken(accessToken);
    writeSession(TOKEN_KEY, accessToken);
    setPrefill(body.prefill || null);
    setCustomerId(body.auth?.customer_id ?? null);
    return { ok: true };
  }, []);

  const addItem = useCallback((product) => {
    setCart((prev) => {
      const i = prev.findIndex((c) => c.product.id === product.id);
      if (i >= 0) {
        const next = [...prev];
        next[i] = { ...next[i], quantity: next[i].quantity + 1 };
        return next;
      }
      return [...prev, { product, quantity: 1 }];
    });
  }, []);

  const setQuantity = useCallback((productId, quantity) => {
    setCart((prev) => prev
      .map((c) => (c.product.id === productId ? { ...c, quantity } : c))
      .filter((c) => c.quantity > 0));
  }, []);

  const clearCart = useCallback(() => setCart([]), []);

  const itemCount = useMemo(() => cart.reduce((n, c) => n + c.quantity, 0), [cart]);
  const subtotal = useMemo(
    () => cart.reduce((s, c) => s + Number(c.product.price || 0) * c.quantity, 0),
    [cart],
  );

  const placeOrder = useCallback(async ({ orderType, deliveryInfo, paymentMethod, customerNotes }) => {
    const payload = buildOrderPayload({ restaurantId, prefill, cart, orderType, deliveryInfo, paymentMethod, customerNotes });
    const order = unwrap(await consumerOrderAPI.placeOrder(payload, token));
    clearCart();
    return order;
  }, [restaurantId, prefill, cart, token, clearCart]);

  const value = {
    token, restaurantId, prefill, customerId,
    cart, itemCount, subtotal,
    login, addItem, setQuantity, clearCart, placeOrder,
  };
  return <TelegramOrderContext.Provider value={value}>{children}</TelegramOrderContext.Provider>;
}
