import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { kitchenAPI, orderAPI } from '../../services/api';
import { useWebSocketNotifications } from '../../hooks/useWebSocketNotifications';
import { ChefHat, CheckCircle, Clock } from 'lucide-react';

const FALLBACK_POLL_INTERVAL = 120000; // Fallback poll every 2 minutes

/**
 * OrderStatusBoardScreen — customer-facing order status display.
 * Shows a kanban-style board with "Processing" and "Ready" columns.
 * Designed for a wall-mounted TV/monitor in the restaurant.
 *
 * Route: /admin/pos/order-status?restaurant={id}
 *
 * Uses WebSocket for instant updates + 2-minute polling fallback.
 */
export default function OrderStatusBoardScreen() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const restaurantId = searchParams.get('restaurant');

  const [processingOrders, setProcessingOrders] = useState([]);
  const [readyOrders, setReadyOrders] = useState([]);
  const [totalFetched, setTotalFetched] = useState(0);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [error, setError] = useState(null);

  // --- Auto-fullscreen ---
  const enterFullscreen = useCallback(() => {
    const el = document.documentElement;
    if (el.requestFullscreen) {
      el.requestFullscreen().catch(() => {});
    } else if (el.webkitRequestFullscreen) {
      el.webkitRequestFullscreen();
    }
  }, []);

  useEffect(() => {
    enterFullscreen();

    const handleFullscreenChange = () => {
      if (!document.fullscreenElement) {
        const handleClick = () => {
          enterFullscreen();
          document.removeEventListener('click', handleClick);
        };
        document.addEventListener('click', handleClick);
      }
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, [enterFullscreen]);

  // --- Hide cursor after inactivity ---
  useEffect(() => {
    let timer;
    const hideCursor = () => { document.body.style.cursor = 'none'; };
    const showCursor = () => {
      document.body.style.cursor = 'default';
      clearTimeout(timer);
      timer = setTimeout(hideCursor, 3000);
    };

    document.addEventListener('mousemove', showCursor);
    timer = setTimeout(hideCursor, 3000);

    return () => {
      document.removeEventListener('mousemove', showCursor);
      clearTimeout(timer);
      document.body.style.cursor = 'default';
    };
  }, []);

  const PROCESSING_STATUSES = ['NEW', 'PLACED', 'ACCEPTED', 'PREPARING'];
  const READY_STATUSES = ['READY'];

  // --- Fetch orders from API ---
  const fetchOrders = useCallback(async () => {
    if (!restaurantId) return;
    try {
      // Use order API (works for all orders, not just kitchen-accepted ones)
      const res = await orderAPI.getByRestaurant(restaurantId);
      const orders = res.data.data || res.data || [];
      setTotalFetched(orders.length);

      if (orders.length > 0) {
        console.log('[OrderStatusBoard] Fetched', orders.length, 'orders. Statuses:',
          orders.map(o => `${o.orderNumber}=${o.status}`).join(', '));
      }

      setProcessingOrders(
        orders
          .filter(o => PROCESSING_STATUSES.includes(o.status))
          .sort((a, b) => (a.createdAt || '').localeCompare(b.createdAt || ''))
      );

      setReadyOrders(
        orders
          .filter(o => READY_STATUSES.includes(o.status))
          .sort((a, b) => (b.updatedAt || b.createdAt || '').localeCompare(a.updatedAt || a.createdAt || ''))
      );

      setLastUpdated(new Date());
      setError(null);
    } catch (err) {
      console.error('Failed to fetch orders:', err);
      setError(err.response?.status === 401
        ? t('pos.orderBoard.authError', 'Please log in to the POS first')
        : err.message);
    }
  }, [restaurantId]);

  // WebSocket: instant refresh on order events
  useWebSocketNotifications({
    restaurantId: restaurantId || null,
    enabled: !!restaurantId,
    toastEnabled: false,
    soundEnabled: false,
    browserNotificationEnabled: false,
    onOrderEvent: useCallback(() => {
      if (restaurantId) fetchOrders();
    }, [restaurantId, fetchOrders]),
  });

  // Initial fetch + fallback polling every 2 minutes
  useEffect(() => {
    if (!restaurantId) return;
    fetchOrders();
    const interval = setInterval(fetchOrders, FALLBACK_POLL_INTERVAL);
    return () => clearInterval(interval);
  }, [restaurantId, fetchOrders]);

  // --- Helpers ---
  const getOrderNumber = (order) => {
    return order.orderNumber || `#${order.id}`;
  };

  const getOrderType = (order) => {
    return order.orderType || '';
  };

  const formatTime = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  // --- Render ---

  // Missing restaurant param
  if (!restaurantId) {
    return (
      <div className="h-screen w-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-2xl font-bold text-gray-900 mb-2">Missing restaurant parameter</p>
          <p className="text-gray-500">URL should be: /admin/pos/order-status?restaurant=1</p>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen w-screen bg-gray-50 flex flex-col overflow-hidden" onClick={enterFullscreen}>
      {/* Header */}
      <div className="flex-shrink-0 bg-white border-b border-gray-200 px-8 py-4 flex items-center justify-between shadow-sm">
        <div className="flex items-center gap-4">
          <h1 className="text-2xl font-bold text-gray-900">
            {t('pos.orderBoard.title', 'Order Status')}
          </h1>
          <span className="text-sm text-gray-400">
            R:{restaurantId} | {totalFetched} orders
          </span>
        </div>
        <div className="flex items-center gap-4 text-sm">
          {error && (
            <span className="text-red-500">{error}</span>
          )}
          {lastUpdated && (
            <span className="text-gray-400">{formatTime(lastUpdated.toISOString())}</span>
          )}
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${error ? 'bg-red-500' : 'bg-green-500'} animate-pulse`} />
            <span className={error ? 'text-red-500' : 'text-gray-500'}>{error ? t('pos.orderBoard.error', 'Error') : t('pos.orderBoard.live', 'Live')}</span>
          </div>
        </div>
      </div>

      {/* Kanban Board */}
      <div className="flex-1 flex min-h-0">
        {/* Processing Column */}
        <div className="flex-1 flex flex-col border-r border-gray-200">
          {/* Column Header */}
          <div className="flex-shrink-0 bg-amber-50 px-8 py-5 flex items-center gap-3 border-b border-gray-200">
            <ChefHat className="w-8 h-8 text-amber-600" />
            <h2 className="text-3xl font-bold text-amber-600">
              {t('pos.orderBoard.processing', 'Processing')}
            </h2>
            <span className="ml-auto bg-amber-100 text-amber-700 px-4 py-1.5 rounded-full text-xl font-bold">
              {processingOrders.length}
            </span>
          </div>

          {/* Order List */}
          <div className="flex-1 overflow-y-auto px-8 py-4">
            {processingOrders.length === 0 ? (
              <div className="h-full flex items-center justify-center">
                <div className="text-center text-gray-300">
                  <Clock className="w-16 h-16 mx-auto mb-4" />
                  <p className="text-xl">{t('pos.orderBoard.noProcessing', 'No orders processing')}</p>
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                {processingOrders.map((order) => (
                  <div
                    key={order.id}
                    className={`flex items-center justify-between py-4 px-6 rounded-xl ${
                      order.status === 'PREPARING'
                        ? 'bg-amber-50 border border-amber-200 animate-pulse-slow'
                        : 'bg-white border border-gray-100'
                    }`}
                  >
                    <span className="text-5xl font-black text-gray-900">
                      {getOrderNumber(order)}
                    </span>
                    <div className="flex items-center gap-3">
                      {order.status === 'PREPARING' ? (
                        <span className="text-2xl text-amber-600 font-semibold flex items-center gap-2">
                          <ChefHat className="w-7 h-7" />
                          {t('pos.orderBoard.preparing', 'Preparing')}
                        </span>
                      ) : (
                        <span className="text-2xl text-gray-400 font-medium flex items-center gap-2">
                          <Clock className="w-6 h-6" />
                          {t('pos.orderBoard.queued', 'In Queue')}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Ready Column */}
        <div className="flex-1 flex flex-col">
          {/* Column Header */}
          <div className="flex-shrink-0 bg-green-50 px-8 py-5 flex items-center gap-3 border-b border-gray-200">
            <CheckCircle className="w-8 h-8 text-green-600" />
            <h2 className="text-3xl font-bold text-green-600">
              {t('pos.orderBoard.ready', 'Ready')}
            </h2>
            <span className="ml-auto bg-green-100 text-green-700 px-4 py-1.5 rounded-full text-xl font-bold">
              {readyOrders.length}
            </span>
          </div>

          {/* Order List */}
          <div className="flex-1 overflow-y-auto px-8 py-4">
            {readyOrders.length === 0 ? (
              <div className="h-full flex items-center justify-center">
                <div className="text-center text-gray-300">
                  <CheckCircle className="w-16 h-16 mx-auto mb-4" />
                  <p className="text-xl">{t('pos.orderBoard.noReady', 'No orders ready')}</p>
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                {readyOrders.map((order) => (
                  <div
                    key={order.id}
                    className="flex items-center justify-between py-4 px-6 rounded-xl bg-green-50 border border-green-200 animate-in slide-in-from-left duration-500"
                  >
                    <span className="text-6xl font-black text-green-600">
                      {getOrderNumber(order)}
                    </span>
                    <span className="text-2xl text-green-600 font-semibold flex items-center gap-2">
                      <CheckCircle className="w-7 h-7" />
                      {t('pos.orderBoard.pickUp', 'Pick up!')}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Subtle pulse animation for preparing orders */}
      <style>{`
        @keyframes pulse-slow {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.85; }
        }
        .animate-pulse-slow {
          animation: pulse-slow 2s ease-in-out infinite;
        }
      `}</style>
    </div>
  );
}
