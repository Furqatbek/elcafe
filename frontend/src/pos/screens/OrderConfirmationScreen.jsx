import React from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle, Printer, Home } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';

/**
 * OrderConfirmationScreen - Success confirmation after payment
 * Order number display, print receipt, start new order
 */
const OrderConfirmationScreen = () => {
  const { t } = useTranslation();
  const { currentOrder, customer, resetPOS } = usePOSStore();

  const handlePrintReceipt = () => {
    // Open print dialog with receipt
    window.print();
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
                  ${currentOrder.total?.toFixed(2) || '0.00'}
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

        {/* Status Message */}
        <div className="text-center">
          <p className="text-lg text-gray-600">
            {t('pos.confirmation.sentToKitchen', 'The order has been sent to the kitchen')}
          </p>
        </div>
      </div>
    </div>
  );
};

export default OrderConfirmationScreen;
