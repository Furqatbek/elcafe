import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Search, ShoppingCart, X, Grid3x3, List } from 'lucide-react';
import ProductCard from '../components/ProductCard';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';
import { posAPI } from '../../services/api';

/**
 * MenuSelectionScreen - Main menu browsing and product selection
 * Category navigation, search, grid view, cart preview
 */
const MenuSelectionScreen = () => {
  const { t } = useTranslation();
  const {
    currentOrder,
    menu,
    fetchMenuData,
    setSelectedProduct,
    setCurrentScreen,
    ui,
    setSelectedCategory,
    productAvailability,
    checkAllProductsAvailability,
  } = usePOSStore();

  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState('grid'); // 'grid' | 'list'
  const [loading, setLoading] = useState(true);

  const restaurantId = 1; // TODO: Add restaurant selector if multiple restaurants

  // Fetch menu data from backend
  useEffect(() => {
    const loadMenu = async () => {
      try {
        setLoading(true);
        const result = await fetchMenuData(restaurantId);
        // Load product availability after menu is fetched
        if (result.success && result.products.length > 0) {
          await checkAllProductsAvailability(result.products, restaurantId);
        }
      } catch (error) {
        console.error('Failed to fetch menu:', error);
      } finally {
        setLoading(false);
      }
    };

    // Only fetch if menu is stale (older than 5 minutes)
    const isStale = !menu.lastFetched ||
      (new Date() - new Date(menu.lastFetched)) > 5 * 60 * 1000;

    if (isStale) {
      loadMenu();
    } else {
      setLoading(false);
      // Refresh availability even if menu is cached
      if (menu.products.length > 0) {
        checkAllProductsAvailability(menu.products, restaurantId);
      }
    }
  }, []);

  // Filter products based on selected category and search
  const filteredProducts = menu.products.filter(product => {
    const matchesCategory = !ui.selectedCategory || product.categoryId === ui.selectedCategory;
    const matchesSearch = !searchQuery ||
      product.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      product.description?.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesCategory && matchesSearch;
  });

  const handleProductSelect = (product) => {
    // If product has modifiers, show modifier screen
    if (product.hasModifiers || product.variants?.length > 0) {
      setSelectedProduct(product);
      setCurrentScreen('modifiers');
    } else {
      // Add directly to cart
      usePOSStore.getState().addItemToCart(product, [], 1);
    }
  };

  const handleCategorySelect = (categoryId) => {
    setSelectedCategory(categoryId === ui.selectedCategory ? null : categoryId);
  };

  const cartItemCount = currentOrder.items.reduce((sum, item) => sum + item.quantity, 0);

  const [showMobileCategories, setShowMobileCategories] = useState(false);

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Top Bar */}
      <div className="bg-white border-b-2 border-gray-200 px-3 sm:px-6 py-3 sm:py-4 flex-shrink-0">
        <div className="flex items-center justify-between gap-2 sm:gap-4">
          {/* Order Type Badge */}
          <div className="flex items-center gap-2 sm:gap-3">
            <div className="bg-blue-100 text-blue-700 px-2 sm:px-4 py-1.5 sm:py-2 rounded-lg font-semibold text-sm sm:text-base">
              {currentOrder.type}
            </div>
            <TouchButton
              variant="ghost"
              size="small"
              onClick={() => setCurrentScreen('start')}
              className="hidden sm:flex"
            >
              {t('common.buttons.change', 'Change')}
            </TouchButton>
          </div>

          {/* Search */}
          <div className="flex-1 max-w-xs sm:max-w-md relative">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t('pos.menu.searchPlaceholder', 'Search menu...')}
              className={cn(
                'w-full min-h-[40px] sm:min-h-[48px] pl-10 sm:pl-12 pr-10 sm:pr-12 py-2 sm:py-3',
                'bg-gray-100 rounded-lg',
                'text-sm sm:text-base text-gray-900 placeholder-gray-500',
                'border-2 border-transparent',
                'focus:border-blue-400 focus:bg-white focus:outline-none',
                'transition-colors'
              )}
            />
            <Search className="absolute left-3 sm:left-4 top-1/2 -translate-y-1/2 w-4 sm:w-5 h-4 sm:h-5 text-gray-400" />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-2 sm:right-3 top-1/2 -translate-y-1/2 p-1 hover:bg-gray-200 rounded"
              >
                <X className="w-4 sm:w-5 h-4 sm:h-5 text-gray-500" />
              </button>
            )}
          </div>

          {/* Cart Button */}
          <TouchButton
            variant="primary"
            size="medium"
            onClick={() => setCurrentScreen('cart')}
            className="relative !px-2 sm:!px-4"
          >
            <ShoppingCart className="w-5 sm:w-6 h-5 sm:h-6" />
            <span className="ml-2 hidden sm:inline">{t('pos.cart.viewCart', 'View Cart')}</span>
            {cartItemCount > 0 && (
              <span className="absolute -top-2 -right-2 bg-red-500 text-white text-xs sm:text-sm font-bold rounded-full w-5 sm:w-7 h-5 sm:h-7 flex items-center justify-center">
                {cartItemCount}
              </span>
            )}
          </TouchButton>
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden relative">
        {/* Mobile Category Toggle Button */}
        <button
          onClick={() => setShowMobileCategories(!showMobileCategories)}
          className="lg:hidden fixed bottom-20 left-4 z-40 bg-blue-600 text-white p-3 rounded-full shadow-lg"
        >
          <Grid3x3 className="w-6 h-6" />
        </button>

        {/* Category Sidebar - Desktop */}
        <div className="hidden lg:block w-48 xl:w-64 bg-white border-r-2 border-gray-200 overflow-y-auto flex-shrink-0">
          <div className="p-3 xl:p-4 space-y-2">
            {/* All Items */}
            <button
              onClick={() => handleCategorySelect(null)}
              className={cn(
                'w-full text-left px-3 xl:px-4 py-3 xl:py-4 rounded-lg font-semibold transition-colors',
                'min-h-[48px] xl:min-h-[56px] text-sm xl:text-base',
                !ui.selectedCategory
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-900 hover:bg-gray-200'
              )}
            >
              {t('pos.menu.allCategories', 'All Items')}
              <span className="ml-2 text-xs xl:text-sm opacity-75">
                ({menu.products.length})
              </span>
            </button>

            {/* Categories */}
            {menu.categories.map(category => {
              const categoryProductCount = menu.products.filter(
                p => p.categoryId === category.id
              ).length;

              return (
                <button
                  key={category.id}
                  onClick={() => handleCategorySelect(category.id)}
                  className={cn(
                    'w-full text-left px-3 xl:px-4 py-3 xl:py-4 rounded-lg font-semibold transition-colors',
                    'min-h-[48px] xl:min-h-[56px] text-sm xl:text-base',
                    ui.selectedCategory === category.id
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-900 hover:bg-gray-200'
                  )}
                >
                  {category.name}
                  <span className="ml-2 text-xs xl:text-sm opacity-75">
                    ({categoryProductCount})
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Mobile Category Drawer */}
        {showMobileCategories && (
          <>
            <div
              className="lg:hidden fixed inset-0 bg-black bg-opacity-50 z-40"
              onClick={() => setShowMobileCategories(false)}
            />
            <div className="lg:hidden fixed bottom-0 left-0 right-0 bg-white z-50 rounded-t-2xl max-h-[60vh] overflow-y-auto">
              <div className="p-4 border-b sticky top-0 bg-white flex justify-between items-center">
                <h3 className="font-semibold text-lg">{t('pos.menu.categories', 'Categories')}</h3>
                <button onClick={() => setShowMobileCategories(false)} className="p-2">
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="p-4 space-y-2">
                <button
                  onClick={() => { handleCategorySelect(null); setShowMobileCategories(false); }}
                  className={cn(
                    'w-full text-left px-4 py-3 rounded-lg font-semibold transition-colors',
                    !ui.selectedCategory
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-900'
                  )}
                >
                  {t('pos.menu.allCategories', 'All Items')} ({menu.products.length})
                </button>
                {menu.categories.map(category => {
                  const count = menu.products.filter(p => p.categoryId === category.id).length;
                  return (
                    <button
                      key={category.id}
                      onClick={() => { handleCategorySelect(category.id); setShowMobileCategories(false); }}
                      className={cn(
                        'w-full text-left px-4 py-3 rounded-lg font-semibold transition-colors',
                        ui.selectedCategory === category.id
                          ? 'bg-blue-600 text-white'
                          : 'bg-gray-100 text-gray-900'
                      )}
                    >
                      {category.name} ({count})
                    </button>
                  );
                })}
              </div>
            </div>
          </>
        )}

        {/* Product Grid */}
        <div className="flex-1 overflow-y-auto p-3 sm:p-6">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="w-12 sm:w-16 h-12 sm:h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
                <p className="text-lg sm:text-xl text-gray-600">{t('pos.menu.loadingMenu', 'Loading menu...')}</p>
              </div>
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center px-4">
                <p className="text-xl sm:text-2xl text-gray-500 mb-2">{t('pos.menu.noProducts', 'No products found')}</p>
                <p className="text-sm sm:text-base text-gray-400">{t('pos.menu.tryAdjusting', 'Try adjusting your search or category filter')}</p>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-2 sm:gap-4">
              {filteredProducts.map(product => {
                const availability = productAvailability[product.id];
                return (
                  <ProductCard
                    key={product.id}
                    product={{
                      ...product,
                      available: availability?.available !== false,
                      stockStatus: availability?.stockStatus || 'UNKNOWN',
                      maxQuantityAvailable: availability?.maxQuantityAvailable,
                    }}
                    onSelect={handleProductSelect}
                  />
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Bottom Cart Summary (Sticky) */}
      {cartItemCount > 0 && (
        <div className="bg-white border-t-2 border-gray-200 px-3 sm:px-6 py-3 sm:py-4 flex-shrink-0">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs sm:text-sm text-gray-600">{t('pos.menu.currentOrder', 'Current Order')}</p>
              <p className="text-xl sm:text-2xl font-bold text-gray-900">
                {currentOrder.total.toFixed(2)}
              </p>
            </div>
            <div className="flex items-center gap-2 sm:gap-3">
              <div className="text-right hidden sm:block">
                <p className="text-sm text-gray-600">{t('pos.cart.items', 'Items')}</p>
                <p className="text-xl font-semibold text-gray-900">{cartItemCount}</p>
              </div>
              <TouchButton
                variant="success"
                size="large"
                onClick={() => setCurrentScreen('cart')}
                className="!text-sm sm:!text-base !px-3 sm:!px-6"
              >
                <span className="sm:hidden">{t('pos.cart.review', 'Review')} ({cartItemCount})</span>
                <span className="hidden sm:inline">{t('pos.cart.reviewOrder', 'Review Order')}</span>
              </TouchButton>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MenuSelectionScreen;
