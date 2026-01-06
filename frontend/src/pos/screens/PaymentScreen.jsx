import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import {
  ChevronLeft,
  CreditCard,
  Banknote,
  SplitSquareVertical,
  CheckCircle,
  AlertCircle,
  RotateCcw,
  XCircle,
  Percent,
} from 'lucide-react';
import TouchButton from '../components/TouchButton';
import CashPaymentDialog from '../components/CashPaymentDialog';
import CardPaymentDialog from '../components/CardPaymentDialog';
import VoidOrderDialog from '../components/VoidOrderDialog';
import RefundDialog from '../components/RefundDialog';
import usePOSStore from '../store/posStore';
import { posAPI } from '../../services/api';
import PrintReceipt from '../../components/PrintReceipt';

/**
 * PaymentScreen - Payment processing and tender collection
 * Multiple payment methods: Cash, Card, Mobile, Split
 * Supports tips, split payments, voids, and refunds
 */
const PaymentScreen = () => {
  const { t } = useTranslation();
  const {
    currentOrder,
    customer,
    payment,
    setPaymentMethod,
    setAmountTendered,
    setPaymentStatus,
    completeOrder,
    setCurrentScreen,
    fetchFloorPlan,
    setServiceFee,
  } = usePOSStore();

  // State
  const [processingPayment, setProcessingPayment] = useState(false);
  const [paymentError, setPaymentError] = useState(null);

  // Split payment state
  const [splitPaymentMode, setSplitPaymentMode] = useState(false);
  const [payments, setPayments] = useState([]);

  // Dialog states
  const [showCashDialog, setShowCashDialog] = useState(false);
  const [showCardDialog, setShowCardDialog] = useState(false);
  const [showVoidDialog, setShowVoidDialog] = useState(false);
  const [showRefundDialog, setShowRefundDialog] = useState(false);

  // Service fee state
  const [showServiceFeeInput, setShowServiceFeeInput] = useState(false);
  const [serviceFeePercentInput, setServiceFeePercentInput] = useState(
    currentOrder.serviceFeePercent || 0
  );

  // Calculate totals
  const subtotal = currentOrder.subtotal || 0;
  const tax = currentOrder.tax || 0;
  const deliveryFee = currentOrder.deliveryFee || 0;
  const serviceFee = currentOrder.serviceFee || 0;
  const entryFee = currentOrder.entryFee || 0;
  const grandTotal = subtotal + tax + deliveryFee + serviceFee + entryFee;

  // For split payments
  const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
  const remainingBalance = grandTotal - totalPaid;
  const isFullyPaid = remainingBalance <= 0.01;

  const paymentMethods = [
    {
      id: 'CASH',
      label: t('pos.payment.cash', 'Cash'),
      icon: <Banknote className="w-10 h-10" />,
      color: 'green',
      description: t('pos.payment.cashDesc', 'Accept cash payment'),
    },
    {
      id: 'CARD',
      label: t('pos.payment.card', 'Card'),
      icon: <CreditCard className="w-10 h-10" />,
      color: 'blue',
      description: t('pos.payment.cardDesc', 'Card payment'),
    },
  ];

  const handlePaymentMethodSelect = (method) => {
    setPaymentError(null);

    if (method === 'CASH') {
      setShowCashDialog(true);
    } else if (method === 'CARD') {
      setShowCardDialog(true);
    }
  };

  // Helper function to prepare and print receipt
  const printOrderReceipt = (orderNumber) => {
    const receiptData = {
      orderNumber: orderNumber || currentOrder.orderNumber,
      items: currentOrder.items.map(item => ({
        productName: item.name,
        unitPrice: item.basePrice + (item.modifiers?.reduce((sum, mod) => sum + mod.price, 0) || 0),
        quantity: item.quantity,
        totalPrice: item.itemTotal,
        variantName: item.modifiers?.map(m => m.name).join(', ') || null,
      })),
      subtotal: subtotal,
      tax: tax,
      deliveryFee: deliveryFee,
      serviceFee: serviceFee,
      serviceFeePercent: currentOrder.serviceFeePercent,
      entryFee: entryFee,
      total: grandTotal,
      diningTable: customer.tableNumber ? { tableNumber: customer.tableNumber } : null,
      customerNotes: currentOrder.notes || null,
    };

    PrintReceipt(receiptData);
  };

  const handlePaymentComplete = async (paymentData) => {
    setProcessingPayment(true);
    setPaymentError(null);

    try {
      // Get restaurant ID from localStorage
      const restaurantId = parseInt(localStorage.getItem('selectedRestaurantId')) || 1;

      // If we have an existing order ID, update items and process payment
      if (currentOrder.id && !String(currentOrder.id).startsWith('temp-')) {
        // First, sync any item changes to the backend
        // Get the current items from the backend to compare
        try {
          const orderResponse = await posAPI.getOrderById(currentOrder.id);
          const backendItems = orderResponse.data.data?.items || [];
          const backendItemIds = new Set(backendItems.map(item => item.id));

          // Add new items that don't exist in the backend
          for (const item of currentOrder.items) {
            if (!backendItemIds.has(item.id) && !String(item.id).includes('-')) {
              // This is a new item, add it to the order
              await posAPI.addItemToOrder(currentOrder.id, {
                productId: item.productId,
                quantity: item.quantity,
                price: item.basePrice,
                modifiers: item.modifiers?.map(mod => ({
                  name: mod.name,
                  price: mod.price,
                })) || [],
                notes: item.notes || '',
              });
            }
          }
        } catch (syncError) {
          console.error('Failed to sync items:', syncError);
          // Continue with payment even if sync fails
        }

        // Process payment via API
        const response = await posAPI.processPayment(currentOrder.id, {
          method: paymentData.method,
          amount: paymentData.amount,
          tipAmount: paymentData.tipAmount || 0,
          amountTendered: paymentData.amountTendered,
          transactionId: paymentData.transactionId,
        });

        if (splitPaymentMode) {
          // Add to payments list
          setPayments(prev => [...prev, {
            ...paymentData,
            id: response.data.data?.paymentId || Date.now(),
          }]);

          // Check if fully paid
          const newTotalPaid = totalPaid + paymentData.amount;
          if (newTotalPaid >= grandTotal - 0.01) {
            // Order fully paid - close order and release table
            await posAPI.closeOrder(currentOrder.id);
            setPaymentStatus('COMPLETED');
            printOrderReceipt(response.data.data?.orderNumber || currentOrder.orderNumber);
            completeOrder();
          }
        } else {
          // Single payment - close order and release table
          await posAPI.closeOrder(currentOrder.id);
          setPaymentStatus('COMPLETED');
          const orderNum = response.data.data?.orderNumber;
          printOrderReceipt(orderNum);
          completeOrder();
        }

        // Refresh floor plan to show updated table status (for DINE_IN orders)
        if (currentOrder.type === 'DINE_IN') {
          try {
            await fetchFloorPlan(restaurantId);
          } catch (e) {
            console.error('Failed to refresh floor plan:', e);
          }
        }
      } else {
        // Create new order with payment
        const orderData = {
          restaurantId,
          orderType: currentOrder.type,
          orderSource: 'WALK_IN',
          customerInfo: {
            name: customer.name,
            phone: customer.phone,
            email: customer.email || null,
          },
          items: currentOrder.items.map(item => ({
            productId: item.productId,
            quantity: item.quantity,
            price: item.basePrice,
            modifiers: item.modifiers?.map(mod => ({
              name: mod.name,
              price: mod.price,
            })) || [],
            notes: item.notes || null,
          })),
          orderNotes: currentOrder.notes || null,
          paymentMethod: paymentData.method,
          subtotal: currentOrder.subtotal,
          tax: currentOrder.tax,
          deliveryFee: currentOrder.deliveryFee,
          total: grandTotal,
          tipAmount: 0,
          amountTendered: paymentData.amountTendered || grandTotal,
          changeDue: paymentData.changeDue || 0,
        };

        // Add type-specific data
        if (currentOrder.type === 'DELIVERY' && customer.address) {
          orderData.deliveryInfo = {
            street: customer.address.street,
            city: customer.address.city,
            state: customer.address.state || '',
            zipCode: customer.address.zipCode,
            deliveryInstructions: customer.deliveryInstructions || null,
          };
        } else if (currentOrder.type === 'DINE_IN') {
          orderData.dineInInfo = {
            tableNumber: customer.tableNumber,
            tableIds: customer.tableIds || [],
            guestCount: customer.guestCount,
          };
        }

        const response = await posAPI.createOrder(orderData);

        // Refresh floor plan to show updated table status (for DINE_IN orders)
        if (currentOrder.type === 'DINE_IN') {
          try {
            await fetchFloorPlan(restaurantId);
          } catch (e) {
            console.error('Failed to refresh floor plan:', e);
          }
        }

        setPaymentStatus('COMPLETED');
        const orderNum = response.data.data.orderNumber;
        printOrderReceipt(orderNum);
        completeOrder();
      }

      // Close dialogs
      setShowCashDialog(false);
      setShowCardDialog(false);

    } catch (error) {
      console.error('Payment failed:', error);
      setPaymentStatus('FAILED');
      setPaymentError(error.response?.data?.message || error.message || t('pos.payment.errors.paymentFailed', 'Payment failed'));
    } finally {
      setProcessingPayment(false);
    }
  };

  const handleVoidOrder = async (voidData) => {
    setProcessingPayment(true);
    try {
      await posAPI.voidOrder(currentOrder.id, voidData.reason, voidData.voidedBy);
      setShowVoidDialog(false);
      // Reset and go back to start
      usePOSStore.getState().resetPOS();
    } catch (error) {
      console.error('Void failed:', error);
      setPaymentError(error.response?.data?.message || error.message);
    } finally {
      setProcessingPayment(false);
    }
  };

  const handleRefund = async (refundData) => {
    setProcessingPayment(true);
    try {
      await posAPI.processRefund(currentOrder.id, refundData);
      setShowRefundDialog(false);
      // Refresh order or reset
      usePOSStore.getState().resetPOS();
    } catch (error) {
      console.error('Refund failed:', error);
      setPaymentError(error.response?.data?.message || error.message);
    } finally {
      setProcessingPayment(false);
    }
  };

  const toggleSplitPayment = () => {
    setSplitPaymentMode(!splitPaymentMode);
    setPayments([]);
  };

  const handleApplyServiceFee = async () => {
    const percent = parseFloat(serviceFeePercentInput) || 0;
    if (percent >= 0 && percent <= 100) {
      // Update local state
      setServiceFee(percent);
      setShowServiceFeeInput(false);

      // If order exists in backend, save to database
      const orderId = currentOrder.id;
      if (orderId && !String(orderId).startsWith('temp-')) {
        try {
          await posAPI.applyServiceFee(orderId, percent);
        } catch (error) {
          console.error('Failed to save service fee to backend:', error);
        }
      }
    }
  };

  const handleRemoveServiceFee = async () => {
    setServiceFeePercentInput(0);
    setServiceFee(0);
    setShowServiceFeeInput(false);

    // If order exists in backend, save to database
    const orderId = currentOrder.id;
    if (orderId && !String(orderId).startsWith('temp-')) {
      try {
        await posAPI.applyServiceFee(orderId, 0);
      } catch (error) {
        console.error('Failed to remove service fee from backend:', error);
      }
    }
  };

  const colorClasses = {
    green: {
      bg: 'bg-green-50',
      border: 'border-green-200',
      hover: 'hover:border-green-400 hover:bg-green-100',
      text: 'text-green-600',
      ring: 'focus:ring-green-200',
    },
    blue: {
      bg: 'bg-blue-50',
      border: 'border-blue-200',
      hover: 'hover:border-blue-400 hover:bg-blue-100',
      text: 'text-blue-600',
      ring: 'focus:ring-blue-200',
    },
    purple: {
      bg: 'bg-purple-50',
      border: 'border-purple-200',
      hover: 'hover:border-purple-400 hover:bg-purple-100',
      text: 'text-purple-600',
      ring: 'focus:ring-purple-200',
    },
  };

  const [showMobileSummary, setShowMobileSummary] = useState(false);

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-3 sm:px-6 py-3 sm:py-4 flex-shrink-0">
        <div className="flex items-center justify-between gap-2">
          <TouchButton
            variant="ghost"
            size="medium"
            onClick={() => setCurrentScreen('details')}
            icon={<ChevronLeft className="w-5 sm:w-6 h-5 sm:h-6" />}
            disabled={processingPayment}
            className="!px-2 sm:!px-4"
          >
            <span className="hidden sm:inline">{t('common.buttons.back', 'Back')}</span>
          </TouchButton>

          <div className="text-center flex-1">
            <h1 className="text-lg sm:text-2xl font-bold text-gray-900">{t('pos.payment.title', 'Payment')}</h1>
            <p className="text-xs sm:text-sm text-gray-600">
              {splitPaymentMode
                ? t('pos.payment.splitPaymentMode', 'Split Payment Mode')
                : t('pos.payment.selectMethod', 'Select payment method')
              }
            </p>
          </div>

          <div className="flex gap-2">
            <TouchButton
              variant={splitPaymentMode ? 'primary' : 'outline'}
              size="small"
              onClick={toggleSplitPayment}
              icon={<SplitSquareVertical className="w-4 sm:w-5 h-4 sm:h-5" />}
              className="!px-2 sm:!px-3"
            >
              <span className="hidden sm:inline">{t('pos.payment.split', 'Split')}</span>
            </TouchButton>
          </div>
        </div>
      </div>

      {/* Error Banner */}
      {paymentError && (
        <div className="bg-red-50 border-b-2 border-red-200 px-6 py-3 flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-600" />
          <span className="text-red-700">{paymentError}</span>
          <button
            onClick={() => setPaymentError(null)}
            className="ml-auto text-red-600 hover:text-red-800"
          >
            &times;
          </button>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-hidden flex flex-col lg:flex-row">
        {/* Payment Method Selection */}
        <div className="flex-1 overflow-y-auto p-3 sm:p-6 pb-32 lg:pb-6">
          <div className="max-w-4xl mx-auto space-y-4 sm:space-y-6">
            {/* Total Amount Due */}
            <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-xl sm:rounded-2xl p-4 sm:p-8 text-white">
              <p className="text-sm sm:text-xl mb-1 sm:mb-2 opacity-90">
                {splitPaymentMode
                  ? t('pos.payment.remainingBalance', 'Remaining Balance')
                  : t('pos.payment.amountDue', 'Amount Due')
                }
              </p>
              <p className="text-3xl sm:text-6xl font-bold">
                {splitPaymentMode ? remainingBalance.toFixed(2) : grandTotal.toFixed(2)}
              </p>
            </div>

            {/* Split Payment Progress */}
            {splitPaymentMode && payments.length > 0 && (
              <div className="bg-white rounded-lg sm:rounded-xl p-3 sm:p-4 border-2 border-gray-200">
                <h3 className="font-semibold text-gray-900 mb-2 sm:mb-3 text-sm sm:text-base">
                  {t('pos.payment.paymentsReceived', 'Payments Received')}
                </h3>
                <div className="space-y-2">
                  {payments.map((p, idx) => (
                    <div key={p.id || idx} className="flex justify-between items-center py-2 border-b border-gray-100 last:border-0 text-sm">
                      <div className="flex items-center gap-2">
                        <CheckCircle className="w-4 sm:w-5 h-4 sm:h-5 text-green-500" />
                        <span className="text-gray-700">{p.method}</span>
                      </div>
                      <span className="font-semibold text-gray-900">{p.amount.toFixed(2)}</span>
                    </div>
                  ))}
                </div>
                <div className="flex justify-between items-center mt-2 sm:mt-3 pt-2 sm:pt-3 border-t-2 border-gray-200 text-sm sm:text-base">
                  <span className="font-semibold text-gray-700">{t('pos.payment.totalPaid', 'Total Paid')}</span>
                  <span className="font-bold text-green-600">{totalPaid.toFixed(2)}</span>
                </div>
              </div>
            )}

            {/* Payment Methods */}
            {!isFullyPaid && (
              <div>
                <h2 className="text-lg sm:text-2xl font-bold text-gray-900 mb-3 sm:mb-4">
                  {t('pos.payment.chooseMethod', 'Choose Payment Method')}
                </h2>
                <div className="grid grid-cols-2 gap-2 sm:gap-4">
                  {paymentMethods.map(({ id, label, icon, color, description }) => {
                    const colors = colorClasses[color];

                    return (
                      <button
                        key={id}
                        onClick={() => handlePaymentMethodSelect(id)}
                        disabled={processingPayment}
                        className={cn(
                          'flex flex-col items-center justify-center',
                          'min-h-[120px] sm:min-h-[200px] p-3 sm:p-6',
                          'bg-white rounded-lg sm:rounded-xl',
                          'border-2 sm:border-4 transition-all duration-200',
                          colors.border,
                          colors.hover,
                          'active:scale-98',
                          'disabled:opacity-50 disabled:cursor-not-allowed',
                          'focus:outline-none focus:ring-4 sm:focus:ring-8',
                          colors.ring
                        )}
                      >
                        <div className={cn('mb-2 sm:mb-4', colors.text)}>
                          {React.cloneElement(icon, { className: 'w-8 h-8 sm:w-10 sm:h-10' })}
                        </div>
                        <h3 className="text-lg sm:text-2xl font-bold text-gray-900 mb-1 sm:mb-2">
                          {label}
                        </h3>
                        <p className="text-xs sm:text-sm text-gray-600 text-center hidden sm:block">
                          {description}
                        </p>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Fully Paid - Complete Order */}
            {isFullyPaid && splitPaymentMode && (
              <div className="text-center py-4 sm:py-8">
                <div className="w-16 sm:w-24 h-16 sm:h-24 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4 sm:mb-6">
                  <CheckCircle className="w-10 sm:w-16 h-10 sm:h-16 text-green-600" />
                </div>
                <h2 className="text-xl sm:text-3xl font-bold text-gray-900 mb-3 sm:mb-4">
                  {t('pos.payment.orderPaid', 'Order Fully Paid')}
                </h2>
                <TouchButton
                  variant="success"
                  size="large"
                  onClick={() => completeOrder()}
                >
                  {t('pos.payment.completeOrder', 'Complete Order')}
                </TouchButton>
              </div>
            )}
          </div>
        </div>

        {/* Mobile Bottom Summary Bar */}
        <div className="lg:hidden fixed bottom-0 left-0 right-0 bg-white border-t-2 border-gray-200 p-3 z-40">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-gray-600">{t('pos.cart.total', 'Total')}</p>
              <p className="text-xl font-bold text-gray-900">{grandTotal.toFixed(2)}</p>
            </div>
            <TouchButton
              variant="outline"
              size="small"
              onClick={() => setShowMobileSummary(true)}
            >
              {t('pos.cart.details', 'Details')}
            </TouchButton>
          </div>
        </div>

        {/* Mobile Summary Drawer */}
        {showMobileSummary && (
          <>
            <div
              className="lg:hidden fixed inset-0 bg-black bg-opacity-50 z-40"
              onClick={() => setShowMobileSummary(false)}
            />
            <div className="lg:hidden fixed bottom-0 left-0 right-0 bg-white z-50 rounded-t-2xl max-h-[70vh] overflow-y-auto">
              <div className="p-4 border-b sticky top-0 bg-white flex justify-between items-center">
                <h3 className="font-semibold text-lg">{t('pos.cart.orderSummary', 'Order Summary')}</h3>
                <button onClick={() => setShowMobileSummary(false)} className="p-2">
                  <XCircle className="w-5 h-5" />
                </button>
              </div>
              <div className="p-4 space-y-4">
                {/* Items Summary */}
                <div className="space-y-2">
                  {currentOrder.items.map(item => (
                    <div key={item.id} className="flex justify-between text-sm">
                      <span className="text-gray-700">{item.quantity}x {item.name}</span>
                      <span className="font-semibold">{item.itemTotal.toFixed(2)}</span>
                    </div>
                  ))}
                </div>
                {/* Totals */}
                <div className="border-t pt-3 space-y-2">
                  <div className="flex justify-between text-sm">
                    <span>{t('pos.cart.subtotal', 'Subtotal')}</span>
                    <span>{subtotal.toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span>{t('pos.cart.tax', 'Tax')}</span>
                    <span>{tax.toFixed(2)}</span>
                  </div>
                  {serviceFee > 0 && (
                    <div className="flex justify-between text-sm text-purple-700">
                      <span>{t('pos.payment.serviceFee', 'Service Fee')}</span>
                      <span>{serviceFee.toFixed(2)}</span>
                    </div>
                  )}
                  <div className="flex justify-between font-bold text-lg pt-2 border-t">
                    <span>{t('pos.cart.total', 'Total')}</span>
                    <span>{grandTotal.toFixed(2)}</span>
                  </div>
                </div>
              </div>
            </div>
          </>
        )}

        {/* Order Summary Sidebar - Desktop */}
        <div className="hidden lg:flex w-[280px] xl:w-[360px] bg-white border-l-2 border-gray-200 p-4 xl:p-6 flex-col">
          <h3 className="text-lg font-bold text-gray-900 mb-4">{t('pos.cart.orderSummary', 'Order Summary')}</h3>

          {/* Customer Info */}
          <div className="mb-4 p-4 bg-gray-50 rounded-lg">
            <p className="text-sm text-gray-600 mb-1">{t('pos.payment.customer', 'Customer')}</p>
            <p className="font-semibold text-gray-900">{customer.name || t('pos.payment.walkIn', 'Walk-in')}</p>
            {customer.phone && (
              <p className="text-sm text-gray-600">{customer.phone}</p>
            )}
          </div>

          {/* Items Summary */}
          <div className="flex-1 overflow-y-auto space-y-2 mb-4">
            {currentOrder.items.map(item => (
              <div key={item.id} className="flex justify-between text-sm">
                <span className="text-gray-700">
                  {item.quantity}x {item.name}
                </span>
                <span className="font-semibold text-gray-900">
                  {item.itemTotal.toFixed(2)}
                </span>
              </div>
            ))}
          </div>

          {/* Service Fee Button */}
          <div className="mb-4">
            {!showServiceFeeInput ? (
              <TouchButton
                variant={serviceFee > 0 ? 'secondary' : 'outline'}
                size="small"
                fullWidth
                onClick={() => setShowServiceFeeInput(true)}
                icon={<Percent className="w-4 h-4" />}
              >
                {serviceFee > 0
                  ? t('pos.payment.editServiceFee', 'Edit Service Fee ({{percent}}%)', { percent: currentOrder.serviceFeePercent })
                  : t('pos.payment.addServiceFee', 'Add Service Fee')
                }
              </TouchButton>
            ) : (
              <div className="bg-purple-50 border-2 border-purple-200 rounded-lg p-3 space-y-2">
                <label className="block text-sm font-semibold text-purple-800">
                  {t('pos.payment.serviceFeePercent', 'Service Fee %')}
                </label>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="0.5"
                    value={serviceFeePercentInput}
                    onChange={(e) => setServiceFeePercentInput(e.target.value)}
                    className="flex-1 px-3 py-2 border-2 border-purple-300 rounded-lg text-center font-semibold focus:border-purple-500 focus:outline-none"
                    placeholder="0"
                  />
                  <span className="flex items-center text-lg font-bold text-purple-700">%</span>
                </div>
                <div className="flex gap-2">
                  <TouchButton
                    variant="primary"
                    size="small"
                    fullWidth
                    onClick={handleApplyServiceFee}
                  >
                    {t('common.buttons.apply', 'Apply')}
                  </TouchButton>
                  {serviceFee > 0 && (
                    <TouchButton
                      variant="danger"
                      size="small"
                      onClick={handleRemoveServiceFee}
                    >
                      {t('common.buttons.remove', 'Remove')}
                    </TouchButton>
                  )}
                  <TouchButton
                    variant="ghost"
                    size="small"
                    onClick={() => setShowServiceFeeInput(false)}
                  >
                    {t('common.buttons.cancel', 'Cancel')}
                  </TouchButton>
                </div>
              </div>
            )}
          </div>

          {/* Totals */}
          <div className="border-t-2 border-gray-200 pt-4 space-y-2">
            <div className="flex justify-between text-gray-700">
              <span>{t('pos.cart.subtotal', 'Subtotal')}</span>
              <span>{subtotal.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-gray-700">
              <span>{t('pos.cart.tax', 'Tax')}</span>
              <span>{tax.toFixed(2)}</span>
            </div>
            {deliveryFee > 0 && (
              <div className="flex justify-between text-gray-700">
                <span>{t('pos.cart.deliveryFee', 'Delivery')}</span>
                <span>{deliveryFee.toFixed(2)}</span>
              </div>
            )}
            {serviceFee > 0 && (
              <div className="flex justify-between text-purple-700">
                <span>{t('pos.payment.serviceFee', 'Service Fee')} ({currentOrder.serviceFeePercent}%)</span>
                <span>{serviceFee.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between text-xl font-bold text-gray-900 pt-2 border-t-2 border-gray-200">
              <span>{t('pos.cart.total', 'Total')}</span>
              <span>{grandTotal.toFixed(2)}</span>
            </div>
          </div>

          {/* Void/Refund Buttons (only for existing orders) */}
          {currentOrder.id && !String(currentOrder.id).startsWith('temp-') && (
            <div className="mt-4 pt-4 border-t border-gray-200 space-y-2">
              <TouchButton
                variant="outline"
                size="small"
                fullWidth
                onClick={() => setShowRefundDialog(true)}
                icon={<RotateCcw className="w-4 h-4" />}
              >
                {t('pos.payment.refund', 'Refund')}
              </TouchButton>
              <TouchButton
                variant="danger"
                size="small"
                fullWidth
                onClick={() => setShowVoidDialog(true)}
                icon={<XCircle className="w-4 h-4" />}
              >
                {t('pos.payment.voidOrder', 'Void Order')}
              </TouchButton>
            </div>
          )}
        </div>
      </div>

      {/* Payment Dialogs */}
      <CashPaymentDialog
        open={showCashDialog}
        onOpenChange={setShowCashDialog}
        amountDue={splitPaymentMode ? remainingBalance : grandTotal}
        onPaymentComplete={handlePaymentComplete}
        onCancel={() => setShowCashDialog(false)}
        loading={processingPayment}
        splitMode={splitPaymentMode}
      />

      <CardPaymentDialog
        open={showCardDialog}
        onOpenChange={setShowCardDialog}
        amountDue={splitPaymentMode ? remainingBalance : grandTotal}
        onPaymentComplete={handlePaymentComplete}
        onCancel={() => setShowCardDialog(false)}
        loading={processingPayment}
        splitMode={splitPaymentMode}
      />

      <VoidOrderDialog
        open={showVoidDialog}
        onOpenChange={setShowVoidDialog}
        order={currentOrder}
        onVoidConfirm={handleVoidOrder}
        onCancel={() => setShowVoidDialog(false)}
        loading={processingPayment}
      />

      <RefundDialog
        open={showRefundDialog}
        onOpenChange={setShowRefundDialog}
        order={currentOrder}
        onRefundConfirm={handleRefund}
        onCancel={() => setShowRefundDialog(false)}
        loading={processingPayment}
      />
    </div>
  );
};

export default PaymentScreen;
