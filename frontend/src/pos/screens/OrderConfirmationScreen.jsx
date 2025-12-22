import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle, Printer, Home, ChefHat, Clock, RefreshCw } from 'lucide-react';
import { cn } from '../../lib/utils';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';
import PrintReceipt from '../../components/PrintReceipt';
import { posAPI } from '../../services/api';

/**
 * OrderConfirmationScreen - Success confirmation after payment
 * Order number display, print receipt, start new order, kitchen status tracking
 */
const OrderConfirmationScreen = () => {
  const { t } = useTranslation();
  const { currentOrder, customer, kitchenStatus, fetchKitchenStatus, clearKitchenStatus, resetPOS } = usePOSStore();
  const [isPolling, setIsPolling] = useState(true);
  const [fullOrderData, setFullOrderData] = useState(null);

  // Fetch full order data for printing
  useEffect(() => {
    const fetchFullOrder = async () => {
      if (currentOrder.id) {
        try {
          const response = await posAPI.getOrderById(currentOrder.id);
          setFullOrderData(response.data.data);
        } catch (error) {
          console.error('Failed to fetch full order data:', error);
        }
      }
    };
    fetchFullOrder();
  }, [currentOrder.id]);

  // Poll kitchen status every 10 seconds
  useEffect(() => {
    if (!currentOrder.id && !currentOrder.orderNumber) return;

    // Initial fetch
    const orderId = currentOrder.id;
    if (orderId) {
      fetchKitchenStatus(orderId);
    }

    // Set up polling interval
    const interval = setInterval(() => {
      if (isPolling && orderId) {
        fetchKitchenStatus(orderId);
      }
    }, 10000);

    return () => {
      clearInterval(interval);
      clearKitchenStatus();
    };
  }, [currentOrder.id, isPolling]);

  const getKitchenStatusConfig = (status) => {
    const configs = {
      PENDING: {
        label: t('pos.kitchen.pending', 'Pending'),
        icon: <Clock className="w-5 h-5" />,
        bgColor: 'bg-yellow-100',
        textColor: 'text-yellow-800',
        borderColor: 'border-yellow-300',
      },
      PREPARING: {
        label: t('pos.kitchen.preparing', 'Preparing'),
        icon: <ChefHat className="w-5 h-5 animate-pulse" />,
        bgColor: 'bg-blue-100',
        textColor: 'text-blue-800',
        borderColor: 'border-blue-300',
      },
      READY: {
        label: t('pos.kitchen.ready', 'Ready'),
        icon: <CheckCircle className="w-5 h-5" />,
        bgColor: 'bg-green-100',
        textColor: 'text-green-800',
        borderColor: 'border-green-300',
      },
      PICKED_UP: {
        label: t('pos.kitchen.pickedUp', 'Picked Up'),
        icon: <CheckCircle className="w-5 h-5" />,
        bgColor: 'bg-gray-100',
        textColor: 'text-gray-800',
        borderColor: 'border-gray-300',
      },
      NOT_SENT: {
        label: t('pos.kitchen.notSent', 'Waiting'),
        icon: <RefreshCw className="w-5 h-5 animate-spin" />,
        bgColor: 'bg-gray-100',
        textColor: 'text-gray-600',
        borderColor: 'border-gray-300',
      },
    };
    return configs[status] || configs.NOT_SENT;
  };

  const handlePrintReceipt = () => {
    if (fullOrderData) {
      PrintReceipt(fullOrderData);
    } else {
      // Fallback: print the page if order data not loaded
      window.print();
    }
  };

  const handlePrintKitchenTicket = () => {
    // Open kitchen ticket in new window and print
    const ticketWindow = window.open('/pos/kitchen-ticket', '_blank');
    if (ticketWindow) {
      ticketWindow.onload = () => {
        ticketWindow.print();
      };
    }
  };

  const handleStartNewOrder = () => {
    resetPOS();
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-green-50 to-emerald-50 flex items-center justify-center p-8">
      <div className="max-w-2xl w-full space-y-8">
        {/* Success Icon */}
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-32 h-32 bg-green-500 rounded-full mb-6 animate-bounce">
            <CheckCircle className="w-20 h-20 text-white" />
          </div>
          <h1 className="text-5xl font-bold text-gray-900 mb-4">
            {t('pos.confirmation.success', 'Order Placed Successfully!')}
          </h1>
          <p className="text-2xl text-gray-600">
            {t('pos.confirmation.thankYou', 'Thank you for your order')}
          </p>
        </div>

        {/* Order Details Card */}
        <div className="bg-white rounded-2xl shadow-xl p-8 border-2 border-gray-200">
          {/* Order Number */}
          <div className="text-center mb-8">
            <p className="text-sm text-gray-600 mb-2">{t('pos.confirmation.orderNumber', 'Order Number')}</p>
            <p className="text-6xl font-bold text-gray-900 tracking-wider">
              {currentOrder.orderNumber || t('pos.confirmation.processing', 'Processing...')}
            </p>
          </div>

          {/* Customer Info */}
          <div className="border-t-2 border-gray-200 pt-6 mb-6">
            <h3 className="text-lg font-bold text-gray-900 mb-4">{t('pos.details.customerInfo', 'Customer Information')}</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-sm text-gray-600">{t('pos.confirmation.name', 'Name')}</p>
                <p className="font-semibold text-gray-900">{customer.name}</p>
              </div>
              {customer.phone && (
                <div>
                  <p className="text-sm text-gray-600">{t('pos.confirmation.phone', 'Phone')}</p>
                  <p className="font-semibold text-gray-900">{customer.phone}</p>
                </div>
              )}
              {customer.tableNumber && (
                <div>
                  <p className="text-sm text-gray-600">{t('pos.details.tableNumber', 'Table Number')}</p>
                  <p className="font-semibold text-gray-900">{customer.tableNumber}</p>
                </div>
              )}
              {customer.address && (
                <div className="col-span-2">
                  <p className="text-sm text-gray-600">{t('pos.details.deliveryAddress', 'Delivery Address')}</p>
                  <p className="font-semibold text-gray-900">
                    {customer.address.street}, {customer.address.city} {customer.address.zipCode}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Order Summary */}
          <div className="border-t-2 border-gray-200 pt-6">
            <h3 className="text-lg font-bold text-gray-900 mb-4">{t('pos.cart.orderSummary', 'Order Summary')}</h3>
            <div className="space-y-3">
              <div className="flex justify-between">
                <span className="text-gray-700">{t('pos.cart.orderType', 'Order Type')}</span>
                <span className="font-semibold text-gray-900">{currentOrder.type}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-700">{t('pos.confirmation.totalAmount', 'Total Amount')}</span>
                <span className="text-2xl font-bold text-gray-900">
                  {currentOrder.total?.toFixed(2) || '0.00'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-700">{t('pos.confirmation.paymentMethod', 'Payment Method')}</span>
                <span className="font-semibold text-gray-900">
                  {usePOSStore.getState().payment.method}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="grid grid-cols-2 gap-4">
          <TouchButton
            variant="outline"
            size="xl"
            fullWidth
            onClick={handlePrintKitchenTicket}
            icon={<Printer className="w-6 h-6" />}
          >
            {t('pos.confirmation.printKitchen', 'Print Kitchen Ticket')}
          </TouchButton>

          <TouchButton
            variant="outline"
            size="xl"
            fullWidth
            onClick={handlePrintReceipt}
            icon={<Printer className="w-6 h-6" />}
          >
            {t('pos.confirmation.printReceipt', 'Print Receipt')}
          </TouchButton>
        </div>

        <TouchButton
          variant="success"
          size="xl"
          fullWidth
          onClick={handleStartNewOrder}
          icon={<Home className="w-6 h-6" />}
        >
          {t('pos.confirmation.startNew', 'Start New Order')}
        </TouchButton>

        {/* Kitchen Status */}
        {kitchenStatus.status && (
          <div className="bg-white rounded-xl shadow-lg p-6 border-2 border-gray-200">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <ChefHat className="w-8 h-8 text-gray-600" />
                <div>
                  <p className="text-sm text-gray-600">{t('pos.kitchen.status', 'Kitchen Status')}</p>
                  <p className="text-lg font-semibold text-gray-900">
                    {kitchenStatus.assignedChef
                      ? t('pos.kitchen.preparedBy', 'Being prepared by {{chef}}', { chef: kitchenStatus.assignedChef })
                      : t('pos.kitchen.inQueue', 'In kitchen queue')}
                  </p>
                </div>
              </div>

              {/* Status Badge */}
              {(() => {
                const config = getKitchenStatusConfig(kitchenStatus.status);
                return (
                  <div className={cn(
                    'flex items-center gap-2 px-4 py-2 rounded-full border-2',
                    config.bgColor,
                    config.textColor,
                    config.borderColor
                  )}>
                    {config.icon}
                    <span className="font-bold text-lg">{config.label}</span>
                  </div>
                );
              })()}
            </div>

            {/* Estimated Time */}
            {kitchenStatus.estimatedMinutes && kitchenStatus.status !== 'READY' && (
              <div className="mt-4 pt-4 border-t border-gray-200 flex items-center gap-2 text-gray-600">
                <Clock className="w-5 h-5" />
                <span>
                  {t('pos.kitchen.estimatedTime', 'Estimated time: {{minutes}} minutes', {
                    minutes: kitchenStatus.estimatedMinutes,
                  })}
                </span>
              </div>
            )}
          </div>
        )}

        {/* Status Message */}
        <div className="text-center">
          <p className="text-lg text-gray-600">
            {kitchenStatus.status === 'READY'
              ? t('pos.confirmation.readyForPickup', 'Order is ready for pickup!')
              : t('pos.confirmation.sentToKitchen', 'The order has been sent to the kitchen')}
          </p>
        </div>
      </div>
    </div>
  );
};

export default OrderConfirmationScreen;
