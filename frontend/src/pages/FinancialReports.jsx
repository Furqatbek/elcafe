import React, { useState, useEffect } from 'react';
import { financialAPI } from '../services/api';
import { useAuthStore } from '../stores/authStore';
import { TrendingUp, TrendingDown, DollarSign, PieChart, BarChart3, Calendar } from 'lucide-react';

const FinancialReports = () => {
  const { user } = useAuthStore();
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('profitLoss');

  const [dateRange, setDateRange] = useState({
    startDate: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0]
  });

  const [profitLossReport, setProfitLossReport] = useState(null);
  const [balanceSheet, setBalanceSheet] = useState(null);
  const [cashFlowReport, setCashFlowReport] = useState(null);
  const [cogsReport, setCogsReport] = useState(null);

  useEffect(() => {
    if (user?.restaurantId) {
      setSelectedRestaurant(user.restaurantId);
      loadReports(user.restaurantId);
    }
  }, [user, dateRange]);

  const loadReports = async (restaurantId) => {
    setLoading(true);
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
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD'
    }).format(amount || 0);
  };

  const formatPercentage = (value) => {
    return `${(value || 0).toFixed(2)}%`;
  };

  const renderProfitLoss = () => {
    if (!profitLossReport) return <div className="text-center py-8">No data available</div>;

    const netMargin = profitLossReport.totalRevenue > 0
      ? (profitLossReport.netIncome / profitLossReport.totalRevenue * 100).toFixed(2)
      : 0;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-green-800">Total Revenue</span>
              <TrendingUp className="text-green-600" size={20} />
            </div>
            <div className="text-2xl font-bold text-green-900">
              {formatCurrency(profitLossReport.totalRevenue)}
            </div>
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-red-800">Total Expenses</span>
              <TrendingDown className="text-red-600" size={20} />
            </div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency(profitLossReport.totalExpenses)}
            </div>
          </div>

          <div className={`p-4 rounded-lg border ${profitLossReport.netIncome >= 0 ? 'bg-blue-50 border-blue-200' : 'bg-red-50 border-red-200'}`}>
            <div className="flex items-center justify-between mb-2">
              <span className={`text-sm font-medium ${profitLossReport.netIncome >= 0 ? 'text-blue-800' : 'text-red-800'}`}>
                Net Income
              </span>
              <DollarSign className={profitLossReport.netIncome >= 0 ? 'text-blue-600' : 'text-red-600'} size={20} />
            </div>
            <div className={`text-2xl font-bold ${profitLossReport.netIncome >= 0 ? 'text-blue-900' : 'text-red-900'}`}>
              {formatCurrency(profitLossReport.netIncome)}
            </div>
            <div className="text-sm text-gray-600 mt-1">
              Net Margin: {netMargin}%
            </div>
          </div>
        </div>

        {/* Detailed Statement */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Account</th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              <tr className="bg-green-50">
                <td className="px-6 py-4 font-semibold text-green-900">REVENUE</td>
                <td className="px-6 py-4 text-right font-semibold text-green-900">
                  {formatCurrency(profitLossReport.totalRevenue)}
                </td>
              </tr>

              <tr className="bg-red-50">
                <td className="px-6 py-4 font-semibold text-red-900">EXPENSES</td>
                <td className="px-6 py-4 text-right font-semibold text-red-900">
                  {formatCurrency(profitLossReport.totalExpenses)}
                </td>
              </tr>

              {profitLossReport.expensesByCategory && Object.entries(profitLossReport.expensesByCategory).map(([category, amount]) => (
                <tr key={category}>
                  <td className="px-6 py-3 pl-12 text-sm text-gray-700">
                    {category.replace(/_/g, ' ')}
                  </td>
                  <td className="px-6 py-3 text-right text-sm text-gray-700">
                    {formatCurrency(amount)}
                  </td>
                </tr>
              ))}

              <tr className={`font-bold ${profitLossReport.netIncome >= 0 ? 'bg-blue-50' : 'bg-red-50'}`}>
                <td className="px-6 py-4 text-lg">NET INCOME</td>
                <td className={`px-6 py-4 text-right text-lg ${profitLossReport.netIncome >= 0 ? 'text-blue-900' : 'text-red-900'}`}>
                  {formatCurrency(profitLossReport.netIncome)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    );
  };

  const renderBalanceSheet = () => {
    if (!balanceSheet) return <div className="text-center py-8">No data available</div>;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
            <div className="text-sm font-medium text-blue-800 mb-2">Total Assets</div>
            <div className="text-2xl font-bold text-blue-900">
              {formatCurrency(balanceSheet.totalAssets)}
            </div>
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="text-sm font-medium text-red-800 mb-2">Total Liabilities</div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency(balanceSheet.totalLiabilities)}
            </div>
          </div>

          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="text-sm font-medium text-green-800 mb-2">Total Equity</div>
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
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Account</th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Balance</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              <tr className="bg-blue-50">
                <td className="px-6 py-4 font-semibold text-blue-900">ASSETS</td>
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
                <td className="px-6 py-4 font-semibold text-red-900">LIABILITIES</td>
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
                <td className="px-6 py-4 font-semibold text-green-900">EQUITY</td>
                <td className="px-6 py-4 text-right font-semibold text-green-900">
                  {formatCurrency(balanceSheet.totalEquity)}
                </td>
              </tr>

              <tr className="bg-gray-100 font-bold">
                <td className="px-6 py-4 text-lg">LIABILITIES + EQUITY</td>
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
              ? '✓ Balance Sheet is Balanced'
              : '⚠ Balance Sheet is Not Balanced'}
          </div>
          <div className="text-sm text-gray-600">
            Assets = Liabilities + Equity
          </div>
        </div>
      </div>
    );
  };

  const renderCashFlow = () => {
    if (!cashFlowReport) return <div className="text-center py-8">No data available</div>;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="text-sm font-medium text-green-800 mb-2">Cash Inflows</div>
            <div className="text-2xl font-bold text-green-900">
              {formatCurrency(cashFlowReport.cashInflows)}
            </div>
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="text-sm font-medium text-red-800 mb-2">Cash Outflows</div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency(cashFlowReport.cashOutflows)}
            </div>
          </div>

          <div className={`p-4 rounded-lg border ${cashFlowReport.netCashFlow >= 0 ? 'bg-blue-50 border-blue-200' : 'bg-red-50 border-red-200'}`}>
            <div className={`text-sm font-medium mb-2 ${cashFlowReport.netCashFlow >= 0 ? 'text-blue-800' : 'text-red-800'}`}>
              Net Cash Flow
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
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Cash Flow Category</th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              <tr className="bg-green-50">
                <td className="px-6 py-4 font-semibold text-green-900">Cash Inflows</td>
                <td className="px-6 py-4 text-right font-semibold text-green-900">
                  {formatCurrency(cashFlowReport.cashInflows)}
                </td>
              </tr>

              <tr className="bg-red-50">
                <td className="px-6 py-4 font-semibold text-red-900">Cash Outflows</td>
                <td className="px-6 py-4 text-right font-semibold text-red-900">
                  {formatCurrency(cashFlowReport.cashOutflows)}
                </td>
              </tr>

              <tr className={`font-bold ${cashFlowReport.netCashFlow >= 0 ? 'bg-blue-50' : 'bg-red-50'}`}>
                <td className="px-6 py-4 text-lg">NET CASH FLOW</td>
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
    if (!cogsReport) return <div className="text-center py-8">No data available</div>;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="text-sm font-medium text-green-800 mb-2">Total Revenue</div>
            <div className="text-2xl font-bold text-green-900">
              {formatCurrency(cogsReport.totalRevenue)}
            </div>
          </div>

          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <div className="text-sm font-medium text-red-800 mb-2">Total COGS</div>
            <div className="text-2xl font-bold text-red-900">
              {formatCurrency(cogsReport.totalCogs)}
            </div>
          </div>

          <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
            <div className="text-sm font-medium text-blue-800 mb-2">COGS Percentage</div>
            <div className="text-2xl font-bold text-blue-900">
              {formatPercentage(cogsReport.cogsPercentage)}
            </div>
            <div className="text-xs text-gray-600 mt-1">
              {cogsReport.cogsPercentage < 30 ? 'Excellent' : cogsReport.cogsPercentage < 35 ? 'Good' : cogsReport.cogsPercentage < 40 ? 'Fair' : 'High'}
            </div>
          </div>
        </div>

        {/* COGS Analysis */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold mb-4">COGS Analysis</h3>

          <div className="space-y-4">
            <div className="flex justify-between items-center p-3 bg-gray-50 rounded">
              <span className="font-medium">Gross Profit</span>
              <span className="text-lg font-bold text-green-600">
                {formatCurrency(cogsReport.totalRevenue - cogsReport.totalCogs)}
              </span>
            </div>

            <div className="flex justify-between items-center p-3 bg-gray-50 rounded">
              <span className="font-medium">Gross Margin</span>
              <span className="text-lg font-bold text-blue-600">
                {formatPercentage(cogsReport.totalRevenue > 0
                  ? ((cogsReport.totalRevenue - cogsReport.totalCogs) / cogsReport.totalRevenue * 100)
                  : 0
                )}
              </span>
            </div>
          </div>

          <div className="mt-6 p-4 bg-blue-50 rounded-lg">
            <h4 className="font-semibold text-blue-900 mb-2">Industry Benchmarks</h4>
            <ul className="text-sm text-blue-800 space-y-1">
              <li>• Excellent COGS: &lt; 30%</li>
              <li>• Good COGS: 30-35%</li>
              <li>• Fair COGS: 35-40%</li>
              <li>• High COGS: &gt; 40%</li>
            </ul>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Financial Reports</h1>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Calendar size={20} className="text-gray-500" />
            <input
              type="date"
              value={dateRange.startDate}
              onChange={(e) => setDateRange({ ...dateRange, startDate: e.target.value })}
              className="px-3 py-2 border border-gray-300 rounded-lg"
            />
            <span className="text-gray-500">to</span>
            <input
              type="date"
              value={dateRange.endDate}
              onChange={(e) => setDateRange({ ...dateRange, endDate: e.target.value })}
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
              Profit & Loss
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
              Balance Sheet
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
            <div className="flex items-center gap-2">
              <DollarSign size={18} />
              Cash Flow
            </div>
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
              COGS Analysis
            </div>
          </button>
        </nav>
      </div>

      {/* Report Content */}
      {loading ? (
        <div className="flex justify-center items-center py-12">
          <div className="text-gray-500">Loading reports...</div>
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
