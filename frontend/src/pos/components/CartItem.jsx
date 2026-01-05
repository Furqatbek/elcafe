import React from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Minus, Plus, Trash2 } from 'lucide-react';

/**
 * CartItem - Display and manage individual cart items
 * Quantity controls, modifier display, remove option
 */
const CartItem = ({
  item,
  onUpdateQuantity,
  onRemove,
  className = '',
}) => {
  const { t } = useTranslation();
  const { id, name, basePrice, modifiers, quantity, itemTotal, notes } = item;

  const handleQuantityChange = (newQuantity) => {
    if (newQuantity < 1) return;
    if (newQuantity > 99) return;

    // Haptic feedback
    if ('vibrate' in navigator) {
      navigator.vibrate(5);
    }

    onUpdateQuantity(id, newQuantity);
  };

  const handleRemove = () => {
    // Stronger haptic for destructive action
    if ('vibrate' in navigator) {
      navigator.vibrate([10, 50, 10]);
    }

    onRemove(id);
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
          <h4 className="font-semibold text-base text-gray-900 truncate">
            {name}
          </h4>
          <p className="text-sm text-gray-600 mt-0.5">
            {basePrice.toFixed(2)} {t('pos.cart.each', 'each')}
          </p>
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
              {modifier.price > 0 && (
                <span>{modifier.price.toFixed(2)}</span>
              )}
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

      {/* Quantity Controls & Total */}
      <div className="flex items-center justify-between gap-4 pt-2 border-t border-gray-200">
        {/* Quantity Controls */}
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
