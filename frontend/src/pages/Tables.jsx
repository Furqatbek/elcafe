import React, { useState, useEffect } from 'react';
import { tablesAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { useTranslation } from 'react-i18next';
import { Plus, Edit, Trash2, Users, Grid, CheckCircle, XCircle, GitMerge, GitBranch } from 'lucide-react';

const Tables = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [tables, setTables] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingTable, setEditingTable] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');
  const [stats, setStats] = useState({ totalTables: 0, availableTables: 0, occupiedTables: 0, reservedTables: 0 });
  const [selectedTables, setSelectedTables] = useState([]);

  const [formData, setFormData] = useState({
    restaurantId: '',
    tableNumber: '',
    tableName: '',
    capacity: 4,
    section: '',
    notes: '',
    active: true
  });

  const tableStatuses = ['AVAILABLE', 'OCCUPIED', 'RESERVED', 'CLEANING', 'OUT_OF_SERVICE'];

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
      loadTables();
      loadStats();
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll();
      console.log('Restaurants response:', response);
      console.log('Restaurants data:', response.data);

      // Handle paginated response - restaurants are in content array
      const restaurantsData = response?.data?.data?.content || response?.data?.data;
      console.log('Restaurants array:', restaurantsData);

      const restaurantsArray = Array.isArray(restaurantsData) ? restaurantsData : [];
      setRestaurants(restaurantsArray);

      // Set first restaurant as default if no restaurant selected and user has no restaurant assigned
      if (!selectedRestaurant && restaurantsArray.length > 0 && !user?.restaurantId) {
        const firstRestaurantId = restaurantsArray[0].id;
        setSelectedRestaurant(firstRestaurantId);
        setFormData(prev => ({ ...prev, restaurantId: firstRestaurantId }));
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
      setRestaurants([]);
    }
  };

  const loadTables = async () => {
    try {
      setLoading(true);
      const response = await tablesAPI.getAll(selectedRestaurant);
      console.log('Tables response:', response);
      console.log('Tables data:', response.data);

      // Handle both paginated and non-paginated responses
      const tablesData = response?.data?.data?.content || response?.data?.data;
      console.log('Tables array:', tablesData);

      setTables(Array.isArray(tablesData) ? tablesData : []);
    } catch (error) {
      console.error('Failed to load tables:', error);
      setTables([]);
    } finally {
      setLoading(false);
    }
  };

  const loadStats = async () => {
    try {
      const response = await tablesAPI.getStats(selectedRestaurant);
      setStats(response.data.data || { totalTables: 0, availableTables: 0, occupiedTables: 0, reservedTables: 0 });
    } catch (error) {
      console.error('Failed to load stats:', error);
      setStats({ totalTables: 0, availableTables: 0, occupiedTables: 0, reservedTables: 0 });
    }
  };

  const handleSave = async (e) => {
    e.preventDefault();
    try {
      if (!formData.tableNumber || !formData.capacity) {
        alert(t('tables.messages.fillRequiredFields'));
        return;
      }

      setLoading(true);
      if (editingTable) {
        await tablesAPI.update(editingTable.id, formData);
        alert(t('tables.messages.updateSuccess'));
      } else {
        await tablesAPI.create({ ...formData, restaurantId: selectedRestaurant });
        alert(t('tables.messages.createSuccess'));
      }
      setShowModal(false);
      resetForm();
      loadTables();
      loadStats();
    } catch (error) {
      console.error('Failed to save table:', error);
      alert(t('tables.messages.saveError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = (table) => {
    setEditingTable(table);
    setFormData({
      restaurantId: table.restaurantId,
      tableNumber: table.tableNumber,
      tableName: table.tableName || '',
      capacity: table.capacity,
      section: table.section || '',
      notes: table.notes || '',
      active: table.active
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('tables.confirmDelete'))) return;

    try {
      await tablesAPI.delete(id);
      alert(t('tables.messages.deleteSuccess'));
      loadTables();
      loadStats();
    } catch (error) {
      console.error('Failed to delete table:', error);
      alert(t('tables.messages.deleteError'));
    }
  };

  const handleStatusChange = async (id, status) => {
    try {
      await tablesAPI.updateStatus(id, status);
      loadTables();
      loadStats();
    } catch (error) {
      console.error('Failed to update status:', error);
      alert(t('tables.messages.statusError'));
    }
  };

  const resetForm = () => {
    setEditingTable(null);
    setFormData({
      restaurantId: selectedRestaurant,
      tableNumber: '',
      tableName: '',
      capacity: 4,
      section: '',
      notes: '',
      active: true
    });
  };

  const toggleTableSelection = (tableId) => {
    setSelectedTables(prev => {
      if (prev.includes(tableId)) {
        return prev.filter(id => id !== tableId);
      } else {
        return [...prev, tableId];
      }
    });
  };

  const handleMergeTables = async () => {
    if (selectedTables.length < 2) {
      alert(t('tables.messages.selectAtLeast2Tables', 'Please select at least 2 tables to merge'));
      return;
    }

    const mainTableId = selectedTables[0];
    const tableIdsToMerge = selectedTables.slice(1);

    try {
      setLoading(true);
      await tablesAPI.merge({ mainTableId, tableIdsToMerge });
      alert(t('tables.messages.mergeSuccess', 'Tables merged successfully'));
      setSelectedTables([]);
      loadTables();
      loadStats();
    } catch (error) {
      console.error('Failed to merge tables:', error);
      alert(t('tables.messages.mergeError', 'Failed to merge tables') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleUnmergeTables = async (tableId) => {
    if (!window.confirm(t('tables.confirmUnmerge', 'Are you sure you want to unmerge these tables?'))) return;

    try {
      setLoading(true);
      await tablesAPI.unmerge(tableId);
      alert(t('tables.messages.unmergeSuccess', 'Tables unmerged successfully'));
      setSelectedTables([]);
      loadTables();
      loadStats();
    } catch (error) {
      console.error('Failed to unmerge tables:', error);
      alert(t('tables.messages.unmergeError', 'Failed to unmerge tables'));
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    const statusColors = {
      AVAILABLE: 'bg-green-100 text-green-800',
      OCCUPIED: 'bg-red-100 text-red-800',
      RESERVED: 'bg-blue-100 text-blue-800',
      CLEANING: 'bg-yellow-100 text-yellow-800',
      OUT_OF_SERVICE: 'bg-gray-100 text-gray-800'
    };
    return <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[status] || 'bg-gray-100 text-gray-800'}`}>
      {t(`tables.status.${status}`)}
    </span>;
  };

  const filteredTables = Array.isArray(tables) ? tables.filter(table => {
    const matchesSearch = table.tableNumber?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      table.tableName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      table.section?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = filterStatus === 'all' || table.status === filterStatus;
    return matchesSearch && matchesStatus;
  }) : [];

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">{t('tables.title')}</h1>
        <div className="flex gap-2">
          {selectedTables.length >= 2 && (
            <button
              onClick={handleMergeTables}
              className="flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
            >
              <GitMerge size={20} />
              {t('tables.mergeTables', 'Merge {{count}} Tables', { count: selectedTables.length })}
            </button>
          )}
          <button
            onClick={() => { resetForm(); setShowModal(true); }}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            <Plus size={20} />
            {t('tables.createNew')}
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">{t('tables.stats.total')}</div>
          <div className="text-2xl font-bold text-gray-900">{stats.totalTables}</div>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">{t('tables.stats.available')}</div>
          <div className="text-2xl font-bold text-green-600">{stats.availableTables}</div>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">{t('tables.stats.occupied')}</div>
          <div className="text-2xl font-bold text-red-600">{stats.occupiedTables}</div>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="text-sm text-gray-600 mb-1">{t('tables.stats.reserved')}</div>
          <div className="text-2xl font-bold text-blue-600">{stats.reservedTables}</div>
        </div>
      </div>

      {/* Filters */}
      <div className="mb-4 flex gap-4">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => {
            const restaurantId = e.target.value ? parseInt(e.target.value) : null;
            setSelectedRestaurant(restaurantId);
            setFormData(prev => ({ ...prev, restaurantId }));
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">{t('common.selectRestaurant')}</option>
          {Array.isArray(restaurants) && restaurants.map(restaurant => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
        <input
          type="text"
          placeholder={t('tables.searchPlaceholder')}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">{t('common.allStatuses')}</option>
          {tableStatuses.map(status => (
            <option key={status} value={status}>{t(`tables.status.${status}`)}</option>
          ))}
        </select>
      </div>

      {/* Tables Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {!selectedRestaurant ? (
          <div className="col-span-full text-center py-12">
            <div className="text-gray-400 mb-2">
              <Grid size={48} className="mx-auto mb-2" />
            </div>
            <p className="text-gray-600">{t('tables.selectRestaurantFirst', 'Please select a restaurant to view tables')}</p>
          </div>
        ) : loading ? (
          <div className="col-span-full text-center py-8 text-gray-500">{t('common.loading')}</div>
        ) : filteredTables.length === 0 ? (
          <div className="col-span-full text-center py-8 text-gray-500">{t('tables.noTables')}</div>
        ) : (
          filteredTables.map((table) => (
            <div key={table.id} className={`bg-white rounded-lg shadow p-4 hover:shadow-lg transition-shadow ${selectedTables.includes(table.id) ? 'ring-2 ring-purple-500' : ''} ${table.mergedTable ? 'border-2 border-purple-300' : ''}`}>
              {/* Merged Table Indicator Badge */}
              {table.mergedTable && (
                <div className="mb-2 flex items-center gap-2 px-2 py-1 bg-purple-100 text-purple-800 rounded-md text-xs font-medium">
                  <GitMerge size={14} />
                  <span>{t('tables.merged', 'Merged Table')}</span>
                  {table.originalCapacity && (
                    <span className="text-purple-600">({t('tables.originalCapacity', 'Original')}: {table.originalCapacity})</span>
                  )}
                </div>
              )}

              <div className="flex justify-between items-start mb-3">
                <div className="flex items-start gap-2 flex-1">
                  <input
                    type="checkbox"
                    checked={selectedTables.includes(table.id)}
                    onChange={() => toggleTableSelection(table.id)}
                    className="mt-1 h-4 w-4 text-purple-600 rounded"
                  />
                  <div>
                    <h3 className="text-lg font-bold text-gray-900">{table.tableNumber}</h3>
                    {table.tableName && <p className="text-sm text-gray-600">{table.tableName}</p>}
                  </div>
                </div>
                <div className="flex gap-1">
                  {table.mergedTable && (
                    <button
                      onClick={() => handleUnmergeTables(table.id)}
                      className="p-1 text-white bg-purple-600 hover:bg-purple-700 rounded"
                      title={t('tables.unmerge', 'Unmerge tables')}
                    >
                      <GitBranch size={16} />
                    </button>
                  )}
                  <button onClick={() => handleEdit(table)} className="p-1 text-blue-600 hover:bg-blue-50 rounded">
                    <Edit size={16} />
                  </button>
                  <button onClick={() => handleDelete(table.id)} className="p-1 text-red-600 hover:bg-red-50 rounded">
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>

              <div className="space-y-2 mb-3">
                <div className="flex items-center gap-2 text-sm text-gray-600">
                  <Users size={16} />
                  <span>{t('tables.capacity')}: {table.capacity}</span>
                  {table.mergedTable && table.originalCapacity && table.capacity !== table.originalCapacity && (
                    <span className="text-purple-600 font-medium">↑ {t('tables.increased', 'Increased')}</span>
                  )}
                </div>
                {table.section && (
                  <div className="flex items-center gap-2 text-sm text-gray-600">
                    <Grid size={16} />
                    <span>{t('tables.section')}: {table.section}</span>
                  </div>
                )}
              </div>

              <div className="flex justify-between items-center">
                {getStatusBadge(table.status)}
                <select
                  value={table.status}
                  onChange={(e) => handleStatusChange(table.id, e.target.value)}
                  className="text-xs px-2 py-1 border border-gray-300 rounded"
                  onClick={(e) => e.stopPropagation()}
                >
                  {tableStatuses.map(status => (
                    <option key={status} value={status}>{t(`tables.status.${status}`)}</option>
                  ))}
                </select>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-bold mb-4">{editingTable ? t('tables.editTable') : t('tables.createNew')}</h2>

            <form onSubmit={handleSave}>
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('tables.tableNumber')} *</label>
                <input
                  type="text"
                  value={formData.tableNumber}
                  onChange={(e) => setFormData({ ...formData, tableNumber: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                />
              </div>

              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('tables.tableName')}</label>
                <input
                  type="text"
                  value={formData.tableName}
                  onChange={(e) => setFormData({ ...formData, tableName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>

              <div className="grid grid-cols-2 gap-4 mb-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('tables.capacity')} *</label>
                  <input
                    type="number"
                    value={formData.capacity}
                    onChange={(e) => setFormData({ ...formData, capacity: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    min="1"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('tables.section')}</label>
                  <input
                    type="text"
                    value={formData.section}
                    onChange={(e) => setFormData({ ...formData, section: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  />
                </div>
              </div>

              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('tables.notes')}</label>
                <textarea
                  value={formData.notes}
                  onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  rows="2"
                />
              </div>

              <div className="mb-4 flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="h-4 w-4 text-blue-600 rounded"
                />
                <label className="text-sm font-medium text-gray-700">{t('tables.active')}</label>
              </div>

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => { setShowModal(false); resetForm(); }}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                >
                  {loading ? t('common.saving') : (editingTable ? t('common.update') : t('common.create'))}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Tables;
