import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Minus, ShoppingCart, Check } from 'lucide-react';
import { selfServiceAPI } from '../../services/api';
import { useTelegramOrder } from './TelegramOrderContext';

const fmt = (n) => new Intl.NumberFormat('uz-UZ').format(Math.round(Number(n) || 0)) + ' UZS';

/**
 * The whole Telegram ordering experience on one screen: browse the (public) menu, build a cart, then
 * check out (delivery/pickup, prefilled address, payment) → POST /consumer/orders tagged TELEGRAM_BOT,
 * which auto-accepts to the kitchen. Menu browsing needs no auth; only placement uses the consumer token.
 */
export default function TelegramOrderPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { restaurantId, token, prefill, cart, itemCount, subtotal, addItem, setQuantity, placeOrder } = useTelegramOrder();

  const [restaurant, setRestaurant] = useState(null);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [category, setCategory] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const [showCheckout, setShowCheckout] = useState(false);
  const [orderType, setOrderType] = useState('DELIVERY');
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [placing, setPlacing] = useState(false);
  const [placeError, setPlaceError] = useState(null);
  const [placedOrder, setPlacedOrder] = useState(null);

  // Session guard: no restaurant → back to entry; restaurant but no token → re-auth via the bot param.
  useEffect(() => {
    if (!restaurantId) navigate('/tg', { replace: true });
    else if (!token) navigate(`/tg?restaurantId=${restaurantId}`, { replace: true });
  }, [restaurantId, token, navigate]);

  // Restaurant + categories load once.
  useEffect(() => {
    if (!restaurantId) return undefined;
    let cancelled = false;
    (async () => {
      try {
        const [rRes, cRes] = await Promise.all([
          selfServiceAPI.getRestaurantInfo(restaurantId),
          selfServiceAPI.getCategories(restaurantId).catch(() => ({ data: [] })),
        ]);
        if (cancelled) return;
        setRestaurant(rRes.data);
        setCategories(Array.isArray(cRes.data) ? cRes.data : []);
      } catch {
        if (!cancelled) setLoadError(true);
      }
    })();
    return () => { cancelled = true; };
  }, [restaurantId]);

  // Products (re)load per selected category — the server filters, matching the QR menu's pattern.
  useEffect(() => {
    if (!restaurantId) return undefined;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await selfServiceAPI.getProducts(restaurantId, category === 'ALL' ? undefined : category);
        if (!cancelled) setProducts(Array.isArray(res.data) ? res.data : []);
      } catch {
        if (!cancelled) setLoadError(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [restaurantId, category]);

  const qtyOf = (id) => cart.find((c) => c.product.id === id)?.quantity || 0;

  const handlePlace = async () => {
    setPlaceError(null);
    if (orderType === 'DELIVERY' && !address.trim()) {
      setPlaceError(t('telegram.addressRequired', 'Please enter a delivery address.'));
      return;
    }
    setPlacing(true);
    try {
      const deliveryInfo = orderType === 'DELIVERY' ? {
        address: address.trim(),
        latitude: prefill?.latitude ?? undefined,
        longitude: prefill?.longitude ?? undefined,
        deliveryInstructions: notes.trim() || undefined,
      } : undefined;
      const order = await placeOrder({
        orderType,
        deliveryInfo,
        paymentMethod,
        customerNotes: notes.trim() || undefined,
      });
      setPlacedOrder(order);
    } catch (e) {
      setPlaceError(e?.response?.data?.message || t('telegram.orderFailed', 'Could not place the order. Please try again.'));
    } finally {
      setPlacing(false);
    }
  };

  if (placedOrder) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl shadow-lg p-8 max-w-sm w-full text-center">
          <div className="mx-auto mb-4 h-14 w-14 rounded-full bg-green-100 flex items-center justify-center">
            <Check className="h-8 w-8 text-green-600" />
          </div>
          <h2 className="text-xl font-semibold text-gray-800 mb-1">{t('telegram.orderPlaced', 'Order placed!')}</h2>
          <p className="text-gray-600 mb-4">{t('telegram.sentToKitchen', 'Your order was sent to the kitchen.')}</p>
          <div className="text-sm text-gray-500">
            <div>{t('telegram.orderNumber', 'Order')}: <span className="font-medium text-gray-800">{placedOrder.orderNumber}</span></div>
            {placedOrder.total != null && <div className="mt-1">{fmt(placedOrder.total)}</div>}
          </div>
        </div>
      </div>
    );
  }

  if (loading && products.length === 0) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow p-6 max-w-sm w-full text-center">
          <h2 className="text-lg font-semibold text-gray-800 mb-2">{t('selfService.somethingWentWrong', 'Something went wrong')}</h2>
          <p className="text-gray-600">{t('selfService.failedToLoadMenu', 'Failed to load the menu.')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-28">
      <header className="bg-white shadow-sm sticky top-0 z-10 px-4 py-3">
        <h1 className="text-lg font-semibold text-gray-900 truncate">
          {restaurant?.name || t('telegram.menu', 'Menu')}
        </h1>
      </header>

      {categories.length > 0 && (
        <div className="flex gap-2 overflow-x-auto px-4 py-3">
          <Chip active={category === 'ALL'} onClick={() => setCategory('ALL')}>{t('telegram.all', 'All')}</Chip>
          {categories.map((c) => (
            <Chip key={c.id} active={category === c.id} onClick={() => setCategory(c.id)}>{c.name}</Chip>
          ))}
        </div>
      )}

      <ul className="px-4 space-y-2">
        {products.map((p) => {
          const qty = qtyOf(p.id);
          const available = p.inStock !== false;
          return (
            <li key={p.id} className="bg-white rounded-xl shadow-sm p-3 flex items-center gap-3">
              <div className="flex-1 min-w-0">
                <div className="font-medium text-gray-900 truncate">{p.name}</div>
                {p.description && <div className="text-sm text-gray-500 line-clamp-2">{p.description}</div>}
                <div className="text-sm font-semibold text-gray-800 mt-1">{fmt(p.price)}</div>
              </div>
              {qty === 0 ? (
                <button
                  type="button"
                  disabled={!available}
                  onClick={() => addItem(p)}
                  className="shrink-0 h-9 px-3 rounded-lg bg-blue-600 text-white text-sm font-medium disabled:bg-gray-300"
                >
                  {available ? t('telegram.add', 'Add') : t('telegram.unavailable', 'N/A')}
                </button>
              ) : (
                <div className="shrink-0 flex items-center gap-2">
                  <button type="button" onClick={() => setQuantity(p.id, qty - 1)} className="h-8 w-8 rounded-full bg-gray-100 flex items-center justify-center">
                    <Minus className="h-4 w-4" />
                  </button>
                  <span className="w-5 text-center font-medium">{qty}</span>
                  <button type="button" onClick={() => setQuantity(p.id, qty + 1)} className="h-8 w-8 rounded-full bg-blue-600 text-white flex items-center justify-center">
                    <Plus className="h-4 w-4" />
                  </button>
                </div>
              )}
            </li>
          );
        })}
        {products.length === 0 && (
          <li className="text-center text-gray-500 py-10">{t('telegram.noItems', 'No items here yet.')}</li>
        )}
      </ul>

      {/* Sticky cart bar */}
      {itemCount > 0 && !showCheckout && (
        <div className="fixed bottom-0 inset-x-0 bg-white border-t p-3">
          <button
            type="button"
            onClick={() => setShowCheckout(true)}
            className="w-full h-12 rounded-xl bg-blue-600 text-white font-medium flex items-center justify-center gap-2"
          >
            <ShoppingCart className="h-5 w-5" />
            {t('telegram.checkout', 'Checkout')} · {itemCount} · {fmt(subtotal)}
          </button>
        </div>
      )}

      {/* Checkout sheet */}
      {showCheckout && (
        <div className="fixed inset-0 z-20 bg-black/40 flex items-end" onClick={() => !placing && setShowCheckout(false)}>
          <div className="bg-white w-full rounded-t-2xl p-4 max-h-[85vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="w-10 h-1 bg-gray-300 rounded-full mx-auto mb-4" />
            <h2 className="text-lg font-semibold mb-3">{t('telegram.checkout', 'Checkout')}</h2>

            <div className="grid grid-cols-2 gap-2 mb-3">
              <Toggle active={orderType === 'DELIVERY'} onClick={() => setOrderType('DELIVERY')}>{t('telegram.delivery', 'Delivery')}</Toggle>
              <Toggle active={orderType === 'TAKEAWAY'} onClick={() => setOrderType('TAKEAWAY')}>{t('telegram.pickup', 'Pickup')}</Toggle>
            </div>

            {orderType === 'DELIVERY' && (
              <textarea
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder={t('telegram.addressPlaceholder', 'Delivery address')}
                className="w-full border rounded-lg p-2 mb-3 text-sm"
                rows={2}
              />
            )}

            <div className="grid grid-cols-2 gap-2 mb-3">
              <Toggle active={paymentMethod === 'CASH'} onClick={() => setPaymentMethod('CASH')}>{t('telegram.cash', 'Cash')}</Toggle>
              <Toggle active={paymentMethod === 'CARD'} onClick={() => setPaymentMethod('CARD')}>{t('telegram.card', 'Card')}</Toggle>
            </div>

            <input
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={t('telegram.notes', 'Notes (optional)')}
              className="w-full border rounded-lg p-2 mb-3 text-sm"
            />

            <div className="flex justify-between text-sm text-gray-600 mb-1">
              <span>{t('telegram.items', 'Items')} ({itemCount})</span>
              <span>{fmt(subtotal)}</span>
            </div>

            {placeError && <p className="text-sm text-red-600 mb-2">{placeError}</p>}

            <button
              type="button"
              disabled={placing}
              onClick={handlePlace}
              className="w-full h-12 rounded-xl bg-blue-600 text-white font-medium disabled:bg-gray-300"
            >
              {placing ? t('telegram.placing', 'Placing…') : t('telegram.placeOrder', 'Place order')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function Chip({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`shrink-0 px-3 h-8 rounded-full text-sm ${active ? 'bg-blue-600 text-white' : 'bg-white text-gray-700 border'}`}
    >
      {children}
    </button>
  );
}

function Toggle({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`h-10 rounded-lg text-sm font-medium border ${active ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700'}`}
    >
      {children}
    </button>
  );
}
