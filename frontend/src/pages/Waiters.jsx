import { useState, useEffect } from 'react';
import { notifyError } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { Search, Plus, Edit, X, Eye, EyeOff, Key, Power, PowerOff } from 'lucide-react';
import { waiterAPI } from '../services/api';

export default function Waiters() {
  const { t } = useTranslation();
  const [waiters, setWaiters] = useState([]);
  const [filteredWaiters, setFilteredWaiters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [showModal, setShowModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [waiterToDelete, setWaiterToDelete] = useState(null);
  const [editingWaiter, setEditingWaiter] = useState(null);
  const [showPinCode, setShowPinCode] = useState(false);

  // Pagination state
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, _setPageSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [sortBy, _setSortBy] = useState('id');
  const [sortDir, _setSortDir] = useState('asc');

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    pinCode: '',
    email: '',
    phoneNumber: '',
    role: 'WAITER',
    permissions: [],
    active: true,
    commissionPercent: 0,
    commissionEnabled: false,
    commissionType: 'PERCENTAGE',
    fixedCommissionAmount: 0,
  });

  const [errors, setErrors] = useState({});

  const availableRoles = ['WAITER', 'HEAD_WAITER', 'SERVER', 'SUPERVISOR'];
  const availablePermissions = [
    { id: 'MANAGE_TABLES', label: t('pages.waiters.permissions.manageTables', 'Manage Tables') },
    { id: 'OVERRIDE_PRICES', label: t('pages.waiters.permissions.overridePrices', 'Override Prices') },
    { id: 'VOID_ITEMS', label: t('pages.waiters.permissions.voidItems', 'Void Items') },
    { id: 'MERGE_TABLES', label: t('pages.waiters.permissions.mergeTables', 'Merge Tables') },
    { id: 'VIEW_REPORTS', label: t('pages.waiters.permissions.viewReports', 'View Reports') },
    { id: 'HANDLE_PAYMENTS', label: t('pages.waiters.permissions.handlePayments', 'Handle Payments') },
    { id: 'APPLY_DISCOUNTS', label: t('pages.waiters.permissions.applyDiscounts', 'Apply Discounts') },
    { id: 'REFUND_ORDERS', label: t('pages.waiters.permissions.refundOrders', 'Refund Orders') },
  ];

  useEffect(() => {
    loadWaiters();
  }, [currentPage, pageSize, sortBy, sortDir]);

  const loadWaiters = async () => {
    try {
      setLoading(true);
      const response = await waiterAPI.getAll({
        page: currentPage,
        size: pageSize,
        sortBy,
        sortDir,
      });

      const pageData = response.data.data || response.data;
      setWaiters(pageData.content || []);
      setFilteredWaiters(pageData.content || []);
      setTotalElements(pageData.totalElements || 0);
      setTotalPages(pageData.totalPages || 0);
    } catch (error) {
      console.error('Error loading waiters:', error);
      setWaiters([]);
      setFilteredWaiters([]);
    } finally {
      setLoading(false);
    }
  };

  // Filter waiters based on search and status
  useEffect(() => {
    let filtered = [...waiters];

    if (searchTerm) {
      const search = searchTerm.toLowerCase();
      filtered = filtered.filter(
        (w) =>
          w.name?.toLowerCase().includes(search) ||
          w.email?.toLowerCase().includes(search) ||
          w.phoneNumber?.toLowerCase().includes(search) ||
          w.pinCode?.toLowerCase().includes(search)
      );
    }

    if (statusFilter !== 'all') {
      filtered = filtered.filter((w) => {
        if (statusFilter === 'active') return w.active === true;
        if (statusFilter === 'inactive') return w.active === false;
        return true;
      });
    }

    setFilteredWaiters(filtered);
  }, [waiters, searchTerm, statusFilter]);

  const generatePinCode = () => {
    const pin = Math.floor(1000 + Math.random() * 9000).toString();
    setFormData((prev) => ({ ...prev, pinCode: pin }));
  };

  const handleOpenModal = (waiter = null) => {
    if (waiter) {
      setEditingWaiter(waiter);
      // Parse permissions if they're stored as JSON string
      let permissionsList = [];
      if (waiter.permissions) {
        try {
          permissionsList = typeof waiter.permissions === 'string'
            ? JSON.parse(waiter.permissions)
            : waiter.permissions;
        } catch (e) {
          permissionsList = [];
        }
      }

      setFormData({
        name: waiter.name || '',
        pinCode: waiter.pinCode || '',
        email: waiter.email || '',
        phoneNumber: waiter.phoneNumber || '',
        role: waiter.role || 'WAITER',
        permissions: permissionsList,
        active: waiter.active !== undefined ? waiter.active : true,
        commissionPercent: waiter.commissionPercent || 0,
        commissionEnabled: waiter.commissionEnabled || false,
        commissionType: waiter.commissionType || 'PERCENTAGE',
        fixedCommissionAmount: waiter.fixedCommissionAmount || 0,
      });
    } else {
      setEditingWaiter(null);
      setFormData({
        name: '',
        pinCode: '',
        email: '',
        phoneNumber: '',
        role: 'WAITER',
        permissions: [],
        active: true,
        commissionPercent: 0,
        commissionEnabled: false,
        commissionType: 'PERCENTAGE',
        fixedCommissionAmount: 0,
      });
      generatePinCode();
    }
    setErrors({});
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingWaiter(null);
    setErrors({});
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    setErrors((prev) => ({ ...prev, [name]: '' }));
  };

  const handlePermissionToggle = (permissionId) => {
    setFormData((prev) => {
      const permissions = prev.permissions || [];
      if (permissions.includes(permissionId)) {
        return {
          ...prev,
          permissions: permissions.filter((p) => p !== permissionId),
        };
      } else {
        return {
          ...prev,
          permissions: [...permissions, permissionId],
        };
      }
    });
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.name?.trim()) {
      newErrors.name = t('pages.waiters.errors.nameRequired', 'Name is required');
    }

    if (!formData.pinCode?.trim()) {
      newErrors.pinCode = t('pages.waiters.errors.pinCodeRequired', 'PIN code is required');
    } else if (!/^\d{4}$/.test(formData.pinCode)) {
      newErrors.pinCode = t('pages.waiters.errors.pinMust4Digits', 'PIN must be 4 digits');
    }

    if (formData.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = t('pages.waiters.errors.invalidEmail', 'Invalid email format');
    }

    if (!formData.role) {
      newErrors.role = t('pages.waiters.errors.roleRequired', 'Role is required');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    try {
      if (editingWaiter) {
        await waiterAPI.update(editingWaiter.id, formData);
      } else {
        await waiterAPI.create(formData);
      }

      handleCloseModal();
      loadWaiters();
    } catch (error) {
      console.error('Error saving waiter:', error);
      notifyError(error);
    }
  };

  // Disable opens a confirm dialog (irreversible-feeling action, even though
  // it's just a soft-delete on the backend). Enable is one click — no
  // need for a confirmation to flip a waiter back on.
  const handleDisableClick = (waiter) => {
    setWaiterToDelete(waiter);
    setShowDeleteModal(true);
  };

  const handleDisableConfirm = async () => {
    if (!waiterToDelete) return;
    try {
      await waiterAPI.delete(waiterToDelete.id);
      setShowDeleteModal(false);
      setWaiterToDelete(null);
      loadWaiters();
    } catch (error) {
      console.error('Error disabling waiter:', error);
      notifyError(error);
    }
  };

  const handleEnable = async (waiter) => {
    try {
      await waiterAPI.update(waiter.id, { active: true });
      loadWaiters();
    } catch (error) {
      console.error('Error enabling waiter:', error);
      notifyError(error);
    }
  };

  const getRoleBadgeColor = (role) => {
    const colors = {
      WAITER: 'bg-blue-100 text-blue-800',
      HEAD_WAITER: 'bg-purple-100 text-purple-800',
      SERVER: 'bg-green-100 text-green-800',
      SUPERVISOR: 'bg-red-100 text-red-800',
    };
    return colors[role] || 'bg-gray-100 text-gray-800';
  };

  return (
    <div className="p-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">{t('pages.waiters.title', 'Waiters Management')}</h1>
        <p className="text-gray-600">{t('pages.waiters.description', 'Manage waiter accounts, PIN codes, roles, and permissions')}</p>
      </div>

      {/* Content */}
      <>
          {/* Filters and Actions */}
          <div className="mb-6 flex flex-col md:flex-row gap-4">
            {/* Search */}
            <div className="flex-1">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-5 w-5" />
                <input
                  type="text"
                  placeholder={t("common.placeholders.searchWaiters")}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            {/* Status Filter */}
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="all">{t('pages.waiters.allStatus', 'All Status')}</option>
              <option value="active">{t('pages.waiters.active', 'Active')}</option>
              <option value="inactive">{t('pages.waiters.inactive', 'Inactive')}</option>
            </select>

            {/* Add Button */}
            <button
              onClick={() => handleOpenModal()}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Plus className="h-5 w-5" />
              {t('pages.waiters.addWaiter', 'Add Waiter')}
            </button>
          </div>

          {/* Waiters Table */}
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.name', 'Name')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.pinCode', 'PIN Code')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.contact', 'Contact')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.role', 'Role')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.permissions', 'Permissions')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.commission', 'Commission')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.status', 'Status')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      {t('pages.waiters.actions', 'Actions')}
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {loading ? (
                    <tr>
                      <td colSpan="8" className="px-6 py-12 text-center">
                        <div className="flex flex-col items-center justify-center">
                          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                          <p className="mt-2 text-gray-500">{t('pages.waiters.loadingWaiters', 'Loading waiters...')}</p>
                        </div>
                      </td>
                    </tr>
                  ) : filteredWaiters.length === 0 ? (
                    <tr>
                      <td colSpan="8" className="px-6 py-12 text-center text-gray-500">
                        {searchTerm || statusFilter !== 'all'
                          ? t('pages.waiters.noWaitersFound', 'No waiters found matching your filters')
                          : t('pages.waiters.noWaitersYet', 'No waiters yet. Click "Add Waiter" to create one.')}
                      </td>
                    </tr>
                  ) : (
                    filteredWaiters.map((waiter) => (
                      <tr key={waiter.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="flex items-center">
                            <div className="flex-shrink-0 h-10 w-10 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-semibold">
                              {waiter.name?.[0]?.toUpperCase() || 'W'}
                            </div>
                            <div className="ml-4">
                              <div className="text-sm font-medium text-gray-900">{waiter.name}</div>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <Key className="h-4 w-4 text-gray-400" />
                            <span className="text-sm font-mono text-gray-900">{waiter.pinCode}</span>
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="text-sm text-gray-900">{waiter.email || '-'}</div>
                          <div className="text-sm text-gray-500">{waiter.phoneNumber || '-'}</div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span
                            className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${getRoleBadgeColor(
                              waiter.role
                            )}`}
                          >
                            {waiter.role}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex flex-wrap gap-1 max-w-xs">
                            {(() => {
                              let permList = [];
                              if (waiter.permissions) {
                                try {
                                  permList = typeof waiter.permissions === 'string'
                                    ? JSON.parse(waiter.permissions)
                                    : waiter.permissions;
                                } catch (e) {
                                  permList = [];
                                }
                              }
                              return (
                                <>
                                  {permList.slice(0, 3).map((perm) => (
                                    <span
                                      key={perm}
                                      className="inline-flex px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded"
                                    >
                                      {perm.replace(/_/g, ' ')}
                                    </span>
                                  ))}
                                  {permList.length > 3 && (
                                    <span className="inline-flex px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded">
                                      {t('pages.waiters.morePermissions', '+{{count}} more', { count: permList.length - 3 })}
                                    </span>
                                  )}
                                </>
                              );
                            })()}
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          {waiter.commissionEnabled ? (
                            <div className="flex flex-col">
                              <span className="text-sm font-medium text-green-600">
                                {waiter.commissionType === 'FIXED_AMOUNT'
                                  ? `${waiter.fixedCommissionAmount || 0}`
                                  : `${waiter.commissionPercent || 0}%`}
                              </span>
                              <span className="text-xs text-gray-500">
                                {waiter.commissionType === 'FIXED_AMOUNT'
                                  ? t('pages.waiters.commissionFixed', 'Fixed/order')
                                  : t('pages.waiters.commissionPercentage', 'Percentage')}
                              </span>
                            </div>
                          ) : (
                            <span className="text-sm text-gray-400">
                              {t('pages.waiters.commissionDisabled', 'Disabled')}
                            </span>
                          )}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span
                            className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                              waiter.active
                                ? 'bg-green-100 text-green-800'
                                : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {waiter.active ? t('pages.waiters.active', 'Active') : t('pages.waiters.inactive', 'Inactive')}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => handleOpenModal(waiter)}
                              className="text-blue-600 hover:text-blue-900"
                              title={t('pages.waiters.edit', 'Edit')}
                            >
                              <Edit className="h-5 w-5" />
                            </button>
                            {waiter.active ? (
                              <button
                                onClick={() => handleDisableClick(waiter)}
                                className="text-red-600 hover:text-red-900"
                                title={t('pages.waiters.disable', 'Disable')}
                              >
                                <PowerOff className="h-5 w-5" />
                              </button>
                            ) : (
                              <button
                                onClick={() => handleEnable(waiter)}
                                className="text-green-600 hover:text-green-900"
                                title={t('pages.waiters.enable', 'Enable')}
                              >
                                <Power className="h-5 w-5" />
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
          </div>

        {/* Summary and Pagination */}
        <div className="mt-4 flex items-center justify-between">
          <div className="text-sm text-gray-600">
            {t('pages.waiters.showingWaiters', 'Showing {{count}} of {{total}} waiters', { count: filteredWaiters.length, total: totalElements })}
          </div>
          {totalPages > 1 && (
            <div className="flex items-center gap-2">
              <button
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                disabled={currentPage === 0}
                className="px-3 py-1 border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {t('pages.waiters.previous', 'Previous')}
              </button>
              <span className="text-sm text-gray-600">
                {t('pages.waiters.pageOf', 'Page {{current}} of {{total}}', { current: currentPage + 1, total: totalPages })}
              </span>
              <button
                onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={currentPage >= totalPages - 1}
                className="px-3 py-1 border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {t('pages.waiters.next', 'Next')}
              </button>
            </div>
          )}
        </div>
      </>

      {/* Add/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="p-6">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-xl font-bold text-gray-900">
                  {editingWaiter ? t('pages.waiters.editWaiter', 'Edit Waiter') : t('pages.waiters.addNewWaiter', 'Add New Waiter')}
                </h2>
                <button
                  onClick={handleCloseModal}
                  className="text-gray-400 hover:text-gray-600"
                >
                  <X className="h-6 w-6" />
                </button>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">
                {/* Name */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('pages.waiters.nameLabel', 'Name')} <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    name="name"
                    value={formData.name}
                    onChange={handleInputChange}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                      errors.name ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder={t("common.placeholders.name")}
                  />
                  {errors.name && <p className="mt-1 text-sm text-red-500">{errors.name}</p>}
                </div>

                {/* PIN Code */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('pages.waiters.pinCodeLabel', 'PIN Code (4 digits)')} <span className="text-red-500">*</span>
                  </label>
                  <div className="flex gap-2">
                    <div className="relative flex-1">
                      <input
                        type={showPinCode ? 'text' : 'password'}
                        name="pinCode"
                        value={formData.pinCode}
                        onChange={handleInputChange}
                        maxLength={4}
                        className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                          errors.pinCode ? 'border-red-500' : 'border-gray-300'
                        }`}
                        placeholder={t("common.placeholders.employeeId")}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPinCode(!showPinCode)}
                        className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
                      >
                        {showPinCode ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={generatePinCode}
                      className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 transition-colors"
                      title={t('pages.waiters.generatePinTitle', 'Generate PIN')}
                    >
                      {t('pages.waiters.generate', 'Generate')}
                    </button>
                  </div>
                  {errors.pinCode && <p className="mt-1 text-sm text-red-500">{errors.pinCode}</p>}
                </div>

                {/* Email */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('pages.waiters.emailLabel', 'Email')}</label>
                  <input
                    type="email"
                    name="email"
                    value={formData.email}
                    onChange={handleInputChange}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                      errors.email ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder={t("common.placeholders.contactEmail")}
                  />
                  {errors.email && <p className="mt-1 text-sm text-red-500">{errors.email}</p>}
                </div>

                {/* Phone Number */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('pages.waiters.phoneNumberLabel', 'Phone Number')}
                  </label>
                  <input
                    type="tel"
                    name="phoneNumber"
                    value={formData.phoneNumber}
                    onChange={handleInputChange}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder={t("common.placeholders.phone")}
                  />
                </div>

                {/* Role */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('pages.waiters.roleLabel', 'Role')} <span className="text-red-500">*</span>
                  </label>
                  <select
                    name="role"
                    value={formData.role}
                    onChange={handleInputChange}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                      errors.role ? 'border-red-500' : 'border-gray-300'
                    }`}
                  >
                    {availableRoles.map((role) => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>
                  {errors.role && <p className="mt-1 text-sm text-red-500">{errors.role}</p>}
                </div>

                {/* Permissions */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    {t('pages.waiters.permissionsLabel', 'Permissions')}
                  </label>
                  <div className="grid grid-cols-2 gap-2">
                    {availablePermissions.map((permission) => (
                      <label
                        key={permission.id}
                        className="flex items-center space-x-2 p-2 border border-gray-200 rounded-md hover:bg-gray-50 cursor-pointer"
                      >
                        <input
                          type="checkbox"
                          checked={formData.permissions.includes(permission.id)}
                          onChange={() => handlePermissionToggle(permission.id)}
                          className="rounded text-blue-600 focus:ring-blue-500"
                        />
                        <span className="text-sm text-gray-700">{permission.label}</span>
                      </label>
                    ))}
                  </div>
                </div>

                {/* Commission Configuration */}
                <div className="border-t pt-4 mt-4">
                  <h3 className="text-sm font-semibold text-gray-700 mb-3">
                    {t('pages.waiters.commissionSettings', 'Commission Settings')}
                  </h3>

                  {/* Commission Enabled Toggle */}
                  <div className="flex items-center space-x-2 mb-3">
                    <input
                      type="checkbox"
                      name="commissionEnabled"
                      id="commissionEnabled"
                      checked={formData.commissionEnabled}
                      onChange={handleInputChange}
                      className="rounded text-green-600 focus:ring-green-500"
                    />
                    <label htmlFor="commissionEnabled" className="text-sm font-medium text-gray-700">
                      {t('pages.waiters.enableCommission', 'Enable Commission Tracking')}
                    </label>
                  </div>

                  {/* Commission Type and Amount */}
                  {formData.commissionEnabled && (
                    <div className="space-y-3">
                      {/* Commission Type Selection */}
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                          {t('pages.waiters.commissionType', 'Commission Type')}
                        </label>
                        <div className="flex gap-4">
                          <label className="flex items-center">
                            <input
                              type="radio"
                              name="commissionType"
                              value="PERCENTAGE"
                              checked={formData.commissionType === 'PERCENTAGE'}
                              onChange={handleInputChange}
                              className="mr-2 text-blue-600 focus:ring-blue-500"
                            />
                            <span className="text-sm text-gray-700">
                              {t('pages.waiters.commissionTypePercentage', 'Percentage')}
                            </span>
                          </label>
                          <label className="flex items-center">
                            <input
                              type="radio"
                              name="commissionType"
                              value="FIXED_AMOUNT"
                              checked={formData.commissionType === 'FIXED_AMOUNT'}
                              onChange={handleInputChange}
                              className="mr-2 text-blue-600 focus:ring-blue-500"
                            />
                            <span className="text-sm text-gray-700">
                              {t('pages.waiters.commissionTypeFixed', 'Fixed Amount')}
                            </span>
                          </label>
                        </div>
                      </div>

                      {/* Commission Percentage (for PERCENTAGE type) */}
                      {formData.commissionType === 'PERCENTAGE' && (
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">
                            {t('pages.waiters.commissionPercent', 'Commission Percentage')}
                          </label>
                          <div className="flex items-center gap-2">
                            <input
                              type="number"
                              name="commissionPercent"
                              value={formData.commissionPercent}
                              onChange={handleInputChange}
                              min="0"
                              max="100"
                              step="0.5"
                              className="w-32 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            <span className="text-gray-500">%</span>
                            <span className="text-xs text-gray-400">
                              {t('pages.waiters.commissionHint', '(e.g., 5% of order total)')}
                            </span>
                          </div>
                        </div>
                      )}

                      {/* Fixed Commission Amount (for FIXED_AMOUNT type) */}
                      {formData.commissionType === 'FIXED_AMOUNT' && (
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">
                            {t('pages.waiters.fixedCommissionAmount', 'Fixed Amount per Order')}
                          </label>
                          <div className="flex items-center gap-2">
                            <input
                              type="number"
                              name="fixedCommissionAmount"
                              value={formData.fixedCommissionAmount}
                              onChange={handleInputChange}
                              min="0"
                              step="0.01"
                              className="w-32 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            <span className="text-xs text-gray-400">
                              {t('pages.waiters.fixedCommissionHint', '(fixed amount per completed order)')}
                            </span>
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Active Status */}
                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    name="active"
                    id="active"
                    checked={formData.active}
                    onChange={handleInputChange}
                    className="rounded text-blue-600 focus:ring-blue-500"
                  />
                  <label htmlFor="active" className="text-sm font-medium text-gray-700">
                    {t('pages.waiters.activeLabel', 'Active')}
                  </label>
                </div>

                {/* Buttons */}
                <div className="flex justify-end gap-3 pt-4 border-t">
                  <button
                    type="button"
                    onClick={handleCloseModal}
                    className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
                  >
                    {t('pages.waiters.cancel', 'Cancel')}
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                  >
                    {editingWaiter ? t('pages.waiters.updateWaiter', 'Update Waiter') : t('pages.waiters.createWaiter', 'Create Waiter')}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Disable Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md">
            <div className="p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                {t('pages.waiters.confirmDisable', 'Disable waiter?')}
              </h3>
              <p className="text-gray-600 mb-6">
                {t(
                  'pages.waiters.disableConfirmationMessage',
                  '“{{name}}” will be hidden from the POS and waiter lists. Shift, payroll and commission history is preserved and you can re-enable them later.',
                  { name: waiterToDelete?.name }
                )}
              </p>
              <div className="flex justify-end gap-3">
                <button
                  onClick={() => setShowDeleteModal(false)}
                  className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
                >
                  {t('pages.waiters.cancel', 'Cancel')}
                </button>
                <button
                  onClick={handleDisableConfirm}
                  className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors"
                >
                  {t('pages.waiters.disable', 'Disable')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
