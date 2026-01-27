import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Plus } from 'lucide-react';

/**
 * ProductCard - Touch-optimized product card for menu selection
 * Large touch target, clear pricing, visual availability status
 */
const ProductCard = ({
  product,
  onSelect,
  cartQuantity = 0,
  className = '',
}) => {
  const { t } = useTranslation();
  const { name, price, description, imageUrl, available = true, stockStatus, maxQuantityAvailable } = product;

  const handleSelect = () => {
    if (!available) return;

    // Haptic feedback
    if ('vibrate' in navigator) {
      navigator.vibrate(10);
    }

    onSelect(product);
  };

  return (
    <button
      onClick={handleSelect}
      disabled={!available}
      className={cn(
        'group relative flex flex-col bg-white rounded-lg sm:rounded-xl overflow-hidden transition-all duration-200',
        'min-h-[140px] sm:min-h-[180px] w-full',
        'border border-gray-200 sm:border-2',
        available && 'hover:border-blue-400 hover:shadow-lg active:scale-98',
        !available && 'opacity-60 cursor-not-allowed',
        'focus:outline-none focus:ring-2 sm:focus:ring-4 focus:ring-blue-200',
        className
      )}
    >
      {/* Product Image */}
      <div className="relative h-20 sm:h-32 bg-gray-100 overflow-hidden">
        {imageUrl ? (
          <img
            src={imageUrl}
            alt={name}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400">
            <svg
              className="w-10 sm:w-16 h-10 sm:h-16"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
              />
            </svg>
          </div>
        )}

        {/* Stock Status Badge */}
        {stockStatus === 'OUT_OF_STOCK' && (
          <div className="absolute inset-0 bg-black bg-opacity-60 flex items-center justify-center">
            <span className="text-white font-bold text-xs sm:text-lg">{t('pos.menu.outOfStock', 'Out of Stock')}</span>
          </div>
        )}

        {stockStatus === 'LOW_STOCK' && available && (
          <div className="absolute top-1 sm:top-2 left-1 sm:left-2">
            <span className="bg-orange-500 text-white text-[10px] sm:text-xs font-bold px-1.5 sm:px-2 py-0.5 sm:py-1 rounded">
              {t('pos.menu.lowStock', 'Low Stock')} ({maxQuantityAvailable})
            </span>
          </div>
        )}

        {/* Legacy Unavailable Badge */}
        {!available && stockStatus !== 'OUT_OF_STOCK' && (
          <div className="absolute inset-0 bg-black bg-opacity-60 flex items-center justify-center">
            <span className="text-white font-bold text-xs sm:text-lg">{t('pos.menu.unavailable', 'Unavailable')}</span>
          </div>
        )}

        {/* Cart Quantity Badge */}
        {cartQuantity > 0 && (
          <div className="absolute top-1 sm:top-2 right-1 sm:right-2 z-10">
            <div className="bg-blue-600 text-white rounded-full min-w-[24px] sm:min-w-[32px] h-6 sm:h-8 flex items-center justify-center font-bold text-sm sm:text-lg shadow-lg">
              {cartQuantity}
            </div>
          </div>
        )}

        {/* Add Icon - Shows on hover when no items in cart */}
        {available && cartQuantity === 0 && (
          <div className="absolute top-1 sm:top-2 right-1 sm:right-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <div className="bg-blue-600 text-white rounded-full p-1 sm:p-2">
              <Plus className="w-4 sm:w-5 h-4 sm:h-5" />
            </div>
          </div>
        )}
      </div>

      {/* Product Info */}
      <div className="flex-1 p-2 sm:p-4 flex flex-col">
        <h3 className="font-semibold text-sm sm:text-lg text-gray-900 line-clamp-1 text-left">
          {name}
        </h3>

        {description && (
          <p className="text-xs sm:text-sm text-gray-600 mt-0.5 sm:mt-1 line-clamp-1 sm:line-clamp-2 text-left hidden sm:block">
            {description}
          </p>
        )}

        <div className="mt-auto pt-1 sm:pt-3 flex items-center justify-between">
          <span className="text-lg sm:text-2xl font-bold text-gray-900">
            {price.toFixed(2)}
          </span>
        </div>
      </div>
    </button>
  );
};

export default ProductCard;
