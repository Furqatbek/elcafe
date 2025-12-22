import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import {
  ArrowLeft,
  RefreshCw,
  Users,
  Clock,
  Edit2,
  CreditCard,
  SplitSquareHorizontal,
  Utensils,
  ChefHat
} from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';
import { posAPI } from '../../services/api';

/**
 * ActiveOrdersScreen - Shows all open dine-in orders
 * Allows selecting an order to modify, pay, or split bill
 */
const ActiveOrdersScreen = () => {
  const { t } = useTranslation();
  const { setCurrentScreen, setActiveOrder, ui } = usePOSStore();

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedOrderId, setSelectedOrderId] = useState(null);

  const restaurantId = 1; // TODO: Get from context or settings

  // Fetch open dine-in orders
  const fetchOrders = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await posAPI.getOpenDineInOrders(restaurantId);
      setOrders(response.data.data || []);
    } catch (err) {
      console.error('Failed to fetch orders:', err);
      setError(err.response?.data?.message || t('pos.orders.fetchError', 'Failed to load orders'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  const handleSelectOrder = (order) => {
    setSelectedOrderId(order.id === selectedOrderId ? null : order.id);
  };

  const handleModifyOrder = (order) => {
    // Store selected order and navigate to modification screen
    usePOSStore.setState({
      activeOrder: order,
      ui: { ...ui, currentScreen: 'modify-order' }
    });
  };

  const handlePayOrder = (order) => {
    // Store selected order and navigate to payment
    usePOSStore.setState({
      activeOrder: order,
      currentOrder: {
        id: order.id,
        orderNumber: order.orderNumber,
        type: 'DINE_IN',
        items: order.items || [],
        subtotal: order.subtotal,
        tax: order.tax || 0,
        deliveryFee: 0,
        total: order.total,
        notes: order.orderNotes || '',
      },
      customer: {
        tableNumber: order.tableNumber,
        tableIds: order.tableIds,
        guestCount: order.guestCount,
      },
      ui: { ...ui, currentScreen: 'payment' }
    });
  };

  const handleSplitBill = (order) => {
    // Store selected order and navigate to split bill screen
    usePOSStore.setState({
      activeOrder: order,
      ui: { ...ui, currentScreen: 'split-bill' }
    });
  };

  const getStatusColor = (status) => {
    const colors = {
      PENDING: 'bg-yellow-100 text-yellow-800',
      CONFIRMED: 'bg-blue-100 text-blue-800',
      PREPARING: 'bg-orange-100 text-orange-800',
      READY: 'bg-green-100 text-green-800',
      SERVED: 'bg-purple-100 text-purple-800',
    };
    return colors[status] || 'bg-gray-100 text-gray-800';
  };

  const getKitchenStatusIcon = (kitchenStatus) => {
    if (!kitchenStatus) return null;

    const icons = {
      PENDING: <Clock className="w-4 h-4 text-yellow-600" />,
      PREPARING: <ChefHat className="w-4 h-4 text-orange-600 animate-pulse" />,
      READY: <Utensils className="w-4 h-4 text-green-600" />,
    };
    return icons[kitchenStatus];
  };

  const formatTime = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const selectedOrder = orders.find(o => o.id === selectedOrderId);

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-6 py-4 flex-shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <TouchButton
              variant="ghost"
              size="medium"
              onClick={() => setCurrentScreen('start')}
            >
              <ArrowLeft className="w-5 h-5" />
            </TouchButton>
            <h1 className="text-2xl font-bold text-gray-900">
              {t('pos.orders.activeOrders', 'Active Orders')}
            </h1>
            <span className="bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm font-medium">
              {orders.length} {t('pos.orders.open', 'open')}
            </span>
          </div>

          <TouchButton
            variant="secondary"
            size="medium"
            onClick={fetchOrders}
            disabled={loading}
          >
            <RefreshCw className={cn('w-5 h-5 mr-2', loading && 'animate-spin')} />
            {t('common.buttons.refresh', 'Refresh')}
          </TouchButton>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Orders List */}
        <div className="flex-1 overflow-y-auto p-6">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="w-16 h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
                <p className="text-xl text-gray-600">
                  {t('pos.orders.loading', 'Loading orders...')}
                </p>
              </div>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <p className="text-xl text-red-600 mb-4">{error}</p>
                <TouchButton variant="primary" onClick={fetchOrders}>
                  {t('common.buttons.tryAgain', 'Try Again')}
                </TouchButton>
              </div>
            </div>
          ) : orders.length === 0 ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <Utensils className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                <p className="text-2xl text-gray-500 mb-2">
                  {t('pos.orders.noActiveOrders', 'No active orders')}
                </p>
                <p className="text-gray-400">
                  {t('pos.orders.startNewOrder', 'Start a new order to see it here')}
                </p>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {orders.map(order => (
                <button
                  key={order.id}
                  onClick={() => handleSelectOrder(order)}
                  className={cn(
                    'bg-white rounded-xl border-2 p-5 text-left transition-all',
                    'hover:shadow-lg hover:border-blue-300',
                    selectedOrderId === order.id
                      ? 'border-blue-500 ring-2 ring-blue-200'
                      : 'border-gray-200'
                  )}
                >
                  {/* Order Header */}
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <span className="text-xl font-bold text-gray-900">
                        #{order.orderNumber}
                      </span>
                      <span className={cn(
                        'px-2 py-1 rounded-full text-xs font-medium',
                        getStatusColor(order.status)
                      )}>
                        {order.status}
                      </span>
                    </div>
                    {order.kitchenStatus && getKitchenStatusIcon(order.kitchenStatus)}
                  </div>

                  {/* Table Info */}
                  <div className="flex items-center gap-4 mb-3 text-gray-600">
                    <div className="flex items-center gap-1">
                      <Utensils className="w-4 h-4" />
                      <span className="font-medium">
                        {t('pos.orders.table', 'Table')} {order.tableNumber}
                      </span>
                    </div>
                    {order.guestCount && (
                      <div className="flex items-center gap-1">
                        <Users className="w-4 h-4" />
                        <span>{order.guestCount}</span>
                      </div>
                    )}
                    <div className="flex items-center gap-1">
                      <Clock className="w-4 h-4" />
                      <span>{formatTime(order.createdAt)}</span>
                    </div>
                  </div>

                  {/* Items Preview */}
                  <div className="text-sm text-gray-500 mb-3">
                    {order.items?.slice(0, 3).map((item, idx) => (
                      <span key={idx}>
                        {item.quantity}x {item.productName || item.name}
                        {idx < Math.min(order.items.length - 1, 2) && ', '}
                      </span>
                    ))}
                    {order.items?.length > 3 && (
                      <span className="text-gray-400">
                        {' '}+{order.items.length - 3} {t('pos.orders.more', 'more')}
                      </span>
                    )}
                  </div>

                  {/* Total */}
                  <div className="text-right">
                    <span className="text-2xl font-bold text-gray-900">
                      {order.total?.toFixed(2)}
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Action Panel (when order selected) */}
        {selectedOrder && (
          <div className="w-80 bg-white border-l-2 border-gray-200 p-6 flex flex-col">
            <h2 className="text-lg font-bold text-gray-900 mb-4">
              {t('pos.orders.orderActions', 'Order Actions')}
            </h2>

            <div className="space-y-3 flex-1">
              <TouchButton
                variant="primary"
                size="large"
                className="w-full justify-start"
                onClick={() => handleModifyOrder(selectedOrder)}
              >
                <Edit2 className="w-5 h-5 mr-3" />
                {t('pos.orders.modifyOrder', 'Modify Order')}
              </TouchButton>

              <TouchButton
                variant="success"
                size="large"
                className="w-full justify-start"
                onClick={() => handlePayOrder(selectedOrder)}
              >
                <CreditCard className="w-5 h-5 mr-3" />
                {t('pos.orders.payNow', 'Pay Now')}
              </TouchButton>

              <TouchButton
                variant="secondary"
                size="large"
                className="w-full justify-start"
                onClick={() => handleSplitBill(selectedOrder)}
              >
                <SplitSquareHorizontal className="w-5 h-5 mr-3" />
                {t('pos.orders.splitBill', 'Split Bill')}
              </TouchButton>
            </div>

            {/* Order Summary */}
            <div className="border-t border-gray-200 pt-4 mt-4">
              <h3 className="font-medium text-gray-700 mb-2">
                {t('pos.orders.orderSummary', 'Order Summary')}
              </h3>
              <div className="space-y-1 text-sm">
                {selectedOrder.items?.map((item, idx) => (
                  <div key={idx} className="flex justify-between">
                    <span className="text-gray-600">
                      {item.quantity}x {item.productName || item.name}
                    </span>
                    <span className="font-medium">
                      {(item.subtotal || item.itemTotal || 0).toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>
              <div className="border-t border-gray-200 mt-2 pt-2">
                <div className="flex justify-between font-bold text-lg">
                  <span>{t('pos.cart.total', 'Total')}</span>
                  <span>{selectedOrder.total?.toFixed(2)}</span>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ActiveOrdersScreen;
