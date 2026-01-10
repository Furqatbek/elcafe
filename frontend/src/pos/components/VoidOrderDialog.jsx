import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { XCircle, AlertTriangle } from 'lucide-react';
import POSModal from './POSModal';
import TouchButton from './TouchButton';

const VoidOrderDialog = ({
  open,
  onOpenChange,
  order,
  onVoidConfirm,
  onCancel,
  loading,
}) => {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [voidedBy, setVoidedBy] = useState('');

  const voidReasons = [
    { id: 'customer_request', label: t('pos.void.reasons.customerRequest', 'Customer Request') },
    { id: 'wrong_order', label: t('pos.void.reasons.wrongOrder', 'Wrong Order') },
    { id: 'duplicate', label: t('pos.void.reasons.duplicate', 'Duplicate Order') },
    { id: 'other', label: t('pos.void.reasons.other', 'Other') },
  ];

  const handleConfirm = () => {
    if (reason) {
      onVoidConfirm({
        reason,
        voidedBy: voidedBy || 'Staff',
      });
    }
  };

  return (
    <POSModal
      open={open}
      onOpenChange={onOpenChange}
      title={t('pos.void.title', 'Void Order')}
      icon={<XCircle className="w-6 h-6 text-red-600" />}
      size="small"
    >
      <div className="space-y-4">
        {/* Warning */}
        <div className="bg-red-50 border-2 border-red-200 rounded-lg p-4 flex items-start gap-3">
          <AlertTriangle className="w-6 h-6 text-red-600 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-red-800">
              {t('pos.void.warning', 'This action cannot be undone')}
            </p>
            <p className="text-sm text-red-700 mt-1">
              {t('pos.void.warningDesc', 'Voiding this order will cancel all items and remove it from reports.')}
            </p>
          </div>
        </div>

        {/* Order Info */}
        <div className="bg-gray-50 rounded-lg p-4">
          <div className="flex justify-between mb-2">
            <span className="text-gray-600">{t('pos.void.orderNumber', 'Order #')}</span>
            <span className="font-semibold">{order?.orderNumber || order?.id}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">{t('pos.void.orderTotal', 'Total')}</span>
            <span className="font-semibold">{(order?.total || order?.subtotal || 0).toFixed(2)}</span>
          </div>
        </div>

        {/* Void Reason */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            {t('pos.void.selectReason', 'Select Reason')} *
          </label>
          <div className="grid grid-cols-2 gap-2">
            {voidReasons.map(({ id, label }) => (
              <TouchButton
                key={id}
                variant={reason === id ? 'danger' : 'outline'}
                size="small"
                onClick={() => setReason(id)}
              >
                {label}
              </TouchButton>
            ))}
          </div>
        </div>

        {/* Voided By */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            {t('pos.void.voidedBy', 'Voided By')}
          </label>
          <input
            type="text"
            value={voidedBy}
            onChange={(e) => setVoidedBy(e.target.value)}
            placeholder={t('pos.void.enterName', 'Enter name')}
            className="w-full px-4 py-2 border-2 border-gray-300 rounded-lg focus:border-red-500 focus:outline-none"
          />
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
            variant="danger"
            size="large"
            fullWidth
            onClick={handleConfirm}
            disabled={!reason || loading}
            icon={<XCircle className="w-5 h-5" />}
          >
            {loading ? t('common.processing', 'Processing...') : t('pos.void.confirmVoid', 'Void Order')}
          </TouchButton>
        </div>
      </div>
    </POSModal>
  );
};

export default VoidOrderDialog;
