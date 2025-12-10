import React, { useState } from 'react';
import { cn } from '../../lib/utils';
import { ChevronLeft, CreditCard, Banknote, Smartphone, CheckCircle } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import NumericKeypad from '../components/NumericKeypad';
import usePOSStore from '../store/posStore';
import axios from 'axios';

/**
 * PaymentScreen - Payment processing and tender collection
 * Multiple payment methods: Cash, Card, Mobile, Split
 * Cash change calculator, payment confirmation
 */
const PaymentScreen = () => {
  const {
    currentOrder,
    customer,
    payment,
    setPaymentMethod,
    setAmountTendered,
    setPaymentStatus,
    completeOrder,
    setCurrentScreen,
    setLoading,
  } = usePOSStore();

  const [cashAmount, setCashAmount] = useState('');
  const [processingPayment, setProcessingPayment] = useState(false);

  const paymentMethods = [
    {
      id: 'CASH',
      label: 'Cash',
      icon: <Banknote className="w-10 h-10" />,
      color: 'green',
      description: 'Accept cash payment',
    },
    {
      id: 'CARD',
      label: 'Card',
      icon: <CreditCard className="w-10 h-10" />,
      color: 'blue',
      description: 'Credit/Debit card',
    },
    {
      id: 'MOBILE',
      label: 'Mobile Pay',
      icon: <Smartphone className="w-10 h-10" />,
      color: 'purple',
      description: 'Apple Pay, Google Pay',
    },
  ];

  const handlePaymentMethodSelect = (method) => {
    setPaymentMethod(method);

    // For card and mobile, go straight to processing
    if (method === 'CARD' || method === 'MOBILE') {
      handleProcessPayment(method);
    }
  };

  const handleCashPayment = () => {
    const amount = parseFloat(cashAmount);

    if (!amount || amount < currentOrder.total) {
      alert(`Amount must be at least $${currentOrder.total.toFixed(2)}`);
      return;
    }

    setAmountTendered(amount);
    handleProcessPayment('CASH', amount);
  };

  const handleProcessPayment = async (method, cashTendered = 0) => {
    setProcessingPayment(true);
    setPaymentStatus('PROCESSING');

    try {
      // Prepare order data
      const orderData = {
        type: currentOrder.type,
        customerId: customer.id || null,
        customerName: customer.name,
        customerPhone: customer.phone,
        customerEmail: customer.email || null,
        items: currentOrder.items.map(item => ({
          productId: item.productId,
          quantity: item.quantity,
          price: item.basePrice,
          modifiers: item.modifiers,
          notes: item.notes,
        })),
        subtotal: currentOrder.subtotal,
        tax: currentOrder.tax,
        deliveryFee: currentOrder.deliveryFee,
        total: currentOrder.total,
        notes: currentOrder.notes,
        paymentMethod: method,
        paymentStatus: 'PAID',
        // Type-specific data
        ...(currentOrder.type === 'DELIVERY' && {
          deliveryAddress: customer.address,
          deliveryInstructions: customer.deliveryInstructions,
        }),
        ...(currentOrder.type === 'DINE_IN' && {
          tableNumber: customer.tableNumber,
          guestCount: customer.guestCount,
        }),
      };

      // Submit order to backend
      const response = await axios.post('/api/v1/consumer/orders', orderData);

      setPaymentStatus('COMPLETED');

      // Update order number in store
      usePOSStore.getState().currentOrder.orderNumber = response.data.data.orderNumber;

      // Complete order (clears cart, moves to confirmation)
      completeOrder();

    } catch (error) {
      console.error('Payment failed:', error);
      setPaymentStatus('FAILED');
      alert('Payment failed. Please try again.');
    } finally {
      setProcessingPayment(false);
    }
  };

  const quickCashAmounts = [
    currentOrder.total, // Exact amount
    Math.ceil(currentOrder.total / 5) * 5, // Round up to nearest $5
    Math.ceil(currentOrder.total / 10) * 10, // Round up to nearest $10
    Math.ceil(currentOrder.total / 20) * 20, // Round up to nearest $20
  ];

  const changeDue = parseFloat(cashAmount) - currentOrder.total;

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
            Back
          </TouchButton>

          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-900">Payment</h1>
            <p className="text-sm text-gray-600">Select payment method</p>
          </div>

          <div className="w-[140px]" />
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-hidden flex">
        {/* Payment Method Selection */}
        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-4xl mx-auto space-y-6">
            {/* Total Amount Due */}
            <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-2xl p-8 text-white">
              <p className="text-xl mb-2 opacity-90">Amount Due</p>
              <p className="text-6xl font-bold">${currentOrder.total.toFixed(2)}</p>
            </div>

            {/* Payment Methods */}
            {!payment.method ? (
              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Choose Payment Method</h2>
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
            ) : payment.method === 'CASH' && payment.status === 'PENDING' ? (
              /* Cash Payment Interface */
              <div className="space-y-6">
                <div className="bg-white rounded-xl p-6 border-2 border-gray-200">
                  <h2 className="text-2xl font-bold text-gray-900 mb-4">Cash Payment</h2>

                  {/* Quick Amount Buttons */}
                  <div className="grid grid-cols-4 gap-3 mb-6">
                    {quickCashAmounts.map((amount, index) => (
                      <TouchButton
                        key={index}
                        variant="outline"
                        size="large"
                        onClick={() => setCashAmount(amount.toFixed(2))}
                      >
                        ${amount.toFixed(2)}
                      </TouchButton>
                    ))}
                  </div>

                  {/* Numeric Keypad */}
                  <NumericKeypad
                    value={cashAmount}
                    onValueChange={setCashAmount}
                    label="Amount Tendered"
                    placeholder="0.00"
                    allowDecimal={true}
                    maxLength={8}
                  />

                  {/* Change Due */}
                  {cashAmount && changeDue >= 0 && (
                    <div className="mt-6 bg-green-50 border-2 border-green-200 rounded-xl p-6">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm text-green-700 mb-1">Change Due</p>
                          <p className="text-5xl font-bold text-green-900">
                            ${changeDue.toFixed(2)}
                          </p>
                        </div>
                        <CheckCircle className="w-16 h-16 text-green-500" />
                      </div>
                    </div>
                  )}

                  {/* Complete Cash Payment */}
                  <div className="mt-6 flex gap-3">
                    <TouchButton
                      variant="secondary"
                      size="large"
                      onClick={() => setPaymentMethod(null)}
                      disabled={processingPayment}
                    >
                      Cancel
                    </TouchButton>
                    <TouchButton
                      variant="success"
                      size="large"
                      fullWidth
                      onClick={handleCashPayment}
                      disabled={!cashAmount || changeDue < 0 || processingPayment}
                      loading={processingPayment}
                    >
                      Complete Payment
                    </TouchButton>
                  </div>
                </div>
              </div>
            ) : (
              /* Processing Payment */
              <div className="flex items-center justify-center h-full">
                <div className="text-center">
                  <div className="w-24 h-24 border-8 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-6" />
                  <h2 className="text-3xl font-bold text-gray-900 mb-2">
                    Processing Payment...
                  </h2>
                  <p className="text-lg text-gray-600">
                    {payment.method === 'CARD' && 'Waiting for card...'}
                    {payment.method === 'MOBILE' && 'Waiting for mobile payment...'}
                    {payment.method === 'CASH' && 'Finalizing transaction...'}
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Order Summary Sidebar */}
        <div className="w-[360px] bg-white border-l-2 border-gray-200 p-6">
          <h3 className="text-lg font-bold text-gray-900 mb-4">Order Summary</h3>

          {/* Customer Info */}
          <div className="mb-6 p-4 bg-gray-50 rounded-lg">
            <p className="text-sm text-gray-600 mb-1">Customer</p>
            <p className="font-semibold text-gray-900">{customer.name}</p>
            {customer.phone && (
              <p className="text-sm text-gray-600">{customer.phone}</p>
            )}
          </div>

          {/* Items Summary */}
          <div className="space-y-2 mb-6">
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
              <span>Subtotal</span>
              <span>${currentOrder.subtotal.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-gray-700">
              <span>Tax</span>
              <span>${currentOrder.tax.toFixed(2)}</span>
            </div>
            {currentOrder.deliveryFee > 0 && (
              <div className="flex justify-between text-gray-700">
                <span>Delivery</span>
                <span>${currentOrder.deliveryFee.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between text-xl font-bold text-gray-900 pt-2 border-t-2 border-gray-200">
              <span>Total</span>
              <span>${currentOrder.total.toFixed(2)}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PaymentScreen;
