import React, { useState, useEffect } from 'react';
import { kitchenStationAPI, printerAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { Plus, Edit, Trash2, Printer, CheckCircle, XCircle, ChefHat } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const KitchenStations = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [stations, setStations] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [printers, setPrinters] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingStation, setEditingStation] = useState(null);

  const [formData, setFormData] = useState({
    restaurantId: null,
    name: '',
    description: '',
    printerId: null,
    color: '#3B82F6',
    sortOrder: 0,
    active: true
  });

  const colorOptions = [
    { value: '#3B82F6', label: 'Blue' },
    { value: '#EF4444', label: 'Red' },
    { value: '#10B981', label: 'Green' },
    { value: '#F59E0B', label: 'Orange' },
    { value: '#8B5CF6', label: 'Purple' },
    { value: '#EC4899', label: 'Pink' },
    { value: '#6366F1', label: 'Indigo' },
    { value: '#14B8A6', label: 'Teal' },
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
      loadStations(selectedRestaurant);
      loadPrinters(selectedRestaurant);
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantsData = response.data.data?.content || response.data.data || [];
      setRestaurants(Array.isArray(restaurantsData) ? restaurantsData : []);

      if (!selectedRestaurant && restaurantsData.length > 0 && !user?.restaurantId) {
        const firstRestaurantId = restaurantsData[0].id;
        setSelectedRestaurant(firstRestaurantId);
        setFormData(prev => ({ ...prev, restaurantId: firstRestaurantId }));
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
      setRestaurants([]);
    }
  };

  const loadStations = async (restaurantId) => {
    try {
      setLoading(true);
      const response = await kitchenStationAPI.getStations(restaurantId);
      setStations(response.data.data || []);
    } catch (error) {
      console.error('Failed to load kitchen stations:', error);
      setStations([]);
    } finally {
      setLoading(false);
    }
  };

  const loadPrinters = async (restaurantId) => {
    try {
      const response = await printerAPI.getPrinters(restaurantId);
      setPrinters(response.data.data || []);
    } catch (error) {
      console.error('Failed to load printers:', error);
      setPrinters([]);
    }
  };

  const handleSave = async () => {
    try {
      if (!formData.name) {
        alert(t('kitchenStations.messages.fillRequiredFields', 'Please fill in all required fields'));
        return;
      }

      setLoading(true);
      const payload = {
        ...formData,
        restaurantId: selectedRestaurant,
        printerId: formData.printerId || null
      };

      if (editingStation) {
        await kitchenStationAPI.updateStation(editingStation.id, payload);
        alert(t('kitchenStations.messages.updateSuccess', 'Kitchen station updated successfully'));
      } else {
        await kitchenStationAPI.createStation(payload);
        alert(t('kitchenStations.messages.createSuccess', 'Kitchen station created successfully'));
      }

      setShowModal(false);
      setEditingStation(null);
      resetForm();
      loadStations(selectedRestaurant);
    } catch (error) {
      console.error('Failed to save kitchen station:', error);
      alert(t('kitchenStations.messages.saveError', 'Failed to save kitchen station') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = (station) => {
    setEditingStation(station);
    setFormData({
      restaurantId: station.restaurantId,
      name: station.name,
      description: station.description || '',
      printerId: station.printerId || null,
      color: station.color || '#3B82F6',
      sortOrder: station.sortOrder || 0,
      active: station.active !== undefined ? station.active : true
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!confirm(t('kitchenStations.messages.confirmDelete', 'Are you sure you want to delete this kitchen station?'))) {
      return;
    }

    try {
      setLoading(true);
      await kitchenStationAPI.deleteStation(id);
      alert(t('kitchenStations.messages.deleteSuccess', 'Kitchen station deleted successfully'));
      loadStations(selectedRestaurant);
    } catch (error) {
      console.error('Failed to delete kitchen station:', error);
      alert(t('kitchenStations.messages.deleteError', 'Failed to delete kitchen station'));
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = async (id) => {
    try {
      await kitchenStationAPI.toggleStation(id);
      loadStations(selectedRestaurant);
    } catch (error) {
      console.error('Failed to toggle kitchen station:', error);
      alert(t('kitchenStations.messages.toggleError', 'Failed to toggle kitchen station'));
    }
  };

  const resetForm = () => {
    setFormData({
      restaurantId: selectedRestaurant,
      name: '',
      description: '',
      printerId: null,
      color: '#3B82F6',
      sortOrder: 0,
      active: true
    });
  };

  const openCreateModal = () => {
    resetForm();
    setEditingStation(null);
    setShowModal(true);
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <ChefHat className="h-6 w-6" />
            {t('kitchenStations.title', 'Kitchen Stations')}
          </h1>
          <p className="text-gray-500 mt-1">
            {t('kitchenStations.description', 'Configure kitchen stations for ticket routing to different printers')}
          </p>
        </div>
        <button
          onClick={openCreateModal}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          {t('kitchenStations.addStation', 'Add Station')}
        </button>
      </div>

      {/* Restaurant Filter */}
      <div className="mb-4">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => {
            const restaurantId = e.target.value ? parseInt(e.target.value) : null;
            setSelectedRestaurant(restaurantId);
            setFormData(prev => ({ ...prev, restaurantId: restaurantId }));
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">{t('finance.common.selectRestaurant', 'Select Restaurant')}</option>
          {(Array.isArray(restaurants) ? restaurants : []).map(restaurant => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
      </div>

      {/* Info Box */}
      <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
        <h3 className="font-semibold text-blue-800 mb-2">
          {t('kitchenStations.howItWorks', 'How Kitchen Station Routing Works')}
        </h3>
        <ul className="text-sm text-blue-700 space-y-1">
          <li>1. {t('kitchenStations.step1', 'Create kitchen stations (e.g., Fryer, Grill, Cold Station)')}</li>
          <li>2. {t('kitchenStations.step2', 'Assign a printer to each station')}</li>
          <li>3. {t('kitchenStations.step3', 'In Categories page, assign each category to a kitchen station')}</li>
          <li>4. {t('kitchenStations.step4', 'When orders are created, items will automatically print to the correct station printer')}</li>
        </ul>
      </div>

      {/* Stations List */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('kitchenStations.stationName', 'Station Name')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('kitchenStations.description', 'Description')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('kitchenStations.printer', 'Printer')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('kitchenStations.color', 'Color')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('kitchenStations.status', 'Status')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.actions', 'Actions')}</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                <td colSpan="6" className="px-6 py-4 text-center text-gray-500">{t('finance.common.loading', 'Loading...')}</td>
              </tr>
            ) : stations.length === 0 ? (
              <tr>
                <td colSpan="6" className="px-6 py-4 text-center text-gray-500">
                  {t('kitchenStations.noStations', 'No kitchen stations configured. Click "Add Station" to create one.')}
                </td>
              </tr>
            ) : (
              stations.map((station) => (
                <tr key={station.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center gap-2">
                      <div
                        className="w-4 h-4 rounded-full"
                        style={{ backgroundColor: station.color || '#3B82F6' }}
                      />
                      <span className="font-medium text-gray-900">{station.name}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-500">
                    {station.description || '-'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {station.printerName ? (
                      <div className="flex items-center gap-1">
                        <Printer size={16} className="text-gray-400" />
                        {station.printerName}
                      </div>
                    ) : (
                      <span className="text-orange-500">{t('kitchenStations.noPrinter', 'No printer assigned')}</span>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div
                      className="w-8 h-8 rounded-lg border-2 border-gray-200"
                      style={{ backgroundColor: station.color || '#3B82F6' }}
                    />
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <button
                      onClick={() => handleToggle(station.id)}
                      className={`px-2 py-1 rounded-full text-xs font-medium ${
                        station.active
                          ? 'bg-green-100 text-green-800'
                          : 'bg-red-100 text-red-800'
                      }`}
                    >
                      {station.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                    </button>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleEdit(station)}
                        className="text-indigo-600 hover:text-indigo-900"
                        title={t('finance.common.edit', 'Edit')}
                      >
                        <Edit size={18} />
                      </button>
                      <button
                        onClick={() => handleDelete(station.id)}
                        className="text-red-600 hover:text-red-900"
                        title={t('finance.common.delete', 'Delete')}
                      >
                        <Trash2 size={18} />
                      </button>
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
          <div className="bg-white rounded-lg p-6 w-full max-w-lg my-8">
            <h2 className="text-xl font-bold mb-4">
              {editingStation ? t('kitchenStations.editStation', 'Edit Kitchen Station') : t('kitchenStations.addStation', 'Add Kitchen Station')}
            </h2>

            <div className="space-y-4 mb-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('kitchenStations.stationName', 'Station Name')} *
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                  placeholder={t('kitchenStations.stationNamePlaceholder', 'e.g., Fryer, Grill, Cold Station')}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('kitchenStations.description', 'Description')}
                </label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                  rows="2"
                  placeholder={t('kitchenStations.descriptionPlaceholder', 'Optional description for this station')}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('kitchenStations.printer', 'Printer')}
                </label>
                <select
                  value={formData.printerId || ''}
                  onChange={(e) => setFormData({ ...formData, printerId: e.target.value ? parseInt(e.target.value) : null })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">{t('kitchenStations.selectPrinter', 'Select a printer...')}</option>
                  {printers.map((printer) => (
                    <option key={printer.id} value={printer.id}>
                      {printer.printerName} ({printer.printerType})
                    </option>
                  ))}
                </select>
                {printers.length === 0 && (
                  <p className="text-sm text-orange-500 mt-1">
                    {t('kitchenStations.noPrintersAvailable', 'No printers configured. Go to Printer Settings to add printers first.')}
                  </p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('kitchenStations.color', 'Color')}
                </label>
                <div className="flex gap-2 flex-wrap">
                  {colorOptions.map((color) => (
                    <button
                      key={color.value}
                      type="button"
                      onClick={() => setFormData({ ...formData, color: color.value })}
                      className={`w-8 h-8 rounded-lg border-2 ${
                        formData.color === color.value ? 'border-gray-800 ring-2 ring-offset-2 ring-gray-400' : 'border-gray-200'
                      }`}
                      style={{ backgroundColor: color.value }}
                      title={color.label}
                    />
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('kitchenStations.sortOrder', 'Sort Order')}
                </label>
                <input
                  type="number"
                  value={formData.sortOrder}
                  onChange={(e) => setFormData({ ...formData, sortOrder: parseInt(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                  min="0"
                />
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="active"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="rounded"
                />
                <label htmlFor="active" className="text-sm font-medium text-gray-700">
                  {t('common.active', 'Active')}
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowModal(false); setEditingStation(null); resetForm(); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel', 'Cancel')}
              </button>
              <button
                onClick={handleSave}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? t('finance.common.saving', 'Saving...') : t('finance.common.save', 'Save')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default KitchenStations;
