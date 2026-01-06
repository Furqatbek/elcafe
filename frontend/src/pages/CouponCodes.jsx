import React, { useState, useEffect } from 'react';
import { promotionAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { useTranslation } from 'react-i18next';
import { useSearchParams, Link } from 'react-router-dom';
import { Plus, Trash2, Copy, ToggleLeft, ToggleRight, Tag, Ticket, Download, RefreshCw } from 'lucide-react';

const CouponCodes = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [searchParams] = useSearchParams();
  const [coupons, setCoupons] = useState([]);
  const [promotions, setPromotions] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [selectedPromotion, setSelectedPromotion] = useState(searchParams.get('promotionId') || '');
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [showGenerateModal, setShowGenerateModal] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formData, setFormData] = useState({
    code: '',
    promotionId: '',
    singleUse: false,
    maxUses: null,
    validFrom: '',
    validUntil: '',
    active: true,
  });

  const [generateData, setGenerateData] = useState({
    promotionId: '',
    count: 10,
    prefix: '',
    codeLength: 8,
    singleUse: true,
    maxUses: null,
    validFrom: '',
    validUntil: '',
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
    }
  }, [selectedRestaurant]);

  useEffect(() => {
    if (selectedRestaurant) {
      loadCoupons();
    }
  }, [selectedRestaurant, selectedPromotion, currentPage]);

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
      const response = await promotionAPI.getPromotions(selectedRestaurant, { page: 0, size: 100 });
      const data = response.data.data || response.data;
      setPromotions(data.content || []);
    } catch (error) {
      console.error('Failed to load promotions:', error);
    }
  };

  const loadCoupons = async () => {
    try {
      setLoading(true);
      let response;
      if (selectedPromotion) {
        response = await promotionAPI.getCouponsByPromotion(selectedPromotion, { page: currentPage, size: 20 });
      } else {
        response = await promotionAPI.getCoupons(selectedRestaurant, { page: currentPage, size: 20 });
      }
      const data = response.data.data || response.data;
      setCoupons(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (error) {
      console.error('Failed to load coupons:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    try {
      if (!formData.code || !formData.promotionId) {
        alert(t('coupons.messages.fillRequired', 'Please fill in required fields'));
        return;
      }

      setLoading(true);
      const payload = {
        ...formData,
        validFrom: formData.validFrom ? new Date(formData.validFrom).toISOString() : null,
        validUntil: formData.validUntil ? new Date(formData.validUntil).toISOString() : null,
      };

      await promotionAPI.createCoupon(payload);
      alert(t('coupons.messages.createSuccess', 'Coupon created successfully'));
      setShowModal(false);
      resetForm();
      loadCoupons();
    } catch (error) {
      console.error('Failed to create coupon:', error);
      alert(t('coupons.messages.createError', 'Failed to create coupon') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    try {
      if (!generateData.promotionId || generateData.count < 1) {
        alert(t('coupons.messages.fillRequired', 'Please fill in required fields'));
        return;
      }

      setLoading(true);
      const payload = {
        ...generateData,
        validFrom: generateData.validFrom ? new Date(generateData.validFrom).toISOString() : null,
        validUntil: generateData.validUntil ? new Date(generateData.validUntil).toISOString() : null,
      };

      const response = await promotionAPI.generateCoupons(payload);
      const generated = response.data.data || response.data;
      alert(t('coupons.messages.generateSuccess', `Successfully generated ${generated.length} coupons`));
      setShowGenerateModal(false);
      resetGenerateForm();
      loadCoupons();
    } catch (error) {
      console.error('Failed to generate coupons:', error);
      alert(t('coupons.messages.generateError', 'Failed to generate coupons') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('coupons.messages.confirmDelete', 'Are you sure you want to delete this coupon?'))) {
      return;
    }
    try {
      await promotionAPI.deleteCoupon(id);
      alert(t('coupons.messages.deleteSuccess', 'Coupon deleted successfully'));
      loadCoupons();
    } catch (error) {
      console.error('Failed to delete coupon:', error);
      alert(t('coupons.messages.deleteError', 'Failed to delete coupon'));
    }
  };

  const handleToggle = async (id) => {
    try {
      await promotionAPI.toggleCoupon(id);
      loadCoupons();
    } catch (error) {
      console.error('Failed to toggle coupon:', error);
    }
  };

  const copyToClipboard = (code) => {
    navigator.clipboard.writeText(code);
    alert(t('coupons.messages.copied', 'Coupon code copied to clipboard'));
  };

  const exportCoupons = () => {
    const csvContent = [
      ['Code', 'Promotion', 'Single Use', 'Max Uses', 'Used Count', 'Valid From', 'Valid Until', 'Active'].join(','),
      ...coupons.map(c => [
        c.code,
        c.promotionName,
        c.singleUse ? 'Yes' : 'No',
        c.maxUses || 'Unlimited',
        c.usedCount,
        c.validFrom || '-',
        c.validUntil || '-',
        c.active ? 'Yes' : 'No'
      ].join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `coupons-${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
  };

  const resetForm = () => {
    setFormData({
      code: '',
      promotionId: selectedPromotion || '',
      singleUse: false,
      maxUses: null,
      validFrom: '',
      validUntil: '',
      active: true,
    });
  };

  const resetGenerateForm = () => {
    setGenerateData({
      promotionId: selectedPromotion || '',
      count: 10,
      prefix: '',
      codeLength: 8,
      singleUse: true,
      maxUses: null,
      validFrom: '',
      validUntil: '',
    });
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
          <h1 className="text-2xl font-bold text-gray-900">{t('coupons.title', 'Coupon Codes')}</h1>
          <p className="text-gray-500">{t('coupons.subtitle', 'Manage coupon codes for promotions')}</p>
        </div>
        <div className="flex gap-3">
          <Link
            to="/promotions"
            className="btn btn-outline flex items-center gap-2"
          >
            <Tag size={18} />
            {t('coupons.managePromotions', 'Manage Promotions')}
          </Link>
          <button
            onClick={exportCoupons}
            className="btn btn-outline flex items-center gap-2"
            disabled={coupons.length === 0}
          >
            <Download size={18} />
            {t('coupons.export', 'Export')}
          </button>
          <button
            onClick={() => { resetGenerateForm(); setShowGenerateModal(true); }}
            className="btn btn-secondary flex items-center gap-2"
          >
            <RefreshCw size={18} />
            {t('coupons.generateBatch', 'Generate Batch')}
          </button>
          <button
            onClick={() => { resetForm(); setShowModal(true); }}
            className="btn btn-primary flex items-center gap-2"
          >
            <Plus size={18} />
            {t('coupons.addCoupon', 'Add Coupon')}
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4 mb-6">
        {restaurants.length > 1 && (
          <select
            className="select select-bordered"
            value={selectedRestaurant || ''}
            onChange={(e) => {
              setSelectedRestaurant(Number(e.target.value));
              setSelectedPromotion('');
              setCurrentPage(0);
            }}
          >
            {restaurants.map(r => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>
        )}

        <select
          className="select select-bordered"
          value={selectedPromotion}
          onChange={(e) => {
            setSelectedPromotion(e.target.value);
            setCurrentPage(0);
          }}
        >
          <option value="">{t('coupons.allPromotions', 'All Promotions')}</option>
          {promotions.map(p => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
      </div>

      {/* Coupons List */}
      <div className="bg-white rounded-lg shadow">
        {loading ? (
          <div className="flex justify-center items-center p-12">
            <span className="loading loading-spinner loading-lg"></span>
          </div>
        ) : coupons.length === 0 ? (
          <div className="text-center py-12">
            <Ticket className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-2 text-sm font-medium text-gray-900">{t('coupons.noCoupons', 'No coupons')}</h3>
            <p className="mt-1 text-sm text-gray-500">{t('coupons.getStarted', 'Get started by creating or generating coupons.')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('coupons.table.code', 'Code')}</th>
                  <th>{t('coupons.table.promotion', 'Promotion')}</th>
                  <th>{t('coupons.table.usage', 'Usage')}</th>
                  <th>{t('coupons.table.validity', 'Validity')}</th>
                  <th>{t('coupons.table.status', 'Status')}</th>
                  <th>{t('common.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody>
                {coupons.map(coupon => (
                  <tr key={coupon.id}>
                    <td>
                      <div className="flex items-center gap-2">
                        <code className="bg-base-200 px-2 py-1 rounded text-sm font-mono">{coupon.code}</code>
                        <button
                          onClick={() => copyToClipboard(coupon.code)}
                          className="btn btn-ghost btn-xs"
                          title={t('coupons.copy', 'Copy')}
                        >
                          <Copy size={14} />
                        </button>
                      </div>
                    </td>
                    <td>
                      <div className="text-sm">
                        <div className="font-medium">{coupon.promotionName}</div>
                      </div>
                    </td>
                    <td>
                      <div className="text-sm">
                        <div>{coupon.usedCount} / {coupon.singleUse ? '1' : (coupon.maxUses || '∞')}</div>
                        <div className="text-gray-500 text-xs">
                          {coupon.singleUse ? t('coupons.singleUse', 'Single use') : t('coupons.multiUse', 'Multi-use')}
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="text-sm">
                        {coupon.validFrom || coupon.validUntil ? (
                          <>
                            <div>{formatDate(coupon.validFrom)} - {formatDate(coupon.validUntil)}</div>
                          </>
                        ) : (
                          <span className="text-gray-500">{t('coupons.noExpiry', 'No expiry')}</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <button
                        onClick={() => handleToggle(coupon.id)}
                        className={`badge ${coupon.active && coupon.currentlyValid ? 'badge-success' : 'badge-ghost'} gap-1`}
                      >
                        {coupon.active && coupon.currentlyValid ? (
                          <><ToggleRight size={14} /> {t('common.active', 'Active')}</>
                        ) : (
                          <><ToggleLeft size={14} /> {t('common.inactive', 'Inactive')}</>
                        )}
                      </button>
                    </td>
                    <td>
                      <div className="flex gap-2">
                        <button
                          onClick={() => copyToClipboard(coupon.code)}
                          className="btn btn-ghost btn-sm"
                          title={t('coupons.copy', 'Copy')}
                        >
                          <Copy size={16} />
                        </button>
                        <button
                          onClick={() => handleDelete(coupon.id)}
                          className="btn btn-ghost btn-sm text-error"
                          title={t('common.delete', 'Delete')}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
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

      {/* Create Single Coupon Modal */}
      {showModal && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg mb-4">{t('coupons.createCoupon', 'Create Coupon')}</h3>

            <div className="space-y-4">
              <div className="form-control">
                <label className="label"><span className="label-text">{t('coupons.form.code', 'Coupon Code')} *</span></label>
                <input
                  type="text"
                  className="input input-bordered uppercase"
                  value={formData.code}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                  placeholder="e.g., SUMMER20"
                />
              </div>

              <div className="form-control">
                <label className="label"><span className="label-text">{t('coupons.form.promotion', 'Promotion')} *</span></label>
                <select
                  className="select select-bordered"
                  value={formData.promotionId}
                  onChange={(e) => setFormData({ ...formData, promotionId: Number(e.target.value) })}
                >
                  <option value="">{t('common.select', 'Select...')}</option>
                  {promotions.map(p => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="form-control">
                  <label className="label cursor-pointer justify-start gap-2">
                    <input
                      type="checkbox"
                      className="checkbox"
                      checked={formData.singleUse}
                      onChange={(e) => setFormData({ ...formData, singleUse: e.target.checked })}
                    />
                    <span className="label-text">{t('coupons.form.singleUse', 'Single Use')}</span>
                  </label>
                </div>

                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.maxUses', 'Max Uses')}</span></label>
                  <input
                    type="number"
                    className="input input-bordered input-sm"
                    value={formData.maxUses || ''}
                    onChange={(e) => setFormData({ ...formData, maxUses: e.target.value ? parseInt(e.target.value) : null })}
                    min="1"
                    disabled={formData.singleUse}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.validFrom', 'Valid From')}</span></label>
                  <input
                    type="datetime-local"
                    className="input input-bordered input-sm"
                    value={formData.validFrom}
                    onChange={(e) => setFormData({ ...formData, validFrom: e.target.value })}
                  />
                </div>

                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.validUntil', 'Valid Until')}</span></label>
                  <input
                    type="datetime-local"
                    className="input input-bordered input-sm"
                    value={formData.validUntil}
                    onChange={(e) => setFormData({ ...formData, validUntil: e.target.value })}
                  />
                </div>
              </div>
            </div>

            <div className="modal-action">
              <button className="btn btn-ghost" onClick={() => setShowModal(false)}>
                {t('common.cancel', 'Cancel')}
              </button>
              <button
                className="btn btn-primary"
                onClick={handleCreate}
                disabled={loading}
              >
                {loading && <span className="loading loading-spinner loading-sm"></span>}
                {t('common.create', 'Create')}
              </button>
            </div>
          </div>
          <div className="modal-backdrop" onClick={() => setShowModal(false)}></div>
        </div>
      )}

      {/* Generate Batch Modal */}
      {showGenerateModal && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg mb-4">{t('coupons.generateBatch', 'Generate Batch Coupons')}</h3>

            <div className="space-y-4">
              <div className="form-control">
                <label className="label"><span className="label-text">{t('coupons.form.promotion', 'Promotion')} *</span></label>
                <select
                  className="select select-bordered"
                  value={generateData.promotionId}
                  onChange={(e) => setGenerateData({ ...generateData, promotionId: Number(e.target.value) })}
                >
                  <option value="">{t('common.select', 'Select...')}</option>
                  {promotions.map(p => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.count', 'Number of Coupons')} *</span></label>
                  <input
                    type="number"
                    className="input input-bordered"
                    value={generateData.count}
                    onChange={(e) => setGenerateData({ ...generateData, count: parseInt(e.target.value) || 1 })}
                    min="1"
                    max="1000"
                  />
                </div>

                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.prefix', 'Code Prefix')}</span></label>
                  <input
                    type="text"
                    className="input input-bordered uppercase"
                    value={generateData.prefix}
                    onChange={(e) => setGenerateData({ ...generateData, prefix: e.target.value.toUpperCase() })}
                    placeholder="e.g., SUMMER"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.codeLength', 'Code Length')}</span></label>
                  <input
                    type="number"
                    className="input input-bordered"
                    value={generateData.codeLength}
                    onChange={(e) => setGenerateData({ ...generateData, codeLength: parseInt(e.target.value) || 8 })}
                    min="4"
                    max="16"
                  />
                </div>

                <div className="form-control">
                  <label className="label cursor-pointer justify-start gap-2">
                    <input
                      type="checkbox"
                      className="checkbox"
                      checked={generateData.singleUse}
                      onChange={(e) => setGenerateData({ ...generateData, singleUse: e.target.checked })}
                    />
                    <span className="label-text">{t('coupons.form.singleUse', 'Single Use')}</span>
                  </label>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.validFrom', 'Valid From')}</span></label>
                  <input
                    type="datetime-local"
                    className="input input-bordered input-sm"
                    value={generateData.validFrom}
                    onChange={(e) => setGenerateData({ ...generateData, validFrom: e.target.value })}
                  />
                </div>

                <div className="form-control">
                  <label className="label"><span className="label-text">{t('coupons.form.validUntil', 'Valid Until')}</span></label>
                  <input
                    type="datetime-local"
                    className="input input-bordered input-sm"
                    value={generateData.validUntil}
                    onChange={(e) => setGenerateData({ ...generateData, validUntil: e.target.value })}
                  />
                </div>
              </div>

              <div className="alert alert-info">
                <span>{t('coupons.generatePreview', `Will generate ${generateData.count} coupons with format: ${generateData.prefix || ''}XXXXXXXX`)}</span>
              </div>
            </div>

            <div className="modal-action">
              <button className="btn btn-ghost" onClick={() => setShowGenerateModal(false)}>
                {t('common.cancel', 'Cancel')}
              </button>
              <button
                className="btn btn-primary"
                onClick={handleGenerate}
                disabled={loading}
              >
                {loading && <span className="loading loading-spinner loading-sm"></span>}
                {t('coupons.generate', 'Generate')}
              </button>
            </div>
          </div>
          <div className="modal-backdrop" onClick={() => setShowGenerateModal(false)}></div>
        </div>
      )}
    </div>
  );
};

export default CouponCodes;
