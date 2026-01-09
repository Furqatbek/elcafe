import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../components/ui/dialog';
import {
  ShoppingCart,
  DollarSign,
  TrendingUp,
  Clock,
  Calendar,
  Filter,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Eye,
  Download,
  Search,
  X,
  Printer,
} from 'lucide-react';
import { format, startOfDay, endOfDay, subDays, startOfMonth, endOfMonth, parseISO } from 'date-fns';
import PrintReceipt from '../components/PrintReceipt';

const orderStatusColors = {
  NEW: 'bg-blue-100 text-blue-800',
  ACCEPTED: 'bg-green-100 text-green-800',
  PREPARING: 'bg-yellow-100 text-yellow-800',
  READY: 'bg-purple-100 text-purple-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  ON_DELIVERY: 'bg-indigo-100 text-indigo-800',
  COURIER_ASSIGNED: 'bg-cyan-100 text-cyan-800',
};

const paymentStatusColors = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  COMPLETED: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  REFUNDED: 'bg-gray-100 text-gray-800',
};

export default function OrdersHistory() {
  const { t } = useTranslation();

  // Data state
  const [orders, setOrders] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalOrders: 0,
    totalRevenue: 0,
    avgOrderValue: 0,
    completedOrders: 0,
    cancelledOrders: 0,
  });

  // Filter state
  const [selectedRestaurant, setSelectedRestaurant] = useState('1');
  const [selectedStatus, setSelectedStatus] = useState('all');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [shiftDate, setShiftDate] = useState(format(new Date(), 'yyyy-MM-dd')); // Default to today's shift
  const [useShiftFilter, setUseShiftFilter] = useState(true); // Use shift-aware filtering by default
  const [searchQuery, setSearchQuery] = useState('');

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [pageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);

  // Modal state
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [detailsModalOpen, setDetailsModalOpen] = useState(false);

  // Load restaurants
  useEffect(() => {
    const loadRestaurants = async () => {
      try {
        const response = await restaurantAPI.getActive();
        setRestaurants(response.data.data || []);
      } catch (error) {
        console.error('Failed to load restaurants:', error);
      }
    };
    loadRestaurants();
  }, []);

  // Load orders
  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page: currentPage - 1,
        size: pageSize,
        sort: 'createdAt,desc',
      };

      if (selectedRestaurant !== 'all') {
        params.restaurantId = selectedRestaurant;
      }
      if (selectedStatus !== 'all') {
        params.status = selectedStatus;
      }

      // Use shift-aware filtering or explicit date range
      if (useShiftFilter && shiftDate) {
        params.shiftDate = shiftDate;
      } else {
        if (dateFrom) {
          params.fromDate = startOfDay(new Date(dateFrom)).toISOString();
        }
        if (dateTo) {
          params.toDate = endOfDay(new Date(dateTo)).toISOString();
        }
      }

      if (searchQuery) {
        params.search = searchQuery;
      }

      const response = await orderAPI.getAll(params);
      const data = response.data.data;

      if (data?.content) {
        setOrders(data.content);
        setTotalPages(data.totalPages || 1);
        setTotalElements(data.totalElements || 0);
      } else if (Array.isArray(data)) {
        setOrders(data);
        setTotalPages(1);
        setTotalElements(data.length);
      } else {
        setOrders([]);
        setTotalPages(1);
        setTotalElements(0);
      }

      // Calculate stats from loaded orders
      calculateStats(Array.isArray(data) ? data : data?.content || []);
    } catch (error) {
      console.error('Failed to load orders:', error);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, selectedRestaurant, selectedStatus, dateFrom, dateTo, shiftDate, useShiftFilter, searchQuery]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  // Calculate statistics
  const calculateStats = (ordersList) => {
    const completed = ordersList.filter(o => o.status === 'DELIVERED' || o.paymentStatus === 'COMPLETED');
    const cancelled = ordersList.filter(o => o.status === 'CANCELLED');
    const totalRevenue = completed.reduce((sum, o) => sum + (o.total || 0), 0);

    setStats({
      totalOrders: ordersList.length,
      totalRevenue,
      avgOrderValue: completed.length > 0 ? totalRevenue / completed.length : 0,
      completedOrders: completed.length,
      cancelledOrders: cancelled.length,
    });
  };

  // Quick date filters
  const applyQuickFilter = (filter) => {
    const today = new Date();
    switch (filter) {
      case 'today':
        // Use shift-aware filtering for today
        setUseShiftFilter(true);
        setShiftDate(format(today, 'yyyy-MM-dd'));
        setDateFrom('');
        setDateTo('');
        break;
      case 'yesterday':
        // Use shift-aware filtering for yesterday
        const yesterday = subDays(today, 1);
        setUseShiftFilter(true);
        setShiftDate(format(yesterday, 'yyyy-MM-dd'));
        setDateFrom('');
        setDateTo('');
        break;
      case 'week':
        // Use date range for week (not shift-aware)
        setUseShiftFilter(false);
        setShiftDate('');
        setDateFrom(format(subDays(today, 7), 'yyyy-MM-dd'));
        setDateTo(format(today, 'yyyy-MM-dd'));
        break;
      case 'month':
        // Use date range for month (not shift-aware)
        setUseShiftFilter(false);
        setShiftDate('');
        setDateFrom(format(startOfMonth(today), 'yyyy-MM-dd'));
        setDateTo(format(endOfMonth(today), 'yyyy-MM-dd'));
        break;
      case 'all':
        setUseShiftFilter(false);
        setShiftDate('');
        setDateFrom('');
        setDateTo('');
        break;
    }
    setCurrentPage(1);
  };

  // Clear all filters
  const clearFilters = () => {
    setSelectedRestaurant('all');
    setSelectedStatus('all');
    setDateFrom('');
    setDateTo('');
    setShiftDate(format(new Date(), 'yyyy-MM-dd'));
    setUseShiftFilter(true);
    setSearchQuery('');
    setCurrentPage(1);
  };

  // View order details
  const handleViewOrder = (order) => {
    setSelectedOrder(order);
    setDetailsModalOpen(true);
  };

  // Export to CSV
  const handleExport = () => {
    const headers = [
      t('ordersHistory.orderNumber'),
      t('ordersHistory.date'),
      t('ordersHistory.customer'),
      t('ordersHistory.status'),
      t('ordersHistory.paymentStatus'),
      t('ordersHistory.total'),
    ];

    const rows = orders.map(order => [
      order.orderNumber || order.id,
      format(new Date(order.createdAt), 'yyyy-MM-dd HH:mm'),
      order.customer?.name || order.customerName || 'N/A',
      order.status,
      order.paymentStatus || 'N/A',
      order.total || 0,
    ]);

    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.map(cell => `"${cell}"`).join(',')),
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `orders_${format(new Date(), 'yyyy-MM-dd')}.csv`;
    link.click();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('ordersHistory.title', 'Orders History')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('ordersHistory.subtitle', 'View and analyze all orders')}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleExport}>
            <Download className="h-4 w-4 mr-2" />
            {t('common.export', 'Export')}
          </Button>
          <Button onClick={loadOrders} disabled={loading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
            {t('common.refresh', 'Refresh')}
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('ordersHistory.totalOrders', 'Total Orders')}
            </CardTitle>
            <ShoppingCart className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalOrders.toLocaleString()}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('ordersHistory.totalRevenue', 'Total Revenue')}
            </CardTitle>
            <DollarSign className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalRevenue.toLocaleString()}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('ordersHistory.avgOrderValue', 'Avg. Order Value')}
            </CardTitle>
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{Math.round(stats.avgOrderValue).toLocaleString()}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-green-600">
              {t('ordersHistory.completed', 'Completed')}
            </CardTitle>
            <Clock className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">{stats.completedOrders}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-red-600">
              {t('ordersHistory.cancelled', 'Cancelled')}
            </CardTitle>
            <X className="h-4 w-4 text-red-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">{stats.cancelledOrders}</div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Filter className="h-5 w-5" />
            {t('ordersHistory.filters', 'Filters')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
            {/* Search */}
            <div className="space-y-2">
              <Label>{t('ordersHistory.search', 'Search')}</Label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder={t('ordersHistory.searchPlaceholder', 'Order number...')}
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                    setCurrentPage(1);
                  }}
                  className="pl-9"
                />
              </div>
            </div>

            {/* Restaurant Filter */}
            <div className="space-y-2">
              <Label>{t('ordersHistory.restaurant', 'Restaurant')}</Label>
              <Select value={selectedRestaurant} onValueChange={(v) => { setSelectedRestaurant(v); setCurrentPage(1); }}>
                <SelectTrigger>
                  <SelectValue placeholder={t('common.all', 'All')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('common.all', 'All')}</SelectItem>
                  {restaurants.map(r => (
                    <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Status Filter */}
            <div className="space-y-2">
              <Label>{t('ordersHistory.status', 'Status')}</Label>
              <Select value={selectedStatus} onValueChange={(v) => { setSelectedStatus(v); setCurrentPage(1); }}>
                <SelectTrigger>
                  <SelectValue placeholder={t('common.all', 'All')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('common.all', 'All')}</SelectItem>
                  <SelectItem value="NEW">{t('orders.statuses.NEW', 'New')}</SelectItem>
                  <SelectItem value="ACCEPTED">{t('orders.statuses.ACCEPTED', 'Accepted')}</SelectItem>
                  <SelectItem value="PREPARING">{t('orders.statuses.PREPARING', 'Preparing')}</SelectItem>
                  <SelectItem value="READY">{t('orders.statuses.READY', 'Ready')}</SelectItem>
                  <SelectItem value="DELIVERED">{t('orders.statuses.DELIVERED', 'Delivered')}</SelectItem>
                  <SelectItem value="CANCELLED">{t('orders.statuses.CANCELLED', 'Cancelled')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Date From */}
            <div className="space-y-2">
              <Label>{t('ordersHistory.dateFrom', 'From')}</Label>
              <Input
                type="date"
                value={dateFrom}
                onChange={(e) => { setDateFrom(e.target.value); setCurrentPage(1); }}
              />
            </div>

            {/* Date To */}
            <div className="space-y-2">
              <Label>{t('ordersHistory.dateTo', 'To')}</Label>
              <Input
                type="date"
                value={dateTo}
                onChange={(e) => { setDateTo(e.target.value); setCurrentPage(1); }}
              />
            </div>
          </div>

          {/* Quick Date Filters */}
          <div className="flex flex-wrap gap-2 mt-4">
            <Button variant="outline" size="sm" onClick={() => applyQuickFilter('today')}>
              {t('ordersHistory.today', 'Today')}
            </Button>
            <Button variant="outline" size="sm" onClick={() => applyQuickFilter('yesterday')}>
              {t('ordersHistory.yesterday', 'Yesterday')}
            </Button>
            <Button variant="outline" size="sm" onClick={() => applyQuickFilter('week')}>
              {t('ordersHistory.lastWeek', 'Last 7 Days')}
            </Button>
            <Button variant="outline" size="sm" onClick={() => applyQuickFilter('month')}>
              {t('ordersHistory.thisMonth', 'This Month')}
            </Button>
            <Button variant="outline" size="sm" onClick={() => applyQuickFilter('all')}>
              {t('ordersHistory.allTime', 'All Time')}
            </Button>
            <Button variant="ghost" size="sm" onClick={clearFilters} className="text-red-600">
              <X className="h-4 w-4 mr-1" />
              {t('ordersHistory.clearFilters', 'Clear Filters')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Orders Table */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>{t('ordersHistory.ordersList', 'Orders List')}</span>
            <span className="text-sm font-normal text-muted-foreground">
              {t('ordersHistory.showing', 'Showing')} {orders.length} {t('ordersHistory.of', 'of')} {totalElements}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex justify-center items-center py-12">
              <RefreshCw className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : orders.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              {t('ordersHistory.noOrders', 'No orders found')}
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('ordersHistory.orderNumber', 'Order #')}</TableHead>
                    <TableHead>{t('ordersHistory.date', 'Date')}</TableHead>
                    <TableHead>{t('ordersHistory.table', 'Table')}</TableHead>
                    <TableHead>{t('ordersHistory.waiter', 'Waiter')}</TableHead>
                    <TableHead>{t('ordersHistory.type', 'Type')}</TableHead>
                    <TableHead>{t('ordersHistory.status', 'Status')}</TableHead>
                    <TableHead>{t('ordersHistory.payment', 'Payment')}</TableHead>
                    <TableHead className="text-right">{t('ordersHistory.total', 'Total')}</TableHead>
                    <TableHead className="text-center">{t('common.actions', 'Actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {orders.map((order) => (
                    <TableRow key={order.id}>
                      <TableCell className="font-medium">
                        {order.orderNumber || `#${order.id}`}
                      </TableCell>
                      <TableCell>
                        {order.createdAt ? format(new Date(order.createdAt), 'dd/MM/yyyy HH:mm') : '-'}
                      </TableCell>
                      <TableCell>
                        {order.diningTable?.tableNumber || order.tableIds || '-'}
                      </TableCell>
                      <TableCell>
                        {order.waiter?.username || order.waiter?.name || '-'}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">
                          {order.orderType ? t(`orders.orderTypes.${order.orderType}`, order.orderType) : t('common.na', 'N/A')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge className={orderStatusColors[order.status] || 'bg-gray-100'}>
                          {t(`orders.statuses.${order.status}`, order.status)}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge className={paymentStatusColors[order.paymentStatus] || 'bg-gray-100'}>
                          {order.paymentStatus || t('common.na', 'N/A')}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        {(order.total || 0).toLocaleString()}
                      </TableCell>
                      <TableCell className="text-center">
                        <div className="flex items-center justify-center gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleViewOrder(order)}
                            title={t('common.view', 'View')}
                          >
                            <Eye className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => PrintReceipt({ ...order, entryFee: 0 })}
                            title={t('common.print', 'Print')}
                          >
                            <Printer className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Pagination */}
              <div className="flex items-center justify-between mt-4">
                <div className="text-sm text-muted-foreground">
                  {t('ordersHistory.page', 'Page')} {currentPage} {t('ordersHistory.of', 'of')} {totalPages}
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                    disabled={currentPage === 1}
                  >
                    <ChevronLeft className="h-4 w-4" />
                    {t('common.previous', 'Previous')}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                    disabled={currentPage === totalPages}
                  >
                    {t('common.next', 'Next')}
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {/* Order Details Modal */}
      <Dialog open={detailsModalOpen} onOpenChange={setDetailsModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {t('ordersHistory.orderDetails', 'Order Details')} - {selectedOrder?.orderNumber || `#${selectedOrder?.id}`}
            </DialogTitle>
            <DialogDescription>
              {selectedOrder?.createdAt && format(new Date(selectedOrder.createdAt), 'dd/MM/yyyy HH:mm')}
            </DialogDescription>
          </DialogHeader>

          {selectedOrder && (
            <div className="space-y-6">
              {/* Order Info */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label className="text-muted-foreground">{t('ordersHistory.status', 'Status')}</Label>
                  <Badge className={`${orderStatusColors[selectedOrder.status]} mt-1`}>
                    {t(`orders.statuses.${selectedOrder.status}`, selectedOrder.status)}
                  </Badge>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('ordersHistory.payment', 'Payment')}</Label>
                  <div className="flex items-center gap-2 mt-1">
                    <Badge className={paymentStatusColors[selectedOrder.paymentStatus] || 'bg-gray-100'}>
                      {selectedOrder.paymentStatus || 'N/A'}
                    </Badge>
                    {selectedOrder.paymentMethod && (
                      <span className="text-sm">({selectedOrder.paymentMethod})</span>
                    )}
                  </div>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('ordersHistory.type', 'Type')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.orderType ? t(`orders.orderTypes.${selectedOrder.orderType}`, selectedOrder.orderType) : t('common.na', 'N/A')}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('ordersHistory.table', 'Table')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.diningTable?.tableNumber || selectedOrder.tableIds || 'N/A'}
                    {selectedOrder.guestCount && ` (${selectedOrder.guestCount} guests)`}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('ordersHistory.waiter', 'Waiter')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.waiter?.username || selectedOrder.waiter?.name || 'N/A'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('ordersHistory.customer', 'Customer')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.customer?.name || selectedOrder.customerName || 'N/A'}
                  </p>
                </div>
              </div>

              {/* Items */}
              <div>
                <Label className="text-muted-foreground mb-2 block">
                  {t('ordersHistory.items', 'Items')}
                </Label>
                <div className="bg-gray-50 rounded-lg p-4 space-y-2">
                  {selectedOrder.items?.map((item, idx) => (
                    <div key={idx} className="flex justify-between">
                      <div>
                        <span className="font-medium">{item.quantity}x</span> {item.productName}
                        {item.variantName && <span className="text-muted-foreground"> ({item.variantName})</span>}
                      </div>
                      <span className="font-medium">
                        {(item.totalPrice || item.price * item.quantity).toLocaleString()}
                      </span>
                    </div>
                  )) || (
                    <p className="text-muted-foreground">{t('ordersHistory.noItems', 'No items')}</p>
                  )}
                </div>
              </div>

              {/* Totals */}
              <div className="bg-gray-50 rounded-lg p-4 space-y-2">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t('orders.subtotal', 'Subtotal')}</span>
                  <span>{(selectedOrder.subtotal || 0).toLocaleString()}</span>
                </div>
                {selectedOrder.tax > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">{t('orders.tax', 'Tax')}</span>
                    <span>{selectedOrder.tax.toLocaleString()}</span>
                  </div>
                )}
                {selectedOrder.serviceFee > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">{t('orders.serviceFee', 'Service Fee')}</span>
                    <span>{selectedOrder.serviceFee.toLocaleString()}</span>
                  </div>
                )}
                {selectedOrder.deliveryFee > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">{t('orders.deliveryFee', 'Delivery Fee')}</span>
                    <span>{selectedOrder.deliveryFee.toLocaleString()}</span>
                  </div>
                )}
                <div className="flex justify-between font-bold text-lg pt-2 border-t">
                  <span>{t('orders.total', 'Total')}</span>
                  <span>{(selectedOrder.total || 0).toLocaleString()}</span>
                </div>
              </div>

              {/* Notes */}
              {selectedOrder.orderNotes && (
                <div>
                  <Label className="text-muted-foreground">{t('orders.notes', 'Notes')}</Label>
                  <p className="mt-1 bg-yellow-50 p-3 rounded-lg">{selectedOrder.orderNotes}</p>
                </div>
              )}

              {/* Print Button */}
              <div className="flex justify-end pt-4 border-t">
                <Button
                  onClick={() => PrintReceipt({ ...selectedOrder, entryFee: 0 })}
                  className="flex items-center gap-2"
                >
                  <Printer className="h-4 w-4" />
                  {t('common.printReceipt', 'Print Receipt')}
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
