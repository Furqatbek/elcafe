import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CreditCard, CheckCircle, Loader2 } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';

const CardPaymentDialog = ({
  open,
  onOpenChange,
  amountDue,
  onPaymentComplete,
  onCancel,
  loading,
  splitMode,
}) => {
  const { t } = useTranslation();
  const [cardType, setCardType] = useState('VISA');
  const [processing, setProcessing] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState(amountDue);

  React.useEffect(() => {
    if (open) {
      setPaymentAmount(amountDue);
      setProcessing(false);
    }
  }, [open, amountDue]);

  const cardTypes = [
    { id: 'VISA', label: 'Visa' },
    { id: 'MASTERCARD', label: 'Mastercard' },
    { id: 'AMEX', label: 'Amex' },
  ];

  const handleConfirm = async () => {
    setProcessing(true);

    // Simulate card processing delay
    await new Promise(resolve => setTimeout(resolve, 1500));

    onPaymentComplete({
      method: 'CARD',
      cardType: cardType,
      amount: splitMode ? paymentAmount : amountDue,
      amountTendered: splitMode ? paymentAmount : amountDue,
      transactionId: `TXN-${Date.now()}`,
    });
  };

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={t('pos.payment.cardPayment', 'Card Payment')}
      icon={<CreditCard className="w-6 h-6 text-blue-600" />}
      size="small"
    >
      <div className="space-y-4">
        {/* Amount */}
        <div className="bg-blue-50 rounded-lg p-4 text-center">
          <p className="text-sm text-blue-600 mb-1">{t('pos.payment.chargeAmount', 'Charge Amount')}</p>
          <p className="text-3xl font-bold text-blue-700">{amountDue.toFixed(2)}</p>
        </div>

        {/* Split Payment Amount */}
        {splitMode && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              {t('pos.payment.partialAmount', 'Partial Amount')}
            </label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              max={amountDue}
              value={paymentAmount}
              onChange={(e) => setPaymentAmount(parseFloat(e.target.value) || 0)}
              className="w-full px-4 py-3 text-xl font-bold text-center border-2 border-gray-300 rounded-lg focus:border-blue-500 focus:outline-none"
            />
          </div>
        )}

        {/* Card Type Selection */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            {t('pos.payment.cardType', 'Card Type')}
          </label>
          <div className="grid grid-cols-3 gap-2">
            {cardTypes.map(({ id, label }) => (
              <TouchButton
                key={id}
                variant={cardType === id ? 'primary' : 'outline'}
                size="medium"
                onClick={() => setCardType(id)}
              >
                {label}
              </TouchButton>
            ))}
          </div>
        </div>

        {/* Processing State */}
        {processing && (
          <div className="bg-yellow-50 rounded-lg p-4 text-center">
            <Loader2 className="w-8 h-8 animate-spin text-yellow-600 mx-auto mb-2" />
            <p className="text-yellow-700 font-medium">
              {t('pos.payment.processingCard', 'Processing card payment...')}
            </p>
            <p className="text-sm text-yellow-600">
              {t('pos.payment.waitingForTerminal', 'Waiting for terminal')}
            </p>
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-3 pt-4">
          <TouchButton
            variant="outline"
            size="large"
            fullWidth
            onClick={onCancel}
            disabled={loading || processing}
          >
            {t('common.buttons.cancel', 'Cancel')}
          </TouchButton>
          <TouchButton
            variant="primary"
            size="large"
            fullWidth
            onClick={handleConfirm}
            disabled={loading || processing}
            icon={processing ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle className="w-5 h-5" />}
          >
            {processing ? t('common.processing', 'Processing...') : t('pos.payment.chargeCard', 'Charge Card')}
          </TouchButton>
        </div>
      </div>
    </POSModal>
  );
};

export default CardPaymentDialog;
