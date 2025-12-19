import React, { useState, useEffect } from 'react';
import { financialAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { useTranslation } from 'react-i18next';
import { Plus, Edit, Trash2, Check, DollarSign, Calendar } from 'lucide-react';

const Expenses = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [expenses, setExpenses] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedExpense, setSelectedExpense] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterCategory, setFilterCategory] = useState('all');
  const [filterStatus, setFilterStatus] = useState('all');
  const [dateRange, setDateRange] = useState({
    startDate: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0]
  });

  const [formData, setFormData] = useState({
    restaurantId: '',
    expenseDate: new Date().toISOString().split('T')[0],
    category: 'SUPPLIES',
    description: '',
    vendor: '',
    amount: 0,
    taxAmount: 0,
    paymentMethod: 'BANK_TRANSFER',
    referenceNumber: '',
    notes: '',
    recurring: false,
    recurringPeriod: null
  });

  const expenseCategories = [
    { value: 'RENT', label: t('finance.expenses.categories.RENT') },
    { value: 'UTILITIES', label: t('finance.expenses.categories.UTILITIES') },
    { value: 'SUPPLIES', label: t('finance.expenses.categories.SUPPLIES') },
    { value: 'MARKETING', label: t('finance.expenses.categories.MARKETING') },
    { value: 'INSURANCE', label: t('finance.expenses.categories.INSURANCE') },
    { value: 'MAINTENANCE', label: t('finance.expenses.categories.MAINTENANCE') },
    { value: 'EQUIPMENT', label: t('finance.expenses.categories.EQUIPMENT') },
    { value: 'LICENSES', label: t('finance.expenses.categories.LICENSES') },
    { value: 'TAXES', label: t('finance.expenses.categories.TAXES') },
    { value: 'DELIVERY_COSTS', label: t('finance.expenses.categories.DELIVERY_COSTS') },
    { value: 'PROFESSIONAL_FEES', label: t('finance.expenses.categories.PROFESSIONAL_FEES') },
    { value: 'BANK_FEES', label: t('finance.expenses.categories.BANK_FEES') },
    { value: 'OTHER', label: t('finance.expenses.categories.OTHER') }
  ];

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (user?.restaurantId && !selectedRestaurant) {
      setSelectedRestaurant(user.restaurantId);
      setFormData(prev => ({ ...prev, restaurantId: user.restaurantId }));
    }
  }, [user]);

  useEffect(() => {
    if (selectedRestaurant) {
      loadExpenses(selectedRestaurant);
    }
  }, [selectedRestaurant, dateRange]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll();
      setRestaurants(response.data.data || []);
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadExpenses = async (restaurantId) => {
    try {
      setLoading(true);
      const response = await financialAPI.getExpenses(
        restaurantId,
        dateRange.startDate,
        dateRange.endDate
      );
      setExpenses(response.data.data || []);
    } catch (error) {
      console.error('Failed to load expenses:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    try {
      if (!formData.description || !formData.amount) {
        alert(t('finance.common.fillRequiredFields'));
        return;
      }

      setLoading(true);
      await financialAPI.createExpense(formData);
      alert(t('finance.expenses.messages.createSuccess'));
      setShowModal(false);
      resetForm();
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to create expense:', error);
      alert(t('finance.expenses.messages.createError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      await financialAPI.approveExpense(id, user?.username || 'ADMIN');
      alert(t('finance.expenses.messages.approveSuccess'));
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to approve expense:', error);
      alert(t('finance.expenses.messages.approveError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleRecordPayment = async () => {
    try {
      setLoading(true);
      const paymentDate = new Date().toISOString().split('T')[0];
      const recordedBy = user?.username || 'ADMIN';
      await financialAPI.recordExpensePayment(selectedExpense.id, paymentDate, recordedBy);
      alert(t('finance.expenses.messages.paymentSuccess'));
      setShowPaymentModal(false);
      setSelectedExpense(null);
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to record payment:', error);
      alert(t('finance.expenses.messages.paymentError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('finance.expenses.messages.confirmDelete'))) {
      return;
    }

    try {
      await financialAPI.deleteExpense(id);
      alert(t('finance.expenses.messages.deleteSuccess'));
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to delete expense:', error);
      alert(t('finance.expenses.messages.deleteError') + ': ' + (error.response?.data?.message || t('finance.expenses.messages.cannotDeletePaid')));
    }
  };

  const resetForm = () => {
    setFormData({
      restaurantId: selectedRestaurant,
      expenseDate: new Date().toISOString().split('T')[0],
      category: 'SUPPLIES',
      description: '',
      vendor: '',
      amount: 0,
      taxAmount: 0,
      paymentMethod: 'BANK_TRANSFER',
      referenceNumber: '',
      notes: '',
      recurring: false,
      recurringPeriod: null
    });
  };

  const getStatusBadge = (status) => {
    const statusColors = {
      UNPAID: 'bg-red-100 text-red-800',
      PAID: 'bg-green-100 text-green-800',
      PARTIALLY_PAID: 'bg-yellow-100 text-yellow-800',
      OVERDUE: 'bg-red-200 text-red-900'
    };
    return <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[status] || 'bg-gray-100 text-gray-800'}`}>
      {status?.replace(/_/g, ' ')}
    </span>;
  };

  const getCategoryBadge = (category) => {
    const categoryColors = {
      RENT: 'bg-purple-100 text-purple-800',
      UTILITIES: 'bg-blue-100 text-blue-800',
      SUPPLIES: 'bg-green-100 text-green-800',
      MARKETING: 'bg-pink-100 text-pink-800',
      INSURANCE: 'bg-indigo-100 text-indigo-800',
      MAINTENANCE: 'bg-orange-100 text-orange-800',
      EQUIPMENT: 'bg-teal-100 text-teal-800',
      LICENSES: 'bg-cyan-100 text-cyan-800',
      TAXES: 'bg-red-100 text-red-800',
      DELIVERY_COSTS: 'bg-yellow-100 text-yellow-800',
      PROFESSIONAL_FEES: 'bg-gray-100 text-gray-800',
      BANK_FEES: 'bg-slate-100 text-slate-800',
      OTHER: 'bg-gray-100 text-gray-800'
    };
    return <span className={`px-2 py-1 rounded-full text-xs font-medium ${categoryColors[category] || 'bg-gray-100 text-gray-800'}`}>
      {category?.replace(/_/g, ' ')}
    </span>;
  };

  const filteredExpenses = (Array.isArray(expenses) ? expenses : []).filter(expense => {
    const matchesSearch = expense.description?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         expense.vendor?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         expense.expenseNumber?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesCategory = filterCategory === 'all' || expense.category === filterCategory;
    const matchesStatus = filterStatus === 'all' || expense.paymentStatus === filterStatus;
    return matchesSearch && matchesCategory && matchesStatus;
  });

  const totalExpenses = (Array.isArray(filteredExpenses) ? filteredExpenses : []).reduce((sum, exp) => sum + (exp.totalAmount || 0), 0);
  const unpaidExpenses = (Array.isArray(filteredExpenses) ? filteredExpenses : []).filter(exp => exp.paymentStatus === 'UNPAID')
    .reduce((sum, exp) => sum + (exp.totalAmount || 0), 0);

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">{t('finance.expenses.title')}</h1>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          {t('finance.expenses.createNew')}
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">{t('finance.expenses.totalExpenses')}</div>
          <div className="text-2xl font-bold text-gray-900">${totalExpenses.toFixed(2)}</div>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">{t('finance.expenses.unpaidExpenses')}</div>
          <div className="text-2xl font-bold text-red-600">${unpaidExpenses.toFixed(2)}</div>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">{t('finance.expenses.totalItems')}</div>
          <div className="text-2xl font-bold text-gray-900">{filteredExpenses.length}</div>
        </div>
      </div>

      {/* Filters */}
      <div className="mb-4 grid grid-cols-6 gap-4">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => {
            const restaurantId = e.target.value ? parseInt(e.target.value) : null;
            setSelectedRestaurant(restaurantId);
            setFormData(prev => ({ ...prev, restaurantId }));
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">{t('finance.common.selectRestaurant')}</option>
          {(Array.isArray(restaurants) ? restaurants : []).map(restaurant => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
        <input
          type="text"
          placeholder={t('finance.expenses.searchPlaceholder')}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="col-span-2 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <select
          value={filterCategory}
          onChange={(e) => setFilterCategory(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">{t('finance.expenses.allCategories')}</option>
          {expenseCategories.map(cat => (
            <option key={cat.value} value={cat.value}>{cat.label}</option>
          ))}
        </select>
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">{t('finance.expenses.allStatuses')}</option>
          <option value="UNPAID">{t('finance.expenses.statuses.UNPAID')}</option>
          <option value="PAID">{t('finance.expenses.statuses.PAID')}</option>
          <option value="OVERDUE">{t('finance.expenses.statuses.OVERDUE')}</option>
        </select>
        <div className="flex gap-2">
          <input
            type="date"
            value={dateRange.startDate}
            onChange={(e) => setDateRange({ ...dateRange, startDate: e.target.value })}
            className="flex-1 px-2 py-2 border border-gray-300 rounded-lg text-sm"
          />
          <input
            type="date"
            value={dateRange.endDate}
            onChange={(e) => setDateRange({ ...dateRange, endDate: e.target.value })}
            className="flex-1 px-2 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
      </div>

      {/* Expenses List */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.expenses.expenseNumber')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.date')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.expenses.category')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.expenses.description')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.expenses.vendor')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.expenses.amount')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.expenses.status')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.actions')}</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                <td colSpan="8" className="px-6 py-4 text-center text-gray-500">{t('finance.expenses.loading')}</td>
              </tr>
            ) : filteredExpenses.length === 0 ? (
              <tr>
                <td colSpan="8" className="px-6 py-4 text-center text-gray-500">{t('finance.expenses.noExpenses')}</td>
              </tr>
            ) : (
              filteredExpenses.map((expense) => (
                <tr key={expense.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{expense.expenseNumber}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{expense.expenseDate}</td>
                  <td className="px-6 py-4 whitespace-nowrap">{getCategoryBadge(expense.category)}</td>
                  <td className="px-6 py-4 text-sm text-gray-900">{expense.description}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{expense.vendor || '-'}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">${expense.totalAmount?.toFixed(2)}</td>
                  <td className="px-6 py-4 whitespace-nowrap">{getStatusBadge(expense.paymentStatus)}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <div className="flex gap-2">
                      {!expense.approvedBy && (
                        <button
                          onClick={() => handleApprove(expense.id)}
                          className="text-green-600 hover:text-green-900"
                          title="Approve"
                        >
                          <Check size={18} />
                        </button>
                      )}
                      {expense.paymentStatus === 'UNPAID' && expense.approvedBy && (
                        <button
                          onClick={() => { setSelectedExpense(expense); setShowPaymentModal(true); }}
                          className="text-blue-600 hover:text-blue-900"
                          title="Record Payment"
                        >
                          <DollarSign size={18} />
                        </button>
                      )}
                      {expense.paymentStatus === 'UNPAID' && (
                        <button
                          onClick={() => handleDelete(expense.id)}
                          className="text-red-600 hover:text-red-900"
                          title="Delete"
                        >
                          <Trash2 size={18} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 overflow-y-auto">
          <div className="bg-white rounded-lg p-6 w-full max-w-2xl my-8">
            <h2 className="text-xl font-bold mb-4">{t('finance.expenses.createNew')}</h2>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.expenseDate')} *</label>
                <input
                  type="date"
                  value={formData.expenseDate}
                  onChange={(e) => setFormData({ ...formData, expenseDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.category')} *</label>
                <select
                  value={formData.category}
                  onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                >
                  {expenseCategories.map(cat => (
                    <option key={cat.value} value={cat.value}>{cat.label}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.description')} *</label>
              <input
                type="text"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                placeholder={t('finance.expenses.description')}
              />
            </div>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.vendor')}</label>
                <input
                  type="text"
                  value={formData.vendor}
                  onChange={(e) => setFormData({ ...formData, vendor: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.referenceNumber')}</label>
                <input
                  type="text"
                  value={formData.referenceNumber}
                  onChange={(e) => setFormData({ ...formData, referenceNumber: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  placeholder={t('finance.expenses.referenceNumber')}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.amount')} *</label>
                <input
                  type="number"
                  value={formData.amount}
                  onChange={(e) => setFormData({ ...formData, amount: parseFloat(e.target.value) })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  min="0"
                  step="0.01"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.taxAmount')}</label>
                <input
                  type="number"
                  value={formData.taxAmount}
                  onChange={(e) => setFormData({ ...formData, taxAmount: parseFloat(e.target.value) })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  min="0"
                  step="0.01"
                />
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.paymentMethod')}</label>
              <select
                value={formData.paymentMethod}
                onChange={(e) => setFormData({ ...formData, paymentMethod: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
              >
                <option value="CASH">{t('finance.expenses.paymentMethods.CASH')}</option>
                <option value="CARD">{t('finance.expenses.paymentMethods.CARD')}</option>
                <option value="BANK_TRANSFER">{t('finance.expenses.paymentMethods.BANK_TRANSFER')}</option>
                <option value="CHECK">{t('finance.expenses.paymentMethods.CHECK')}</option>
                <option value="OTHER">{t('finance.expenses.paymentMethods.OTHER')}</option>
              </select>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.expenses.notes')}</label>
              <textarea
                value={formData.notes}
                onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                rows="3"
              />
            </div>

            <div className="mb-4 flex items-center gap-2">
              <input
                type="checkbox"
                checked={formData.recurring}
                onChange={(e) => setFormData({ ...formData, recurring: e.target.checked })}
                className="h-4 w-4 text-blue-600 rounded"
              />
              <label className="text-sm font-medium text-gray-700">{t('finance.expenses.recurring')}</label>
              {formData.recurring && (
                <select
                  value={formData.recurringPeriod || ''}
                  onChange={(e) => setFormData({ ...formData, recurringPeriod: e.target.value })}
                  className="ml-4 px-3 py-1 border border-gray-300 rounded-lg text-sm"
                >
                  <option value="">{t('finance.expenses.recurringPeriod')}</option>
                  <option value="WEEKLY">{t('finance.expenses.recurringPeriods.WEEKLY')}</option>
                  <option value="MONTHLY">{t('finance.expenses.recurringPeriods.MONTHLY')}</option>
                  <option value="QUARTERLY">{t('finance.expenses.recurringPeriods.QUARTERLY')}</option>
                  <option value="YEARLY">{t('finance.expenses.recurringPeriods.YEARLY')}</option>
                </select>
              )}
            </div>

            <div className="border-t pt-4 mb-4">
              <div className="text-right space-y-2">
                <div className="text-lg">
                  <span className="font-medium">{t('finance.expenses.amount')}:</span> ${(parseFloat(formData.amount) || 0).toFixed(2)}
                </div>
                <div className="text-lg">
                  <span className="font-medium">{t('finance.expenses.taxAmount')}:</span> ${(parseFloat(formData.taxAmount) || 0).toFixed(2)}
                </div>
                <div className="text-xl font-bold">
                  <span>{t('finance.common.total')}:</span> ${((parseFloat(formData.amount) || 0) + (parseFloat(formData.taxAmount) || 0)).toFixed(2)}
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowModal(false); resetForm(); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel')}
              </button>
              <button
                onClick={handleSave}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? t('finance.expenses.saving') : t('finance.expenses.createExpense')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Payment Modal */}
      {showPaymentModal && selectedExpense && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-bold mb-4">{t('finance.expenses.recordPayment')}</h2>

            <div className="bg-gray-50 p-4 rounded-lg mb-4">
              <div className="mb-2">
                <div className="text-sm text-gray-600">{t('finance.expenses.expenseNumber')}</div>
                <div className="font-medium">{selectedExpense.expenseNumber}</div>
              </div>
              <div className="mb-2">
                <div className="text-sm text-gray-600">{t('finance.expenses.description')}</div>
                <div className="font-medium">{selectedExpense.description}</div>
              </div>
              <div className="border-t pt-2 mt-2">
                <div className="text-sm text-gray-600">{t('finance.expenses.amountToPay')}</div>
                <div className="text-2xl font-bold text-green-600">${selectedExpense.totalAmount?.toFixed(2)}</div>
              </div>
            </div>

            <p className="text-sm text-gray-600 mb-4">
              {t('finance.expenses.paymentConfirmation')}
            </p>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowPaymentModal(false); setSelectedExpense(null); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel')}
              </button>
              <button
                onClick={handleRecordPayment}
                disabled={loading}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
              >
                {loading ? t('finance.expenses.saving') : t('finance.expenses.confirmPayment')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Expenses;
