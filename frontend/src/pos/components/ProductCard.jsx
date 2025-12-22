import React from 'react';
import { cn } from '../../lib/utils';
import { Plus } from 'lucide-react';

/**
 * ProductCard - Touch-optimized product card for menu selection
 * Large touch target, clear pricing, visual availability status
 */
const ProductCard = ({
  product,
  onSelect,
  className = '',
}) => {
  const { name, price, description, imageUrl, available = true, category, stockStatus, maxQuantityAvailable } = product;

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
        'group relative flex flex-col bg-white rounded-xl overflow-hidden transition-all duration-200',
        'min-h-[180px] w-full',
        'border-2 border-gray-200',
        available && 'hover:border-blue-400 hover:shadow-lg active:scale-98',
        !available && 'opacity-60 cursor-not-allowed',
        'focus:outline-none focus:ring-4 focus:ring-blue-200',
        className
      )}
    >
      {/* Product Image */}
      <div className="relative h-32 bg-gray-100 overflow-hidden">
        {imageUrl ? (
          <img
            src={imageUrl}
            alt={name}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400">
            <svg
              className="w-16 h-16"
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
            <span className="text-white font-bold text-lg">Out of Stock</span>
          </div>
        )}

        {stockStatus === 'LOW_STOCK' && available && (
          <div className="absolute top-2 left-2">
            <span className="bg-orange-500 text-white text-xs font-bold px-2 py-1 rounded">
              Low Stock ({maxQuantityAvailable})
            </span>
          </div>
        )}

        {/* Legacy Unavailable Badge */}
        {!available && stockStatus !== 'OUT_OF_STOCK' && (
          <div className="absolute inset-0 bg-black bg-opacity-60 flex items-center justify-center">
            <span className="text-white font-bold text-lg">Unavailable</span>
          </div>
        )}

        {/* Add Icon - Shows on hover */}
        {available && (
          <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <div className="bg-blue-600 text-white rounded-full p-2">
              <Plus className="w-5 h-5" />
            </div>
          </div>
        )}
      </div>

      {/* Product Info */}
      <div className="flex-1 p-4 flex flex-col">
        <h3 className="font-semibold text-lg text-gray-900 line-clamp-1 text-left">
          {name}
        </h3>

        {description && (
          <p className="text-sm text-gray-600 mt-1 line-clamp-2 text-left">
            {description}
          </p>
        )}

        <div className="mt-auto pt-3 flex items-center justify-between">
          <span className="text-2xl font-bold text-gray-900">
            {price.toFixed(2)}
          </span>
        </div>
      </div>
    </button>
  );
};

export default ProductCard;
