import React, { useState, useEffect } from 'react';
import { financialAPI } from '../services/api';
import { useAuthStore } from '../stores/authStore';
import { Plus, Edit, Trash2, Check, DollarSign, Calendar } from 'lucide-react';

const Expenses = () => {
  const { user } = useAuthStore();
  const [expenses, setExpenses] = useState([]);
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
    { value: 'RENT', label: 'Rent' },
    { value: 'UTILITIES', label: 'Utilities' },
    { value: 'SUPPLIES', label: 'Supplies' },
    { value: 'MARKETING', label: 'Marketing' },
    { value: 'INSURANCE', label: 'Insurance' },
    { value: 'MAINTENANCE', label: 'Maintenance' },
    { value: 'EQUIPMENT', label: 'Equipment' },
    { value: 'LICENSES', label: 'Licenses & Permits' },
    { value: 'TAXES', label: 'Taxes' },
    { value: 'DELIVERY_COSTS', label: 'Delivery Costs' },
    { value: 'PROFESSIONAL_FEES', label: 'Professional Fees' },
    { value: 'BANK_FEES', label: 'Bank Fees' },
    { value: 'OTHER', label: 'Other' }
  ];

  useEffect(() => {
    if (user?.restaurantId) {
      setSelectedRestaurant(user.restaurantId);
      setFormData(prev => ({ ...prev, restaurantId: user.restaurantId }));
      loadExpenses(user.restaurantId);
    }
  }, [user, dateRange]);

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
        alert('Please fill in all required fields');
        return;
      }

      setLoading(true);
      await financialAPI.createExpense(formData);
      alert('Expense created successfully!');
      setShowModal(false);
      resetForm();
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to create expense:', error);
      alert('Failed to create expense: ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      await financialAPI.approveExpense(id, user?.username);
      alert('Expense approved successfully!');
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to approve expense:', error);
      alert('Failed to approve expense');
    }
  };

  const handleRecordPayment = async () => {
    try {
      setLoading(true);
      await financialAPI.recordExpensePayment(
        selectedExpense.id,
        new Date().toISOString().split('T')[0],
        user?.username
      );
      alert('Payment recorded successfully!');
      setShowPaymentModal(false);
      setSelectedExpense(null);
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to record payment:', error);
      alert('Failed to record payment');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this expense?')) {
      return;
    }

    try {
      await financialAPI.deleteExpense(id);
      alert('Expense deleted successfully!');
      loadExpenses(selectedRestaurant);
    } catch (error) {
      console.error('Failed to delete expense:', error);
      alert('Failed to delete expense: ' + (error.response?.data?.message || 'Cannot delete paid expense'));
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

  const filteredExpenses = expenses.filter(expense => {
    const matchesSearch = expense.description?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         expense.vendor?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         expense.expenseNumber?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesCategory = filterCategory === 'all' || expense.category === filterCategory;
    const matchesStatus = filterStatus === 'all' || expense.paymentStatus === filterStatus;
    return matchesSearch && matchesCategory && matchesStatus;
  });

  const totalExpenses = filteredExpenses.reduce((sum, exp) => sum + (exp.totalAmount || 0), 0);
  const unpaidExpenses = filteredExpenses.filter(exp => exp.paymentStatus === 'UNPAID')
    .reduce((sum, exp) => sum + (exp.totalAmount || 0), 0);

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Expenses</h1>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          Add Expense
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">Total Expenses (Period)</div>
          <div className="text-2xl font-bold text-gray-900">${totalExpenses.toFixed(2)}</div>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">Unpaid Expenses</div>
          <div className="text-2xl font-bold text-red-600">${unpaidExpenses.toFixed(2)}</div>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">Total Items</div>
          <div className="text-2xl font-bold text-gray-900">{filteredExpenses.length}</div>
        </div>
      </div>

      {/* Filters */}
      <div className="mb-4 grid grid-cols-5 gap-4">
        <input
          type="text"
          placeholder="Search expenses..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="col-span-2 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <select
          value={filterCategory}
          onChange={(e) => setFilterCategory(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">All Categories</option>
          {expenseCategories.map(cat => (
            <option key={cat.value} value={cat.value}>{cat.label}</option>
          ))}
        </select>
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">All Status</option>
          <option value="UNPAID">Unpaid</option>
          <option value="PAID">Paid</option>
          <option value="OVERDUE">Overdue</option>
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
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Expense #</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Description</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Vendor</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Amount</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                <td colSpan="8" className="px-6 py-4 text-center text-gray-500">Loading...</td>
              </tr>
            ) : filteredExpenses.length === 0 ? (
              <tr>
                <td colSpan="8" className="px-6 py-4 text-center text-gray-500">No expenses found</td>
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
            <h2 className="text-xl font-bold mb-4">Add Expense</h2>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Expense Date *</label>
                <input
                  type="date"
                  value={formData.expenseDate}
                  onChange={(e) => setFormData({ ...formData, expenseDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Category *</label>
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
              <label className="block text-sm font-medium text-gray-700 mb-1">Description *</label>
              <input
                type="text"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                placeholder="Brief description of the expense"
              />
            </div>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Vendor</label>
                <input
                  type="text"
                  value={formData.vendor}
                  onChange={(e) => setFormData({ ...formData, vendor: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Reference Number</label>
                <input
                  type="text"
                  value={formData.referenceNumber}
                  onChange={(e) => setFormData({ ...formData, referenceNumber: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  placeholder="Invoice #, Receipt #, etc."
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Amount *</label>
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
                <label className="block text-sm font-medium text-gray-700 mb-1">Tax Amount</label>
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
              <label className="block text-sm font-medium text-gray-700 mb-1">Payment Method</label>
              <select
                value={formData.paymentMethod}
                onChange={(e) => setFormData({ ...formData, paymentMethod: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
              >
                <option value="CASH">Cash</option>
                <option value="CARD">Card</option>
                <option value="BANK_TRANSFER">Bank Transfer</option>
                <option value="CHECK">Check</option>
                <option value="OTHER">Other</option>
              </select>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
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
              <label className="text-sm font-medium text-gray-700">Recurring Expense</label>
              {formData.recurring && (
                <select
                  value={formData.recurringPeriod || ''}
                  onChange={(e) => setFormData({ ...formData, recurringPeriod: e.target.value })}
                  className="ml-4 px-3 py-1 border border-gray-300 rounded-lg text-sm"
                >
                  <option value="">Select Period</option>
                  <option value="WEEKLY">Weekly</option>
                  <option value="MONTHLY">Monthly</option>
                  <option value="QUARTERLY">Quarterly</option>
                  <option value="YEARLY">Yearly</option>
                </select>
              )}
            </div>

            <div className="border-t pt-4 mb-4">
              <div className="text-right space-y-2">
                <div className="text-lg">
                  <span className="font-medium">Amount:</span> ${(parseFloat(formData.amount) || 0).toFixed(2)}
                </div>
                <div className="text-lg">
                  <span className="font-medium">Tax:</span> ${(parseFloat(formData.taxAmount) || 0).toFixed(2)}
                </div>
                <div className="text-xl font-bold">
                  <span>Total:</span> ${((parseFloat(formData.amount) || 0) + (parseFloat(formData.taxAmount) || 0)).toFixed(2)}
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowModal(false); resetForm(); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? 'Saving...' : 'Create Expense'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Payment Modal */}
      {showPaymentModal && selectedExpense && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-bold mb-4">Record Payment</h2>

            <div className="bg-gray-50 p-4 rounded-lg mb-4">
              <div className="mb-2">
                <div className="text-sm text-gray-600">Expense #</div>
                <div className="font-medium">{selectedExpense.expenseNumber}</div>
              </div>
              <div className="mb-2">
                <div className="text-sm text-gray-600">Description</div>
                <div className="font-medium">{selectedExpense.description}</div>
              </div>
              <div className="border-t pt-2 mt-2">
                <div className="text-sm text-gray-600">Amount to Pay</div>
                <div className="text-2xl font-bold text-green-600">${selectedExpense.totalAmount?.toFixed(2)}</div>
              </div>
            </div>

            <p className="text-sm text-gray-600 mb-4">
              This will mark the expense as paid. The payment will be recorded with today's date.
            </p>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowPaymentModal(false); setSelectedExpense(null); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleRecordPayment}
                disabled={loading}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
              >
                {loading ? 'Recording...' : 'Confirm Payment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Expenses;
