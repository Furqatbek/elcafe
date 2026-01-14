import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { ChevronLeft, Plus, Minus, ShoppingCart } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';

/**
 * ProductModifiersScreen - Product customization and add-ons
 * Size selection, modifiers, quantity, special instructions
 */
const ProductModifiersScreen = () => {
  const { t } = useTranslation();
  const { ui, addItemToCart, setCurrentScreen } = usePOSStore();
  const product = ui.selectedProduct;

  const [selectedSize, setSelectedSize] = useState(null);
  const [selectedModifiers, setSelectedModifiers] = useState([]);
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState('');

  if (!product) {
    return null;
  }

  // Calculate total price
  const basePrice = selectedSize?.price || product.price;
  const modifiersTotal = selectedModifiers.reduce((sum, mod) => sum + mod.price, 0);
  const itemPrice = basePrice + modifiersTotal;
  const totalPrice = itemPrice * quantity;

  const handleModifierToggle = (modifier) => {
    const isSelected = selectedModifiers.find(m => m.id === modifier.id);

    if (isSelected) {
      setSelectedModifiers(selectedModifiers.filter(m => m.id !== modifier.id));
    } else {
      setSelectedModifiers([...selectedModifiers, modifier]);
    }
  };

  const handleAddToCart = () => {
    const finalModifiers = [
      ...(selectedSize ? [{ name: t('pos.modifiers.sizeLabel', 'Size: {{size}}', { size: selectedSize.name }), price: 0 }] : []),
      ...selectedModifiers,
    ];

    addItemToCart(
      { ...product, price: basePrice },
      finalModifiers,
      quantity
    );

    // Go back to menu
    setCurrentScreen('menu');
  };

  const handleQuantityChange = (delta) => {
    const newQuantity = quantity + delta;
    if (newQuantity >= 1 && newQuantity <= 99) {
      setQuantity(newQuantity);
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
            onClick={() => setCurrentScreen('menu')}
            icon={<ChevronLeft className="w-6 h-6" />}
          >
            {t('pos.modifiers.backToMenu', 'Back to Menu')}
          </TouchButton>

          <h1 className="text-2xl font-bold text-gray-900">{t('pos.modifiers.title', 'Customize Your Order')}</h1>

          <div className="w-[140px]" /> {/* Spacer for centering */}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-6 space-y-6">
          {/* Product Header */}
          <div className="bg-white rounded-xl p-6 border-2 border-gray-200">
            <div className="flex gap-6">
              {/* Product Image */}
              {product.imageUrl && (
                <div className="w-32 h-32 flex-shrink-0 bg-gray-100 rounded-lg overflow-hidden">
                  <img
                    src={product.imageUrl}
                    alt={product.name}
                    className="w-full h-full object-cover"
                  />
                </div>
              )}

              {/* Product Info */}
              <div className="flex-1">
                <h2 className="text-3xl font-bold text-gray-900 mb-2">
                  {product.name}
                </h2>
                {product.description && (
                  <p className="text-lg text-gray-600 mb-3">
                    {product.description}
                  </p>
                )}
                <p className="text-2xl font-bold text-blue-600">
                  {basePrice.toFixed(2)}
                </p>
              </div>
            </div>
          </div>

          {/* Size Selection (if applicable) */}
          {product.variants && product.variants.length > 0 && (
            <div className="bg-white rounded-xl p-6 border-2 border-gray-200">
              <h3 className="text-xl font-bold text-gray-900 mb-4">
                {t('pos.modifiers.selectSize', 'Select Size')} <span className="text-red-500">*</span>
              </h3>
              <div className="grid grid-cols-3 gap-3">
                {product.variants.map(variant => (
                  <button
                    key={variant.id}
                    onClick={() => setSelectedSize(variant)}
                    className={cn(
                      'flex flex-col items-center justify-center',
                      'min-h-[100px] p-4 rounded-lg',
                      'border-3 transition-all',
                      selectedSize?.id === variant.id
                        ? 'border-blue-600 bg-blue-50'
                        : 'border-gray-300 bg-white hover:border-gray-400',
                      'focus:outline-none focus:ring-4 focus:ring-blue-200'
                    )}
                  >
                    <span className="text-lg font-semibold text-gray-900">
                      {variant.name}
                    </span>
                    <span className="text-sm text-gray-600 mt-1">
                      {variant.price.toFixed(2)}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Add-ons / Modifiers */}
          {product.addOns && product.addOns.length > 0 && (
            <div className="bg-white rounded-xl p-6 border-2 border-gray-200">
              <h3 className="text-xl font-bold text-gray-900 mb-4">
                {t('pos.modifiers.addOns', 'Add-ons & Extras')}
              </h3>
              <div className="space-y-3">
                {product.addOns.map(addon => {
                  const isSelected = selectedModifiers.find(m => m.id === addon.id);

                  return (
                    <button
                      key={addon.id}
                      onClick={() => handleModifierToggle(addon)}
                      className={cn(
                        'w-full flex items-center justify-between',
                        'min-h-[64px] p-4 rounded-lg',
                        'border-2 transition-all',
                        isSelected
                          ? 'border-blue-600 bg-blue-50'
                          : 'border-gray-300 bg-white hover:border-gray-400',
                        'focus:outline-none focus:ring-4 focus:ring-blue-200'
                      )}
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={cn(
                            'w-6 h-6 rounded-md border-2 flex items-center justify-center',
                            isSelected
                              ? 'bg-blue-600 border-blue-600'
                              : 'border-gray-300'
                          )}
                        >
                          {isSelected && (
                            <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                              <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                            </svg>
                          )}
                        </div>
                        <div className="text-left">
                          <span className="font-semibold text-gray-900 block">
                            {addon.name}
                          </span>
                          {addon.description && (
                            <span className="text-sm text-gray-600">
                              {addon.description}
                            </span>
                          )}
                        </div>
                      </div>
                      <span className="font-bold text-gray-900">
                        +{addon.price.toFixed(2)}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Special Instructions */}
          <div className="bg-white rounded-xl p-6 border-2 border-gray-200">
            <h3 className="text-xl font-bold text-gray-900 mb-4">
              {t('pos.modifiers.specialInstructions', 'Special Instructions')}
            </h3>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={t('pos.modifiers.instructionsPlaceholder', 'E.g., No onions, extra sauce...')}
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
        </div>
      </div>

      {/* Footer - Quantity & Add to Cart */}
      <div className="bg-white border-t-2 border-gray-200 px-6 py-5 flex-shrink-0">
        <div className="max-w-4xl mx-auto flex items-center justify-between gap-6">
          {/* Quantity Controls */}
          <div className="flex items-center gap-3">
            <span className="text-lg font-semibold text-gray-700">{t('pos.modifiers.quantity', 'Quantity:')}</span>
            <div className="flex items-center gap-2">
              <TouchButton
                variant="secondary"
                size="medium"
                onClick={() => handleQuantityChange(-1)}
                disabled={quantity <= 1}
                icon={<Minus className="w-5 h-5" />}
              />
              <div className="min-w-[64px] text-center">
                <span className="text-3xl font-bold text-gray-900">{quantity}</span>
              </div>
              <TouchButton
                variant="secondary"
                size="medium"
                onClick={() => handleQuantityChange(1)}
                disabled={quantity >= 99}
                icon={<Plus className="w-5 h-5" />}
              />
            </div>
          </div>

          {/* Add to Cart Button */}
          <div className="flex items-center gap-4">
            <div className="text-right">
              <p className="text-sm text-gray-600">{t('pos.cart.total', 'Total')}</p>
              <p className="text-3xl font-bold text-gray-900">
                {totalPrice.toFixed(2)}
              </p>
            </div>
            <TouchButton
              variant="success"
              size="large"
              onClick={handleAddToCart}
              icon={<ShoppingCart className="w-6 h-6" />}
            >
              {t('pos.modifiers.addToCart', 'Add to Cart')}
            </TouchButton>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductModifiersScreen;
