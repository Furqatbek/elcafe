import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Minus, Plus, Trash2, Scale, ChefHat } from 'lucide-react';
import usePOSStore from '../store/posStore';

const PORTION_OPTIONS = [
  { label: '¼', value: 0.25 },
  { label: '½', value: 0.5 },
  { label: '¾', value: 0.75 },
  { label: '1×', value: 1 },
  { label: '1½', value: 1.5 },
  { label: '2×', value: 2 },
];

/**
 * CartItem - Display and manage individual cart items.
 * Supports countable, weight-based, and portion-based items.
 */
const CartItem = ({
  item,
  onUpdateQuantity,
  onRemove,
  className = '',
}) => {
  const { t } = useTranslation();
  const { updateItemPortion } = usePOSStore();
  const { id, name, basePrice, modifiers, quantity, itemTotal, notes, weightAmount, weightUnit, portionMultiplier } = item;

  const [showPortions, setShowPortions] = useState(false);

  const isWeightItem = !!weightAmount;
  const isPortionItem = portionMultiplier && portionMultiplier !== 1;
  const currentPortion = portionMultiplier || 1;

  const handleQuantityChange = (newQuantity) => {
    if (newQuantity < 1) return;
    if (newQuantity > 99) return;
    if ('vibrate' in navigator) navigator.vibrate(5);
    onUpdateQuantity(id, newQuantity);
  };

  const handleRemove = () => {
    if ('vibrate' in navigator) navigator.vibrate([10, 50, 10]);
    onRemove(id);
  };

  const handlePortionSelect = (value) => {
    updateItemPortion(id, value);
    setShowPortions(false);
  };

  // Display label for portion in header
  const portionLabel = () => {
    const match = PORTION_OPTIONS.find(o => o.value === currentPortion);
    return match ? match.label : `${currentPortion}×`;
  };

  return (
    <div
      className={cn(
        'bg-white rounded-lg p-4 border-2 border-gray-200',
        'flex flex-col gap-3',
        className
      )}
    >
      {/* Item Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <h4 className="font-semibold text-base text-gray-900 truncate">{name}</h4>
            {isWeightItem && (
              <span className="inline-flex items-center gap-0.5 text-xs font-medium bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">
                <Scale className="w-3 h-3" />
                {t('pos.cart.weightItem', 'by weight')}
              </span>
            )}
            {!isWeightItem && isPortionItem && (
              <span className="inline-flex items-center gap-0.5 text-xs font-medium bg-purple-100 text-purple-700 px-1.5 py-0.5 rounded">
                <ChefHat className="w-3 h-3" />
                {portionLabel()} {t('pos.cart.portion', 'portion')}
              </span>
            )}
          </div>

          {/* Price line */}
          {isWeightItem ? (
            <p className="text-sm text-gray-600 mt-0.5">
              {basePrice.toFixed(2)} / {weightUnit || 'KG'}
              {' · '}
              <span className="font-medium text-amber-700">{weightAmount} {weightUnit || 'KG'}</span>
            </p>
          ) : (
            <p className="text-sm text-gray-600 mt-0.5">
              {basePrice.toFixed(2)} {t('pos.cart.each', 'each')}
              {currentPortion !== 1 && (
                <span className="ml-1 text-purple-600">× {portionLabel()}</span>
              )}
            </p>
          )}
        </div>

        {/* Remove Button */}
        <button
          onClick={handleRemove}
          className="flex-shrink-0 p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors active:scale-95"
          aria-label={t('pos.cart.removeItem', 'Remove item')}
        >
          <Trash2 className="w-5 h-5" />
        </button>
      </div>

      {/* Modifiers */}
      {modifiers && modifiers.length > 0 && (
        <div className="space-y-1">
          {modifiers.map((modifier, index) => (
            <div
              key={index}
              className="flex items-center justify-between text-sm text-gray-600"
            >
              <span className="flex items-center gap-1">
                <span className="text-gray-400">+</span>
                {modifier.name}
              </span>
              {modifier.price > 0 && <span>{modifier.price.toFixed(2)}</span>}
            </div>
          ))}
        </div>
      )}

      {/* Notes */}
      {notes && (
        <div className="text-sm text-gray-600 italic bg-gray-50 rounded p-2">
          {t('pos.cart.note', 'Note')}: {notes}
        </div>
      )}

      {/* Portion selector (expandable, non-weight items only) */}
      {!isWeightItem && (
        <div>
          <button
            onClick={() => setShowPortions(v => !v)}
            className={cn(
              'text-xs font-medium px-2.5 py-1 rounded-lg border transition-colors',
              showPortions
                ? 'bg-purple-100 border-purple-300 text-purple-700'
                : 'bg-gray-50 border-gray-200 text-gray-500 hover:border-gray-300'
            )}
          >
            <ChefHat className="w-3 h-3 inline mr-1" />
            {showPortions
              ? t('pos.cart.closePortion', 'Close')
              : t('pos.cart.setPortion', 'Set portion')}
            {currentPortion !== 1 && !showPortions && (
              <span className="ml-1 text-purple-700 font-bold">({portionLabel()})</span>
            )}
          </button>

          {showPortions && (
            <div className="flex flex-wrap gap-1.5 mt-2">
              {PORTION_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  onClick={() => handlePortionSelect(opt.value)}
                  className={cn(
                    'px-3 py-1.5 rounded-lg text-sm font-semibold border-2 transition-all active:scale-95',
                    currentPortion === opt.value
                      ? 'bg-purple-600 border-purple-600 text-white'
                      : 'bg-white border-gray-200 text-gray-700 hover:border-purple-300'
                  )}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Quantity Controls & Total */}
      <div className="flex items-center justify-between gap-4 pt-2 border-t border-gray-200">
        {/* Quantity Controls — hidden for weight items (weight IS the quantity) */}
        {isWeightItem ? (
          <div className="text-sm text-gray-500">
            {t('pos.cart.qty', 'Qty')}: <span className="font-semibold text-gray-800">{quantity}</span>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <button
              onClick={() => handleQuantityChange(quantity - 1)}
              disabled={quantity <= 1}
              className="flex items-center justify-center w-10 h-10 bg-gray-200 hover:bg-gray-300 active:bg-gray-400 disabled:opacity-50 disabled:hover:bg-gray-200 rounded-lg transition-colors active:scale-95"
              aria-label={t('pos.cart.decreaseQty', 'Decrease quantity')}
            >
              <Minus className="w-5 h-5 text-gray-900" />
            </button>

            <div className="min-w-[48px] text-center">
              <span className="text-xl font-bold text-gray-900">{quantity}</span>
            </div>

            <button
              onClick={() => handleQuantityChange(quantity + 1)}
              disabled={quantity >= 99}
              className="flex items-center justify-center w-10 h-10 bg-gray-200 hover:bg-gray-300 active:bg-gray-400 disabled:opacity-50 disabled:hover:bg-gray-200 rounded-lg transition-colors active:scale-95"
              aria-label={t('pos.cart.increaseQty', 'Increase quantity')}
            >
              <Plus className="w-5 h-5 text-gray-900" />
            </button>
          </div>
        )}

        {/* Item Total */}
        <div className="text-right">
          <div className="text-xl font-bold text-gray-900">
            {itemTotal.toFixed(2)}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CartItem;
