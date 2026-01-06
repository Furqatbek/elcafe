import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Tag, X, Check, Loader2 } from 'lucide-react';
import TouchButton from './TouchButton';
import usePOSStore from '../store/posStore';

/**
 * CouponInput - POS component for applying coupon codes
 * Shows coupon input field and applied discount
 */
const CouponInput = ({ restaurantId = 1 }) => {
  const { t } = useTranslation();
  const { currentOrder, applyCoupon, removeDiscount } = usePOSStore();
  const [couponInput, setCouponInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const { discount, couponCode, discountType } = currentOrder;

  const handleApplyCoupon = async () => {
    if (!couponInput.trim()) return;

    setIsLoading(true);
    setError(null);

    try {
      await applyCoupon(couponInput.trim().toUpperCase(), restaurantId);
      setCouponInput('');
    } catch (err) {
      setError(err.message || t('pos.coupon.invalidCoupon', 'Invalid coupon code'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleRemoveCoupon = () => {
    removeDiscount();
    setError(null);
  };

  // If a coupon is already applied
  if (couponCode && discountType === 'COUPON') {
    return (
      <div className="bg-green-50 border-2 border-green-200 rounded-lg p-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-green-100 rounded-full">
              <Check className="w-4 h-4 text-green-600" />
            </div>
            <div>
              <p className="text-sm font-medium text-green-800">
                {t('pos.coupon.applied', 'Coupon Applied')}
              </p>
              <p className="text-xs text-green-600">
                <code className="bg-green-100 px-1 rounded">{couponCode}</code>
                {' - '}{discount.toFixed(2)} {t('pos.coupon.off', 'off')}
              </p>
            </div>
          </div>
          <button
            onClick={handleRemoveCoupon}
            className="p-2 text-green-600 hover:text-green-800 hover:bg-green-100 rounded-full transition-colors"
            title={t('pos.coupon.remove', 'Remove coupon')}
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>
    );
  }

  // If manual discount is applied
  if (discount > 0 && discountType === 'MANUAL') {
    return (
      <div className="bg-blue-50 border-2 border-blue-200 rounded-lg p-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-blue-100 rounded-full">
              <Tag className="w-4 h-4 text-blue-600" />
            </div>
            <div>
              <p className="text-sm font-medium text-blue-800">
                {t('pos.coupon.manualDiscount', 'Manual Discount')}
              </p>
              <p className="text-xs text-blue-600">
                {discount.toFixed(2)} {t('pos.coupon.off', 'off')}
              </p>
            </div>
          </div>
          <button
            onClick={handleRemoveCoupon}
            className="p-2 text-blue-600 hover:text-blue-800 hover:bg-blue-100 rounded-full transition-colors"
            title={t('pos.coupon.remove', 'Remove discount')}
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>
    );
  }

  // Coupon input form
  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <div className="flex-1 relative">
          <div className="absolute left-3 top-1/2 -translate-y-1/2">
            <Tag className="w-4 h-4 text-gray-400" />
          </div>
          <input
            type="text"
            value={couponInput}
            onChange={(e) => {
              setCouponInput(e.target.value.toUpperCase());
              setError(null);
            }}
            onKeyPress={(e) => e.key === 'Enter' && handleApplyCoupon()}
            placeholder={t('pos.coupon.enterCode', 'Enter coupon code')}
            className={cn(
              'w-full pl-10 pr-4 py-2.5 rounded-lg',
              'bg-gray-50 border-2',
              error ? 'border-red-300 focus:border-red-400' : 'border-gray-300 focus:border-blue-400',
              'text-sm font-medium uppercase',
              'placeholder-gray-400',
              'focus:outline-none focus:bg-white',
              'transition-colors'
            )}
            disabled={isLoading}
          />
        </div>
        <TouchButton
          variant="secondary"
          size="medium"
          onClick={handleApplyCoupon}
          disabled={!couponInput.trim() || isLoading}
          className="!px-4"
        >
          {isLoading ? (
            <Loader2 className="w-5 h-5 animate-spin" />
          ) : (
            t('pos.coupon.apply', 'Apply')
          )}
        </TouchButton>
      </div>
      {error && (
        <p className="text-sm text-red-600 flex items-center gap-1">
          <X className="w-4 h-4" />
          {error}
        </p>
      )}
    </div>
  );
};

export default CouponInput;
