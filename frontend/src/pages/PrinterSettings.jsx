import React, { useState, useEffect } from 'react';
import { printerAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { Plus, Edit, Trash2, Printer, CheckCircle, XCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const PrinterSettings = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [printers, setPrinters] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [availablePrinters, setAvailablePrinters] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingPrinter, setEditingPrinter] = useState(null);
  const [testingPrinter, setTestingPrinter] = useState(null);

  const [formData, setFormData] = useState({
    restaurant: { id: null },
    printerType: 'KITCHEN',
    printerName: '',
    ipAddress: '',
    port: 9100,
    connectionType: 'USB',
    paperWidth: 80,
    fontSize: 12,
    autoPrint: false,
    enabled: true,
    notes: ''
  });

  useEffect(() => {
    loadRestaurants();
    loadAvailablePrinters();
  }, []);

  useEffect(() => {
    if (user?.restaurantId && !selectedRestaurant) {
      setSelectedRestaurant(user.restaurantId);
      setFormData(prev => ({ ...prev, restaurant: { id: user.restaurantId } }));
    }
  }, [user]);

  useEffect(() => {
    if (selectedRestaurant) {
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
        setFormData(prev => ({ ...prev, restaurant: { id: firstRestaurantId } }));
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
      setRestaurants([]);
    }
  };

  const loadPrinters = async (restaurantId) => {
    try {
      setLoading(true);
      const response = await printerAPI.getPrinters(restaurantId);
      setPrinters(response.data.data || []);
    } catch (error) {
      console.error('Failed to load printers:', error);
      setPrinters([]);
    } finally {
      setLoading(false);
    }
  };

  const loadAvailablePrinters = async () => {
    try {
      const response = await printerAPI.getAvailablePrinters();
      setAvailablePrinters(response.data.data || []);
    } catch (error) {
      console.error('Failed to load available printers:', error);
      setAvailablePrinters([]);
    }
  };

  const handleSave = async () => {
    try {
      if (!formData.printerName) {
        alert(t('printers.messages.fillRequiredFields'));
        return;
      }

      setLoading(true);
      if (editingPrinter) {
        await printerAPI.updatePrinter(editingPrinter.id, formData);
        alert(t('printers.messages.updateSuccess'));
      } else {
        await printerAPI.createPrinter(formData);
        alert(t('printers.messages.createSuccess'));
      }

      setShowModal(false);
      setEditingPrinter(null);
      resetForm();
      loadPrinters(selectedRestaurant);
    } catch (error) {
      console.error('Failed to save printer:', error);
      alert(t('printers.messages.saveError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = (printer) => {
    setEditingPrinter(printer);
    setFormData({
      restaurant: printer.restaurant,
      printerType: printer.printerType,
      printerName: printer.printerName,
      ipAddress: printer.ipAddress || '',
      port: printer.port || 9100,
      connectionType: printer.connectionType || 'USB',
      paperWidth: printer.paperWidth || 80,
      fontSize: printer.fontSize || 12,
      autoPrint: printer.autoPrint || false,
      enabled: printer.enabled || true,
      notes: printer.notes || ''
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!confirm(t('printers.messages.confirmDelete'))) {
      return;
    }

    try {
      setLoading(true);
      await printerAPI.deletePrinter(id);
      alert(t('printers.messages.deleteSuccess'));
      loadPrinters(selectedRestaurant);
    } catch (error) {
      console.error('Failed to delete printer:', error);
      alert(t('printers.messages.deleteError'));
    } finally {
      setLoading(false);
    }
  };

  const handleTest = async (id) => {
    try {
      setTestingPrinter(id);
      const response = await printerAPI.testPrinter(id);
      const success = response.data.data;

      if (success) {
        alert(t('printers.messages.testSuccess'));
      } else {
        alert(t('printers.messages.testFailed'));
      }
    } catch (error) {
      console.error('Failed to test printer:', error);
      alert(t('printers.messages.testError'));
    } finally {
      setTestingPrinter(null);
    }
  };

  const resetForm = () => {
    setFormData({
      restaurant: { id: selectedRestaurant },
      printerType: 'KITCHEN',
      printerName: '',
      ipAddress: '',
      port: 9100,
      connectionType: 'USB',
      paperWidth: 80,
      fontSize: 12,
      autoPrint: false,
      enabled: true,
      notes: ''
    });
  };

  const openCreateModal = () => {
    resetForm();
    setEditingPrinter(null);
    setShowModal(true);
  };

  const getPrinterTypeLabel = (type) => {
    const labels = {
      KITCHEN: t('printers.types.kitchen'),
      CUSTOMER: t('printers.types.customer'),
      LABEL: t('printers.types.label'),
      REPORT: t('printers.types.report')
    };
    return labels[type] || type;
  };

  const getConnectionTypeLabel = (type) => {
    const labels = {
      USB: 'USB',
      NETWORK: t('printers.connectionTypes.network'),
      BLUETOOTH: t('printers.connectionTypes.bluetooth')
    };
    return labels[type] || type;
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">{t('printers.title')}</h1>
        <button
          onClick={openCreateModal}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          {t('printers.addPrinter')}
        </button>
      </div>

      {/* Restaurant Filter */}
      <div className="mb-4">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => {
            const restaurantId = e.target.value ? parseInt(e.target.value) : null;
            setSelectedRestaurant(restaurantId);
            setFormData(prev => ({ ...prev, restaurant: { id: restaurantId } }));
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
      </div>

      {/* Printers List */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('printers.printerName')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('printers.type')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('printers.connection')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('printers.autoPrint')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('printers.status')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.actions')}</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                <td colSpan="6" className="px-6 py-4 text-center text-gray-500">{t('finance.common.loading')}</td>
              </tr>
            ) : printers.length === 0 ? (
              <tr>
                <td colSpan="6" className="px-6 py-4 text-center text-gray-500">{t('printers.noPrinters')}</td>
              </tr>
            ) : (
              printers.map((printer) => (
                <tr key={printer.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {printer.printerName}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {getPrinterTypeLabel(printer.printerType)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {getConnectionTypeLabel(printer.connectionType)}
                    {printer.connectionType === 'NETWORK' && printer.ipAddress && (
                      <div className="text-xs text-gray-400">{printer.ipAddress}:{printer.port}</div>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {printer.autoPrint ? (
                      <CheckCircle size={20} className="text-green-500" />
                    ) : (
                      <XCircle size={20} className="text-gray-300" />
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    {printer.enabled ? (
                      <span className="px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                        {t('printers.enabled')}
                      </span>
                    ) : (
                      <span className="px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800">
                        {t('printers.disabled')}
                      </span>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleTest(printer.id)}
                        disabled={testingPrinter === printer.id}
                        className="text-blue-600 hover:text-blue-900 disabled:opacity-50"
                        title={t('printers.test')}
                      >
                        <Printer size={18} />
                      </button>
                      <button
                        onClick={() => handleEdit(printer)}
                        className="text-indigo-600 hover:text-indigo-900"
                        title={t('finance.common.edit')}
                      >
                        <Edit size={18} />
                      </button>
                      <button
                        onClick={() => handleDelete(printer.id)}
                        className="text-red-600 hover:text-red-900"
                        title={t('finance.common.delete')}
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
          <div className="bg-white rounded-lg p-6 w-full max-w-2xl my-8">
            <h2 className="text-xl font-bold mb-4">
              {editingPrinter ? t('printers.editPrinter') : t('printers.addPrinter')}
            </h2>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('printers.type')} *
                </label>
                <select
                  value={formData.printerType}
                  onChange={(e) => setFormData({ ...formData, printerType: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                >
                  <option value="KITCHEN">{t('printers.types.kitchen')}</option>
                  <option value="CUSTOMER">{t('printers.types.customer')}</option>
                  <option value="LABEL">{t('printers.types.label')}</option>
                  <option value="REPORT">{t('printers.types.report')}</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('printers.printerName')} *
                </label>
                <select
                  value={formData.printerName}
                  onChange={(e) => setFormData({ ...formData, printerName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                >
                  <option value="">{t('printers.selectPrinter')}</option>
                  {availablePrinters.map((printer, index) => (
                    <option key={index} value={printer}>
                      {printer}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('printers.connectionType')}
                </label>
                <select
                  value={formData.connectionType}
                  onChange={(e) => setFormData({ ...formData, connectionType: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                >
                  <option value="USB">USB</option>
                  <option value="NETWORK">{t('printers.connectionTypes.network')}</option>
                  <option value="BLUETOOTH">{t('printers.connectionTypes.bluetooth')}</option>
                </select>
              </div>

              {formData.connectionType === 'NETWORK' && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('printers.ipAddress')}
                    </label>
                    <input
                      type="text"
                      value={formData.ipAddress}
                      onChange={(e) => setFormData({ ...formData, ipAddress: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                      placeholder="192.168.1.100"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('printers.port')}
                    </label>
                    <input
                      type="number"
                      value={formData.port}
                      onChange={(e) => setFormData({ ...formData, port: parseInt(e.target.value) })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    />
                  </div>
                </>
              )}

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('printers.paperWidth')} (mm)
                </label>
                <input
                  type="number"
                  value={formData.paperWidth}
                  onChange={(e) => setFormData({ ...formData, paperWidth: parseInt(e.target.value) })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('printers.fontSize')}
                </label>
                <input
                  type="number"
                  value={formData.fontSize}
                  onChange={(e) => setFormData({ ...formData, fontSize: parseInt(e.target.value) })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                {t('finance.common.notes')}
              </label>
              <textarea
                value={formData.notes}
                onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                rows="2"
              />
            </div>

            <div className="flex gap-4 mb-4">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={formData.autoPrint}
                  onChange={(e) => setFormData({ ...formData, autoPrint: e.target.checked })}
                  className="rounded"
                />
                <span className="text-sm font-medium text-gray-700">{t('printers.autoPrint')}</span>
              </label>

              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={formData.enabled}
                  onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                  className="rounded"
                />
                <span className="text-sm font-medium text-gray-700">{t('printers.enabled')}</span>
              </label>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowModal(false); setEditingPrinter(null); resetForm(); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel')}
              </button>
              <button
                onClick={handleSave}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? t('finance.common.saving') : t('finance.common.save')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PrinterSettings;
