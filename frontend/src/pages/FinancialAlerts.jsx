import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { financialAlertAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import {
  Plus,
  Edit,
  Trash2,
  Bell,
  Send,
  Power,
  TrendingUp,
  TrendingDown,
  DollarSign,
  Clock,
} from 'lucide-react';

const FinancialAlerts = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();

  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [subscriptions, setSubscriptions] = useState([]);
  const [dailyMetrics, setDailyMetrics] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingSubscription, setEditingSubscription] = useState(null);

  const [formData, setFormData] = useState({
    telegramChatId: '',
    subscriberName: '',
    alertDailyRevenue: true,
    alertDailyExpenses: true,
    alertDailyProfit: true,
    reportTime: '23:00',
    active: true,
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
      loadSubscriptions();
      loadDailyMetrics();
    }
  }, [selectedRestaurant]);

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

  const loadSubscriptions = async () => {
    try {
      setLoading(true);
      const response = await financialAlertAPI.getSubscriptions(selectedRestaurant);
      setSubscriptions(response.data.data || response.data || []);
    } catch (error) {
      console.error('Failed to load subscriptions:', error);
      setSubscriptions([]);
    } finally {
      setLoading(false);
    }
  };

  const loadDailyMetrics = async () => {
    try {
      const response = await financialAlertAPI.getMetrics(selectedRestaurant);
      setDailyMetrics(response.data.data || response.data || null);
    } catch (error) {
      console.error('Failed to load daily metrics:', error);
      setDailyMetrics(null);
    }
  };

  const handleAddSubscription = () => {
    setEditingSubscription(null);
    setFormData({
      telegramChatId: '',
      subscriberName: '',
      alertDailyRevenue: true,
      alertDailyExpenses: true,
      alertDailyProfit: true,
      reportTime: '23:00',
      active: true,
    });
    setShowModal(true);
  };

  const handleEditSubscription = (subscription) => {
    setEditingSubscription(subscription);
    setFormData({
      telegramChatId: subscription.telegramChatId?.toString() || '',
      subscriberName: subscription.subscriberName || '',
      alertDailyRevenue: subscription.alertDailyRevenue ?? true,
      alertDailyExpenses: subscription.alertDailyExpenses ?? true,
      alertDailyProfit: subscription.alertDailyProfit ?? true,
      reportTime: subscription.reportTime || '23:00',
      active: subscription.active ?? true,
    });
    setShowModal(true);
  };

  const handleSaveSubscription = async () => {
    try {
      if (!formData.telegramChatId) {
        alert(t('finance.financialAlerts.errors.chatIdRequired', 'Telegram Chat ID is required'));
        return;
      }

      setLoading(true);
      const data = {
        restaurantId: selectedRestaurant,
        telegramChatId: parseInt(formData.telegramChatId),
        subscriberName: formData.subscriberName,
        alertDailyRevenue: formData.alertDailyRevenue,
        alertDailyExpenses: formData.alertDailyExpenses,
        alertDailyProfit: formData.alertDailyProfit,
        reportTime: formData.reportTime,
        active: formData.active,
      };

      if (editingSubscription) {
        await financialAlertAPI.updateSubscription(editingSubscription.id, data);
      } else {
        await financialAlertAPI.createSubscription(data);
      }

      setShowModal(false);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to save subscription:', error);
      alert(t('finance.financialAlerts.errors.saveFailed', 'Failed to save subscription'));
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSubscription = async (id) => {
    try {
      await financialAlertAPI.toggleSubscription(id);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to toggle subscription:', error);
    }
  };

  const handleDeleteSubscription = async (id) => {
    if (!window.confirm(t('finance.financialAlerts.confirmDelete', 'Are you sure you want to delete this subscription?'))) {
      return;
    }

    try {
      await financialAlertAPI.deleteSubscription(id);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to delete subscription:', error);
    }
  };

  const handleTriggerReport = async () => {
    try {
      await financialAlertAPI.trigger(selectedRestaurant);
      alert(t('finance.financialAlerts.reportTriggered', 'Daily financial report sent successfully!'));
    } catch (error) {
      console.error('Failed to trigger report:', error);
      alert(t('finance.financialAlerts.errors.triggerFailed', 'Failed to trigger report'));
    }
  };

  const formatCurrency = (value) => {
    if (value == null) return '-';
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  };

  const formatTime = (time) => {
    if (!time) return '-';
    return time;
  };

  const formatDateTime = (dateTime) => {
    if (!dateTime) return t('finance.financialAlerts.never', 'Never');
    return new Date(dateTime).toLocaleString();
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">{t('finance.financialAlerts.title', 'Financial Alerts')}</h1>
          <p className="text-gray-600 mt-1">
            {t('finance.financialAlerts.description', 'Manage daily financial report notifications via Telegram')}
          </p>
        </div>
        <button
          onClick={handleAddSubscription}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          {t('finance.financialAlerts.addSubscription', 'Add Subscription')}
        </button>
      </div>

      {/* Restaurant Selector */}
      <div className="mb-6">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => setSelectedRestaurant(e.target.value ? parseInt(e.target.value) : null)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">{t('finance.common.selectRestaurant', 'Select Restaurant')}</option>
          {restaurants.map((restaurant) => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
      </div>

      {selectedRestaurant && (
        <>
          {/* Daily Metrics Cards */}
          {dailyMetrics && (
            <div className="grid grid-cols-4 gap-4 mb-6">
              <div className="bg-white p-4 rounded-lg shadow border-l-4 border-blue-500">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm text-gray-600">{t('finance.financialAlerts.todayRevenue', "Today's Revenue")}</div>
                    <div className="text-2xl font-bold text-gray-900">{formatCurrency(dailyMetrics.revenue)}</div>
                  </div>
                  <DollarSign className="h-8 w-8 text-blue-500" />
                </div>
                <div className="text-xs text-gray-500 mt-1">
                  {dailyMetrics.orderCount} {t('finance.financialAlerts.orders', 'orders')}
                </div>
              </div>

              <div className="bg-white p-4 rounded-lg shadow border-l-4 border-red-500">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm text-gray-600">{t('finance.financialAlerts.todayExpenses', "Today's Expenses")}</div>
                    <div className="text-2xl font-bold text-gray-900">{formatCurrency(dailyMetrics.expenses)}</div>
                  </div>
                  <TrendingDown className="h-8 w-8 text-red-500" />
                </div>
              </div>

              <div className="bg-white p-4 rounded-lg shadow border-l-4 border-green-500">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm text-gray-600">{t('finance.financialAlerts.todayProfit', "Today's Profit")}</div>
                    <div className={`text-2xl font-bold ${dailyMetrics.profit >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {formatCurrency(dailyMetrics.profit)}
                    </div>
                  </div>
                  <TrendingUp className="h-8 w-8 text-green-500" />
                </div>
              </div>

              <div className="bg-white p-4 rounded-lg shadow border-l-4 border-purple-500">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm text-gray-600">{t('finance.financialAlerts.activeSubscriptions', 'Active Subscriptions')}</div>
                    <div className="text-2xl font-bold text-gray-900">
                      {subscriptions.filter((s) => s.active).length}
                    </div>
                  </div>
                  <Bell className="h-8 w-8 text-purple-500" />
                </div>
                <button
                  onClick={handleTriggerReport}
                  className="mt-2 text-sm text-purple-600 hover:text-purple-800 flex items-center gap-1"
                >
                  <Send size={14} />
                  {t('finance.financialAlerts.triggerNow', 'Send Report Now')}
                </button>
              </div>
            </div>
          )}

          {/* Subscriptions Table */}
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-200">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Bell size={20} />
                {t('finance.financialAlerts.subscriptions', 'Telegram Subscriptions')}
              </h2>
              <p className="text-sm text-gray-600">
                {t('finance.financialAlerts.subscriptionsDesc', 'Configure Telegram notifications for daily financial reports')}
              </p>
            </div>

            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                    {t('finance.financialAlerts.subscriber', 'Subscriber')}
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                    {t('finance.financialAlerts.chatId', 'Telegram Chat ID')}
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                    {t('finance.financialAlerts.alerts', 'Alerts')}
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                    {t('finance.financialAlerts.reportTime', 'Report Time')}
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                    {t('finance.financialAlerts.status', 'Status')}
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                    {t('finance.financialAlerts.lastReport', 'Last Report')}
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                    {t('common.actions', 'Actions')}
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {loading ? (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-gray-500">
                      {t('common.loading', 'Loading...')}
                    </td>
                  </tr>
                ) : subscriptions.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-gray-500">
                      {t('finance.financialAlerts.noSubscriptions', 'No subscriptions found. Add a Telegram chat to receive daily financial reports.')}
                    </td>
                  </tr>
                ) : (
                  subscriptions.map((subscription) => (
                    <tr key={subscription.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                        {subscription.subscriberName || '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {subscription.telegramChatId}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        <div className="flex gap-1">
                          {subscription.alertDailyRevenue && (
                            <span className="px-2 py-0.5 bg-blue-100 text-blue-800 rounded text-xs">
                              {t('finance.financialAlerts.revenue', 'Revenue')}
                            </span>
                          )}
                          {subscription.alertDailyExpenses && (
                            <span className="px-2 py-0.5 bg-red-100 text-red-800 rounded text-xs">
                              {t('finance.financialAlerts.expenses', 'Expenses')}
                            </span>
                          )}
                          {subscription.alertDailyProfit && (
                            <span className="px-2 py-0.5 bg-green-100 text-green-800 rounded text-xs">
                              {t('finance.financialAlerts.profit', 'Profit')}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        <div className="flex items-center gap-1">
                          <Clock size={14} />
                          {formatTime(subscription.reportTime)}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span
                          className={`px-2 py-1 rounded-full text-xs font-medium ${
                            subscription.active
                              ? 'bg-green-100 text-green-800'
                              : 'bg-red-100 text-red-800'
                          }`}
                        >
                          {subscription.active
                            ? t('common.active', 'Active')
                            : t('common.inactive', 'Inactive')}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {formatDateTime(subscription.lastReportSentAt)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                        <div className="flex justify-end gap-2">
                          <button
                            onClick={() => handleToggleSubscription(subscription.id)}
                            className={`p-1 rounded ${
                              subscription.active
                                ? 'text-green-600 hover:text-green-800'
                                : 'text-gray-400 hover:text-gray-600'
                            }`}
                            title={subscription.active ? t('common.deactivate', 'Deactivate') : t('common.activate', 'Activate')}
                          >
                            <Power size={18} />
                          </button>
                          <button
                            onClick={() => handleEditSubscription(subscription)}
                            className="p-1 text-blue-600 hover:text-blue-800"
                            title={t('common.edit', 'Edit')}
                          >
                            <Edit size={18} />
                          </button>
                          <button
                            onClick={() => handleDeleteSubscription(subscription.id)}
                            className="p-1 text-red-600 hover:text-red-800"
                            title={t('common.delete', 'Delete')}
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

          {/* Setup Instructions */}
          <div className="mt-6 bg-white rounded-lg shadow p-6">
            <h3 className="text-lg font-semibold mb-4">
              {t('finance.financialAlerts.howToSetup', 'How to Get Telegram Chat ID')}
            </h3>
            <ol className="list-decimal list-inside space-y-2 text-sm text-gray-600">
              <li>{t('finance.financialAlerts.step1', 'Start a chat with your Telegram bot')}</li>
              <li>{t('finance.financialAlerts.step2', 'Send the /start command to the bot')}</li>
              <li>{t('finance.financialAlerts.step3', 'The bot will reply with your Chat ID')}</li>
              <li>{t('finance.financialAlerts.step4', 'Copy the Chat ID and add it as a subscription here')}</li>
            </ol>
          </div>
        </>
      )}

      {/* Add/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-bold mb-4">
              {editingSubscription
                ? t('finance.financialAlerts.editSubscription', 'Edit Subscription')
                : t('finance.financialAlerts.addSubscription', 'Add Subscription')}
            </h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('finance.financialAlerts.subscriberName', 'Subscriber Name')}
                </label>
                <input
                  type="text"
                  value={formData.subscriberName}
                  onChange={(e) => setFormData({ ...formData, subscriberName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                  placeholder={t('finance.financialAlerts.namePlaceholder', 'e.g., Manager John')}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('finance.financialAlerts.chatId', 'Telegram Chat ID')} *
                </label>
                <input
                  type="number"
                  value={formData.telegramChatId}
                  onChange={(e) => setFormData({ ...formData, telegramChatId: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                  placeholder={t('finance.financialAlerts.chatIdPlaceholder', 'Enter Chat ID from bot')}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('finance.financialAlerts.reportTime', 'Daily Report Time')}
                </label>
                <input
                  type="time"
                  value={formData.reportTime}
                  onChange={(e) => setFormData({ ...formData, reportTime: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">
                  {t('finance.financialAlerts.alertTypes', 'Alert Types')}
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={formData.alertDailyRevenue}
                    onChange={(e) => setFormData({ ...formData, alertDailyRevenue: e.target.checked })}
                    className="h-4 w-4 text-blue-600 rounded"
                  />
                  <span className="text-sm">{t('finance.financialAlerts.alertRevenue', 'Daily Revenue')}</span>
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={formData.alertDailyExpenses}
                    onChange={(e) => setFormData({ ...formData, alertDailyExpenses: e.target.checked })}
                    className="h-4 w-4 text-blue-600 rounded"
                  />
                  <span className="text-sm">{t('finance.financialAlerts.alertExpenses', 'Daily Expenses')}</span>
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={formData.alertDailyProfit}
                    onChange={(e) => setFormData({ ...formData, alertDailyProfit: e.target.checked })}
                    className="h-4 w-4 text-blue-600 rounded"
                  />
                  <span className="text-sm">{t('finance.financialAlerts.alertProfit', 'Daily Profit')}</span>
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-2 mt-6">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('common.cancel', 'Cancel')}
              </button>
              <button
                onClick={handleSaveSubscription}
                disabled={loading || !formData.telegramChatId}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? t('common.saving', 'Saving...') : t('common.save', 'Save')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default FinancialAlerts;
