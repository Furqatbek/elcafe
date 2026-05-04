import React, { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { Maximize2, Minimize2 } from 'lucide-react';
import usePOSStore from './store/posStore';
import { posAPI, shiftAPI } from '../services/api';

// Screens
import StartOrderScreen from './screens/StartOrderScreen';
import TableSelectionScreen from './screens/TableSelectionScreen';
import MenuSelectionScreen from './screens/MenuSelectionScreen';
import ProductModifiersScreen from './screens/ProductModifiersScreen';
import CartScreen from './screens/CartScreen';
import OrderDetailsScreen from './screens/OrderDetailsScreen';
import PaymentScreen from './screens/PaymentScreen';
import ActiveOrdersScreen from './screens/ActiveOrdersScreen';
import OrderModificationScreen from './screens/OrderModificationScreen';
import SplitBillScreen from './screens/SplitBillScreen';

/**
 * POSApp - Main POS application with screen routing
 * Touch-optimized restaurant point-of-sale system
 */
const POSApp = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const { ui, activeShift, setActiveShift, setCurrentScreen } = usePOSStore();
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isLoadingOrder, setIsLoadingOrder] = useState(false);
  const [shiftLoading, setShiftLoading] = useState(true);
  const [clockInError, setClockInError] = useState(null);

  const restaurantId = localStorage.getItem('selectedRestaurantId') || '1';

  // Set default restaurantId on mount if not already set
  useEffect(() => {
    if (!localStorage.getItem('selectedRestaurantId')) {
      localStorage.setItem('selectedRestaurantId', '1');
    }
  }, []);

  // Check for active shift on mount
  useEffect(() => {
    const checkShift = async () => {
      try {
        const res = await shiftAPI.getActive(restaurantId);
        const shifts = res.data.data || [];
        // Find shift for current user (first active shift)
        if (shifts.length > 0) {
          setActiveShift(shifts[0]);
        } else {
          setActiveShift(null);
        }
      } catch (e) {
        console.error('Failed to check active shift:', e);
        setActiveShift(null);
      } finally {
        setShiftLoading(false);
      }
    };
    checkShift();
  }, []);

  // Check for pending payment order on mount
  useEffect(() => {
    const screen = searchParams.get('screen');
    const pendingOrderId = localStorage.getItem('pendingPaymentOrderId');

    if (screen === 'payment' && pendingOrderId) {
      // Clear the pending order ID
      localStorage.removeItem('pendingPaymentOrderId');
      // Clear the URL param
      setSearchParams({});

      // Fetch and load the order
      setIsLoadingOrder(true);
      posAPI.getOrderById(pendingOrderId)
        .then(response => {
          const fullOrder = response.data.data;

          // Map order items to POS format
          const posItems = (fullOrder.items || []).map((item, index) => ({
            id: item.id || `${item.productId}-${index}`,
            productId: item.productId,
            name: item.productName || item.name,
            basePrice: item.unitPrice || item.price || 0,
            modifiers: item.modifiers || [],
            quantity: item.quantity,
            itemTotal: item.totalPrice || (item.unitPrice || item.price || 0) * item.quantity,
            notes: item.notes || item.specialInstructions || '',
          }));

          // Calculate totals
          const subtotal = fullOrder.subtotal || posItems.reduce((sum, item) => sum + item.itemTotal, 0);
          const tax = fullOrder.tax || 0;
          const deliveryFee = fullOrder.deliveryFee || 0;
          const serviceFeePercent = fullOrder.serviceFeePercent || 0;
          const serviceFee = fullOrder.serviceFee || 0;
          const total = fullOrder.total || (subtotal + tax + deliveryFee + serviceFee);

          // Set up POS store with order data
          usePOSStore.setState({
            currentOrder: {
              id: fullOrder.id,
              orderNumber: fullOrder.orderNumber,
              type: fullOrder.orderType || 'DINE_IN',
              items: posItems,
              subtotal,
              tax,
              deliveryFee,
              serviceFeePercent,
              serviceFee,
              total,
              notes: fullOrder.orderNotes || '',
            },
            customer: {
              id: null,
              name: fullOrder.customerName || '',
              phone: fullOrder.customerPhone || '',
              email: '',
              address: null,
              deliveryInstructions: '',
              tableNumber: fullOrder.dineInInfo?.tableNumber || null,
              tableIds: fullOrder.dineInInfo?.tableIds || null,
              guestCount: fullOrder.dineInInfo?.guestCount || null,
            },
            payment: {
              method: null,
              amountTendered: 0,
              changeDue: 0,
              status: 'PENDING',
            },
            ui: {
              currentScreen: 'payment',
              isLoading: false,
              error: null,
              selectedCategory: null,
              selectedProduct: null,
            },
          });
        })
        .catch(error => {
          console.error('Failed to load order for payment:', error);
          usePOSStore.setState({
            ui: { ...usePOSStore.getState().ui, error: t('pos.errors.loadOrderFailed', 'Failed to load order') }
          });
        })
        .finally(() => {
          setIsLoadingOrder(false);
        });
    }
  }, [searchParams, setSearchParams]);

  // Fullscreen toggle function (manual only, no auto-fullscreen)
  const toggleFullscreen = useCallback(() => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(err => {
        console.log('Fullscreen request failed:', err);
      });
    } else {
      document.exitFullscreen();
    }
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
    // Show loading while fetching order for payment
    if (isLoadingOrder) {
      return (
        <div className="h-screen flex items-center justify-center bg-gray-50">
          <div className="text-center">
            <div className="w-16 h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
            <p className="text-xl font-semibold text-gray-900">{t('pos.loading', 'Loading order...')}</p>
          </div>
        </div>
      );
    }

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

  // Clock-in gate — show if no active shift and not loading
  if (!shiftLoading && !activeShift) {
    const handleClockIn = async () => {
      setClockInError(null);
      try {
        const res = await shiftAPI.clockIn(restaurantId, {});
        setActiveShift(res.data.data || res.data);
      } catch (e) {
        setClockInError(e.response?.data?.message || e.message);
      }
    };

    return (
      <div className="h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100">
        <div className="max-w-md w-full text-center p-8">
          <div className="w-20 h-20 bg-amber-100 rounded-full flex items-center justify-center mx-auto mb-6">
            <svg className="w-10 h-10 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            {t('pos.shift.noActiveShift', 'No Active Shift')}
          </h1>
          <p className="text-lg text-gray-500 mb-8">
            {t('pos.shift.clockInRequired', 'You must clock in before using the POS')}
          </p>
          {clockInError && (
            <p className="text-red-500 mb-4">{clockInError}</p>
          )}
          <button
            onClick={handleClockIn}
            className="w-full py-4 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-xl font-semibold rounded-xl transition-colors"
          >
            {t('pos.shift.clockIn', 'Clock In')}
          </button>
        </div>
      </div>
    );
  }

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
