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
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '../components/ui/tabs';
import {
  Globe,
  ShoppingCart,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Eye,
  Clock,
  QrCode,
  Smartphone,
  Phone,
  Send,
  MessageCircle,
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

const orderSourceConfig = {
  TELEGRAM_BOT: { label: 'Telegram', icon: Send, color: 'bg-blue-100 text-blue-800' },
  WEBSITE: { label: 'Website', icon: Globe, color: 'bg-green-100 text-green-800' },
  MOBILE_APP: { label: 'Mobile App', icon: Smartphone, color: 'bg-purple-100 text-purple-800' },
  PHONE_CALL: { label: 'Phone', icon: Phone, color: 'bg-orange-100 text-orange-800' },
  OTHER: { label: 'Other', icon: MessageCircle, color: 'bg-gray-100 text-gray-800' },
  SELF_SERVICE: { label: 'QR Code', icon: QrCode, color: 'bg-cyan-100 text-cyan-800' },
};

export default function SelfServiceOrders() {
  const { t } = useTranslation();

  // Data state
  const [selfServiceOrders, setSelfServiceOrders] = useState([]);
  const [externalOrders, setExternalOrders] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('all');

  // Filter state
  const [selectedRestaurant, setSelectedRestaurant] = useState('1');
  const [selectedSource, setSelectedSource] = useState('all');

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

      // Load self-service orders
      const selfServiceResponse = await orderAPI.getSelfServiceOrders(params);
      const selfServiceData = selfServiceResponse.data.data;
      if (selfServiceData?.content) {
        setSelfServiceOrders(selfServiceData.content);
      } else {
        setSelfServiceOrders(Array.isArray(selfServiceData) ? selfServiceData : []);
      }

      // Load external orders with optional source filter
      const externalParams = { ...params };
      if (selectedSource !== 'all') {
        externalParams.source = selectedSource;
      }
      const externalResponse = await orderAPI.getExternalOrders(externalParams);
      const externalData = externalResponse.data.data;
      if (externalData?.content) {
        setExternalOrders(externalData.content);
        setTotalPages(externalData.totalPages || 1);
        setTotalElements(externalData.totalElements || 0);
      } else {
        setExternalOrders(Array.isArray(externalData) ? externalData : []);
        setTotalPages(1);
        setTotalElements(0);
      }
    } catch (error) {
      console.error('Failed to load orders:', error);
      setSelfServiceOrders([]);
      setExternalOrders([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, selectedRestaurant, selectedSource]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  // Get displayed orders based on active tab
  const getDisplayedOrders = () => {
    if (activeTab === 'self-service') {
      return selfServiceOrders.map(so => ({
        ...so.order,
        orderSource: 'SELF_SERVICE',
        selfServiceData: so,
      }));
    } else if (activeTab === 'external') {
      return externalOrders;
    } else {
      // Combine both
      const ssOrders = selfServiceOrders.map(so => ({
        ...so.order,
        orderSource: 'SELF_SERVICE',
        selfServiceData: so,
      }));
      return [...ssOrders, ...externalOrders].sort((a, b) =>
        new Date(b.createdAt) - new Date(a.createdAt)
      );
    }
  };

  const displayedOrders = getDisplayedOrders();

  // View order details
  const handleViewOrder = (order) => {
    setSelectedOrder(order);
    setDetailsModalOpen(true);
  };

  // Get source icon and label
  const getSourceDisplay = (source) => {
    const config = orderSourceConfig[source] || orderSourceConfig.OTHER;
    const Icon = config.icon;
    return (
      <Badge className={config.color}>
        <Icon className="h-3 w-3 mr-1" />
        {config.label}
      </Badge>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-3">
            <Globe className="h-8 w-8" />
            {t('externalOrders.title', 'Online & External Orders')}
          </h1>
          <p className="text-muted-foreground mt-1">
            {t('externalOrders.subtitle', 'Orders from QR code, website, mobile app, Telegram, phone calls')}
          </p>
        </div>
        <Button onClick={loadOrders} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          {t('common.refresh', 'Refresh')}
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('externalOrders.qrCode', 'QR Code')}
            </CardTitle>
            <QrCode className="h-4 w-4 text-cyan-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-cyan-600">{selfServiceOrders.length}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('externalOrders.website', 'Website')}
            </CardTitle>
            <Globe className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {externalOrders.filter(o => o.orderSource === 'WEBSITE').length}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('externalOrders.mobileApp', 'Mobile App')}
            </CardTitle>
            <Smartphone className="h-4 w-4 text-purple-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-purple-600">
              {externalOrders.filter(o => o.orderSource === 'MOBILE_APP').length}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('externalOrders.telegram', 'Telegram')}
            </CardTitle>
            <Send className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {externalOrders.filter(o => o.orderSource === 'TELEGRAM_BOT').length}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('externalOrders.phoneCall', 'Phone')}
            </CardTitle>
            <Phone className="h-4 w-4 text-orange-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-orange-600">
              {externalOrders.filter(o => o.orderSource === 'PHONE_CALL').length}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-yellow-600">
              {t('externalOrders.pending', 'Pending')}
            </CardTitle>
            <Clock className="h-4 w-4 text-yellow-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">
              {displayedOrders.filter(o =>
                o.status === 'NEW' || o.status === 'PENDING' || o.status === 'PREPARING'
              ).length}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Globe className="h-5 w-5" />
            {t('externalOrders.filters', 'Filters')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-4">
            {/* Restaurant Filter */}
            <div className="space-y-2 w-64">
              <Label>{t('externalOrders.restaurant', 'Restaurant')}</Label>
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

            {/* Source Filter */}
            <div className="space-y-2 w-64">
              <Label>{t('externalOrders.source', 'Order Source')}</Label>
              <Select value={selectedSource} onValueChange={(v) => { setSelectedSource(v); setCurrentPage(1); }}>
                <SelectTrigger>
                  <SelectValue placeholder={t('common.all', 'All')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('common.all', 'All Sources')}</SelectItem>
                  <SelectItem value="TELEGRAM_BOT">Telegram Bot</SelectItem>
                  <SelectItem value="WEBSITE">Website</SelectItem>
                  <SelectItem value="MOBILE_APP">Mobile App</SelectItem>
                  <SelectItem value="PHONE_CALL">Phone Call</SelectItem>
                  <SelectItem value="OTHER">Other</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Orders Table with Tabs */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>{t('externalOrders.ordersList', 'External Orders')}</span>
            <span className="text-sm font-normal text-muted-foreground">
              {t('externalOrders.showing', 'Showing')} {displayedOrders.length} {t('externalOrders.orders', 'orders')}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Tabs value={activeTab} onValueChange={setActiveTab} className="mb-4">
            <TabsList>
              <TabsTrigger value="all">
                <ShoppingCart className="h-4 w-4 mr-2" />
                {t('externalOrders.all', 'All')}
              </TabsTrigger>
              <TabsTrigger value="self-service">
                <QrCode className="h-4 w-4 mr-2" />
                {t('externalOrders.qrOrders', 'QR Code')}
              </TabsTrigger>
              <TabsTrigger value="external">
                <Globe className="h-4 w-4 mr-2" />
                {t('externalOrders.onlineOrders', 'Online')}
              </TabsTrigger>
            </TabsList>
          </Tabs>

          {loading ? (
            <div className="flex justify-center items-center py-12">
              <RefreshCw className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : displayedOrders.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <Globe className="h-12 w-12 mx-auto mb-4 opacity-30" />
              <p>{t('externalOrders.noOrders', 'No external orders found')}</p>
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('externalOrders.orderNumber', 'Order #')}</TableHead>
                    <TableHead>{t('externalOrders.date', 'Date')}</TableHead>
                    <TableHead>{t('externalOrders.source', 'Source')}</TableHead>
                    <TableHead>{t('externalOrders.customer', 'Customer')}</TableHead>
                    <TableHead>{t('externalOrders.type', 'Type')}</TableHead>
                    <TableHead>{t('externalOrders.status', 'Status')}</TableHead>
                    <TableHead className="text-right">{t('externalOrders.total', 'Total')}</TableHead>
                    <TableHead className="text-center">{t('common.actions', 'Actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {displayedOrders.map((order) => (
                    <TableRow key={order.id}>
                      <TableCell className="font-medium">
                        {order.orderNumber || `#${order.id}`}
                      </TableCell>
                      <TableCell>
                        {order.createdAt ? format(new Date(order.createdAt), 'dd/MM/yyyy HH:mm') : '-'}
                      </TableCell>
                      <TableCell>
                        {getSourceDisplay(order.orderSource)}
                      </TableCell>
                      <TableCell>
                        {order.customer?.firstName || order.customer?.phone || order.selfServiceData?.customerName || '-'}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">
                          {order.orderType ? t(`orders.orderTypes.${order.orderType}`, order.orderType) : '-'}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge className={orderStatusColors[order.status] || 'bg-gray-100'}>
                          {t(`orders.statuses.${order.status}`, order.status)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        {(order.total || 0).toLocaleString()}
                      </TableCell>
                      <TableCell className="text-center">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleViewOrder(order)}
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
                  {t('externalOrders.page', 'Page')} {currentPage} {t('externalOrders.of', 'of')} {totalPages}
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
              <Globe className="h-5 w-5" />
              {t('externalOrders.orderDetails', 'Order Details')}
            </DialogTitle>
            <DialogDescription>
              {selectedOrder?.orderNumber || `#${selectedOrder?.id}`}
            </DialogDescription>
          </DialogHeader>

          {selectedOrder && (
            <div className="space-y-6">
              {/* Order Info */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label className="text-muted-foreground">{t('externalOrders.source', 'Source')}</Label>
                  <div className="mt-1">
                    {getSourceDisplay(selectedOrder.orderSource)}
                  </div>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('externalOrders.status', 'Status')}</Label>
                  <Badge className={`${orderStatusColors[selectedOrder.status]} mt-1`}>
                    {t(`orders.statuses.${selectedOrder.status}`, selectedOrder.status)}
                  </Badge>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('externalOrders.orderType', 'Order Type')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.orderType ? t(`orders.orderTypes.${selectedOrder.orderType}`, selectedOrder.orderType) : '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('externalOrders.customer', 'Customer')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.customer?.firstName || selectedOrder.selfServiceData?.customerName || '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('externalOrders.phone', 'Phone')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.customer?.phone || selectedOrder.selfServiceData?.customerPhone || '-'}
                  </p>
                </div>
                <div>
                  <Label className="text-muted-foreground">{t('externalOrders.createdAt', 'Created At')}</Label>
                  <p className="mt-1 font-medium">
                    {selectedOrder.createdAt ? format(new Date(selectedOrder.createdAt), 'dd/MM/yyyy HH:mm') : '-'}
                  </p>
                </div>
              </div>

              {/* Order Items */}
              <div>
                <Label className="text-muted-foreground mb-2 block">
                  {t('externalOrders.items', 'Items')}
                </Label>
                <div className="bg-gray-50 rounded-lg p-4 space-y-2">
                  {selectedOrder.items?.map((item, idx) => (
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
                    <p className="text-muted-foreground">{t('externalOrders.noItems', 'No items')}</p>
                  )}
                </div>
              </div>

              {/* Totals */}
              <div className="bg-gray-50 rounded-lg p-4 space-y-2">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t('orders.subtotal', 'Subtotal')}</span>
                  <span>{(selectedOrder.subtotal || 0).toLocaleString()}</span>
                </div>
                {selectedOrder.deliveryFee > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">{t('orders.deliveryFee', 'Delivery Fee')}</span>
                    <span>{selectedOrder.deliveryFee.toLocaleString()}</span>
                  </div>
                )}
                {selectedOrder.serviceFee > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">{t('orders.serviceFee', 'Service Fee')}</span>
                    <span>{selectedOrder.serviceFee.toLocaleString()}</span>
                  </div>
                )}
                <div className="flex justify-between font-bold text-lg pt-2 border-t">
                  <span>{t('orders.total', 'Total')}</span>
                  <span>{(selectedOrder.total || 0).toLocaleString()}</span>
                </div>
              </div>

              {/* Notes */}
              {selectedOrder.customerNotes && (
                <div>
                  <Label className="text-muted-foreground">{t('externalOrders.notes', 'Customer Notes')}</Label>
                  <p className="mt-1 bg-yellow-50 p-3 rounded-lg">{selectedOrder.customerNotes}</p>
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
