import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
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
  QrCode,
  ShoppingCart,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Eye,
  Clock,
  Utensils,
  ShoppingBag,
} from 'lucide-react';
import { format } from 'date-fns';

const orderStatusColors = {
  NEW: 'bg-blue-100 text-blue-800',
  PENDING: 'bg-blue-100 text-blue-800',
  ACCEPTED: 'bg-green-100 text-green-800',
  PREPARING: 'bg-yellow-100 text-yellow-800',
  READY: 'bg-purple-100 text-purple-800',
  DELIVERED: 'bg-green-100 text-green-800',
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
};

const orderTypeColors = {
  DINE_IN: 'bg-blue-100 text-blue-800',
  TAKEAWAY: 'bg-orange-100 text-orange-800',
};

export default function SelfServiceOrders() {
  const { t } = useTranslation();

  // Data state
  const [orders, setOrders] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalOrders: 0,
    dineInOrders: 0,
    takeawayOrders: 0,
    pendingOrders: 0,
  });

  // Filter state
  const [selectedRestaurant, setSelectedRestaurant] = useState('1');

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

      const response = await orderAPI.getSelfServiceOrders(params);
      const data = response.data.data;

      if (data?.content) {
        setOrders(data.content);
        setTotalPages(data.totalPages || 1);
        setTotalElements(data.totalElements || 0);
        calculateStats(data.content);
      } else if (Array.isArray(data)) {
        setOrders(data);
        setTotalPages(1);
        setTotalElements(data.length);
        calculateStats(data);
      } else {
        setOrders([]);
        setTotalPages(1);
        setTotalElements(0);
      }
    } catch (error) {
      console.error('Failed to load self-service orders:', error);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, selectedRestaurant]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  // Calculate statistics
  const calculateStats = (ordersList) => {
    const dineIn = ordersList.filter(o => o.orderType === 'DINE_IN').length;
    const takeaway = ordersList.filter(o => o.orderType === 'TAKEAWAY').length;
    const pending = ordersList.filter(o =>
      o.order?.status === 'NEW' || o.order?.status === 'PENDING' || o.order?.status === 'PREPARING'
    ).length;

    setStats({
      totalOrders: ordersList.length,
      dineInOrders: dineIn,
      takeawayOrders: takeaway,
      pendingOrders: pending,
    });
  };

  // View order details
  const handleViewOrder = (selfServiceOrder) => {
    setSelectedOrder(selfServiceOrder);
    setDetailsModalOpen(true);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-3">
            <QrCode className="h-8 w-8" />
            {t('selfServiceOrders.title', 'Self-Service Orders')}
          </h1>
          <p className="text-muted-foreground mt-1">
            {t('selfServiceOrders.subtitle', 'Orders placed via QR code scanning')}
          </p>
        </div>
        <Button onClick={loadOrders} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          {t('common.refresh', 'Refresh')}
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('selfServiceOrders.totalOrders', 'Total Orders')}
            </CardTitle>
            <ShoppingCart className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalOrders}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('selfServiceOrders.dineIn', 'Dine-In')}
            </CardTitle>
            <Utensils className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">{stats.dineInOrders}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('selfServiceOrders.takeaway', 'Takeaway')}
            </CardTitle>
            <ShoppingBag className="h-4 w-4 text-orange-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-orange-600">{stats.takeawayOrders}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-yellow-600">
              {t('selfServiceOrders.pending', 'Pending')}
            </CardTitle>
            <Clock className="h-4 w-4 text-yellow-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">{stats.pendingOrders}</div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <QrCode className="h-5 w-5" />
            {t('selfServiceOrders.filters', 'Filters')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex gap-4">
            {/* Restaurant Filter */}
            <div className="space-y-2 w-64">
              <Label>{t('selfServiceOrders.restaurant', 'Restaurant')}</Label>
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
          </div>
        </CardContent>
      </Card>

      {/* Orders Table */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>{t('selfServiceOrders.ordersList', 'Self-Service Orders')}</span>
            <span className="text-sm font-normal text-muted-foreground">
              {t('selfServiceOrders.showing', 'Showing')} {orders.length} {t('selfServiceOrders.of', 'of')} {totalElements}
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
              <QrCode className="h-12 w-12 mx-auto mb-4 opacity-30" />
              <p>{t('selfServiceOrders.noOrders', 'No self-service orders found')}</p>
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('selfServiceOrders.orderNumber', 'Order #')}</TableHead>
                    <TableHead>{t('selfServiceOrders.date', 'Date')}</TableHead>
                    <TableHead>{t('selfServiceOrders.customer', 'Customer')}</TableHead>
                    <TableHead>{t('selfServiceOrders.table', 'Table')}</TableHead>
                    <TableHead>{t('selfServiceOrders.type', 'Type')}</TableHead>
                    <TableHead>{t('selfServiceOrders.status', 'Status')}</TableHead>
                    <TableHead className="text-right">{t('selfServiceOrders.total', 'Total')}</TableHead>
                    <TableHead className="text-center">{t('common.actions', 'Actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {orders.map((selfServiceOrder) => (
                    <TableRow key={selfServiceOrder.id}>
                      <TableCell className="font-medium">
                        {selfServiceOrder.order?.orderNumber || `#${selfServiceOrder.order?.id}`}
                      </TableCell>
                      <TableCell>
                        {selfServiceOrder.createdAt ? format(new Date(selfServiceOrder.createdAt), 'dd/MM/yyyy HH:mm') : '-'}
                      </TableCell>
                      <TableCell>
                        {selfServiceOrder.customerName || selfServiceOrder.customerPhone || '-'}
                      </TableCell>
                      <TableCell>
                        {selfServiceOrder.order?.diningTable?.tableNumber || '-'}
                      </TableCell>
                      <TableCell>
                        <Badge className={orderTypeColors[selfServiceOrder.orderType] || 'bg-gray-100'}>
                          {selfServiceOrder.orderType === 'DINE_IN'
                            ? t('orders.orderTypes.DINE_IN', 'Dine In')
                            : t('orders.orderTypes.TAKEAWAY', 'Takeaway')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge className={orderStatusColors[selfServiceOrder.order?.status] || 'bg-gray-100'}>
                          {t(`orders.statuses.${selfServiceOrder.order?.status}`, selfServiceOrder.order?.status)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        {(selfServiceOrder.order?.total || 0).toLocaleString()}
                      </TableCell>
                      <TableCell className="text-center">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleViewOrder(selfServiceOrder)}
                          title={t('common.view', 'View')}
                        >
                          <Eye className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Pagination */}
              <div className="flex items-center justify-between mt-4">
                <div className="text-sm text-muted-foreground">
                  {t('selfServiceOrders.page', 'Page')} {currentPage} {t('selfServiceOrders.of', 'of')} {totalPages}
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
            <DialogTitle className="flex items-center gap-2">
              <QrCode className="h-5 w-5" />
              {t('selfServiceOrders.orderDetails', 'Self-Service Order Details')}
            </DialogTitle>
            <DialogDescription>
              {selectedOrder?.order?.orderNumber || `#${selectedOrder?.order?.id}`}
            </DialogDescription>
          </DialogHeader>

          {selectedOrder && (
            <div className="space-y-6">
              {/* Order Info */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.orderType', 'Order Type')}</Label>
                  <Badge className={`${orderTypeColors[selectedOrder.orderType]} mt-1`}>
                    {selectedOrder.orderType === 'DINE_IN'
                      ? t('orders.orderTypes.DINE_IN', 'Dine In')
                      : t('orders.orderTypes.TAKEAWAY', 'Takeaway')}
                  </Badge>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.status', 'Status')}</Label>
                  <Badge className={`${orderStatusColors[selectedOrder.order?.status]} mt-1`}>
                    {t(`orders.statuses.${selectedOrder.order?.status}`, selectedOrder.order?.status)}
                  </Badge>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.customer', 'Customer')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.customerName || '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.phone', 'Phone')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.customerPhone || '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.table', 'Table')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.order?.diningTable?.tableNumber || '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.createdAt', 'Created At')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.createdAt ? format(new Date(selectedOrder.createdAt), 'dd/MM/yyyy HH:mm') : '-'}
                  </p>
                </div>
              </div>

              {/* Timing Info */}
              <div className="grid grid-cols-2 gap-4 bg-gray-50 rounded-lg p-4">
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.estimatedReady', 'Estimated Ready')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.estimatedReadyTime
                      ? format(new Date(selectedOrder.estimatedReadyTime), 'HH:mm')
                      : '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.actualReady', 'Actual Ready')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.actualReadyTime
                      ? format(new Date(selectedOrder.actualReadyTime), 'HH:mm')
                      : '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.pickedUp', 'Picked Up')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.pickedUpAt
                      ? format(new Date(selectedOrder.pickedUpAt), 'HH:mm')
                      : '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.notified', 'Notified')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.notifiedAt
                      ? format(new Date(selectedOrder.notifiedAt), 'HH:mm')
                      : '-'}
                  </p>
                </div>
              </div>

              {/* Special Instructions */}
              {selectedOrder.specialInstructions && (
                <div>
                  <Label className="text-muted-foreground">{t('selfServiceOrders.specialInstructions', 'Special Instructions')}</Label>
                  <p className="mt-1 bg-yellow-50 p-3 rounded-lg">{selectedOrder.specialInstructions}</p>
                </div>
              )}

              {/* Order Items */}
              <div>
                <Label className="text-muted-foreground mb-2 block">
                  {t('selfServiceOrders.items', 'Items')}
                </Label>
                <div className="bg-gray-50 rounded-lg p-4 space-y-2">
                  {selectedOrder.order?.items?.map((item, idx) => (
                    <div key={idx} className="flex justify-between">
                      <div>
                        <span className="font-medium">{item.quantity}x</span> {item.productName}
                        {item.variantName && <span className="text-muted-foreground"> ({item.variantName})</span>}
                      </div>
                      <span className="font-medium">
                        {(item.totalPrice || item.unitPrice * item.quantity).toLocaleString()}
                      </span>
                    </div>
                  )) || (
                    <p className="text-muted-foreground">{t('selfServiceOrders.noItems', 'No items')}</p>
                  )}
                </div>
              </div>

              {/* Totals */}
              <div className="bg-gray-50 rounded-lg p-4 space-y-2">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t('orders.subtotal', 'Subtotal')}</span>
                  <span>{(selectedOrder.order?.subtotal || 0).toLocaleString()}</span>
                </div>
                {selectedOrder.order?.serviceFee > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">{t('orders.serviceFee', 'Service Fee')}</span>
                    <span>{selectedOrder.order.serviceFee.toLocaleString()}</span>
                  </div>
                )}
                <div className="flex justify-between font-bold text-lg pt-2 border-t">
                  <span>{t('orders.total', 'Total')}</span>
                  <span>{(selectedOrder.order?.total || 0).toLocaleString()}</span>
                </div>
              </div>

              {/* Feedback */}
              {selectedOrder.feedbackRating && (
                <div className="bg-green-50 rounded-lg p-4">
                  <Label className="text-muted-foreground">{t('selfServiceOrders.feedback', 'Customer Feedback')}</Label>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-2xl">{'⭐'.repeat(selectedOrder.feedbackRating)}</span>
                    <span className="text-muted-foreground">({selectedOrder.feedbackRating}/5)</span>
                  </div>
                  {selectedOrder.feedbackComment && (
                    <p className="mt-2 text-sm">{selectedOrder.feedbackComment}</p>
                  )}
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
