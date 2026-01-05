import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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
import {
  Plus,
  Search,
  X,
  Utensils,
  Printer,
  Edit,
  CheckCircle,
  Trash2,
  Minus,
  CreditCard,
  Banknote,
  Wallet,
  Users,
  Clock,
  RefreshCw,
  ChevronRight,
  AlertCircle,
  XCircle,
  Coffee,
  Ban
} from 'lucide-react';
import { format } from 'date-fns';
import PrintReceipt from '../components/PrintReceipt';

// Table status colors
const tableStatusColors = {
  AVAILABLE: 'bg-green-500 hover:bg-green-600',
  OCCUPIED: 'bg-orange-500 hover:bg-orange-600',
  RESERVED: 'bg-blue-500 hover:bg-blue-600',
  CLEANING: 'bg-yellow-500 hover:bg-yellow-600',
  OUT_OF_SERVICE: 'bg-gray-500 hover:bg-gray-600',
};

const tableStatusBgColors = {
  AVAILABLE: 'bg-green-50 border-green-200',
  OCCUPIED: 'bg-orange-50 border-orange-200',
  RESERVED: 'bg-blue-50 border-blue-200',
  CLEANING: 'bg-yellow-50 border-yellow-200',
  OUT_OF_SERVICE: 'bg-gray-50 border-gray-200',
};

const orderStatusColors = {
  NEW: 'bg-blue-100 text-blue-800',
  ACCEPTED: 'bg-green-100 text-green-800',
  PREPARING: 'bg-yellow-100 text-yellow-800',
  READY: 'bg-purple-100 text-purple-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
};

