import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { XCircle, AlertTriangle } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';

/**
 * VoidOrderDialog - Void entire order confirmation
 * Requires reason selection and manager approval
 */
const VoidOrderDialog = ({
  open,
  onOpenChange,
  order,
  onVoidConfirm,
  onCancel,
  loading = false,
}) => {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [customReason, setCustomReason] = useState('');

  const voidReasons = [
    { id: 'CUSTOMER_REQUEST', label: t('pos.void.customerRequest', 'Customer Request') },
    { id: 'ORDER_ERROR', label: t('pos.void.orderError', 'Order Error') },
    { id: 'ITEM_UNAVAILABLE', label: t('pos.void.itemUnavailable', 'Item Unavailable') },
    { id: 'DUPLICATE_ORDER', label: t('pos.void.duplicateOrder', 'Duplicate Order') },
    { id: 'PAYMENT_ISSUE', label: t('pos.void.paymentIssue', 'Payment Issue') },
    { id: 'OTHER', label: t('pos.void.other', 'Other') },
  ];

  const handleConfirm = () => {
    const finalReason = reason === 'OTHER' ? customReason : reason;
    if (!finalReason) return;

    onVoidConfirm({
      orderId: order?.id,
      reason: finalReason,
      voidedBy: localStorage.getItem('userName') || 'POS User',
    });
  };

  const isValid = reason && (reason !== 'OTHER' || customReason.trim().length > 0);

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
        variant="danger"
        size="large"
        fullWidth
        onClick={handleConfirm}
        disabled={!isValid || loading}
        loading={loading}
      >
        {t('pos.void.confirmVoid', 'Void Order')}
      </TouchButton>
    </div>
  );

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={
        <div className="flex items-center gap-3">
          <XCircle className="w-8 h-8 text-red-600" />
          {t('pos.void.title', 'Void Order')}
        </div>
      }
      size="medium"
      footer={footer}
    >
      <div className="space-y-6">
        {/* Warning Banner */}
        <div className="bg-red-50 border-2 border-red-200 rounded-xl p-4 flex items-start gap-3">
          <AlertTriangle className="w-6 h-6 text-red-600 flex-shrink-0 mt-0.5" />
          <div>
            <h4 className="font-semibold text-red-800 mb-1">
              {t('pos.void.warningTitle', 'This action cannot be undone')}
            </h4>
            <p className="text-sm text-red-700">
              {t('pos.void.warningDesc', 'Voiding this order will cancel all payments and mark the order as void. The order cannot be recovered.')}
            </p>
          </div>
        </div>

        {/* Order Summary */}
        {order && (
          <div className="bg-gray-50 rounded-xl p-4">
            <h4 className="font-semibold text-gray-900 mb-3">
              {t('pos.void.orderDetails', 'Order Details')}
            </h4>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600">{t('pos.orders.orderNumber', 'Order #')}</span>
                <span className="font-medium text-gray-900">{order.orderNumber}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">{t('pos.orders.type', 'Type')}</span>
                <span className="font-medium text-gray-900">{order.type}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">{t('pos.orders.items', 'Items')}</span>
                <span className="font-medium text-gray-900">{order.items?.length || 0}</span>
              </div>
              <div className="flex justify-between pt-2 border-t border-gray-200">
                <span className="text-gray-600">{t('pos.cart.total', 'Total')}</span>
                <span className="font-bold text-gray-900">${(order.total || 0).toFixed(2)}</span>
              </div>
            </div>
          </div>
        )}

        {/* Reason Selection */}
        <div>
          <h4 className="font-semibold text-gray-900 mb-3">
            {t('pos.void.selectReason', 'Select Reason for Void')}
          </h4>
          <div className="grid grid-cols-2 gap-2">
            {voidReasons.map((r) => (
              <button
                key={r.id}
                onClick={() => setReason(r.id)}
                className={cn(
                  'p-4 rounded-xl border-2 text-left transition-all',
                  reason === r.id
                    ? 'bg-red-100 border-red-500 text-red-800'
                    : 'bg-white border-gray-200 hover:border-red-300'
                )}
              >
                <span className="font-medium">{r.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Custom Reason Input */}
        {reason === 'OTHER' && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              {t('pos.void.specifyReason', 'Please specify the reason')}
            </label>
            <textarea
              value={customReason}
              onChange={(e) => setCustomReason(e.target.value)}
              placeholder={t('pos.void.enterReason', 'Enter reason for voiding this order...')}
              className={cn(
                'w-full p-4 rounded-xl border-2 border-gray-200',
                'focus:outline-none focus:border-red-500 focus:ring-2 focus:ring-red-200',
                'min-h-[100px] resize-none'
              )}
            />
          </div>
        )}
      </div>
    </POSModal>
  );
};

export default VoidOrderDialog;
