import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { qrCodeAPI, restaurantAPI } from '../services/api';
import {
  QrCode,
  Plus,
  Download,
  Trash2,
  ToggleLeft,
  ToggleRight,
  RefreshCw,
  Settings,
  Scan,
} from 'lucide-react';

export default function QRCodes() {
  const { t } = useTranslation();
  const [qrCodes, setQrCodes] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  const [settings, setSettings] = useState(null);
  const [showQRModal, setShowQRModal] = useState(false);
  const [selectedQR, setSelectedQR] = useState(null);
  const [qrImage, setQrImage] = useState(null);

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadQRCodes();
      loadStats();
      loadSettings();
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll();
      const data = response.data?.content || response.data || [];
      setRestaurants(data);
      if (data.length > 0) {
        setSelectedRestaurant(data[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadQRCodes = async () => {
    setLoading(true);
    try {
      const response = await qrCodeAPI.getByRestaurant(selectedRestaurant);
      setQrCodes(response.data?.content || response.data || []);
    } catch (error) {
      console.error('Failed to load QR codes:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadStats = async () => {
    try {
      const response = await qrCodeAPI.getStats(selectedRestaurant);
      setStats(response.data);
    } catch (error) {
      console.error('Failed to load stats:', error);
    }
  };

  const loadSettings = async () => {
    try {
      const response = await qrCodeAPI.getSettings(selectedRestaurant);
      setSettings(response.data);
    } catch (error) {
      setSettings({
        enabled: false,
        requirePayment: false,
        allowTakeaway: true,
        allowDineIn: true,
        minimumOrderAmount: 0,
        estimatedPrepTimeMinutes: 15,
        autoAcceptOrders: false,
      });
    }
  };

  const handleGenerateAll = async () => {
    try {
      const response = await qrCodeAPI.generateForAllTables(selectedRestaurant);
      alert(t('qrCodes.generatedSuccess', { count: response.data.generated }));
      loadQRCodes();
      loadStats();
    } catch (error) {
      alert(t('qrCodes.generateError'));
    }
  };

  const handleToggle = async (id) => {
    try {
      await qrCodeAPI.toggle(id);
      loadQRCodes();
    } catch (error) {
      alert(t('qrCodes.toggleError'));
    }
  };

  const handleDelete = async (id) => {
    if (!confirm(t('qrCodes.confirmDelete'))) return;
    try {
      await qrCodeAPI.delete(id);
      loadQRCodes();
      loadStats();
    } catch (error) {
      alert(t('qrCodes.deleteError'));
    }
  };

  const handleViewQR = async (qr) => {
    setSelectedQR(qr);
    setShowQRModal(true);
    try {
      const response = await qrCodeAPI.getImage(qr.id, 400, 400);
      setQrImage(response.data.image);
    } catch (error) {
      console.error('Failed to load QR image:', error);
    }
  };

  const handleDownloadQR = () => {
    if (!qrImage) return;
    const link = document.createElement('a');
    link.href = qrImage;
    link.download = `qr-${selectedQR.code}.png`;
    link.click();
  };

  const handleSaveSettings = async () => {
    try {
      await qrCodeAPI.saveSettings(selectedRestaurant, settings);
      alert(t('qrCodes.settingsSaved'));
      setShowSettings(false);
    } catch (error) {
      alert(t('qrCodes.settingsError'));
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('qrCodes.title', 'QR Codes')}</h1>
          <p className="text-gray-500">{t('qrCodes.description', 'Manage QR codes for self-service ordering')}</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={selectedRestaurant || ''}
            onChange={(e) => setSelectedRestaurant(Number(e.target.value))}
            className="border rounded-lg px-3 py-2"
          >
            {restaurants.map((r) => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>
          <button
            onClick={() => setShowSettings(true)}
            className="flex items-center gap-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200"
          >
            <Settings className="w-4 h-4" />
            {t('common.settings', 'Settings')}
          </button>
          <button
            onClick={handleGenerateAll}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            <RefreshCw className="w-4 h-4" />
            {t('qrCodes.generateAll', 'Generate for Tables')}
          </button>
        </div>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-white p-4 rounded-lg shadow">
            <div className="flex items-center gap-3">
              <QrCode className="w-8 h-8 text-blue-600" />
              <div>
                <p className="text-sm text-gray-500">{t('qrCodes.activeQRCodes', 'Active QR Codes')}</p>
                <p className="text-2xl font-bold">{stats.activeQRCodes}</p>
              </div>
            </div>
          </div>
          <div className="bg-white p-4 rounded-lg shadow">
            <div className="flex items-center gap-3">
              <Scan className="w-8 h-8 text-green-600" />
              <div>
                <p className="text-sm text-gray-500">{t('qrCodes.totalScans', 'Total Scans')}</p>
                <p className="text-2xl font-bold">{stats.totalScans}</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* QR Codes List */}
      <div className="bg-white rounded-lg shadow">
        <div className="p-4 border-b">
          <h2 className="text-lg font-semibold">{t('qrCodes.list', 'QR Codes')}</h2>
        </div>
        {loading ? (
          <div className="p-8 text-center text-gray-500">{t('common.loading', 'Loading...')}</div>
        ) : qrCodes.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            {t('qrCodes.empty', 'No QR codes yet. Generate QR codes for your tables.')}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('qrCodes.code', 'Code')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('qrCodes.name', 'Name')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('qrCodes.table', 'Table')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('qrCodes.type', 'Type')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('qrCodes.scans', 'Scans')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('qrCodes.status', 'Status')}</th>
                  <th className="px-4 py-3 text-right text-sm font-medium text-gray-500">{t('common.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {qrCodes.map((qr) => (
                  <tr key={qr.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <button
                        onClick={() => handleViewQR(qr)}
                        className="font-mono text-blue-600 hover:underline"
                      >
                        {qr.code}
                      </button>
                    </td>
                    <td className="px-4 py-3">{qr.name}</td>
                    <td className="px-4 py-3">{qr.table?.tableNumber || '-'}</td>
                    <td className="px-4 py-3">
                      <span className="px-2 py-1 text-xs bg-gray-100 rounded">{qr.qrType}</span>
                    </td>
                    <td className="px-4 py-3">{qr.scanCount}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-1 text-xs rounded ${qr.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
                        {qr.isActive ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => handleViewQR(qr)}
                          className="p-1 text-blue-600 hover:bg-blue-50 rounded"
                          title={t('qrCodes.view', 'View QR')}
                        >
                          <QrCode className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleToggle(qr.id)}
                          className="p-1 text-gray-600 hover:bg-gray-50 rounded"
                          title={t('common.toggle', 'Toggle')}
                        >
                          {qr.isActive ? <ToggleRight className="w-4 h-4 text-green-600" /> : <ToggleLeft className="w-4 h-4" />}
                        </button>
                        <button
                          onClick={() => handleDelete(qr.id)}
                          className="p-1 text-red-600 hover:bg-red-50 rounded"
                          title={t('common.delete', 'Delete')}
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* QR Code Modal */}
      {showQRModal && selectedQR && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold mb-4">{selectedQR.name}</h3>
            <div className="flex flex-col items-center">
              {qrImage ? (
                <img src={qrImage} alt="QR Code" className="w-64 h-64" />
              ) : (
                <div className="w-64 h-64 bg-gray-100 flex items-center justify-center">
                  {t('common.loading', 'Loading...')}
                </div>
              )}
              <p className="mt-4 text-sm text-gray-500">{selectedQR.shortUrl}</p>
            </div>
            <div className="flex gap-3 mt-6">
              <button
                onClick={handleDownloadQR}
                disabled={!qrImage}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                <Download className="w-4 h-4" />
                {t('qrCodes.download', 'Download')}
              </button>
              <button
                onClick={() => { setShowQRModal(false); setSelectedQR(null); setQrImage(null); }}
                className="flex-1 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200"
              >
                {t('common.close', 'Close')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Settings Modal */}
      {showSettings && settings && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-lg w-full mx-4 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-semibold mb-4">{t('qrCodes.selfServiceSettings', 'Self-Service Settings')}</h3>
            <div className="space-y-4">
              <label className="flex items-center gap-3">
                <input
                  type="checkbox"
                  checked={settings.enabled || false}
                  onChange={(e) => setSettings({ ...settings, enabled: e.target.checked })}
                  className="w-5 h-5 rounded"
                />
                <span>{t('qrCodes.enableSelfService', 'Enable Self-Service Ordering')}</span>
              </label>

              <label className="flex items-center gap-3">
                <input
                  type="checkbox"
                  checked={settings.allowDineIn || false}
                  onChange={(e) => setSettings({ ...settings, allowDineIn: e.target.checked })}
                  className="w-5 h-5 rounded"
                />
                <span>{t('qrCodes.allowDineIn', 'Allow Dine-In Orders')}</span>
              </label>

              <label className="flex items-center gap-3">
                <input
                  type="checkbox"
                  checked={settings.allowTakeaway || false}
                  onChange={(e) => setSettings({ ...settings, allowTakeaway: e.target.checked })}
                  className="w-5 h-5 rounded"
                />
                <span>{t('qrCodes.allowTakeaway', 'Allow Takeaway Orders')}</span>
              </label>

              <label className="flex items-center gap-3">
                <input
                  type="checkbox"
                  checked={settings.autoAcceptOrders || false}
                  onChange={(e) => setSettings({ ...settings, autoAcceptOrders: e.target.checked })}
                  className="w-5 h-5 rounded"
                />
                <span>{t('qrCodes.autoAccept', 'Auto-Accept Orders')}</span>
              </label>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('qrCodes.minOrder', 'Minimum Order Amount')}
                </label>
                <input
                  type="number"
                  value={settings.minimumOrderAmount || 0}
                  onChange={(e) => setSettings({ ...settings, minimumOrderAmount: Number(e.target.value) })}
                  className="w-full border rounded-lg px-3 py-2"
                  min="0"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('qrCodes.prepTime', 'Estimated Prep Time (minutes)')}
                </label>
                <input
                  type="number"
                  value={settings.estimatedPrepTimeMinutes || 15}
                  onChange={(e) => setSettings({ ...settings, estimatedPrepTimeMinutes: Number(e.target.value) })}
                  className="w-full border rounded-lg px-3 py-2"
                  min="1"
                />
              </div>
            </div>

            <div className="flex gap-3 mt-6">
              <button
                onClick={handleSaveSettings}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                {t('common.save', 'Save')}
              </button>
              <button
                onClick={() => setShowSettings(false)}
                className="flex-1 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200"
              >
                {t('common.cancel', 'Cancel')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
