import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Banknote, Calculator } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';
import NumericKeypad from './NumericKeypad';

const CashPaymentDialog = ({
  open,
  onOpenChange,
  amountDue,
  onPaymentComplete,
  onCancel,
  loading,
  splitMode,
}) => {
  const { t } = useTranslation();
  const [amountTendered, setAmountTendered] = useState('');

  useEffect(() => {
    if (open) {
      setAmountTendered(amountDue.toFixed(2));
    }
  }, [open, amountDue]);

  const tenderedValue = parseFloat(amountTendered) || 0;
  const changeDue = Math.max(0, tenderedValue - amountDue);
  const isValidAmount = tenderedValue >= amountDue;

  const quickAmounts = [
    Math.ceil(amountDue),
    Math.ceil(amountDue / 10) * 10,
    Math.ceil(amountDue / 50) * 50,
    Math.ceil(amountDue / 100) * 100,
  ].filter((v, i, a) => a.indexOf(v) === i && v >= amountDue).slice(0, 4);

  const handleKeypadInput = (value) => {
    if (value === 'clear') {
      setAmountTendered('');
    } else if (value === 'backspace') {
      setAmountTendered(prev => prev.slice(0, -1));
    } else if (value === '.') {
      if (!amountTendered.includes('.')) {
        setAmountTendered(prev => prev + '.');
      }
    } else {
      setAmountTendered(prev => prev + value);
    }
  };

  const handleConfirm = () => {
    if (isValidAmount) {
      onPaymentComplete({
        method: 'CASH',
        amount: splitMode ? tenderedValue : amountDue,
        amountTendered: tenderedValue,
        changeDue: changeDue,
      });
    }
  };

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={t('pos.payment.cashPayment', 'Cash Payment')}
      icon={<Banknote className="w-6 h-6 text-green-600" />}
      size="medium"
    >
      <div className="space-y-4">
        {/* Amount Due */}
        <div className="bg-blue-50 rounded-lg p-4 text-center">
          <p className="text-sm text-blue-600 mb-1">{t('pos.payment.amountDue', 'Amount Due')}</p>
          <p className="text-3xl font-bold text-blue-700">{amountDue.toFixed(2)}</p>
        </div>

        {/* Quick Amount Buttons */}
        <div className="grid grid-cols-4 gap-2">
          {quickAmounts.map((amount) => (
            <TouchButton
              key={amount}
              variant="outline"
              size="small"
              onClick={() => setAmountTendered(amount.toString())}
            >
              {amount.toFixed(0)}
            </TouchButton>
          ))}
        </div>

        {/* Amount Tendered Input */}
        <div className="bg-gray-50 rounded-lg p-4">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            {t('pos.payment.amountTendered', 'Amount Tendered')}
          </label>
          <div className="text-4xl font-bold text-center text-gray-900 mb-4">
            {amountTendered || '0.00'}
          </div>
          <NumericKeypad onInput={handleKeypadInput} />
        </div>

        {/* Change Due */}
        {isValidAmount && changeDue > 0 && (
          <div className="bg-green-50 rounded-lg p-4 text-center">
            <p className="text-sm text-green-600 mb-1">{t('pos.payment.changeDue', 'Change Due')}</p>
            <p className="text-3xl font-bold text-green-700">{changeDue.toFixed(2)}</p>
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-3 pt-4">
          <TouchButton
            variant="outline"
            size="large"
            fullWidth
            onClick={onCancel}
            disabled={loading}
          >
            {t('common.buttons.cancel', 'Cancel')}
          </TouchButton>
          <TouchButton
            variant="success"
            size="large"
            fullWidth
            onClick={handleConfirm}
            disabled={!isValidAmount || loading}
            icon={<Calculator className="w-5 h-5" />}
          >
            {loading ? t('common.processing', 'Processing...') : t('pos.payment.confirm', 'Confirm Payment')}
          </TouchButton>
        </div>
      </div>
    </POSModal>
  );
};

export default CashPaymentDialog;
