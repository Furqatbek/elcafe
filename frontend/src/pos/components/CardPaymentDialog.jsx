import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { CreditCard, CheckCircle } from 'lucide-react';
import POSModal from './POSModal';
import NumericKeypad from './NumericKeypad';
import TouchButton from './TouchButton';

/**
 * CardPaymentDialog - Card payment processing
 * Simple confirmation for card payments
 * Supports split payments when splitMode is true
 */
const CardPaymentDialog = ({
  open,
  onOpenChange,
  amountDue,
  onPaymentComplete,
  onCancel,
  loading = false,
  splitMode = false,
}) => {
  const { t } = useTranslation();
  const [confirmed, setConfirmed] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState('');
  const [editingAmount, setEditingAmount] = useState(false);

  // Reset when dialog opens
  useEffect(() => {
    if (open) {
      setConfirmed(false);
      setPaymentAmount(amountDue.toString());
      setEditingAmount(false);
    }
  }, [open, amountDue]);

  const effectivePaymentAmount = splitMode && paymentAmount
    ? parseFloat(paymentAmount)
    : amountDue;

  const isValidAmount = splitMode
    ? (paymentAmount && parseFloat(paymentAmount) > 0 && parseFloat(paymentAmount) <= amountDue)
    : true;

  // Quick split amounts (for split mode)
  const splitQuickAmounts = [
    Math.round(amountDue / 2),
    Math.round(amountDue / 3),
    Math.round(amountDue / 4),
  ].filter(a => a > 0);

  const handleConfirm = () => {
    if (!isValidAmount) return;
    setConfirmed(true);
  };

  const handleComplete = () => {
    onPaymentComplete({
      method: 'CARD',
      amount: effectivePaymentAmount,
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
          {splitMode
            ? t('pos.payment.splitCardPayment', 'Split Card Payment')
            : t('pos.payment.cardPayment', 'Card Payment')
          }
        </div>
      }
      size="medium"
      footer={footer}
    >
      <div className="space-y-6">
        {/* Amount Display */}
        {splitMode ? (
          <div className="space-y-3">
            <div className="bg-gray-100 rounded-xl p-4 text-center">
              <p className="text-sm text-gray-600 mb-1">{t('pos.payment.remainingBalance', 'Remaining Balance')}</p>
              <p className="text-2xl font-bold text-gray-900">{amountDue.toFixed(2)}</p>
            </div>

            {/* Payment Amount Input for Split Mode */}
            {editingAmount && !confirmed ? (
              <div className="bg-blue-50 border-2 border-blue-300 rounded-xl p-4">
                <NumericKeypad
                  value={paymentAmount}
                  onValueChange={(val) => {
                    const num = parseFloat(val || '0');
                    if (num <= amountDue) {
                      setPaymentAmount(val);
                    }
                  }}
                  label={t('pos.payment.payingAmount', 'Amount to Pay')}
                  placeholder="0.00"
                  allowDecimal={true}
                  maxLength={8}
                />
                <div className="flex gap-2 mt-3">
                  {splitQuickAmounts.map((amount, idx) => (
                    <TouchButton
                      key={idx}
                      variant="outline"
                      size="small"
                      onClick={() => setPaymentAmount(amount.toString())}
                    >
                      {amount.toLocaleString()}
                    </TouchButton>
                  ))}
                  <TouchButton
                    variant="outline"
                    size="small"
                    onClick={() => setPaymentAmount(amountDue.toString())}
                  >
                    {t('pos.payment.payFull', 'Full')}
                  </TouchButton>
                </div>
                <TouchButton
                  variant="primary"
                  size="small"
                  fullWidth
                  className="mt-3"
                  onClick={() => setEditingAmount(false)}
                  disabled={!isValidAmount}
                >
                  {t('common.buttons.confirm', 'Confirm')}
                </TouchButton>
              </div>
            ) : !confirmed ? (
              <div
                className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-xl p-6 text-white text-center cursor-pointer hover:from-blue-700 hover:to-blue-800 transition-all"
                onClick={() => setEditingAmount(true)}
              >
                <p className="text-lg opacity-90 mb-1">{t('pos.payment.payingNow', 'Paying Now')} ({t('pos.payment.tapToChange', 'tap to change')})</p>
                <p className="text-5xl font-bold">{effectivePaymentAmount.toFixed(2)}</p>
              </div>
            ) : (
              <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-xl p-6 text-white text-center">
                <p className="text-lg opacity-90 mb-1">{t('pos.payment.payingNow', 'Paying Now')}</p>
                <p className="text-5xl font-bold">{effectivePaymentAmount.toFixed(2)}</p>
              </div>
            )}
          </div>
        ) : (
          <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-xl p-6 text-white text-center">
            <p className="text-lg opacity-90 mb-1">{t('pos.payment.totalAmount', 'Total Amount')}</p>
            <p className="text-5xl font-bold">{amountDue.toFixed(2)}</p>
          </div>
        )}

        {/* Confirmation State */}
        {!confirmed && !editingAmount ? (
          <div className="text-center py-8">
            <button
              onClick={handleConfirm}
              disabled={!isValidAmount}
              className="flex flex-col items-center justify-center w-full min-h-[160px] p-6 bg-white rounded-xl border-4 border-blue-200 hover:border-blue-400 hover:bg-blue-50 active:scale-98 transition-all focus:outline-none focus:ring-4 focus:ring-blue-200 disabled:opacity-50 disabled:cursor-not-allowed"
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
        ) : confirmed ? (
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
        ) : null}
      </div>
    </POSModal>
  );
};

export default CardPaymentDialog;
