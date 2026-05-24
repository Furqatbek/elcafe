import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { shiftAPI, orderAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { getCurrentRestaurantId } from '../utils/restaurant';
import {
  Clock, ChevronDown, ChevronUp, ShoppingCart,
  CreditCard, Banknote, User, Coffee,
} from 'lucide-react';

const statusColors = {
  ACTIVE: 'bg-green-100 text-green-800',
  ON_BREAK: 'bg-yellow-100 text-yellow-800',
  COMPLETED: 'bg-blue-100 text-blue-800',
  APPROVED: 'bg-indigo-100 text-indigo-800',
  DISPUTED: 'bg-red-100 text-red-800',
};

const orderStatusColors = {
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  REJECTED: 'bg-red-100 text-red-800',
  PREPARING: 'bg-yellow-100 text-yellow-800',
  READY: 'bg-blue-100 text-blue-800',
  ACCEPTED: 'bg-cyan-100 text-cyan-800',
  NEW: 'bg-gray-100 text-gray-800',
  DELIVERED: 'bg-green-100 text-green-800',
};

export default function OrdersByShift() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(getCurrentRestaurantId() || 1);
  const [shifts, setShifts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [expandedShift, setExpandedShift] = useState(null);
  const [shiftOrders, setShiftOrders] = useState({});
  const [ordersLoading, setOrdersLoading] = useState(null);
  const [expandedOrder, setExpandedOrder] = useState(null);
  const [filterStatus, setFilterStatus] = useState('all');
  const [dateRange, setDateRange] = useState({
    startDate: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0],
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
    if (selectedRestaurant) loadShifts();
  }, [selectedRestaurant, dateRange]);

  const loadRestaurants = async () => {
    try {
      const res = await restaurantAPI.getAll({ page: 0, size: 100 });
      const data = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error('Failed to load restaurants:', e);
    }
  };

  const loadShifts = async () => {
    try {
      setLoading(true);
      const res = await shiftAPI.getByDateRange(selectedRestaurant, dateRange.startDate, dateRange.endDate);
      const data = res.data || [];
      setShifts(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error('Failed to load shifts:', e);
      setShifts([]);
    } finally {
      setLoading(false);
    }
  };

  const toggleShift = async (shift) => {
    if (expandedShift === shift.id) {
      setExpandedShift(null);
      setExpandedOrder(null);
      return;
    }
    setExpandedShift(shift.id);
    setExpandedOrder(null);
    if (!shiftOrders[shift.id]) {
      try {
        setOrdersLoading(shift.id);
        const res = await orderAPI.getByShift(shift.id);
        setShiftOrders((prev) => ({ ...prev, [shift.id]: res.data.data || [] }));
      } catch (e) {
        console.error('Failed to load orders for shift:', e);
        setShiftOrders((prev) => ({ ...prev, [shift.id]: [] }));
      } finally {
        setOrdersLoading(null);
      }
    }
  };

  const formatTime = (dt) => {
    if (!dt) return '—';
    return new Date(dt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const formatDateTime = (dt) => {
    if (!dt) return '—';
    return new Date(dt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  const formatMoney = (v) => {
    if (v == null) return '—';
    return Number(v).toLocaleString();
  };

  const filteredShifts = shifts.filter((s) => filterStatus === 'all' || s.status === filterStatus);

  const shiftStats = {
    total: shifts.length,
    active: shifts.filter((s) => s.status === 'ACTIVE' || s.status === 'ON_BREAK').length,
    totalSales: shifts.reduce((sum, s) => sum + (s.totalSales || 0), 0),
    totalOrders: shifts.reduce((sum, s) => sum + (s.totalOrders || 0), 0),
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">{t('ordersByShift.title', 'Orders by Shift')}</h1>
          <p className="text-gray-500 mt-1">{t('ordersByShift.subtitle', 'View orders grouped by employee shifts')}</p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 mb-6">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => setSelectedRestaurant(parseInt(e.target.value))}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
        >
          {restaurants.map((r) => (
            <option key={r.id} value={r.id}>{r.name}</option>
          ))}
        </select>
        <input
          type="date"
          value={dateRange.startDate}
          onChange={(e) => setDateRange((p) => ({ ...p, startDate: e.target.value }))}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
        />
        <input
          type="date"
          value={dateRange.endDate}
          onChange={(e) => setDateRange((p) => ({ ...p, endDate: e.target.value }))}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
        >
          <option value="all">{t('ordersByShift.allStatuses', 'All Statuses')}</option>
          <option value="ACTIVE">{t('ordersByShift.status.active', 'Active')}</option>
          <option value="COMPLETED">{t('ordersByShift.status.completed', 'Completed')}</option>
          <option value="APPROVED">{t('ordersByShift.status.approved', 'Approved')}</option>
        </select>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        <div className="bg-white rounded-lg border p-4">
          <p className="text-sm text-gray-500">{t('ordersByShift.stats.totalShifts', 'Total Shifts')}</p>
          <p className="text-2xl font-bold">{shiftStats.total}</p>
        </div>
        <div className="bg-white rounded-lg border p-4">
          <p className="text-sm text-gray-500">{t('ordersByShift.stats.activeShifts', 'Active Shifts')}</p>
          <p className="text-2xl font-bold text-green-600">{shiftStats.active}</p>
        </div>
        <div className="bg-white rounded-lg border p-4">
          <p className="text-sm text-gray-500">{t('ordersByShift.stats.totalOrders', 'Total Orders')}</p>
          <p className="text-2xl font-bold">{shiftStats.totalOrders}</p>
        </div>
        <div className="bg-white rounded-lg border p-4">
          <p className="text-sm text-gray-500">{t('ordersByShift.stats.totalSales', 'Total Sales')}</p>
          <p className="text-2xl font-bold">{formatMoney(shiftStats.totalSales)}</p>
        </div>
      </div>

      {/* Shifts list */}
      <div className="space-y-3">
        {loading ? (
          <div className="text-center py-8 text-gray-500">{t('finance.common.loading', 'Loading...')}</div>
        ) : filteredShifts.length === 0 ? (
          <div className="text-center py-8 text-gray-500">{t('ordersByShift.noShifts', 'No shifts found for the selected period')}</div>
        ) : (
          filteredShifts.map((shift) => (
            <div key={shift.id} className="bg-white rounded-lg border overflow-hidden">
              {/* Shift header row */}
              <button
                onClick={() => toggleShift(shift)}
                className="w-full px-6 py-4 flex items-center justify-between hover:bg-gray-50 transition-colors"
              >
                <div className="flex items-center gap-6">
                  <div className="flex items-center gap-2">
                    <User size={18} className="text-gray-400" />
                    <span className="font-semibold text-gray-900">{shift.employeeName || '—'}</span>
                  </div>
                  <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[shift.status] || 'bg-gray-100 text-gray-800'}`}>
                    {shift.status}
                  </span>
                  <div className="flex items-center gap-1 text-sm text-gray-500">
                    <Clock size={14} />
                    <span>{shift.shiftDate} {formatTime(shift.clockIn)} — {formatTime(shift.clockOut)}</span>
                  </div>
                </div>
                <div className="flex items-center gap-6">
                  <div className="flex items-center gap-4 text-sm">
                    <span className="flex items-center gap-1" title={t('ordersByShift.orders', 'Orders')}>
                      <ShoppingCart size={14} className="text-gray-400" /> {shift.totalOrders || 0}
                    </span>
                    <span className="flex items-center gap-1 text-green-600" title={t('ordersByShift.cash', 'Cash')}>
                      <Banknote size={14} /> {formatMoney(shift.totalCashSales)}
                    </span>
                    <span className="flex items-center gap-1 text-blue-600" title={t('ordersByShift.card', 'Card')}>
                      <CreditCard size={14} /> {formatMoney(shift.totalCardSales)}
                    </span>
                    <span className="font-semibold">{formatMoney(shift.totalSales)}</span>
                  </div>
                  {expandedShift === shift.id ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                </div>
              </button>

              {/* Expanded orders list */}
              {expandedShift === shift.id && (
                <div className="border-t bg-gray-50 px-6 py-4">
                  {ordersLoading === shift.id ? (
                    <div className="text-center py-4 text-gray-500">{t('finance.common.loading', 'Loading...')}</div>
                  ) : (shiftOrders[shift.id] || []).length === 0 ? (
                    <div className="text-center py-4 text-gray-500">{t('ordersByShift.noOrders', 'No orders in this shift')}</div>
                  ) : (
                    <div className="space-y-2">
                      <div className="text-sm font-medium text-gray-700 mb-2">
                        {t('ordersByShift.ordersCount', '{{count}} orders', { count: shiftOrders[shift.id].length })}
                      </div>
                      <table className="min-w-full bg-white rounded-lg overflow-hidden">
                        <thead className="bg-gray-100">
                          <tr>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">#</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.time', 'Time')}</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.type', 'Type')}</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.tableName', 'Table')}</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.waiter', 'Waiter')}</th>
                            <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.items', 'Items')}</th>
                            <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.total', 'Total')}</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.payment', 'Payment')}</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">{t('ordersByShift.table.status', 'Status')}</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-200">
                          {shiftOrders[shift.id].map((order) => {
                            const items = (order.items || []).filter((i) => !i.deletedAt);
                            const isExpanded = expandedOrder === order.id;
                            return (
                              <React.Fragment key={order.id}>
                                <tr
                                  className="hover:bg-gray-50 cursor-pointer"
                                  onClick={() => setExpandedOrder(isExpanded ? null : order.id)}
                                >
                                  <td className="px-4 py-2 text-sm font-medium">{order.orderNumber}</td>
                                  <td className="px-4 py-2 text-sm text-gray-500">{formatDateTime(order.createdAt)}</td>
                                  <td className="px-4 py-2 text-sm">{order.orderType}</td>
                                  <td className="px-4 py-2 text-sm">{order.diningTable?.name || '—'}</td>
                                  <td className="px-4 py-2 text-sm">{order.waiter ? `${order.waiter.firstName || ''} ${order.waiter.lastName || ''}`.trim() : '—'}</td>
                                  <td className="px-4 py-2 text-sm text-right">{items.reduce((s, i) => s + (i.quantity || 0), 0)}</td>
                                  <td className="px-4 py-2 text-sm text-right font-medium">{formatMoney(order.total)}</td>
                                  <td className="px-4 py-2 text-sm">{order.paymentMethod || order.paymentStatus || '—'}</td>
                                  <td className="px-4 py-2">
                                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${orderStatusColors[order.status] || 'bg-gray-100 text-gray-800'}`}>
                                      {order.status}
                                    </span>
                                  </td>
                                </tr>
                                {isExpanded && items.length > 0 && (
                                  <tr>
                                    <td colSpan="9" className="px-4 py-3 bg-blue-50">
                                      <div className="text-xs font-medium text-gray-600 mb-2 uppercase">{t('ordersByShift.orderItems', 'Order Items')}</div>
                                      <table className="w-full">
                                        <thead>
                                          <tr className="text-xs text-gray-500">
                                            <th className="text-left pb-1">{t('ordersByShift.item.product', 'Product')}</th>
                                            <th className="text-left pb-1">{t('ordersByShift.item.variant', 'Variant')}</th>
                                            <th className="text-right pb-1">{t('ordersByShift.item.qty', 'Qty')}</th>
                                            <th className="text-right pb-1">{t('ordersByShift.item.unitPrice', 'Unit Price')}</th>
                                            <th className="text-right pb-1">{t('ordersByShift.item.total', 'Total')}</th>
                                            <th className="text-left pb-1">{t('ordersByShift.item.addOns', 'Add-ons')}</th>
                                            <th className="text-left pb-1">{t('ordersByShift.item.notes', 'Notes')}</th>
                                          </tr>
                                        </thead>
                                        <tbody>
                                          {items.map((item, idx) => (
                                            <tr key={item.id || idx} className="text-sm border-t border-blue-100">
                                              <td className="py-1">{item.productName || '—'}</td>
                                              <td className="py-1 text-gray-500">{item.variantName || '—'}</td>
                                              <td className="py-1 text-right">{item.quantity}</td>
                                              <td className="py-1 text-right">{formatMoney(item.unitPrice)}</td>
                                              <td className="py-1 text-right font-medium">{formatMoney(item.totalPrice)}</td>
                                              <td className="py-1 text-gray-500 text-xs">
                                                {(item.itemAddOns || []).map((a) => a.name || a.addOnName).filter(Boolean).join(', ') || '—'}
                                              </td>
                                              <td className="py-1 text-gray-500 text-xs">{item.specialInstructions || '—'}</td>
                                            </tr>
                                          ))}
                                        </tbody>
                                      </table>
                                      {(order.discount > 0 || order.tipAmount > 0 || order.deliveryFee > 0) && (
                                        <div className="flex gap-4 mt-2 pt-2 border-t border-blue-200 text-xs text-gray-600">
                                          {order.discount > 0 && <span>{t('ordersByShift.discount', 'Discount')}: -{formatMoney(order.discount)}</span>}
                                          {order.tipAmount > 0 && <span>{t('ordersByShift.tip', 'Tip')}: +{formatMoney(order.tipAmount)}</span>}
                                          {order.deliveryFee > 0 && <span>{t('ordersByShift.delivery', 'Delivery')}: {formatMoney(order.deliveryFee)}</span>}
                                        </div>
                                      )}
                                    </td>
                                  </tr>
                                )}
                              </React.Fragment>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
