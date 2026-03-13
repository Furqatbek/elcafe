import { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { orderAPI, restaurantAPI, menuAPI, tablesAPI, posAPI } from '../services/api';
import { useOrderNotifications, requestNotificationPermission } from '../hooks/useOrderNotifications';
import { Card, CardContent, CardHeader } from '../components/ui/card';
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
  AlertCircle,
  XCircle,
  Coffee,
  Percent,
  Check,
  ArrowRightLeft,
  DoorOpen
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

  // Service fee state
  const [showServiceFeeInput, setShowServiceFeeInput] = useState(false);
  const [serviceFeePercent, setServiceFeePercent] = useState(0);
  const [serviceFeeAmount, setServiceFeeAmount] = useState(0);
  const [serviceFeeMode, setServiceFeeMode] = useState('percent'); // 'percent' or 'amount'
  const [serviceFeePercentInput, setServiceFeePercentInput] = useState('');
  const [serviceFeeAmountInput, setServiceFeeAmountInput] = useState('');


  // Cancel order confirmation
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [orderToCancel, setOrderToCancel] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  // Edit order modal state
  const [editOrderModalOpen, setEditOrderModalOpen] = useState(false);
  const [editingOrder, setEditingOrder] = useState(null);
  const [updatingItem, setUpdatingItem] = useState(null);

  // Success notification state
  const [successMessage, setSuccessMessage] = useState('');

  // Change table modal state
  const [changeTableModalOpen, setChangeTableModalOpen] = useState(false);
  const [orderToChangeTable, setOrderToChangeTable] = useState(null);
  const [selectedNewTableId, setSelectedNewTableId] = useState('');
  const [changingTable, setChangingTable] = useState(false);

  // Split payment state
  const [splitPaymentMode, setSplitPaymentMode] = useState(false);
  const [splitCount, setSplitCount] = useState(2);
  const [splitPayments, setSplitPayments] = useState([]);
  const [currentSplitIndex, setCurrentSplitIndex] = useState(0);

  // Section filter state
  const [selectedSection, setSelectedSection] = useState('all');

  // Flatten tableOrders for notification tracking
  const allOrders = useMemo(() => {
    return Object.values(tableOrders).flat();
  }, [tableOrders]);

  // Extract unique sections from tables
  const availableSections = useMemo(() => {
    const sections = tables
      .map(t => t.section)
      .filter(Boolean);
    return [...new Set(sections)].sort();
  }, [tables]);

  // Filter tables by selected section
  const filteredTables = useMemo(() => {
    if (selectedSection === 'all') {
      return tables;
    }
    return tables.filter(t => t.section === selectedSection);
  }, [tables, selectedSection]);

  // Enable notifications for new orders
  useOrderNotifications(allOrders, {
    enabled: true,
    soundEnabled: true,
    toastEnabled: true,
    browserNotificationEnabled: true,
  });

  // Load restaurants on mount
  useEffect(() => {
    loadRestaurants();
    // Request browser notification permission
    requestNotificationPermission();
  }, []);

  // Track whether any modal is open to pause auto-refresh
  const anyModalOpen = addItemModalOpen || paymentModalOpen || cancelModalOpen || editOrderModalOpen || changeTableModalOpen;

  // Load tables when restaurant changes, with auto-refresh
  useEffect(() => {
    if (selectedRestaurantId) {
      loadTablesAndOrders();
    }
  }, [selectedRestaurantId]);

  // Auto-refresh every 5 seconds, but pause when any modal is open
  useEffect(() => {
    if (!selectedRestaurantId || anyModalOpen) return;
    const interval = setInterval(() => {
      loadTablesAndOrders();
    }, 5000);
    return () => clearInterval(interval);
  }, [selectedRestaurantId, anyModalOpen]);

  // Preload products when restaurant changes so Edit modal opens instantly
  useEffect(() => {
    if (selectedRestaurantId) {
      loadProducts();
    }
  }, [selectedRestaurantId]);

  // Update selected table orders when tableOrders changes
  useEffect(() => {
    if (selectedTable) {
      const orders = tableOrders[selectedTable.id] || [];
      setSelectedTableOrders(orders);
    }
  }, [tableOrders, selectedTable]);

  // Keep editingOrder synced with latest data from tableOrders
  useEffect(() => {
    if (editingOrder && selectedTable) {
      const orders = tableOrders[selectedTable.id] || [];
      const updatedOrder = orders.find(o => o.id === editingOrder.id);
      if (updatedOrder) {
        setEditingOrder(updatedOrder);
      }
    }
  }, [tableOrders, selectedTable?.id, editingOrder?.id]);

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
      // Sort tables by table number
      const sortedTables = (Array.isArray(tablesData) ? tablesData : []).sort((a, b) => {
        const numA = parseInt(a.tableNumber) || 0;
        const numB = parseInt(b.tableNumber) || 0;
        return numA - numB;
      });
      setTables(sortedTables);

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

      // Extract unique categories (using categoryName field from API)
      const categories = [...new Set(products.map(p => p.categoryName).filter(Boolean))];
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
  const _handleOpenAddItem = async () => {
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
      o.status !== 'CANCELLED' && o.status !== 'DELIVERED' && o.status !== 'COMPLETED' && !o.fullyPaid
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
  const handleOpenPayment = async (order) => {
    setPaymentOrder(order);
    setPaymentMethod('CASH');
    setAmountTendered('');

    // Check if order already has service fee
    const existingServiceFeePercent = order.serviceFeePercent || 0;
    const existingServiceFee = order.serviceFee || 0;
    const _subtotal = order.subtotal || 0;

    if (existingServiceFee > 0 || existingServiceFeePercent > 0) {
      // Use existing service fee
      setServiceFeePercent(existingServiceFeePercent);
      setServiceFeeAmount(existingServiceFee);
      setServiceFeePercentInput(existingServiceFeePercent.toString());
      setServiceFeeAmountInput(existingServiceFee.toString());
    } else {
      // No default service fee - user can add manually if needed
      setServiceFeePercent(0);
      setServiceFeeAmount(0);
      setServiceFeePercentInput('0');
      setServiceFeeAmountInput('0');
    }

    setShowServiceFeeInput(false);
    setServiceFeeMode('percent');

    // Reset split payment state
    setSplitPaymentMode(false);
    setSplitCount(2);
    setSplitPayments([]);
    setCurrentSplitIndex(0);

    setPaymentModalOpen(true);
  };

  // Initialize split payments when split mode is enabled or count changes
  const initializeSplitPayments = (total, count) => {
    const amountPerPerson = Math.floor(total / count);
    const remainder = total - (amountPerPerson * count);

    const splits = Array.from({ length: count }, (_, index) => ({
      personNumber: index + 1,
      amount: index === 0 ? amountPerPerson + remainder : amountPerPerson,
      paid: false,
      paymentMethod: 'CASH',
      amountTendered: ''
    }));

    setSplitPayments(splits);
    setCurrentSplitIndex(0);
  };

  // Toggle split payment mode
  const handleToggleSplitPayment = () => {
    if (!splitPaymentMode) {
      const total = calculateTotalWithFees();
      initializeSplitPayments(total, splitCount);
    }
    setSplitPaymentMode(!splitPaymentMode);
  };

  // Update split count
  const handleSplitCountChange = (newCount) => {
    if (newCount < 2) newCount = 2;
    if (newCount > 10) newCount = 10;
    setSplitCount(newCount);
    const total = calculateTotalWithFees();
    initializeSplitPayments(total, newCount);
  };

  // Update individual split amount
  const handleSplitAmountChange = (index, newAmount) => {
    const updated = [...splitPayments];
    updated[index].amount = parseFloat(newAmount) || 0;
    setSplitPayments(updated);
  };

  // Update split payment method
  const handleSplitPaymentMethodChange = (index, method) => {
    const updated = [...splitPayments];
    updated[index].paymentMethod = method;
    setSplitPayments(updated);
  };

  // Update split amount tendered
  const handleSplitAmountTenderedChange = (index, amount) => {
    const updated = [...splitPayments];
    updated[index].amountTendered = amount;
    setSplitPayments(updated);
  };

  // Apply service fee (supports both percent and fixed amount)
  const handleApplyServiceFee = async () => {
    if (!paymentOrder) return;

    const subtotal = paymentOrder.subtotal || 0;

    if (serviceFeeMode === 'percent') {
      const percent = parseFloat(serviceFeePercentInput) || 0;
      if (percent >= 0 && percent <= 100) {
        const calculatedAmount = subtotal * (percent / 100);
        setServiceFeePercent(percent);
        setServiceFeeAmount(calculatedAmount);
        setServiceFeeAmountInput(calculatedAmount.toString());
        setShowServiceFeeInput(false);

        // Save to backend
        try {
          await posAPI.applyServiceFee(paymentOrder.id, percent);
        } catch (error) {
          console.error('Failed to apply service fee:', error);
        }
      }
    } else {
      // Fixed amount mode
      const amount = parseFloat(serviceFeeAmountInput) || 0;
      if (amount >= 0) {
        const calculatedPercent = subtotal > 0 ? (amount / subtotal) * 100 : 0;
        setServiceFeeAmount(amount);
        setServiceFeePercent(parseFloat(calculatedPercent.toFixed(2)));
        setServiceFeePercentInput(calculatedPercent.toFixed(2));
        setShowServiceFeeInput(false);

        // Save to backend
        try {
          await posAPI.applyServiceFeeAmount(paymentOrder.id, amount);
        } catch (error) {
          console.error('Failed to apply service fee amount:', error);
          // Fallback to percent-based API
          try {
            await posAPI.applyServiceFee(paymentOrder.id, calculatedPercent);
          } catch (e) {
            console.error('Fallback also failed:', e);
          }
        }
      }
    }

    // Reinitialize split payments if in split mode
    if (splitPaymentMode) {
      const newTotal = calculateTotalWithFees();
      initializeSplitPayments(newTotal, splitCount);
    }
  };

  // Remove service fee
  const handleRemoveServiceFee = async () => {
    if (!paymentOrder) return;

    setServiceFeePercent(0);
    setServiceFeeAmount(0);
    setServiceFeePercentInput('0');
    setServiceFeeAmountInput('0');
    setShowServiceFeeInput(false);

    try {
      await posAPI.applyServiceFee(paymentOrder.id, 0);
    } catch (error) {
      console.error('Failed to remove service fee:', error);
    }

    // Reinitialize split payments if in split mode
    if (splitPaymentMode) {
      const newTotal = calculateTotalWithFees();
      initializeSplitPayments(newTotal, splitCount);
    }
  };

  // Process single split payment
  const handleProcessSplitPayment = async (index) => {
    if (!paymentOrder) return;

    const split = splitPayments[index];
    if (split.paid) return;

    setProcessingPayment(true);
    try {
      await posAPI.processPayment(paymentOrder.id, {
        method: split.paymentMethod,
        amount: split.amount,
        amountTendered: split.paymentMethod === 'CASH' ? parseFloat(split.amountTendered) || split.amount : null,
        isSplitPayment: true,
        splitPersonNumber: split.personNumber
      });

      // Mark this split as paid
      const updated = [...splitPayments];
      updated[index].paid = true;
      setSplitPayments(updated);

      // Check if all splits are paid
      const allPaid = updated.every(s => s.paid);
      if (allPaid) {
        // Close the order
        await posAPI.closeOrder(paymentOrder.id);
        setPaymentModalOpen(false);
        setPaymentOrder(null);
        await loadTablesAndOrders();
        alert(t('orders.paymentSuccess', 'Payment completed successfully!'));
      } else {
        // Move to next unpaid split
        const nextUnpaid = updated.findIndex(s => !s.paid);
        if (nextUnpaid >= 0) {
          setCurrentSplitIndex(nextUnpaid);
        }
      }
    } catch (error) {
      console.error('Failed to process split payment:', error);
      alert(t('orders.paymentError', 'Payment failed. Please try again.'));
    } finally {
      setProcessingPayment(false);
    }
  };

  // Process payment and close order
  const handleProcessPayment = async () => {
    if (!paymentOrder) return;

    setProcessingPayment(true);
    try {
      // Calculate final total with all fees
      const finalTotal = calculateTotalWithFees();

      // Process payment
      const paymentData = {
        method: paymentMethod,
        amount: finalTotal,
        amountTendered: paymentMethod === 'CASH' ? parseFloat(amountTendered) || finalTotal : finalTotal
      };

      await posAPI.processPayment(paymentOrder.id, paymentData);

      // Close order and release table
      await posAPI.closeOrder(paymentOrder.id);

      // Print receipt with updated totals
      const receiptData = {
        ...paymentOrder,
        serviceFeePercent: serviceFeePercent,
        serviceFee: serviceFeeAmount,
        entryFee: 0, // Explicitly set to 0 to override any backend value
        total: finalTotal
      };
      PrintReceipt(receiptData);

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

  // Release table for a fully-paid order that is still marked as occupied
  const handleReleaseTable = async (order) => {
    try {
      await posAPI.closeOrder(order.id);
      await loadTablesAndOrders();
    } catch (error) {
      console.error('Failed to release table:', error);
      alert(t('orders.releaseTableError', 'Failed to release table: ') + (error.response?.data?.message || error.message));
    }
  };

  // Cancel order with table status update
  const handleCancelOrder = async () => {
    if (!orderToCancel) return;

    setCancelling(true);
    try {
      // Cancel the order
      await orderAPI.updateStatus(orderToCancel.id, 'CANCELLED', 'Order cancelled by operator');

      // Get table ID from multiple sources
      let tableId = null;
      if (orderToCancel.dineInInfo?.tableIds?.length > 0) {
        tableId = Number(orderToCancel.dineInInfo.tableIds[0]);
      } else if (orderToCancel.diningTable?.id) {
        tableId = Number(orderToCancel.diningTable.id);
      } else if (orderToCancel.tableIds) {
        const firstTableId = String(orderToCancel.tableIds).split(',')[0].trim();
        tableId = parseInt(firstTableId, 10);
      } else if (selectedTable?.id) {
        // Fallback to currently selected table
        tableId = selectedTable.id;
      }

      if (tableId && !isNaN(tableId)) {
        const tableOrdersList = tableOrders[tableId] || [];
        const remainingOrders = tableOrdersList.filter(o =>
          o.id !== orderToCancel.id &&
          o.status !== 'CANCELLED' &&
          o.status !== 'DELIVERED' &&
          !o.fullyPaid
        );

        // If no remaining active orders, release the table
        if (remainingOrders.length === 0) {
          try {
            await tablesAPI.updateStatus(tableId, 'AVAILABLE');
            console.log('Table status updated to AVAILABLE for table:', tableId);
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

  // Open edit order modal (products already preloaded on restaurant change)
  const handleOpenEditOrder = (order) => {
    setEditingOrder(order);
    setNewItemProductId('');
    setNewItemQuantity(1);
    setSelectedCategory('all');
    setEditOrderModalOpen(true);
  };

  // Update item quantity with optimistic UI
  const handleUpdateItemQuantity = async (orderId, itemId, newQuantity) => {
    if (newQuantity < 1) return;

    // Optimistic update: immediately update local state
    if (editingOrder && editingOrder.id === orderId) {
      const optimisticOrder = {
        ...editingOrder,
        items: editingOrder.items?.map(item =>
          item.id === itemId
            ? { ...item, quantity: newQuantity, totalPrice: (item.unitPrice || 0) * newQuantity }
            : item
        ),
      };
      setEditingOrder(optimisticOrder);
    }

    setUpdatingItem(itemId);
    try {
      await posAPI.updateItemQuantity(orderId, itemId, newQuantity);
      // Background sync - don't block UI
      loadTablesAndOrders().then(() => {
        if (editingOrder && editingOrder.id === orderId) {
          const updatedOrders = tableOrders[selectedTable?.id] || [];
          const updatedOrder = updatedOrders.find(o => o.id === orderId);
          if (updatedOrder) {
            setEditingOrder(updatedOrder);
          }
        }
      });
    } catch (error) {
      console.error('Failed to update item quantity:', error);
      // Revert optimistic update on failure
      loadTablesAndOrders();
      alert(t('orders.updateItemError', 'Failed to update item quantity'));
    } finally {
      setUpdatingItem(null);
    }
  };

  // Remove item from order with optimistic UI
  const handleRemoveItem = async (orderId, itemId) => {
    if (!confirm(t('orders.confirmRemoveItem', 'Are you sure you want to remove this item?'))) {
      return;
    }

    // Optimistic update: immediately remove item from local state
    const previousOrder = editingOrder;
    if (editingOrder && editingOrder.id === orderId) {
      const remainingItems = editingOrder.items?.filter(item => item.id !== itemId);
      if (remainingItems && remainingItems.length > 0) {
        setEditingOrder({ ...editingOrder, items: remainingItems });
      } else {
        setEditOrderModalOpen(false);
        setEditingOrder(null);
      }
    }

    setUpdatingItem(itemId);
    try {
      await posAPI.removeItemFromOrder(orderId, itemId);
      // Background sync
      loadTablesAndOrders().then(() => {
        if (editingOrder && editingOrder.id === orderId) {
          const updatedOrders = tableOrders[selectedTable?.id] || [];
          const updatedOrder = updatedOrders.find(o => o.id === orderId);
          if (updatedOrder) {
            setEditingOrder(updatedOrder);
          }
        }
      });
    } catch (error) {
      console.error('Failed to remove item:', error);
      // Revert optimistic update on failure
      if (previousOrder) {
        setEditingOrder(previousOrder);
        setEditOrderModalOpen(true);
      }
      loadTablesAndOrders();
      alert(t('orders.removeItemError', 'Failed to remove item'));
    } finally {
      setUpdatingItem(null);
    }
  };

  // Show success notification helper
  const showSuccessNotification = (message) => {
    setSuccessMessage(message);
    setTimeout(() => setSuccessMessage(''), 3000);
  };

  // Open change table modal
  const handleOpenChangeTable = (order) => {
    setOrderToChangeTable(order);
    setSelectedNewTableId('');
    setChangeTableModalOpen(true);
  };

  // Handle change table
  const handleChangeTable = async () => {
    if (!orderToChangeTable || !selectedNewTableId) return;

    setChangingTable(true);
    try {
      await posAPI.changeTable(orderToChangeTable.id, parseInt(selectedNewTableId));
      setChangeTableModalOpen(false);
      setOrderToChangeTable(null);
      setSelectedNewTableId('');

      // Refresh data
      await loadTablesAndOrders();
      showSuccessNotification(t('orders.tableChanged', 'Order moved to new table successfully'));
    } catch (error) {
      console.error('Failed to change table:', error);
      alert(t('orders.changeTableError', 'Failed to change table: ') + (error.response?.data?.message || error.message));
    } finally {
      setChangingTable(false);
    }
  };

  // Get available tables for change table modal
  const getAvailableTables = () => {
    if (!orderToChangeTable) return [];
    const currentTableId = orderToChangeTable.dineInInfo?.tableIds?.[0] || orderToChangeTable.diningTable?.id;
    return tables.filter(t =>
      t.status === 'AVAILABLE' || t.id === currentTableId
    );
  };

  // Add item from edit modal
  const handleAddItemFromEditModal = async () => {
    if (!newItemProductId || !editingOrder) return;

    const selectedProduct = availableProducts.find(p => p.id.toString() === newItemProductId);
    setAddingItem(true);
    try {
      await posAPI.addItemToOrder(editingOrder.id, {
        productId: parseInt(newItemProductId),
        quantity: newItemQuantity,
        specialInstructions: ''
      });

      setNewItemProductId('');
      setNewItemQuantity(1);

      // Show success notification
      showSuccessNotification(t('orders.itemAddedSuccess', '{{name}} added successfully!', { name: selectedProduct?.name || 'Item' }));

      // Background sync - don't block UI
      loadTablesAndOrders().then(() => {
        const updatedOrders = tableOrders[selectedTable?.id] || [];
        const updatedOrder = updatedOrders.find(o => o.id === editingOrder.id);
        if (updatedOrder) {
          setEditingOrder(updatedOrder);
        }
      });
    } catch (error) {
      console.error('Failed to add item:', error);
      alert(t('orders.addItemError', 'Failed to add item: ') + (error.response?.data?.message || error.message));
    } finally {
      setAddingItem(false);
    }
  };

  // Calculate total with service fee
  const calculateTotalWithFees = () => {
    if (!paymentOrder) return 0;
    const subtotal = paymentOrder.subtotal || 0;
    const tax = paymentOrder.tax || 0;
    return subtotal + tax + serviceFeeAmount;
  };

  // Calculate change for cash payment
  const calculateChange = () => {
    if (!paymentOrder || paymentMethod !== 'CASH') return 0;
    const tendered = parseFloat(amountTendered) || 0;
    const totalWithFees = calculateTotalWithFees();
    return Math.max(0, tendered - totalWithFees);
  };

  // Get table statistics (based on filtered tables)
  const getTableStats = () => {
    const stats = {
      total: filteredTables.length,
      available: filteredTables.filter(t => t.status === 'AVAILABLE').length,
      occupied: filteredTables.filter(t => t.status === 'OCCUPIED').length,
      reserved: filteredTables.filter(t => t.status === 'RESERVED').length,
    };
    return stats;
  };

  // Filter products by category (memoized to avoid re-computation on every render)
  const filteredProducts = useMemo(() => {
    return selectedCategory === 'all'
      ? availableProducts
      : availableProducts.filter(p => p.categoryName === selectedCategory);
  }, [availableProducts, selectedCategory]);

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

          {/* Section Filter */}
          {availableSections.length > 0 && (
            <Select value={selectedSection} onValueChange={setSelectedSection}>
              <SelectTrigger className="w-40">
                <SelectValue placeholder={t('orders.selectSection', 'Select Section')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t('orders.allSections', 'All Sections')}</SelectItem>
                {availableSections.map((section) => (
                  <SelectItem key={section} value={section}>
                    {section}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}

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
          {filteredTables.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                <div className="text-center py-8">
                  <Utensils className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                  <p className="text-lg font-medium text-gray-900 mb-2">
                    {selectedSection !== 'all'
                      ? t('orders.noTablesInSection', 'No Tables in This Section')
                      : t('orders.noTables', 'No Tables Found')}
                  </p>
                  <p className="text-muted-foreground">
                    {selectedSection !== 'all'
                      ? t('orders.tryAnotherSection', 'Try selecting a different section')
                      : t('orders.noTablesDesc', 'Add tables in the Restaurant settings')}
                  </p>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="grid grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
              {filteredTables.map((table) => {
                const orders = tableOrders[table.id] || [];
                const activeOrders = orders.filter(o => o.status !== 'CANCELLED' && o.status !== 'DELIVERED' && o.status !== 'COMPLETED');
                const hasOrders = activeOrders.length > 0;
                const isSelected = selectedTable?.id === table.id;
                const tableTotal = activeOrders.reduce((sum, o) => sum + (o.total || 0), 0);
                const totalGuests = activeOrders.reduce((sum, o) => sum + (o.guestCount || 0), 0);

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
                        {table.tableName || table.tableNumber}
                      </p>
                      <p className="text-xs text-muted-foreground flex items-center justify-center gap-1">
                        <Users className="h-3 w-3" />
                        {hasOrders && totalGuests > 0 ? (
                          <span>{totalGuests}/{table.capacity}</span>
                        ) : (
                          <span>{table.capacity}</span>
                        )}
                      </p>
                      {table.section && (
                        <p className="text-xs text-muted-foreground mt-1 truncate">
                          {table.section}
                        </p>
                      )}
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
                            onClick={() => PrintReceipt({ ...order, entryFee: 0 })}
                          >
                            <Printer className="h-4 w-4 mr-1" />
                            {t('orders.print', 'Print')}
                          </Button>

                          {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && order.status !== 'COMPLETED' && !order.fullyPaid && (
                            <>
                              {/* Edit Order */}
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleOpenEditOrder(order)}
                              >
                                <Edit className="h-4 w-4 mr-1" />
                                {t('orders.editOrder', 'Edit Order')}
                              </Button>

                              {/* Change Table */}
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleOpenChangeTable(order)}
                              >
                                <ArrowRightLeft className="h-4 w-4 mr-1" />
                                {t('orders.changeTable', 'Change Table')}
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
                            <>
                              <Badge className="bg-green-100 text-green-800">
                                <CheckCircle className="h-3 w-3 mr-1" />
                                {t('orders.paid', 'Paid')}
                              </Badge>
                              <Button
                                size="sm"
                                variant="outline"
                                className="text-orange-600 border-orange-300 hover:bg-orange-50"
                                onClick={() => handleReleaseTable(order)}
                              >
                                <DoorOpen className="h-4 w-4 mr-1" />
                                {t('orders.releaseTable', 'Release Table')}
                              </Button>
                            </>
                          )}
                        </div>
                      </CardContent>
                    </Card>
                  ))}

                  {/* Add New Order Button (only if all orders are closed) */}
                  {selectedTableOrders.every(o =>
                    o.status === 'CANCELLED' || o.status === 'DELIVERED' || o.status === 'COMPLETED' || o.fullyPaid
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
        <DialogContent className="w-[95vw] max-w-md">
          <DialogHeader>
            <DialogTitle>{t('orders.addItemToOrder', 'Add Item to Order')}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Category Dropdown */}
            <div>
              <Label className="mb-2 block">{t('orders.category', 'Category')}</Label>
              <Select value={selectedCategory} onValueChange={setSelectedCategory}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder={t('orders.selectCategory', 'Select category')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('orders.allCategories', 'All Categories')}</SelectItem>
                  {productCategories.map((cat) => (
                    <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Product Dropdown */}
            <div>
              <Label className="mb-2 block">{t('orders.product', 'Product')}</Label>
              <Select value={newItemProductId} onValueChange={setNewItemProductId}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder={t('orders.selectProduct', 'Select product')} />
                </SelectTrigger>
                <SelectContent className="max-h-60">
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
              <Label className="mb-2 block">{t('orders.quantity', 'Quantity')}</Label>
              <div className="flex items-center gap-3">
                <Button
                  type="button"
                  variant="outline"
                  size="lg"
                  onClick={() => setNewItemQuantity(Math.max(1, newItemQuantity - 1))}
                  className="h-12 w-12"
                >
                  <Minus className="h-5 w-5" />
                </Button>
                <Input
                  type="number"
                  min="1"
                  value={newItemQuantity}
                  onChange={(e) => setNewItemQuantity(parseInt(e.target.value) || 1)}
                  className="w-20 h-12 text-center text-xl font-bold"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="lg"
                  onClick={() => setNewItemQuantity(newItemQuantity + 1)}
                  className="h-12 w-12"
                >
                  <Plus className="h-5 w-5" />
                </Button>
              </div>
            </div>
          </div>

          <DialogFooter className="flex-col sm:flex-row gap-2">
            <Button variant="outline" onClick={() => setAddItemModalOpen(false)} className="w-full sm:w-auto">
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button
              onClick={() => {
                const activeOrder = selectedTableOrders.find(o =>
                  o.status !== 'CANCELLED' && o.status !== 'DELIVERED' && o.status !== 'COMPLETED' && !o.fullyPaid
                );
                if (activeOrder) {
                  handleAddItemToOrder(activeOrder.id);
                }
              }}
              disabled={!newItemProductId || addingItem}
              className="w-full sm:w-auto h-12"
              size="lg"
            >
              {addingItem ? (
                <>
                  <RefreshCw className="h-5 w-5 mr-2 animate-spin" />
                  {t('common.adding', 'Adding...')}
                </>
              ) : (
                <>
                  <Plus className="h-5 w-5 mr-2" />
                  {t('orders.addItem', 'Add Item')}
                </>
              )}
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
              {serviceFeeAmount > 0 && (
                <div className="flex justify-between mb-2 text-purple-700">
                  <span>{t('orders.serviceFee', 'Service Fee')} ({serviceFeePercent}%)</span>
                  <span>{serviceFeeAmount?.toLocaleString()}</span>
                </div>
              )}
              <div className="flex justify-between pt-2 border-t font-bold">
                <span>{t('orders.total', 'Total')}</span>
                <span className="text-xl">{calculateTotalWithFees().toLocaleString()}</span>
              </div>
            </div>

            {/* Adjustable Service Fee */}
            {!showServiceFeeInput ? (
              <div
                className="bg-purple-50 border border-purple-200 rounded-lg p-3 flex items-center gap-3 cursor-pointer hover:bg-purple-100 transition-colors"
                onClick={() => setShowServiceFeeInput(true)}
              >
                <Percent className="h-5 w-5 text-purple-600" />
                <div className="flex-1">
                  <p className="text-sm font-medium text-purple-800">
                    {t('orders.serviceFee', 'Service Fee')} ({serviceFeePercent}%)
                  </p>
                  <p className="text-xs text-purple-600">
                    {t('orders.clickToEdit', 'Click to edit')}
                  </p>
                </div>
                <span className="font-bold text-purple-700">{serviceFeeAmount?.toLocaleString()}</span>
              </div>
            ) : (
              <div className="bg-purple-50 border-2 border-purple-300 rounded-lg p-4 space-y-3">
                {/* Mode Toggle */}
                <div className="flex gap-1 bg-purple-100 rounded-lg p-1">
                  <button
                    type="button"
                    onClick={() => setServiceFeeMode('percent')}
                    className={`flex-1 py-1.5 px-2 rounded-md text-sm font-medium transition-colors ${
                      serviceFeeMode === 'percent'
                        ? 'bg-white text-purple-700 shadow-sm'
                        : 'text-purple-600 hover:text-purple-800'
                    }`}
                  >
                    {t('orders.percent', 'Percent')}
                  </button>
                  <button
                    type="button"
                    onClick={() => setServiceFeeMode('amount')}
                    className={`flex-1 py-1.5 px-2 rounded-md text-sm font-medium transition-colors ${
                      serviceFeeMode === 'amount'
                        ? 'bg-white text-purple-700 shadow-sm'
                        : 'text-purple-600 hover:text-purple-800'
                    }`}
                  >
                    {t('orders.fixedAmount', 'Fixed Amount')}
                  </button>
                </div>

                {/* Input Field */}
                {serviceFeeMode === 'percent' ? (
                  <div>
                    <Label className="text-sm font-semibold text-purple-800 mb-1">
                      {t('orders.serviceFeePercent', 'Service Fee %')}
                    </Label>
                    <div className="flex gap-2 items-center">
                      <Input
                        type="number"
                        min="0"
                        max="100"
                        step="0.5"
                        value={serviceFeePercentInput}
                        onChange={(e) => setServiceFeePercentInput(e.target.value)}
                        className="flex-1 text-center font-semibold"
                        placeholder="0"
                      />
                      <span className="text-lg font-bold text-purple-700">%</span>
                    </div>
                    {parseFloat(serviceFeePercentInput) > 0 && (
                      <p className="text-xs text-purple-600 mt-1">
                        = {((parseFloat(serviceFeePercentInput) || 0) / 100 * (paymentOrder?.subtotal || 0)).toLocaleString()}
                      </p>
                    )}
                  </div>
                ) : (
                  <div>
                    <Label className="text-sm font-semibold text-purple-800 mb-1">
                      {t('orders.serviceFeeAmount', 'Service Fee Amount')}
                    </Label>
                    <Input
                      type="number"
                      min="0"
                      step="1000"
                      value={serviceFeeAmountInput}
                      onChange={(e) => setServiceFeeAmountInput(e.target.value)}
                      className="w-full text-center font-semibold"
                      placeholder="0"
                    />
                  </div>
                )}

                {/* Action Buttons */}
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    onClick={handleApplyServiceFee}
                    className="flex-1"
                  >
                    {t('common.apply', 'Apply')}
                  </Button>
                  {serviceFeeAmount > 0 && (
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      onClick={handleRemoveServiceFee}
                    >
                      {t('common.remove', 'Remove')}
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => setShowServiceFeeInput(false)}
                  >
                    {t('common.cancel', 'Cancel')}
                  </Button>
                </div>
              </div>
            )}

            {/* Split Payment Toggle */}
            <div className="border rounded-lg p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Users className="h-5 w-5 text-blue-600" />
                  <span className="font-medium">{t('orders.splitPayment', 'Split Payment')}</span>
                </div>
                <Button
                  variant={splitPaymentMode ? 'default' : 'outline'}
                  size="sm"
                  onClick={handleToggleSplitPayment}
                >
                  {splitPaymentMode ? t('orders.splitEnabled', 'Enabled') : t('orders.splitDisabled', 'Disabled')}
                </Button>
              </div>

              {/* Split Payment Controls */}
              {splitPaymentMode && (
                <div className="mt-4 space-y-4">
                  {/* Split Count */}
                  <div className="flex items-center gap-3">
                    <Label className="whitespace-nowrap">{t('orders.splitBetween', 'Split between')}:</Label>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleSplitCountChange(splitCount - 1)}
                        disabled={splitCount <= 2}
                      >
                        <Minus className="h-4 w-4" />
                      </Button>
                      <span className="w-8 text-center font-bold text-lg">{splitCount}</span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleSplitCountChange(splitCount + 1)}
                        disabled={splitCount >= 10}
                      >
                        <Plus className="h-4 w-4" />
                      </Button>
                    </div>
                    <span className="text-muted-foreground">{t('orders.people', 'people')}</span>
                  </div>

                  {/* Split Details */}
                  <div className="space-y-3 max-h-60 overflow-y-auto">
                    {splitPayments.map((split, index) => (
                      <div
                        key={index}
                        className={`p-3 rounded-lg border-2 ${
                          split.paid
                            ? 'bg-green-50 border-green-300'
                            : currentSplitIndex === index
                            ? 'bg-blue-50 border-blue-300'
                            : 'bg-gray-50 border-gray-200'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-2">
                          <span className="font-medium">
                            {t('orders.person', 'Person')} {split.personNumber}
                          </span>
                          {split.paid ? (
                            <Badge className="bg-green-100 text-green-800">
                              <CheckCircle className="h-3 w-3 mr-1" />
                              {t('orders.paid', 'Paid')}
                            </Badge>
                          ) : (
                            <Badge variant="outline">{t('orders.pending', 'Pending')}</Badge>
                          )}
                        </div>

                        {!split.paid && (
                          <>
                            {/* Amount */}
                            <div className="flex items-center gap-2 mb-2">
                              <Label className="text-sm whitespace-nowrap">{t('orders.amount', 'Amount')}:</Label>
                              <Input
                                type="number"
                                value={split.amount}
                                onChange={(e) => handleSplitAmountChange(index, e.target.value)}
                                className="w-32 text-right font-medium"
                              />
                            </div>

                            {/* Payment Method for this split */}
                            <div className="flex gap-2 mb-2">
                              {['CASH', 'CARD', 'ONLINE'].map((method) => (
                                <Button
                                  key={method}
                                  variant={split.paymentMethod === method ? 'default' : 'outline'}
                                  size="sm"
                                  onClick={() => handleSplitPaymentMethodChange(index, method)}
                                  className="flex-1"
                                >
                                  {method === 'CASH' && <Banknote className="h-3 w-3 mr-1" />}
                                  {method === 'CARD' && <CreditCard className="h-3 w-3 mr-1" />}
                                  {method === 'ONLINE' && <Wallet className="h-3 w-3 mr-1" />}
                                  {t(`orders.${method.toLowerCase()}`, method)}
                                </Button>
                              ))}
                            </div>

                            {/* Amount tendered for cash */}
                            {split.paymentMethod === 'CASH' && (
                              <div className="flex items-center gap-2 mb-2">
                                <Label className="text-sm whitespace-nowrap">{t('orders.tendered', 'Tendered')}:</Label>
                                <Input
                                  type="number"
                                  value={split.amountTendered}
                                  onChange={(e) => handleSplitAmountTenderedChange(index, e.target.value)}
                                  placeholder={split.amount.toString()}
                                  className="w-32 text-right"
                                />
                                {parseFloat(split.amountTendered) > split.amount && (
                                  <span className="text-green-600 font-medium text-sm">
                                    {t('orders.change', 'Change')}: {(parseFloat(split.amountTendered) - split.amount).toLocaleString()}
                                  </span>
                                )}
                              </div>
                            )}

                            {/* Pay button */}
                            <Button
                              onClick={() => handleProcessSplitPayment(index)}
                              disabled={processingPayment}
                              className="w-full bg-green-600 hover:bg-green-700"
                              size="sm"
                            >
                              {processingPayment && currentSplitIndex === index
                                ? t('common.processing', 'Processing...')
                                : t('orders.payAmount', 'Pay {{amount}}', { amount: split.amount.toLocaleString() })}
                            </Button>
                          </>
                        )}

                        {split.paid && (
                          <p className="text-sm text-green-700">
                            {t('orders.paidWith', 'Paid with {{method}}', {
                              method: t(`orders.${split.paymentMethod.toLowerCase()}`, split.paymentMethod)
                            })}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>

                  {/* Progress indicator */}
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">
                      {t('orders.splitProgress', 'Progress')}: {splitPayments.filter(s => s.paid).length}/{splitPayments.length}
                    </span>
                    <span className="font-medium">
                      {t('orders.remaining', 'Remaining')}: {splitPayments.filter(s => !s.paid).reduce((sum, s) => sum + s.amount, 0).toLocaleString()}
                    </span>
                  </div>
                </div>
              )}
            </div>

            {/* Regular Payment Method Selection (only when not in split mode) */}
            {!splitPaymentMode && (
              <>
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
                    min={calculateTotalWithFees()}
                    value={amountTendered}
                    onChange={(e) => setAmountTendered(e.target.value)}
                    placeholder={calculateTotalWithFees().toString()}
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
                {parseFloat(amountTendered) >= calculateTotalWithFees() && (
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
              </>
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
            {!splitPaymentMode && (
              <Button
                onClick={handleProcessPayment}
                disabled={processingPayment || (paymentMethod === 'CASH' && parseFloat(amountTendered) < calculateTotalWithFees())}
                className="bg-green-600 hover:bg-green-700"
              >
                {processingPayment
                  ? t('common.processing', 'Processing...')
                  : t('orders.completePayment', 'Complete Payment')}
              </Button>
            )}
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

      {/* Edit Order Modal */}
      <Dialog open={editOrderModalOpen} onOpenChange={setEditOrderModalOpen}>
        <DialogContent className="w-[95vw] max-w-3xl h-[90vh] max-h-[700px] flex flex-col p-0">
          <DialogHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <DialogTitle className="flex items-center gap-2">
              <Edit className="h-5 w-5" />
              {t('orders.editOrder', 'Edit Order')} #{editingOrder?.orderNumber}
            </DialogTitle>
            <DialogDescription>
              {t('orders.editOrderDesc', 'Add, remove, or modify items in this order')}
            </DialogDescription>
          </DialogHeader>

          {/* Success Notification */}
          {successMessage && (
            <div className="mx-6 mt-4 bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2 animate-in fade-in slide-in-from-top-2">
              <Check className="h-5 w-5 text-green-600" />
              <span className="text-green-800 font-medium">{successMessage}</span>
            </div>
          )}

          <div className="flex-1 overflow-hidden flex flex-col md:flex-row">
            {/* Left Side - Add New Item Section */}
            <div className="md:w-1/2 p-6 border-b md:border-b-0 md:border-r bg-gray-50 shrink-0">
              <h4 className="font-medium mb-4 flex items-center gap-2 text-lg">
                <Plus className="h-5 w-5" />
                {t('orders.addNewItem', 'Add New Item')}
              </h4>

              {/* Category Dropdown */}
              <div className="mb-4">
                <Label className="mb-2 block">{t('orders.category', 'Category')}</Label>
                <Select value={selectedCategory} onValueChange={setSelectedCategory}>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder={t('orders.selectCategory', 'Select category')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">{t('orders.allCategories', 'All Categories')}</SelectItem>
                    {[...new Set(availableProducts.map(p => p.categoryName).filter(Boolean))].sort((a, b) => a.localeCompare(b)).map((category) => (
                      <SelectItem key={category} value={category}>{category}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Product Dropdown */}
              <div className="mb-4">
                <Label className="mb-2 block">{t('orders.product', 'Product')}</Label>
                <Select value={newItemProductId} onValueChange={setNewItemProductId}>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder={t('orders.selectProduct', 'Select a product')} />
                  </SelectTrigger>
                  <SelectContent className="max-h-60">
                    {(selectedCategory === 'all'
                      ? availableProducts
                      : availableProducts.filter(p => p.categoryName === selectedCategory)
                    ).map((product) => (
                      <SelectItem key={product.id} value={product.id.toString()}>
                        <span className="flex justify-between items-center w-full gap-4">
                          <span>{product.name}</span>
                          <span className="text-muted-foreground">{(product.basePrice || product.price)?.toLocaleString()}</span>
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Quantity */}
              <div className="mb-4">
                <Label className="mb-2 block">{t('orders.quantity', 'Quantity')}</Label>
                <div className="flex items-center gap-3">
                  <Button
                    variant="outline"
                    size="lg"
                    onClick={() => setNewItemQuantity(Math.max(1, newItemQuantity - 1))}
                    className="h-12 w-12"
                  >
                    <Minus className="h-5 w-5" />
                  </Button>
                  <Input
                    type="number"
                    min="1"
                    value={newItemQuantity}
                    onChange={(e) => setNewItemQuantity(parseInt(e.target.value) || 1)}
                    className="w-20 h-12 text-center text-xl font-bold"
                  />
                  <Button
                    variant="outline"
                    size="lg"
                    onClick={() => setNewItemQuantity(newItemQuantity + 1)}
                    className="h-12 w-12"
                  >
                    <Plus className="h-5 w-5" />
                  </Button>
                </div>
              </div>

              {/* Add Button */}
              <Button
                onClick={handleAddItemFromEditModal}
                disabled={!newItemProductId || addingItem}
                className="w-full h-12 text-lg"
                size="lg"
              >
                {addingItem ? (
                  <>
                    <RefreshCw className="h-5 w-5 mr-2 animate-spin" />
                    {t('common.adding', 'Adding...')}
                  </>
                ) : (
                  <>
                    <Plus className="h-5 w-5 mr-2" />
                    {t('orders.addItem', 'Add Item')}
                  </>
                )}
              </Button>
            </div>

            {/* Right Side - Current Order Items */}
            <div className="md:w-1/2 p-6 flex flex-col min-h-0">
              <h4 className="font-medium mb-4 flex items-center gap-2 text-lg shrink-0">
                <Utensils className="h-5 w-5" />
                {t('orders.currentItems', 'Current Items')} ({editingOrder?.items?.length || 0})
              </h4>

              <div className="flex-1 overflow-y-auto space-y-2 min-h-0">
                {editingOrder?.items?.map((item) => (
                  <div
                    key={item.id}
                    className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                  >
                    <div className="flex-1 min-w-0 mr-2">
                      <p className="font-medium truncate">{item.productName}</p>
                      {item.variantName && (
                        <p className="text-sm text-muted-foreground truncate">{item.variantName}</p>
                      )}
                      <p className="text-sm text-green-600 font-medium">
                        {(item.totalPrice || item.unitPrice * item.quantity)?.toLocaleString()}
                      </p>
                    </div>

                    {/* Quantity Controls */}
                    <div className="flex items-center gap-1 shrink-0">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleUpdateItemQuantity(editingOrder.id, item.id, item.quantity - 1)}
                        disabled={item.quantity <= 1 || updatingItem === item.id}
                        className="h-8 w-8 p-0"
                      >
                        <Minus className="h-4 w-4" />
                      </Button>
                      <span className="w-8 text-center font-medium">
                        {updatingItem === item.id ? '...' : item.quantity}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleUpdateItemQuantity(editingOrder.id, item.id, item.quantity + 1)}
                        disabled={updatingItem === item.id}
                        className="h-8 w-8 p-0"
                      >
                        <Plus className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-red-600 hover:text-red-700 hover:bg-red-50 h-8 w-8 p-0"
                        onClick={() => handleRemoveItem(editingOrder.id, item.id)}
                        disabled={updatingItem === item.id}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))}

                {(!editingOrder?.items || editingOrder.items.length === 0) && (
                  <div className="text-center text-muted-foreground py-8">
                    <Utensils className="h-12 w-12 mx-auto mb-3 opacity-30" />
                    <p>{t('orders.noItems', 'No items in this order')}</p>
                  </div>
                )}
              </div>

              {/* Order Total */}
              {editingOrder?.items?.length > 0 && (
                <div className="flex justify-between items-center mt-4 pt-4 border-t shrink-0">
                  <span className="font-medium text-lg">{t('orders.subtotal', 'Subtotal')}</span>
                  <span className="text-2xl font-bold">{editingOrder?.subtotal?.toLocaleString()}</span>
                </div>
              )}
            </div>
          </div>

          <DialogFooter className="px-6 py-4 border-t shrink-0">
            <Button
              variant="outline"
              onClick={() => {
                setEditOrderModalOpen(false);
                setEditingOrder(null);
                setSuccessMessage('');
              }}
            >
              {t('common.close', 'Close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Change Table Modal */}
      <Dialog open={changeTableModalOpen} onOpenChange={setChangeTableModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ArrowRightLeft className="h-5 w-5" />
              {t('orders.changeTable', 'Change Table')}
            </DialogTitle>
            <DialogDescription>
              {t('orders.changeTableDesc', 'Move this order to a different table')}
            </DialogDescription>
          </DialogHeader>

          <div className="py-4">
            <Label className="mb-3 block">{t('orders.selectNewTable', 'Select New Table')}</Label>
            <div className="grid grid-cols-4 gap-2 max-h-64 overflow-y-auto">
              {getAvailableTables().map((table) => {
                const currentTableId = orderToChangeTable?.dineInInfo?.tableIds?.[0] || orderToChangeTable?.diningTable?.id;
                const isCurrentTable = table.id === currentTableId;
                const isSelected = selectedNewTableId === table.id.toString();

                return (
                  <button
                    key={table.id}
                    onClick={() => setSelectedNewTableId(table.id.toString())}
                    disabled={isCurrentTable}
                    className={`
                      p-3 rounded-lg border-2 transition-all text-center
                      ${isSelected ? 'border-blue-500 bg-blue-50' : 'border-gray-200'}
                      ${isCurrentTable ? 'opacity-50 cursor-not-allowed bg-orange-50' : 'hover:border-blue-300 cursor-pointer'}
                    `}
                  >
                    <div className={`
                      w-10 h-10 mx-auto rounded-lg flex items-center justify-center text-white mb-1
                      ${isCurrentTable ? 'bg-orange-500' : isSelected ? 'bg-blue-500' : 'bg-green-500'}
                    `}>
                      <span className="font-bold">{table.tableNumber}</span>
                    </div>
                    <p className="text-xs truncate">{table.tableName || `Table ${table.tableNumber}`}</p>
                    <p className="text-xs text-muted-foreground">
                      <Users className="h-3 w-3 inline mr-1" />
                      {table.capacity}
                    </p>
                    {isCurrentTable && (
                      <Badge variant="secondary" className="text-xs mt-1">
                        {t('orders.current', 'Current')}
                      </Badge>
                    )}
                  </button>
                );
              })}
            </div>

            {getAvailableTables().length === 0 && (
              <div className="text-center py-8 text-muted-foreground">
                <AlertCircle className="h-12 w-12 mx-auto mb-3 opacity-50" />
                <p>{t('orders.noAvailableTables', 'No available tables')}</p>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setChangeTableModalOpen(false);
                setOrderToChangeTable(null);
                setSelectedNewTableId('');
              }}
              disabled={changingTable}
            >
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button
              onClick={handleChangeTable}
              disabled={changingTable || !selectedNewTableId}
            >
              {changingTable ? (
                <>
                  <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                  {t('common.moving', 'Moving...')}
                </>
              ) : (
                <>
                  <ArrowRightLeft className="h-4 w-4 mr-2" />
                  {t('orders.moveToTable', 'Move to Table')}
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
