import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, Plus, Edit, Trash2, X, Eye, EyeOff, Key, ShieldCheck, UserCircle } from 'lucide-react';
import { waiterAPI, restaurantAPI } from '../services/api';
import { format } from 'date-fns';

export default function Waiters() {
  const { t } = useTranslation();
  const [waiters, setWaiters] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [filteredWaiters, setFilteredWaiters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [waiterToDelete, setWaiterToDelete] = useState(null);
  const [editingWaiter, setEditingWaiter] = useState(null);
  const [showPinCode, setShowPinCode] = useState(false);

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    pinCode: '',
    email: '',
    phoneNumber: '',
    role: 'WAITER',
    permissions: ['take_orders', 'view_menu', 'request_bill'],
    active: true,
  });

  const [errors, setErrors] = useState({});

  const availableRoles = ['WAITER', 'HEAD_WAITER', 'SERVER', 'CAPTAIN'];
  const availablePermissions = [
    { id: 'take_orders', label: 'Take Orders' },
    { id: 'view_menu', label: 'View Menu' },
    { id: 'request_bill', label: 'Request Bill' },
    { id: 'manage_tables', label: 'Manage Tables' },
    { id: 'view_reports', label: 'View Reports' },
    { id: 'handle_payments', label: 'Handle Payments' },
    { id: 'cancel_orders', label: 'Cancel Orders' },
    { id: 'apply_discounts', label: 'Apply Discounts' },
  ];

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadWaiters();
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantList = response.data.data?.content || response.data.data || [];
      setRestaurants(restaurantList);

      if (restaurantList.length > 0 && !selectedRestaurant) {
        setSelectedRestaurant(restaurantList[0].id);
      }
    } catch (error) {
      console.error('Error loading restaurants:', error);
    }
  };

  const loadWaiters = async () => {
    if (!selectedRestaurant) return;

    try {
      setLoading(true);
      const response = await waiterAPI.getAll(selectedRestaurant, { active: true });
      const waiterList = response.data.data || response.data || [];
      setWaiters(waiterList);
      setFilteredWaiters(waiterList);
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
      setFormData({
        name: waiter.name || '',
        pinCode: waiter.pinCode || '',
        email: waiter.email || '',
        phoneNumber: waiter.phoneNumber || '',
        role: waiter.role || 'WAITER',
        permissions: waiter.permissions || ['take_orders', 'view_menu', 'request_bill'],
        active: waiter.active !== undefined ? waiter.active : true,
      });
    } else {
      setEditingWaiter(null);
      setFormData({
        name: '',
        pinCode: '',
        email: '',
        phoneNumber: '',
        role: 'WAITER',
        permissions: ['take_orders', 'view_menu', 'request_bill'],
        active: true,
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
      newErrors.name = 'Name is required';
    }

    if (!formData.pinCode?.trim()) {
      newErrors.pinCode = 'PIN code is required';
    } else if (!/^\d{4}$/.test(formData.pinCode)) {
      newErrors.pinCode = 'PIN must be 4 digits';
    }

    if (formData.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = 'Invalid email format';
    }

    if (!formData.role) {
      newErrors.role = 'Role is required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    if (!selectedRestaurant) {
      alert('Please select a restaurant first');
      return;
    }

    try {
      if (editingWaiter) {
        await waiterAPI.update(editingWaiter.id, formData);
      } else {
        await waiterAPI.create(selectedRestaurant, formData);
      }

      handleCloseModal();
      loadWaiters();
    } catch (error) {
      console.error('Error saving waiter:', error);
      alert('Error saving waiter: ' + (error.response?.data?.message || error.message));
    }
  };

  const handleDeleteClick = (waiter) => {
    setWaiterToDelete(waiter);
    setShowDeleteModal(true);
  };

  const handleDeleteConfirm = async () => {
    if (!waiterToDelete) return;

    try {
      await waiterAPI.delete(waiterToDelete.id);
      setShowDeleteModal(false);
      setWaiterToDelete(null);
      loadWaiters();
    } catch (error) {
      console.error('Error deleting waiter:', error);
      alert('Error deleting waiter: ' + (error.response?.data?.message || error.message));
    }
  };

  const getRoleBadgeColor = (role) => {
    const colors = {
      WAITER: 'bg-blue-100 text-blue-800',
      HEAD_WAITER: 'bg-purple-100 text-purple-800',
      SERVER: 'bg-green-100 text-green-800',
      CAPTAIN: 'bg-red-100 text-red-800',
    };
    return colors[role] || 'bg-gray-100 text-gray-800';
  };

  if (loading && !selectedRestaurant) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">Waiters Management</h1>
        <p className="text-gray-600">Manage waiter accounts, PIN codes, roles, and permissions</p>
      </div>

      {/* Restaurant Selector */}
      <div className="mb-6 bg-white p-4 rounded-lg border border-gray-200">
        <label className="block text-sm font-medium text-gray-700 mb-2">Select Restaurant</label>
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => setSelectedRestaurant(Number(e.target.value))}
          className="w-full md:w-64 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">Select a restaurant...</option>
          {restaurants.map((restaurant) => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
      </div>

      {selectedRestaurant && (
        <>
          {/* Filters and Actions */}
          <div className="mb-6 flex flex-col md:flex-row gap-4">
            {/* Search */}
            <div className="flex-1">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-5 w-5" />
                <input
                  type="text"
                  placeholder="Search waiters..."
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
              <option value="all">All Status</option>
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
            </select>

            {/* Add Button */}
            <button
              onClick={() => handleOpenModal()}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Plus className="h-5 w-5" />
              Add Waiter
            </button>
          </div>

          {/* Waiters Table */}
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Name
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      PIN Code
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Contact
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Role
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Permissions
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Status
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {loading ? (
                    <tr>
                      <td colSpan="7" className="px-6 py-12 text-center">
                        <div className="flex flex-col items-center justify-center">
                          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                          <p className="mt-2 text-gray-500">Loading waiters...</p>
                        </div>
                      </td>
                    </tr>
                  ) : filteredWaiters.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="px-6 py-12 text-center text-gray-500">
                        {searchTerm || statusFilter !== 'all'
                          ? 'No waiters found matching your filters'
                          : 'No waiters yet. Click "Add Waiter" to create one.'}
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
                            {(waiter.permissions || []).slice(0, 3).map((perm) => (
                              <span
                                key={perm}
                                className="inline-flex px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded"
                              >
                                {perm.replace(/_/g, ' ')}
                              </span>
                            ))}
                            {waiter.permissions?.length > 3 && (
                              <span className="inline-flex px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded">
                                +{waiter.permissions.length - 3} more
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span
                            className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                              waiter.active
                                ? 'bg-green-100 text-green-800'
                                : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {waiter.active ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => handleOpenModal(waiter)}
                              className="text-blue-600 hover:text-blue-900"
                              title="Edit"
                            >
                              <Edit className="h-5 w-5" />
                            </button>
                            <button
                              onClick={() => handleDeleteClick(waiter)}
                              className="text-red-600 hover:text-red-900"
                              title="Delete"
                            >
                              <Trash2 className="h-5 w-5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Summary */}
          <div className="mt-4 text-sm text-gray-600">
            Showing {filteredWaiters.length} of {waiters.length} waiters
          </div>
        </>
      )}

      {/* Add/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="p-6">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-xl font-bold text-gray-900">
                  {editingWaiter ? 'Edit Waiter' : 'Add New Waiter'}
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
                    Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    name="name"
                    value={formData.name}
                    onChange={handleInputChange}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                      errors.name ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder="John Doe"
                  />
                  {errors.name && <p className="mt-1 text-sm text-red-500">{errors.name}</p>}
                </div>

                {/* PIN Code */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    PIN Code (4 digits) <span className="text-red-500">*</span>
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
                        placeholder="1234"
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
                      title="Generate PIN"
                    >
                      Generate
                    </button>
                  </div>
                  {errors.pinCode && <p className="mt-1 text-sm text-red-500">{errors.pinCode}</p>}
                </div>

                {/* Email */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                  <input
                    type="email"
                    name="email"
                    value={formData.email}
                    onChange={handleInputChange}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                      errors.email ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder="john@example.com"
                  />
                  {errors.email && <p className="mt-1 text-sm text-red-500">{errors.email}</p>}
                </div>

                {/* Phone Number */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Phone Number
                  </label>
                  <input
                    type="tel"
                    name="phoneNumber"
                    value={formData.phoneNumber}
                    onChange={handleInputChange}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="+998901234567"
                  />
                </div>

                {/* Role */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Role <span className="text-red-500">*</span>
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
                    Permissions
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
                    Active
                  </label>
                </div>

                {/* Buttons */}
                <div className="flex justify-end gap-3 pt-4 border-t">
                  <button
                    type="button"
                    onClick={handleCloseModal}
                    className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                  >
                    {editingWaiter ? 'Update Waiter' : 'Create Waiter'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md">
            <div className="p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Confirm Deletion</h3>
              <p className="text-gray-600 mb-6">
                Are you sure you want to delete waiter "{waiterToDelete?.name}"? This action
                cannot be undone.
              </p>
              <div className="flex justify-end gap-3">
                <button
                  onClick={() => setShowDeleteModal(false)}
                  className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleDeleteConfirm}
                  className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors"
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
