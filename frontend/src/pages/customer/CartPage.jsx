import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCustomer } from './CustomerContext';
import {
  ChevronLeft,
  Plus,
  Minus,
  Trash2,
  ShoppingBag,
  AlertCircle,
} from 'lucide-react';

export default function CartPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { session, cart, updateCartItem, removeFromCart, clearCart, loading } = useCustomer();
  const [updating, setUpdating] = useState(null);
  const [error, setError] = useState(null);

  const formatPrice = (price) => {
    return new Intl.NumberFormat('uz-UZ').format(price) + ' UZS';
  };

  const handleQuantityChange = async (itemId, newQuantity) => {
    if (newQuantity < 1) {
      handleRemove(itemId);
      return;
    }
    setUpdating(itemId);
    try {
      await updateCartItem(itemId, newQuantity);
    } catch (err) {
      setError(t('selfService.failedToUpdateQty'));
    } finally {
      setUpdating(null);
    }
  };

  const handleRemove = async (itemId) => {
    setUpdating(itemId);
    try {
      await removeFromCart(itemId);
    } catch (err) {
      setError(t('selfService.failedToRemoveItem'));
    } finally {
      setUpdating(null);
    }
  };

  const handleClearCart = async () => {
    if (!window.confirm(t('selfService.confirmClearCart'))) return;
    try {
      await clearCart();
    } catch (err) {
      setError(t('selfService.failedToClearCart'));
    }
  };

  if (!session) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
          <AlertCircle className="w-12 h-12 mx-auto mb-4 text-orange-500" />
          <h2 className="text-xl font-semibold text-gray-800 mb-2">{t('selfService.noActiveSession')}</h2>
          <p className="text-gray-600 mb-4">{t('selfService.scanQrCodeMessage')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-32">
      {/* Header */}
      <div className="bg-white shadow-sm sticky top-0 z-40">
        <div className="max-w-lg mx-auto px-4 py-3">
          <div className="flex items-center justify-between">
            <button
              onClick={() => navigate(-1)}
              className="flex items-center gap-2 text-gray-600"
            >
              <ChevronLeft className="w-5 h-5" />
              <span>{t('selfService.back')}</span>
            </button>
            <h1 className="text-lg font-bold text-gray-900">{t('selfService.yourCart')}</h1>
            {cart.items.length > 0 && (
              <button
                onClick={handleClearCart}
                className="text-red-600 text-sm"
              >
                {t('selfService.clear')}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="max-w-lg mx-auto px-4 py-3">
          <div className="bg-red-50 text-red-700 rounded-lg p-3 flex items-center gap-2">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
            <button onClick={() => setError(null)} className="ml-auto">
              &times;
            </button>
          </div>
        </div>
      )}

      {/* Cart Items */}
      <div className="max-w-lg mx-auto px-4 py-4">
        {cart.items.length === 0 ? (
          <div className="text-center py-12">
            <ShoppingBag className="w-16 h-16 mx-auto mb-4 text-gray-300" />
            <h2 className="text-xl font-semibold text-gray-800 mb-2">{t('selfService.emptyCartTitle')}</h2>
            <p className="text-gray-600 mb-6">{t('selfService.emptyCartMessage')}</p>
            <button
              onClick={() => navigate(-1)}
              className="bg-blue-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-blue-700"
            >
              {t('selfService.browseMenu')}
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            {cart.items.map((item) => (
              <div
                key={item.id}
                className={`bg-white rounded-lg shadow-sm p-4 ${updating === item.id ? 'opacity-50' : ''}`}
              >
                <div className="flex gap-4">
                  {item.imageUrl ? (
                    <img
                      src={item.imageUrl}
                      alt={item.productName}
                      className="w-20 h-20 rounded-lg object-cover flex-shrink-0"
                    />
                  ) : (
                    <div className="w-20 h-20 rounded-lg bg-gray-100 flex-shrink-0" />
                  )}
                  <div className="flex-1 min-w-0">
                    <h3 className="font-medium text-gray-900">{item.productName}</h3>
                    {item.variantName && (
                      <p className="text-sm text-gray-500">{item.variantName}</p>
                    )}
                    {item.specialInstructions && (
                      <p className="text-sm text-gray-500 italic mt-1">
                        &quot;{item.specialInstructions}&quot;
                      </p>
                    )}
                    <p className="text-blue-600 font-semibold mt-1">
                      {formatPrice(item.unitPrice)}
                    </p>
                  </div>
                  <button
                    onClick={() => handleRemove(item.id)}
                    className="text-red-500 p-1 hover:bg-red-50 rounded self-start"
                  >
                    <Trash2 className="w-5 h-5" />
                  </button>
                </div>

                {/* Quantity Controls */}
                <div className="flex items-center justify-between mt-3 pt-3 border-t">
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => handleQuantityChange(item.id, item.quantity - 1)}
                      disabled={updating === item.id}
                      className="w-8 h-8 rounded-full border flex items-center justify-center hover:bg-gray-100 disabled:opacity-50"
                    >
                      <Minus className="w-4 h-4" />
                    </button>
                    <span className="font-semibold w-6 text-center">{item.quantity}</span>
                    <button
                      onClick={() => handleQuantityChange(item.id, item.quantity + 1)}
                      disabled={updating === item.id}
                      className="w-8 h-8 rounded-full border flex items-center justify-center hover:bg-gray-100 disabled:opacity-50"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                  <span className="font-bold text-gray-900">
                    {formatPrice(item.totalPrice)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Order Summary & Checkout */}
      {cart.items.length > 0 && (
        <div className="fixed bottom-0 left-0 right-0 bg-white border-t shadow-lg z-50">
          <div className="max-w-lg mx-auto px-4 py-4">
            {/* Summary */}
            <div className="space-y-2 mb-4">
              <div className="flex justify-between text-gray-600">
                <span>{t('selfService.subtotal')} ({cart.itemCount} {t('selfService.items')})</span>
                <span>{formatPrice(cart.total)}</span>
              </div>
              <div className="flex justify-between font-bold text-lg text-gray-900">
                <span>{t('selfService.total')}</span>
                <span>{formatPrice(cart.total)}</span>
              </div>
            </div>

            {/* Checkout Button */}
            <button
              onClick={() => navigate('/checkout')}
              className="w-full bg-blue-600 text-white rounded-lg py-4 font-semibold hover:bg-blue-700 transition-colors"
            >
              {t('selfService.proceedToCheckout')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
