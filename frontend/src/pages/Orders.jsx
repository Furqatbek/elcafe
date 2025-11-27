import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI, restaurantAPI, menuAPI } from '../services/api';
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
  X
} from 'lucide-react';
import { format } from 'date-fns';

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
    customerName: '',
    customerPhone: '',
    customerEmail: '',
    deliveryAddress: '',
    deliveryCity: '',
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

  const loadOrders = async () => {
    try {
      const response = await orderAPI.getAll({ page: 0, size: 100, sort: 'createdAt,desc' });
      setOrders(response.data.data.content || []);
    } catch (error) {
      console.error('Failed to load orders:', error);
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
      alert('Please add at least one item to the order');
      return;
    }

    try {
      const orderData = {
        restaurantId: parseInt(formData.restaurantId),
        customer: {
          name: formData.customerName,
          phone: formData.customerPhone,
          email: formData.customerEmail
        },
        deliveryInfo: {
          address: formData.deliveryAddress,
          city: formData.deliveryCity,
          contactName: formData.customerName,
          contactPhone: formData.customerPhone
        },
        items: formData.items,
        customerNotes: formData.customerNotes,
        payment: {
          method: formData.paymentMethod
        }
      };

      await orderAPI.create(orderData);
      setCreateModalOpen(false);
      resetForm();
      loadOrders();
    } catch (error) {
      console.error('Failed to create order:', error);
      alert('Failed to create order: ' + (error.response?.data?.message || error.message));
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
      customerName: '',
      customerPhone: '',
      customerEmail: '',
      deliveryAddress: '',
      deliveryCity: '',
      customerNotes: '',
      paymentMethod: 'CARD',
      items: []
    });
    setSelectedProduct('');
    setSelectedQuantity(1);
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
                placeholder="Search by order #"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>

            <Select value={selectedStatus} onValueChange={setSelectedStatus}>
              <SelectTrigger>
                <SelectValue placeholder="All Statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Statuses</SelectItem>
                <SelectItem value="NEW">New</SelectItem>
                <SelectItem value="ACCEPTED">Accepted</SelectItem>
                <SelectItem value="PREPARING">Preparing</SelectItem>
                <SelectItem value="READY">Ready</SelectItem>
                <SelectItem value="COURIER_ASSIGNED">Courier Assigned</SelectItem>
                <SelectItem value="ON_DELIVERY">On Delivery</SelectItem>
                <SelectItem value="DELIVERED">Delivered</SelectItem>
                <SelectItem value="CANCELLED">Cancelled</SelectItem>
              </SelectContent>
            </Select>

            <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
              <SelectTrigger>
                <SelectValue placeholder="All Restaurants" />
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
              placeholder="Start Date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />

            <Input
              type="date"
              placeholder="End Date"
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
                  <div>
                    <CardTitle className="text-lg">
                      {t('orders.orderNumber')}: #{order.orderNumber}
                    </CardTitle>
                    <p className="text-sm text-muted-foreground mt-1">
                      {order.restaurant?.name || 'Restaurant'}
                    </p>
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

                {nextStatusMap[order.status] && (
                  <div className="mt-4 flex gap-2">
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
                  </div>
                )}
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
                    setFormData({ ...formData, restaurantId: value });
                    loadProducts(parseInt(value));
                  }}
                  required
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select restaurant" />
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

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="customerName">Customer Name *</Label>
                  <Input
                    id="customerName"
                    value={formData.customerName}
                    onChange={(e) => setFormData({ ...formData, customerName: e.target.value })}
                    required
                  />
                </div>

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

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="deliveryAddress">Delivery Address *</Label>
                  <Input
                    id="deliveryAddress"
                    value={formData.deliveryAddress}
                    onChange={(e) => setFormData({ ...formData, deliveryAddress: e.target.value })}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="deliveryCity">City *</Label>
                  <Input
                    id="deliveryCity"
                    value={formData.deliveryCity}
                    onChange={(e) => setFormData({ ...formData, deliveryCity: e.target.value })}
                    required
                  />
                </div>
              </div>

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
                    <SelectItem value="CARD">Card</SelectItem>
                    <SelectItem value="CASH">Cash</SelectItem>
                    <SelectItem value="ONLINE">Online</SelectItem>
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
                        <SelectValue placeholder="Select product" />
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
                      placeholder="Qty"
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
