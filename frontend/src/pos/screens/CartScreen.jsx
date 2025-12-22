import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { ChevronLeft, Plus, Trash2, CreditCard, AlertTriangle } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import CartItem from '../components/CartItem';
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

  const { items, subtotal, tax, deliveryFee, total, type, notes } = currentOrder;
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
                message: `${item.name} is out of stock`,
              });
            } else if (availability.maxQuantityAvailable < item.quantity) {
              warnings.push({
                itemId: item.id,
                productName: item.name,
                type: 'insufficient',
                message: `Only ${availability.maxQuantityAvailable} available for ${item.name}`,
                maxAvailable: availability.maxQuantityAvailable,
              });
            } else if (availability.stockStatus === 'LOW_STOCK') {
              warnings.push({
                itemId: item.id,
                productName: item.name,
                type: 'low_stock',
                message: `${item.name} is running low (${availability.maxQuantityAvailable} left)`,
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

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-6 py-4 flex-shrink-0">
        <div className="flex items-center justify-between">
          <TouchButton
            variant="ghost"
            size="medium"
            onClick={handleContinueShopping}
            icon={<ChevronLeft className="w-6 h-6" />}
          >
            {t('pos.cart.continueShopping', 'Continue Shopping')}
          </TouchButton>

          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-900">{t('pos.cart.orderSummary', 'Order Summary')}</h1>
            <p className="text-sm text-gray-600">{t('pos.cart.orderTypeLabel', '{{type}} Order', { type })}</p>
          </div>

          {items.length > 0 && (
            <TouchButton
              variant="danger"
              size="medium"
              onClick={handleClearCart}
              icon={<Trash2 className="w-5 h-5" />}
            >
              {t('pos.cart.clearCart', 'Clear Cart')}
            </TouchButton>
          )}

          {items.length === 0 && <div className="w-[140px]" />}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-hidden flex">
        {/* Items List */}
        <div className="flex-1 overflow-y-auto p-6">
          {items.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-center">
              <div className="w-32 h-32 bg-gray-200 rounded-full flex items-center justify-center mb-6">
                <svg
                  className="w-16 h-16 text-gray-400"
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
              <h2 className="text-3xl font-bold text-gray-900 mb-2">
                {t('pos.cart.emptyCart', 'Your cart is empty')}
              </h2>
              <p className="text-lg text-gray-600 mb-6">
                {t('pos.cart.emptyCartMessage', 'Add some items from the menu to get started')}
              </p>
              <TouchButton
                variant="primary"
                size="large"
                onClick={handleContinueShopping}
                icon={<Plus className="w-6 h-6" />}
              >
                {t('pos.cart.browseMenu', 'Browse Menu')}
              </TouchButton>
            </div>
          ) : (
            <div className="max-w-3xl mx-auto space-y-4">
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

        {/* Order Summary Sidebar */}
        {items.length > 0 && (
          <div className="w-[400px] bg-white border-l-2 border-gray-200 flex flex-col">
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
                    <span className="text-gray-700">{t('pos.cart.tax', 'Tax (8%)')}</span>
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
