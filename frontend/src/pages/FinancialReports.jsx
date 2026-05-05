import { useState, useEffect } from 'react';
import { financialAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { TrendingUp, TrendingDown, PieChart, BarChart3, Calendar, AlertCircle, RefreshCw, Settings, Building2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const FinancialReports = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('profitLoss');
  const [accountsExist, setAccountsExist] = useState(true);
  const [initializingAccounts, setInitializingAccounts] = useState(false);
  const [periodType, setPeriodType] = useState('monthly'); // 'daily' or 'monthly'

  // Helper to get date range based on period type
  const getDateRange = (type) => {
    const today = new Date();
    if (type === 'daily') {
      const dateStr = today.toISOString().split('T')[0];
      return { startDate: dateStr, endDate: dateStr };
    } else {
      // Monthly - first day of current month to today
      const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
      return {
        startDate: firstDay.toISOString().split('T')[0],
        endDate: today.toISOString().split('T')[0]
      };
    }
  };

  const [dateRange, setDateRange] = useState(getDateRange('monthly'));

  const [profitLossReport, setProfitLossReport] = useState(null);
  const [balanceSheet, setBalanceSheet] = useState(null);
  const [cashFlowReport, setCashFlowReport] = useState(null);
  const [cogsReport, setCogsReport] = useState(null);

  // Fetch restaurants on mount
  useEffect(() => {
    const fetchRestaurants = async () => {
      try {
        const response = await restaurantAPI.getAll();
        const restaurantList = response.data.data?.content || response.data.data || [];
        setRestaurants(restaurantList);

        // Set initial restaurant: user's restaurant or first available
        if (!selectedRestaurant) {
          if (user?.restaurantId) {
            setSelectedRestaurant(user.restaurantId);
          } else if (restaurantList.length > 0) {
            setSelectedRestaurant(restaurantList[0].id);
          }
        }
      } catch (error) {
        console.error('Error fetching restaurants:', error);
      }
    };
    fetchRestaurants();
  }, [user]);

  // Load reports when restaurant or date range changes
  useEffect(() => {
    if (selectedRestaurant) {
      checkAccountsAndLoadReports(selectedRestaurant);
    }
  }, [selectedRestaurant, dateRange]);

  const checkAccountsAndLoadReports = async (restaurantId) => {
    setLoading(true);
    try {
      // First check if accounts exist
      const accountsResponse = await financialAPI.getAccounts(restaurantId);
      const accounts = accountsResponse.data?.data || [];

      if (accounts.length === 0) {
        setAccountsExist(false);
        setLoading(false);
        return;
      }

      setAccountsExist(true);
      await loadReports(restaurantId);
    } catch (error) {
      console.error('Failed to check accounts:', error);
      setAccountsExist(false);
    } finally {
      setLoading(false);
    }
  };

  const loadReports = async (restaurantId) => {
    try {
      const [plResponse, bsResponse, cfResponse, cogsResponse] = await Promise.all([
        financialAPI.getProfitLossReport(restaurantId, dateRange.startDate, dateRange.endDate),
        financialAPI.getBalanceSheet(restaurantId, dateRange.endDate),
        financialAPI.getCashFlowReport(restaurantId, dateRange.startDate, dateRange.endDate),
        financialAPI.getCogsReport(restaurantId, dateRange.startDate, dateRange.endDate)
      ]);

      setProfitLossReport(plResponse.data.data);
      setBalanceSheet(bsResponse.data.data);
      setCashFlowReport(cfResponse.data.data);
      setCogsReport(cogsResponse.data.data);
    } catch (error) {
      console.error('Failed to load reports:', error);
    }
  };

  const initializeChartOfAccounts = async () => {
    if (!selectedRestaurant) return;

    setInitializingAccounts(true);
    try {
      await financialAPI.initializeAccounts(selectedRestaurant);
      setAccountsExist(true);
      await loadReports(selectedRestaurant);
    } catch (error) {
      console.error('Failed to initialize accounts:', error);
      alert(t('finance.reports.initializationFailed', 'Failed to initialize Chart of Accounts'));
    } finally {
      setInitializingAccounts(false);
    }
  };

  const handlePeriodChange = (type) => {
    setPeriodType(type);
    setDateRange(getDateRange(type));
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount || 0);
  };

  const formatPercentage = (value) => {
    return `${(value || 0).toFixed(2)}%`;
  };

  const renderProfitLoss = () => {
    if (!profitLossReport) return <div className="text-center py-8">{t('finance.common.noDataAvailable')}</div>;

    const netMargin = profitLossReport.totalRevenue > 0
      ? (profitLossReport.netIncome / profitLossReport.totalRevenue * 100).toFixed(2)
      : 0;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-green-800">{t('finance.reports.totalRevenue')}</span>
              <TrendingUp className="text-green-600" size={20} />
            </div>
            <div className="text-2xl font-bold text-green-900">
              {formatCurrency(profitLossReport.totalRevenue)}
            </div>
            {profitLossReport.totalDiscounts > 0 && (
              <div className="text-xs text-green-700 mt-1">
                {t('finance.reports.grossRevenue', 'Gross')}: {formatCurrency(profitLossReport.grossRevenue)} | {t('finance.reports.discounts', 'Discounts')}: -{formatCurrency(profitLossReport.totalDiscounts)}
              </div>
            )}
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-red-800">{t('finance.reports.totalExpenses')}</span>
              <TrendingDown className="text-red-600" size={20} />
            </div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency((profitLossReport.totalExpenses || 0) + (profitLossReport.totalPayroll || 0))}
            </div>
            {profitLossReport.totalPayroll > 0 && (
              <div className="text-xs text-red-700 mt-1">
                {t('finance.reports.payroll', 'Payroll')}: {formatCurrency(profitLossReport.totalPayroll)}
              </div>
            )}
          </div>

          <div className={`p-4 rounded-lg border ${profitLossReport.netIncome >= 0 ? 'bg-blue-50 border-blue-200' : 'bg-red-50 border-red-200'}`}>
            <div className="mb-2">
              <span className={`text-sm font-medium ${profitLossReport.netIncome >= 0 ? 'text-blue-800' : 'text-red-800'}`}>
                {t('finance.reports.netIncome')}
              </span>
            </div>
            <div className={`text-2xl font-bold ${profitLossReport.netIncome >= 0 ? 'text-blue-900' : 'text-red-900'}`}>
              {formatCurrency(profitLossReport.netIncome)}
            </div>
            <div className="text-sm text-gray-600 mt-1">
              {t('finance.reports.netMargin')}: {netMargin}%
            </div>
          </div>
        </div>

        {/* Detailed Statement */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.reports.account')}</th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('finance.common.amount')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              <tr className="bg-green-50">
                <td className="px-6 py-4 font-semibold text-green-900">{t('finance.reports.revenue', 'REVENUE').toUpperCase()}</td>
                <td className="px-6 py-4 text-right font-semibold text-green-900">
                  {formatCurrency(profitLossReport.grossRevenue)}
                </td>
              </tr>

              {profitLossReport.salesRevenue > 0 && (
                <tr>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">{t('finance.reports.salesRevenue', 'Sales')}</td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">{formatCurrency(profitLossReport.salesRevenue)}</td>
                </tr>
              )}
              {profitLossReport.serviceFeeRevenue > 0 && (
                <tr>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">{t('finance.reports.serviceFeeRevenue', 'Service Fees')}</td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">{formatCurrency(profitLossReport.serviceFeeRevenue)}</td>
                </tr>
              )}
              {profitLossReport.deliveryFeeRevenue > 0 && (
                <tr>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">{t('finance.reports.deliveryFeeRevenue', 'Delivery Fees')}</td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">{formatCurrency(profitLossReport.deliveryFeeRevenue)}</td>
                </tr>
              )}
              {profitLossReport.tipRevenue > 0 && (
                <tr>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">{t('finance.reports.tipRevenue', 'Tips')}</td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">{formatCurrency(profitLossReport.tipRevenue)}</td>
                </tr>
              )}

              {profitLossReport.totalDiscounts > 0 && (
                <>
                  <tr className="bg-orange-50">
                    <td className="px-6 py-3 font-medium text-orange-900">{t('finance.reports.discounts', 'DISCOUNTS')}</td>
                    <td className="px-6 py-3 text-right font-medium text-orange-900">-{formatCurrency(profitLossReport.totalDiscounts)}</td>
                  </tr>
                  {profitLossReport.discountsByType && Object.entries(profitLossReport.discountsByType).map(([type, amount]) => (
                    <tr key={type}>
                      <td className="px-6 py-2 pl-12 text-sm text-gray-600">{t(`finance.reports.discountType.${type}`, type.replace(/_/g, ' '))}</td>
                      <td className="px-6 py-2 text-right text-sm text-gray-600">-{formatCurrency(amount)}</td>
                    </tr>
                  ))}
                </>
              )}

              <tr className="bg-green-100">
                <td className="px-6 py-3 font-semibold text-green-900">{t('finance.reports.netRevenue', 'NET REVENUE')}</td>
                <td className="px-6 py-3 text-right font-semibold text-green-900">{formatCurrency(profitLossReport.totalRevenue)}</td>
              </tr>

              <tr className="bg-red-50">
                <td className="px-6 py-4 font-semibold text-red-900">{t('finance.reports.expenses', 'EXPENSES').toUpperCase()}</td>
                <td className="px-6 py-4 text-right font-semibold text-red-900">
                  {formatCurrency(profitLossReport.totalExpenses)}
                </td>
              </tr>

              {profitLossReport.expensesByCategory && Object.entries(profitLossReport.expensesByCategory).map(([category, amount]) => (
                <tr key={category}>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">
                    {t(`finance.expenses.categories.${category}`, category.replace(/_/g, ' '))}
                  </td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">
                    {formatCurrency(amount)}
                  </td>
                </tr>
              ))}

              {profitLossReport.totalPayroll > 0 && (
                <tr>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">
                    {t('finance.reports.payroll', 'Payroll')}
                  </td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">
                    {formatCurrency(profitLossReport.totalPayroll)}
                  </td>
                </tr>
              )}

              <tr className={`font-bold ${profitLossReport.netIncome >= 0 ? 'bg-blue-50' : 'bg-red-50'}`}>
                <td className="px-6 py-4 text-lg">{t('finance.reports.netIncome').toUpperCase()}</td>
                <td className={`px-6 py-4 text-right text-lg ${profitLossReport.netIncome >= 0 ? 'text-blue-900' : 'text-red-900'}`}>
                  {formatCurrency(profitLossReport.netIncome)}
                </td>
              </tr>

              {profitLossReport.orderCount > 0 && (
                <tr className="bg-gray-50">
                  <td className="px-6 py-3 text-sm text-gray-600">{t('finance.reports.orderCount', 'Total Orders')}</td>
                  <td className="px-6 py-3 text-right text-sm text-gray-600">
                    {profitLossReport.orderCount}
                    {profitLossReport.discountedOrderCount > 0 && (
                      <span className="ml-2 text-orange-600">({profitLossReport.discountedOrderCount} {t('finance.reports.withDiscounts', 'with discounts')})</span>
                    )}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    );
  };

  const renderBalanceSheet = () => {
    if (!balanceSheet) return <div className="text-center py-8">{t('finance.common.noDataAvailable')}</div>;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
            <div className="text-sm font-medium text-blue-800 mb-2">{t('finance.reports.totalAssets')}</div>
            <div className="text-2xl font-bold text-blue-900">
              {formatCurrency(balanceSheet.totalAssets)}
            </div>
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="text-sm font-medium text-red-800 mb-2">{t('finance.reports.totalLiabilities')}</div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency(balanceSheet.totalLiabilities)}
            </div>
          </div>

          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="text-sm font-medium text-green-800 mb-2">{t('finance.reports.totalEquity')}</div>
            <div className="text-2xl font-bold text-green-900">
              {formatCurrency(balanceSheet.totalEquity)}
            </div>
          </div>
        </div>

        {/* Detailed Balance Sheet */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.reports.account')}</th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('finance.reports.balance')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              <tr className="bg-blue-50">
                <td className="px-6 py-4 font-semibold text-blue-900">{t('finance.reports.assets').toUpperCase()}</td>
                <td className="px-6 py-4 text-right font-semibold text-blue-900">
                  {formatCurrency(balanceSheet.totalAssets)}
                </td>
              </tr>

              {balanceSheet.assetsByCategory && Object.entries(balanceSheet.assetsByCategory).map(([category, amount]) => (
                <tr key={category}>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">
                    {category.replace(/_/g, ' ')}
                  </td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">
                    {formatCurrency(amount)}
                  </td>
                </tr>
              ))}

              <tr className="bg-red-50">
                <td className="px-6 py-4 font-semibold text-red-900">{t('finance.reports.liabilities').toUpperCase()}</td>
                <td className="px-6 py-4 text-right font-semibold text-red-900">
                  {formatCurrency(balanceSheet.totalLiabilities)}
                </td>
              </tr>

              {balanceSheet.liabilitiesByCategory && Object.entries(balanceSheet.liabilitiesByCategory).map(([category, amount]) => (
                <tr key={category}>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">
                    {category.replace(/_/g, ' ')}
                  </td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">
                    {formatCurrency(amount)}
                  </td>
                </tr>
              ))}

              <tr className="bg-green-50">
                <td className="px-6 py-4 font-semibold text-green-900">{t('finance.reports.equity').toUpperCase()}</td>
                <td className="px-6 py-4 text-right font-semibold text-green-900">
                  {formatCurrency(balanceSheet.totalEquity)}
                </td>
              </tr>

              <tr className="bg-gray-100 font-bold">
                <td className="px-6 py-4 text-lg">{t('finance.reports.liabilitiesPlusEquity').toUpperCase()}</td>
                <td className="px-6 py-4 text-right text-lg">
                  {formatCurrency(balanceSheet.totalLiabilities + balanceSheet.totalEquity)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* Balance Check */}
        <div className={`p-4 rounded-lg ${
          Math.abs(balanceSheet.totalAssets - (balanceSheet.totalLiabilities + balanceSheet.totalEquity)) < 0.01
            ? 'bg-green-50 border border-green-200'
            : 'bg-red-50 border border-red-200'
        }`}>
          <div className="font-semibold mb-1">
            {Math.abs(balanceSheet.totalAssets - (balanceSheet.totalLiabilities + balanceSheet.totalEquity)) < 0.01
              ? '✓ ' + t('finance.reports.balanceSheetBalanced')
              : '⚠ ' + t('finance.reports.balanceSheetNotBalanced')}
          </div>
          <div className="text-sm text-gray-600">
            {t('finance.reports.balanceEquation')}
          </div>
        </div>
      </div>
    );
  };

  const renderCashFlow = () => {
    if (!cashFlowReport) return <div className="text-center py-8">{t('finance.common.noDataAvailable')}</div>;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="text-sm font-medium text-green-800 mb-2">{t('finance.reports.cashInflows')}</div>
            <div className="text-2xl font-bold text-green-900">
              {formatCurrency(cashFlowReport.cashInflows)}
            </div>
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="text-sm font-medium text-red-800 mb-2">{t('finance.reports.cashOutflows')}</div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency(cashFlowReport.cashOutflows)}
            </div>
          </div>

          <div className={`p-4 rounded-lg border ${cashFlowReport.netCashFlow >= 0 ? 'bg-blue-50 border-blue-200' : 'bg-red-50 border-red-200'}`}>
            <div className={`text-sm font-medium mb-2 ${cashFlowReport.netCashFlow >= 0 ? 'text-blue-800' : 'text-red-800'}`}>
              {t('finance.reports.netCashFlow')}
            </div>
            <div className={`text-2xl font-bold ${cashFlowReport.netCashFlow >= 0 ? 'text-blue-900' : 'text-red-900'}`}>
              {formatCurrency(cashFlowReport.netCashFlow)}
            </div>
          </div>
        </div>

        {/* Cash Flow Statement */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.reports.cashFlowCategory')}</th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">{t('finance.common.amount')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              <tr className="bg-green-50">
                <td className="px-6 py-4 font-semibold text-green-900">{t('finance.reports.cashInflows')}</td>
                <td className="px-6 py-4 text-right font-semibold text-green-900">
                  {formatCurrency(cashFlowReport.cashInflows)}
                </td>
              </tr>

              <tr className="bg-red-50">
                <td className="px-6 py-4 font-semibold text-red-900">{t('finance.reports.cashOutflows')}</td>
                <td className="px-6 py-4 text-right font-semibold text-red-900">
                  {formatCurrency(cashFlowReport.cashOutflows)}
                </td>
              </tr>

              <tr className={`font-bold ${cashFlowReport.netCashFlow >= 0 ? 'bg-blue-50' : 'bg-red-50'}`}>
                <td className="px-6 py-4 text-lg">{t('finance.reports.netCashFlow').toUpperCase()}</td>
                <td className={`px-6 py-4 text-right text-lg ${cashFlowReport.netCashFlow >= 0 ? 'text-blue-900' : 'text-red-900'}`}>
                  {formatCurrency(cashFlowReport.netCashFlow)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    );
  };

  const renderCOGS = () => {
    if (!cogsReport) return <div className="text-center py-8">{t('finance.common.noDataAvailable')}</div>;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="text-sm font-medium text-green-800 mb-2">{t('finance.reports.totalRevenue')}</div>
            <div className="text-2xl font-bold text-green-900">
              {formatCurrency(cogsReport.totalRevenue)}
            </div>
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="text-sm font-medium text-red-800 mb-2">{t('finance.reports.totalCOGS')}</div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency(cogsReport.totalCogs)}
            </div>
          </div>

          <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
            <div className="text-sm font-medium text-blue-800 mb-2">{t('finance.reports.cogsPercentage')}</div>
            <div className="text-2xl font-bold text-blue-900">
              {formatPercentage(cogsReport.cogsPercentage)}
            </div>
            <div className="text-xs text-gray-600 mt-1">
              {cogsReport.cogsPercentage < 30 ? t('finance.reports.excellent') : cogsReport.cogsPercentage < 35 ? t('finance.reports.good') : cogsReport.cogsPercentage < 40 ? t('finance.reports.fair') : t('finance.reports.high')}
            </div>
          </div>
        </div>

        {/* COGS Analysis */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold mb-4">{t('finance.reports.cogsAnalysis')}</h3>

          <div className="space-y-4">
            <div className="flex justify-between items-center p-3 bg-gray-50 rounded">
              <span className="font-medium">{t('finance.reports.grossProfit')}</span>
              <span className="text-lg font-bold text-green-600">
                {formatCurrency(cogsReport.totalRevenue - cogsReport.totalCogs)}
              </span>
            </div>

            <div className="flex justify-between items-center p-3 bg-gray-50 rounded">
              <span className="font-medium">{t('finance.reports.grossMargin')}</span>
              <span className="text-lg font-bold text-blue-600">
                {formatPercentage(cogsReport.totalRevenue > 0
                  ? ((cogsReport.totalRevenue - cogsReport.totalCogs) / cogsReport.totalRevenue * 100)
                  : 0
                )}
              </span>
            </div>
          </div>

          <div className="mt-6 p-4 bg-blue-50 rounded-lg">
            <h4 className="font-semibold text-blue-900 mb-2">{t('finance.reports.industryBenchmarks')}</h4>
            <ul className="text-sm text-blue-800 space-y-1">
              <li>• {t('finance.reports.excellentCOGS')}: &lt; 30%</li>
              <li>• {t('finance.reports.goodCOGS')}: 30-35%</li>
              <li>• {t('finance.reports.fairCOGS')}: 35-40%</li>
              <li>• {t('finance.reports.highCOGS')}: &gt; 40%</li>
            </ul>
          </div>
        </div>
      </div>
    );
  };

  const handleRefresh = () => {
    if (selectedRestaurant) {
      checkAccountsAndLoadReports(selectedRestaurant);
    }
  };

  const handleReinitialize = async () => {
    if (!selectedRestaurant) return;

    if (!confirm(t('finance.reports.confirmReinitialize', 'This will reset all accounts to default values. Existing account balances will be preserved. Continue?'))) {
      return;
    }

    setInitializingAccounts(true);
    try {
      await financialAPI.initializeAccounts(selectedRestaurant);
      await loadReports(selectedRestaurant);
      alert(t('finance.reports.reinitializeSuccess', 'Chart of Accounts re-initialized successfully'));
    } catch (error) {
      console.error('Failed to re-initialize accounts:', error);
      alert(t('finance.reports.initializationFailed', 'Failed to initialize Chart of Accounts'));
    } finally {
      setInitializingAccounts(false);
    }
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">{t('finance.reports.title')}</h1>
        <div className="flex items-center gap-4">
          {/* Restaurant Selector */}
          <div className="flex items-center gap-2">
            <Building2 className="h-5 w-5 text-gray-500" />
            <select
              value={selectedRestaurant || ''}
              onChange={(e) => setSelectedRestaurant(e.target.value ? parseInt(e.target.value) : null)}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-[200px]"
            >
              <option value="">{t('finance.common.selectRestaurant', 'Select Restaurant')}</option>
              {restaurants.map((restaurant) => (
                <option key={restaurant.id} value={restaurant.id}>
                  {restaurant.name}
                </option>
              ))}
            </select>
          </div>

          {/* Refresh and Initialize Buttons */}
          {accountsExist && selectedRestaurant && (
            <div className="flex items-center gap-2">
              <button
                onClick={handleRefresh}
                disabled={loading}
                className="inline-flex items-center px-3 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                title={t('finance.reports.refresh', 'Refresh Reports')}
              >
                <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
                {t('finance.reports.refresh', 'Refresh')}
              </button>
              <button
                onClick={handleReinitialize}
                disabled={initializingAccounts}
                className="inline-flex items-center px-3 py-2 border border-blue-300 rounded-lg text-sm font-medium text-blue-700 bg-blue-50 hover:bg-blue-100 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                title={t('finance.reports.reinitializeAccounts', 'Re-initialize Accounts')}
              >
                <Settings className={`h-4 w-4 mr-2 ${initializingAccounts ? 'animate-spin' : ''}`} />
                {t('finance.reports.initializeAccounts', 'Initialize Accounts')}
              </button>
            </div>
          )}

          {/* Period Type Filter */}
          <div className="flex rounded-lg border border-gray-300 overflow-hidden">
            <button
              onClick={() => handlePeriodChange('daily')}
              className={`px-4 py-2 text-sm font-medium transition-colors ${
                periodType === 'daily'
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-700 hover:bg-gray-50'
              }`}
            >
              {t('finance.reports.daily', 'Daily')}
            </button>
            <button
              onClick={() => handlePeriodChange('monthly')}
              className={`px-4 py-2 text-sm font-medium transition-colors border-l border-gray-300 ${
                periodType === 'monthly'
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-700 hover:bg-gray-50'
              }`}
            >
              {t('finance.reports.monthly', 'Monthly')}
            </button>
          </div>

          {/* Date Range Selector */}
          <div className="flex items-center gap-2">
            <Calendar size={20} className="text-gray-500" />
            <input
              type="date"
              value={dateRange.startDate}
              onChange={(e) => {
                setPeriodType('custom');
                setDateRange({ ...dateRange, startDate: e.target.value });
              }}
              className="px-3 py-2 border border-gray-300 rounded-lg"
            />
            <span className="text-gray-500">{t('finance.common.to')}</span>
            <input
              type="date"
              value={dateRange.endDate}
              onChange={(e) => {
                setPeriodType('custom');
                setDateRange({ ...dateRange, endDate: e.target.value });
              }}
              className="px-3 py-2 border border-gray-300 rounded-lg"
            />
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="mb-6 border-b border-gray-200">
        <nav className="flex space-x-8">
          <button
            onClick={() => setActiveTab('profitLoss')}
            className={`pb-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'profitLoss'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            <div className="flex items-center gap-2">
              <TrendingUp size={18} />
              {t('finance.reports.profitLoss')}
            </div>
          </button>
          <button
            onClick={() => setActiveTab('balanceSheet')}
            className={`pb-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'balanceSheet'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            <div className="flex items-center gap-2">
              <BarChart3 size={18} />
              {t('finance.reports.balanceSheet')}
            </div>
          </button>
          <button
            onClick={() => setActiveTab('cashFlow')}
            className={`pb-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'cashFlow'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            {t('finance.reports.cashFlow')}
          </button>
          <button
            onClick={() => setActiveTab('cogs')}
            className={`pb-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'cogs'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            <div className="flex items-center gap-2">
              <PieChart size={18} />
              {t('finance.reports.cogsAnalysis')}
            </div>
          </button>
        </nav>
      </div>

      {/* Report Content */}
      {loading ? (
        <div className="flex justify-center items-center py-12">
          <div className="text-gray-500">{t('finance.common.loadingReports')}</div>
        </div>
      ) : !accountsExist ? (
        <div className="flex flex-col items-center justify-center py-16 px-4">
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-8 max-w-lg text-center">
            <AlertCircle className="mx-auto h-12 w-12 text-yellow-500 mb-4" />
            <h3 className="text-lg font-semibold text-yellow-800 mb-2">
              {t('finance.reports.noAccountsTitle', 'Chart of Accounts Not Initialized')}
            </h3>
            <p className="text-yellow-700 mb-6">
              {t('finance.reports.noAccountsDescription',
                'Financial reports require a Chart of Accounts to be set up first. Initialize the default accounts to start tracking revenue, expenses, and generating financial reports.')}
            </p>
            <button
              onClick={initializeChartOfAccounts}
              disabled={initializingAccounts}
              className="inline-flex items-center px-6 py-3 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {initializingAccounts ? (
                <>
                  <RefreshCw className="animate-spin -ml-1 mr-2 h-5 w-5" />
                  {t('finance.reports.initializing', 'Initializing...')}
                </>
              ) : (
                <>
                  {t('finance.reports.initializeAccounts', 'Initialize Chart of Accounts')}
                </>
              )}
            </button>
          </div>
        </div>
      ) : (
        <div>
          {activeTab === 'profitLoss' && renderProfitLoss()}
          {activeTab === 'balanceSheet' && renderBalanceSheet()}
          {activeTab === 'cashFlow' && renderCashFlow()}
          {activeTab === 'cogs' && renderCOGS()}
        </div>
      )}
    </div>
  );
};

export default FinancialReports;
