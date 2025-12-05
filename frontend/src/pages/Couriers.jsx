import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, Plus, Download, Edit, Trash2, X, Eye, EyeOff, Wallet, CheckCircle, XCircle, Shuffle } from 'lucide-react';
import { courierAPI } from '../services/api';
import { format } from 'date-fns';
import { generatePassword, copyToClipboard } from '../utils/passwordGenerator';

export default function Couriers() {
  const { t } = useTranslation();
  const [couriers, setCouriers] = useState([]);
  const [filteredCouriers, setFilteredCouriers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [typeFilter, setTypeFilter] = useState('all');
  const [vehicleFilter, setVehicleFilter] = useState('all');
  const [showModal, setShowModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showWalletModal, setShowWalletModal] = useState(false);
  const [courierToDelete, setCourierToDelete] = useState(null);
  const [selectedWallet, setSelectedWallet] = useState(null);
  const [editingCourier, setEditingCourier] = useState(null);
  const [showPassword, setShowPassword] = useState(false);

  // Pagination state
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [sortBy, setSortBy] = useState('id');
  const [sortDir, setSortDir] = useState('asc');

  // Form state
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    phone: '',
    courierType: 'FULL_TIME',
    vehicle: 'BICYCLE',
    vehiclePlate: '',
    licenseNumber: '',
    address: '',
    city: '',
    emergencyContact: '',
    available: true,
    active: true,
  });

  const [errors, setErrors] = useState({});

  useEffect(() => {
    loadCouriers();
  }, [currentPage, pageSize, sortBy, sortDir]);

  const loadCouriers = async () => {
    try {
      setLoading(true);
      const response = await courierAPI.getAll({
        page: currentPage,
        size: pageSize,
        sortBy,
        sortDir,
      });

      const pageData = response.data.data || response.data;
      setCouriers(pageData.content || []);
      setFilteredCouriers(pageData.content || []);
      setTotalElements(pageData.totalElements || 0);
      setTotalPages(pageData.totalPages || 0);
    } catch (error) {
      console.error('Error loading couriers:', error);
    } finally {
      setLoading(false);
    }
  };

  // Filter couriers
  useEffect(() => {
    let filtered = [...couriers];

    if (searchTerm) {
      const search = searchTerm.toLowerCase();
      filtered = filtered.filter(
        (c) =>
          c.firstName?.toLowerCase().includes(search) ||
          c.lastName?.toLowerCase().includes(search) ||
          c.email?.toLowerCase().includes(search) ||
          c.phone?.toLowerCase().includes(search) ||
          c.city?.toLowerCase().includes(search)
      );
    }

    if (statusFilter !== 'all') {
      filtered = filtered.filter((c) => {
        if (statusFilter === 'active') return c.userActive === true;
        if (statusFilter === 'inactive') return c.userActive === false;
        return true;
      });
    }

    if (typeFilter !== 'all') {
      filtered = filtered.filter((c) => c.courierType === typeFilter);
    }

    if (vehicleFilter !== 'all') {
      filtered = filtered.filter((c) => c.vehicle === vehicleFilter);
    }

    setFilteredCouriers(filtered);
  }, [couriers, searchTerm, statusFilter, typeFilter, vehicleFilter]);

  const handleSort = (field) => {
    if (sortBy === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(field);
      setSortDir('asc');
    }
  };

  const getSortIcon = (field) => {
    if (sortBy !== field) return '↕';
    return sortDir === 'asc' ? '↑' : '↓';
  };

  const handlePageChange = (newPage) => {
    if (newPage >= 0 && newPage < totalPages) {
      setCurrentPage(newPage);
    }
  };

  const handlePageSizeChange = (e) => {
    setPageSize(Number(e.target.value));
    setCurrentPage(0);
  };

  const handleCreateNew = () => {
    setEditingCourier(null);
    setFormData({
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      phone: '',
      courierType: 'FULL_TIME',
      vehicle: 'BICYCLE',
      vehiclePlate: '',
      licenseNumber: '',
      address: '',
      city: '',
      emergencyContact: '',
      available: true,
      active: true,
    });
    setErrors({});
    setShowPassword(false);
    setShowModal(true);
  };

  const handleEdit = (courier) => {
    setEditingCourier(courier);
    setFormData({
      email: courier.email,
      password: '',
      firstName: courier.firstName,
      lastName: courier.lastName,
      phone: courier.phone || '',
      courierType: courier.courierType,
      vehicle: courier.vehicle,
      vehiclePlate: courier.vehiclePlate || '',
      licenseNumber: courier.licenseNumber || '',
      address: courier.address || '',
      city: courier.city || '',
      emergencyContact: courier.emergencyContact || '',
      available: courier.available,
      active: courier.userActive,
    });
    setErrors({});
    setShowPassword(false);
    setShowModal(true);
  };

  const handleViewWallet = async (courier) => {
    try {
      const response = await courierAPI.getWallet(courier.id);
      setSelectedWallet(response.data.data || response.data);
      setShowWalletModal(true);
    } catch (error) {
      console.error('Error loading wallet:', error);
      alert(t('couriers.walletError'));
    }
  };

  const handleDeleteClick = (courier) => {
    setCourierToDelete(courier);
    setShowDeleteModal(true);
  };

  const handleDeleteConfirm = async () => {
    if (!courierToDelete) return;

    try {
      await courierAPI.delete(courierToDelete.id);
      await loadCouriers();
      setShowDeleteModal(false);
      setCourierToDelete(null);
    } catch (error) {
      console.error('Error deleting courier:', error);
      alert(t('couriers.deleteError'));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.email) {
      newErrors.email = t('couriers.validation.emailRequired');
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = t('couriers.validation.emailInvalid');
    }

    if (!editingCourier && !formData.password) {
      newErrors.password = t('couriers.validation.passwordRequired');
    } else if (formData.password && formData.password.length < 8) {
      newErrors.password = t('couriers.validation.passwordMin');
    }

    if (!formData.firstName) {
      newErrors.firstName = t('couriers.validation.firstNameRequired');
    }

    if (!formData.lastName) {
      newErrors.lastName = t('couriers.validation.lastNameRequired');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) return;

    try {
      const submitData = { ...formData };

      if (editingCourier && !submitData.password) {
        delete submitData.password;
      }

      if (editingCourier) {
        await courierAPI.update(editingCourier.id, submitData);
      } else {
        await courierAPI.create(submitData);
      }

      await loadCouriers();
      setShowModal(false);
    } catch (error) {
      console.error('Error saving courier:', error);
      if (error.response?.data?.message) {
        alert(error.response.data.message);
      } else {
        alert(t('couriers.saveError'));
      }
    }
  };

  const exportToCSV = () => {
    const headers = [
      t('couriers.id'),
      t('couriers.email'),
      t('couriers.firstName'),
      t('couriers.lastName'),
      t('couriers.phone'),
      t('couriers.courierType'),
      t('couriers.vehicle'),
      t('couriers.city'),
      t('couriers.verified'),
      t('couriers.available'),
      t('couriers.status'),
      t('couriers.createdAt'),
    ];

    const rows = filteredCouriers.map((c) => [
      c.id,
      c.email,
      c.firstName,
      c.lastName,
      c.phone || '',
      t(`couriers.types.${c.courierType?.toLowerCase()}`),
      t(`couriers.vehicles.${c.vehicle?.toLowerCase()}`),
      c.city || '',
      c.verified ? t('couriers.yes') : t('couriers.no'),
      c.available ? t('couriers.yes') : t('couriers.no'),
      c.userActive ? t('couriers.active') : t('couriers.inactive'),
      c.createdAt ? format(new Date(c.createdAt), 'yyyy-MM-dd HH:mm') : '',
    ]);

    const csvContent = [
      headers.join(','),
      ...rows.map((row) => row.map((cell) => `"${cell}"`).join(',')),
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `couriers_${format(new Date(), 'yyyy-MM-dd_HH-mm')}.csv`;
    link.click();
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">{t('couriers.title')}</h1>
          <p className="text-gray-500 mt-1">{t('couriers.description')}</p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={exportToCSV}
            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
          >
            <Download className="w-4 h-4" />
            {t('couriers.export')}
          </button>
          <button
            onClick={handleCreateNew}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            {t('couriers.createNew')}
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
            <input
              type="text"
              placeholder={t('couriers.search')}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="all">{t('couriers.allStatuses')}</option>
            <option value="active">{t('couriers.active')}</option>
            <option value="inactive">{t('couriers.inactive')}</option>
          </select>

          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="all">{t('couriers.allTypes')}</option>
            <option value="FULL_TIME">{t('couriers.types.full_time')}</option>
            <option value="PART_TIME">{t('couriers.types.part_time')}</option>
            <option value="FREELANCE">{t('couriers.types.freelance')}</option>
            <option value="CONTRACTOR">{t('couriers.types.contractor')}</option>
          </select>

          <select
            value={vehicleFilter}
            onChange={(e) => setVehicleFilter(e.target.value)}
            className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="all">{t('couriers.allVehicles')}</option>
            <option value="BICYCLE">{t('couriers.vehicles.bicycle')}</option>
            <option value="MOTORCYCLE">{t('couriers.vehicles.motorcycle')}</option>
            <option value="SCOOTER">{t('couriers.vehicles.scooter')}</option>
            <option value="CAR">{t('couriers.vehicles.car')}</option>
            <option value="ON_FOOT">{t('couriers.vehicles.on_foot')}</option>
          </select>

          <select
            value={pageSize}
            onChange={handlePageSizeChange}
            className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="5">5 {t('couriers.perPage')}</option>
            <option value="10">10 {t('couriers.perPage')}</option>
            <option value="20">20 {t('couriers.perPage')}</option>
            <option value="50">50 {t('couriers.perPage')}</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th
                  onClick={() => handleSort('id')}
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                >
                  {t('couriers.id')} {getSortIcon('id')}
                </th>
                <th
                  onClick={() => handleSort('firstName')}
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                >
                  {t('couriers.name')} {getSortIcon('firstName')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.phone')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.courierType')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.vehicle')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.city')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.verified')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.available')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.status')}
                </th>
                <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('couriers.actions')}
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {loading ? (
                <tr>
                  <td colSpan="10" className="px-6 py-12 text-center text-gray-500">
                    {t('couriers.loading')}
                  </td>
                </tr>
              ) : filteredCouriers.length === 0 ? (
                <tr>
                  <td colSpan="10" className="px-6 py-12 text-center text-gray-500">
                    {t('couriers.noData')}
                  </td>
                </tr>
              ) : (
                filteredCouriers.map((courier) => (
                  <tr key={courier.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {courier.id}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">
                        {courier.firstName} {courier.lastName}
                      </div>
                      <div className="text-sm text-gray-500">{courier.email}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {courier.phone || '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {t(`couriers.types.${courier.courierType?.toLowerCase()}`)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {t(`couriers.vehicles.${courier.vehicle?.toLowerCase()}`)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {courier.city || '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {courier.verified ? (
                        <CheckCircle className="w-5 h-5 text-green-600" />
                      ) : (
                        <XCircle className="w-5 h-5 text-red-600" />
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {courier.available ? (
                        <CheckCircle className="w-5 h-5 text-green-600" />
                      ) : (
                        <XCircle className="w-5 h-5 text-gray-400" />
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${
                          courier.userActive
                            ? 'bg-green-100 text-green-800'
                            : 'bg-red-100 text-red-800'
                        }`}
                      >
                        {courier.userActive ? t('couriers.active') : t('couriers.inactive')}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-center text-sm font-medium">
                      <div className="flex items-center justify-center gap-2">
                        <button
                          onClick={() => handleViewWallet(courier)}
                          className="text-green-600 hover:text-green-900"
                          title={t('couriers.viewWallet')}
                        >
                          <Wallet className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleEdit(courier)}
                          className="text-blue-600 hover:text-blue-900"
                          title={t('couriers.edit')}
                        >
                          <Edit className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleDeleteClick(courier)}
                          className="text-red-600 hover:text-red-900"
                          title={t('couriers.delete')}
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {!loading && totalPages > 0 && (
          <div className="bg-gray-50 px-6 py-3 border-t border-gray-200 flex items-center justify-between">
            <div className="text-sm text-gray-700">
              {t('couriers.showing')} {currentPage * pageSize + 1} {t('couriers.to')}{' '}
              {Math.min((currentPage + 1) * pageSize, totalElements)} {t('couriers.of')}{' '}
              {totalElements} {t('couriers.results')}
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => handlePageChange(0)}
                disabled={currentPage === 0}
                className="px-3 py-1 border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                «
              </button>
              <button
                onClick={() => handlePageChange(currentPage - 1)}
                disabled={currentPage === 0}
                className="px-3 py-1 border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                ‹
              </button>
              <span className="px-4 py-1 text-sm text-gray-700">
                {t('couriers.page')} {currentPage + 1} {t('couriers.of')} {totalPages}
              </span>
              <button
                onClick={() => handlePageChange(currentPage + 1)}
                disabled={currentPage >= totalPages - 1}
                className="px-3 py-1 border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                ›
              </button>
              <button
                onClick={() => handlePageChange(totalPages - 1)}
                disabled={currentPage >= totalPages - 1}
                className="px-3 py-1 border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                »
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 overflow-y-auto">
          <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 my-8 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h2 className="text-xl font-bold text-gray-800">
                {editingCourier ? t('couriers.editCourier') : t('couriers.createCourier')}
              </h2>
              <button
                onClick={() => setShowModal(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.email')} *
                  </label>
                  <input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className={`w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                      errors.email ? 'border-red-500' : 'border-gray-300'
                    }`}
                  />
                  {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.password')} {!editingCourier && '*'}
                  </label>
                  <div className="flex gap-2">
                    <div className="relative flex-1">
                      <input
                        type={showPassword ? 'text' : 'password'}
                        value={formData.password}
                        onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                        placeholder={editingCourier ? t('couriers.passwordPlaceholder') : ''}
                        className={`w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent pr-10 ${
                          errors.password ? 'border-red-500' : 'border-gray-300'
                        }`}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
                      >
                        {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={async () => {
                        const newPassword = generatePassword(12);
                        setFormData({ ...formData, password: newPassword });
                        setShowPassword(true);
                        await copyToClipboard(newPassword);
                      }}
                      className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 flex items-center gap-2 whitespace-nowrap"
                      title="Generate secure password"
                    >
                      <Shuffle className="w-4 h-4" />
                      <span className="hidden sm:inline">Generate</span>
                    </button>
                  </div>
                  {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
                  {formData.password && showPassword && (
                    <p className="text-xs text-green-600 mt-1">
                      ✓ Password copied to clipboard
                    </p>
                  )}
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.firstName')} *
                  </label>
                  <input
                    type="text"
                    value={formData.firstName}
                    onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                    className={`w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                      errors.firstName ? 'border-red-500' : 'border-gray-300'
                    }`}
                  />
                  {errors.firstName && (
                    <p className="text-red-500 text-xs mt-1">{errors.firstName}</p>
                  )}
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.lastName')} *
                  </label>
                  <input
                    type="text"
                    value={formData.lastName}
                    onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                    className={`w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                      errors.lastName ? 'border-red-500' : 'border-gray-300'
                    }`}
                  />
                  {errors.lastName && <p className="text-red-500 text-xs mt-1">{errors.lastName}</p>}
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.phone')}
                  </label>
                  <input
                    type="text"
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.courierType')} *
                  </label>
                  <select
                    value={formData.courierType}
                    onChange={(e) => setFormData({ ...formData, courierType: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="FULL_TIME">{t('couriers.types.full_time')}</option>
                    <option value="PART_TIME">{t('couriers.types.part_time')}</option>
                    <option value="FREELANCE">{t('couriers.types.freelance')}</option>
                    <option value="CONTRACTOR">{t('couriers.types.contractor')}</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.vehicle')} *
                  </label>
                  <select
                    value={formData.vehicle}
                    onChange={(e) => setFormData({ ...formData, vehicle: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="BICYCLE">{t('couriers.vehicles.bicycle')}</option>
                    <option value="MOTORCYCLE">{t('couriers.vehicles.motorcycle')}</option>
                    <option value="SCOOTER">{t('couriers.vehicles.scooter')}</option>
                    <option value="CAR">{t('couriers.vehicles.car')}</option>
                    <option value="ON_FOOT">{t('couriers.vehicles.on_foot')}</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.vehiclePlate')}
                  </label>
                  <input
                    type="text"
                    value={formData.vehiclePlate}
                    onChange={(e) => setFormData({ ...formData, vehiclePlate: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.licenseNumber')}
                  </label>
                  <input
                    type="text"
                    value={formData.licenseNumber}
                    onChange={(e) => setFormData({ ...formData, licenseNumber: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.city')}
                  </label>
                  <input
                    type="text"
                    value={formData.city}
                    onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('couriers.emergencyContact')}
                  </label>
                  <input
                    type="text"
                    value={formData.emergencyContact}
                    onChange={(e) => setFormData({ ...formData, emergencyContact: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('couriers.address')}
                </label>
                <textarea
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  rows="2"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>

              <div className="flex gap-6">
                <div className="flex items-center">
                  <input
                    type="checkbox"
                    id="available"
                    checked={formData.available}
                    onChange={(e) => setFormData({ ...formData, available: e.target.checked })}
                    className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                  <label htmlFor="available" className="ml-2 text-sm text-gray-700">
                    {t('couriers.availableStatus')}
                  </label>
                </div>

                <div className="flex items-center">
                  <input
                    type="checkbox"
                    id="active"
                    checked={formData.active}
                    onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                    className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                  <label htmlFor="active" className="ml-2 text-sm text-gray-700">
                    {t('couriers.activeStatus')}
                  </label>
                </div>
              </div>

              <div className="flex gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  {t('couriers.cancel')}
                </button>
                <button
                  type="submit"
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                >
                  {editingCourier ? t('couriers.update') : t('couriers.create')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Wallet Modal */}
      {showWalletModal && selectedWallet && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h2 className="text-xl font-bold text-gray-800 flex items-center gap-2">
                <Wallet className="w-6 h-6" />
                {t('couriers.walletDetails')}
              </h2>
              <button
                onClick={() => setShowWalletModal(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <p className="text-sm text-gray-500">{t('couriers.courierName')}</p>
                <p className="text-lg font-semibold text-gray-900">{selectedWallet.courierName}</p>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-green-50 p-4 rounded-lg">
                  <p className="text-sm text-gray-600">{t('couriers.balance')}</p>
                  <p className="text-2xl font-bold text-green-600">
                    ${selectedWallet.balance?.toFixed(2) || '0.00'}
                  </p>
                </div>
                <div className="bg-blue-50 p-4 rounded-lg">
                  <p className="text-sm text-gray-600">{t('couriers.totalEarned')}</p>
                  <p className="text-2xl font-bold text-blue-600">
                    ${selectedWallet.totalEarned?.toFixed(2) || '0.00'}
                  </p>
                </div>
                <div className="bg-purple-50 p-4 rounded-lg">
                  <p className="text-sm text-gray-600">{t('couriers.totalBonuses')}</p>
                  <p className="text-xl font-bold text-purple-600">
                    ${selectedWallet.totalBonuses?.toFixed(2) || '0.00'}
                  </p>
                </div>
                <div className="bg-red-50 p-4 rounded-lg">
                  <p className="text-sm text-gray-600">{t('couriers.totalFines')}</p>
                  <p className="text-xl font-bold text-red-600">
                    ${selectedWallet.totalFines?.toFixed(2) || '0.00'}
                  </p>
                </div>
                <div className="bg-gray-50 p-4 rounded-lg">
                  <p className="text-sm text-gray-600">{t('couriers.totalWithdrawn')}</p>
                  <p className="text-xl font-bold text-gray-600">
                    ${selectedWallet.totalWithdrawn?.toFixed(2) || '0.00'}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <div className="p-6">
              <h3 className="text-lg font-bold text-gray-800 mb-4">
                {t('couriers.confirmDelete')}
              </h3>
              <p className="text-gray-600 mb-6">
                {t('couriers.deleteMessage', {
                  name: `${courierToDelete?.firstName} ${courierToDelete?.lastName}`,
                })}
              </p>
              <div className="flex gap-3">
                <button
                  onClick={() => setShowDeleteModal(false)}
                  className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  {t('couriers.cancel')}
                </button>
                <button
                  onClick={handleDeleteConfirm}
                  className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                >
                  {t('couriers.delete')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
