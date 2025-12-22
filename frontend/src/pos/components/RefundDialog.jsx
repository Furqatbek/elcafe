import React, { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { RotateCcw, Check, AlertCircle } from 'lucide-react';
import POSModal from './POSModal';
import NumericKeypad from './NumericKeypad';
import TouchButton from './TouchButton';

/**
 * RefundDialog - Process refunds (full, partial, or item-based)
 * Shows refund options and amount entry
 */
const RefundDialog = ({
  open,
  onOpenChange,
  order,
  onRefundConfirm,
  onCancel,
  loading = false,
}) => {
  const { t } = useTranslation();
  const [refundType, setRefundType] = useState('FULL'); // FULL, PARTIAL, ITEMS
  const [partialAmount, setPartialAmount] = useState('');
  const [selectedItems, setSelectedItems] = useState([]);
  const [reason, setReason] = useState('');

  const refundReasons = [
    { id: 'CUSTOMER_DISSATISFIED', label: t('pos.refund.customerDissatisfied', 'Customer Dissatisfied') },
    { id: 'WRONG_ORDER', label: t('pos.refund.wrongOrder', 'Wrong Order') },
    { id: 'QUALITY_ISSUE', label: t('pos.refund.qualityIssue', 'Quality Issue') },
    { id: 'LATE_DELIVERY', label: t('pos.refund.lateDelivery', 'Late Delivery') },
    { id: 'OTHER', label: t('pos.refund.other', 'Other') },
  ];

  const orderTotal = order?.total || 0;
  const orderItems = order?.items || [];

  // Calculate refund amount based on type
  const refundAmount = useMemo(() => {
    if (refundType === 'FULL') {
      return orderTotal;
    } else if (refundType === 'PARTIAL') {
      return parseFloat(partialAmount) || 0;
    } else if (refundType === 'ITEMS') {
      return selectedItems.reduce((sum, itemId) => {
        const item = orderItems.find(i => i.id === itemId);
        return sum + (item?.itemTotal || item?.total || 0);
      }, 0);
    }
    return 0;
  }, [refundType, partialAmount, selectedItems, orderTotal, orderItems]);

  const handleItemToggle = (itemId) => {
    setSelectedItems(prev =>
      prev.includes(itemId)
        ? prev.filter(id => id !== itemId)
        : [...prev, itemId]
    );
  };

  const handleConfirm = () => {
    if (!reason) return;

    onRefundConfirm({
      orderId: order?.id,
      type: refundType,
      amount: refundType === 'PARTIAL' ? parseFloat(partialAmount) : undefined,
      itemIds: refundType === 'ITEMS' ? selectedItems : undefined,
      reason: reason,
      processedBy: localStorage.getItem('userName') || 'POS User',
    });
  };

  const isValid = reason && (
    (refundType === 'FULL') ||
    (refundType === 'PARTIAL' && parseFloat(partialAmount) > 0 && parseFloat(partialAmount) <= orderTotal) ||
    (refundType === 'ITEMS' && selectedItems.length > 0)
  );

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
        variant="warning"
        size="large"
        fullWidth
        onClick={handleConfirm}
        disabled={!isValid || loading}
        loading={loading}
      >
        {t('pos.refund.processRefund', 'Process Refund')} (${refundAmount.toFixed(2)})
      </TouchButton>
    </div>
  );

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={
        <div className="flex items-center gap-3">
          <RotateCcw className="w-8 h-8 text-amber-600" />
          {t('pos.refund.title', 'Process Refund')}
        </div>
      }
      size="large"
      footer={footer}
    >
      <div className="grid grid-cols-2 gap-6">
        {/* Left Column - Refund Type & Amount */}
        <div className="space-y-6">
          {/* Refund Type Selection */}
          <div>
            <h4 className="font-semibold text-gray-900 mb-3">
              {t('pos.refund.selectType', 'Refund Type')}
            </h4>
            <div className="space-y-2">
              <button
                onClick={() => setRefundType('FULL')}
                className={cn(
                  'w-full p-4 rounded-xl border-2 text-left transition-all flex justify-between items-center',
                  refundType === 'FULL'
                    ? 'bg-amber-100 border-amber-500'
                    : 'bg-white border-gray-200 hover:border-amber-300'
                )}
              >
                <div>
                  <span className="font-medium text-gray-900">{t('pos.refund.fullRefund', 'Full Refund')}</span>
                  <p className="text-sm text-gray-600">{t('pos.refund.fullRefundDesc', 'Refund entire order amount')}</p>
                </div>
                <span className="font-bold text-gray-900">${orderTotal.toFixed(2)}</span>
              </button>

              <button
                onClick={() => setRefundType('PARTIAL')}
                className={cn(
                  'w-full p-4 rounded-xl border-2 text-left transition-all',
                  refundType === 'PARTIAL'
                    ? 'bg-amber-100 border-amber-500'
                    : 'bg-white border-gray-200 hover:border-amber-300'
                )}
              >
                <span className="font-medium text-gray-900">{t('pos.refund.partialRefund', 'Partial Refund')}</span>
                <p className="text-sm text-gray-600">{t('pos.refund.partialRefundDesc', 'Refund a specific amount')}</p>
              </button>

              <button
                onClick={() => setRefundType('ITEMS')}
                className={cn(
                  'w-full p-4 rounded-xl border-2 text-left transition-all',
                  refundType === 'ITEMS'
                    ? 'bg-amber-100 border-amber-500'
                    : 'bg-white border-gray-200 hover:border-amber-300'
                )}
              >
                <span className="font-medium text-gray-900">{t('pos.refund.itemRefund', 'Item Refund')}</span>
                <p className="text-sm text-gray-600">{t('pos.refund.itemRefundDesc', 'Refund specific items')}</p>
              </button>
            </div>
          </div>

          {/* Partial Amount Entry */}
          {refundType === 'PARTIAL' && (
            <div>
              <NumericKeypad
                value={partialAmount}
                onValueChange={setPartialAmount}
                label={t('pos.refund.enterAmount', 'Enter Refund Amount')}
                placeholder="0.00"
                allowDecimal={true}
                maxLength={8}
              />
              {parseFloat(partialAmount) > orderTotal && (
                <div className="mt-2 flex items-center gap-2 text-red-600 text-sm">
                  <AlertCircle className="w-4 h-4" />
                  {t('pos.refund.exceedsTotal', 'Cannot exceed order total of ${{total}}', { total: orderTotal.toFixed(2) })}
                </div>
              )}
            </div>
          )}

          {/* Item Selection */}
          {refundType === 'ITEMS' && (
            <div>
              <h4 className="font-semibold text-gray-900 mb-3">
                {t('pos.refund.selectItems', 'Select Items to Refund')}
              </h4>
              <div className="space-y-2 max-h-[300px] overflow-y-auto">
                {orderItems.map((item) => {
                  const isSelected = selectedItems.includes(item.id);
                  const itemTotal = item.itemTotal || item.total || 0;

                  return (
                    <button
                      key={item.id}
                      onClick={() => handleItemToggle(item.id)}
                      className={cn(
                        'w-full p-3 rounded-lg border-2 text-left transition-all flex justify-between items-center',
                        isSelected
                          ? 'bg-amber-100 border-amber-500'
                          : 'bg-white border-gray-200 hover:border-amber-300'
                      )}
                    >
                      <div className="flex items-center gap-3">
                        <div className={cn(
                          'w-6 h-6 rounded border-2 flex items-center justify-center',
                          isSelected ? 'bg-amber-500 border-amber-500' : 'border-gray-300'
                        )}>
                          {isSelected && <Check className="w-4 h-4 text-white" />}
                        </div>
                        <div>
                          <span className="font-medium text-gray-900">
                            {item.quantity}x {item.name || item.productName}
                          </span>
                        </div>
                      </div>
                      <span className="font-bold text-gray-900">${itemTotal.toFixed(2)}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Right Column - Reason & Summary */}
        <div className="space-y-6">
          {/* Reason Selection */}
          <div>
            <h4 className="font-semibold text-gray-900 mb-3">
              {t('pos.refund.selectReason', 'Reason for Refund')}
            </h4>
            <div className="space-y-2">
              {refundReasons.map((r) => (
                <button
                  key={r.id}
                  onClick={() => setReason(r.id)}
                  className={cn(
                    'w-full p-3 rounded-lg border-2 text-left transition-all',
                    reason === r.id
                      ? 'bg-amber-100 border-amber-500'
                      : 'bg-white border-gray-200 hover:border-amber-300'
                  )}
                >
                  <span className="font-medium">{r.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Refund Summary */}
          <div className="bg-amber-50 border-2 border-amber-200 rounded-xl p-4">
            <h4 className="font-semibold text-amber-900 mb-3">
              {t('pos.refund.summary', 'Refund Summary')}
            </h4>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-amber-800">{t('pos.orders.orderNumber', 'Order #')}</span>
                <span className="font-medium text-amber-900">{order?.orderNumber}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-amber-800">{t('pos.refund.originalTotal', 'Original Total')}</span>
                <span className="font-medium text-amber-900">${orderTotal.toFixed(2)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-amber-800">{t('pos.refund.refundType', 'Refund Type')}</span>
                <span className="font-medium text-amber-900">
                  {refundType === 'FULL' && t('pos.refund.full', 'Full')}
                  {refundType === 'PARTIAL' && t('pos.refund.partial', 'Partial')}
                  {refundType === 'ITEMS' && t('pos.refund.items', 'Items')}
                </span>
              </div>
              {refundType === 'ITEMS' && selectedItems.length > 0 && (
                <div className="flex justify-between">
                  <span className="text-amber-800">{t('pos.refund.itemsSelected', 'Items Selected')}</span>
                  <span className="font-medium text-amber-900">{selectedItems.length}</span>
                </div>
              )}
              <div className="flex justify-between pt-2 border-t border-amber-300">
                <span className="text-amber-800 font-semibold">{t('pos.refund.refundAmount', 'Refund Amount')}</span>
                <span className="font-bold text-amber-900 text-lg">${refundAmount.toFixed(2)}</span>
              </div>
            </div>
          </div>

          {/* Warning */}
          <div className="flex items-start gap-2 text-sm text-gray-600">
            <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
            <p>
              {t('pos.refund.warning', 'Refund will be processed to the original payment method. This action will be logged.')}
            </p>
          </div>
        </div>
      </div>
    </POSModal>
  );
};

export default RefundDialog;
