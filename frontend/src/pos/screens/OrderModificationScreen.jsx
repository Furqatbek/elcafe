import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import {
  ArrowLeft,
  Plus,
  Minus,
  Trash2,
  Search,
  X,
  Save,
  AlertCircle,
  Check
} from 'lucide-react';
import TouchButton from '../components/TouchButton';
import ProductCard from '../components/ProductCard';
import usePOSStore from '../store/posStore';
import { posAPI } from '../../services/api';

/**
 * OrderModificationScreen - Modify an existing order
 * Add new items, remove items, or change quantities
 */
const OrderModificationScreen = () => {
  const { t } = useTranslation();
  const {
    activeOrder,
    menu,
    fetchMenuData,
    productAvailability,
    checkAllProductsAvailability,
    setCurrentScreen,
    ui,
  } = usePOSStore();

  const [order, setOrder] = useState(activeOrder);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [showAddItems, setShowAddItems] = useState(false);
  const [pendingChanges, setPendingChanges] = useState({
    addedItems: [],
    removedItemIds: [],
    quantityChanges: {}, // itemId -> newQuantity
  });

  const restaurantId = 1;

  // Load menu if not already loaded
  useEffect(() => {
    const loadMenu = async () => {
      if (!menu.categories.length || !menu.products.length) {
        setLoading(true);
        const result = await fetchMenuData(restaurantId);
        if (result.success && result.products.length > 0) {
          await checkAllProductsAvailability(result.products, restaurantId);
        }
        setLoading(false);
      }
    };
    loadMenu();
  }, []);

  // Filter products for adding
  const filteredProducts = menu.products.filter(product => {
    const matchesCategory = !selectedCategory || product.categoryId === selectedCategory;
    const matchesSearch = !searchQuery ||
      product.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  // Calculate current order items with pending changes
  const getCurrentItems = () => {
    if (!order?.items) return [];

    return order.items
      .filter(item => !pendingChanges.removedItemIds.includes(item.id))
      .map(item => ({
        ...item,
        quantity: pendingChanges.quantityChanges[item.id] ?? item.quantity,
      }))
      .concat(pendingChanges.addedItems);
  };

  const currentItems = getCurrentItems();

  // Calculate totals
  const calculateTotal = () => {
    return currentItems.reduce((sum, item) => {
      const itemPrice = (item.price || item.basePrice || 0) +
        (item.modifiers?.reduce((ms, m) => ms + (m.price || 0), 0) || 0);
      return sum + (itemPrice * item.quantity);
    }, 0);
  };

  const handleQuantityChange = (itemId, delta) => {
    const item = currentItems.find(i => i.id === itemId);
    if (!item) return;

    const newQuantity = item.quantity + delta;

    if (newQuantity <= 0) {
      // Mark for removal
      if (pendingChanges.addedItems.some(i => i.id === itemId)) {
        // Remove from pending additions
        setPendingChanges(prev => ({
          ...prev,
          addedItems: prev.addedItems.filter(i => i.id !== itemId),
        }));
      } else {
        // Mark original item for removal
        setPendingChanges(prev => ({
          ...prev,
          removedItemIds: [...prev.removedItemIds, itemId],
        }));
      }
    } else {
      if (pendingChanges.addedItems.some(i => i.id === itemId)) {
        // Update pending addition quantity
        setPendingChanges(prev => ({
          ...prev,
          addedItems: prev.addedItems.map(i =>
            i.id === itemId ? { ...i, quantity: newQuantity } : i
          ),
        }));
      } else {
        // Update quantity change for existing item
        setPendingChanges(prev => ({
          ...prev,
          quantityChanges: { ...prev.quantityChanges, [itemId]: newQuantity },
        }));
      }
    }
  };

  const handleRemoveItem = (itemId) => {
    if (pendingChanges.addedItems.some(i => i.id === itemId)) {
      setPendingChanges(prev => ({
        ...prev,
        addedItems: prev.addedItems.filter(i => i.id !== itemId),
      }));
    } else {
      setPendingChanges(prev => ({
        ...prev,
        removedItemIds: [...prev.removedItemIds, itemId],
        quantityChanges: Object.fromEntries(
          Object.entries(prev.quantityChanges).filter(([k]) => k !== String(itemId))
        ),
      }));
    }
  };

  const handleAddProduct = (product) => {
    const newItem = {
      id: `new-${Date.now()}`,
      productId: product.id,
      productName: product.name,
      name: product.name,
      price: product.price,
      basePrice: product.price,
      quantity: 1,
      modifiers: [],
    };

    // Check if product already exists in pending additions
    const existingIndex = pendingChanges.addedItems.findIndex(
      i => i.productId === product.id
    );

    if (existingIndex >= 0) {
      setPendingChanges(prev => ({
        ...prev,
        addedItems: prev.addedItems.map((item, idx) =>
          idx === existingIndex
            ? { ...item, quantity: item.quantity + 1 }
            : item
        ),
      }));
    } else {
      setPendingChanges(prev => ({
        ...prev,
        addedItems: [...prev.addedItems, newItem],
      }));
    }

    // Brief success feedback
    setSuccess(t('pos.modify.itemAdded', 'Item added'));
    setTimeout(() => setSuccess(null), 1500);
  };

  const handleSaveChanges = async () => {
    setSaving(true);
    setError(null);

    try {
      // Apply quantity changes
      for (const [itemId, newQuantity] of Object.entries(pendingChanges.quantityChanges)) {
        await posAPI.updateItemQuantity(order.id, itemId, newQuantity);
      }

      // Remove items
      for (const itemId of pendingChanges.removedItemIds) {
        await posAPI.removeItemFromOrder(order.id, itemId);
      }

      // Add new items
      for (const item of pendingChanges.addedItems) {
        await posAPI.addItemToOrder(order.id, {
          productId: item.productId,
          quantity: item.quantity,
          price: item.price,
          modifiers: item.modifiers || [],
          notes: item.notes || '',
        });
      }

      setSuccess(t('pos.modify.changesSaved', 'Changes saved successfully'));
      setTimeout(() => {
        setCurrentScreen('active-orders');
      }, 1500);
    } catch (err) {
      console.error('Failed to save changes:', err);
      setError(err.response?.data?.message || t('pos.modify.saveError', 'Failed to save changes'));
    } finally {
      setSaving(false);
    }
  };

  const hasChanges = pendingChanges.addedItems.length > 0 ||
    pendingChanges.removedItemIds.length > 0 ||
    Object.keys(pendingChanges.quantityChanges).length > 0;

  if (!order) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <AlertCircle className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-xl text-gray-600">{t('pos.modify.noOrderSelected', 'No order selected')}</p>
          <TouchButton
            variant="primary"
            className="mt-4"
            onClick={() => setCurrentScreen('active-orders')}
          >
            {t('pos.orders.backToOrders', 'Back to Orders')}
          </TouchButton>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-6 py-4 flex-shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <TouchButton
              variant="ghost"
              size="medium"
              onClick={() => setCurrentScreen('active-orders')}
            >
              <ArrowLeft className="w-5 h-5" />
            </TouchButton>
            <div>
              <h1 className="text-2xl font-bold text-gray-900">
                {t('pos.modify.title', 'Modify Order')} #{order.orderNumber}
              </h1>
              <p className="text-sm text-gray-500">
                {t('pos.orders.table', 'Table')} {order.tableNumber}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <TouchButton
              variant="secondary"
              size="medium"
              onClick={() => setShowAddItems(!showAddItems)}
            >
              <Plus className="w-5 h-5 mr-2" />
              {t('pos.modify.addItems', 'Add Items')}
            </TouchButton>

            <TouchButton
              variant="success"
              size="medium"
              onClick={handleSaveChanges}
              disabled={!hasChanges || saving}
            >
              {saving ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin mr-2" />
              ) : (
                <Save className="w-5 h-5 mr-2" />
              )}
              {t('pos.modify.saveChanges', 'Save Changes')}
            </TouchButton>
          </div>
        </div>
      </div>

      {/* Success/Error Messages */}
      {success && (
        <div className="bg-green-100 border-l-4 border-green-500 text-green-700 p-4 flex items-center gap-2">
          <Check className="w-5 h-5" />
          {success}
        </div>
      )}
      {error && (
        <div className="bg-red-100 border-l-4 border-red-500 text-red-700 p-4 flex items-center gap-2">
          <AlertCircle className="w-5 h-5" />
          {error}
          <button onClick={() => setError(null)} className="ml-auto">
            <X className="w-5 h-5" />
          </button>
        </div>
      )}

      {/* Main Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Current Order Items */}
        <div className="flex-1 overflow-y-auto p-6">
          <h2 className="text-lg font-bold text-gray-900 mb-4">
            {t('pos.modify.currentItems', 'Current Items')}
          </h2>

          <div className="space-y-3">
            {currentItems.map(item => {
              const isRemoved = pendingChanges.removedItemIds.includes(item.id);
              const isNew = pendingChanges.addedItems.some(i => i.id === item.id);
              const isModified = pendingChanges.quantityChanges[item.id] !== undefined;

              if (isRemoved) return null;

              return (
                <div
                  key={item.id}
                  className={cn(
                    'bg-white rounded-xl border-2 p-4 flex items-center gap-4',
                    isNew ? 'border-green-400 bg-green-50' : 'border-gray-200',
                    isModified && 'border-yellow-400 bg-yellow-50'
                  )}
                >
                  {/* Item Info */}
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <h3 className="font-semibold text-gray-900">
                        {item.productName || item.name}
                      </h3>
                      {isNew && (
                        <span className="bg-green-500 text-white text-xs px-2 py-0.5 rounded-full">
                          {t('pos.modify.new', 'NEW')}
                        </span>
                      )}
                      {isModified && (
                        <span className="bg-yellow-500 text-white text-xs px-2 py-0.5 rounded-full">
                          {t('pos.modify.modified', 'MODIFIED')}
                        </span>
                      )}
                    </div>
                    {item.modifiers?.length > 0 && (
                      <p className="text-sm text-gray-500">
                        {item.modifiers.map(m => m.name).join(', ')}
                      </p>
                    )}
                    <p className="text-lg font-bold text-gray-900 mt-1">
                      {((item.price || item.basePrice || 0) * item.quantity).toFixed(2)}
                    </p>
                  </div>

                  {/* Quantity Controls */}
                  <div className="flex items-center gap-2">
                    <TouchButton
                      variant="ghost"
                      size="small"
                      onClick={() => handleQuantityChange(item.id, -1)}
                      className="w-12 h-12 rounded-full"
                    >
                      <Minus className="w-5 h-5" />
                    </TouchButton>
                    <span className="w-12 text-center text-xl font-bold">
                      {item.quantity}
                    </span>
                    <TouchButton
                      variant="ghost"
                      size="small"
                      onClick={() => handleQuantityChange(item.id, 1)}
                      className="w-12 h-12 rounded-full"
                    >
                      <Plus className="w-5 h-5" />
                    </TouchButton>
                  </div>

                  {/* Remove Button */}
                  <TouchButton
                    variant="ghost"
                    size="small"
                    onClick={() => handleRemoveItem(item.id)}
                    className="text-red-500 hover:bg-red-50"
                  >
                    <Trash2 className="w-5 h-5" />
                  </TouchButton>
                </div>
              );
            })}

            {currentItems.length === 0 && (
              <div className="text-center py-8 text-gray-500">
                <AlertCircle className="w-12 h-12 mx-auto mb-2 opacity-50" />
                <p>{t('pos.modify.noItems', 'No items in order')}</p>
              </div>
            )}
          </div>

          {/* Order Total */}
          <div className="mt-6 bg-white rounded-xl border-2 border-gray-200 p-4">
            <div className="flex justify-between items-center">
              <span className="text-lg font-medium text-gray-700">
                {t('pos.cart.total', 'Total')}
              </span>
              <span className="text-2xl font-bold text-gray-900">
                {calculateTotal().toFixed(2)}
              </span>
            </div>
            {order.total !== calculateTotal() && (
              <p className="text-sm text-gray-500 mt-1">
                {t('pos.modify.originalTotal', 'Original')}: {order.total?.toFixed(2)}
              </p>
            )}
          </div>
        </div>

        {/* Add Items Panel */}
        {showAddItems && (
          <div className="w-96 bg-white border-l-2 border-gray-200 flex flex-col">
            <div className="p-4 border-b border-gray-200">
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-bold text-gray-900">
                  {t('pos.modify.addItems', 'Add Items')}
                </h3>
                <button onClick={() => setShowAddItems(false)}>
                  <X className="w-5 h-5 text-gray-500" />
                </button>
              </div>
              <div className="relative">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder={t('pos.menu.searchPlaceholder', 'Search...')}
                  className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg"
                />
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              </div>
            </div>

            {/* Categories */}
            <div className="p-2 border-b border-gray-200 flex gap-1 flex-wrap">
              <button
                onClick={() => setSelectedCategory(null)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm font-medium',
                  !selectedCategory ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700'
                )}
              >
                {t('pos.menu.all', 'All')}
              </button>
              {menu.categories.slice(0, 5).map(cat => (
                <button
                  key={cat.id}
                  onClick={() => setSelectedCategory(cat.id)}
                  className={cn(
                    'px-3 py-1 rounded-full text-sm font-medium',
                    selectedCategory === cat.id ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700'
                  )}
                >
                  {cat.name}
                </button>
              ))}
            </div>

            {/* Products List */}
            <div className="flex-1 overflow-y-auto p-2">
              {loading ? (
                <div className="flex items-center justify-center h-32">
                  <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin" />
                </div>
              ) : (
                <div className="space-y-2">
                  {filteredProducts.map(product => {
                    const availability = productAvailability[product.id];
                    return (
                      <button
                        key={product.id}
                        onClick={() => handleAddProduct(product)}
                        disabled={availability?.available === false}
                        className={cn(
                          'w-full text-left p-3 rounded-lg border-2 transition-all',
                          availability?.available === false
                            ? 'bg-gray-100 border-gray-200 opacity-50 cursor-not-allowed'
                            : 'bg-white border-gray-200 hover:border-blue-400 hover:shadow-md'
                        )}
                      >
                        <div className="flex justify-between items-center">
                          <span className="font-medium text-gray-900">
                            {product.name}
                          </span>
                          <span className="font-bold text-gray-900">
                            {product.price?.toFixed(2)}
                          </span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default OrderModificationScreen;
