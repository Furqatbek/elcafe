import React, { useState, useEffect } from 'react';
import { poSuggestionAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Package, ShoppingCart, CheckCircle, RefreshCw } from 'lucide-react';

const POSuggestions = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [suggestions, setSuggestions] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(null);

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (user?.restaurantId && !selectedRestaurant) {
      setSelectedRestaurant(user.restaurantId);
    }
  }, [user]);

  useEffect(() => {
    if (selectedRestaurant) {
      loadSuggestions(selectedRestaurant);
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantsData = response.data.data?.content || response.data.data || [];
      setRestaurants(Array.isArray(restaurantsData) ? restaurantsData : []);

      if (!selectedRestaurant && restaurantsData.length > 0 && !user?.restaurantId) {
        setSelectedRestaurant(restaurantsData[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadSuggestions = async (restaurantId) => {
    try {
      setLoading(true);
      const response = await poSuggestionAPI.getSuggestions(restaurantId);
      setSuggestions(response.data.data || []);
    } catch (error) {
      console.error('Failed to load suggestions:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleGeneratePO = async (suggestion) => {
    try {
      setGenerating(suggestion.supplierId);
      await poSuggestionAPI.generate({
        restaurantId: selectedRestaurant,
        supplierId: suggestion.supplierId
      });
      alert(t('poSuggestions.messages.poCreated'));
      loadSuggestions(selectedRestaurant);
    } catch (error) {
      console.error('Failed to generate PO:', error);
      alert(t('poSuggestions.messages.poCreateError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setGenerating(null);
    }
  };

  const handleGenerateAll = async () => {
    if (!confirm(t('poSuggestions.confirmGenerateAll'))) return;

    try {
      setGenerating('all');
      await poSuggestionAPI.generateAll(selectedRestaurant);
      alert(t('poSuggestions.messages.allPOsCreated'));
      loadSuggestions(selectedRestaurant);
    } catch (error) {
      console.error('Failed to generate all POs:', error);
      alert(t('poSuggestions.messages.poCreateError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setGenerating(null);
    }
  };

  const getUrgencyBadge = (urgency) => {
    const styles = {
      CRITICAL: 'bg-red-100 text-red-800 border-red-300',
      HIGH: 'bg-orange-100 text-orange-800 border-orange-300',
      MEDIUM: 'bg-yellow-100 text-yellow-800 border-yellow-300'
    };
    const icons = {
      CRITICAL: <AlertTriangle className="w-4 h-4" />,
      HIGH: <AlertTriangle className="w-4 h-4" />,
      MEDIUM: <Package className="w-4 h-4" />
    };
    return (
      <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium border ${styles[urgency]}`}>
        {icons[urgency]}
        {t(`poSuggestions.urgency.${urgency}`)}
      </span>
    );
  };

  const formatCurrency = (amount) => {
    if (amount == null) return '-';
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
  };

  const totalItems = suggestions.reduce((sum, s) => sum + s.itemCount, 0);
  const totalEstimatedCost = suggestions.reduce((sum, s) => sum + (s.estimatedTotal || 0), 0);

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">{t('poSuggestions.title')}</h1>
          <p className="text-gray-500 mt-1">{t('poSuggestions.subtitle')}</p>
        </div>
        {suggestions.length > 0 && (
          <button
            onClick={handleGenerateAll}
            disabled={generating !== null}
            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
          >
            {generating === 'all' ? (
              <RefreshCw className="w-5 h-5 animate-spin" />
            ) : (
              <ShoppingCart className="w-5 h-5" />
            )}
            {t('poSuggestions.createAllPOs')}
          </button>
        )}
      </div>

      {/* Restaurant Filter */}
      <div className="mb-6">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => setSelectedRestaurant(e.target.value ? parseInt(e.target.value) : null)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">{t('common.selectRestaurant')}</option>
          {restaurants.map(restaurant => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
      </div>

      {/* Summary Stats */}
      {suggestions.length > 0 && (
        <div className="grid grid-cols-3 gap-4 mb-6">
          <div className="bg-white rounded-lg shadow p-4">
            <div className="text-sm text-gray-500">{t('poSuggestions.totalSuppliers')}</div>
            <div className="text-2xl font-bold text-blue-600">{suggestions.length}</div>
          </div>
          <div className="bg-white rounded-lg shadow p-4">
            <div className="text-sm text-gray-500">{t('poSuggestions.totalItems')}</div>
            <div className="text-2xl font-bold text-orange-600">{totalItems}</div>
          </div>
          <div className="bg-white rounded-lg shadow p-4">
            <div className="text-sm text-gray-500">{t('poSuggestions.estimatedCost')}</div>
            <div className="text-2xl font-bold text-green-600">{formatCurrency(totalEstimatedCost)}</div>
          </div>
        </div>
      )}

      {/* Loading State */}
      {loading && (
        <div className="flex justify-center items-center py-12">
          <RefreshCw className="w-8 h-8 animate-spin text-blue-500" />
        </div>
      )}

      {/* Empty State */}
      {!loading && suggestions.length === 0 && (
        <div className="text-center py-12 bg-white rounded-lg shadow">
          <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
          <h3 className="text-xl font-semibold text-gray-700">{t('poSuggestions.noSuggestions')}</h3>
          <p className="text-gray-500 mt-2">{t('poSuggestions.allStocked')}</p>
        </div>
      )}

      {/* Suggestions List */}
      {!loading && suggestions.length > 0 && (
        <div className="space-y-4">
          {suggestions.map((suggestion) => (
            <div key={suggestion.supplierId} className="bg-white rounded-lg shadow overflow-hidden">
              {/* Supplier Header */}
              <div className="p-4 border-b bg-gray-50 flex justify-between items-start">
                <div>
                  <div className="flex items-center gap-3">
                    <h3 className="text-lg font-semibold">{suggestion.supplierName}</h3>
                    {suggestion.supplierCode && (
                      <span className="text-sm text-gray-500">({suggestion.supplierCode})</span>
                    )}
                    {getUrgencyBadge(suggestion.urgency)}
                  </div>
                  <div className="text-sm text-gray-500 mt-1">
                    {suggestion.supplierContact && <span className="mr-4">{suggestion.supplierContact}</span>}
                    {suggestion.supplierPaymentTerms && (
                      <span className="text-blue-600">{t('poSuggestions.paymentTerms')}: {suggestion.supplierPaymentTerms}</span>
                    )}
                  </div>
                </div>
                <button
                  onClick={() => handleGeneratePO(suggestion)}
                  disabled={generating !== null}
                  className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                >
                  {generating === suggestion.supplierId ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <ShoppingCart className="w-4 h-4" />
                  )}
                  {t('poSuggestions.createPO')}
                </button>
              </div>

              {/* Items Table */}
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('poSuggestions.ingredient')}</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('poSuggestions.sku')}</th>
                      <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('poSuggestions.currentStock')}</th>
                      <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('poSuggestions.reorderLevel')}</th>
                      <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('poSuggestions.suggestedQty')}</th>
                      <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('poSuggestions.unitPrice')}</th>
                      <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('poSuggestions.estimatedCost')}</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {suggestion.items.map((item) => (
                      <tr key={item.ingredientId} className="hover:bg-gray-50">
                        <td className="px-4 py-3 text-sm font-medium text-gray-900">{item.ingredientName}</td>
                        <td className="px-4 py-3 text-sm text-gray-500">{item.sku || '-'}</td>
                        <td className="px-4 py-3 text-sm text-right">
                          <span className={item.currentStock <= item.minimumStock ? 'text-red-600 font-semibold' : ''}>
                            {item.currentStock} {item.unit}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm text-right text-gray-500">{item.reorderLevel} {item.unit}</td>
                        <td className="px-4 py-3 text-sm text-right font-semibold text-blue-600">{item.suggestedQuantity} {item.unit}</td>
                        <td className="px-4 py-3 text-sm text-right text-gray-500">{formatCurrency(item.costPerUnit)}</td>
                        <td className="px-4 py-3 text-sm text-right font-semibold">{formatCurrency(item.estimatedCost)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot className="bg-gray-50">
                    <tr>
                      <td colSpan="5" className="px-4 py-3 text-sm font-semibold text-right">
                        {suggestion.itemCount} {t('poSuggestions.itemsNeedReorder')}
                      </td>
                      <td className="px-4 py-3 text-sm font-semibold text-right">{t('poSuggestions.total')}:</td>
                      <td className="px-4 py-3 text-sm font-bold text-right text-green-600">{formatCurrency(suggestion.estimatedTotal)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default POSuggestions;
