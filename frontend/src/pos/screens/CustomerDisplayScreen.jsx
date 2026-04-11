import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { restaurantAPI } from '../../services/api';
import CustomerAdsPanel from '../components/CustomerAdsPanel';

const CUSTOMER_DISPLAY_KEY = 'pos_customer_display_order';
import CustomerOrderPanel from '../components/CustomerOrderPanel';
import { CheckCircle } from 'lucide-react';

const COMPLETED_DISPLAY_MS = 5000; // Show "Thank you" for 5 seconds

/**
 * CustomerDisplayScreen — standalone customer-facing display page.
 * Runs in a separate browser window on the customer-facing monitor.
 * Auto-enters fullscreen. Syncs with POS via localStorage events.
 *
 * Route: /admin/pos/customer-display?restaurant={id}
 *
 * States:
 * - idle: no active order → full-screen branding
 * - active: order being built → 70/30 split (ads | order)
 * - completed: order submitted → "Thank you + order number" → returns to idle
 */
export default function CustomerDisplayScreen() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const restaurantId = searchParams.get('restaurant');

  const [restaurant, setRestaurant] = useState(null);
  const [order, setOrder] = useState(null);
  const [displayStatus, setDisplayStatus] = useState('idle'); // 'idle' | 'active' | 'completed'
  const [completedOrderNumber, setCompletedOrderNumber] = useState(null);

  // --- Auto-fullscreen ---
  const enterFullscreen = useCallback(() => {
    const el = document.documentElement;
    if (el.requestFullscreen) {
      el.requestFullscreen().catch(() => {});
    } else if (el.webkitRequestFullscreen) {
      el.webkitRequestFullscreen();
    } else if (el.msRequestFullscreen) {
      el.msRequestFullscreen();
    }
  }, []);

  useEffect(() => {
    // Enter fullscreen on mount
    enterFullscreen();

    // Re-enter on fullscreen exit (click anywhere to restore)
    const handleFullscreenChange = () => {
      if (!document.fullscreenElement) {
        // Fullscreen exited — next click will re-enter
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

  // --- Hide cursor after inactivity (kiosk mode) ---
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

  // --- Load restaurant info ---
  useEffect(() => {
    if (!restaurantId) return;
    restaurantAPI.getById(restaurantId)
      .then((res) => setRestaurant(res.data.data || res.data))
      .catch((err) => console.error('Failed to load restaurant:', err));
  }, [restaurantId]);

  // --- Sync with POS via localStorage ---
  const processOrderData = useCallback((data) => {
    if (!data) {
      setDisplayStatus('idle');
      setOrder(null);
      return;
    }

    if (data.status === 'completed') {
      setCompletedOrderNumber(data.orderNumber);
      setDisplayStatus('completed');
      // Return to idle after delay
      setTimeout(() => {
        setDisplayStatus('idle');
        setOrder(null);
        setCompletedOrderNumber(null);
      }, COMPLETED_DISPLAY_MS);
    } else if (data.status === 'active' && data.items?.length > 0) {
      setOrder(data);
      setDisplayStatus('active');
    } else {
      setDisplayStatus('idle');
      setOrder(null);
    }
  }, []);

  // Listen for storage events (cross-tab sync)
  useEffect(() => {
    const handleStorage = (e) => {
      if (e.key === CUSTOMER_DISPLAY_KEY && e.newValue) {
        try {
          processOrderData(JSON.parse(e.newValue));
        } catch {
          // Ignore parse errors
        }
      }
    };

    window.addEventListener('storage', handleStorage);

    // Also load current state on mount
    try {
      const stored = localStorage.getItem(CUSTOMER_DISPLAY_KEY);
      if (stored) processOrderData(JSON.parse(stored));
    } catch {
      // Ignore
    }

    return () => window.removeEventListener('storage', handleStorage);
  }, [processOrderData]);

  // Fallback: poll localStorage every 500ms (storage events don't fire in same tab)
  useEffect(() => {
    let lastUpdatedAt = null;
    const interval = setInterval(() => {
      try {
        const stored = localStorage.getItem(CUSTOMER_DISPLAY_KEY);
        if (stored) {
          const data = JSON.parse(stored);
          if (data.updatedAt !== lastUpdatedAt) {
            lastUpdatedAt = data.updatedAt;
            processOrderData(data);
          }
        }
      } catch {
        // Ignore
      }
    }, 500);
    return () => clearInterval(interval);
  }, [processOrderData]);

  // --- Render ---

  // Completed state: Thank you + order number
  if (displayStatus === 'completed') {
    return (
      <div className="h-screen w-screen bg-gray-950 flex items-center justify-center" onClick={enterFullscreen}>
        <div className="text-center animate-in zoom-in duration-500">
          <CheckCircle className="w-28 h-28 text-green-400 mx-auto mb-8" />
          <h1 className="text-6xl font-bold text-white mb-6">
            {t('pos.customerDisplay.thankYou', 'Thank you!')}
          </h1>
          {completedOrderNumber && (
            <p className="text-3xl text-gray-300">
              {t('pos.customerDisplay.orderNumber', 'Your order number is {{orderNumber}}', { orderNumber: completedOrderNumber })}
            </p>
          )}
        </div>
      </div>
    );
  }

  // Idle state: full-screen branding
  if (displayStatus === 'idle' || !order) {
    return (
      <div className="h-screen w-screen" onClick={enterFullscreen}>
        <CustomerAdsPanel restaurant={restaurant} restaurantId={restaurantId} />
      </div>
    );
  }

  // Active state: 70/30 split
  return (
    <div className="h-screen w-screen flex" onClick={enterFullscreen}>
      {/* 70% — Ads / Branding (60% on smaller screens) */}
      <div className="w-[60%] lg:w-[70%] h-full">
        <CustomerAdsPanel restaurant={restaurant} restaurantId={restaurantId} />
      </div>

      {/* 30% — Order Info (40% on smaller screens) */}
      <div className="w-[40%] lg:w-[30%] h-full border-l border-gray-800">
        <CustomerOrderPanel order={order} />
      </div>
    </div>
  );
}
