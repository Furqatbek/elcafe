import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import usePOSStore from '../pos/store/posStore';
import { orderAPI, restaurantAPI, menuAPI, tablesAPI, posAPI } from '../services/api';
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
  Printer,
  Edit,
  CheckCircle,
  Trash2,
  Minus,
  CreditCard,
  Banknote,
  Wallet,
  LayoutGrid,
  List,
  Users,
  Clock,
  RefreshCw
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
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [filteredOrders, setFilteredOrders] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [products, setProducts] = useState([]);
  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);

  // View mode: 'list' or 'byTable'
  const [viewMode, setViewMode] = useState('list');
  const [floorPlan, setFloorPlan] = useState(null);
  const [tableOrders, setTableOrders] = useState({});
  const [loadingTableView, setLoadingTableView] = useState(false);

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

  // Edit order items state
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingOrder, setEditingOrder] = useState(null);
  const [editItems, setEditItems] = useState([]);
  const [availableProducts, setAvailableProducts] = useState([]);
  const [newItemProductId, setNewItemProductId] = useState('');
  const [newItemQuantity, setNewItemQuantity] = useState(1);

  // Payment modal state
  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [paymentOrder, setPaymentOrder] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [amountTendered, setAmountTendered] = useState('');
  const [processingPayment, setProcessingPayment] = useState(false);

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
      // Don't auto-select - let user choose, or auto-select when switching to table view
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

  // Load table view data (floor plan + orders grouped by table)
  const loadTableView = async (restaurantId) => {
    if (!restaurantId) return;

    setLoadingTableView(true);
    try {
      // Get floor plan for the restaurant
      const floorPlanResponse = await tablesAPI.getFloorPlan(restaurantId);
      const floorPlanData = floorPlanResponse?.data?.data || floorPlanResponse?.data;
      console.log('Floor plan data:', floorPlanData);
      setFloorPlan(floorPlanData);

      // Get open dine-in orders
      const ordersResponse = await posAPI.getOpenDineInOrders(restaurantId);
      console.log('Orders response:', ordersResponse);
      const openOrders = ordersResponse?.data?.data || ordersResponse?.data || [];
      console.log('Open orders:', openOrders);

      // Group orders by table ID
      // Handle different response formats:
      // - diningTable.id (from entity with full table object)
      // - dineInInfo.tableIds[0] (from POS response)
      // - tableIds as string "14" or "14,15" (from direct entity serialization)
      const ordersByTable = {};
      if (Array.isArray(openOrders)) {
        openOrders.forEach(order => {
          console.log('Processing order:', order.id, order.orderNumber);
          console.log('  - diningTable:', order.diningTable);
          console.log('  - dineInInfo:', order.dineInInfo);
          console.log('  - tableIds:', order.tableIds);

          let tableId = null;

          // Try different sources for table ID - check dineInInfo first (POS response format)
          if (order.dineInInfo?.tableIds?.length > 0) {
            tableId = Number(order.dineInInfo.tableIds[0]);
            console.log('  - Found tableId from dineInInfo.tableIds:', tableId);
          } else if (order.diningTable?.id) {
            tableId = Number(order.diningTable.id);
            console.log('  - Found tableId from diningTable.id:', tableId);
          } else if (order.tableIds) {
            // tableIds can be a string like "14" or "14,15"
            const firstTableId = String(order.tableIds).split(',')[0].trim();
            tableId = parseInt(firstTableId, 10);
            console.log('  - Found tableId from tableIds string:', tableId);
          }

          if (tableId && !isNaN(tableId)) {
            // Use number as key for consistency
            const key = tableId;
            if (!ordersByTable[key]) {
              ordersByTable[key] = [];
            }
            ordersByTable[key].push(order);
            console.log('  - Added order to table', key);
          } else {
            console.log('  - No valid tableId found for order');
          }
        });
      }
      console.log('Orders by table:', ordersByTable);
      setTableOrders(ordersByTable);
    } catch (error) {
      console.error('Failed to load table view:', error);
      setFloorPlan(null);
      setTableOrders({});
    } finally {
      setLoadingTableView(false);
    }
  };

  // Refresh table view when view mode changes to 'byTable'
  useEffect(() => {
    if (viewMode === 'byTable') {
      if (selectedRestaurant && selectedRestaurant !== 'all') {
        loadTableView(parseInt(selectedRestaurant));
      } else if (restaurants.length > 0) {
        // Auto-select first restaurant when switching to table view
        setSelectedRestaurant(restaurants[0].id.toString());
      }
    }
  }, [viewMode, selectedRestaurant, restaurants]);

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

  // Open payment modal for closing table
  const handleCloseTable = (order) => {
    setPaymentOrder(order);
    setPaymentMethod('CASH');
    setAmountTendered('');
    setPaymentModalOpen(true);
  };

  // Process payment and close table
  const handleProcessPaymentAndClose = async () => {
    if (!paymentOrder) return;

    setProcessingPayment(true);
    try {
      // Process payment
      const paymentData = {
        paymentMethod: paymentMethod,
        amount: paymentOrder.total,
        amountTendered: paymentMethod === 'CASH' ? parseFloat(amountTendered) || paymentOrder.total : paymentOrder.total
      };

      await posAPI.processPayment(paymentOrder.id, paymentData);

      // Close order and release table
      await posAPI.closeOrder(paymentOrder.id);

      setPaymentModalOpen(false);
      setPaymentOrder(null);
      loadOrders();
      alert(t('orders.paymentSuccessTableReleased', 'Payment processed and table released successfully'));
    } catch (error) {
      console.error('Failed to process payment:', error);
      alert(t('orders.paymentError', 'Failed to process payment: ') + (error.response?.data?.message || error.message));
    } finally {
      setProcessingPayment(false);
    }
  };

  // Calculate change for cash payment
  const calculateChange = () => {
    if (!paymentOrder || paymentMethod !== 'CASH') return 0;
    const tendered = parseFloat(amountTendered) || 0;
    return Math.max(0, tendered - paymentOrder.total);
  };

  // Navigate to payment screen for an order (opens full POS payment)
  const handleCloseCheck = async (order) => {
    // Store order ID and navigate - POSApp will fetch the full order
    localStorage.setItem('pendingPaymentOrderId', order.id.toString());

    // Store selected restaurant ID for the POS
    if (order.restaurant?.id) {
      localStorage.setItem('selectedRestaurantId', order.restaurant.id.toString());
    }

    // Navigate to POS payment screen
    navigate('/pos?screen=payment');
  };

  // Open edit items modal
  const handleEditItems = async (order) => {
    setEditingOrder(order);
    setEditItems(order.items?.map(item => ({
      ...item,
      isModified: false,
      isDeleted: false
    })) || []);

    // Load products for the restaurant
    if (order.restaurant?.id) {
      try {
        const response = await menuAPI.getProductsByRestaurant(order.restaurant.id);
        setAvailableProducts(response.data.data || []);
      } catch (error) {
        console.error('Failed to load products:', error);
      }
    }

    setNewItemProductId('');
    setNewItemQuantity(1);
    setEditModalOpen(true);
  };

  // Update item quantity in edit modal
  const handleUpdateItemQuantity = (itemIndex, newQuantity) => {
    if (newQuantity < 1) return;
    setEditItems(prev => prev.map((item, idx) =>
      idx === itemIndex
        ? { ...item, quantity: newQuantity, isModified: true }
        : item
    ));
  };

  // Mark item for deletion in edit modal
  const handleMarkItemDeleted = (itemIndex) => {
    setEditItems(prev => prev.map((item, idx) =>
      idx === itemIndex
        ? { ...item, isDeleted: true }
        : item
    ));
  };

  // Restore deleted item in edit modal
  const handleRestoreEditItem = (itemIndex) => {
    setEditItems(prev => prev.map((item, idx) =>
      idx === itemIndex
        ? { ...item, isDeleted: false }
        : item
    ));
  };

  // Add new item
  const handleAddNewItem = () => {
    if (!newItemProductId) return;

    const product = availableProducts.find(p => p.id === parseInt(newItemProductId));
    if (!product) return;

    setEditItems(prev => [...prev, {
      id: null,
      productId: product.id,
      productName: product.name,
      quantity: newItemQuantity,
      unitPrice: product.basePrice || product.price,
      totalPrice: (product.basePrice || product.price) * newItemQuantity,
      isNew: true,
      isModified: false,
      isDeleted: false
    }]);

    setNewItemProductId('');
    setNewItemQuantity(1);
  };

  // Save order item changes
  const handleSaveItemChanges = async () => {
    if (!editingOrder) return;

    try {
      // Process deletions
      for (const item of editItems.filter(i => i.isDeleted && i.id)) {
        await posAPI.removeItemFromOrder(editingOrder.id, item.id);
      }

      // Process quantity updates
      for (const item of editItems.filter(i => i.isModified && !i.isDeleted && !i.isNew && i.id)) {
        await posAPI.updateItemQuantity(editingOrder.id, item.id, item.quantity);
      }

      // Process new items
      for (const item of editItems.filter(i => i.isNew && !i.isDeleted)) {
        await posAPI.addItemToOrder(editingOrder.id, {
          productId: item.productId,
          quantity: item.quantity,
          specialInstructions: ''
        });
      }

      setEditModalOpen(false);
      setEditingOrder(null);
      loadOrders();
      alert(t('orders.itemsUpdated', 'Order items updated successfully'));
    } catch (error) {
      console.error('Failed to update order items:', error);
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
            {viewMode === 'list' ? t('orders.allOrders') : t('orders.ordersByTable', 'Orders by Table')}
          </p>
        </div>
        <div className="flex items-center gap-3">
          {/* View Mode Toggle */}
          <div className="flex bg-gray-100 rounded-lg p-1">
            <button
              onClick={() => setViewMode('list')}
              className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors ${
                viewMode === 'list'
                  ? 'bg-white shadow text-blue-600'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <List className="h-4 w-4" />
              <span className="text-sm font-medium">{t('orders.listView', 'List')}</span>
            </button>
            <button
              onClick={() => setViewMode('byTable')}
              className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors ${
                viewMode === 'byTable'
                  ? 'bg-white shadow text-blue-600'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <LayoutGrid className="h-4 w-4" />
              <span className="text-sm font-medium">{t('orders.tableView', 'By Table')}</span>
            </button>
          </div>
          <Button onClick={() => setCreateModalOpen(true)}>
            <Plus className="h-4 w-4 mr-2" />
            {t('pages.orders.createOrder', 'Create Order')}
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <div className="flex justify-between items-center">
            <CardTitle className="text-lg">
              <Filter className="h-5 w-5 inline mr-2" />
              {t('pages.orders.filters', 'Filters')}
            </CardTitle>
            <Button variant="outline" size="sm" onClick={resetFilters}>
              <X className="h-4 w-4 mr-1" />
              {t('pages.orders.reset', 'Reset')}
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
                <SelectItem value="COURIER_ASSIGNED">{t("orders.status.courierAssigned", "Courier Assigned")}</SelectItem>
                <SelectItem value="ON_DELIVERY">{t("orders.status.onDelivery", "On Delivery")}</SelectItem>
                <SelectItem value="DELIVERED">{t("orders.status.delivered")}</SelectItem>
                <SelectItem value="CANCELLED">{t("orders.status.cancelled")}</SelectItem>
              </SelectContent>
            </Select>

            <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.placeholders.allRestaurants")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("pages.orders.allRestaurants", "All Restaurants")}</SelectItem>
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

      {/* View Content */}
      {viewMode === 'byTable' ? (
        /* Table View */
        <div className="space-y-4">
          {selectedRestaurant === 'all' ? (
            <Card>
              <CardContent className="pt-6">
                <div className="text-center py-8">
                  <LayoutGrid className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                  <p className="text-lg font-medium text-gray-900 mb-2">
                    {t('orders.selectRestaurantForTableView', 'Select a Restaurant')}
                  </p>
                  <p className="text-muted-foreground">
                    {t('orders.selectRestaurantDesc', 'Please select a restaurant from the filter above to view orders by table')}
                  </p>
                </div>
              </CardContent>
            </Card>
          ) : loadingTableView ? (
            <Card>
              <CardContent className="pt-6">
                <div className="flex items-center justify-center py-8">
                  <RefreshCw className="h-8 w-8 animate-spin text-blue-600" />
                  <span className="ml-3 text-lg">{t('common.loading')}</span>
                </div>
              </CardContent>
            </Card>
          ) : (
            <>
              {/* Refresh Button */}
              <div className="flex justify-end">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => loadTableView(parseInt(selectedRestaurant))}
                >
                  <RefreshCw className="h-4 w-4 mr-2" />
                  {t('common.refresh', 'Refresh')}
                </Button>
              </div>

              {/* Tables Grid - Only show occupied tables */}
              <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                {floorPlan?.tables?.filter(table => {
                  const hasOrders = tableOrders[table.id]?.length > 0;
                  const isOccupied = table.status === 'OCCUPIED';
                  console.log(`Table ${table.id} (${table.tableNumber}): status=${table.status}, isOccupied=${isOccupied}, hasOrders=${hasOrders}`);
                  return isOccupied; // Only show occupied tables, hide available ones
                }).map((table) => {
                  const ordersForTable = tableOrders[table.id] || [];
                  console.log(`Rendering table ${table.id}: ${ordersForTable.length} orders`, ordersForTable);
                  const tableTotal = ordersForTable.reduce((sum, order) => sum + (order.total || 0), 0);

                  return (
                    <Card key={table.id} className={`${table.status === 'OCCUPIED' ? 'border-orange-300 bg-orange-50' : 'border-gray-200'}`}>
                      <CardHeader className="pb-2">
                        <div className="flex justify-between items-center">
                          <div className="flex items-center gap-2">
                            <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                              table.status === 'OCCUPIED' ? 'bg-orange-500 text-white' : 'bg-gray-200 text-gray-600'
                            }`}>
                              <Utensils className="h-5 w-5" />
                            </div>
                            <div>
                              <CardTitle className="text-lg">
                                {t('orders.table', 'Table')} {table.tableNumber}
                              </CardTitle>
                              {table.section && (
                                <p className="text-xs text-muted-foreground">{table.section}</p>
                              )}
                            </div>
                          </div>
                          <Badge className={table.status === 'OCCUPIED' ? 'bg-orange-100 text-orange-800' : 'bg-gray-100 text-gray-800'}>
                            {t(`tables.status.${table.status?.toLowerCase()}`, table.status)}
                          </Badge>
                        </div>
                      </CardHeader>
                      <CardContent>
                        {ordersForTable.length === 0 ? (
                          <p className="text-sm text-muted-foreground text-center py-4">
                            {t('orders.noActiveOrders', 'No active orders')}
                          </p>
                        ) : (
                          <div className="space-y-3">
                            {ordersForTable.map((order) => (
                              <div key={order.id} className="border rounded-lg p-3 bg-white">
                                <div className="flex justify-between items-start mb-2">
                                  <div>
                                    <p className="font-medium text-sm">#{order.orderNumber}</p>
                                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                                      <Clock className="h-3 w-3" />
                                      {order.createdAt && format(new Date(order.createdAt), 'HH:mm')}
                                      {order.waiter && (
                                        <>
                                          <span>•</span>
                                          <Users className="h-3 w-3" />
                                          {order.waiter.name}
                                        </>
                                      )}
                                    </div>
                                  </div>
                                  <Badge className={statusColors[order.status] || 'bg-gray-100'} variant="secondary">
                                    {t(`orders.statuses.${order.status}`, order.status)}
                                  </Badge>
                                </div>

                                {/* Order Items Preview */}
                                {order.items && order.items.length > 0 && (
                                  <div className="text-xs text-muted-foreground mb-2 max-h-16 overflow-y-auto">
                                    {order.items.slice(0, 3).map((item, idx) => (
                                      <div key={idx} className="flex justify-between">
                                        <span>{item.quantity}x {item.productName}</span>
                                        <span>{item.totalPrice?.toFixed(0)}</span>
                                      </div>
                                    ))}
                                    {order.items.length > 3 && (
                                      <p className="text-center text-gray-400">+{order.items.length - 3} more items</p>
                                    )}
                                  </div>
                                )}

                                <div className="flex justify-between items-center pt-2 border-t">
                                  <span className="font-semibold">{order.total?.toFixed(0)}</span>
                                  <div className="flex gap-1 items-center">
                                    {/* Print Receipt Button - Always visible */}
                                    <Button
                                      variant="outline"
                                      size="sm"
                                      onClick={() => PrintReceipt(order)}
                                      className="h-7 px-2"
                                      title={t('orders.printReceipt', 'Print Receipt')}
                                    >
                                      <Printer className="h-3 w-3" />
                                    </Button>
                                    {order.paymentStatus === 'COMPLETED' ? (
                                      <Badge className="bg-green-100 text-green-800 h-7">
                                        {t('orders.paid', 'Paid')}
                                      </Badge>
                                    ) : (
                                      <>
                                        <Button
                                          variant="outline"
                                          size="sm"
                                          onClick={() => handleEditItems(order)}
                                          className="h-7 px-2"
                                          title={t('orders.editItems', 'Edit Items')}
                                        >
                                          <Edit className="h-3 w-3" />
                                        </Button>
                                        <Button
                                          variant="default"
                                          size="sm"
                                          onClick={() => handleCloseCheck(order)}
                                          className="h-7 px-2 bg-blue-600 hover:bg-blue-700"
                                          title={t('orders.closeCheck', 'Close Check')}
                                        >
                                          <CreditCard className="h-3 w-3" />
                                        </Button>
                                      </>
                                    )}
                                  </div>
                                </div>
                              </div>
                            ))}

                            {/* Table Total */}
                            {ordersForTable.length > 1 && (
                              <div className="flex justify-between items-center pt-2 border-t-2 border-dashed">
                                <span className="font-semibold text-sm">{t('orders.tableTotal', 'Table Total')}</span>
                                <span className="text-lg font-bold">{tableTotal.toFixed(0)}</span>
                              </div>
                            )}
                          </div>
                        )}
                      </CardContent>
                    </Card>
                  );
                })}

                {/* Empty State */}
                {(!floorPlan?.tables || floorPlan.tables.filter(t => t.status === 'OCCUPIED').length === 0) && (
                  <Card className="col-span-full">
                    <CardContent className="pt-6">
                      <div className="text-center py-8">
                        <Utensils className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                        <p className="text-lg font-medium text-gray-900 mb-2">
                          {t('orders.noActiveTableOrders', 'No Active Table Orders')}
                        </p>
                        <p className="text-muted-foreground">
                          {t('orders.noActiveTableOrdersDesc', 'There are no tables with active orders at the moment')}
                        </p>
                      </div>
                    </CardContent>
                  </Card>
                )}
              </div>
            </>
          )}
        </div>
      ) : (
        /* List View - Original Orders List */
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
                      {order.createdAt && format(new Date(order.createdAt), 'dd/MM/yyyy HH:mm')}
                    </p>
                  </div>
                  <Badge className={statusColors[order.status] || 'bg-gray-100'}>
                    {t(`orders.statuses.${order.status}`)}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid gap-4 md:grid-cols-3">
                  {order.diningTable && (
                    <div>
                      <p className="text-sm font-medium">{t('orders.table', 'Table')}</p>
                      <p className="text-lg font-bold">{order.diningTable.tableNumber}</p>
                    </div>
                  )}
                  {order.waiter && (
                    <div>
                      <p className="text-sm font-medium">{t('orders.waiter', 'Waiter')}</p>
                      <p className="text-lg font-bold">{order.waiter.name}</p>
                    </div>
                  )}
                  <div>
                    <p className="text-sm font-medium">{t('orders.total')}</p>
                    <p className="text-2xl font-bold">{order.total?.toFixed(0)}</p>
                  </div>
                </div>

                {order.items && order.items.length > 0 && (
                  <div className="mt-4">
                    <p className="text-sm font-medium mb-2">{t('orders.items')}:</p>
                    <div className="space-y-1">
                      {order.items.map((item, idx) => (
                        <div key={idx} className="text-sm text-muted-foreground flex justify-between">
                          <span>{item.quantity}x {item.productName} {item.variantName ? `(${item.variantName})` : ''}</span>
                          <span>{item.totalPrice?.toFixed(2)}</span>
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

                  {/* Edit Items Button - Only for open orders */}
                  {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleEditItems(order)}
                      className="gap-1"
                    >
                      <Edit className="h-4 w-4" />
                      {t('orders.editItems', 'Edit Items')}
                    </Button>
                  )}

                  {/* Close Check / Pay Button - For unpaid open orders */}
                  {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && order.paymentStatus !== 'COMPLETED' && (
                    <Button
                      variant="default"
                      size="sm"
                      onClick={() => handleCloseCheck(order)}
                      className="gap-1 bg-blue-600 hover:bg-blue-700"
                    >
                      <CreditCard className="h-4 w-4" />
                      {t('orders.closeCheck', 'Close Check')}
                    </Button>
                  )}

                  {/* Close Table Button - Only for unpaid dine-in orders */}
                  {order.orderType === 'DINE_IN' && order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && order.paymentStatus !== 'COMPLETED' && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleCloseTable(order)}
                      className="gap-1 text-green-600 hover:text-green-700"
                    >
                      <CheckCircle className="h-4 w-4" />
                      {t('orders.closeTable', 'Close Table')}
                    </Button>
                  )}

                  {/* Show paid badge for fully paid orders */}
                  {order.paymentStatus === 'COMPLETED' && (
                    <Badge className="bg-green-100 text-green-800">
                      {t('orders.paid', 'Paid')}
                    </Badge>
                  )}

                  {/* Cancel Order Button */}
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
              </CardContent>
            </Card>
          ))
        )}
        </div>
      )}

      {/* Create Order Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('pages.orders.createNewOrder', 'Create New Order')}</DialogTitle>
            <DialogDescription>
              {t('pages.orders.createOrderDescription', 'Create a new order for a customer')}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateOrder}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="restaurantId">{t('pages.orders.restaurant', 'Restaurant')} *</Label>
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
                <Label>{t('pages.orders.orderType', 'Order Type')} *</Label>
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
                    <span className="font-medium">{t('pages.orders.delivery', 'Delivery')}</span>
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
                    <span className="font-medium">{t('pages.orders.takeaway', 'Takeaway')}</span>
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
                    <span className="font-medium">{t('pages.orders.dineIn', 'Dine-In')}</span>
                  </button>
                </div>
              </div>

              {/* Table Selection for Dine-In Orders */}
              {formData.orderType === 'DINE_IN' && (
                <div className="space-y-2">
                  <Label htmlFor="diningTableId">{t('pages.orders.selectTable', 'Select Table')} *</Label>
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
                          {t('pages.orders.noAvailableTables', 'No available tables')}
                        </SelectItem>
                      ) : (
                        tables.map((table) => (
                          <SelectItem key={table.id} value={table.id.toString()}>
                            {t('pages.orders.table', 'Table')} {table.tableNumber} {table.tableName ? `- ${table.tableName}` : ''}
                            ({t('pages.orders.capacity', 'Capacity')}: {table.capacity})
                          </SelectItem>
                        ))
                      )}
                    </SelectContent>
                  </Select>
                </div>
              )}

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="customerFirstName">{t('pages.orders.firstName', 'First Name')} *</Label>
                  <Input
                    id="customerFirstName"
                    value={formData.customerFirstName}
                    onChange={(e) => setFormData({ ...formData, customerFirstName: e.target.value })}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="customerLastName">{t('pages.orders.lastName', 'Last Name')} *</Label>
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
                  <Label htmlFor="customerPhone">{t('pages.orders.customerPhone', 'Customer Phone')} *</Label>
                  <Input
                    id="customerPhone"
                    type="tel"
                    value={formData.customerPhone}
                    onChange={(e) => setFormData({ ...formData, customerPhone: e.target.value })}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="customerEmail">{t('pages.orders.customerEmail', 'Customer Email')}</Label>
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
                    <Label htmlFor="deliveryAddress">{t('pages.orders.deliveryAddress', 'Delivery Address')} *</Label>
                    <Input
                      id="deliveryAddress"
                      value={formData.deliveryAddress}
                      onChange={(e) => setFormData({ ...formData, deliveryAddress: e.target.value })}
                      required
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="deliveryCity">{t('pages.orders.city', 'City')} *</Label>
                      <Input
                        id="deliveryCity"
                        value={formData.deliveryCity}
                        onChange={(e) => setFormData({ ...formData, deliveryCity: e.target.value })}
                        required
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="deliveryState">{t('pages.orders.state', 'State')}</Label>
                      <Input
                        id="deliveryState"
                        value={formData.deliveryState}
                        onChange={(e) => setFormData({ ...formData, deliveryState: e.target.value })}
                      />
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="deliveryZipCode">{t('pages.orders.zipCode', 'ZIP Code')}</Label>
                    <Input
                      id="deliveryZipCode"
                      value={formData.deliveryZipCode}
                      onChange={(e) => setFormData({ ...formData, deliveryZipCode: e.target.value })}
                    />
                  </div>
                </>
              )}

              <div className="space-y-2">
                <Label htmlFor="paymentMethod">{t('pages.orders.paymentMethod', 'Payment Method')} *</Label>
                <Select
                  value={formData.paymentMethod}
                  onValueChange={(value) => setFormData({ ...formData, paymentMethod: value })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t('pages.orders.selectPaymentMethod', 'Select payment method')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CARD">{t("orders.paymentMethod.card")}</SelectItem>
                    <SelectItem value="CASH">{t("orders.paymentMethod.cash")}</SelectItem>
                    <SelectItem value="ONLINE">{t("orders.paymentMethod.online")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="customerNotes">{t('pages.orders.customerNotes', 'Customer Notes')}</Label>
                <Textarea
                  id="customerNotes"
                  value={formData.customerNotes}
                  onChange={(e) => setFormData({ ...formData, customerNotes: e.target.value })}
                  rows={3}
                />
              </div>

              {/* Add Items Section */}
              <div className="border-t pt-4">
                <Label className="text-base font-semibold">{t('pages.orders.orderItems', 'Order Items')}</Label>
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
                            {product.name} - {product.price?.toFixed(2)}
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
                          <span className="text-sm font-medium">{item.totalPrice.toFixed(2)}</span>
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
                      <span>{t('pages.orders.total', 'Total')}</span>
                      <span>{calculateTotal().toFixed(2)}</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateModalOpen(false); resetForm(); }}>
                {t('pages.orders.cancel', 'Cancel')}
              </Button>
              <Button type="submit" disabled={formData.items.length === 0}>
                {t('pages.orders.createOrder', 'Create Order')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Edit Order Items Modal */}
      <Dialog open={editModalOpen} onOpenChange={setEditModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('orders.editOrderItems', 'Edit Order Items')}</DialogTitle>
            <DialogDescription>
              {t('orders.editOrderItemsDesc', 'Modify items in order')} #{editingOrder?.orderNumber}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            {/* Current Items */}
            <div className="space-y-2">
              <Label>{t('orders.currentItems', 'Current Items')}</Label>
              {editItems.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('orders.noItems', 'No items')}</p>
              ) : (
                <div className="space-y-2">
                  {editItems.map((item, index) => (
                    <div
                      key={index}
                      className={`flex items-center justify-between p-3 rounded-lg border ${
                        item.isDeleted ? 'bg-red-50 border-red-200 opacity-50' :
                        item.isNew ? 'bg-green-50 border-green-200' :
                        item.isModified ? 'bg-yellow-50 border-yellow-200' :
                        'bg-gray-50 border-gray-200'
                      }`}
                    >
                      <div className="flex-1">
                        <p className={`font-medium ${item.isDeleted ? 'line-through' : ''}`}>
                          {item.productName}
                          {item.variantName && ` (${item.variantName})`}
                        </p>
                        <p className="text-sm text-muted-foreground">
                          {(item.unitPrice || item.price || 0).toFixed(2)} x {item.quantity} = {((item.unitPrice || item.price || 0) * item.quantity).toFixed(2)}
                        </p>
                      </div>

                      {!item.isDeleted ? (
                        <div className="flex items-center gap-2">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => handleUpdateItemQuantity(index, item.quantity - 1)}
                            disabled={item.quantity <= 1}
                          >
                            <Minus className="h-4 w-4" />
                          </Button>
                          <span className="w-8 text-center font-medium">{item.quantity}</span>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => handleUpdateItemQuantity(index, item.quantity + 1)}
                          >
                            <Plus className="h-4 w-4" />
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => handleMarkItemDeleted(index)}
                            className="text-red-600 hover:text-red-700"
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      ) : (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => handleRestoreEditItem(index)}
                          className="text-green-600 hover:text-green-700"
                        >
                          {t('orders.restore', 'Restore')}
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Add New Item */}
            <div className="border-t pt-4">
              <Label>{t('orders.addNewItem', 'Add New Item')}</Label>
              <div className="flex gap-2 mt-2">
                <Select value={newItemProductId} onValueChange={setNewItemProductId}>
                  <SelectTrigger className="flex-1">
                    <SelectValue placeholder={t('orders.selectProduct', 'Select product')} />
                  </SelectTrigger>
                  <SelectContent>
                    {availableProducts.map((product) => (
                      <SelectItem key={product.id} value={product.id.toString()}>
                        {product.name} - {(product.basePrice || product.price || 0).toFixed(2)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Input
                  type="number"
                  min="1"
                  value={newItemQuantity}
                  onChange={(e) => setNewItemQuantity(parseInt(e.target.value) || 1)}
                  className="w-20"
                />
                <Button type="button" onClick={handleAddNewItem} disabled={!newItemProductId}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
            </div>

            {/* Summary */}
            <div className="border-t pt-4">
              <div className="flex justify-between items-center p-3 bg-blue-50 rounded-lg font-semibold">
                <span>{t('orders.newTotal', 'New Total')}</span>
                <span>
                  {editItems
                    .filter(i => !i.isDeleted)
                    .reduce((sum, item) => sum + (item.unitPrice || item.price || 0) * item.quantity, 0)
                    .toFixed(2)}
                </span>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setEditModalOpen(false)}>
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button type="button" onClick={handleSaveItemChanges}>
              {t('orders.saveChanges', 'Save Changes')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Payment Modal */}
      <Dialog open={paymentModalOpen} onOpenChange={setPaymentModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('orders.processPayment', 'Process Payment')}</DialogTitle>
            <DialogDescription>
              {t('orders.processPaymentDesc', 'Complete payment for order')} #{paymentOrder?.orderNumber}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-6 py-4">
            {/* Order Summary */}
            <div className="bg-gray-50 p-4 rounded-lg">
              <div className="flex justify-between mb-2">
                <span className="text-sm text-muted-foreground">{t('orders.subtotal', 'Subtotal')}</span>
                <span className="font-medium">{paymentOrder?.subtotal?.toFixed(2)}</span>
              </div>
              {paymentOrder?.tax > 0 && (
                <div className="flex justify-between mb-2">
                  <span className="text-sm text-muted-foreground">{t('orders.tax', 'Tax')}</span>
                  <span className="font-medium">{paymentOrder?.tax?.toFixed(2)}</span>
                </div>
              )}
              <div className="flex justify-between pt-2 border-t">
                <span className="font-semibold">{t('orders.total', 'Total')}</span>
                <span className="text-xl font-bold">{paymentOrder?.total?.toFixed(2)}</span>
              </div>
            </div>

            {/* Payment Method Selection */}
            <div className="space-y-2">
              <Label>{t('orders.selectPaymentMethod', 'Payment Method')}</Label>
              <div className="grid grid-cols-3 gap-3">
                <button
                  type="button"
                  onClick={() => setPaymentMethod('CASH')}
                  className={`flex flex-col items-center justify-center p-4 border-2 rounded-lg transition-all ${
                    paymentMethod === 'CASH'
                      ? 'border-green-500 bg-green-50 text-green-700'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <Banknote className="h-6 w-6 mb-2" />
                  <span className="text-sm font-medium">{t('orders.paymentMethods.CASH', 'Cash')}</span>
                </button>
                <button
                  type="button"
                  onClick={() => setPaymentMethod('CARD')}
                  className={`flex flex-col items-center justify-center p-4 border-2 rounded-lg transition-all ${
                    paymentMethod === 'CARD'
                      ? 'border-blue-500 bg-blue-50 text-blue-700'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <CreditCard className="h-6 w-6 mb-2" />
                  <span className="text-sm font-medium">{t('orders.paymentMethods.CARD', 'Card')}</span>
                </button>
                <button
                  type="button"
                  onClick={() => setPaymentMethod('ONLINE')}
                  className={`flex flex-col items-center justify-center p-4 border-2 rounded-lg transition-all ${
                    paymentMethod === 'ONLINE'
                      ? 'border-purple-500 bg-purple-50 text-purple-700'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <Wallet className="h-6 w-6 mb-2" />
                  <span className="text-sm font-medium">{t('orders.paymentMethods.ONLINE', 'Online')}</span>
                </button>
              </div>
            </div>

            {/* Cash Payment - Amount Tendered */}
            {paymentMethod === 'CASH' && (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="amountTendered">{t('orders.amountTendered', 'Amount Tendered')}</Label>
                  <Input
                    id="amountTendered"
                    type="number"
                    step="0.01"
                    min={paymentOrder?.total || 0}
                    value={amountTendered}
                    onChange={(e) => setAmountTendered(e.target.value)}
                    placeholder={paymentOrder?.total?.toFixed(2)}
                    className="text-lg"
                  />
                </div>

                {/* Quick Amount Buttons */}
                <div className="grid grid-cols-4 gap-2">
                  {[10000, 20000, 50000, 100000].map((amount) => (
                    <Button
                      key={amount}
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setAmountTendered(amount.toString())}
                    >
                      {amount.toLocaleString()}
                    </Button>
                  ))}
                </div>

                {/* Change Calculation */}
                {parseFloat(amountTendered) >= (paymentOrder?.total || 0) && (
                  <div className="bg-green-50 p-4 rounded-lg border border-green-200">
                    <div className="flex justify-between items-center">
                      <span className="font-medium text-green-800">{t('orders.changeDue', 'Change Due')}</span>
                      <span className="text-2xl font-bold text-green-700">
                        {calculateChange().toFixed(2)}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setPaymentModalOpen(false);
                setPaymentOrder(null);
              }}
              disabled={processingPayment}
            >
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button
              type="button"
              onClick={handleProcessPaymentAndClose}
              disabled={processingPayment || (paymentMethod === 'CASH' && parseFloat(amountTendered) < (paymentOrder?.total || 0))}
              className="bg-green-600 hover:bg-green-700"
            >
              {processingPayment ? t('common.processing', 'Processing...') : t('orders.completePayment', 'Complete Payment')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
