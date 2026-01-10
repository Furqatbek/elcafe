import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { RotateCcw, AlertCircle } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';
import NumericKeypad from './NumericKeypad';

const RefundDialog = ({
  open,
  onOpenChange,
  order,
  onRefundConfirm,
  onCancel,
  loading,
}) => {
  const { t } = useTranslation();
  const [refundAmount, setRefundAmount] = useState('');
  const [reason, setReason] = useState('');
  const [refundType, setRefundType] = useState('FULL');

  const orderTotal = order?.total || order?.subtotal || 0;

  React.useEffect(() => {
    if (open) {
      setRefundAmount(orderTotal.toFixed(2));
      setRefundType('FULL');
      setReason('');
    }
  }, [open, orderTotal]);

  const refundReasons = [
    { id: 'customer_complaint', label: t('pos.refund.reasons.complaint', 'Customer Complaint') },
    { id: 'wrong_item', label: t('pos.refund.reasons.wrongItem', 'Wrong Item') },
    { id: 'quality_issue', label: t('pos.refund.reasons.quality', 'Quality Issue') },
    { id: 'other', label: t('pos.refund.reasons.other', 'Other') },
  ];

  const handleKeypadInput = (value) => {
    if (value === 'clear') {
      setRefundAmount('');
    } else if (value === 'backspace') {
      setRefundAmount(prev => prev.slice(0, -1));
    } else if (value === '.') {
      if (!refundAmount.includes('.')) {
        setRefundAmount(prev => prev + '.');
      }
    } else {
      setRefundAmount(prev => prev + value);
    }
  };

  const refundValue = parseFloat(refundAmount) || 0;
  const isValidAmount = refundValue > 0 && refundValue <= orderTotal;

  const handleConfirm = () => {
    if (isValidAmount && reason) {
      onRefundConfirm({
        amount: refundValue,
        reason,
        refundType,
        orderId: order?.id,
      });
    }
  };

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={t('pos.refund.title', 'Process Refund')}
      icon={<RotateCcw className="w-6 h-6 text-orange-600" />}
      size="medium"
    >
      <div className="space-y-4">
        {/* Order Info */}
        <div className="bg-gray-50 rounded-lg p-4">
          <div className="flex justify-between mb-2">
            <span className="text-gray-600">{t('pos.refund.orderNumber', 'Order #')}</span>
            <span className="font-semibold">{order?.orderNumber || order?.id}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">{t('pos.refund.orderTotal', 'Order Total')}</span>
            <span className="font-semibold">{orderTotal.toFixed(2)}</span>
          </div>
        </div>

        {/* Refund Type */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            {t('pos.refund.type', 'Refund Type')}
          </label>
          <div className="grid grid-cols-2 gap-2">
            <TouchButton
              variant={refundType === 'FULL' ? 'primary' : 'outline'}
              size="medium"
              onClick={() => {
                setRefundType('FULL');
                setRefundAmount(orderTotal.toFixed(2));
              }}
            >
              {t('pos.refund.fullRefund', 'Full Refund')}
            </TouchButton>
            <TouchButton
              variant={refundType === 'PARTIAL' ? 'primary' : 'outline'}
              size="medium"
              onClick={() => {
                setRefundType('PARTIAL');
                setRefundAmount('');
              }}
            >
              {t('pos.refund.partialRefund', 'Partial Refund')}
            </TouchButton>
          </div>
        </div>

        {/* Refund Amount */}
        {refundType === 'PARTIAL' && (
          <div className="bg-orange-50 rounded-lg p-4">
            <label className="block text-sm font-medium text-orange-700 mb-2">
              {t('pos.refund.amount', 'Refund Amount')}
            </label>
            <div className="text-3xl font-bold text-center text-orange-800 mb-3">
              {refundAmount || '0.00'}
            </div>
            <NumericKeypad onInput={handleKeypadInput} />
            {refundValue > orderTotal && (
              <div className="flex items-center gap-2 mt-2 text-red-600 text-sm">
                <AlertCircle className="w-4 h-4" />
                {t('pos.refund.exceedsTotal', 'Amount exceeds order total')}
              </div>
            )}
          </div>
        )}

        {/* Refund Reason */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            {t('pos.refund.selectReason', 'Select Reason')} *
          </label>
          <div className="grid grid-cols-2 gap-2">
            {refundReasons.map(({ id, label }) => (
              <TouchButton
                key={id}
                variant={reason === id ? 'warning' : 'outline'}
                size="small"
                onClick={() => setReason(id)}
              >
                {label}
              </TouchButton>
            ))}
          </div>
        </div>

        {/* Refund Summary */}
        <div className="bg-orange-100 rounded-lg p-4 text-center">
          <p className="text-sm text-orange-700 mb-1">{t('pos.refund.refundAmount', 'Refund Amount')}</p>
          <p className="text-3xl font-bold text-orange-800">
            {(refundType === 'FULL' ? orderTotal : refundValue).toFixed(2)}
          </p>
        </div>

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
            variant="warning"
            size="large"
            fullWidth
            onClick={handleConfirm}
            disabled={!isValidAmount || !reason || loading}
            icon={<RotateCcw className="w-5 h-5" />}
          >
            {loading ? t('common.processing', 'Processing...') : t('pos.refund.processRefund', 'Process Refund')}
          </TouchButton>
        </div>
      </div>
    </POSModal>
  );
};

export default RefundDialog;