export default function Orders() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  // Core state
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurantId, setSelectedRestaurantId] = useState('');
  const [tables, setTables] = useState([]);
  const [tableOrders, setTableOrders] = useState({}); // Orders grouped by table ID
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Selected table state
  const [selectedTable, setSelectedTable] = useState(null);
  const [selectedTableOrders, setSelectedTableOrders] = useState([]);

  // Add item modal
  const [addItemModalOpen, setAddItemModalOpen] = useState(false);
  const [availableProducts, setAvailableProducts] = useState([]);
  const [productCategories, setProductCategories] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [newItemProductId, setNewItemProductId] = useState('');
  const [newItemQuantity, setNewItemQuantity] = useState(1);
  const [addingItem, setAddingItem] = useState(false);

  // Payment modal state
  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [paymentOrder, setPaymentOrder] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [amountTendered, setAmountTendered] = useState('');
  const [processingPayment, setProcessingPayment] = useState(false);

  // Cancel order confirmation
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [orderToCancel, setOrderToCancel] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  // Load restaurants on mount
  useEffect(() => {
    loadRestaurants();
  }, []);

  // Load tables when restaurant changes
  useEffect(() => {
    if (selectedRestaurantId) {
      loadTablesAndOrders();
    }
  }, [selectedRestaurantId]);

  // Update selected table orders when tableOrders changes
  useEffect(() => {
    if (selectedTable) {
      const orders = tableOrders[selectedTable.id] || [];
      setSelectedTableOrders(orders);
    }
  }, [tableOrders, selectedTable]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantList = response.data.data.content || [];
      setRestaurants(restaurantList);

      // Auto-select first restaurant
      if (restaurantList.length > 0) {
        setSelectedRestaurantId(restaurantList[0].id.toString());
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadTablesAndOrders = useCallback(async () => {
    if (!selectedRestaurantId) return;

    setRefreshing(true);
    try {
      // Load all tables for the restaurant
      const tablesResponse = await tablesAPI.getAll(parseInt(selectedRestaurantId));
      const tablesData = tablesResponse?.data?.data?.content || tablesResponse?.data?.data || [];
      setTables(Array.isArray(tablesData) ? tablesData : []);

      // Load open dine-in orders
      const ordersResponse = await posAPI.getOpenDineInOrders(parseInt(selectedRestaurantId));
      const openOrders = ordersResponse?.data?.data || ordersResponse?.data || [];

      // Group orders by table ID
      const ordersByTable = {};
      if (Array.isArray(openOrders)) {
        openOrders.forEach(order => {
          let tableId = null;

          // Try different sources for table ID
          if (order.dineInInfo?.tableIds?.length > 0) {
            tableId = Number(order.dineInInfo.tableIds[0]);
          } else if (order.diningTable?.id) {
            tableId = Number(order.diningTable.id);
          } else if (order.tableIds) {
            const firstTableId = String(order.tableIds).split(',')[0].trim();
            tableId = parseInt(firstTableId, 10);
          }

          if (tableId && !isNaN(tableId)) {
            if (!ordersByTable[tableId]) {
              ordersByTable[tableId] = [];
            }
            ordersByTable[tableId].push(order);
          }
        });
      }
      setTableOrders(ordersByTable);

      // Update selected table if it exists
      if (selectedTable) {
        const updatedTable = tablesData.find(t => t.id === selectedTable.id);
        if (updatedTable) {
          setSelectedTable(updatedTable);
        }
      }
    } catch (error) {
      console.error('Failed to load tables and orders:', error);
    } finally {
      setRefreshing(false);
    }
  }, [selectedRestaurantId, selectedTable]);

  // Load products for add item modal
  const loadProducts = async () => {
    if (!selectedRestaurantId) return;

    try {
      const response = await menuAPI.getProductsByRestaurant(parseInt(selectedRestaurantId));
      const products = response.data.data || [];
      setAvailableProducts(products);

      // Extract unique categories
      const categories = [...new Set(products.map(p => p.category?.name).filter(Boolean))];
      setProductCategories(categories);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  // Handle table click
  const handleTableClick = (table) => {
    setSelectedTable(table);
    setSelectedTableOrders(tableOrders[table.id] || []);
  };

  // Close table panel
  const closeTablePanel = () => {
    setSelectedTable(null);
    setSelectedTableOrders([]);
  };

  // Open add item modal
  const handleOpenAddItem = async () => {
    await loadProducts();
    setNewItemProductId('');
    setNewItemQuantity(1);
    setSelectedCategory('all');
    setAddItemModalOpen(true);
  };

  // Add item to order
  const handleAddItemToOrder = async (orderId) => {
    if (!newItemProductId || !orderId) return;

    setAddingItem(true);
    try {
      await posAPI.addItemToOrder(orderId, {
        productId: parseInt(newItemProductId),
        quantity: newItemQuantity,
        specialInstructions: ''
      });

      setAddItemModalOpen(false);
      setNewItemProductId('');
      setNewItemQuantity(1);

      // Refresh data
      await loadTablesAndOrders();
    } catch (error) {
      console.error('Failed to add item:', error);
      alert(t('orders.addItemError', 'Failed to add item: ') + (error.response?.data?.message || error.message));
    } finally {
      setAddingItem(false);
    }
  };

  // Create new order for table (only if no unclosed orders exist)
  const handleCreateOrderForTable = async () => {
    if (!selectedTable) return;

    // Check if table has unclosed orders
    const existingOrders = tableOrders[selectedTable.id] || [];
    const unclosedOrders = existingOrders.filter(o =>
      o.status !== 'CANCELLED' && o.status !== 'DELIVERED' && !o.fullyPaid
    );

    if (unclosedOrders.length > 0) {
      alert(t('orders.tableHasUnclosedOrders', 'This table has unclosed orders. Please close them first before creating a new order.'));
      return;
    }

    // Navigate to POS with table pre-selected
    localStorage.setItem('selectedRestaurantId', selectedRestaurantId);
    localStorage.setItem('preselectedTableId', selectedTable.id.toString());
    navigate('/pos?screen=tables');
  };

  // Open payment modal
  const handleOpenPayment = (order) => {
    setPaymentOrder(order);
    setPaymentMethod('CASH');
    setAmountTendered('');
    setPaymentModalOpen(true);
  };

  // Process payment and close order
  const handleProcessPayment = async () => {
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

      // Refresh data
      await loadTablesAndOrders();

      alert(t('orders.paymentSuccess', 'Payment processed successfully'));
    } catch (error) {
      console.error('Failed to process payment:', error);
      alert(t('orders.paymentError', 'Failed to process payment: ') + (error.response?.data?.message || error.message));
    } finally {
      setProcessingPayment(false);
    }
  };

  // Cancel order with table status update
  const handleCancelOrder = async () => {
    if (!orderToCancel) return;

    setCancelling(true);
    try {
      // Cancel the order
      await orderAPI.updateStatus(orderToCancel.id, 'CANCELLED', 'Order cancelled by operator');

      // If this was the only order for the table, release the table
      const tableId = orderToCancel.diningTable?.id ||
                      (orderToCancel.dineInInfo?.tableIds?.[0]) ||
                      (orderToCancel.tableIds && parseInt(String(orderToCancel.tableIds).split(',')[0]));

      if (tableId) {
        const tableOrdersList = tableOrders[tableId] || [];
        const remainingOrders = tableOrdersList.filter(o =>
          o.id !== orderToCancel.id &&
          o.status !== 'CANCELLED' &&
          o.status !== 'DELIVERED'
        );

        // If no remaining active orders, release the table
        if (remainingOrders.length === 0) {
          try {
            await tablesAPI.updateStatus(tableId, 'AVAILABLE');
          } catch (e) {
            console.error('Failed to update table status:', e);
          }
        }
      }

      setCancelModalOpen(false);
      setOrderToCancel(null);

      // Refresh data
      await loadTablesAndOrders();
    } catch (error) {
      console.error('Failed to cancel order:', error);
      alert(t('orders.cancelError', 'Failed to cancel order'));
    } finally {
      setCancelling(false);
    }
  };

  // Calculate change for cash payment
  const calculateChange = () => {
    if (!paymentOrder || paymentMethod !== 'CASH') return 0;
    const tendered = parseFloat(amountTendered) || 0;
    return Math.max(0, tendered - paymentOrder.total);
  };

  // Get table statistics
  const getTableStats = () => {
    const stats = {
      total: tables.length,
      available: tables.filter(t => t.status === 'AVAILABLE').length,
      occupied: tables.filter(t => t.status === 'OCCUPIED').length,
      reserved: tables.filter(t => t.status === 'RESERVED').length,
    };
    return stats;
  };

  // Filter products by category
  const filteredProducts = selectedCategory === 'all'
    ? availableProducts
    : availableProducts.filter(p => p.category?.name === selectedCategory);

  const stats = getTableStats();

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <RefreshCw className="h-8 w-8 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-8rem)] flex flex-col">
      {/* Header */}
      <div className="flex justify-between items-center mb-4">
        <div>
          <h1 className="text-2xl font-bold">{t('orders.tableManagement', 'Table Management')}</h1>
          <p className="text-sm text-muted-foreground">
            {t('orders.selectTableToManage', 'Select a table to view and manage orders')}
          </p>
        </div>
        <div className="flex items-center gap-3">
          {/* Restaurant Selector */}
          <Select value={selectedRestaurantId} onValueChange={setSelectedRestaurantId}>
            <SelectTrigger className="w-48">
              <SelectValue placeholder={t('orders.selectRestaurant', 'Select Restaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map((restaurant) => (
                <SelectItem key={restaurant.id} value={restaurant.id.toString()}>
                  {restaurant.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* Refresh Button */}
          <Button
            variant="outline"
            size="sm"
            onClick={loadTablesAndOrders}
            disabled={refreshing}
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${refreshing ? 'animate-spin' : ''}`} />
            {t('common.refresh', 'Refresh')}
          </Button>
        </div>
      </div>

      {/* Stats Bar */}
      <div className="grid grid-cols-4 gap-3 mb-4">
        <div className="bg-gray-50 rounded-lg p-3 text-center">
          <p className="text-2xl font-bold">{stats.total}</p>
          <p className="text-xs text-muted-foreground">{t('orders.totalTables', 'Total Tables')}</p>
        </div>
        <div className="bg-green-50 rounded-lg p-3 text-center">
          <p className="text-2xl font-bold text-green-600">{stats.available}</p>
          <p className="text-xs text-green-600">{t('orders.available', 'Available')}</p>
        </div>
        <div className="bg-orange-50 rounded-lg p-3 text-center">
          <p className="text-2xl font-bold text-orange-600">{stats.occupied}</p>
          <p className="text-xs text-orange-600">{t('orders.occupied', 'Occupied')}</p>
        </div>
        <div className="bg-blue-50 rounded-lg p-3 text-center">
          <p className="text-2xl font-bold text-blue-600">{stats.reserved}</p>
          <p className="text-xs text-blue-600">{t('orders.reserved', 'Reserved')}</p>
        </div>
      </div>

      {/* Main Content - Tables Grid + Selected Table Panel */}
      <div className="flex-1 flex gap-4 overflow-hidden">
        {/* Tables Grid */}
        <div className={`${selectedTable ? 'w-2/3' : 'w-full'} overflow-auto transition-all duration-300`}>
          {tables.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                <div className="text-center py-8">
                  <Utensils className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                  <p className="text-lg font-medium text-gray-900 mb-2">
                    {t('orders.noTables', 'No Tables Found')}
                  </p>
                  <p className="text-muted-foreground">
                    {t('orders.noTablesDesc', 'Add tables in the Restaurant settings')}
                  </p>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="grid grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
              {tables.map((table) => {
                const orders = tableOrders[table.id] || [];
                const activeOrders = orders.filter(o => o.status !== 'CANCELLED' && o.status !== 'DELIVERED');
                const hasOrders = activeOrders.length > 0;
                const isSelected = selectedTable?.id === table.id;
                const tableTotal = activeOrders.reduce((sum, o) => sum + (o.total || 0), 0);

                return (
                  <button
                    key={table.id}
                    onClick={() => handleTableClick(table)}
                    className={`
                      relative p-4 rounded-xl border-2 transition-all duration-200
                      ${isSelected ? 'ring-2 ring-blue-500 ring-offset-2' : ''}
                      ${tableStatusBgColors[table.status] || 'bg-gray-50 border-gray-200'}
                      hover:shadow-lg hover:scale-105
                    `}
                  >
                    {/* Table Icon */}
                    <div className={`
                      w-12 h-12 mx-auto rounded-lg flex items-center justify-center text-white mb-2
                      ${tableStatusColors[table.status] || 'bg-gray-500'}
                    `}>
                      <span className="text-lg font-bold">{table.tableNumber}</span>
                    </div>

                    {/* Table Info */}
                    <div className="text-center">
                      <p className="font-medium text-sm truncate">
                        {table.tableName || `Table ${table.tableNumber}`}
                      </p>
                      <p className="text-xs text-muted-foreground flex items-center justify-center gap-1">
                        <Users className="h-3 w-3" />
                        {table.capacity}
                      </p>
                    </div>

                    {/* Orders Badge */}
                    {hasOrders && (
                      <div className="absolute -top-2 -right-2 bg-red-500 text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center">
                        {activeOrders.length}
                      </div>
                    )}

                    {/* Total Amount */}
                    {hasOrders && tableTotal > 0 && (
                      <div className="mt-2 bg-white/80 rounded px-2 py-1">
                        <p className="text-sm font-bold text-gray-800">
                          {tableTotal.toLocaleString()}
                        </p>
                      </div>
                    )}

                    {/* Status Indicator */}
                    <div className="mt-2">
                      <Badge
                        variant="secondary"
                        className={`text-xs ${
                          table.status === 'AVAILABLE' ? 'bg-green-100 text-green-800' :
                          table.status === 'OCCUPIED' ? 'bg-orange-100 text-orange-800' :
                          table.status === 'RESERVED' ? 'bg-blue-100 text-blue-800' :
                          'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {t(`tables.status.${table.status?.toLowerCase()}`, table.status)}
                      </Badge>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Selected Table Panel */}
        {selectedTable && (
          <div className="w-1/3 bg-white rounded-lg border shadow-lg overflow-hidden flex flex-col">
            {/* Panel Header */}
            <div className={`p-4 ${tableStatusBgColors[selectedTable.status]}`}>
              <div className="flex justify-between items-start">
                <div>
                  <h2 className="text-xl font-bold">
                    {t('orders.table', 'Table')} {selectedTable.tableNumber}
                  </h2>
                  {selectedTable.tableName && (
                    <p className="text-sm text-muted-foreground">{selectedTable.tableName}</p>
                  )}
                  <div className="flex items-center gap-3 mt-2 text-sm">
                    <span className="flex items-center gap-1">
                      <Users className="h-4 w-4" />
                      {t('orders.capacity', 'Capacity')}: {selectedTable.capacity}
                    </span>
                    {selectedTable.section && (
                      <span>{selectedTable.section}</span>
                    )}
                  </div>
                </div>
                <button
                  onClick={closeTablePanel}
                  className="p-1 hover:bg-black/10 rounded"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {/* Table Status Badge */}
              <Badge
                className={`mt-2 ${
                  selectedTable.status === 'AVAILABLE' ? 'bg-green-500' :
                  selectedTable.status === 'OCCUPIED' ? 'bg-orange-500' :
                  selectedTable.status === 'RESERVED' ? 'bg-blue-500' :
                  'bg-gray-500'
                } text-white`}
              >
                {t(`tables.status.${selectedTable.status?.toLowerCase()}`, selectedTable.status)}
              </Badge>
            </div>

            {/* Orders Section */}
            <div className="flex-1 overflow-auto p-4">
              {selectedTableOrders.length === 0 ? (
                <div className="text-center py-8">
                  <Coffee className="h-12 w-12 mx-auto text-gray-300 mb-3" />
                  <p className="text-muted-foreground mb-4">
                    {t('orders.noOrdersForTable', 'No active orders for this table')}
                  </p>
                  {selectedTable.status === 'AVAILABLE' && (
                    <Button onClick={handleCreateOrderForTable}>
                      <Plus className="h-4 w-4 mr-2" />
                      {t('orders.createOrder', 'Create Order')}
                    </Button>
                  )}
                </div>
              ) : (
                <div className="space-y-4">
                  {selectedTableOrders.map((order) => (
                    <Card key={order.id} className="overflow-hidden">
                      <CardHeader className="py-3 bg-gray-50">
                        <div className="flex justify-between items-center">
                          <div>
                            <p className="font-bold">#{order.orderNumber}</p>
                            <div className="flex items-center gap-2 text-xs text-muted-foreground mt-1">
                              <Clock className="h-3 w-3" />
                              {order.createdAt && format(new Date(order.createdAt), 'HH:mm')}
                              {order.waiter && (
                                <>
                                  <span>•</span>
                                  <Users className="h-3 w-3" />
                                  {order.waiter.firstName || order.waiter.name}
                                </>
                              )}
                            </div>
                          </div>
                          <Badge className={orderStatusColors[order.status]}>
                            {t(`orders.statuses.${order.status}`, order.status)}
                          </Badge>
                        </div>
                      </CardHeader>
                      <CardContent className="py-3">
                        {/* Guest Count */}
                        {order.guestCount && (
                          <div className="flex items-center gap-2 text-sm mb-2">
                            <Users className="h-4 w-4 text-muted-foreground" />
                            <span>{order.guestCount} {t('orders.guests', 'guests')}</span>
                          </div>
                        )}

                        {/* Waiter Info */}
                        {order.waiter && (
                          <div className="bg-blue-50 rounded-lg p-2 mb-3">
                            <p className="text-xs text-blue-600 font-medium">{t('orders.waiter', 'Waiter')}</p>
                            <p className="font-medium">{order.waiter.firstName} {order.waiter.lastName}</p>
                            {order.waiter.phone && (
                              <p className="text-xs text-muted-foreground">{order.waiter.phone}</p>
                            )}
                          </div>
                        )}

                        {/* Order Items - Full List */}
                        {order.items && order.items.length > 0 && (
                          <div className="space-y-1 mb-3">
                            <p className="text-xs font-medium text-muted-foreground mb-2">
                              {t('orders.items', 'Items')} ({order.items.length})
                            </p>
                            <div className="max-h-40 overflow-y-auto space-y-1">
                              {order.items.map((item, idx) => (
                                <div key={idx} className="flex justify-between text-sm py-1 border-b border-gray-100 last:border-0">
                                  <div className="flex-1">
                                    <span className="font-medium">{item.quantity}x</span>{' '}
                                    <span>{item.productName}</span>
                                    {item.variantName && (
                                      <span className="text-muted-foreground"> ({item.variantName})</span>
                                    )}
                                    {item.specialInstructions && (
                                      <p className="text-xs text-orange-600 italic">{item.specialInstructions}</p>
                                    )}
                                  </div>
                                  <span className="font-medium ml-2">
                                    {(item.totalPrice || item.unitPrice * item.quantity)?.toLocaleString()}
                                  </span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {/* Customer Notes */}
                        {order.customerNotes && (
                          <div className="bg-yellow-50 rounded-lg p-2 mb-3">
                            <p className="text-xs text-yellow-700 font-medium">{t('orders.notes', 'Notes')}</p>
                            <p className="text-sm">{order.customerNotes}</p>
                          </div>
                        )}

                        {/* Order Totals */}
                        <div className="bg-gray-50 rounded-lg p-3 space-y-1">
                          {order.subtotal && order.subtotal !== order.total && (
                            <div className="flex justify-between text-sm">
                              <span className="text-muted-foreground">{t('orders.subtotal', 'Subtotal')}</span>
                              <span>{order.subtotal?.toLocaleString()}</span>
                            </div>
                          )}
                          {order.tax > 0 && (
                            <div className="flex justify-between text-sm">
                              <span className="text-muted-foreground">{t('orders.tax', 'Tax')}</span>
                              <span>{order.tax?.toLocaleString()}</span>
                            </div>
                          )}
                          {order.serviceFee > 0 && (
                            <div className="flex justify-between text-sm">
                              <span className="text-muted-foreground">{t('orders.serviceFee', 'Service Fee')}</span>
                              <span>{order.serviceFee?.toLocaleString()}</span>
                            </div>
                          )}
                          <div className="flex justify-between font-bold text-lg pt-1 border-t">
                            <span>{t('orders.total', 'Total')}</span>
                            <span>{order.total?.toLocaleString()}</span>
                          </div>
                        </div>

                        {/* Action Buttons */}
                        <div className="flex flex-wrap gap-2 mt-3">
                          {/* Print Receipt */}
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => PrintReceipt(order)}
                          >
                            <Printer className="h-4 w-4 mr-1" />
                            {t('orders.print', 'Print')}
                          </Button>

                          {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && !order.fullyPaid && (
                            <>
                              {/* Add Item */}
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={handleOpenAddItem}
                              >
                                <Plus className="h-4 w-4 mr-1" />
                                {t('orders.addItem', 'Add Item')}
                              </Button>

                              {/* Pay Button */}
                              <Button
                                size="sm"
                                className="bg-green-600 hover:bg-green-700"
                                onClick={() => handleOpenPayment(order)}
                              >
                                <CreditCard className="h-4 w-4 mr-1" />
                                {t('orders.pay', 'Pay')}
                              </Button>

                              {/* Cancel Order */}
                              <Button
                                variant="outline"
                                size="sm"
                                className="text-red-600 hover:text-red-700"
                                onClick={() => {
                                  setOrderToCancel(order);
                                  setCancelModalOpen(true);
                                }}
                              >
                                <XCircle className="h-4 w-4 mr-1" />
                                {t('orders.cancel', 'Cancel')}
                              </Button>
                            </>
                          )}

                          {(order.paymentStatus === 'COMPLETED' || order.fullyPaid) && (
                            <Badge className="bg-green-100 text-green-800">
                              <CheckCircle className="h-3 w-3 mr-1" />
                              {t('orders.paid', 'Paid')}
                            </Badge>
                          )}
                        </div>
                      </CardContent>
                    </Card>
                  ))}

                  {/* Add New Order Button (only if all orders are closed) */}
                  {selectedTableOrders.every(o =>
                    o.status === 'CANCELLED' || o.status === 'DELIVERED' || o.fullyPaid
                  ) && selectedTable.status === 'AVAILABLE' && (
                    <Button onClick={handleCreateOrderForTable} className="w-full">
                      <Plus className="h-4 w-4 mr-2" />
                      {t('orders.createNewOrder', 'Create New Order')}
                    </Button>
                  )}
                </div>
              )}
            </div>

            {/* Table Total (if multiple orders) */}
            {selectedTableOrders.filter(o => o.status !== 'CANCELLED' && !o.fullyPaid).length > 1 && (
              <div className="p-4 border-t bg-gray-50">
                <div className="flex justify-between items-center">
                  <span className="font-medium">{t('orders.tableTotal', 'Table Total')}</span>
                  <span className="text-xl font-bold">
                    {selectedTableOrders
                      .filter(o => o.status !== 'CANCELLED' && !o.fullyPaid)
                      .reduce((sum, o) => sum + (o.total || 0), 0)
                      .toLocaleString()}
                  </span>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Add Item Modal */}
      <Dialog open={addItemModalOpen} onOpenChange={setAddItemModalOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('orders.addItemToOrder', 'Add Item to Order')}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Category Filter */}
            <div>
              <Label>{t('orders.category', 'Category')}</Label>
              <Select value={selectedCategory} onValueChange={setSelectedCategory}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('orders.allCategories', 'All Categories')}</SelectItem>
                  {productCategories.map((cat) => (
                    <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Product Selection */}
            <div>
              <Label>{t('orders.product', 'Product')}</Label>
              <Select value={newItemProductId} onValueChange={setNewItemProductId}>
                <SelectTrigger>
                  <SelectValue placeholder={t('orders.selectProduct', 'Select product')} />
                </SelectTrigger>
                <SelectContent>
                  {filteredProducts.map((product) => (
                    <SelectItem key={product.id} value={product.id.toString()}>
                      {product.name} - {(product.basePrice || product.price)?.toLocaleString()}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Quantity */}
            <div>
              <Label>{t('orders.quantity', 'Quantity')}</Label>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setNewItemQuantity(Math.max(1, newItemQuantity - 1))}
                >
                  <Minus className="h-4 w-4" />
                </Button>
                <Input
                  type="number"
                  min="1"
                  value={newItemQuantity}
                  onChange={(e) => setNewItemQuantity(parseInt(e.target.value) || 1)}
                  className="w-20 text-center"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setNewItemQuantity(newItemQuantity + 1)}
                >
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setAddItemModalOpen(false)}>
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button
              onClick={() => {
                const activeOrder = selectedTableOrders.find(o =>
                  o.status !== 'CANCELLED' && o.status !== 'DELIVERED' && !o.fullyPaid
                );
                if (activeOrder) {
                  handleAddItemToOrder(activeOrder.id);
                }
              }}
              disabled={!newItemProductId || addingItem}
            >
              {addingItem ? t('common.adding', 'Adding...') : t('orders.addItem', 'Add Item')}
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
              {t('orders.order', 'Order')} #{paymentOrder?.orderNumber}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-6">
            {/* Order Summary */}
            <div className="bg-gray-50 p-4 rounded-lg">
              <div className="flex justify-between mb-2">
                <span className="text-muted-foreground">{t('orders.subtotal', 'Subtotal')}</span>
                <span>{paymentOrder?.subtotal?.toLocaleString()}</span>
              </div>
              {paymentOrder?.tax > 0 && (
                <div className="flex justify-between mb-2">
                  <span className="text-muted-foreground">{t('orders.tax', 'Tax')}</span>
                  <span>{paymentOrder?.tax?.toLocaleString()}</span>
                </div>
              )}
              <div className="flex justify-between pt-2 border-t font-bold">
                <span>{t('orders.total', 'Total')}</span>
                <span className="text-xl">{paymentOrder?.total?.toLocaleString()}</span>
              </div>
            </div>

            {/* Payment Method Selection */}
            <div>
              <Label className="mb-2 block">{t('orders.paymentMethod', 'Payment Method')}</Label>
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
                  <span className="text-sm font-medium">{t('orders.cash', 'Cash')}</span>
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
                  <span className="text-sm font-medium">{t('orders.card', 'Card')}</span>
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
                  <span className="text-sm font-medium">{t('orders.online', 'Online')}</span>
                </button>
              </div>
            </div>

            {/* Cash Payment - Amount Tendered */}
            {paymentMethod === 'CASH' && (
              <div className="space-y-4">
                <div>
                  <Label>{t('orders.amountTendered', 'Amount Tendered')}</Label>
                  <Input
                    type="number"
                    step="1000"
                    min={paymentOrder?.total || 0}
                    value={amountTendered}
                    onChange={(e) => setAmountTendered(e.target.value)}
                    placeholder={paymentOrder?.total?.toString()}
                    className="text-lg mt-1"
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
                      <span className="font-medium text-green-800">{t('orders.change', 'Change')}</span>
                      <span className="text-2xl font-bold text-green-700">
                        {calculateChange().toLocaleString()}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button
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
              onClick={handleProcessPayment}
              disabled={processingPayment || (paymentMethod === 'CASH' && parseFloat(amountTendered) < (paymentOrder?.total || 0))}
              className="bg-green-600 hover:bg-green-700"
            >
              {processingPayment
                ? t('common.processing', 'Processing...')
                : t('orders.completePayment', 'Complete Payment')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Cancel Order Confirmation Modal */}
      <Dialog open={cancelModalOpen} onOpenChange={setCancelModalOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-red-600">
              <AlertCircle className="h-5 w-5" />
              {t('orders.cancelOrderConfirm', 'Cancel Order?')}
            </DialogTitle>
            <DialogDescription>
              {t('orders.cancelOrderDesc', 'Are you sure you want to cancel order')} #{orderToCancel?.orderNumber}?
              {t('orders.cancelOrderNote', ' This action cannot be undone.')}
            </DialogDescription>
          </DialogHeader>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setCancelModalOpen(false);
                setOrderToCancel(null);
              }}
              disabled={cancelling}
            >
              {t('common.no', 'No')}
            </Button>
            <Button
              variant="destructive"
              onClick={handleCancelOrder}
              disabled={cancelling}
            >
              {cancelling
                ? t('common.cancelling', 'Cancelling...')
                : t('orders.yesCancel', 'Yes, Cancel')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
