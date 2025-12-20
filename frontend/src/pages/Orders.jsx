import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI, restaurantAPI, menuAPI, tablesAPI } from '../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Textarea } from '../components/ui/textarea';
import {
  Plus,
  Search,
  Filter,
  X,
  Truck,
  ShoppingBag,
  Utensils,
  Printer
} from 'lucide-react';
import { format } from 'date-fns';
import PrintReceipt from '../components/PrintReceipt';

const statusColors = {
  NEW: 'bg-blue-100 text-blue-800',
  ACCEPTED: 'bg-green-100 text-green-800',
  PREPARING: 'bg-yellow-100 text-yellow-800',
  READY: 'bg-purple-100 text-purple-800',
  COURIER_ASSIGNED: 'bg-indigo-100 text-indigo-800',
  ON_DELIVERY: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
};

export default function Orders() {
  const { t } = useTranslation();
  const [orders, setOrders] = useState([]);
  const [filteredOrders, setFilteredOrders] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [products, setProducts] = useState([]);
  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);

  // Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('all');
  const [selectedRestaurant, setSelectedRestaurant] = useState('all');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Create order form
  const [formData, setFormData] = useState({
    restaurantId: '',
    orderType: 'DELIVERY', // DELIVERY, TAKEAWAY, DINE_IN
    diningTableId: '',
    customerFirstName: '',
    customerLastName: '',
    customerPhone: '',
    customerEmail: '',
    deliveryAddress: '',
    deliveryCity: '',
    deliveryState: '',
    deliveryZipCode: '',
    customerNotes: '',
    paymentMethod: 'CARD',
    items: []
  });

  const [selectedProduct, setSelectedProduct] = useState('');
  const [selectedQuantity, setSelectedQuantity] = useState(1);

  useEffect(() => {
    loadOrders();
    loadRestaurants();
  }, []);

  useEffect(() => {
    filterOrders();
  }, [orders, searchTerm, selectedStatus, selectedRestaurant, startDate, endDate]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantList = response.data.data.content || [];
      setRestaurants(restaurantList);
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadProducts = async (restaurantId) => {
    try {
      const response = await menuAPI.getProductsByRestaurant(restaurantId);
      const productsData = response.data.data || [];
      setProducts(productsData);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const loadTables = async (restaurantId) => {
    try {
      console.log('Loading tables for restaurant ID:', restaurantId);
      const response = await tablesAPI.getAvailable(restaurantId);
      console.log('Tables API response:', response);
      console.log('Tables data:', response?.data);
      console.log('Tables data.data:', response?.data?.data);

      const tablesData = response?.data?.data?.content || response?.data?.data || [];
      console.log('Extracted tables data:', tablesData);
      console.log('Is array?', Array.isArray(tablesData));

      setTables(Array.isArray(tablesData) ? tablesData : []);
    } catch (error) {
      console.error('Failed to load tables:', error);
      console.error('Error response:', error.response);
      console.error('Error message:', error.message);
      setTables([]);
    }
  };

  const loadOrders = async () => {
    try {
      const response = await orderAPI.getAll({ page: 0, size: 100, sort: 'createdAt,desc' });
      console.log('Orders response:', response);
      console.log('Orders data:', response?.data);
      console.log('Orders data.data:', response?.data?.data);

      // Handle different response structures
      const ordersData = response?.data?.data?.content ||
                        response?.data?.data ||
                        response?.data ||
                        [];

      console.log('Extracted orders:', ordersData);
      setOrders(Array.isArray(ordersData) ? ordersData : []);
    } catch (error) {
      console.error('Failed to load orders:', error);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  };

  const filterOrders = () => {
    let filtered = [...orders];

    // Filter by search term (order number)
    if (searchTerm) {
      filtered = filtered.filter(order =>
        order.orderNumber?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    // Filter by status
    if (selectedStatus !== 'all') {
      filtered = filtered.filter(order => order.status === selectedStatus);
    }

    // Filter by restaurant
    if (selectedRestaurant !== 'all') {
      filtered = filtered.filter(order => order.restaurant?.id === parseInt(selectedRestaurant));
    }

    // Filter by date range
    if (startDate) {
      filtered = filtered.filter(order =>
        new Date(order.createdAt) >= new Date(startDate)
      );
    }
    if (endDate) {
      filtered = filtered.filter(order =>
        new Date(order.createdAt) <= new Date(endDate + 'T23:59:59')
      );
    }

    setFilteredOrders(filtered);
  };

  const resetFilters = () => {
    setSearchTerm('');
    setSelectedStatus('all');
    setSelectedRestaurant('all');
    setStartDate('');
    setEndDate('');
  };

  const updateOrderStatus = async (orderId, newStatus) => {
    try {
      await orderAPI.updateStatus(orderId, newStatus, `Status updated to ${newStatus}`);
      loadOrders();
    } catch (error) {
      console.error('Failed to update order status:', error);
      alert(t('messages.error'));
    }
  };

  const handleCreateOrder = async (e) => {
    e.preventDefault();

    if (formData.items.length === 0) {
      alert(t('orders.messages.addAtLeastOneItem'));
      return;
    }

    try {
      const orderData = {
        restaurantId: parseInt(formData.restaurantId),
        orderSource: 'ADMIN_PANEL',
        orderType: formData.orderType,
        diningTableId: formData.orderType === 'DINE_IN' && formData.diningTableId
          ? parseInt(formData.diningTableId)
          : null,
        customerInfo: {
          firstName: formData.customerFirstName,
          lastName: formData.customerLastName,
          phone: formData.customerPhone,
          email: formData.customerEmail
        },
        items: formData.items.map(item => ({
          productId: item.productId,
          quantity: item.quantity,
          specialInstructions: item.specialInstructions || null
        })),
        deliveryInfo: formData.orderType === 'DELIVERY' ? {
          address: formData.deliveryAddress,
          city: formData.deliveryCity,
          state: formData.deliveryState || null,
          zipCode: formData.deliveryZipCode || null,
          deliveryInstructions: null
        } : null,
        customerNotes: formData.customerNotes,
        paymentMethod: formData.paymentMethod,
        scheduledFor: null
      };

      await orderAPI.create(orderData);
      setCreateModalOpen(false);
      resetForm();
      loadOrders();
    } catch (error) {
      console.error('Failed to create order:', error);
      alert(t('orders.messages.createOrderError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleAddItem = () => {
    if (!selectedProduct || selectedQuantity <= 0) return;

    const product = products.find(p => p.id === parseInt(selectedProduct));
    if (!product) return;

    const newItem = {
      productId: product.id,
      productName: product.name,
      quantity: parseInt(selectedQuantity),
      unitPrice: product.price,
      totalPrice: product.price * parseInt(selectedQuantity)
    };

    setFormData({
      ...formData,
      items: [...formData.items, newItem]
    });

    setSelectedProduct('');
    setSelectedQuantity(1);
  };

  const handleRemoveItem = (index) => {
    setFormData({
      ...formData,
      items: formData.items.filter((_, i) => i !== index)
    });
  };

  const resetForm = () => {
    setFormData({
      restaurantId: '',
      orderType: 'DELIVERY',
      diningTableId: '',
      customerFirstName: '',
      customerLastName: '',
      customerPhone: '',
      customerEmail: '',
      deliveryAddress: '',
      deliveryCity: '',
      deliveryState: '',
      deliveryZipCode: '',
      customerNotes: '',
      paymentMethod: 'CARD',
      items: []
    });
    setSelectedProduct('');
    setSelectedQuantity(1);
    setTables([]);
  };

  const calculateTotal = () => {
    return formData.items.reduce((sum, item) => sum + item.totalPrice, 0);
  };

  const nextStatusMap = {
    NEW: 'ACCEPTED',
    ACCEPTED: 'PREPARING',
    PREPARING: 'READY',
    READY: 'COURIER_ASSIGNED',
    COURIER_ASSIGNED: 'ON_DELIVERY',
    ON_DELIVERY: 'DELIVERED',
  };

  if (loading) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('orders.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('orders.allOrders')}
          </p>
        </div>
        <Button onClick={() => setCreateModalOpen(true)}>
          <Plus className="h-4 w-4 mr-2" />
          Create Order
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <div className="flex justify-between items-center">
            <CardTitle className="text-lg">
              <Filter className="h-5 w-5 inline mr-2" />
              Filters
            </CardTitle>
            <Button variant="outline" size="sm" onClick={resetFilters}>
              <X className="h-4 w-4 mr-1" />
              Reset
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t("common.placeholders.searchOrderNumber")}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>

            <Select value={selectedStatus} onValueChange={setSelectedStatus}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.placeholders.allStatuses")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("orders.status.all")}</SelectItem>
                <SelectItem value="NEW">{t("orders.status.new")}</SelectItem>
                <SelectItem value="ACCEPTED">{t("orders.status.accepted")}</SelectItem>
                <SelectItem value="PREPARING">{t("orders.status.preparing")}</SelectItem>
                <SelectItem value="READY">{t("orders.status.ready")}</SelectItem>
                <SelectItem value="COURIER_ASSIGNED">Courier Assigned</SelectItem>
                <SelectItem value="ON_DELIVERY">On Delivery</SelectItem>
                <SelectItem value="DELIVERED">{t("orders.status.delivered")}</SelectItem>
                <SelectItem value="CANCELLED">{t("orders.status.cancelled")}</SelectItem>
              </SelectContent>
            </Select>

            <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.placeholders.allRestaurants")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Restaurants</SelectItem>
                {restaurants.map((restaurant) => (
                  <SelectItem key={restaurant.id} value={restaurant.id.toString()}>
                    {restaurant.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Input
              type="date"
              placeholder={t("common.placeholders.startDate")}
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />

            <Input
              type="date"
              placeholder={t("common.placeholders.endDate")}
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      {/* Orders List */}
      <div className="space-y-4">
        {filteredOrders.length === 0 ? (
          <Card>
            <CardContent className="pt-6">
              <p className="text-center text-muted-foreground">
                {t('common.noData')}
              </p>
            </CardContent>
          </Card>
        ) : (
          filteredOrders.map((order) => (
            <Card key={order.id}>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <CardTitle className="text-lg">
                        {t('orders.orderNumber')}: #{order.orderNumber}
                      </CardTitle>
                      {/* Order Type Icon and Badge */}
                      {order.orderType === 'DELIVERY' && (
                        <Badge variant="outline" className="gap-1">
                          <Truck className="h-3 w-3" />
                          {t('orders.types.delivery') || 'Delivery'}
                        </Badge>
                      )}
                      {order.orderType === 'TAKEAWAY' && (
                        <Badge variant="outline" className="gap-1">
                          <ShoppingBag className="h-3 w-3" />
                          {t('orders.types.takeaway') || 'Takeaway'}
                        </Badge>
                      )}
                      {order.orderType === 'DINE_IN' && (
                        <Badge variant="outline" className="gap-1">
                          <Utensils className="h-3 w-3" />
                          {t('orders.types.dineIn') || 'Dine In'}
                        </Badge>
                      )}
                    </div>
                    <p className="text-sm text-muted-foreground mt-1">
                      {order.restaurant?.name || 'Restaurant'}
                    </p>
                    {/* Table Info for Dine-In Orders */}
                    {order.orderType === 'DINE_IN' && order.diningTable && (
                      <p className="text-sm font-medium text-blue-600">
                        {t('orders.table') || 'Table'}: {order.diningTable.tableNumber}
                        {order.diningTable.section && ` - ${order.diningTable.section}`}
                      </p>
                    )}
                    <p className="text-sm text-muted-foreground">
                      {order.createdAt && format(new Date(order.createdAt), 'PPpp')}
                    </p>
                  </div>
                  <Badge className={statusColors[order.status] || 'bg-gray-100'}>
                    {t(`orders.statuses.${order.status}`)}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid gap-4 md:grid-cols-3">
                  <div>
                    <p className="text-sm font-medium">{t('orders.subtotal')}</p>
                    <p className="text-lg font-bold">${order.subtotal?.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm font-medium">{t('orders.deliveryFee')}</p>
                    <p className="text-lg font-bold">${order.deliveryFee?.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm font-medium">{t('orders.total')}</p>
                    <p className="text-2xl font-bold">${order.total?.toFixed(2)}</p>
                  </div>
                </div>

                {order.items && order.items.length > 0 && (
                  <div className="mt-4">
                    <p className="text-sm font-medium mb-2">{t('orders.items')}:</p>
                    <div className="space-y-1">
                      {order.items.map((item, idx) => (
                        <div key={idx} className="text-sm text-muted-foreground flex justify-between">
                          <span>{item.quantity}x {item.productName} {item.variantName ? `(${item.variantName})` : ''}</span>
                          <span>${item.totalPrice?.toFixed(2)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {order.deliveryInfo && (
                  <div className="mt-4">
                    <p className="text-sm font-medium">{t('orders.deliveryInfo.address')}:</p>
                    <p className="text-sm text-muted-foreground">
                      {order.deliveryInfo.address}, {order.deliveryInfo.city}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {t('orders.deliveryInfo.contactName')}: {order.deliveryInfo.contactName}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {t('orders.deliveryInfo.contactPhone')}: {order.deliveryInfo.contactPhone}
                    </p>
                  </div>
                )}

                {order.customerNotes && (
                  <div className="mt-4">
                    <p className="text-sm font-medium">{t('orders.notes')}:</p>
                    <p className="text-sm text-muted-foreground">
                      {order.customerNotes}
                    </p>
                  </div>
                )}

                {order.payment && (
                  <div className="mt-4">
                    <p className="text-sm font-medium">{t('orders.payment')}:</p>
                    <p className="text-sm text-muted-foreground">
                      {t(`orders.paymentMethods.${order.payment.method}`)} - {t(`orders.paymentStatus.${order.payment.status}`)}
                    </p>
                  </div>
                )}

                <div className="mt-4 flex gap-2 flex-wrap">
                  {/* Print Button - Always visible */}
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => PrintReceipt(order)}
                    className="gap-1"
                  >
                    <Printer className="h-4 w-4" />
                    {t('orders.printReceipt') || 'Print Receipt'}
                  </Button>

                  {/* Status Update Buttons */}
                  {nextStatusMap[order.status] && (
                    <>
                      <Button
                        size="sm"
                        onClick={() => updateOrderStatus(order.id, nextStatusMap[order.status])}
                      >
                        {t('orders.updateStatus')}: {t(`orders.statuses.${nextStatusMap[order.status]}`)}
                      </Button>
                      {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => updateOrderStatus(order.id, 'CANCELLED')}
                        >
                          {t('orders.cancelOrder')}
                        </Button>
                      )}
                    </>
                  )}
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {/* Create Order Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Create New Order</DialogTitle>
            <DialogDescription>
              Create a new order for a customer
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateOrder}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="restaurantId">Restaurant *</Label>
                <Select
                  value={formData.restaurantId}
                  onValueChange={(value) => {
                    setFormData({ ...formData, restaurantId: value, diningTableId: '' });
                    loadProducts(parseInt(value));
                    if (formData.orderType === 'DINE_IN') {
                      loadTables(parseInt(value));
                    }
                  }}
                  required
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.placeholders.selectRestaurant")} />
                  </SelectTrigger>
                  <SelectContent>
                    {restaurants.map((restaurant) => (
                      <SelectItem key={restaurant.id} value={restaurant.id.toString()}>
                        {restaurant.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Order Type Selection */}
              <div className="space-y-2">
                <Label>Order Type *</Label>
                <div className="grid grid-cols-3 gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setFormData({ ...formData, orderType: 'DELIVERY', diningTableId: '' });
                    }}
                    className={`flex flex-col items-center justify-center p-4 border-2 rounded-lg transition-all ${
                      formData.orderType === 'DELIVERY'
                        ? 'border-blue-500 bg-blue-50 text-blue-700'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <Truck className="h-6 w-6 mb-2" />
                    <span className="font-medium">Delivery</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setFormData({ ...formData, orderType: 'TAKEAWAY', diningTableId: '' });
                    }}
                    className={`flex flex-col items-center justify-center p-4 border-2 rounded-lg transition-all ${
                      formData.orderType === 'TAKEAWAY'
                        ? 'border-blue-500 bg-blue-50 text-blue-700'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <ShoppingBag className="h-6 w-6 mb-2" />
                    <span className="font-medium">Takeaway</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setFormData({ ...formData, orderType: 'DINE_IN', diningTableId: '' });
                      if (formData.restaurantId) {
                        loadTables(parseInt(formData.restaurantId));
                      }
                    }}
                    className={`flex flex-col items-center justify-center p-4 border-2 rounded-lg transition-all ${
                      formData.orderType === 'DINE_IN'
                        ? 'border-blue-500 bg-blue-50 text-blue-700'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <Utensils className="h-6 w-6 mb-2" />
                    <span className="font-medium">Dine-In</span>
                  </button>
                </div>
              </div>

              {/* Table Selection for Dine-In Orders */}
              {formData.orderType === 'DINE_IN' && (
                <div className="space-y-2">
                  <Label htmlFor="diningTableId">Select Table *</Label>
                  <Select
                    value={formData.diningTableId}
                    onValueChange={(value) => setFormData({ ...formData, diningTableId: value })}
                    required={formData.orderType === 'DINE_IN'}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t("common.placeholders.selectTable")} />
                    </SelectTrigger>
                    <SelectContent>
                      {tables.length === 0 ? (
                        <SelectItem value="no-tables" disabled>
                          No available tables
                        </SelectItem>
                      ) : (
                        tables.map((table) => (
                          <SelectItem key={table.id} value={table.id.toString()}>
                            Table {table.tableNumber} {table.tableName ? `- ${table.tableName}` : ''}
                            (Capacity: {table.capacity})
                          </SelectItem>
                        ))
                      )}
                    </SelectContent>
                  </Select>
                </div>
              )}

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="customerFirstName">First Name *</Label>
                  <Input
                    id="customerFirstName"
                    value={formData.customerFirstName}
                    onChange={(e) => setFormData({ ...formData, customerFirstName: e.target.value })}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="customerLastName">Last Name *</Label>
                  <Input
                    id="customerLastName"
                    value={formData.customerLastName}
                    onChange={(e) => setFormData({ ...formData, customerLastName: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="customerPhone">Customer Phone *</Label>
                  <Input
                    id="customerPhone"
                    type="tel"
                    value={formData.customerPhone}
                    onChange={(e) => setFormData({ ...formData, customerPhone: e.target.value })}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="customerEmail">Customer Email</Label>
                  <Input
                    id="customerEmail"
                    type="email"
                    value={formData.customerEmail}
                    onChange={(e) => setFormData({ ...formData, customerEmail: e.target.value })}
                  />
                </div>
              </div>

              {/* Delivery Address Fields - Only for Delivery Orders */}
              {formData.orderType === 'DELIVERY' && (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="deliveryAddress">Delivery Address *</Label>
                    <Input
                      id="deliveryAddress"
                      value={formData.deliveryAddress}
                      onChange={(e) => setFormData({ ...formData, deliveryAddress: e.target.value })}
                      required
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="deliveryCity">City *</Label>
                      <Input
                        id="deliveryCity"
                        value={formData.deliveryCity}
                        onChange={(e) => setFormData({ ...formData, deliveryCity: e.target.value })}
                        required
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="deliveryState">State</Label>
                      <Input
                        id="deliveryState"
                        value={formData.deliveryState}
                        onChange={(e) => setFormData({ ...formData, deliveryState: e.target.value })}
                      />
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="deliveryZipCode">ZIP Code</Label>
                    <Input
                      id="deliveryZipCode"
                      value={formData.deliveryZipCode}
                      onChange={(e) => setFormData({ ...formData, deliveryZipCode: e.target.value })}
                    />
                  </div>
                </>
              )}

              <div className="space-y-2">
                <Label htmlFor="paymentMethod">Payment Method *</Label>
                <Select
                  value={formData.paymentMethod}
                  onValueChange={(value) => setFormData({ ...formData, paymentMethod: value })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select payment method" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CARD">{t("orders.paymentMethod.card")}</SelectItem>
                    <SelectItem value="CASH">{t("orders.paymentMethod.cash")}</SelectItem>
                    <SelectItem value="ONLINE">{t("orders.paymentMethod.online")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="customerNotes">Customer Notes</Label>
                <Textarea
                  id="customerNotes"
                  value={formData.customerNotes}
                  onChange={(e) => setFormData({ ...formData, customerNotes: e.target.value })}
                  rows={3}
                />
              </div>

              {/* Add Items Section */}
              <div className="border-t pt-4">
                <Label className="text-base font-semibold">Order Items</Label>
                <div className="grid grid-cols-3 gap-2 mt-2">
                  <div className="col-span-2">
                    <Select
                      value={selectedProduct}
                      onValueChange={setSelectedProduct}
                      disabled={!formData.restaurantId}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder={t("common.placeholders.selectProduct")} />
                      </SelectTrigger>
                      <SelectContent>
                        {products.map((product) => (
                          <SelectItem key={product.id} value={product.id.toString()}>
                            {product.name} - ${product.price?.toFixed(2)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="flex gap-2">
                    <Input
                      type="number"
                      min="1"
                      value={selectedQuantity}
                      onChange={(e) => setSelectedQuantity(parseInt(e.target.value) || 1)}
                      placeholder={t("common.placeholders.quantity")}
                    />
                    <Button type="button" onClick={handleAddItem} disabled={!selectedProduct}>
                      <Plus className="h-4 w-4" />
                    </Button>
                  </div>
                </div>

                {/* Items List */}
                {formData.items.length > 0 && (
                  <div className="mt-4 space-y-2">
                    {formData.items.map((item, index) => (
                      <div key={index} className="flex justify-between items-center p-2 bg-gray-50 rounded">
                        <span className="text-sm">
                          {item.quantity}x {item.productName}
                        </span>
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium">${item.totalPrice.toFixed(2)}</span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => handleRemoveItem(index)}
                          >
                            <X className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    ))}
                    <div className="flex justify-between items-center p-2 bg-blue-50 rounded font-semibold">
                      <span>Total</span>
                      <span>${calculateTotal().toFixed(2)}</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateModalOpen(false); resetForm(); }}>
                Cancel
              </Button>
              <Button type="submit" disabled={formData.items.length === 0}>
                Create Order
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
