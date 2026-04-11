import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { kitchenAPI } from '../../services/api';
import { ChefHat, CheckCircle, Clock } from 'lucide-react';

const POLL_INTERVAL = 5000; // Poll every 5 seconds

/**
 * OrderStatusBoardScreen — customer-facing order status display.
 * Shows a kanban-style board with "Processing" and "Ready" columns.
 * Designed for a wall-mounted TV/monitor in the restaurant.
 *
 * Route: /admin/pos/order-status?restaurant={id}
 *
 * Auto-fullscreen, polls kitchen API for real-time order updates.
 */
export default function OrderStatusBoardScreen() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const restaurantId = searchParams.get('restaurant');

  const [processingOrders, setProcessingOrders] = useState([]);
  const [readyOrders, setReadyOrders] = useState([]);
  const [lastUpdated, setLastUpdated] = useState(null);

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

  // --- Poll kitchen API ---
  const fetchOrders = useCallback(async () => {
    if (!restaurantId) return;
    try {
      const [activeRes, readyRes] = await Promise.all([
        kitchenAPI.getActiveOrders(restaurantId),
        kitchenAPI.getReadyOrders(restaurantId),
      ]);

      const active = activeRes.data.data || activeRes.data || [];
      const ready = readyRes.data.data || readyRes.data || [];

      // Processing = PENDING + PREPARING from active orders
      setProcessingOrders(
        active
          .filter(o => o.status === 'PENDING' || o.status === 'PREPARING')
          .sort((a, b) => (a.createdAt || '').localeCompare(b.createdAt || ''))
      );

      setReadyOrders(
        ready.sort((a, b) => (b.readyAt || b.updatedAt || '').localeCompare(a.readyAt || a.updatedAt || ''))
      );

      setLastUpdated(new Date());
    } catch (err) {
      console.error('Failed to fetch orders:', err);
    }
  }, [restaurantId]);

  useEffect(() => {
    fetchOrders();
    const interval = setInterval(fetchOrders, POLL_INTERVAL);
    return () => clearInterval(interval);
  }, [fetchOrders]);

  // --- Helpers ---
  const getOrderNumber = (order) => {
    return order.orderNumber || order.order?.orderNumber || `#${order.id}`;
  };

  const getOrderType = (order) => {
    return order.orderType || order.order?.orderType || '';
  };

  const formatTime = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  // --- Render ---
  return (
    <div className="h-screen w-screen bg-gray-950 flex flex-col overflow-hidden" onClick={enterFullscreen}>
      {/* Header */}
      <div className="flex-shrink-0 bg-gray-900 border-b border-gray-800 px-8 py-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">
          {t('pos.orderBoard.title', 'Order Status')}
        </h1>
        <div className="flex items-center gap-4 text-gray-400 text-sm">
          {lastUpdated && (
            <span>{formatTime(lastUpdated.toISOString())}</span>
          )}
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
            <span>{t('pos.orderBoard.live', 'Live')}</span>
          </div>
        </div>
      </div>

      {/* Kanban Board */}
      <div className="flex-1 flex min-h-0">
        {/* Processing Column */}
        <div className="flex-1 flex flex-col border-r border-gray-800">
          {/* Column Header */}
          <div className="flex-shrink-0 bg-amber-500/10 px-8 py-5 flex items-center gap-3 border-b border-gray-800">
            <ChefHat className="w-8 h-8 text-amber-400" />
            <h2 className="text-3xl font-bold text-amber-400">
              {t('pos.orderBoard.processing', 'Processing')}
            </h2>
            <span className="ml-auto bg-amber-500/20 text-amber-300 px-4 py-1.5 rounded-full text-xl font-bold">
              {processingOrders.length}
            </span>
          </div>

          {/* Order Cards */}
          <div className="flex-1 overflow-y-auto p-6">
            {processingOrders.length === 0 ? (
              <div className="h-full flex items-center justify-center">
                <div className="text-center text-gray-600">
                  <Clock className="w-16 h-16 mx-auto mb-4" />
                  <p className="text-xl">{t('pos.orderBoard.noProcessing', 'No orders processing')}</p>
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-2 xl:grid-cols-3 gap-4">
                {processingOrders.map((order) => (
                  <div
                    key={order.id}
                    className={`rounded-2xl p-6 border-2 ${
                      order.status === 'PREPARING'
                        ? 'bg-amber-500/10 border-amber-500/30 animate-pulse-slow'
                        : 'bg-gray-800/50 border-gray-700'
                    }`}
                  >
                    <div className="text-4xl font-black text-white mb-2">
                      {getOrderNumber(order)}
                    </div>
                    <div className="flex items-center gap-2 text-gray-400">
                      {order.status === 'PREPARING' ? (
                        <>
                          <ChefHat className="w-4 h-4 text-amber-400" />
                          <span className="text-amber-400 font-medium">
                            {t('pos.orderBoard.preparing', 'Preparing')}
                          </span>
                        </>
                      ) : (
                        <>
                          <Clock className="w-4 h-4" />
                          <span>{t('pos.orderBoard.queued', 'In Queue')}</span>
                        </>
                      )}
                      {getOrderType(order) && (
                        <span className="ml-auto text-xs bg-gray-700 px-2 py-0.5 rounded text-gray-300">
                          {getOrderType(order)}
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
          <div className="flex-shrink-0 bg-green-500/10 px-8 py-5 flex items-center gap-3 border-b border-gray-800">
            <CheckCircle className="w-8 h-8 text-green-400" />
            <h2 className="text-3xl font-bold text-green-400">
              {t('pos.orderBoard.ready', 'Ready')}
            </h2>
            <span className="ml-auto bg-green-500/20 text-green-300 px-4 py-1.5 rounded-full text-xl font-bold">
              {readyOrders.length}
            </span>
          </div>

          {/* Order Cards */}
          <div className="flex-1 overflow-y-auto p-6">
            {readyOrders.length === 0 ? (
              <div className="h-full flex items-center justify-center">
                <div className="text-center text-gray-600">
                  <CheckCircle className="w-16 h-16 mx-auto mb-4" />
                  <p className="text-xl">{t('pos.orderBoard.noReady', 'No orders ready')}</p>
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-2 xl:grid-cols-3 gap-4">
                {readyOrders.map((order) => (
                  <div
                    key={order.id}
                    className="rounded-2xl p-6 border-2 bg-green-500/10 border-green-500/30 animate-in zoom-in duration-500"
                  >
                    <div className="text-5xl font-black text-green-400 mb-2">
                      {getOrderNumber(order)}
                    </div>
                    <div className="flex items-center gap-2 text-green-300">
                      <CheckCircle className="w-5 h-5" />
                      <span className="font-medium text-lg">
                        {t('pos.orderBoard.pickUp', 'Pick up!')}
                      </span>
                      {getOrderType(order) && (
                        <span className="ml-auto text-xs bg-green-900/50 px-2 py-0.5 rounded text-green-300">
                          {getOrderType(order)}
                        </span>
                      )}
                    </div>
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
