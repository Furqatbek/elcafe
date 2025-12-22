import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Smartphone, CheckCircle, Loader2, Wallet } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';

/**
 * MobilePaymentDialog - Mobile payment processing (Apple Pay, Google Pay, etc.)
 * Shows NFC simulation, QR code option
 */
const MobilePaymentDialog = ({
  open,
  onOpenChange,
  amountDue,
  tipAmount = 0,
  onPaymentComplete,
  onCancel,
  loading = false,
}) => {
  const { t } = useTranslation();
  const [status, setStatus] = useState('waiting'); // waiting, processing, success, error
  const [paymentType, setPaymentType] = useState(null);

  const totalAmount = amountDue + tipAmount;

  // Reset when dialog opens
  useEffect(() => {
    if (open) {
      setStatus('waiting');
      setPaymentType(null);
    }
  }, [open]);

  const mobilePaymentTypes = [
    {
      id: 'APPLE_PAY',
      name: 'Apple Pay',
      icon: '🍎',
      color: 'gray',
    },
    {
      id: 'GOOGLE_PAY',
      name: 'Google Pay',
      icon: '🔵',
      color: 'blue',
    },
    {
      id: 'SAMSUNG_PAY',
      name: 'Samsung Pay',
      icon: '📱',
      color: 'purple',
    },
    {
      id: 'OTHER',
      name: t('pos.payment.otherWallet', 'Other Wallet'),
      icon: <Wallet className="w-8 h-8" />,
      color: 'green',
    },
  ];

  const handlePaymentTypeSelect = (type) => {
    setPaymentType(type);
    setStatus('processing');

    // Simulate successful NFC tap after 2 seconds
    setTimeout(() => {
      setStatus('success');
    }, 2000);
  };

  const handleComplete = () => {
    onPaymentComplete({
      method: 'MOBILE_PAYMENT',
      amount: totalAmount,
      tipAmount: tipAmount,
      transactionId: `MOB-${Date.now()}`,
      mobilePaymentType: paymentType,
    });
  };

  const footer = status === 'success' ? (
    <div className="flex gap-3">
      <TouchButton
        variant="secondary"
        size="large"
        onClick={() => {
          setStatus('waiting');
          setPaymentType(null);
        }}
        disabled={loading}
      >
        {t('pos.payment.tryAgain', 'Try Again')}
      </TouchButton>
      <TouchButton
        variant="success"
        size="large"
        fullWidth
        onClick={handleComplete}
        disabled={loading}
        loading={loading}
      >
        {t('pos.payment.confirmPayment', 'Confirm Payment')}
      </TouchButton>
    </div>
  ) : (
    <TouchButton
      variant="secondary"
      size="large"
      fullWidth
      onClick={onCancel}
      disabled={loading || status === 'processing'}
    >
      {t('common.buttons.cancel', 'Cancel')}
    </TouchButton>
  );

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={
        <div className="flex items-center gap-3">
          <Smartphone className="w-8 h-8 text-purple-600" />
          {t('pos.payment.mobilePayment', 'Mobile Payment')}
        </div>
      }
      size="medium"
      footer={footer}
    >
      <div className="space-y-6">
        {/* Amount Display */}
        <div className="bg-gradient-to-r from-purple-600 to-purple-700 rounded-xl p-6 text-white text-center">
          <p className="text-lg opacity-90 mb-1">{t('pos.payment.totalAmount', 'Total Amount')}</p>
          <p className="text-5xl font-bold">${totalAmount.toFixed(2)}</p>
          {tipAmount > 0 && (
            <p className="text-sm opacity-80 mt-2">
              {t('pos.payment.includesTip', 'Includes ${{tip}} tip', { tip: tipAmount.toFixed(2) })}
            </p>
          )}
        </div>

        {/* Payment Type Selection */}
        {status === 'waiting' && (
          <div>
            <p className="text-lg font-medium text-gray-700 mb-4 text-center">
              {t('pos.payment.selectWallet', 'Select Mobile Wallet')}
            </p>
            <div className="grid grid-cols-2 gap-4">
              {mobilePaymentTypes.map((type) => (
                <button
                  key={type.id}
                  onClick={() => handlePaymentTypeSelect(type.id)}
                  className={cn(
                    'flex flex-col items-center justify-center',
                    'min-h-[120px] p-4',
                    'bg-white rounded-xl',
                    'border-3 border-gray-200',
                    'hover:border-purple-400 hover:bg-purple-50',
                    'active:scale-98 transition-all',
                    'focus:outline-none focus:ring-4 focus:ring-purple-200'
                  )}
                >
                  <span className="text-4xl mb-2">
                    {typeof type.icon === 'string' ? type.icon : type.icon}
                  </span>
                  <span className="text-lg font-bold text-gray-900">
                    {type.name}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Processing State - NFC Animation */}
        {status === 'processing' && (
          <div className="text-center py-12">
            <div className="relative w-32 h-32 mx-auto mb-6">
              {/* Pulse rings */}
              <div className="absolute inset-0 bg-purple-200 rounded-full animate-ping opacity-25" />
              <div className="absolute inset-4 bg-purple-300 rounded-full animate-ping opacity-25" style={{ animationDelay: '0.2s' }} />
              <div className="absolute inset-8 bg-purple-400 rounded-full animate-ping opacity-25" style={{ animationDelay: '0.4s' }} />
              {/* Center icon */}
              <div className="absolute inset-0 flex items-center justify-center">
                <div className="w-20 h-20 bg-purple-600 rounded-full flex items-center justify-center">
                  <Smartphone className="w-10 h-10 text-white" />
                </div>
              </div>
            </div>
            <h3 className="text-2xl font-bold text-gray-900 mb-2">
              {t('pos.payment.waitingForTap', 'Waiting for Tap...')}
            </h3>
            <p className="text-gray-600">
              {t('pos.payment.holdNearTerminal', 'Hold device near payment terminal')}
            </p>
          </div>
        )}

        {/* Success State */}
        {status === 'success' && (
          <div className="text-center py-8">
            <div className="w-24 h-24 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
              <CheckCircle className="w-16 h-16 text-green-600" />
            </div>
            <h3 className="text-2xl font-bold text-gray-900 mb-2">
              {t('pos.payment.paymentReceived', 'Payment Received')}
            </h3>
            <p className="text-gray-600 mb-4">
              {mobilePaymentTypes.find(t => t.id === paymentType)?.name || 'Mobile Payment'}
            </p>
            <div className="bg-gray-50 rounded-lg p-4 inline-block">
              <p className="text-sm text-gray-600">
                {t('pos.payment.transactionReady', 'Ready to complete transaction')}
              </p>
            </div>
          </div>
        )}

        {/* Error State */}
        {status === 'error' && (
          <div className="text-center py-8">
            <div className="w-24 h-24 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-6">
              <Smartphone className="w-16 h-16 text-red-600" />
            </div>
            <h3 className="text-2xl font-bold text-gray-900 mb-2">
              {t('pos.payment.paymentFailed', 'Payment Failed')}
            </h3>
            <p className="text-gray-600 mb-4">
              {t('pos.payment.tapFailedDesc', 'Unable to complete payment. Please try again.')}
            </p>
            <TouchButton
              variant="primary"
              size="medium"
              onClick={() => setStatus('waiting')}
            >
              {t('pos.payment.tryAgain', 'Try Again')}
            </TouchButton>
          </div>
        )}
      </div>
    </POSModal>
  );
};

export default MobilePaymentDialog;
