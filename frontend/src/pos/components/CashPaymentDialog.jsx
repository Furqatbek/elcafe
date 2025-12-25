import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Banknote, CheckCircle } from 'lucide-react';
import POSModal from './POSModal';
import NumericKeypad from './NumericKeypad';
import TouchButton from './TouchButton';

/**
 * CashPaymentDialog - Cash payment processing with numeric keypad
 * Shows quick amount buttons, change calculation
 */
const CashPaymentDialog = ({
  open,
  onOpenChange,
  amountDue,
  onPaymentComplete,
  onCancel,
  loading = false,
}) => {
  const { t } = useTranslation();
  const [cashAmount, setCashAmount] = useState('');

  // Reset when dialog opens
  useEffect(() => {
    if (open) {
      setCashAmount('');
    }
  }, [open]);

  const changeDue = parseFloat(cashAmount || '0') - amountDue;
  const isValidAmount = cashAmount && changeDue >= 0;

  // Generate quick cash amounts with round-up denominations
  const generateQuickAmounts = (amount) => {
    const amounts = new Set();

    // Add exact amount first
    amounts.add(Math.ceil(amount));

    // Round up to common denominations
    const denominations = [1000, 5000, 10000, 20000, 50000, 100000, 200000, 500000];

    for (const denom of denominations) {
      const rounded = Math.ceil(amount / denom) * denom;
      if (rounded >= amount) {
        amounts.add(rounded);
      }
    }

    // Convert to array, filter valid amounts, sort, and take first 6
    return Array.from(amounts)
      .filter(v => v >= amount)
      .sort((a, b) => a - b)
      .slice(0, 6);
  };

  const quickAmounts = generateQuickAmounts(amountDue);

  const handleComplete = () => {
    if (!isValidAmount) return;

    onPaymentComplete({
      method: 'CASH',
      amount: amountDue,
      amountTendered: parseFloat(cashAmount),
      changeDue: changeDue,
    });
  };

  const footer = (
    <div className="flex gap-3">
      <TouchButton
        variant="secondary"
        size="large"
        onClick={onCancel}
        disabled={loading}
      >
        {t('common.buttons.cancel', 'Cancel')}
      </TouchButton>
      <TouchButton
        variant="success"
        size="large"
        fullWidth
        onClick={handleComplete}
        disabled={!isValidAmount || loading}
        loading={loading}
      >
        {t('pos.payment.completePayment', 'Complete Payment')}
      </TouchButton>
    </div>
  );

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={
        <div className="flex items-center gap-3">
          <Banknote className="w-8 h-8 text-green-600" />
          {t('pos.payment.cashPayment', 'Cash Payment')}
        </div>
      }
      size="medium"
      footer={footer}
    >
      <div className="space-y-6">
        {/* Amount Due */}
        <div className="bg-gradient-to-r from-green-600 to-green-700 rounded-xl p-6 text-white text-center">
          <p className="text-lg opacity-90 mb-1">{t('pos.payment.amountDue', 'Amount Due')}</p>
          <p className="text-5xl font-bold">{amountDue.toFixed(2)}</p>
        </div>

        {/* Quick Amount Buttons */}
        <div>
          <p className="text-sm font-medium text-gray-600 mb-2">
            {t('pos.payment.quickAmounts', 'Quick Amounts')}
          </p>
          <div className="grid grid-cols-3 gap-2">
            {quickAmounts.map((amount, index) => {
              const displayAmount = Number.isInteger(amount) ? amount.toString() : amount.toFixed(2);
              return (
                <TouchButton
                  key={index}
                  variant={cashAmount === displayAmount ? 'primary' : 'outline'}
                  size="medium"
                  onClick={() => setCashAmount(displayAmount)}
                >
                  {amount.toLocaleString()}
                </TouchButton>
              );
            })}
          </div>
        </div>

        {/* Numeric Keypad */}
        <NumericKeypad
          value={cashAmount}
          onValueChange={setCashAmount}
          label={t('pos.payment.amountTendered', 'Amount Tendered')}
          placeholder="0.00"
          allowDecimal={true}
          maxLength={8}
        />

        {/* Change Due */}
        {isValidAmount && (
          <div className="bg-green-50 border-2 border-green-200 rounded-xl p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-green-700 mb-1">
                  {t('pos.payment.changeDue', 'Change Due')}
                </p>
                <p className="text-4xl font-bold text-green-900">
                  {changeDue.toFixed(2)}
                </p>
              </div>
              <CheckCircle className="w-14 h-14 text-green-500" />
            </div>
          </div>
        )}

        {/* Insufficient Amount Warning */}
        {cashAmount && changeDue < 0 && (
          <div className="bg-red-50 border-2 border-red-200 rounded-xl p-4 text-center">
            <p className="text-red-700 font-medium">
              {t('pos.payment.insufficientAmount', 'Insufficient amount. Need {{remaining}} more.', {
                remaining: Math.abs(changeDue).toFixed(2),
              })}
            </p>
          </div>
        )}
      </div>
    </POSModal>
  );
};

export default CashPaymentDialog;
