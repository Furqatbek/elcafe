import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { CreditCard, CheckCircle } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';

/**
 * CardPaymentDialog - Card payment processing
 * Simple confirmation for card payments
 */
const CardPaymentDialog = ({
  open,
  onOpenChange,
  amountDue,
  onPaymentComplete,
  onCancel,
  loading = false,
}) => {
  const { t } = useTranslation();
  const [confirmed, setConfirmed] = useState(false);

  // Reset when dialog opens
  useEffect(() => {
    if (open) {
      setConfirmed(false);
    }
  }, [open]);

  const handleConfirm = () => {
    setConfirmed(true);
  };

  const handleComplete = () => {
    onPaymentComplete({
      method: 'CARD',
      amount: amountDue,
      tipAmount: 0,
      transactionId: `TXN-${Date.now()}`,
    });
  };

  const footer = confirmed ? (
    <div className="flex gap-3">
      <TouchButton
        variant="secondary"
        size="large"
        onClick={() => setConfirmed(false)}
        disabled={loading}
      >
        {t('common.buttons.back', 'Back')}
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
      disabled={loading}
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
          <p className="text-5xl font-bold">{amountDue.toFixed(2)}</p>
        </div>

        {/* Confirmation State */}
        {!confirmed ? (
          <div className="text-center py-8">
            <button
              onClick={handleConfirm}
              className="flex flex-col items-center justify-center w-full min-h-[160px] p-6 bg-white rounded-xl border-4 border-blue-200 hover:border-blue-400 hover:bg-blue-50 active:scale-98 transition-all focus:outline-none focus:ring-4 focus:ring-blue-200"
            >
              <CreditCard className="w-16 h-16 text-blue-600 mb-4" />
              <span className="text-2xl font-bold text-gray-900">
                {t('pos.payment.card', 'Card')}
              </span>
              <span className="text-gray-600 mt-2">
                {t('pos.payment.tapToConfirm', 'Tap to confirm card payment')}
              </span>
            </button>
          </div>
        ) : (
          <div className="text-center py-8">
            <div className="w-24 h-24 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
              <CheckCircle className="w-16 h-16 text-green-600" />
            </div>
            <h3 className="text-2xl font-bold text-gray-900 mb-2">
              {t('pos.payment.cardReady', 'Card Payment Ready')}
            </h3>
            <p className="text-gray-600">
              {t('pos.payment.clickConfirm', 'Click confirm to complete the payment')}
            </p>
          </div>
        )}
      </div>
    </POSModal>
  );
};

export default CardPaymentDialog;
