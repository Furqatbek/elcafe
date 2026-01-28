import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Trophy,
  Target,
  TrendingUp,
  DollarSign,
  Star,
  ThumbsUp,
  ThumbsDown,
  Settings,
  Calendar,
  X,
  Save,
  Award,
  BarChart3,
  Percent,
  Clock,
  CheckCircle,
  Banknote,
} from 'lucide-react';
import { waiterAPI, waiterPerformanceAPI, restaurantAPI, waiterCommissionAPI } from '../services/api';

export default function WaiterPerformance() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [waiters, setWaiters] = useState([]);
  const [leaderboard, setLeaderboard] = useState([]);
  const [selectedWaiter, setSelectedWaiter] = useState(null);
  const [waiterPerformance, setWaiterPerformance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('leaderboard');

  // Commission state
  const [commissionSummary, setCommissionSummary] = useState(null);
  const [commissionHistory, setCommissionHistory] = useState([]);
  const [commissionLoading, setCommissionLoading] = useState(false);

  // Date range
  const [dateRange, setDateRange] = useState({
    startDate: new Date(new Date().setDate(new Date().getDate() - 7)).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0],
  });

  // KPI Config Modal
  const [showKPIModal, setShowKPIModal] = useState(false);
  const [kpiConfigs, setKpiConfigs] = useState([]);
  const [editingKPI, setEditingKPI] = useState(null);
  const [kpiForm, setKpiForm] = useState({
    name: '',
    waiterId: null,
    targetOrdersPerDay: 20,
    targetRevenuePerDay: 500,
    targetAvgTicket: 25,
    targetTablesPerShift: 10,
    targetAvgServiceTimeMinutes: 45,
    maxComplaintRatePercent: 2,
    minCustomerRating: 4,
    targetUpsellRatePercent: 15,
    targetDessertAttachRatePercent: 20,
    targetBeverageAttachRatePercent: 60,
    bonusThresholdPercent: 100,
    bonusAmountPerThreshold: 50,
    active: true,
  });

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadWaiters();
      loadLeaderboard();
      loadKPIConfigs();
    }
  }, [selectedRestaurant, dateRange]);

  useEffect(() => {
    if (selectedWaiter && selectedRestaurant) {
      loadWaiterPerformance();
    }
  }, [selectedWaiter, dateRange]);

  useEffect(() => {
    if (selectedWaiter && activeTab === 'commission') {
      loadCommissionData();
    }
  }, [selectedWaiter, dateRange, activeTab]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll();
      const data = response.data?.data || response.data;
      const list = Array.isArray(data) ? data : (data?.content || []);
      setRestaurants(list);
      if (list.length > 0) {
        setSelectedRestaurant(list[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadWaiters = async () => {
    try {
      const response = await waiterAPI.getAll({ size: 100 });
      const data = response.data?.data || response.data;
      setWaiters(data?.content || []);
    } catch (error) {
      console.error('Failed to load waiters:', error);
    }
  };

  const loadLeaderboard = async () => {
    setLoading(true);
    try {
      const response = await waiterPerformanceAPI.getLeaderboard(
        selectedRestaurant,
        dateRange.startDate,
        dateRange.endDate
      );
      const data = response.data?.data || response.data || [];
      // Add rank to each entry
      const ranked = data.map((entry, index) => ({ ...entry, rank: index + 1 }));
      setLeaderboard(ranked);
    } catch (error) {
      console.error('Failed to load leaderboard:', error);
      setLeaderboard([]);
    } finally {
      setLoading(false);
    }
  };

  const loadWaiterPerformance = async () => {
    try {
      const response = await waiterPerformanceAPI.getPerformanceSummary(
        selectedWaiter,
        dateRange.startDate,
        dateRange.endDate
      );
      setWaiterPerformance(response.data?.data || response.data);
    } catch (error) {
      console.error('Failed to load waiter performance:', error);
      setWaiterPerformance(null);
    }
  };

  const loadKPIConfigs = async () => {
    try {
      const response = await waiterPerformanceAPI.getKPIConfigs(selectedRestaurant);
      setKpiConfigs(response.data?.data || response.data || []);
    } catch (error) {
      console.error('Failed to load KPI configs:', error);
      setKpiConfigs([]);
    }
  };

  const loadCommissionData = async () => {
    if (!selectedWaiter) return;
    setCommissionLoading(true);
    try {
      const [summaryRes, historyRes] = await Promise.all([
        waiterCommissionAPI.getWaiterSummary(selectedWaiter, dateRange.startDate, dateRange.endDate),
        waiterCommissionAPI.getWaiterHistory(selectedWaiter, { page: 0, size: 20 }),
      ]);
      setCommissionSummary(summaryRes.data?.data || summaryRes.data);
      const historyData = historyRes.data?.data || historyRes.data;
      setCommissionHistory(historyData?.content || []);
    } catch (error) {
      console.error('Failed to load commission data:', error);
      setCommissionSummary(null);
      setCommissionHistory([]);
    } finally {
      setCommissionLoading(false);
    }
  };

  const handleSaveKPI = async () => {
    try {
      await waiterPerformanceAPI.saveKPIConfig(selectedRestaurant, {
        ...kpiForm,
        id: editingKPI?.id,
      });
      setShowKPIModal(false);
      setEditingKPI(null);
      resetKPIForm();
      loadKPIConfigs();
    } catch (error) {
      console.error('Failed to save KPI config:', error);
      alert('Failed to save KPI configuration');
    }
  };

  const handleDeleteKPI = async (configId) => {
    if (!confirm('Are you sure you want to delete this KPI configuration?')) return;
    try {
      await waiterPerformanceAPI.deleteKPIConfig(configId);
      loadKPIConfigs();
    } catch (error) {
      console.error('Failed to delete KPI config:', error);
    }
  };

  const resetKPIForm = () => {
    setKpiForm({
      name: '',
      waiterId: null,
      targetOrdersPerDay: 20,
      targetRevenuePerDay: 500,
      targetAvgTicket: 25,
      targetTablesPerShift: 10,
      targetAvgServiceTimeMinutes: 45,
      maxComplaintRatePercent: 2,
      minCustomerRating: 4,
      targetUpsellRatePercent: 15,
      targetDessertAttachRatePercent: 20,
      targetBeverageAttachRatePercent: 60,
      bonusThresholdPercent: 100,
      bonusAmountPerThreshold: 50,
      active: true,
    });
  };

  const openEditKPI = (config) => {
    setEditingKPI(config);
    setKpiForm({
      name: config.name || '',
      waiterId: config.waiter?.id || null,
      targetOrdersPerDay: config.targetOrdersPerDay || 20,
      targetRevenuePerDay: config.targetRevenuePerDay || 500,
      targetAvgTicket: config.targetAvgTicket || 25,
      targetTablesPerShift: config.targetTablesPerShift || 10,
      targetAvgServiceTimeMinutes: config.targetAvgServiceTimeMinutes || 45,
      maxComplaintRatePercent: config.maxComplaintRatePercent || 2,
      minCustomerRating: config.minCustomerRating || 4,
      targetUpsellRatePercent: config.targetUpsellRatePercent || 15,
      targetDessertAttachRatePercent: config.targetDessertAttachRatePercent || 20,
      targetBeverageAttachRatePercent: config.targetBeverageAttachRatePercent || 60,
      bonusThresholdPercent: config.bonusThresholdPercent || 100,
      bonusAmountPerThreshold: config.bonusAmountPerThreshold || 50,
      active: config.active !== false,
    });
    setShowKPIModal(true);
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount || 0);
  };

  const formatPercent = (value) => {
    return `${(value || 0).toFixed(1)}%`;
  };

  const getKPIColor = (score) => {
    if (score >= 100) return 'text-green-600 bg-green-100';
    if (score >= 80) return 'text-yellow-600 bg-yellow-100';
    return 'text-red-600 bg-red-100';
  };

  const getRankBadge = (rank) => {
    if (rank === 1) return 'bg-yellow-400 text-yellow-900';
    if (rank === 2) return 'bg-gray-300 text-gray-800';
    if (rank === 3) return 'bg-amber-600 text-white';
    return 'bg-gray-100 text-gray-600';
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {t('waiterPerformance.title', 'Waiter Performance')}
          </h1>
          <p className="text-gray-500">
            {t('waiterPerformance.subtitle', 'Track KPIs and performance metrics')}
          </p>
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
            onClick={() => { resetKPIForm(); setEditingKPI(null); setShowKPIModal(true); }}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            <Settings className="w-4 h-4" />
            {t('waiterPerformance.configureKPI', 'Configure KPI')}
          </button>
        </div>
      </div>

      {/* Date Range */}
      <div className="bg-white p-4 rounded-lg shadow flex items-center gap-4">
        <Calendar className="w-5 h-5 text-gray-400" />
        <div className="flex items-center gap-2">
          <input
            type="date"
            value={dateRange.startDate}
            onChange={(e) => setDateRange({ ...dateRange, startDate: e.target.value })}
            className="border rounded px-3 py-2"
          />
          <span className="text-gray-500">{t('common.to', 'to')}</span>
          <input
            type="date"
            value={dateRange.endDate}
            onChange={(e) => setDateRange({ ...dateRange, endDate: e.target.value })}
            className="border rounded px-3 py-2"
          />
        </div>
        <div className="flex gap-2 ml-4">
          <button
            onClick={() => {
              const today = new Date();
              setDateRange({
                startDate: today.toISOString().split('T')[0],
                endDate: today.toISOString().split('T')[0],
              });
            }}
            className="px-3 py-1 text-sm bg-gray-100 rounded hover:bg-gray-200"
          >
            {t('waiterPerformance.dateRange.today', 'Today')}
          </button>
          <button
            onClick={() => {
              const today = new Date();
              const weekAgo = new Date(today.setDate(today.getDate() - 7));
              setDateRange({
                startDate: weekAgo.toISOString().split('T')[0],
                endDate: new Date().toISOString().split('T')[0],
              });
            }}
            className="px-3 py-1 text-sm bg-gray-100 rounded hover:bg-gray-200"
          >
            {t('waiterPerformance.dateRange.thisWeek', 'Last 7 Days')}
          </button>
          <button
            onClick={() => {
              const today = new Date();
              const monthAgo = new Date(today.setDate(today.getDate() - 30));
              setDateRange({
                startDate: monthAgo.toISOString().split('T')[0],
                endDate: new Date().toISOString().split('T')[0],
              });
            }}
            className="px-3 py-1 text-sm bg-gray-100 rounded hover:bg-gray-200"
          >
            {t('waiterPerformance.dateRange.thisMonth', 'Last 30 Days')}
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b">
        <div className="flex gap-4">
          <button
            onClick={() => { setActiveTab('leaderboard'); setSelectedWaiter(null); }}
            className={`pb-2 px-1 border-b-2 ${activeTab === 'leaderboard' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500'}`}
          >
            <div className="flex items-center gap-2">
              <Trophy className="w-4 h-4" />
              {t('waiterPerformance.tabs.leaderboard', 'Leaderboard')}
            </div>
          </button>
          <button
            onClick={() => setActiveTab('kpi')}
            className={`pb-2 px-1 border-b-2 ${activeTab === 'kpi' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500'}`}
          >
            <div className="flex items-center gap-2">
              <Target className="w-4 h-4" />
              {t('waiterPerformance.tabs.kpiSettings', 'KPI Settings')}
            </div>
          </button>
          {selectedWaiter && (
            <button
              onClick={() => setActiveTab('details')}
              className={`pb-2 px-1 border-b-2 ${activeTab === 'details' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500'}`}
            >
              <div className="flex items-center gap-2">
                <BarChart3 className="w-4 h-4" />
                {t('waiterPerformance.tabs.details', 'Details')}
              </div>
            </button>
          )}
          {selectedWaiter && (
            <button
              onClick={() => setActiveTab('commission')}
              className={`pb-2 px-1 border-b-2 ${activeTab === 'commission' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500'}`}
            >
              <div className="flex items-center gap-2">
                <Percent className="w-4 h-4" />
                {t('waiterPerformance.tabs.commission', 'Commission')}
              </div>
            </button>
          )}
        </div>
      </div>

      {/* Leaderboard Tab */}
      {activeTab === 'leaderboard' && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="p-4 border-b">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <Trophy className="w-5 h-5 text-yellow-500" />
              {t('waiterPerformance.leaderboard.title', 'Performance Leaderboard')}
            </h2>
          </div>
          {loading ? (
            <div className="p-8 text-center text-gray-500">{t('waiterPerformance.loading', 'Loading...')}</div>
          ) : leaderboard.length === 0 ? (
            <div className="p-8 text-center text-gray-500">
              {t('waiterPerformance.leaderboard.noData', 'No performance data for selected period')}
            </div>
          ) : (
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.leaderboard.rank', 'Rank')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.leaderboard.waiter', 'Waiter')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.leaderboard.orders', 'Orders')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.leaderboard.revenue', 'Revenue')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.leaderboard.revenueShare', 'Revenue Share')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.leaderboard.avgRating', 'Avg Rating')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.leaderboard.kpiScore', 'KPI Score')}</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('common.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {leaderboard.map((entry) => (
                  <tr key={entry.waiterId} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center justify-center w-8 h-8 rounded-full font-bold ${getRankBadge(entry.rank)}`}>
                        {entry.rank}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-medium">{entry.waiterName}</td>
                    <td className="px-4 py-3">{entry.totalOrders || 0}</td>
                    <td className="px-4 py-3">{formatCurrency(entry.totalRevenue)}</td>
                    <td className="px-4 py-3">
                      {entry.commissionEnabled ? (
                        <div className="flex flex-col">
                          <span className="font-medium text-green-600">{formatCurrency(entry.totalCommission)}</span>
                          <span className="text-xs text-gray-500">{entry.commissionPercent || 0}%</span>
                        </div>
                      ) : (
                        <span className="text-gray-400 text-sm">{t('waiterPerformance.leaderboard.noCommission', 'N/A')}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {entry.avgRating ? (
                        <div className="flex items-center gap-1">
                          <Star className="w-4 h-4 text-yellow-400 fill-yellow-400" />
                          {entry.avgRating.toFixed(1)}
                        </div>
                      ) : '-'}
                    </td>
                    <td className="px-4 py-3">
                      {entry.avgKpiScore ? (
                        <span className={`px-2 py-1 rounded-full text-sm font-medium ${getKPIColor(entry.avgKpiScore)}`}>
                          {formatPercent(entry.avgKpiScore)}
                        </span>
                      ) : '-'}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => { setSelectedWaiter(entry.waiterId); setActiveTab('details'); }}
                        className="text-blue-600 hover:text-blue-800 text-sm"
                      >
                        {t('common.viewDetails', 'View Details')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* KPI Settings Tab */}
      {activeTab === 'kpi' && (
        <div className="bg-white rounded-lg shadow">
          <div className="p-4 border-b flex justify-between items-center">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <Target className="w-5 h-5 text-blue-500" />
              {t('waiterPerformance.kpiSettings.title', 'KPI Configurations')}
            </h2>
            <button
              onClick={() => { resetKPIForm(); setEditingKPI(null); setShowKPIModal(true); }}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm"
            >
              {t('waiterPerformance.kpiSettings.addConfig', '+ Add KPI Config')}
            </button>
          </div>
          {kpiConfigs.length === 0 ? (
            <div className="p-8 text-center text-gray-500">
              {t('waiterPerformance.kpiSettings.noConfigs', 'No KPI configurations. Create one to set performance targets.')}
            </div>
          ) : (
            <div className="divide-y">
              {kpiConfigs.map((config) => (
                <div key={config.id} className="p-4 hover:bg-gray-50">
                  <div className="flex justify-between items-start">
                    <div>
                      <h3 className="font-medium">
                        {config.name || (config.waiter ? `${config.waiter.name}'s KPI` : t('waiterPerformance.kpiSettings.restaurantDefault', 'Restaurant Default'))}
                      </h3>
                      <p className="text-sm text-gray-500">
                        {config.waiter ? t('waiterPerformance.kpiSettings.forWaiter', 'For: {{name}}', { name: config.waiter.name }) : t('waiterPerformance.kpiSettings.appliesToAll', 'Applies to all waiters')}
                      </p>
                      <div className="mt-2 flex flex-wrap gap-3 text-sm text-gray-600">
                        <span>{t('waiterPerformance.kpiSettings.ordersDay', 'Orders: {{count}}/day', { count: config.targetOrdersPerDay })}</span>
                        <span>{t('waiterPerformance.kpiSettings.revenueDay', 'Revenue: {{amount}}/day', { amount: formatCurrency(config.targetRevenuePerDay) })}</span>
                        <span>{t('waiterPerformance.kpiSettings.avgTicketLabel', 'Avg Ticket: {{amount}}', { amount: formatCurrency(config.targetAvgTicket) })}</span>
                        <span>{t('waiterPerformance.kpiSettings.ratingLabel', 'Rating: {{rating}}+', { rating: config.minCustomerRating })}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-1 text-xs rounded ${config.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'}`}>
                        {config.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                      </span>
                      <button
                        onClick={() => openEditKPI(config)}
                        className="text-blue-600 hover:text-blue-800 text-sm"
                      >
                        {t('common.edit', 'Edit')}
                      </button>
                      <button
                        onClick={() => handleDeleteKPI(config.id)}
                        className="text-red-600 hover:text-red-800 text-sm"
                      >
                        {t('common.delete', 'Delete')}
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Waiter Details Tab */}
      {activeTab === 'details' && selectedWaiter && waiterPerformance && (
        <div className="space-y-6">
          {/* Summary Cards */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div className="bg-white p-4 rounded-lg shadow">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-blue-100 rounded-lg">
                  <TrendingUp className="w-6 h-6 text-blue-600" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">{t('waiterPerformance.details.totalOrders', 'Total Orders')}</p>
                  <p className="text-2xl font-bold">{waiterPerformance.totalOrders || 0}</p>
                </div>
              </div>
            </div>
            <div className="bg-white p-4 rounded-lg shadow">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-green-100 rounded-lg">
                  <DollarSign className="w-6 h-6 text-green-600" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">{t('waiterPerformance.details.totalRevenue', 'Total Revenue')}</p>
                  <p className="text-2xl font-bold">{formatCurrency(waiterPerformance.totalRevenue)}</p>
                </div>
              </div>
            </div>
            <div className="bg-white p-4 rounded-lg shadow">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-yellow-100 rounded-lg">
                  <Award className="w-6 h-6 text-yellow-600" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">{t('waiterPerformance.details.totalTips', 'Tips Earned')}</p>
                  <p className="text-2xl font-bold">{formatCurrency(waiterPerformance.totalTips)}</p>
                </div>
              </div>
            </div>
            <div className="bg-white p-4 rounded-lg shadow">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-purple-100 rounded-lg">
                  <Target className="w-6 h-6 text-purple-600" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">{t('waiterPerformance.details.avgKpiScore', 'Avg KPI Score')}</p>
                  <p className={`text-2xl font-bold ${waiterPerformance.avgKpiScore >= 100 ? 'text-green-600' : waiterPerformance.avgKpiScore >= 80 ? 'text-yellow-600' : 'text-red-600'}`}>
                    {formatPercent(waiterPerformance.avgKpiScore)}
                  </p>
                </div>
              </div>
            </div>
            <div className="bg-white p-4 rounded-lg shadow">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-emerald-100 rounded-lg">
                  <Percent className="w-6 h-6 text-emerald-600" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">{t('waiterPerformance.details.revenueShare', 'Revenue Share')}</p>
                  {waiterPerformance.commissionEnabled ? (
                    <div>
                      <p className="text-2xl font-bold text-emerald-600">{formatCurrency(waiterPerformance.totalCommission)}</p>
                      <p className="text-xs text-gray-500">
                        {waiterPerformance.commissionType === 'FIXED_AMOUNT'
                          ? formatCurrency(waiterPerformance.fixedCommissionAmount || 0) + ' ' + t('waiterPerformance.details.perOrder', '/order')
                          : (waiterPerformance.commissionPercent || 0) + '% ' + t('waiterPerformance.details.ofRevenue', 'of revenue')}
                      </p>
                    </div>
                  ) : (
                    <p className="text-lg text-gray-400">{t('waiterPerformance.details.notEnabled', 'Not enabled')}</p>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* More Details */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-white p-6 rounded-lg shadow">
              <h3 className="text-lg font-semibold mb-4">{t('waiterPerformance.details.performanceMetrics', 'Performance Metrics')}</h3>
              <div className="space-y-3">
                <div className="flex justify-between">
                  <span className="text-gray-600">{t('waiterPerformance.details.workingDays', 'Working Days')}</span>
                  <span className="font-medium">{waiterPerformance.workingDays || 0}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">{t('waiterPerformance.details.avgTicketValue', 'Avg Ticket Value')}</span>
                  <span className="font-medium">{formatCurrency(waiterPerformance.avgTicketValue)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">{t('waiterPerformance.details.totalBonusEarned', 'Total Bonus Earned')}</span>
                  <span className="font-medium text-green-600">{formatCurrency(waiterPerformance.totalBonusEarned)}</span>
                </div>
              </div>
            </div>

            <div className="bg-white p-6 rounded-lg shadow">
              <h3 className="text-lg font-semibold mb-4">{t('waiterPerformance.details.qualityMetrics', 'Quality Metrics')}</h3>
              <div className="space-y-3">
                <div className="flex justify-between items-center">
                  <span className="text-gray-600 flex items-center gap-2">
                    <ThumbsUp className="w-4 h-4 text-green-500" />
                    {t('waiterPerformance.details.compliments', 'Compliments')}
                  </span>
                  <span className="font-medium text-green-600">{waiterPerformance.complimentsCount || 0}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-gray-600 flex items-center gap-2">
                    <ThumbsDown className="w-4 h-4 text-red-500" />
                    {t('waiterPerformance.details.complaints', 'Complaints')}
                  </span>
                  <span className="font-medium text-red-600">{waiterPerformance.complaintsCount || 0}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-gray-600 flex items-center gap-2">
                    <Star className="w-4 h-4 text-yellow-500" />
                    {t('waiterPerformance.details.avgRating', 'Avg Rating')}
                  </span>
                  <span className="font-medium">{waiterPerformance.avgCustomerRating?.toFixed(1) || '-'}</span>
                </div>
              </div>
            </div>
          </div>

          <button
            onClick={() => { setSelectedWaiter(null); setActiveTab('leaderboard'); }}
            className="text-gray-600 hover:text-gray-800"
          >
            &larr; {t('waiterPerformance.backToLeaderboard', 'Back to Leaderboard')}
          </button>
        </div>
      )}

      {/* Commission Tab */}
      {activeTab === 'commission' && selectedWaiter && (
        <div className="space-y-6">
          {commissionLoading ? (
            <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
              {t('waiterPerformance.loading', 'Loading...')}
            </div>
          ) : commissionSummary ? (
            <>
              {/* Commission Summary Cards */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="bg-white p-4 rounded-lg shadow">
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-green-100 rounded-lg">
                      <DollarSign className="w-6 h-6 text-green-600" />
                    </div>
                    <div>
                      <p className="text-sm text-gray-500">{t('waiterPerformance.commission.totalEarned', 'Total Earned')}</p>
                      <p className="text-2xl font-bold text-green-600">{formatCurrency(commissionSummary.totalCommissionEarned)}</p>
                    </div>
                  </div>
                </div>
                <div className="bg-white p-4 rounded-lg shadow">
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-yellow-100 rounded-lg">
                      <Clock className="w-6 h-6 text-yellow-600" />
                    </div>
                    <div>
                      <p className="text-sm text-gray-500">{t('waiterPerformance.commission.pending', 'Pending')}</p>
                      <p className="text-2xl font-bold text-yellow-600">{formatCurrency(commissionSummary.pendingCommission)}</p>
                    </div>
                  </div>
                </div>
                <div className="bg-white p-4 rounded-lg shadow">
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-blue-100 rounded-lg">
                      <CheckCircle className="w-6 h-6 text-blue-600" />
                    </div>
                    <div>
                      <p className="text-sm text-gray-500">{t('waiterPerformance.commission.approved', 'Approved')}</p>
                      <p className="text-2xl font-bold text-blue-600">{formatCurrency(commissionSummary.approvedCommission)}</p>
                    </div>
                  </div>
                </div>
                <div className="bg-white p-4 rounded-lg shadow">
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-purple-100 rounded-lg">
                      <Banknote className="w-6 h-6 text-purple-600" />
                    </div>
                    <div>
                      <p className="text-sm text-gray-500">{t('waiterPerformance.commission.paid', 'Paid')}</p>
                      <p className="text-2xl font-bold text-purple-600">{formatCurrency(commissionSummary.paidCommission)}</p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Commission Details */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Commission Info */}
                <div className="bg-white p-6 rounded-lg shadow">
                  <h3 className="text-lg font-semibold mb-4">{t('waiterPerformance.commission.summary', 'Commission Summary')}</h3>
                  <div className="space-y-3">
                    <div className="flex justify-between">
                      <span className="text-gray-600">{t('waiterPerformance.commission.commissionType', 'Commission Type')}</span>
                      <span className="font-medium">
                        {commissionSummary.commissionType === 'FIXED_AMOUNT'
                          ? t('waiterPerformance.commission.fixedAmount', 'Fixed Amount')
                          : t('waiterPerformance.commission.percentage', 'Percentage')}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-600">{t('waiterPerformance.commission.commissionRate', 'Commission Rate')}</span>
                      <span className="font-medium">
                        {commissionSummary.commissionType === 'FIXED_AMOUNT'
                          ? formatCurrency(commissionSummary.fixedCommissionAmount || 0) + ' ' + t('waiterPerformance.commission.perOrder', '/order')
                          : (commissionSummary.currentCommissionPercent || 0) + '%'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-600">{t('waiterPerformance.commission.status', 'Commission Status')}</span>
                      <span className={`font-medium ${commissionSummary.commissionEnabled ? 'text-green-600' : 'text-gray-500'}`}>
                        {commissionSummary.commissionEnabled ? t('common.enabled', 'Enabled') : t('common.disabled', 'Disabled')}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-600">{t('waiterPerformance.commission.totalOrderValue', 'Total Order Value')}</span>
                      <span className="font-medium">{formatCurrency(commissionSummary.totalOrderValue)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-600">{t('waiterPerformance.commission.totalCommissions', 'Total Commissions')}</span>
                      <span className="font-medium">{commissionSummary.totalCommissions || 0}</span>
                    </div>
                  </div>
                </div>

                {/* Daily Breakdown */}
                <div className="bg-white p-6 rounded-lg shadow">
                  <h3 className="text-lg font-semibold mb-4">{t('waiterPerformance.commission.dailyBreakdown', 'Daily Breakdown')}</h3>
                  {commissionSummary.dailyCommissions && commissionSummary.dailyCommissions.length > 0 ? (
                    <div className="space-y-2 max-h-48 overflow-y-auto">
                      {commissionSummary.dailyCommissions.map((day, idx) => (
                        <div key={idx} className="flex justify-between items-center py-2 border-b last:border-0">
                          <span className="text-gray-600">{new Date(day.date).toLocaleDateString()}</span>
                          <div className="text-right">
                            <span className="font-medium text-green-600">{formatCurrency(day.commissionEarned)}</span>
                            <span className="text-xs text-gray-500 ml-2">({day.orderCount} orders)</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-gray-500 text-center py-4">{t('waiterPerformance.commission.noDailyData', 'No daily data available')}</p>
                  )}
                </div>
              </div>

              {/* Commission History Table */}
              <div className="bg-white rounded-lg shadow overflow-hidden">
                <div className="p-4 border-b">
                  <h3 className="text-lg font-semibold">{t('waiterPerformance.commission.history', 'Commission History')}</h3>
                </div>
                {commissionHistory.length === 0 ? (
                  <div className="p-8 text-center text-gray-500">
                    {t('waiterPerformance.commission.noHistory', 'No commission history found')}
                  </div>
                ) : (
                  <table className="w-full">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.commission.orderNumber', 'Order #')}</th>
                        <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.commission.date', 'Date')}</th>
                        <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.commission.orderTotal', 'Order Total')}</th>
                        <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.commission.rate', 'Rate')}</th>
                        <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.commission.amount', 'Amount')}</th>
                        <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">{t('waiterPerformance.commission.status', 'Status')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {commissionHistory.map((commission) => (
                        <tr key={commission.id} className="hover:bg-gray-50">
                          <td className="px-4 py-3 font-medium">{commission.orderNumber}</td>
                          <td className="px-4 py-3 text-gray-600">
                            {commission.createdAt ? new Date(commission.createdAt).toLocaleDateString() : '-'}
                          </td>
                          <td className="px-4 py-3">{formatCurrency(commission.orderTotal)}</td>
                          <td className="px-4 py-3">{commission.commissionPercent}%</td>
                          <td className="px-4 py-3 font-medium text-green-600">{formatCurrency(commission.commissionAmount)}</td>
                          <td className="px-4 py-3">
                            <span className={`px-2 py-1 text-xs rounded-full ${
                              commission.status === 'PAID' ? 'bg-green-100 text-green-800' :
                              commission.status === 'APPROVED' || commission.status === 'PROCESSED' ? 'bg-blue-100 text-blue-800' :
                              commission.status === 'PENDING' ? 'bg-yellow-100 text-yellow-800' :
                              'bg-gray-100 text-gray-800'
                            }`}>
                              {commission.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </>
          ) : (
            <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
              {t('waiterPerformance.commission.noData', 'No commission data available')}
            </div>
          )}

          <button
            onClick={() => { setSelectedWaiter(null); setActiveTab('leaderboard'); }}
            className="text-gray-600 hover:text-gray-800"
          >
            &larr; {t('waiterPerformance.backToLeaderboard', 'Back to Leaderboard')}
          </button>
        </div>
      )}

      {/* KPI Configuration Modal */}
      {showKPIModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold">
                {editingKPI ? t('waiterPerformance.kpiSettings.editConfig', 'Edit KPI Configuration') : t('waiterPerformance.kpiSettings.addConfig', 'Create KPI Configuration')}
              </h3>
              <button onClick={() => setShowKPIModal(false)} className="text-gray-500 hover:text-gray-700">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-6">
              {/* Basic Info */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('waiterPerformance.kpiSettings.configName', 'Configuration Name')}</label>
                  <input
                    type="text"
                    value={kpiForm.name}
                    onChange={(e) => setKpiForm({ ...kpiForm, name: e.target.value })}
                    placeholder={t('waiterPerformance.kpiSettings.configNamePlaceholder', 'e.g., Default KPI, Peak Hours')}
                    className="w-full border rounded-lg px-3 py-2"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('waiterPerformance.kpiSettings.applyTo', 'Apply To')}</label>
                  <select
                    value={kpiForm.waiterId || ''}
                    onChange={(e) => setKpiForm({ ...kpiForm, waiterId: e.target.value ? Number(e.target.value) : null })}
                    className="w-full border rounded-lg px-3 py-2"
                  >
                    <option value="">{t('waiterPerformance.kpiSettings.allWaiters', 'All Waiters (Restaurant Default)')}</option>
                    {waiters.map((w) => (
                      <option key={w.id} value={w.id}>{w.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Daily Targets */}
              <div>
                <h4 className="font-medium mb-3">{t('waiterPerformance.kpiSettings.dailyTargets', 'Daily Targets')}</h4>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.ordersPerDay', 'Orders/Day')}</label>
                    <input
                      type="number"
                      value={kpiForm.targetOrdersPerDay}
                      onChange={(e) => setKpiForm({ ...kpiForm, targetOrdersPerDay: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.revenuePerDay', 'Revenue/Day')}</label>
                    <input
                      type="number"
                      value={kpiForm.targetRevenuePerDay}
                      onChange={(e) => setKpiForm({ ...kpiForm, targetRevenuePerDay: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.avgTicket', 'Avg Ticket')}</label>
                    <input
                      type="number"
                      value={kpiForm.targetAvgTicket}
                      onChange={(e) => setKpiForm({ ...kpiForm, targetAvgTicket: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.tablesPerShift', 'Tables/Shift')}</label>
                    <input
                      type="number"
                      value={kpiForm.targetTablesPerShift}
                      onChange={(e) => setKpiForm({ ...kpiForm, targetTablesPerShift: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                  </div>
                </div>
              </div>

              {/* Quality Targets */}
              <div>
                <h4 className="font-medium mb-3">{t('waiterPerformance.kpiSettings.qualityTargets', 'Quality Targets')}</h4>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.avgServiceTimeMin', 'Avg Service Time (min)')}</label>
                    <input
                      type="number"
                      value={kpiForm.targetAvgServiceTimeMinutes}
                      onChange={(e) => setKpiForm({ ...kpiForm, targetAvgServiceTimeMinutes: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.maxComplaintRate', 'Max Complaint Rate (%)')}</label>
                    <input
                      type="number"
                      step="0.1"
                      value={kpiForm.maxComplaintRatePercent}
                      onChange={(e) => setKpiForm({ ...kpiForm, maxComplaintRatePercent: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.minCustomerRating', 'Min Customer Rating')}</label>
                    <input
                      type="number"
                      step="0.1"
                      min="1"
                      max="5"
                      value={kpiForm.minCustomerRating}
                      onChange={(e) => setKpiForm({ ...kpiForm, minCustomerRating: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                  </div>
                </div>
              </div>

              {/* Bonus Configuration */}
              <div>
                <h4 className="font-medium mb-3">{t('waiterPerformance.kpiSettings.bonusConfig', 'Bonus Configuration')}</h4>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.bonusThreshold', 'Bonus Threshold (%)')}</label>
                    <input
                      type="number"
                      value={kpiForm.bonusThresholdPercent}
                      onChange={(e) => setKpiForm({ ...kpiForm, bonusThresholdPercent: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                    <p className="text-xs text-gray-500 mt-1">{t('waiterPerformance.kpiSettings.bonusThresholdHint', 'KPI score needed to earn bonus')}</p>
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('waiterPerformance.kpiSettings.bonusAmount', 'Bonus Amount')}</label>
                    <input
                      type="number"
                      value={kpiForm.bonusAmountPerThreshold}
                      onChange={(e) => setKpiForm({ ...kpiForm, bonusAmountPerThreshold: Number(e.target.value) })}
                      className="w-full border rounded px-3 py-2"
                    />
                    <p className="text-xs text-gray-500 mt-1">{t('waiterPerformance.kpiSettings.bonusAmountHint', 'Bonus per threshold achieved')}</p>
                  </div>
                </div>
              </div>

              {/* Active Toggle */}
              <label className="flex items-center gap-3">
                <input
                  type="checkbox"
                  checked={kpiForm.active}
                  onChange={(e) => setKpiForm({ ...kpiForm, active: e.target.checked })}
                  className="w-5 h-5 rounded"
                />
                <span>{t('common.active', 'Active')}</span>
              </label>
            </div>

            <div className="flex gap-3 mt-6">
              <button
                onClick={handleSaveKPI}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                <Save className="w-4 h-4" />
                {t('waiterPerformance.kpiSettings.saveConfig', 'Save Configuration')}
              </button>
              <button
                onClick={() => setShowKPIModal(false)}
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
