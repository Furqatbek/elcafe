import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { ChevronLeft, Plus, Trash2, CreditCard, AlertTriangle, X } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import CartItem from '../components/CartItem';
import CouponInput from '../components/CouponInput';
import usePOSStore from '../store/posStore';

/**
 * CartScreen - Order review and summary
 * Edit quantities, remove items, view totals, proceed to payment
 */
const CartScreen = () => {
  const { t } = useTranslation();
  const {
    currentOrder,
    updateItemQuantity,
    removeItemFromCart,
    clearCart,
    setCurrentScreen,
    productAvailability,
    checkProductAvailability,
  } = usePOSStore();

  const { items, subtotal, tax, deliveryFee, serviceFee, entryFee, discount, total, type, notes } = currentOrder;
  const [availabilityWarnings, setAvailabilityWarnings] = useState([]);
  const [isValidating, setIsValidating] = useState(false);

  const restaurantId = 1; // TODO: Add restaurant selector

  // Validate cart availability when items change
  useEffect(() => {
    const validateCart = async () => {
      if (items.length === 0) {
        setAvailabilityWarnings([]);
        return;
      }

      setIsValidating(true);
      const warnings = [];

      for (const item of items) {
        try {
          const availability = await checkProductAvailability(item.productId, restaurantId);
          if (availability) {
            if (!availability.available) {
              warnings.push({
                itemId: item.id,
                productName: item.name,
                type: 'unavailable',
                message: t('pos.cart.itemOutOfStock', '{{name}} is out of stock', { name: item.name }),
              });
            } else if (availability.maxQuantityAvailable < item.quantity) {
              warnings.push({
                itemId: item.id,
                productName: item.name,
                type: 'insufficient',
                message: t('pos.cart.itemInsufficientStock', 'Only {{count}} available for {{name}}', { count: availability.maxQuantityAvailable, name: item.name }),
                maxAvailable: availability.maxQuantityAvailable,
              });
            } else if (availability.stockStatus === 'LOW_STOCK') {
              warnings.push({
                itemId: item.id,
                productName: item.name,
                type: 'low_stock',
                message: t('pos.cart.itemLowStock', '{{name}} is running low ({{count}} left)', { name: item.name, count: availability.maxQuantityAvailable }),
              });
            }
          }
        } catch (error) {
          console.error('Failed to check availability for item:', item.productId);
        }
      }

      setAvailabilityWarnings(warnings);
      setIsValidating(false);
    };

    validateCart();
  }, [items]);

  const hasBlockingWarnings = availabilityWarnings.some(
    w => w.type === 'unavailable' || w.type === 'insufficient'
  );

  const handleProceedToDetails = () => {
    // Block proceeding if there are unavailable or insufficient items
    if (hasBlockingWarnings) {
      alert(t('pos.cart.cannotProceed', 'Please remove or adjust unavailable items before proceeding.'));
      return;
    }
    // Navigate to order details screen based on type
    setCurrentScreen('details');
  };

  const handleContinueShopping = () => {
    setCurrentScreen('menu');
  };

  const handleClearCart = () => {
    if (window.confirm(t('pos.cart.confirmClear', 'Are you sure you want to clear the entire cart?'))) {
      clearCart();
    }
  };

  const [showMobileSummary, setShowMobileSummary] = useState(false);

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-3 sm:px-6 py-3 sm:py-4 flex-shrink-0">
        <div className="flex items-center justify-between gap-2">
          <TouchButton
            variant="ghost"
            size="medium"
            onClick={handleContinueShopping}
            icon={<ChevronLeft className="w-5 sm:w-6 h-5 sm:h-6" />}
            className="!px-2 sm:!px-4"
          >
            <span className="hidden sm:inline">{t('pos.cart.continueShopping', 'Continue Shopping')}</span>
            <span className="sm:hidden">{t('common.back', 'Back')}</span>
          </TouchButton>

          <div className="text-center flex-1">
            <h1 className="text-lg sm:text-2xl font-bold text-gray-900">{t('pos.cart.orderSummary', 'Order Summary')}</h1>
            <p className="text-xs sm:text-sm text-gray-600">{t('pos.cart.orderTypeLabel', '{{type}} Order', { type })}</p>
          </div>

          {items.length > 0 && (
            <TouchButton
              variant="danger"
              size="medium"
              onClick={handleClearCart}
              icon={<Trash2 className="w-4 sm:w-5 h-4 sm:h-5" />}
              className="!px-2 sm:!px-4"
            >
              <span className="hidden sm:inline">{t('pos.cart.clearCart', 'Clear Cart')}</span>
            </TouchButton>
          )}

          {items.length === 0 && <div className="w-10 sm:w-[140px]" />}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-hidden flex flex-col lg:flex-row">
        {/* Items List */}
        <div className="flex-1 overflow-y-auto p-3 sm:p-6">
          {items.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-center px-4">
              <div className="w-20 sm:w-32 h-20 sm:h-32 bg-gray-200 rounded-full flex items-center justify-center mb-4 sm:mb-6">
                <svg
                  className="w-10 sm:w-16 h-10 sm:h-16 text-gray-400"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"
                  />
                </svg>
              </div>
              <h2 className="text-xl sm:text-3xl font-bold text-gray-900 mb-2">
                {t('pos.cart.emptyCart', 'Your cart is empty')}
              </h2>
              <p className="text-sm sm:text-lg text-gray-600 mb-4 sm:mb-6">
                {t('pos.cart.emptyCartMessage', 'Add some items from the menu to get started')}
              </p>
              <TouchButton
                variant="primary"
                size="large"
                onClick={handleContinueShopping}
                icon={<Plus className="w-5 sm:w-6 h-5 sm:h-6" />}
              >
                {t('pos.cart.browseMenu', 'Browse Menu')}
              </TouchButton>
            </div>
          ) : (
            <div className="max-w-3xl mx-auto space-y-3 sm:space-y-4 pb-32 lg:pb-0">
              {items.map(item => (
                <CartItem
                  key={item.id}
                  item={item}
                  onUpdateQuantity={updateItemQuantity}
                  onRemove={removeItemFromCart}
                />
              ))}
            </div>
          )}
        </div>

        {/* Mobile Bottom Summary Bar */}
        {items.length > 0 && (
          <div className="lg:hidden fixed bottom-0 left-0 right-0 bg-white border-t-2 border-gray-200 p-3 z-40">
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="text-xs text-gray-600">{t('pos.cart.total', 'Total')}</p>
                <p className="text-xl font-bold text-gray-900">{total.toFixed(2)}</p>
              </div>
              <div className="flex gap-2">
                <TouchButton
                  variant="outline"
                  size="small"
                  onClick={() => setShowMobileSummary(true)}
                >
                  {t('pos.cart.details', 'Details')}
                </TouchButton>
                <TouchButton
                  variant={hasBlockingWarnings ? 'secondary' : 'primary'}
                  size="medium"
                  onClick={handleProceedToDetails}
                  disabled={hasBlockingWarnings || isValidating}
                  icon={<CreditCard className="w-5 h-5" />}
                >
                  {t('pos.cart.proceed', 'Proceed')}
                </TouchButton>
              </div>
            </div>
            {availabilityWarnings.length > 0 && (
              <div className={cn(
                'rounded-lg p-2 text-xs',
                hasBlockingWarnings ? 'bg-red-50 text-red-700' : 'bg-orange-50 text-orange-700'
              )}>
                <AlertTriangle className="w-4 h-4 inline mr-1" />
                {availabilityWarnings[0].message}
                {availabilityWarnings.length > 1 && ` (+${availabilityWarnings.length - 1} more)`}
              </div>
            )}
          </div>
        )}

        {/* Mobile Summary Drawer */}
        {showMobileSummary && items.length > 0 && (
          <>
            <div
              className="lg:hidden fixed inset-0 bg-black bg-opacity-50 z-40"
              onClick={() => setShowMobileSummary(false)}
            />
            <div className="lg:hidden fixed bottom-0 left-0 right-0 bg-white z-50 rounded-t-2xl max-h-[70vh] overflow-y-auto">
              <div className="p-4 border-b sticky top-0 bg-white flex justify-between items-center">
                <h3 className="font-semibold text-lg">{t('pos.cart.orderDetails', 'Order Details')}</h3>
                <button onClick={() => setShowMobileSummary(false)} className="p-2">
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="p-4 space-y-4">
                {/* Coupon Input */}
                <CouponInput restaurantId={restaurantId} />

                {/* Price Breakdown */}
                <div className="space-y-2 bg-gray-50 rounded-lg p-3">
                  <div className="flex justify-between">
                    <span className="text-gray-700">{t('pos.cart.subtotal', 'Subtotal')}</span>
                    <span className="font-semibold">{subtotal.toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-700">{t('pos.cart.tax', 'Tax')}</span>
                    <span className="font-semibold">{tax.toFixed(2)}</span>
                  </div>
                  {deliveryFee > 0 && (
                    <div className="flex justify-between">
                      <span className="text-gray-700">{t('pos.cart.deliveryFee', 'Delivery')}</span>
                      <span className="font-semibold">{deliveryFee.toFixed(2)}</span>
                    </div>
                  )}
                  {serviceFee > 0 && (
                    <div className="flex justify-between">
                      <span className="text-gray-700">{t('pos.cart.serviceFee', 'Service Fee')}</span>
                      <span className="font-semibold">{serviceFee.toFixed(2)}</span>
                    </div>
                  )}
                  {entryFee > 0 && (
                    <div className="flex justify-between">
                      <span className="text-gray-700">{t('pos.cart.entryFee', 'Entry Fee')}</span>
                      <span className="font-semibold">{entryFee.toFixed(2)}</span>
                    </div>
                  )}
                  {discount > 0 && (
                    <div className="flex justify-between text-green-600">
                      <span>{t('pos.cart.discount', 'Discount')}</span>
                      <span className="font-semibold">-{discount.toFixed(2)}</span>
                    </div>
                  )}
                  <div className="pt-2 border-t flex justify-between">
                    <span className="font-bold">{t('pos.cart.total', 'Total')}</span>
                    <span className="text-xl font-bold">{total.toFixed(2)}</span>
                  </div>
                </div>

                {/* Action Buttons */}
                <div className="space-y-2">
                  <TouchButton
                    variant={hasBlockingWarnings ? 'secondary' : 'primary'}
                    size="large"
                    fullWidth
                    onClick={() => { setShowMobileSummary(false); handleProceedToDetails(); }}
                    disabled={hasBlockingWarnings || isValidating}
                    icon={<CreditCard className="w-5 h-5" />}
                  >
                    {isValidating
                      ? t('pos.cart.validating', 'Checking...')
                      : t('pos.cart.proceedToDetails', 'Proceed to Details')}
                  </TouchButton>
                  <TouchButton
                    variant="outline"
                    size="medium"
                    fullWidth
                    onClick={() => { setShowMobileSummary(false); handleContinueShopping(); }}
                  >
                    {t('pos.cart.addMoreItems', 'Add More Items')}
                  </TouchButton>
                </div>
              </div>
            </div>
          </>
        )}

        {/* Order Summary Sidebar - Desktop */}
        {items.length > 0 && (
          <div className="hidden lg:flex w-[320px] xl:w-[400px] bg-white border-l-2 border-gray-200 flex-col">
            {/* Summary Details */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Order Type */}
              <div>
                <h3 className="text-sm font-semibold text-gray-600 uppercase mb-3">
                  {t('pos.cart.orderType', 'Order Type')}
                </h3>
                <div className="bg-blue-50 border-2 border-blue-200 rounded-lg p-4">
                  <p className="text-lg font-bold text-blue-900">{type}</p>
                </div>
              </div>

              {/* Order Notes */}
              <div>
                <h3 className="text-sm font-semibold text-gray-600 uppercase mb-3">
                  {t('pos.cart.orderNotes', 'Order Notes')}
                </h3>
                <textarea
                  value={notes}
                  onChange={(e) => usePOSStore.getState().updateOrderNotes(e.target.value)}
                  placeholder={t('pos.cart.notesPlaceholder', 'Add special instructions...')}
                  rows={3}
                  className={cn(
                    'w-full px-4 py-3 rounded-lg',
                    'bg-gray-50 border-2 border-gray-300',
                    'text-base text-gray-900 placeholder-gray-500',
                    'focus:border-blue-400 focus:bg-white focus:outline-none',
                    'resize-none'
                  )}
                />
              </div>

              {/* Coupon Input */}
              <div>
                <h3 className="text-sm font-semibold text-gray-600 uppercase mb-3">
                  {t('pos.cart.discount', 'Discount')}
                </h3>
                <CouponInput restaurantId={restaurantId} />
              </div>

              {/* Price Breakdown */}
              <div>
                <h3 className="text-sm font-semibold text-gray-600 uppercase mb-3">
                  {t('pos.cart.priceBreakdown', 'Price Breakdown')}
                </h3>
                <div className="space-y-3 bg-gray-50 rounded-lg p-4">
                  <div className="flex justify-between items-center">
                    <span className="text-gray-700">{t('pos.cart.subtotal', 'Subtotal')}</span>
                    <span className="text-lg font-semibold text-gray-900">
                      {subtotal.toFixed(2)}
                    </span>
                  </div>

                  <div className="flex justify-between items-center">
                    <span className="text-gray-700">{t('pos.cart.tax', 'Tax')}</span>
                    <span className="text-lg font-semibold text-gray-900">
                      {tax.toFixed(2)}
                    </span>
                  </div>

                  {deliveryFee > 0 && (
                    <div className="flex justify-between items-center">
                      <span className="text-gray-700">{t('pos.cart.deliveryFee', 'Delivery Fee')}</span>
                      <span className="text-lg font-semibold text-gray-900">
                        {deliveryFee.toFixed(2)}
                      </span>
                    </div>
                  )}

                  {serviceFee > 0 && (
                    <div className="flex justify-between items-center">
                      <span className="text-gray-700">{t('pos.cart.serviceFee', 'Service Fee')}</span>
                      <span className="text-lg font-semibold text-gray-900">
                        {serviceFee.toFixed(2)}
                      </span>
                    </div>
                  )}

                  {entryFee > 0 && (
                    <div className="flex justify-between items-center">
                      <span className="text-gray-700">{t('pos.cart.entryFee', 'Entry Fee')}</span>
                      <span className="text-lg font-semibold text-gray-900">
                        {entryFee.toFixed(2)}
                      </span>
                    </div>
                  )}

                  {discount > 0 && (
                    <div className="flex justify-between items-center text-green-600">
                      <span>{t('pos.cart.discount', 'Discount')}</span>
                      <span className="text-lg font-semibold">
                        -{discount.toFixed(2)}
                      </span>
                    </div>
                  )}

                  <div className="pt-3 border-t-2 border-gray-300">
                    <div className="flex justify-between items-center">
                      <span className="text-lg font-bold text-gray-900">{t('pos.cart.total', 'Total')}</span>
                      <span className="text-3xl font-bold text-gray-900">
                        {total.toFixed(2)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Item Count */}
              <div className="bg-blue-50 rounded-lg p-4 text-center">
                <p className="text-sm text-blue-700 mb-1">{t('pos.cart.totalItems', 'Total Items')}</p>
                <p className="text-3xl font-bold text-blue-900">
                  {items.reduce((sum, item) => sum + item.quantity, 0)}
                </p>
              </div>
            </div>

            {/* Availability Warnings */}
            {availabilityWarnings.length > 0 && (
              <div className="border-t-2 border-gray-200 p-4">
                <div className={cn(
                  'rounded-lg p-4',
                  hasBlockingWarnings ? 'bg-red-50 border-2 border-red-200' : 'bg-orange-50 border-2 border-orange-200'
                )}>
                  <div className="flex items-center gap-2 mb-2">
                    <AlertTriangle className={cn(
                      'w-5 h-5',
                      hasBlockingWarnings ? 'text-red-600' : 'text-orange-600'
                    )} />
                    <span className={cn(
                      'font-semibold',
                      hasBlockingWarnings ? 'text-red-800' : 'text-orange-800'
                    )}>
                      {t('pos.cart.stockWarning', 'Stock Warning')}
                    </span>
                  </div>
                  <ul className="space-y-1">
                    {availabilityWarnings.map((warning, index) => (
                      <li key={index} className={cn(
                        'text-sm',
                        warning.type === 'low_stock' ? 'text-orange-700' : 'text-red-700'
                      )}>
                        • {warning.message}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            )}

            {/* Action Buttons */}
            <div className="border-t-2 border-gray-200 p-6 space-y-3">
              <TouchButton
                variant={hasBlockingWarnings ? 'secondary' : 'primary'}
                size="large"
                fullWidth
                onClick={handleProceedToDetails}
                disabled={hasBlockingWarnings || isValidating}
                icon={<CreditCard className="w-6 h-6" />}
              >
                {isValidating
                  ? t('pos.cart.validating', 'Checking availability...')
                  : t('pos.cart.proceedToDetails', 'Proceed to Details')}
              </TouchButton>

              <TouchButton
                variant="outline"
                size="medium"
                fullWidth
                onClick={handleContinueShopping}
              >
                {t('pos.cart.addMoreItems', 'Add More Items')}
              </TouchButton>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CartScreen;
