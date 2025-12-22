import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { CreditCard, CheckCircle, Loader2 } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';

/**
 * CardPaymentDialog - Credit/Debit card payment processing
 * Shows card terminal simulation, supports manual entry
 */
const CardPaymentDialog = ({
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
  const [cardType, setCardType] = useState(null); // CREDIT_CARD, DEBIT_CARD
  const [lastFour, setLastFour] = useState('');

  const totalAmount = amountDue + tipAmount;

  // Reset when dialog opens
  useEffect(() => {
    if (open) {
      setStatus('waiting');
      setCardType(null);
      setLastFour('');
    }
  }, [open]);

  const handleCardTypeSelect = (type) => {
    setCardType(type);
    // Simulate card reading
    setStatus('processing');

    // Simulate successful card read after 1.5 seconds
    setTimeout(() => {
      setLastFour(Math.floor(1000 + Math.random() * 9000).toString());
      setStatus('success');
    }, 1500);
  };

  const handleComplete = () => {
    onPaymentComplete({
      method: cardType || 'CREDIT_CARD',
      amount: totalAmount,
      tipAmount: tipAmount,
      transactionId: `TXN-${Date.now()}`,
      cardLastFour: lastFour,
    });
  };

  const footer = status === 'success' ? (
    <div className="flex gap-3">
      <TouchButton
        variant="secondary"
        size="large"
        onClick={() => {
          setStatus('waiting');
          setCardType(null);
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
          <CreditCard className="w-8 h-8 text-blue-600" />
          {t('pos.payment.cardPayment', 'Card Payment')}
        </div>
      }
      size="medium"
      footer={footer}
    >
      <div className="space-y-6">
        {/* Amount Display */}
        <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-xl p-6 text-white text-center">
          <p className="text-lg opacity-90 mb-1">{t('pos.payment.totalAmount', 'Total Amount')}</p>
          <p className="text-5xl font-bold">{totalAmount.toFixed(2)}</p>
          {tipAmount > 0 && (
            <p className="text-sm opacity-80 mt-2">
              {t('pos.payment.includesTip', 'Includes {{tip}} tip', { tip: tipAmount.toFixed(2) })}
            </p>
          )}
        </div>

        {/* Card Type Selection */}
        {status === 'waiting' && (
          <div>
            <p className="text-lg font-medium text-gray-700 mb-4 text-center">
              {t('pos.payment.selectCardType', 'Select Card Type')}
            </p>
            <div className="grid grid-cols-2 gap-4">
              <button
                onClick={() => handleCardTypeSelect('CREDIT_CARD')}
                className={cn(
                  'flex flex-col items-center justify-center',
                  'min-h-[160px] p-6',
                  'bg-white rounded-xl',
                  'border-4 border-blue-200',
                  'hover:border-blue-400 hover:bg-blue-50',
                  'active:scale-98 transition-all',
                  'focus:outline-none focus:ring-4 focus:ring-blue-200'
                )}
              >
                <CreditCard className="w-12 h-12 text-blue-600 mb-3" />
                <span className="text-xl font-bold text-gray-900">
                  {t('pos.payment.creditCard', 'Credit Card')}
                </span>
              </button>
              <button
                onClick={() => handleCardTypeSelect('DEBIT_CARD')}
                className={cn(
                  'flex flex-col items-center justify-center',
                  'min-h-[160px] p-6',
                  'bg-white rounded-xl',
                  'border-4 border-green-200',
                  'hover:border-green-400 hover:bg-green-50',
                  'active:scale-98 transition-all',
                  'focus:outline-none focus:ring-4 focus:ring-green-200'
                )}
              >
                <CreditCard className="w-12 h-12 text-green-600 mb-3" />
                <span className="text-xl font-bold text-gray-900">
                  {t('pos.payment.debitCard', 'Debit Card')}
                </span>
              </button>
            </div>
          </div>
        )}

        {/* Processing State */}
        {status === 'processing' && (
          <div className="text-center py-12">
            <Loader2 className="w-20 h-20 text-blue-600 animate-spin mx-auto mb-6" />
            <h3 className="text-2xl font-bold text-gray-900 mb-2">
              {t('pos.payment.readingCard', 'Reading Card...')}
            </h3>
            <p className="text-gray-600">
              {t('pos.payment.insertOrTap', 'Insert, tap, or swipe card')}
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
              {t('pos.payment.cardAccepted', 'Card Accepted')}
            </h3>
            <p className="text-gray-600 mb-4">
              {cardType === 'CREDIT_CARD' ? t('pos.payment.creditCard', 'Credit Card') : t('pos.payment.debitCard', 'Debit Card')}
              {' '} •••• {lastFour}
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
              <CreditCard className="w-16 h-16 text-red-600" />
            </div>
            <h3 className="text-2xl font-bold text-gray-900 mb-2">
              {t('pos.payment.cardError', 'Card Error')}
            </h3>
            <p className="text-gray-600 mb-4">
              {t('pos.payment.cardErrorDesc', 'Unable to read card. Please try again.')}
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

export default CardPaymentDialog;
