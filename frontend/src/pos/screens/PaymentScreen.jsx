import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import {
  ChevronLeft,
  CreditCard,
  Banknote,
  Smartphone,
  SplitSquareVertical,
  CheckCircle,
  AlertCircle,
  RotateCcw,
  XCircle,
} from 'lucide-react';
import TouchButton from '../components/TouchButton';
import TipSelectionComponent from '../components/TipSelectionComponent';
import CashPaymentDialog from '../components/CashPaymentDialog';
import CardPaymentDialog from '../components/CardPaymentDialog';
import MobilePaymentDialog from '../components/MobilePaymentDialog';
import VoidOrderDialog from '../components/VoidOrderDialog';
import RefundDialog from '../components/RefundDialog';
import usePOSStore from '../store/posStore';
import { posAPI } from '../../services/api';

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
  } = usePOSStore();

  // State
  const [tipAmount, setTipAmount] = useState(0);
  const [showTipSelection, setShowTipSelection] = useState(true);
  const [processingPayment, setProcessingPayment] = useState(false);
  const [paymentError, setPaymentError] = useState(null);

  // Split payment state
  const [splitPaymentMode, setSplitPaymentMode] = useState(false);
  const [payments, setPayments] = useState([]);

  // Dialog states
  const [showCashDialog, setShowCashDialog] = useState(false);
  const [showCardDialog, setShowCardDialog] = useState(false);
  const [showMobileDialog, setShowMobileDialog] = useState(false);
  const [showVoidDialog, setShowVoidDialog] = useState(false);
  const [showRefundDialog, setShowRefundDialog] = useState(false);

  // Calculate totals
  const subtotal = currentOrder.subtotal || 0;
  const tax = currentOrder.tax || 0;
  const deliveryFee = currentOrder.deliveryFee || 0;
  const totalBeforeTip = subtotal + tax + deliveryFee;
  const grandTotal = totalBeforeTip + tipAmount;

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
      description: t('pos.payment.cardDesc', 'Credit/Debit card'),
    },
    {
      id: 'MOBILE',
      label: t('pos.payment.mobile', 'Mobile Pay'),
      icon: <Smartphone className="w-10 h-10" />,
      color: 'purple',
      description: t('pos.payment.mobileDesc', 'Apple Pay, Google Pay'),
    },
  ];

  const handlePaymentMethodSelect = (method) => {
    setPaymentError(null);

    if (method === 'CASH') {
      setShowCashDialog(true);
    } else if (method === 'CARD') {
      setShowCardDialog(true);
    } else if (method === 'MOBILE') {
      setShowMobileDialog(true);
    }
  };

  const handlePaymentComplete = async (paymentData) => {
    setProcessingPayment(true);
    setPaymentError(null);

    try {
      // Get restaurant ID from localStorage
      const restaurantId = parseInt(localStorage.getItem('selectedRestaurantId')) || 1;

      // If we have an existing order ID, process payment separately
      if (currentOrder.id && !String(currentOrder.id).startsWith('temp-')) {
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
            // Order fully paid
            setPaymentStatus('COMPLETED');
            completeOrder();
          }
        } else {
          // Single payment - complete order
          setPaymentStatus('COMPLETED');
          usePOSStore.getState().currentOrder.orderNumber = response.data.data?.orderNumber;
          completeOrder();
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
          tipAmount: tipAmount,
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

        setPaymentStatus('COMPLETED');
        usePOSStore.getState().currentOrder.orderNumber = response.data.data.orderNumber;
        completeOrder();
      }

      // Close dialogs
      setShowCashDialog(false);
      setShowCardDialog(false);
      setShowMobileDialog(false);

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

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-6 py-4 flex-shrink-0">
        <div className="flex items-center justify-between">
          <TouchButton
            variant="ghost"
            size="medium"
            onClick={() => setCurrentScreen('details')}
            icon={<ChevronLeft className="w-6 h-6" />}
            disabled={processingPayment}
          >
            {t('common.buttons.back', 'Back')}
          </TouchButton>

          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-900">{t('pos.payment.title', 'Payment')}</h1>
            <p className="text-sm text-gray-600">
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
              icon={<SplitSquareVertical className="w-5 h-5" />}
            >
              {t('pos.payment.split', 'Split')}
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
      <div className="flex-1 overflow-hidden flex">
        {/* Payment Method Selection */}
        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-4xl mx-auto space-y-6">
            {/* Total Amount Due */}
            <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-2xl p-8 text-white">
              <p className="text-xl mb-2 opacity-90">
                {splitPaymentMode
                  ? t('pos.payment.remainingBalance', 'Remaining Balance')
                  : t('pos.payment.amountDue', 'Amount Due')
                }
              </p>
              <p className="text-6xl font-bold">
                ${splitPaymentMode ? remainingBalance.toFixed(2) : grandTotal.toFixed(2)}
              </p>
              {tipAmount > 0 && (
                <p className="text-sm opacity-80 mt-2">
                  {t('pos.payment.includesTip', 'Includes ${{tip}} tip', { tip: tipAmount.toFixed(2) })}
                </p>
              )}
            </div>

            {/* Split Payment Progress */}
            {splitPaymentMode && payments.length > 0 && (
              <div className="bg-white rounded-xl p-4 border-2 border-gray-200">
                <h3 className="font-semibold text-gray-900 mb-3">
                  {t('pos.payment.paymentsReceived', 'Payments Received')}
                </h3>
                <div className="space-y-2">
                  {payments.map((p, idx) => (
                    <div key={p.id || idx} className="flex justify-between items-center py-2 border-b border-gray-100 last:border-0">
                      <div className="flex items-center gap-2">
                        <CheckCircle className="w-5 h-5 text-green-500" />
                        <span className="text-gray-700">{p.method}</span>
                      </div>
                      <span className="font-semibold text-gray-900">${p.amount.toFixed(2)}</span>
                    </div>
                  ))}
                </div>
                <div className="flex justify-between items-center mt-3 pt-3 border-t-2 border-gray-200">
                  <span className="font-semibold text-gray-700">{t('pos.payment.totalPaid', 'Total Paid')}</span>
                  <span className="font-bold text-green-600">${totalPaid.toFixed(2)}</span>
                </div>
              </div>
            )}

            {/* Tip Selection */}
            {showTipSelection && !splitPaymentMode && (
              <div className="bg-white rounded-xl p-6 border-2 border-gray-200">
                <TipSelectionComponent
                  subtotal={totalBeforeTip}
                  selectedTip={tipAmount}
                  onTipChange={setTipAmount}
                  showKeypad={false}
                />
              </div>
            )}

            {/* Payment Methods */}
            {!isFullyPaid && (
              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">
                  {t('pos.payment.chooseMethod', 'Choose Payment Method')}
                </h2>
                <div className="grid grid-cols-3 gap-4">
                  {paymentMethods.map(({ id, label, icon, color, description }) => {
                    const colors = colorClasses[color];

                    return (
                      <button
                        key={id}
                        onClick={() => handlePaymentMethodSelect(id)}
                        disabled={processingPayment}
                        className={cn(
                          'flex flex-col items-center justify-center',
                          'min-h-[200px] p-6',
                          'bg-white rounded-xl',
                          'border-4 transition-all duration-200',
                          colors.border,
                          colors.hover,
                          'active:scale-98',
                          'disabled:opacity-50 disabled:cursor-not-allowed',
                          'focus:outline-none focus:ring-8',
                          colors.ring
                        )}
                      >
                        <div className={cn('mb-4', colors.text)}>
                          {icon}
                        </div>
                        <h3 className="text-2xl font-bold text-gray-900 mb-2">
                          {label}
                        </h3>
                        <p className="text-sm text-gray-600 text-center">
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
              <div className="text-center py-8">
                <div className="w-24 h-24 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
                  <CheckCircle className="w-16 h-16 text-green-600" />
                </div>
                <h2 className="text-3xl font-bold text-gray-900 mb-4">
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

        {/* Order Summary Sidebar */}
        <div className="w-[360px] bg-white border-l-2 border-gray-200 p-6 flex flex-col">
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
                  ${item.itemTotal.toFixed(2)}
                </span>
              </div>
            ))}
          </div>

          {/* Totals */}
          <div className="border-t-2 border-gray-200 pt-4 space-y-2">
            <div className="flex justify-between text-gray-700">
              <span>{t('pos.cart.subtotal', 'Subtotal')}</span>
              <span>${subtotal.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-gray-700">
              <span>{t('pos.cart.tax', 'Tax')}</span>
              <span>${tax.toFixed(2)}</span>
            </div>
            {deliveryFee > 0 && (
              <div className="flex justify-between text-gray-700">
                <span>{t('pos.cart.deliveryFee', 'Delivery')}</span>
                <span>${deliveryFee.toFixed(2)}</span>
              </div>
            )}
            {tipAmount > 0 && (
              <div className="flex justify-between text-green-600">
                <span>{t('pos.payment.tip', 'Tip')}</span>
                <span>${tipAmount.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between text-xl font-bold text-gray-900 pt-2 border-t-2 border-gray-200">
              <span>{t('pos.cart.total', 'Total')}</span>
              <span>${grandTotal.toFixed(2)}</span>
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
      />

      <CardPaymentDialog
        open={showCardDialog}
        onOpenChange={setShowCardDialog}
        amountDue={splitPaymentMode ? remainingBalance : totalBeforeTip}
        tipAmount={splitPaymentMode ? 0 : tipAmount}
        onPaymentComplete={handlePaymentComplete}
        onCancel={() => setShowCardDialog(false)}
        loading={processingPayment}
      />

      <MobilePaymentDialog
        open={showMobileDialog}
        onOpenChange={setShowMobileDialog}
        amountDue={splitPaymentMode ? remainingBalance : totalBeforeTip}
        tipAmount={splitPaymentMode ? 0 : tipAmount}
        onPaymentComplete={handlePaymentComplete}
        onCancel={() => setShowMobileDialog(false)}
        loading={processingPayment}
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
