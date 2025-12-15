import React from 'react';
import { CheckCircle, Printer, Home } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';

/**
 * OrderConfirmationScreen - Success confirmation after payment
 * Order number display, print receipt, start new order
 */
const OrderConfirmationScreen = () => {
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
            Order Placed Successfully!
          </h1>
          <p className="text-2xl text-gray-600">
            Thank you for your order
          </p>
        </div>

        {/* Order Details Card */}
        <div className="bg-white rounded-2xl shadow-xl p-8 border-2 border-gray-200">
          {/* Order Number */}
          <div className="text-center mb-8">
            <p className="text-sm text-gray-600 mb-2">Order Number</p>
            <p className="text-6xl font-bold text-gray-900 tracking-wider">
              {currentOrder.orderNumber || 'Processing...'}
            </p>
          </div>

          {/* Customer Info */}
          <div className="border-t-2 border-gray-200 pt-6 mb-6">
            <h3 className="text-lg font-bold text-gray-900 mb-4">Customer Information</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-sm text-gray-600">Name</p>
                <p className="font-semibold text-gray-900">{customer.name}</p>
              </div>
              {customer.phone && (
                <div>
                  <p className="text-sm text-gray-600">Phone</p>
                  <p className="font-semibold text-gray-900">{customer.phone}</p>
                </div>
              )}
              {customer.tableNumber && (
                <div>
                  <p className="text-sm text-gray-600">Table Number</p>
                  <p className="font-semibold text-gray-900">{customer.tableNumber}</p>
                </div>
              )}
              {customer.address && (
                <div className="col-span-2">
                  <p className="text-sm text-gray-600">Delivery Address</p>
                  <p className="font-semibold text-gray-900">
                    {customer.address.street}, {customer.address.city} {customer.address.zipCode}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Order Summary */}
          <div className="border-t-2 border-gray-200 pt-6">
            <h3 className="text-lg font-bold text-gray-900 mb-4">Order Summary</h3>
            <div className="space-y-3">
              <div className="flex justify-between">
                <span className="text-gray-700">Order Type</span>
                <span className="font-semibold text-gray-900">{currentOrder.type}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-700">Total Amount</span>
                <span className="text-2xl font-bold text-gray-900">
                  ${currentOrder.total?.toFixed(2) || '0.00'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-700">Payment Method</span>
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
            Print Kitchen Ticket
          </TouchButton>

          <TouchButton
            variant="outline"
            size="xl"
            fullWidth
            onClick={handlePrintReceipt}
            icon={<Printer className="w-6 h-6" />}
          >
            Print Receipt
          </TouchButton>
        </div>

        <TouchButton
          variant="success"
          size="xl"
          fullWidth
          onClick={handleStartNewOrder}
          icon={<Home className="w-6 h-6" />}
        >
          Start New Order
        </TouchButton>

        {/* Status Message */}
        <div className="text-center">
          <p className="text-lg text-gray-600">
            The order has been sent to the kitchen
          </p>
        </div>
      </div>
    </div>
  );
};

export default OrderConfirmationScreen;
