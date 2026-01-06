import React, { useState, useEffect } from 'react';
import { promotionAPI, restaurantAPI, menuAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { useTranslation } from 'react-i18next';
import { Plus, Edit, Trash2, Tag, Ticket, ToggleLeft, ToggleRight, Calendar, Percent, DollarSign, Gift, Package } from 'lucide-react';
import { Link } from 'react-router-dom';

const Promotions = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [promotions, setPromotions] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingPromotion, setEditingPromotion] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const promotionTypes = [
    { value: 'PERCENTAGE', label: t('promotions.types.PERCENTAGE', 'Percentage Off'), icon: Percent },
    { value: 'FIXED_AMOUNT', label: t('promotions.types.FIXED_AMOUNT', 'Fixed Amount Off'), icon: DollarSign },
    { value: 'FREE_ITEM', label: t('promotions.types.FREE_ITEM', 'Free Item'), icon: Gift },
    { value: 'BUY_X_GET_Y', label: t('promotions.types.BUY_X_GET_Y', 'Buy X Get Y'), icon: Package },
  ];

  const promotionScopes = [
    { value: 'ALL', label: t('promotions.scopes.ALL', 'All Products') },
    { value: 'CATEGORY', label: t('promotions.scopes.CATEGORY', 'Specific Categories') },
    { value: 'PRODUCT', label: t('promotions.scopes.PRODUCT', 'Specific Products') },
    { value: 'ORDER_TYPE', label: t('promotions.scopes.ORDER_TYPE', 'Order Type') },
  ];

  const orderTypes = ['DINE_IN', 'TAKEAWAY', 'DELIVERY'];
  const daysOfWeek = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    promotionType: 'PERCENTAGE',
    promotionScope: 'ALL',
    discountValue: 0,
    buyQuantity: null,
    getQuantity: null,
    freeProductId: null,
    startDate: new Date().toISOString().slice(0, 16),
    endDate: '',
    active: true,
    priority: 0,
    stackable: false,
    rule: {
      minOrderAmount: null,
      maxDiscountAmount: null,
      usageLimit: null,
      perCustomerLimit: null,
      minItems: null,
      applicableOrderTypes: [],
      applicableDays: [],
      startTime: null,
      endTime: null,
      firstOrderOnly: false,
    },
    promotionProducts: [],
  });

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
      loadPromotions();
      loadCategories();
      loadProducts();
    }
  }, [selectedRestaurant, currentPage]);

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

  const loadPromotions = async () => {
    try {
      setLoading(true);
      const response = await promotionAPI.getPromotions(selectedRestaurant, { page: currentPage, size: 10 });
      const data = response.data.data || response.data;
      setPromotions(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (error) {
      console.error('Failed to load promotions:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadCategories = async () => {
    try {
      const response = await menuAPI.getCategories(selectedRestaurant);
      setCategories(response.data.data || []);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  };

  const loadProducts = async () => {
    try {
      const response = await menuAPI.getProducts(selectedRestaurant, { page: 0, size: 100 });
      const data = response.data.data || response.data;
      setProducts(data.content || data || []);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const handleSave = async () => {
    try {
      if (!formData.name || formData.discountValue <= 0) {
        alert(t('promotions.messages.fillRequired', 'Please fill in required fields'));
        return;
      }

      setLoading(true);
      const payload = {
        ...formData,
        startDate: formData.startDate ? new Date(formData.startDate).toISOString() : null,
        endDate: formData.endDate ? new Date(formData.endDate).toISOString() : null,
      };

      if (editingPromotion) {
        await promotionAPI.updatePromotion(editingPromotion.id, payload);
        alert(t('promotions.messages.updateSuccess', 'Promotion updated successfully'));
      } else {
        await promotionAPI.createPromotion(selectedRestaurant, payload);
        alert(t('promotions.messages.createSuccess', 'Promotion created successfully'));
      }
      setShowModal(false);
      resetForm();
      loadPromotions();
    } catch (error) {
      console.error('Failed to save promotion:', error);
      alert(t('promotions.messages.saveError', 'Failed to save promotion') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('promotions.messages.confirmDelete', 'Are you sure you want to delete this promotion?'))) {
      return;
    }
    try {
      await promotionAPI.deletePromotion(id);
      alert(t('promotions.messages.deleteSuccess', 'Promotion deleted successfully'));
      loadPromotions();
    } catch (error) {
      console.error('Failed to delete promotion:', error);
      alert(t('promotions.messages.deleteError', 'Failed to delete promotion'));
    }
  };

  const handleToggle = async (id) => {
    try {
      await promotionAPI.togglePromotion(id);
      loadPromotions();
    } catch (error) {
      console.error('Failed to toggle promotion:', error);
    }
  };

  const handleEdit = (promotion) => {
    setEditingPromotion(promotion);
    setFormData({
      name: promotion.name,
      description: promotion.description || '',
      promotionType: promotion.promotionType,
      promotionScope: promotion.promotionScope,
      discountValue: promotion.discountValue,
      buyQuantity: promotion.buyQuantity,
      getQuantity: promotion.getQuantity,
      freeProductId: promotion.freeProductId,
      startDate: promotion.startDate ? promotion.startDate.slice(0, 16) : '',
      endDate: promotion.endDate ? promotion.endDate.slice(0, 16) : '',
      active: promotion.active,
      priority: promotion.priority,
      stackable: promotion.stackable,
      rule: promotion.rule || {
        minOrderAmount: null,
        maxDiscountAmount: null,
        usageLimit: null,
        perCustomerLimit: null,
        minItems: null,
        applicableOrderTypes: [],
        applicableDays: [],
        startTime: null,
        endTime: null,
        firstOrderOnly: false,
      },
      promotionProducts: promotion.promotionProducts || [],
    });
    setShowModal(true);
  };

  const resetForm = () => {
    setEditingPromotion(null);
    setFormData({
      name: '',
      description: '',
      promotionType: 'PERCENTAGE',
      promotionScope: 'ALL',
      discountValue: 0,
      buyQuantity: null,
      getQuantity: null,
      freeProductId: null,
      startDate: new Date().toISOString().slice(0, 16),
      endDate: '',
      active: true,
      priority: 0,
      stackable: false,
      rule: {
        minOrderAmount: null,
        maxDiscountAmount: null,
        usageLimit: null,
        perCustomerLimit: null,
        minItems: null,
        applicableOrderTypes: [],
        applicableDays: [],
        startTime: null,
        endTime: null,
        firstOrderOnly: false,
      },
      promotionProducts: [],
    });
  };

  const getTypeIcon = (type) => {
    const typeInfo = promotionTypes.find(t => t.value === type);
    return typeInfo ? typeInfo.icon : Tag;
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString();
  };

  return (
    <div className="p-6">
      {/* Header */}
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('promotions.title', 'Promotions & Discounts')}</h1>
          <p className="text-gray-500">{t('promotions.subtitle', 'Manage promotional campaigns and discounts')}</p>
        </div>
        <div className="flex gap-3">
          <Link
            to="/coupons"
            className="btn btn-outline flex items-center gap-2"
          >
            <Ticket size={18} />
            {t('promotions.manageCoupons', 'Manage Coupons')}
          </Link>
          <button
            onClick={() => { resetForm(); setShowModal(true); }}
            className="btn btn-primary flex items-center gap-2"
          >
            <Plus size={18} />
            {t('promotions.addPromotion', 'Add Promotion')}
          </button>
        </div>
      </div>

      {/* Restaurant Selector */}
      {restaurants.length > 1 && (
        <div className="mb-6">
          <select
            className="select select-bordered w-full max-w-xs"
            value={selectedRestaurant || ''}
            onChange={(e) => setSelectedRestaurant(Number(e.target.value))}
          >
            {restaurants.map(r => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>
        </div>
      )}

      {/* Promotions List */}
      <div className="bg-white rounded-lg shadow">
        {loading ? (
          <div className="flex justify-center items-center p-12">
            <span className="loading loading-spinner loading-lg"></span>
          </div>
        ) : promotions.length === 0 ? (
          <div className="text-center py-12">
            <Tag className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-2 text-sm font-medium text-gray-900">{t('promotions.noPromotions', 'No promotions')}</h3>
            <p className="mt-1 text-sm text-gray-500">{t('promotions.getStarted', 'Get started by creating a new promotion.')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('promotions.table.name', 'Name')}</th>
                  <th>{t('promotions.table.type', 'Type')}</th>
                  <th>{t('promotions.table.value', 'Value')}</th>
                  <th>{t('promotions.table.dates', 'Valid Period')}</th>
                  <th>{t('promotions.table.usage', 'Usage')}</th>
                  <th>{t('promotions.table.status', 'Status')}</th>
                  <th>{t('common.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody>
                {promotions.map(promo => {
                  const TypeIcon = getTypeIcon(promo.promotionType);
                  return (
                    <tr key={promo.id}>
                      <td>
                        <div className="flex items-center gap-3">
                          <div className="p-2 bg-primary/10 rounded-lg">
                            <TypeIcon className="w-5 h-5 text-primary" />
                          </div>
                          <div>
                            <div className="font-medium">{promo.name}</div>
                            <div className="text-sm text-gray-500">{promo.description}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="badge badge-outline">
                          {t(`promotions.types.${promo.promotionType}`, promo.promotionType)}
                        </span>
                      </td>
                      <td>
                        {promo.promotionType === 'PERCENTAGE' && `${promo.discountValue}%`}
                        {promo.promotionType === 'FIXED_AMOUNT' && `${promo.discountValue}`}
                        {promo.promotionType === 'FREE_ITEM' && promo.freeProductName}
                        {promo.promotionType === 'BUY_X_GET_Y' && `${promo.buyQuantity}+${promo.getQuantity}`}
                      </td>
                      <td>
                        <div className="text-sm">
                          <div>{formatDate(promo.startDate)}</div>
                          <div className="text-gray-500">to {promo.endDate ? formatDate(promo.endDate) : t('promotions.noEnd', 'No end')}</div>
                        </div>
                      </td>
                      <td>
                        <div className="text-sm">
                          <div>{promo.totalUsage || 0} {t('promotions.uses', 'uses')}</div>
                          <div className="text-gray-500">{promo.couponCount || 0} {t('promotions.coupons', 'coupons')}</div>
                        </div>
                      </td>
                      <td>
                        <button
                          onClick={() => handleToggle(promo.id)}
                          className={`badge ${promo.active && promo.currentlyValid ? 'badge-success' : 'badge-ghost'} gap-1`}
                        >
                          {promo.active && promo.currentlyValid ? (
                            <><ToggleRight size={14} /> {t('common.active', 'Active')}</>
                          ) : (
                            <><ToggleLeft size={14} /> {t('common.inactive', 'Inactive')}</>
                          )}
                        </button>
                      </td>
                      <td>
                        <div className="flex gap-2">
                          <button
                            onClick={() => handleEdit(promo)}
                            className="btn btn-ghost btn-sm"
                            title={t('common.edit', 'Edit')}
                          >
                            <Edit size={16} />
                          </button>
                          <Link
                            to={`/coupons?promotionId=${promo.id}`}
                            className="btn btn-ghost btn-sm"
                            title={t('promotions.viewCoupons', 'View Coupons')}
                          >
                            <Ticket size={16} />
                          </Link>
                          <button
                            onClick={() => handleDelete(promo.id)}
                            className="btn btn-ghost btn-sm text-error"
                            title={t('common.delete', 'Delete')}
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex justify-center p-4">
            <div className="join">
              <button
                className="join-item btn btn-sm"
                onClick={() => setCurrentPage(p => Math.max(0, p - 1))}
                disabled={currentPage === 0}
              >
                «
              </button>
              <button className="join-item btn btn-sm">
                {t('common.page', 'Page')} {currentPage + 1} / {totalPages}
              </button>
              <button
                className="join-item btn btn-sm"
                onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={currentPage >= totalPages - 1}
              >
                »
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="modal modal-open">
          <div className="modal-box max-w-4xl">
            <h3 className="font-bold text-lg mb-4">
              {editingPromotion ? t('promotions.editPromotion', 'Edit Promotion') : t('promotions.createPromotion', 'Create Promotion')}
            </h3>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Basic Info */}
              <div className="form-control">
                <label className="label"><span className="label-text">{t('promotions.form.name', 'Promotion Name')} *</span></label>
                <input
                  type="text"
                  className="input input-bordered"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder={t('promotions.form.namePlaceholder', 'e.g., Summer Sale 20% Off')}
                />
              </div>

              <div className="form-control">
                <label className="label"><span className="label-text">{t('promotions.form.type', 'Promotion Type')} *</span></label>
                <select
                  className="select select-bordered"
                  value={formData.promotionType}
                  onChange={(e) => setFormData({ ...formData, promotionType: e.target.value })}
                >
                  {promotionTypes.map(type => (
                    <option key={type.value} value={type.value}>{type.label}</option>
                  ))}
                </select>
              </div>

              <div className="form-control md:col-span-2">
                <label className="label"><span className="label-text">{t('promotions.form.description', 'Description')}</span></label>
                <textarea
                  className="textarea textarea-bordered"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder={t('promotions.form.descriptionPlaceholder', 'Describe the promotion...')}
                />
              </div>

              {/* Discount Value */}
              <div className="form-control">
                <label className="label">
                  <span className="label-text">
                    {formData.promotionType === 'PERCENTAGE' ? t('promotions.form.percentOff', 'Percent Off') : t('promotions.form.amountOff', 'Amount Off')} *
                  </span>
                </label>
                <input
                  type="number"
                  className="input input-bordered"
                  value={formData.discountValue}
                  onChange={(e) => setFormData({ ...formData, discountValue: parseFloat(e.target.value) || 0 })}
                  min="0"
                  max={formData.promotionType === 'PERCENTAGE' ? 100 : undefined}
                />
              </div>

              <div className="form-control">
                <label className="label"><span className="label-text">{t('promotions.form.scope', 'Applies To')}</span></label>
                <select
                  className="select select-bordered"
                  value={formData.promotionScope}
                  onChange={(e) => setFormData({ ...formData, promotionScope: e.target.value })}
                >
                  {promotionScopes.map(scope => (
                    <option key={scope.value} value={scope.value}>{scope.label}</option>
                  ))}
                </select>
              </div>

              {/* BUY_X_GET_Y fields */}
              {formData.promotionType === 'BUY_X_GET_Y' && (
                <>
                  <div className="form-control">
                    <label className="label"><span className="label-text">{t('promotions.form.buyQuantity', 'Buy Quantity')}</span></label>
                    <input
                      type="number"
                      className="input input-bordered"
                      value={formData.buyQuantity || ''}
                      onChange={(e) => setFormData({ ...formData, buyQuantity: parseInt(e.target.value) || null })}
                      min="1"
                    />
                  </div>
                  <div className="form-control">
                    <label className="label"><span className="label-text">{t('promotions.form.getQuantity', 'Get Quantity Free')}</span></label>
                    <input
                      type="number"
                      className="input input-bordered"
                      value={formData.getQuantity || ''}
                      onChange={(e) => setFormData({ ...formData, getQuantity: parseInt(e.target.value) || null })}
                      min="1"
                    />
                  </div>
                </>
              )}

              {/* FREE_ITEM product selector */}
              {formData.promotionType === 'FREE_ITEM' && (
                <div className="form-control md:col-span-2">
                  <label className="label"><span className="label-text">{t('promotions.form.freeProduct', 'Free Product')}</span></label>
                  <select
                    className="select select-bordered"
                    value={formData.freeProductId || ''}
                    onChange={(e) => setFormData({ ...formData, freeProductId: e.target.value ? Number(e.target.value) : null })}
                  >
                    <option value="">{t('common.select', 'Select...')}</option>
                    {products.map(p => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </div>
              )}

              {/* Date Range */}
              <div className="form-control">
                <label className="label"><span className="label-text">{t('promotions.form.startDate', 'Start Date')} *</span></label>
                <input
                  type="datetime-local"
                  className="input input-bordered"
                  value={formData.startDate}
                  onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                />
              </div>

              <div className="form-control">
                <label className="label"><span className="label-text">{t('promotions.form.endDate', 'End Date')}</span></label>
                <input
                  type="datetime-local"
                  className="input input-bordered"
                  value={formData.endDate}
                  onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                />
              </div>

              {/* Rules Section */}
              <div className="md:col-span-2 border-t pt-4 mt-2">
                <h4 className="font-medium mb-3">{t('promotions.form.rules', 'Promotion Rules')}</h4>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="form-control">
                    <label className="label"><span className="label-text">{t('promotions.form.minOrder', 'Min Order Amount')}</span></label>
                    <input
                      type="number"
                      className="input input-bordered input-sm"
                      value={formData.rule.minOrderAmount || ''}
                      onChange={(e) => setFormData({
                        ...formData,
                        rule: { ...formData.rule, minOrderAmount: e.target.value ? parseFloat(e.target.value) : null }
                      })}
                      min="0"
                    />
                  </div>

                  <div className="form-control">
                    <label className="label"><span className="label-text">{t('promotions.form.maxDiscount', 'Max Discount')}</span></label>
                    <input
                      type="number"
                      className="input input-bordered input-sm"
                      value={formData.rule.maxDiscountAmount || ''}
                      onChange={(e) => setFormData({
                        ...formData,
                        rule: { ...formData.rule, maxDiscountAmount: e.target.value ? parseFloat(e.target.value) : null }
                      })}
                      min="0"
                    />
                  </div>

                  <div className="form-control">
                    <label className="label"><span className="label-text">{t('promotions.form.usageLimit', 'Total Usage Limit')}</span></label>
                    <input
                      type="number"
                      className="input input-bordered input-sm"
                      value={formData.rule.usageLimit || ''}
                      onChange={(e) => setFormData({
                        ...formData,
                        rule: { ...formData.rule, usageLimit: e.target.value ? parseInt(e.target.value) : null }
                      })}
                      min="1"
                    />
                  </div>

                  <div className="form-control">
                    <label className="label"><span className="label-text">{t('promotions.form.perCustomerLimit', 'Per Customer Limit')}</span></label>
                    <input
                      type="number"
                      className="input input-bordered input-sm"
                      value={formData.rule.perCustomerLimit || ''}
                      onChange={(e) => setFormData({
                        ...formData,
                        rule: { ...formData.rule, perCustomerLimit: e.target.value ? parseInt(e.target.value) : null }
                      })}
                      min="1"
                    />
                  </div>

                  <div className="form-control">
                    <label className="label cursor-pointer justify-start gap-2">
                      <input
                        type="checkbox"
                        className="checkbox checkbox-sm"
                        checked={formData.rule.firstOrderOnly}
                        onChange={(e) => setFormData({
                          ...formData,
                          rule: { ...formData.rule, firstOrderOnly: e.target.checked }
                        })}
                      />
                      <span className="label-text">{t('promotions.form.firstOrderOnly', 'First Order Only')}</span>
                    </label>
                  </div>

                  <div className="form-control">
                    <label className="label cursor-pointer justify-start gap-2">
                      <input
                        type="checkbox"
                        className="checkbox checkbox-sm"
                        checked={formData.stackable}
                        onChange={(e) => setFormData({ ...formData, stackable: e.target.checked })}
                      />
                      <span className="label-text">{t('promotions.form.stackable', 'Stackable')}</span>
                    </label>
                  </div>
                </div>

                {/* Applicable Order Types */}
                <div className="form-control mt-3">
                  <label className="label"><span className="label-text">{t('promotions.form.orderTypes', 'Applicable Order Types')}</span></label>
                  <div className="flex gap-3 flex-wrap">
                    {orderTypes.map(type => (
                      <label key={type} className="label cursor-pointer gap-2">
                        <input
                          type="checkbox"
                          className="checkbox checkbox-sm"
                          checked={formData.rule.applicableOrderTypes?.includes(type)}
                          onChange={(e) => {
                            const types = formData.rule.applicableOrderTypes || [];
                            setFormData({
                              ...formData,
                              rule: {
                                ...formData.rule,
                                applicableOrderTypes: e.target.checked
                                  ? [...types, type]
                                  : types.filter(t => t !== type)
                              }
                            });
                          }}
                        />
                        <span className="label-text">{t(`orderTypes.${type}`, type)}</span>
                      </label>
                    ))}
                  </div>
                </div>

                {/* Applicable Days */}
                <div className="form-control mt-3">
                  <label className="label"><span className="label-text">{t('promotions.form.applicableDays', 'Applicable Days')}</span></label>
                  <div className="flex gap-2 flex-wrap">
                    {daysOfWeek.map(day => (
                      <label key={day} className="label cursor-pointer gap-1">
                        <input
                          type="checkbox"
                          className="checkbox checkbox-xs"
                          checked={formData.rule.applicableDays?.includes(day)}
                          onChange={(e) => {
                            const days = formData.rule.applicableDays || [];
                            setFormData({
                              ...formData,
                              rule: {
                                ...formData.rule,
                                applicableDays: e.target.checked
                                  ? [...days, day]
                                  : days.filter(d => d !== day)
                              }
                            });
                          }}
                        />
                        <span className="label-text text-xs">{day}</span>
                      </label>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <div className="modal-action">
              <button className="btn btn-ghost" onClick={() => setShowModal(false)}>
                {t('common.cancel', 'Cancel')}
              </button>
              <button
                className="btn btn-primary"
                onClick={handleSave}
                disabled={loading}
              >
                {loading && <span className="loading loading-spinner loading-sm"></span>}
                {editingPromotion ? t('common.save', 'Save') : t('common.create', 'Create')}
              </button>
            </div>
          </div>
          <div className="modal-backdrop" onClick={() => setShowModal(false)}></div>
        </div>
      )}
    </div>
  );
};

export default Promotions;
