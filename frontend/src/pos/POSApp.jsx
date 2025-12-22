import React, { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Maximize2, Minimize2 } from 'lucide-react';
import usePOSStore from './store/posStore';

// Screens
import StartOrderScreen from './screens/StartOrderScreen';
import TableSelectionScreen from './screens/TableSelectionScreen';
import MenuSelectionScreen from './screens/MenuSelectionScreen';
import ProductModifiersScreen from './screens/ProductModifiersScreen';
import CartScreen from './screens/CartScreen';
import OrderDetailsScreen from './screens/OrderDetailsScreen';
import PaymentScreen from './screens/PaymentScreen';
import OrderConfirmationScreen from './screens/OrderConfirmationScreen';
import ActiveOrdersScreen from './screens/ActiveOrdersScreen';
import OrderModificationScreen from './screens/OrderModificationScreen';
import SplitBillScreen from './screens/SplitBillScreen';

/**
 * POSApp - Main POS application with screen routing
 * Touch-optimized restaurant point-of-sale system
 * Auto-enters fullscreen mode, ESC to exit
 */
const POSApp = () => {
  const { t } = useTranslation();
  const { ui } = usePOSStore();
  const [isFullscreen, setIsFullscreen] = useState(false);

  // Fullscreen toggle function
  const toggleFullscreen = useCallback(() => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(err => {
        console.log('Fullscreen request failed:', err);
      });
    } else {
      document.exitFullscreen();
    }
  }, []);

  // Auto-enter fullscreen on mount
  useEffect(() => {
    // Small delay to ensure DOM is ready
    const timer = setTimeout(() => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(err => {
          console.log('Auto-fullscreen failed:', err);
        });
      }
    }, 100);

    return () => clearTimeout(timer);
  }, []);

  // Listen for fullscreen changes (including ESC key exit)
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  // Prevent accidental page navigation
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      const { currentOrder } = usePOSStore.getState();
      if (currentOrder.items.length > 0) {
        e.preventDefault();
        e.returnValue = 'You have items in your cart. Are you sure you want to leave?';
        return e.returnValue;
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, []);

  // Render current screen based on UI state
  const renderScreen = () => {
    switch (ui.currentScreen) {
      case 'start':
        return <StartOrderScreen />;

      case 'tables':
        return <TableSelectionScreen />;

      case 'menu':
        return <MenuSelectionScreen />;

      case 'modifiers':
        return <ProductModifiersScreen />;

      case 'cart':
        return <CartScreen />;

      case 'details':
        return <OrderDetailsScreen />;

      case 'payment':
        return <PaymentScreen />;

      case 'confirmation':
        return <OrderConfirmationScreen />;

      case 'active-orders':
        return <ActiveOrdersScreen />;

      case 'modify-order':
        return <OrderModificationScreen />;

      case 'split-bill':
        return <SplitBillScreen />;

      default:
        return <StartOrderScreen />;
    }
  };

  return (
    <div className="pos-app h-screen overflow-hidden">
      {renderScreen()}

      {/* Fullscreen Toggle Button */}
      <button
        onClick={toggleFullscreen}
        className="fixed bottom-4 right-4 z-50 p-3 bg-gray-800 hover:bg-gray-700 text-white rounded-full shadow-lg opacity-50 hover:opacity-100 transition-opacity"
        title={isFullscreen ? t('pos.exitFullscreen', 'Exit Fullscreen (ESC)') : t('pos.enterFullscreen', 'Enter Fullscreen')}
      >
        {isFullscreen ? <Minimize2 className="w-5 h-5" /> : <Maximize2 className="w-5 h-5" />}
      </button>

      {/* Global Loading Overlay */}
      {ui.isLoading && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[9999]">
          <div className="bg-white rounded-2xl p-8 shadow-2xl text-center">
            <div className="w-16 h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
            <p className="text-xl font-semibold text-gray-900">{t('pos.loading', 'Loading...')}</p>
          </div>
        </div>
      )}

      {/* Global Error Toast */}
      {ui.error && (
        <div className="fixed top-4 right-4 bg-red-500 text-white px-6 py-4 rounded-lg shadow-lg z-[9999] animate-in slide-in-from-right">
          <div className="flex items-center gap-3">
            <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                clipRule="evenodd"
              />
            </svg>
            <div>
              <p className="font-semibold">{t('common.error', 'Error')}</p>
              <p className="text-sm">{ui.error}</p>
            </div>
            <button
              onClick={() => usePOSStore.getState().clearError()}
              className="ml-4 hover:bg-red-600 p-1 rounded"
            >
              <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                <path
                  fillRule="evenodd"
                  d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                  clipRule="evenodd"
                />
              </svg>
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default POSApp;
