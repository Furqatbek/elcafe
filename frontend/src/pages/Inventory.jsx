import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { inventoryAPI, inventoryBatchAPI, restaurantAPI, menuAPI, stockAlertAPI, supplierAPI, stockCountAPI, wasteAPI } from '../services/api';
import { formatDateTime } from '../utils/dateUtils';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
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
  Package,
  Plus,
  Edit,
  Trash2,
  AlertTriangle,
  TrendingDown,
  CheckCircle,
  Search,
  RefreshCw,
  TrendingUp,
  History,
  UtensilsCrossed,
  Bell,
  Send,
  Power,
  Link2,
  Truck,
  Star,
  Phone,
  Mail,
  MapPin,
  Calendar,
  Clock,
  XCircle,
  Layers,
  ClipboardCheck,
  Play,
  Eye,
  FileCheck,
} from 'lucide-react';

export default function Inventory() {
  const { t } = useTranslation();
  const location = useLocation();

  // Determine initial tab based on route
  const getInitialTab = () => {
    if (location.pathname.includes('recipes')) return 'recipes';
    if (location.pathname.includes('stock-alerts')) return 'alerts';
    if (location.pathname.includes('suppliers')) return 'suppliers';
    if (location.pathname.includes('expiry')) return 'expiry';
    if (location.pathname.includes('stock-counts')) return 'stockCounts';
    if (location.pathname.includes('waste')) return 'waste';
    return 'ingredients';
  };

  const [activeTab, setActiveTab] = useState(getInitialTab);

  // Update active tab when route changes
  useEffect(() => {
    setActiveTab(getInitialTab());
  }, [location.pathname]);

  // Common state
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);

  // Ingredients state
  const [ingredients, setIngredients] = useState([]);
  const [filteredIngredients, setFilteredIngredients] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');
  const [modalOpen, setModalOpen] = useState(false);
  const [stockModalOpen, setStockModalOpen] = useState(false);
  const [transactionModalOpen, setTransactionModalOpen] = useState(false);
  const [selectedIngredient, setSelectedIngredient] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [editingIngredient, setEditingIngredient] = useState(null);
  const [stockAction, setStockAction] = useState('add');
  const [stockFormData, setStockFormData] = useState({
    quantity: '',
    newQuantity: '',
    notes: '',
    performedBy: 'Admin',
  });
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    unit: 'kg',
    currentStock: '',
    minimumStock: '',
    reorderLevel: '',
    costPerUnit: '',
    supplierId: '',
    sku: '',
    active: true,
    trackInventory: true,
    trackExpiry: false,
    defaultShelfLifeDays: '',
    expiryAlertDays: '7',
  });

  // Recipes state
  const [products, setProducts] = useState([]);
  const [recipes, setRecipes] = useState([]);
  const [recipeModalOpen, setRecipeModalOpen] = useState(false);
  const [editingRecipe, setEditingRecipe] = useState(null);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [recipeFormData, setRecipeFormData] = useState({
    productId: '',
    ingredientId: '',
    quantityRequired: '',
    unit: 'kg',
  });

  // Stock Alerts state
  const [subscriptions, setSubscriptions] = useState([]);
  const [alertSummary, setAlertSummary] = useState(null);
  const [subscriptionModalOpen, setSubscriptionModalOpen] = useState(false);
  const [editingSubscription, setEditingSubscription] = useState(null);
  const [subscriptionFormData, setSubscriptionFormData] = useState({
    telegramChatId: '',
    subscriberName: '',
    alertOnLowStock: true,
    alertOnReorder: true,
  });

  // Suppliers state
  const [suppliers, setSuppliers] = useState([]);
  const [supplierModalOpen, setSupplierModalOpen] = useState(false);
  const [editingSupplier, setEditingSupplier] = useState(null);
  const [supplierFormData, setSupplierFormData] = useState({
    name: '',
    code: '',
    contactPerson: '',
    phone: '',
    email: '',
    address: '',
    paymentTerms: '',
    creditLimit: '',
    currency: 'UZS',
    active: true,
    notes: '',
  });

  // Expiry/Batch state
  const [expirySummary, setExpirySummary] = useState(null);
  const [expiringBatches, setExpiringBatches] = useState([]);
  const [expiredBatches, setExpiredBatches] = useState([]);
  const [selectedIngredientBatches, setSelectedIngredientBatches] = useState([]);
  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [batchDetailModalOpen, setBatchDetailModalOpen] = useState(false);
  const [writeOffModalOpen, setWriteOffModalOpen] = useState(false);
  const [selectedBatch, setSelectedBatch] = useState(null);
  const [writeOffReason, setWriteOffReason] = useState('');
  const [batchFormData, setBatchFormData] = useState({
    ingredientId: '',
    batchNumber: '',
    quantity: '',
    receivedDate: new Date().toISOString().split('T')[0],
    expiryDate: '',
    costPerUnit: '',
    supplierId: '',
    poReference: '',
    notes: '',
  });

  // Stock Counts state
  const [stockCounts, setStockCounts] = useState([]);
  const [selectedStockCount, setSelectedStockCount] = useState(null);
  const [stockCountModalOpen, setStockCountModalOpen] = useState(false);
  const [stockCountDetailModalOpen, setStockCountDetailModalOpen] = useState(false);
  const [stockCountFormData, setStockCountFormData] = useState({
    countType: 'FULL',
    scheduledDate: new Date().toISOString().split('T')[0],
    notes: '',
    initiatedBy: 'Admin',
    ingredientIds: [],
  });

  // Waste Management state
  const [wasteRecords, setWasteRecords] = useState([]);
  const [wasteReasons, setWasteReasons] = useState([]);
  const [wasteReport, setWasteReport] = useState(null);
  const [wasteModalOpen, setWasteModalOpen] = useState(false);
  const [wasteFormData, setWasteFormData] = useState({
    ingredientId: '',
    quantity: '',
    wasteReason: 'EXPIRED',
    wasteDate: new Date().toISOString().split('T')[0],
    notes: '',
    recordedBy: 'Admin',
  });

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadIngredients();
      loadProducts();
      loadSubscriptions();
      loadAlertSummary();
      loadSuppliers();
      loadExpirySummary();
      loadExpiringBatches();
      loadExpiredBatches();
      loadStockCounts();
      loadWasteRecords();
      loadWasteReasons();
    }
  }, [selectedRestaurant]);

  useEffect(() => {
    filterIngredients();
  }, [ingredients, searchTerm, filterStatus]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantList = response.data.data?.content || response.data.data || [];
      setRestaurants(restaurantList);
      if (restaurantList.length > 0) {
        setSelectedRestaurant(restaurantList[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadIngredients = async () => {
    setLoading(true);
    try {
      const response = await inventoryAPI.getIngredients(selectedRestaurant);
      setIngredients(response.data.data || []);
    } catch (error) {
      console.error('Failed to load ingredients:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadProducts = async () => {
    try {
      const response = await menuAPI.getProductsByRestaurant(selectedRestaurant);
      setProducts(response.data.data || []);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const loadSubscriptions = async () => {
    try {
      const response = await stockAlertAPI.getSubscriptions(selectedRestaurant);
      setSubscriptions(response.data.data || []);
    } catch (error) {
      console.error('Failed to load subscriptions:', error);
    }
  };

  const loadAlertSummary = async () => {
    try {
      const response = await stockAlertAPI.getSummary(selectedRestaurant);
      setAlertSummary(response.data.data || null);
    } catch (error) {
      console.error('Failed to load alert summary:', error);
    }
  };

  const loadSuppliers = async () => {
    try {
      const response = await supplierAPI.getAll(selectedRestaurant);
      setSuppliers(response.data.data || []);
    } catch (error) {
      console.error('Failed to load suppliers:', error);
    }
  };

  const loadExpirySummary = async () => {
    try {
      const response = await inventoryBatchAPI.getExpirySummary(selectedRestaurant);
      setExpirySummary(response.data.data || null);
    } catch (error) {
      console.error('Failed to load expiry summary:', error);
    }
  };

  const loadExpiringBatches = async () => {
    try {
      const response = await inventoryBatchAPI.getExpiringBatches(selectedRestaurant, 7);
      setExpiringBatches(response.data.data || []);
    } catch (error) {
      console.error('Failed to load expiring batches:', error);
    }
  };

  const loadExpiredBatches = async () => {
    try {
      const response = await inventoryBatchAPI.getExpiredBatches(selectedRestaurant);
      setExpiredBatches(response.data.data || []);
    } catch (error) {
      console.error('Failed to load expired batches:', error);
    }
  };

  const loadStockCounts = async () => {
    try {
      const response = await stockCountAPI.getAll(selectedRestaurant);
      setStockCounts(response.data.data || []);
    } catch (error) {
      console.error('Failed to load stock counts:', error);
    }
  };

  const handleCreateStockCount = async () => {
    try {
      await stockCountAPI.create({
        restaurantId: selectedRestaurant,
        ...stockCountFormData,
      });
      setStockCountModalOpen(false);
      setStockCountFormData({
        countType: 'FULL',
        scheduledDate: new Date().toISOString().split('T')[0],
        notes: '',
        initiatedBy: 'Admin',
        ingredientIds: [],
      });
      loadStockCounts();
    } catch (error) {
      console.error('Failed to create stock count:', error);
    }
  };

  const handleStartStockCount = async (id) => {
    try {
      await stockCountAPI.start(id, 'Admin');
      loadStockCounts();
    } catch (error) {
      console.error('Failed to start stock count:', error);
    }
  };

  const handleViewStockCount = async (id) => {
    try {
      const response = await stockCountAPI.getById(id);
      setSelectedStockCount(response.data.data);
      setStockCountDetailModalOpen(true);
    } catch (error) {
      console.error('Failed to load stock count details:', error);
    }
  };

  const handleRecordCount = async (itemId, countedQuantity) => {
    try {
      await stockCountAPI.recordCount({
        itemId,
        countedQuantity,
        countedBy: 'Admin',
      });
      // Reload the current stock count
      if (selectedStockCount) {
        handleViewStockCount(selectedStockCount.id);
      }
    } catch (error) {
      console.error('Failed to record count:', error);
    }
  };

  const handleSubmitForReview = async (id) => {
    try {
      await stockCountAPI.submitForReview(id, 'Admin');
      loadStockCounts();
      setStockCountDetailModalOpen(false);
    } catch (error) {
      console.error('Failed to submit for review:', error);
    }
  };

  const handleApproveStockCount = async (id, adjustInventory = true) => {
    try {
      await stockCountAPI.approve(id, {
        approvedBy: 'Admin',
        adjustInventory,
      });
      loadStockCounts();
      loadIngredients();
      setStockCountDetailModalOpen(false);
    } catch (error) {
      console.error('Failed to approve stock count:', error);
    }
  };

  const handleCancelStockCount = async (id) => {
    try {
      await stockCountAPI.cancel(id, 'Cancelled by user', 'Admin');
      loadStockCounts();
    } catch (error) {
      console.error('Failed to cancel stock count:', error);
    }
  };

  const getStockCountStatusBadge = (status) => {
    const statusConfig = {
      DRAFT: { variant: 'secondary', label: t('inventory.stockCounts.status.draft', 'Draft') },
      IN_PROGRESS: { variant: 'default', label: t('inventory.stockCounts.status.inProgress', 'In Progress') },
      PENDING_REVIEW: { variant: 'warning', label: t('inventory.stockCounts.status.pendingReview', 'Pending Review') },
      APPROVED: { variant: 'success', label: t('inventory.stockCounts.status.approved', 'Approved') },
      CANCELLED: { variant: 'destructive', label: t('inventory.stockCounts.status.cancelled', 'Cancelled') },
    };
    const config = statusConfig[status] || { variant: 'secondary', label: status };
    return <Badge variant={config.variant}>{config.label}</Badge>;
  };

  const getCountTypeBadge = (type) => {
    const typeConfig = {
      FULL: { variant: 'default', label: t('inventory.stockCounts.type.full', 'Full Count') },
      CYCLE: { variant: 'outline', label: t('inventory.stockCounts.type.cycle', 'Cycle Count') },
      SPOT_CHECK: { variant: 'secondary', label: t('inventory.stockCounts.type.spotCheck', 'Spot Check') },
    };
    const config = typeConfig[type] || { variant: 'secondary', label: type };
    return <Badge variant={config.variant}>{config.label}</Badge>;
  };

  // Waste Management functions
  const loadWasteRecords = async () => {
    try {
      const endDate = new Date().toISOString().split('T')[0];
      const startDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const response = await wasteAPI.getAll(selectedRestaurant, { startDate, endDate });
      setWasteRecords(response.data.data || []);

      // Also load report
      const reportResponse = await wasteAPI.getReport(selectedRestaurant, startDate, endDate);
      setWasteReport(reportResponse.data.data);
    } catch (error) {
      console.error('Failed to load waste records:', error);
    }
  };

  const loadWasteReasons = async () => {
    try {
      const response = await wasteAPI.getReasons();
      setWasteReasons(response.data.data || []);
    } catch (error) {
      console.error('Failed to load waste reasons:', error);
    }
  };

  const handleRecordWaste = async () => {
    try {
      await wasteAPI.record({
        restaurantId: selectedRestaurant,
        ...wasteFormData,
        ingredientId: parseInt(wasteFormData.ingredientId),
        quantity: parseFloat(wasteFormData.quantity),
      });
      setWasteModalOpen(false);
      setWasteFormData({
        ingredientId: '',
        quantity: '',
        wasteReason: 'EXPIRED',
        wasteDate: new Date().toISOString().split('T')[0],
        notes: '',
        recordedBy: 'Admin',
      });
      loadWasteRecords();
      loadIngredients();
    } catch (error) {
      console.error('Failed to record waste:', error);
    }
  };

  const handleDeleteWaste = async (id) => {
    try {
      await wasteAPI.delete(id);
      loadWasteRecords();
    } catch (error) {
      console.error('Failed to delete waste record:', error);
    }
  };

  const getWasteReasonBadge = (reason) => {
    const reasonConfig = {
      EXPIRED: { variant: 'destructive', label: t('inventory.waste.reasons.expired', 'Expired') },
      SPOILED: { variant: 'destructive', label: t('inventory.waste.reasons.spoiled', 'Spoiled') },
      DAMAGED: { variant: 'warning', label: t('inventory.waste.reasons.damaged', 'Damaged') },
      PREPARATION: { variant: 'secondary', label: t('inventory.waste.reasons.preparation', 'Preparation') },
      OVER_PRODUCTION: { variant: 'outline', label: t('inventory.waste.reasons.overProduction', 'Over Production') },
      CUSTOMER_RETURN: { variant: 'outline', label: t('inventory.waste.reasons.customerReturn', 'Customer Return') },
      QUALITY_ISSUE: { variant: 'warning', label: t('inventory.waste.reasons.qualityIssue', 'Quality Issue') },
      CONTAMINATION: { variant: 'destructive', label: t('inventory.waste.reasons.contamination', 'Contamination') },
      THEFT: { variant: 'destructive', label: t('inventory.waste.reasons.theft', 'Theft') },
      OTHER: { variant: 'secondary', label: t('inventory.waste.reasons.other', 'Other') },
    };
    const config = reasonConfig[reason] || { variant: 'secondary', label: reason };
    return <Badge variant={config.variant}>{config.label}</Badge>;
  };

  const loadBatchesForIngredient = async (ingredientId) => {
    try {
      const response = await inventoryBatchAPI.getBatchesByIngredient(ingredientId);
      setSelectedIngredientBatches(response.data.data || []);
    } catch (error) {
      console.error('Failed to load batches:', error);
    }
  };

  const loadRecipesForProduct = async (productId) => {
    try {
      const response = await inventoryAPI.getRecipesByProduct(productId);
      setRecipes(response.data.data || []);
    } catch (error) {
      console.error('Failed to load recipes:', error);
    }
  };

  const filterIngredients = () => {
    let filtered = ingredients;

    if (searchTerm) {
      filtered = filtered.filter(
        (ing) =>
          ing.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
          ing.sku?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          ing.supplierName?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    if (filterStatus === 'low') {
      filtered = filtered.filter((ing) => ing.currentStock <= ing.minimumStock);
    } else if (filterStatus === 'reorder') {
      filtered = filtered.filter((ing) => ing.currentStock <= ing.reorderLevel);
    } else if (filterStatus === 'inactive') {
      filtered = filtered.filter((ing) => !ing.active);
    } else if (filterStatus === 'active') {
      filtered = filtered.filter((ing) => ing.active);
    }

    setFilteredIngredients(filtered);
  };

  // Ingredient handlers
  const handleAddNew = () => {
    setEditingIngredient(null);
    setFormData({
      name: '',
      description: '',
      unit: 'kg',
      currentStock: '',
      minimumStock: '',
      reorderLevel: '',
      costPerUnit: '',
      supplierId: '',
      sku: '',
      active: true,
      trackInventory: true,
      trackExpiry: false,
      defaultShelfLifeDays: '',
      expiryAlertDays: '7',
    });
    setModalOpen(true);
  };

  const handleEdit = (ingredient) => {
    setEditingIngredient(ingredient);
    setFormData({
      name: ingredient.name,
      description: ingredient.description || '',
      unit: ingredient.unit,
      currentStock: ingredient.currentStock,
      minimumStock: ingredient.minimumStock,
      reorderLevel: ingredient.reorderLevel,
      costPerUnit: ingredient.costPerUnit || '',
      supplierId: ingredient.supplierId?.toString() || '',
      sku: ingredient.sku || '',
      active: ingredient.active,
      trackInventory: ingredient.trackInventory,
      trackExpiry: ingredient.trackExpiry || false,
      defaultShelfLifeDays: ingredient.defaultShelfLifeDays?.toString() || '',
      expiryAlertDays: ingredient.expiryAlertDays?.toString() || '7',
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const data = {
        ...formData,
        restaurantId: selectedRestaurant,
        currentStock: parseFloat(formData.currentStock) || 0,
        minimumStock: parseFloat(formData.minimumStock) || 0,
        reorderLevel: parseFloat(formData.reorderLevel) || 0,
        costPerUnit: formData.costPerUnit ? parseFloat(formData.costPerUnit) : null,
        supplierId: formData.supplierId ? parseInt(formData.supplierId) : null,
        trackExpiry: formData.trackExpiry,
        defaultShelfLifeDays: formData.defaultShelfLifeDays ? parseInt(formData.defaultShelfLifeDays) : null,
        expiryAlertDays: formData.expiryAlertDays ? parseInt(formData.expiryAlertDays) : 7,
      };

      if (editingIngredient) {
        await inventoryAPI.updateIngredient(editingIngredient.id, data);
      } else {
        await inventoryAPI.createIngredient(data);
      }

      setModalOpen(false);
      loadIngredients();
    } catch (error) {
      console.error('Failed to save ingredient:', error);
      alert(t('inventory.errors.saveFailed'));
    }
  };

  const handleDelete = async (id) => {
    if (!confirm(t('inventory.confirmDelete'))) return;

    try {
      await inventoryAPI.deleteIngredient(id);
      loadIngredients();
    } catch (error) {
      console.error('Failed to delete ingredient:', error);
      alert(t('inventory.errors.deleteFailed'));
    }
  };

  const handleStockAction = (ingredient, action) => {
    setSelectedIngredient(ingredient);
    setStockAction(action);
    setStockFormData({
      quantity: '',
      newQuantity: action === 'adjust' ? ingredient.currentStock.toString() : '',
      notes: '',
      performedBy: 'Admin',
    });
    setStockModalOpen(true);
  };

  const handleStockSubmit = async () => {
    if (!selectedIngredient) return;

    try {
      if (stockAction === 'add') {
        await inventoryAPI.addStock(selectedIngredient.id, {
          quantity: parseFloat(stockFormData.quantity),
          notes: stockFormData.notes,
          performedBy: stockFormData.performedBy,
        });
      } else {
        await inventoryAPI.adjustStock(selectedIngredient.id, {
          newQuantity: parseFloat(stockFormData.newQuantity),
          notes: stockFormData.notes,
          performedBy: stockFormData.performedBy,
        });
      }

      setStockModalOpen(false);
      loadIngredients();
    } catch (error) {
      console.error('Failed to update stock:', error);
      alert('Failed to update stock');
    }
  };

  const handleViewTransactions = async (ingredient) => {
    setSelectedIngredient(ingredient);
    try {
      const response = await inventoryAPI.getTransactions(ingredient.id);
      setTransactions(response.data.data || []);
      setTransactionModalOpen(true);
    } catch (error) {
      console.error('Failed to load transactions:', error);
      alert('Failed to load transactions');
    }
  };

  // Recipe handlers
  const handleAddRecipe = () => {
    setEditingRecipe(null);
    setRecipeFormData({
      productId: selectedProduct?.id?.toString() || '',
      ingredientId: '',
      quantityRequired: '',
      unit: 'kg',
    });
    setRecipeModalOpen(true);
  };

  const handleSaveRecipe = async () => {
    try {
      const data = {
        productId: parseInt(recipeFormData.productId),
        ingredientId: parseInt(recipeFormData.ingredientId),
        quantityRequired: parseFloat(recipeFormData.quantityRequired),
        unit: recipeFormData.unit,
      };

      if (editingRecipe) {
        await inventoryAPI.updateRecipe(editingRecipe.id, data);
      } else {
        await inventoryAPI.createRecipe(data);
      }

      setRecipeModalOpen(false);
      if (selectedProduct) {
        loadRecipesForProduct(selectedProduct.id);
      }
    } catch (error) {
      console.error('Failed to save recipe:', error);
      alert(t('inventory.recipes.errors.saveFailed', 'Failed to save recipe'));
    }
  };

  const handleDeleteRecipe = async (id) => {
    if (!confirm(t('inventory.recipes.confirmDelete', 'Are you sure you want to delete this recipe?'))) return;

    try {
      await inventoryAPI.deleteRecipe(id);
      if (selectedProduct) {
        loadRecipesForProduct(selectedProduct.id);
      }
    } catch (error) {
      console.error('Failed to delete recipe:', error);
      alert(t('inventory.recipes.errors.deleteFailed', 'Failed to delete recipe'));
    }
  };

  const handleSelectProduct = (product) => {
    setSelectedProduct(product);
    loadRecipesForProduct(product.id);
  };

  // Subscription handlers
  const handleAddSubscription = () => {
    setEditingSubscription(null);
    setSubscriptionFormData({
      telegramChatId: '',
      subscriberName: '',
      alertOnLowStock: true,
      alertOnReorder: true,
    });
    setSubscriptionModalOpen(true);
  };

  const handleEditSubscription = (subscription) => {
    setEditingSubscription(subscription);
    setSubscriptionFormData({
      telegramChatId: subscription.telegramChatId.toString(),
      subscriberName: subscription.subscriberName || '',
      alertOnLowStock: subscription.alertOnLowStock,
      alertOnReorder: subscription.alertOnReorder,
    });
    setSubscriptionModalOpen(true);
  };

  const handleSaveSubscription = async () => {
    try {
      const data = {
        restaurantId: selectedRestaurant,
        telegramChatId: parseInt(subscriptionFormData.telegramChatId),
        subscriberName: subscriptionFormData.subscriberName,
        alertOnLowStock: subscriptionFormData.alertOnLowStock,
        alertOnReorder: subscriptionFormData.alertOnReorder,
      };

      if (editingSubscription) {
        await stockAlertAPI.updateSubscription(editingSubscription.id, data);
      } else {
        await stockAlertAPI.createSubscription(data);
      }

      setSubscriptionModalOpen(false);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to save subscription:', error);
      alert(t('inventory.stockAlerts.errors.saveFailed', 'Failed to save subscription'));
    }
  };

  const handleToggleSubscription = async (id) => {
    try {
      await stockAlertAPI.toggleSubscription(id);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to toggle subscription:', error);
    }
  };

  const handleDeleteSubscription = async (id) => {
    if (!confirm(t('inventory.stockAlerts.confirmDelete', 'Are you sure you want to delete this subscription?'))) return;

    try {
      await stockAlertAPI.deleteSubscription(id);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to delete subscription:', error);
    }
  };

  const handleTriggerAlert = async () => {
    try {
      await stockAlertAPI.triggerAlert(selectedRestaurant);
      alert(t('inventory.stockAlerts.alertTriggered', 'Stock alert sent successfully!'));
    } catch (error) {
      console.error('Failed to trigger alert:', error);
      alert(t('inventory.stockAlerts.errors.triggerFailed', 'Failed to trigger alert'));
    }
  };

  // Supplier handlers
  const handleAddSupplier = () => {
    setEditingSupplier(null);
    setSupplierFormData({
      name: '',
      code: '',
      contactPerson: '',
      phone: '',
      email: '',
      address: '',
      paymentTerms: '',
      creditLimit: '',
      currency: 'UZS',
      active: true,
      notes: '',
    });
    setSupplierModalOpen(true);
  };

  const handleEditSupplier = (supplier) => {
    setEditingSupplier(supplier);
    setSupplierFormData({
      name: supplier.name,
      code: supplier.code || '',
      contactPerson: supplier.contactPerson || '',
      phone: supplier.phone || '',
      email: supplier.email || '',
      address: supplier.address || '',
      paymentTerms: supplier.paymentTerms || '',
      creditLimit: supplier.creditLimit?.toString() || '',
      currency: supplier.currency || 'UZS',
      active: supplier.active,
      notes: supplier.notes || '',
    });
    setSupplierModalOpen(true);
  };

  const handleSaveSupplier = async () => {
    try {
      const data = {
        restaurantId: selectedRestaurant,
        name: supplierFormData.name,
        code: supplierFormData.code || null,
        contactPerson: supplierFormData.contactPerson || null,
        phone: supplierFormData.phone || null,
        email: supplierFormData.email || null,
        address: supplierFormData.address || null,
        paymentTerms: supplierFormData.paymentTerms || null,
        creditLimit: supplierFormData.creditLimit ? parseFloat(supplierFormData.creditLimit) : null,
        currency: supplierFormData.currency,
        active: supplierFormData.active,
        notes: supplierFormData.notes || null,
      };

      if (editingSupplier) {
        await supplierAPI.update(editingSupplier.id, data);
      } else {
        await supplierAPI.create(data);
      }

      setSupplierModalOpen(false);
      loadSuppliers();
    } catch (error) {
      console.error('Failed to save supplier:', error);
      alert(t('inventory.suppliers.errors.saveFailed', 'Failed to save supplier'));
    }
  };

  const handleDeleteSupplier = async (id) => {
    if (!confirm(t('inventory.suppliers.confirmDelete', 'Are you sure you want to delete this supplier?'))) return;

    try {
      await supplierAPI.delete(id);
      loadSuppliers();
    } catch (error) {
      console.error('Failed to delete supplier:', error);
      alert(t('inventory.suppliers.errors.deleteFailed', 'Failed to delete supplier'));
    }
  };

  const handleToggleSupplier = async (id) => {
    try {
      await supplierAPI.toggle(id);
      loadSuppliers();
    } catch (error) {
      console.error('Failed to toggle supplier:', error);
    }
  };

  // Batch handlers
  const handleAddBatch = () => {
    setBatchFormData({
      ingredientId: '',
      batchNumber: `BATCH-${Date.now()}`,
      quantity: '',
      receivedDate: new Date().toISOString().split('T')[0],
      expiryDate: '',
      costPerUnit: '',
      supplierId: '',
      poReference: '',
      notes: '',
    });
    setBatchModalOpen(true);
  };

  const handleSaveBatch = async () => {
    try {
      const data = {
        ingredientId: parseInt(batchFormData.ingredientId),
        batchNumber: batchFormData.batchNumber,
        quantity: parseFloat(batchFormData.quantity),
        receivedDate: batchFormData.receivedDate,
        expiryDate: batchFormData.expiryDate || null,
        costPerUnit: batchFormData.costPerUnit ? parseFloat(batchFormData.costPerUnit) : null,
        supplierId: batchFormData.supplierId ? parseInt(batchFormData.supplierId) : null,
        poReference: batchFormData.poReference || null,
        notes: batchFormData.notes || null,
      };

      await inventoryBatchAPI.createBatch(data);
      setBatchModalOpen(false);
      loadExpiringBatches();
      loadExpiredBatches();
      loadExpirySummary();
      loadIngredients();
    } catch (error) {
      console.error('Failed to save batch:', error);
      alert(t('inventory.expiry.errors.saveFailed', 'Failed to save batch'));
    }
  };

  const handleViewBatches = async (ingredient) => {
    setSelectedIngredient(ingredient);
    await loadBatchesForIngredient(ingredient.id);
    setBatchDetailModalOpen(true);
  };

  const handleWriteOff = (batch) => {
    setSelectedBatch(batch);
    setWriteOffReason('');
    setWriteOffModalOpen(true);
  };

  const handleConfirmWriteOff = async () => {
    if (!selectedBatch || !writeOffReason.trim()) return;

    try {
      await inventoryBatchAPI.writeOffBatch(selectedBatch.id, writeOffReason);
      setWriteOffModalOpen(false);
      loadExpiringBatches();
      loadExpiredBatches();
      loadExpirySummary();
      loadIngredients();
      if (selectedIngredient) {
        loadBatchesForIngredient(selectedIngredient.id);
      }
    } catch (error) {
      console.error('Failed to write off batch:', error);
      alert(t('inventory.expiry.errors.writeOffFailed', 'Failed to write off batch'));
    }
  };

  const handleMarkExpiredBatches = async () => {
    try {
      await inventoryBatchAPI.markExpiredBatches(selectedRestaurant);
      loadExpiringBatches();
      loadExpiredBatches();
      loadExpirySummary();
    } catch (error) {
      console.error('Failed to mark expired batches:', error);
    }
  };

  const getExpiryStatusBadge = (batch) => {
    if (batch.isExpired) {
      return <Badge className="bg-red-100 text-red-800">{t('inventory.expiry.status.expired', 'Expired')}</Badge>;
    }
    if (batch.isExpiringSoon) {
      return <Badge className="bg-yellow-100 text-yellow-800">{t('inventory.expiry.status.expiringSoon', 'Expiring Soon')}</Badge>;
    }
    return <Badge className="bg-green-100 text-green-800">{t('inventory.expiry.status.fresh', 'Fresh')}</Badge>;
  };

  const getStockStatus = (ingredient) => {
    if (!ingredient.trackInventory) {
      return { label: t('inventory.status.notTracked', 'Not Tracked'), color: 'bg-gray-100 text-gray-800' };
    }
    if (ingredient.currentStock <= ingredient.minimumStock) {
      return { label: t('inventory.status.lowStock', 'Low Stock'), color: 'bg-red-100 text-red-800' };
    }
    if (ingredient.currentStock <= ingredient.reorderLevel) {
      return { label: t('inventory.status.reorderSoon', 'Reorder Soon'), color: 'bg-yellow-100 text-yellow-800' };
    }
    return { label: t('inventory.status.inStock', 'In Stock'), color: 'bg-green-100 text-green-800' };
  };

  const lowStockCount = ingredients.filter((ing) => ing.currentStock <= ing.minimumStock).length;
  const reorderCount = ingredients.filter((ing) => ing.currentStock <= ing.reorderLevel).length;
  const activeCount = ingredients.filter((ing) => ing.active).length;

  if (loading && ingredients.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('inventory.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('inventory.subtitle')}</p>
        </div>
        <div className="flex gap-3 items-center">
          <Select
            value={selectedRestaurant?.toString()}
            onValueChange={(value) => setSelectedRestaurant(parseInt(value))}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('common.selectRestaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map((restaurant) => (
                <SelectItem key={restaurant.id} value={restaurant.id.toString()}>
                  {restaurant.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={() => { loadIngredients(); loadSubscriptions(); loadAlertSummary(); }} variant="outline" size="icon">
            <RefreshCw className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="grid w-full grid-cols-7">
          <TabsTrigger value="ingredients" className="flex items-center gap-2">
            <Package className="h-4 w-4" />
            {t('inventory.tabs.ingredients', 'Ingredients')}
          </TabsTrigger>
          <TabsTrigger value="recipes" className="flex items-center gap-2">
            <UtensilsCrossed className="h-4 w-4" />
            {t('inventory.tabs.recipes', 'Recipes')}
          </TabsTrigger>
          <TabsTrigger value="expiry" className="flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            {t('inventory.tabs.expiry', 'Expiry')}
            {expirySummary && (expirySummary.expiredCount > 0 || expirySummary.expiringCount > 0) && (
              <span className="ml-1 bg-red-500 text-white text-xs rounded-full px-1.5 py-0.5">
                {(expirySummary.expiredCount || 0) + (expirySummary.expiringCount || 0)}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger value="stockCounts" className="flex items-center gap-2">
            <ClipboardCheck className="h-4 w-4" />
            {t('inventory.tabs.stockCounts', 'Stock Counts')}
          </TabsTrigger>
          <TabsTrigger value="waste" className="flex items-center gap-2">
            <Trash2 className="h-4 w-4" />
            {t('inventory.tabs.waste', 'Waste')}
          </TabsTrigger>
          <TabsTrigger value="suppliers" className="flex items-center gap-2">
            <Truck className="h-4 w-4" />
            {t('inventory.tabs.suppliers', 'Suppliers')}
          </TabsTrigger>
          <TabsTrigger value="alerts" className="flex items-center gap-2">
            <Bell className="h-4 w-4" />
            {t('inventory.tabs.stockAlerts', 'Stock Alerts')}
          </TabsTrigger>
        </TabsList>

        {/* Ingredients Tab */}
        <TabsContent value="ingredients" className="space-y-6">
          {/* Stats Cards */}
          <div className="grid gap-4 md:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('inventory.stats.total')}</CardTitle>
                <Package className="h-4 w-4 text-blue-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{ingredients.length}</div>
                <p className="text-xs text-muted-foreground">{activeCount} {t('inventory.stats.active')}</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('inventory.stats.lowStock')}</CardTitle>
                <AlertTriangle className="h-4 w-4 text-red-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-red-600">{lowStockCount}</div>
                <p className="text-xs text-muted-foreground">{t('inventory.stats.needsAttention')}</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('inventory.stats.reorder')}</CardTitle>
                <TrendingDown className="h-4 w-4 text-yellow-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-yellow-600">{reorderCount}</div>
                <p className="text-xs text-muted-foreground">{t('inventory.stats.reorderSoon')}</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('inventory.stats.tracked')}</CardTitle>
                <CheckCircle className="h-4 w-4 text-green-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {ingredients.filter((ing) => ing.trackInventory).length}
                </div>
                <p className="text-xs text-muted-foreground">{t('inventory.stats.monitoring')}</p>
              </CardContent>
            </Card>
          </div>

          {/* Filters */}
          <Card>
            <CardContent className="pt-6">
              <div className="flex gap-4 items-end">
                <div className="flex-1">
                  <Label htmlFor="search">{t('common.search')}</Label>
                  <div className="relative">
                    <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="search"
                      placeholder={t('inventory.searchPlaceholder')}
                      value={searchTerm}
                      onChange={(e) => setSearchTerm(e.target.value)}
                      className="pl-10"
                    />
                  </div>
                </div>
                <div className="w-[200px]">
                  <Label htmlFor="filter">{t('common.filter')}</Label>
                  <Select value={filterStatus} onValueChange={setFilterStatus}>
                    <SelectTrigger id="filter">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">{t('inventory.filters.all')}</SelectItem>
                      <SelectItem value="active">{t('inventory.filters.active')}</SelectItem>
                      <SelectItem value="inactive">{t('inventory.filters.inactive')}</SelectItem>
                      <SelectItem value="low">{t('inventory.filters.lowStock')}</SelectItem>
                      <SelectItem value="reorder">{t('inventory.filters.reorder')}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <Button onClick={handleAddNew}>
                  <Plus className="h-4 w-4 mr-2" />
                  {t('inventory.addIngredient')}
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* Ingredients Table */}
          <Card>
            <CardHeader>
              <CardTitle>{t('inventory.ingredientsList')}</CardTitle>
              <CardDescription>
                {t('inventory.ingredientsDescription')} ({filteredIngredients.length} {t('common.items')})
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.fields.name')}</TableHead>
                    <TableHead>{t('inventory.fields.sku')}</TableHead>
                    <TableHead>{t('inventory.fields.currentStock')}</TableHead>
                    <TableHead>{t('inventory.fields.minimumStock')}</TableHead>
                    <TableHead>{t('inventory.fields.unit')}</TableHead>
                    <TableHead>{t('inventory.fields.supplier')}</TableHead>
                    <TableHead>{t('inventory.fields.status')}</TableHead>
                    <TableHead className="text-right">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredIngredients.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                        {t('inventory.noIngredients')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredIngredients.map((ingredient) => {
                      const status = getStockStatus(ingredient);
                      return (
                        <TableRow key={ingredient.id}>
                          <TableCell className="font-medium">
                            <div>
                              <div>{ingredient.name}</div>
                              {ingredient.description && (
                                <div className="text-xs text-muted-foreground">{ingredient.description}</div>
                              )}
                            </div>
                          </TableCell>
                          <TableCell>{ingredient.sku || '-'}</TableCell>
                          <TableCell>
                            <span className={ingredient.currentStock <= ingredient.minimumStock ? 'text-red-600 font-semibold' : ''}>
                              {ingredient.currentStock}
                            </span>
                          </TableCell>
                          <TableCell>{ingredient.minimumStock}</TableCell>
                          <TableCell>{ingredient.unit}</TableCell>
                          <TableCell>{ingredient.supplierName || '-'}</TableCell>
                          <TableCell>
                            <Badge className={status.color}>{status.label}</Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            <div className="flex justify-end gap-1">
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleStockAction(ingredient, 'add')}
                                title={t("common.buttons.addStock")}
                              >
                                <TrendingUp className="h-4 w-4 text-green-600" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleViewTransactions(ingredient)}
                                title={t("common.buttons.viewHistory")}
                              >
                                <History className="h-4 w-4 text-blue-600" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleEdit(ingredient)}
                                title={t("common.buttons.edit")}
                              >
                                <Edit className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleDelete(ingredient.id)}
                                title={t("common.buttons.delete")}
                              >
                                <Trash2 className="h-4 w-4 text-red-600" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Recipes Tab */}
        <TabsContent value="recipes" className="space-y-6">
          <div className="grid gap-6 md:grid-cols-2">
            {/* Products List */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <UtensilsCrossed className="h-5 w-5" />
                  {t('inventory.recipes.selectProduct', 'Select Product')}
                </CardTitle>
                <CardDescription>
                  {t('inventory.recipes.selectProductDesc', 'Select a product to view and manage its recipe')}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-2 max-h-[500px] overflow-y-auto">
                  {products.length === 0 ? (
                    <p className="text-center text-muted-foreground py-8">
                      {t('inventory.recipes.noProducts', 'No products available')}
                    </p>
                  ) : (
                    products.map((product) => (
                      <div
                        key={product.id}
                        className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                          selectedProduct?.id === product.id
                            ? 'bg-primary/10 border-primary'
                            : 'hover:bg-muted'
                        }`}
                        onClick={() => handleSelectProduct(product)}
                      >
                        <div className="flex items-center gap-3">
                          {product.imageUrl ? (
                            <img
                              src={product.imageUrl}
                              alt={product.name}
                              className="w-12 h-12 rounded object-cover"
                            />
                          ) : (
                            <div className="w-12 h-12 rounded bg-muted flex items-center justify-center">
                              <UtensilsCrossed className="h-6 w-6 text-muted-foreground" />
                            </div>
                          )}
                          <div>
                            <div className="font-medium">{product.name}</div>
                            <div className="text-sm text-muted-foreground">
                              {product.categoryName || 'Uncategorized'}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </CardContent>
            </Card>

            {/* Recipe Details */}
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="flex items-center gap-2">
                      <Link2 className="h-5 w-5" />
                      {t('inventory.recipes.ingredientLinks', 'Ingredient Links')}
                    </CardTitle>
                    <CardDescription>
                      {selectedProduct
                        ? `${t('inventory.recipes.for', 'Recipe for')} ${selectedProduct.name}`
                        : t('inventory.recipes.selectProductFirst', 'Select a product to view its recipe')}
                    </CardDescription>
                  </div>
                  {selectedProduct && (
                    <Button onClick={handleAddRecipe} size="sm">
                      <Plus className="h-4 w-4 mr-2" />
                      {t('inventory.recipes.addIngredient', 'Add Ingredient')}
                    </Button>
                  )}
                </div>
              </CardHeader>
              <CardContent>
                {!selectedProduct ? (
                  <div className="text-center text-muted-foreground py-12">
                    <UtensilsCrossed className="h-12 w-12 mx-auto mb-4 opacity-50" />
                    <p>{t('inventory.recipes.selectProductFirst', 'Select a product to view its recipe')}</p>
                  </div>
                ) : recipes.length === 0 ? (
                  <div className="text-center text-muted-foreground py-12">
                    <Package className="h-12 w-12 mx-auto mb-4 opacity-50" />
                    <p>{t('inventory.recipes.noIngredients', 'No ingredients linked to this product')}</p>
                    <Button onClick={handleAddRecipe} variant="outline" className="mt-4">
                      <Plus className="h-4 w-4 mr-2" />
                      {t('inventory.recipes.addFirst', 'Add first ingredient')}
                    </Button>
                  </div>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('inventory.recipes.ingredient', 'Ingredient')}</TableHead>
                        <TableHead>{t('inventory.recipes.quantity', 'Quantity')}</TableHead>
                        <TableHead>{t('inventory.recipes.unit', 'Unit')}</TableHead>
                        <TableHead className="text-right">{t('common.actions')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {recipes.map((recipe) => (
                        <TableRow key={recipe.id}>
                          <TableCell className="font-medium">{recipe.ingredientName}</TableCell>
                          <TableCell>{recipe.quantityRequired}</TableCell>
                          <TableCell>{recipe.unit}</TableCell>
                          <TableCell className="text-right">
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleDeleteRecipe(recipe.id)}
                            >
                              <Trash2 className="h-4 w-4 text-red-600" />
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* Expiry Tab */}
        <TabsContent value="expiry" className="space-y-6">
          {/* Expiry Stats Cards */}
          <div className="grid gap-4 md:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.expiry.stats.expired', 'Expired Batches')}
                </CardTitle>
                <XCircle className="h-4 w-4 text-red-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-red-600">
                  {expirySummary?.expiredCount || 0}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.expiry.stats.expiringSoon', 'Expiring Soon')}
                </CardTitle>
                <Clock className="h-4 w-4 text-yellow-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-yellow-600">
                  {expirySummary?.expiringCount || 0}
                </div>
                <p className="text-xs text-muted-foreground">{t('inventory.expiry.within7Days', 'Within 7 days')}</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.expiry.stats.activeBatches', 'Active Batches')}
                </CardTitle>
                <Layers className="h-4 w-4 text-green-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-green-600">
                  {expirySummary?.activeBatchCount || 0}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.expiry.stats.tracked', 'Tracked Ingredients')}
                </CardTitle>
                <Calendar className="h-4 w-4 text-blue-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {expirySummary?.ingredientsWithExpiryCount || 0}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Actions Row */}
          <Card>
            <CardContent className="pt-6">
              <div className="flex gap-4 items-center justify-between">
                <div className="flex gap-2">
                  <Button onClick={handleAddBatch}>
                    <Plus className="h-4 w-4 mr-2" />
                    {t('inventory.expiry.addBatch', 'Add Batch')}
                  </Button>
                  <Button onClick={handleMarkExpiredBatches} variant="outline">
                    <RefreshCw className="h-4 w-4 mr-2" />
                    {t('inventory.expiry.markExpired', 'Update Expiry Status')}
                  </Button>
                </div>
                <Button
                  onClick={() => {
                    loadExpiringBatches();
                    loadExpiredBatches();
                    loadExpirySummary();
                  }}
                  variant="outline"
                  size="icon"
                >
                  <RefreshCw className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* Expired Batches Table */}
          {expiredBatches.length > 0 && (
            <Card className="border-red-200">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-red-600">
                  <XCircle className="h-5 w-5" />
                  {t('inventory.expiry.expiredBatches', 'Expired Batches')}
                </CardTitle>
                <CardDescription>
                  {t('inventory.expiry.expiredBatchesDesc', 'These batches have passed their expiry date and should be written off')}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('inventory.expiry.fields.ingredient', 'Ingredient')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.batchNumber', 'Batch #')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.quantity', 'Quantity')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.expiryDate', 'Expiry Date')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.daysExpired', 'Days Expired')}</TableHead>
                      <TableHead className="text-right">{t('common.actions')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {expiredBatches.map((batch) => (
                      <TableRow key={batch.id} className="bg-red-50">
                        <TableCell className="font-medium">{batch.ingredientName}</TableCell>
                        <TableCell>{batch.batchNumber}</TableCell>
                        <TableCell>{batch.quantity} {batch.unit}</TableCell>
                        <TableCell>{batch.expiryDate}</TableCell>
                        <TableCell className="text-red-600 font-semibold">
                          {Math.abs(batch.daysUntilExpiry)} {t('common.days', 'days')}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => handleWriteOff(batch)}
                          >
                            <Trash2 className="h-4 w-4 mr-2" />
                            {t('inventory.expiry.writeOff', 'Write Off')}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* Expiring Soon Batches Table */}
          {expiringBatches.length > 0 && (
            <Card className="border-yellow-200">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-yellow-600">
                  <Clock className="h-5 w-5" />
                  {t('inventory.expiry.expiringBatches', 'Expiring Soon')}
                </CardTitle>
                <CardDescription>
                  {t('inventory.expiry.expiringBatchesDesc', 'These batches will expire within the next 7 days')}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('inventory.expiry.fields.ingredient', 'Ingredient')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.batchNumber', 'Batch #')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.quantity', 'Quantity')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.expiryDate', 'Expiry Date')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.daysRemaining', 'Days Left')}</TableHead>
                      <TableHead>{t('inventory.expiry.fields.status', 'Status')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {expiringBatches.map((batch) => (
                      <TableRow key={batch.id} className="bg-yellow-50">
                        <TableCell className="font-medium">{batch.ingredientName}</TableCell>
                        <TableCell>{batch.batchNumber}</TableCell>
                        <TableCell>{batch.quantity} {batch.unit}</TableCell>
                        <TableCell>{batch.expiryDate}</TableCell>
                        <TableCell className="text-yellow-600 font-semibold">
                          {batch.daysUntilExpiry} {t('common.days', 'days')}
                        </TableCell>
                        <TableCell>{getExpiryStatusBadge(batch)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* Ingredients with Expiry Tracking */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Package className="h-5 w-5" />
                {t('inventory.expiry.ingredientsWithExpiry', 'Ingredients with Expiry Tracking')}
              </CardTitle>
              <CardDescription>
                {t('inventory.expiry.ingredientsWithExpiryDesc', 'Ingredients configured to track expiry dates')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.fields.name', 'Name')}</TableHead>
                    <TableHead>{t('inventory.fields.sku', 'SKU')}</TableHead>
                    <TableHead>{t('inventory.expiry.fields.shelfLife', 'Shelf Life')}</TableHead>
                    <TableHead>{t('inventory.expiry.fields.alertDays', 'Alert Days')}</TableHead>
                    <TableHead>{t('inventory.expiry.fields.activeBatches', 'Active Batches')}</TableHead>
                    <TableHead>{t('inventory.expiry.fields.expiringBatches', 'Expiring')}</TableHead>
                    <TableHead className="text-right">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {ingredients.filter(ing => ing.trackExpiry).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                        {t('inventory.expiry.noExpiryTracking', 'No ingredients have expiry tracking enabled. Edit an ingredient to enable it.')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    ingredients.filter(ing => ing.trackExpiry).map((ingredient) => (
                      <TableRow key={ingredient.id}>
                        <TableCell className="font-medium">{ingredient.name}</TableCell>
                        <TableCell>{ingredient.sku || '-'}</TableCell>
                        <TableCell>
                          {ingredient.defaultShelfLifeDays
                            ? `${ingredient.defaultShelfLifeDays} ${t('common.days', 'days')}`
                            : '-'}
                        </TableCell>
                        <TableCell>
                          {ingredient.expiryAlertDays} {t('common.days', 'days')}
                        </TableCell>
                        <TableCell>
                          <Badge className="bg-green-100 text-green-800">
                            {ingredient.activeBatchCount || 0}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {(ingredient.expiringBatchCount || 0) > 0 ? (
                            <Badge className="bg-yellow-100 text-yellow-800">
                              {ingredient.expiringBatchCount}
                            </Badge>
                          ) : (
                            <Badge className="bg-gray-100 text-gray-800">0</Badge>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleViewBatches(ingredient)}
                          >
                            <Layers className="h-4 w-4 mr-2" />
                            {t('inventory.expiry.viewBatches', 'View Batches')}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Stock Counts Tab */}
        <TabsContent value="stockCounts" className="space-y-6">
          {/* Stats Cards */}
          <div className="grid gap-4 md:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.stockCounts.stats.total', 'Total Counts')}
                </CardTitle>
                <ClipboardCheck className="h-4 w-4 text-blue-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stockCounts.length}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.stockCounts.stats.inProgress', 'In Progress')}
                </CardTitle>
                <Play className="h-4 w-4 text-yellow-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stockCounts.filter(sc => sc.status === 'IN_PROGRESS').length}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.stockCounts.stats.pendingReview', 'Pending Review')}
                </CardTitle>
                <Eye className="h-4 w-4 text-orange-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stockCounts.filter(sc => sc.status === 'PENDING_REVIEW').length}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.stockCounts.stats.approved', 'Approved')}
                </CardTitle>
                <FileCheck className="h-4 w-4 text-green-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stockCounts.filter(sc => sc.status === 'APPROVED').length}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Actions */}
          <div className="flex justify-end">
            <Button onClick={() => setStockCountModalOpen(true)}>
              <Plus className="mr-2 h-4 w-4" />
              {t('inventory.stockCounts.newCount', 'New Stock Count')}
            </Button>
          </div>

          {/* Stock Counts Table */}
          <Card>
            <CardHeader>
              <CardTitle>{t('inventory.stockCounts.title', 'Stock Counts')}</CardTitle>
              <CardDescription>
                {t('inventory.stockCounts.description', 'Physical inventory counts and audits')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.stockCounts.countNumber', 'Count #')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.type', 'Type')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.status', 'Status')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.progress', 'Progress')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.variance', 'Variance')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.scheduledDate', 'Scheduled')}</TableHead>
                    <TableHead className="text-right">{t('common.actions', 'Actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {stockCounts.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center text-muted-foreground py-8">
                        {t('inventory.stockCounts.noData', 'No stock counts found')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    stockCounts.map((count) => (
                      <TableRow key={count.id}>
                        <TableCell className="font-medium">{count.countNumber}</TableCell>
                        <TableCell>{getCountTypeBadge(count.countType)}</TableCell>
                        <TableCell>{getStockCountStatusBadge(count.status)}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <div className="w-24 bg-gray-200 rounded-full h-2">
                              <div
                                className="bg-blue-600 h-2 rounded-full"
                                style={{ width: `${count.totalItems > 0 ? (count.countedItems / count.totalItems) * 100 : 0}%` }}
                              />
                            </div>
                            <span className="text-sm text-muted-foreground">
                              {count.countedItems}/{count.totalItems}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell>
                          {count.varianceCount > 0 ? (
                            <span className="text-red-600 font-medium">
                              {count.varianceCount} {t('inventory.stockCounts.items', 'items')}
                              {count.totalVarianceValue && ` (${count.totalVarianceValue.toLocaleString()})`}
                            </span>
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </TableCell>
                        <TableCell>
                          {count.scheduledDate ? new Date(count.scheduledDate).toLocaleDateString() : '-'}
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleViewStockCount(count.id)}
                            >
                              <Eye className="h-4 w-4" />
                            </Button>
                            {count.status === 'DRAFT' && (
                              <>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  onClick={() => handleStartStockCount(count.id)}
                                >
                                  <Play className="h-4 w-4" />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  onClick={() => handleCancelStockCount(count.id)}
                                  className="text-red-600"
                                >
                                  <XCircle className="h-4 w-4" />
                                </Button>
                              </>
                            )}
                            {count.status === 'PENDING_REVIEW' && (
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleApproveStockCount(count.id)}
                                className="text-green-600"
                              >
                                <CheckCircle className="h-4 w-4" />
                              </Button>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Suppliers Tab */}
        <TabsContent value="suppliers" className="space-y-6">
          {/* Stats Cards */}
          <div className="grid gap-4 md:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.suppliers.stats.total', 'Total Suppliers')}
                </CardTitle>
                <Truck className="h-4 w-4 text-blue-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{suppliers.length}</div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.suppliers.stats.active', 'Active')}
                </CardTitle>
                <CheckCircle className="h-4 w-4 text-green-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-green-600">
                  {suppliers.filter((s) => s.active).length}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.suppliers.stats.avgRating', 'Avg. Rating')}
                </CardTitle>
                <Star className="h-4 w-4 text-yellow-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {suppliers.length > 0
                    ? (suppliers.reduce((sum, s) => sum + (parseFloat(s.rating) || 0), 0) / suppliers.length).toFixed(1)
                    : '0.0'}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.suppliers.stats.topPerformer', 'Top Performer')}
                </CardTitle>
                <TrendingUp className="h-4 w-4 text-purple-600" />
              </CardHeader>
              <CardContent>
                <div className="text-lg font-bold truncate">
                  {suppliers.length > 0
                    ? suppliers.reduce((top, s) => (parseFloat(s.rating) || 0) > (parseFloat(top.rating) || 0) ? s : top, suppliers[0])?.name || '-'
                    : '-'}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Suppliers Table */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <Truck className="h-5 w-5" />
                    {t('inventory.suppliers.title', 'Suppliers')}
                  </CardTitle>
                  <CardDescription>
                    {t('inventory.suppliers.subtitle', 'Manage your ingredient suppliers')}
                  </CardDescription>
                </div>
                <Button onClick={handleAddSupplier}>
                  <Plus className="h-4 w-4 mr-2" />
                  {t('inventory.suppliers.addSupplier', 'Add Supplier')}
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.suppliers.fields.name', 'Company Name')}</TableHead>
                    <TableHead>{t('inventory.suppliers.fields.code', 'Code')}</TableHead>
                    <TableHead>{t('inventory.suppliers.fields.contactPerson', 'Contact')}</TableHead>
                    <TableHead>{t('inventory.suppliers.fields.phone', 'Phone')}</TableHead>
                    <TableHead>{t('inventory.suppliers.fields.paymentTerms', 'Payment Terms')}</TableHead>
                    <TableHead>{t('inventory.suppliers.fields.rating', 'Rating')}</TableHead>
                    <TableHead>{t('inventory.stockAlerts.status', 'Status')}</TableHead>
                    <TableHead className="text-right">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {suppliers.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                        {t('inventory.suppliers.noSuppliers', 'No suppliers found. Add your first supplier.')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    suppliers.map((supplier) => (
                      <TableRow key={supplier.id}>
                        <TableCell className="font-medium">
                          <div>
                            <div>{supplier.name}</div>
                            {supplier.email && (
                              <div className="text-xs text-muted-foreground flex items-center gap-1">
                                <Mail className="h-3 w-3" />
                                {supplier.email}
                              </div>
                            )}
                          </div>
                        </TableCell>
                        <TableCell>{supplier.code || '-'}</TableCell>
                        <TableCell>{supplier.contactPerson || '-'}</TableCell>
                        <TableCell>
                          {supplier.phone && (
                            <div className="flex items-center gap-1">
                              <Phone className="h-3 w-3" />
                              {supplier.phone}
                            </div>
                          )}
                        </TableCell>
                        <TableCell>{supplier.paymentTerms || '-'}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            <Star className="h-4 w-4 text-yellow-500" />
                            {supplier.rating || '0.0'}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge className={supplier.active ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}>
                            {supplier.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleToggleSupplier(supplier.id)}
                              title={supplier.active ? t('common.deactivate', 'Deactivate') : t('common.activate', 'Activate')}
                            >
                              <Power className={`h-4 w-4 ${supplier.active ? 'text-green-600' : 'text-gray-400'}`} />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleEditSupplier(supplier)}
                              title={t('common.edit', 'Edit')}
                            >
                              <Edit className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleDeleteSupplier(supplier.id)}
                              title={t('common.delete', 'Delete')}
                            >
                              <Trash2 className="h-4 w-4 text-red-600" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Stock Alerts Tab */}
        <TabsContent value="alerts" className="space-y-6">
          {/* Alert Summary */}
          {alertSummary && (
            <div className="grid gap-4 md:grid-cols-4">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('inventory.stockAlerts.lowStockItems', 'Low Stock Items')}
                  </CardTitle>
                  <AlertTriangle className="h-4 w-4 text-red-600" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold text-red-600">
                    {alertSummary.lowStockCount || 0}
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('inventory.stockAlerts.reorderItems', 'Reorder Items')}
                  </CardTitle>
                  <TrendingDown className="h-4 w-4 text-yellow-600" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold text-yellow-600">
                    {alertSummary.reorderCount || 0}
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('inventory.stockAlerts.activeSubscriptions', 'Active Subscriptions')}
                  </CardTitle>
                  <Bell className="h-4 w-4 text-blue-600" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">
                    {subscriptions.filter((s) => s.active).length}
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('inventory.stockAlerts.sendAlert', 'Send Alert')}
                  </CardTitle>
                  <Send className="h-4 w-4 text-green-600" />
                </CardHeader>
                <CardContent>
                  <Button
                    onClick={handleTriggerAlert}
                    size="sm"
                    className="w-full"
                    disabled={subscriptions.filter((s) => s.active).length === 0}
                  >
                    <Send className="h-4 w-4 mr-2" />
                    {t('inventory.stockAlerts.trigger', 'Trigger Now')}
                  </Button>
                </CardContent>
              </Card>
            </div>
          )}

          {/* Subscriptions Table */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <Bell className="h-5 w-5" />
                    {t('inventory.stockAlerts.subscriptions', 'Telegram Subscriptions')}
                  </CardTitle>
                  <CardDescription>
                    {t('inventory.stockAlerts.subscriptionsDesc', 'Manage Telegram chat subscriptions for stock alerts')}
                  </CardDescription>
                </div>
                <Button onClick={handleAddSubscription}>
                  <Plus className="h-4 w-4 mr-2" />
                  {t('inventory.stockAlerts.addSubscription', 'Add Subscription')}
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.stockAlerts.subscriberName', 'Subscriber')}</TableHead>
                    <TableHead>{t('inventory.stockAlerts.chatId', 'Telegram Chat ID')}</TableHead>
                    <TableHead>{t('inventory.stockAlerts.lowStockAlerts', 'Low Stock')}</TableHead>
                    <TableHead>{t('inventory.stockAlerts.reorderAlerts', 'Reorder')}</TableHead>
                    <TableHead>{t('inventory.stockAlerts.status', 'Status')}</TableHead>
                    <TableHead>{t('inventory.stockAlerts.lastAlert', 'Last Alert')}</TableHead>
                    <TableHead className="text-right">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {subscriptions.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                        {t('inventory.stockAlerts.noSubscriptions', 'No subscriptions found. Add a Telegram chat to receive stock alerts.')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    subscriptions.map((subscription) => (
                      <TableRow key={subscription.id}>
                        <TableCell className="font-medium">
                          {subscription.subscriberName || '-'}
                        </TableCell>
                        <TableCell>{subscription.telegramChatId}</TableCell>
                        <TableCell>
                          <Badge className={subscription.alertOnLowStock ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}>
                            {subscription.alertOnLowStock ? t('common.yes', 'Yes') : t('common.no', 'No')}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge className={subscription.alertOnReorder ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}>
                            {subscription.alertOnReorder ? t('common.yes', 'Yes') : t('common.no', 'No')}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge className={subscription.active ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}>
                            {subscription.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {subscription.lastAlertSentAt
                            ? formatDateTime(subscription.lastAlertSentAt)
                            : t('inventory.stockAlerts.never', 'Never')}
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleToggleSubscription(subscription.id)}
                              title={subscription.active ? t('common.deactivate', 'Deactivate') : t('common.activate', 'Activate')}
                            >
                              <Power className={`h-4 w-4 ${subscription.active ? 'text-green-600' : 'text-gray-400'}`} />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleEditSubscription(subscription)}
                              title={t('common.edit', 'Edit')}
                            >
                              <Edit className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleDeleteSubscription(subscription.id)}
                              title={t('common.delete', 'Delete')}
                            >
                              <Trash2 className="h-4 w-4 text-red-600" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* Instructions Card */}
          <Card>
            <CardHeader>
              <CardTitle>{t('inventory.stockAlerts.howToSetup', 'How to Get Telegram Chat ID')}</CardTitle>
            </CardHeader>
            <CardContent>
              <ol className="list-decimal list-inside space-y-2 text-sm text-muted-foreground">
                <li>{t('inventory.stockAlerts.step1', 'Start a chat with your Telegram bot')}</li>
                <li>{t('inventory.stockAlerts.step2', 'Send the /start command to the bot')}</li>
                <li>{t('inventory.stockAlerts.step3', 'The bot will reply with your Chat ID')}</li>
                <li>{t('inventory.stockAlerts.step4', 'Copy the Chat ID and add it as a subscription here')}</li>
              </ol>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Waste Tab */}
        <TabsContent value="waste" className="space-y-6">
          {/* Stats Cards */}
          <div className="grid gap-4 md:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.waste.totalWaste', 'Total Waste (30 days)')}
                </CardTitle>
                <Trash2 className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-red-600">
                  ${wasteReport?.totalWasteCost?.toFixed(2) || '0.00'}
                </div>
                <p className="text-xs text-muted-foreground">
                  {wasteReport?.recordCount || 0} {t('inventory.waste.records', 'records')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.waste.mostCommonReason', 'Most Common Reason')}
                </CardTitle>
                <AlertTriangle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {wasteReport?.mostCommonReason || '-'}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.waste.totalQuantity', 'Total Quantity')}
                </CardTitle>
                <TrendingDown className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {wasteReport?.totalWasteQuantity?.toFixed(2) || '0'}
                </div>
                <p className="text-xs text-muted-foreground">
                  {t('inventory.waste.units', 'units wasted')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('inventory.waste.avgPerRecord', 'Avg Cost per Record')}
                </CardTitle>
                <History className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  ${wasteReport?.recordCount > 0
                    ? (wasteReport.totalWasteCost / wasteReport.recordCount).toFixed(2)
                    : '0.00'}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Waste Records Table */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <Trash2 className="h-5 w-5" />
                    {t('inventory.waste.records', 'Waste Records')}
                  </CardTitle>
                  <CardDescription>
                    {t('inventory.waste.recordsDesc', 'Track and analyze waste across your inventory')}
                  </CardDescription>
                </div>
                <Button onClick={() => setWasteModalOpen(true)}>
                  <Plus className="h-4 w-4 mr-2" />
                  {t('inventory.waste.recordWaste', 'Record Waste')}
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.waste.date', 'Date')}</TableHead>
                    <TableHead>{t('inventory.fields.ingredient', 'Ingredient')}</TableHead>
                    <TableHead>{t('inventory.waste.quantity', 'Quantity')}</TableHead>
                    <TableHead>{t('inventory.waste.reason', 'Reason')}</TableHead>
                    <TableHead>{t('inventory.waste.cost', 'Cost')}</TableHead>
                    <TableHead>{t('inventory.waste.recordedBy', 'Recorded By')}</TableHead>
                    <TableHead className="text-right">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {wasteRecords.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                        {t('inventory.waste.noRecords', 'No waste records found. Click "Record Waste" to add your first record.')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    wasteRecords.map((record) => (
                      <TableRow key={record.id}>
                        <TableCell>{record.wasteDate}</TableCell>
                        <TableCell className="font-medium">{record.ingredientName}</TableCell>
                        <TableCell>
                          {record.quantity} {record.unit}
                        </TableCell>
                        <TableCell>{getWasteReasonBadge(record.wasteReason)}</TableCell>
                        <TableCell className="text-red-600 font-medium">
                          ${record.totalCost?.toFixed(2) || '0.00'}
                        </TableCell>
                        <TableCell>{record.recordedBy || '-'}</TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleDeleteWaste(record.id)}
                            title={t('common.delete', 'Delete')}
                          >
                            <Trash2 className="h-4 w-4 text-red-600" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* Waste by Reason Breakdown */}
          {wasteReport?.wasteByReason && wasteReport.wasteByReason.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>{t('inventory.waste.byReason', 'Waste by Reason')}</CardTitle>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('inventory.waste.reason', 'Reason')}</TableHead>
                      <TableHead className="text-right">{t('inventory.waste.records', 'Records')}</TableHead>
                      <TableHead className="text-right">{t('inventory.waste.totalQuantity', 'Total Quantity')}</TableHead>
                      <TableHead className="text-right">{t('inventory.waste.totalCost', 'Total Cost')}</TableHead>
                      <TableHead className="text-right">{t('inventory.waste.percentage', '% of Total')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {wasteReport.wasteByReason.map((item) => (
                      <TableRow key={item.reason}>
                        <TableCell>{getWasteReasonBadge(item.reason)}</TableCell>
                        <TableCell className="text-right">{item.recordCount}</TableCell>
                        <TableCell className="text-right">{item.totalQuantity?.toFixed(2)}</TableCell>
                        <TableCell className="text-right text-red-600">${item.totalCost?.toFixed(2)}</TableCell>
                        <TableCell className="text-right">{item.percentageOfTotal?.toFixed(1)}%</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* Top Wasted Ingredients */}
          {wasteReport?.topWastedIngredients && wasteReport.topWastedIngredients.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>{t('inventory.waste.topWasted', 'Top Wasted Ingredients')}</CardTitle>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('inventory.fields.ingredient', 'Ingredient')}</TableHead>
                      <TableHead className="text-right">{t('inventory.waste.records', 'Records')}</TableHead>
                      <TableHead className="text-right">{t('inventory.waste.totalQuantity', 'Total Quantity')}</TableHead>
                      <TableHead className="text-right">{t('inventory.waste.totalCost', 'Total Cost')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {wasteReport.topWastedIngredients.map((item) => (
                      <TableRow key={item.ingredientId}>
                        <TableCell className="font-medium">{item.ingredientName}</TableCell>
                        <TableCell className="text-right">{item.recordCount}</TableCell>
                        <TableCell className="text-right">{item.totalQuantity?.toFixed(2)}</TableCell>
                        <TableCell className="text-right text-red-600">${item.totalCost?.toFixed(2)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
        </TabsContent>
      </Tabs>

      {/* Add/Edit Ingredient Modal */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingIngredient ? t('inventory.editIngredient') : t('inventory.addIngredient')}
            </DialogTitle>
            <DialogDescription>
              {editingIngredient ? t('inventory.editDescription') : t('inventory.addDescription')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="name">{t('inventory.fields.name')} *</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder={t('inventory.placeholders.name')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="sku">{t('inventory.fields.sku')}</Label>
                <Input
                  id="sku"
                  value={formData.sku}
                  onChange={(e) => setFormData({ ...formData, sku: e.target.value })}
                  placeholder={t('inventory.placeholders.sku')}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">{t('inventory.fields.description')}</Label>
              <Input
                id="description"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder={t('inventory.placeholders.description')}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="unit">{t('inventory.fields.unit')} *</Label>
                <Select value={formData.unit} onValueChange={(value) => setFormData({ ...formData, unit: value })}>
                  <SelectTrigger id="unit">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="kg">{t('inventory.units.kg')}</SelectItem>
                    <SelectItem value="g">{t('inventory.units.g')}</SelectItem>
                    <SelectItem value="L">{t('inventory.units.L')}</SelectItem>
                    <SelectItem value="ml">{t('inventory.units.ml')}</SelectItem>
                    <SelectItem value="pieces">{t('inventory.units.pieces')}</SelectItem>
                    <SelectItem value="dozens">{t('inventory.units.dozens')}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="supplierId">{t('inventory.fields.supplier')}</Label>
                <Select
                  value={formData.supplierId || 'none'}
                  onValueChange={(value) => setFormData({ ...formData, supplierId: value === 'none' ? '' : value })}
                >
                  <SelectTrigger id="supplierId">
                    <SelectValue placeholder={t('inventory.placeholders.selectSupplier', 'Select supplier')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">{t('common.none', 'None')}</SelectItem>
                    {suppliers.filter(s => s.active).map((supplier) => (
                      <SelectItem key={supplier.id} value={supplier.id.toString()}>
                        {supplier.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="currentStock">{t('inventory.fields.currentStock')} *</Label>
                <Input
                  id="currentStock"
                  type="number"
                  step="0.001"
                  value={formData.currentStock}
                  onChange={(e) => setFormData({ ...formData, currentStock: e.target.value })}
                  placeholder={t("common.placeholders.decimalValue")}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="minimumStock">{t('inventory.fields.minimumStock')} *</Label>
                <Input
                  id="minimumStock"
                  type="number"
                  step="0.001"
                  value={formData.minimumStock}
                  onChange={(e) => setFormData({ ...formData, minimumStock: e.target.value })}
                  placeholder={t("common.placeholders.decimalValue")}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reorderLevel">{t('inventory.fields.reorderLevel')} *</Label>
                <Input
                  id="reorderLevel"
                  type="number"
                  step="0.001"
                  value={formData.reorderLevel}
                  onChange={(e) => setFormData({ ...formData, reorderLevel: e.target.value })}
                  placeholder={t("common.placeholders.decimalValue")}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="costPerUnit">{t('inventory.fields.costPerUnit')}</Label>
              <Input
                id="costPerUnit"
                type="number"
                step="0.01"
                value={formData.costPerUnit}
                onChange={(e) => setFormData({ ...formData, costPerUnit: e.target.value })}
                placeholder={t("common.placeholders.priceValue")}
              />
            </div>

            <div className="flex gap-4">
              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="active"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="active" className="font-normal cursor-pointer">
                  {t('inventory.fields.active')}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="trackInventory"
                  checked={formData.trackInventory}
                  onChange={(e) => setFormData({ ...formData, trackInventory: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="trackInventory" className="font-normal cursor-pointer">
                  {t('inventory.fields.trackInventory')}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="trackExpiry"
                  checked={formData.trackExpiry}
                  onChange={(e) => setFormData({ ...formData, trackExpiry: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="trackExpiry" className="font-normal cursor-pointer">
                  {t('inventory.fields.trackExpiry', 'Track Expiry')}
                </Label>
              </div>
            </div>

            {formData.trackExpiry && (
              <div className="grid grid-cols-2 gap-4 p-4 border rounded-lg bg-muted/50">
                <div className="space-y-2">
                  <Label htmlFor="defaultShelfLifeDays">{t('inventory.expiry.fields.shelfLife', 'Default Shelf Life (days)')}</Label>
                  <Input
                    id="defaultShelfLifeDays"
                    type="number"
                    value={formData.defaultShelfLifeDays}
                    onChange={(e) => setFormData({ ...formData, defaultShelfLifeDays: e.target.value })}
                    placeholder={t('inventory.expiry.placeholders.shelfLife', 'e.g., 30')}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="expiryAlertDays">{t('inventory.expiry.fields.alertDays', 'Alert Before Expiry (days)')}</Label>
                  <Input
                    id="expiryAlertDays"
                    type="number"
                    value={formData.expiryAlertDays}
                    onChange={(e) => setFormData({ ...formData, expiryAlertDays: e.target.value })}
                    placeholder={t('inventory.expiry.placeholders.alertDays', 'e.g., 7')}
                  />
                </div>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSave}>
              {editingIngredient ? t('common.save') : t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Stock Management Modal */}
      <Dialog open={stockModalOpen} onOpenChange={setStockModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {stockAction === 'add' ? t('inventory.addStock', 'Add Stock') : t('inventory.adjustStock', 'Adjust Stock')} - {selectedIngredient?.name}
            </DialogTitle>
            <DialogDescription>
              {t('inventory.currentStockLabel', 'Current stock: {{currentStock}} {{unit}}', { currentStock: selectedIngredient?.currentStock, unit: selectedIngredient?.unit })}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            {stockAction === 'add' ? (
              <div className="space-y-2">
                <Label htmlFor="quantity">{t('inventory.quantityToAdd', 'Quantity to Add')} *</Label>
                <Input
                  id="quantity"
                  type="number"
                  step="0.001"
                  value={stockFormData.quantity}
                  onChange={(e) => setStockFormData({ ...stockFormData, quantity: e.target.value })}
                  placeholder={t("common.placeholders.decimalValue")}
                />
              </div>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="newQuantity">{t('inventory.newQuantity', 'New Quantity')} *</Label>
                <Input
                  id="newQuantity"
                  type="number"
                  step="0.001"
                  value={stockFormData.newQuantity}
                  onChange={(e) => setStockFormData({ ...stockFormData, newQuantity: e.target.value })}
                  placeholder={t("common.placeholders.decimalValue")}
                />
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="notes">{t('common.notes', 'Notes')}</Label>
              <Input
                id="notes"
                value={stockFormData.notes}
                onChange={(e) => setStockFormData({ ...stockFormData, notes: e.target.value })}
                placeholder={t("common.placeholders.optionalNotes")}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="performedBy">{t('inventory.performedBy', 'Performed By')}</Label>
              <Input
                id="performedBy"
                value={stockFormData.performedBy}
                onChange={(e) => setStockFormData({ ...stockFormData, performedBy: e.target.value })}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStockModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleStockSubmit}>
              {stockAction === 'add' ? t('inventory.addStock', 'Add Stock') : t('inventory.updateStock', 'Update Stock')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Transaction History Modal */}
      <Dialog open={transactionModalOpen} onOpenChange={setTransactionModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('inventory.transactionHistory', 'Transaction History')} - {selectedIngredient?.name}</DialogTitle>
            <DialogDescription>
              {t('inventory.transactionHistoryDesc', 'All stock movements for this ingredient')}
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('inventory.transactionFields.date', 'Date')}</TableHead>
                  <TableHead>{t('inventory.transactionFields.type', 'Type')}</TableHead>
                  <TableHead>{t('inventory.transactionFields.change', 'Change')}</TableHead>
                  <TableHead>{t('inventory.transactionFields.balanceAfter', 'Balance After')}</TableHead>
                  <TableHead>{t('inventory.transactionFields.performedBy', 'Performed By')}</TableHead>
                  <TableHead>{t('inventory.transactionFields.notes', 'Notes')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8">
                      {t('inventory.noTransactions', 'No transactions found')}
                    </TableCell>
                  </TableRow>
                ) : (
                  transactions.map((transaction) => (
                    <TableRow key={transaction.id}>
                      <TableCell>{formatDateTime(transaction.createdAt)}</TableCell>
                      <TableCell>
                        <Badge className={
                          transaction.type === 'PURCHASE' || transaction.type === 'RESTOCK' || transaction.type === 'INITIAL_STOCK'
                            ? 'bg-green-100 text-green-800'
                            : transaction.type === 'ORDER_DEDUCTION' || transaction.type === 'WASTE'
                            ? 'bg-red-100 text-red-800'
                            : 'bg-blue-100 text-blue-800'
                        }>
                          {transaction.type}
                        </Badge>
                      </TableCell>
                      <TableCell className={
                        transaction.quantity > 0 ? 'text-green-600 font-semibold' : 'text-red-600 font-semibold'
                      }>
                        {transaction.quantity > 0 ? '+' : ''}{transaction.quantity}
                      </TableCell>
                      <TableCell>{transaction.balanceAfter}</TableCell>
                      <TableCell>{transaction.performedBy || '-'}</TableCell>
                      <TableCell className="max-w-xs truncate">{transaction.notes || '-'}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
          <DialogFooter>
            <Button onClick={() => setTransactionModalOpen(false)}>
              {t('common.close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Recipe Modal */}
      <Dialog open={recipeModalOpen} onOpenChange={setRecipeModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingRecipe
                ? t('inventory.recipes.editIngredient', 'Edit Ingredient Link')
                : t('inventory.recipes.addIngredient', 'Add Ingredient to Recipe')}
            </DialogTitle>
            <DialogDescription>
              {selectedProduct
                ? `${t('inventory.recipes.for', 'For')} ${selectedProduct.name}`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="ingredientId">{t('inventory.recipes.ingredient', 'Ingredient')} *</Label>
              <Select
                value={recipeFormData.ingredientId}
                onValueChange={(value) => setRecipeFormData({ ...recipeFormData, ingredientId: value })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t('inventory.recipes.selectIngredient', 'Select an ingredient')} />
                </SelectTrigger>
                <SelectContent>
                  {ingredients.map((ingredient) => (
                    <SelectItem key={ingredient.id} value={ingredient.id.toString()}>
                      {ingredient.name} ({ingredient.unit})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="quantityRequired">{t('inventory.recipes.quantity', 'Quantity Required')} *</Label>
                <Input
                  id="quantityRequired"
                  type="number"
                  step="0.001"
                  value={recipeFormData.quantityRequired}
                  onChange={(e) => setRecipeFormData({ ...recipeFormData, quantityRequired: e.target.value })}
                  placeholder={t("common.placeholders.decimalValue")}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="recipeUnit">{t('inventory.recipes.unit', 'Unit')} *</Label>
                <Select
                  value={recipeFormData.unit}
                  onValueChange={(value) => setRecipeFormData({ ...recipeFormData, unit: value })}
                >
                  <SelectTrigger id="recipeUnit">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="kg">{t('inventory.units.kg')}</SelectItem>
                    <SelectItem value="g">{t('inventory.units.g')}</SelectItem>
                    <SelectItem value="L">{t('inventory.units.L')}</SelectItem>
                    <SelectItem value="ml">{t('inventory.units.ml')}</SelectItem>
                    <SelectItem value="pieces">{t('inventory.units.pieces')}</SelectItem>
                    <SelectItem value="dozens">{t('inventory.units.dozens')}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRecipeModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveRecipe}>
              {editingRecipe ? t('common.save') : t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Subscription Modal */}
      <Dialog open={subscriptionModalOpen} onOpenChange={setSubscriptionModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingSubscription
                ? t('inventory.stockAlerts.editSubscription', 'Edit Subscription')
                : t('inventory.stockAlerts.addSubscription', 'Add Subscription')}
            </DialogTitle>
            <DialogDescription>
              {t('inventory.stockAlerts.subscriptionDesc', 'Configure Telegram alerts for stock notifications')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="telegramChatId">{t('inventory.stockAlerts.chatId', 'Telegram Chat ID')} *</Label>
              <Input
                id="telegramChatId"
                type="number"
                value={subscriptionFormData.telegramChatId}
                onChange={(e) => setSubscriptionFormData({ ...subscriptionFormData, telegramChatId: e.target.value })}
                placeholder="123456789"
                disabled={!!editingSubscription}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="subscriberName">{t('inventory.stockAlerts.subscriberName', 'Subscriber Name')}</Label>
              <Input
                id="subscriberName"
                value={subscriptionFormData.subscriberName}
                onChange={(e) => setSubscriptionFormData({ ...subscriptionFormData, subscriberName: e.target.value })}
                placeholder={t('inventory.stockAlerts.namePlaceholder', 'e.g., Manager John')}
              />
            </div>
            <div className="flex gap-4">
              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="alertOnLowStock"
                  checked={subscriptionFormData.alertOnLowStock}
                  onChange={(e) => setSubscriptionFormData({ ...subscriptionFormData, alertOnLowStock: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="alertOnLowStock" className="font-normal cursor-pointer">
                  {t('inventory.stockAlerts.alertOnLowStock', 'Alert on Low Stock')}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="alertOnReorder"
                  checked={subscriptionFormData.alertOnReorder}
                  onChange={(e) => setSubscriptionFormData({ ...subscriptionFormData, alertOnReorder: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="alertOnReorder" className="font-normal cursor-pointer">
                  {t('inventory.stockAlerts.alertOnReorder', 'Alert on Reorder Level')}
                </Label>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSubscriptionModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveSubscription}>
              {editingSubscription ? t('common.save') : t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Supplier Modal */}
      <Dialog open={supplierModalOpen} onOpenChange={setSupplierModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingSupplier
                ? t('inventory.suppliers.editSupplier', 'Edit Supplier')
                : t('inventory.suppliers.addSupplier', 'Add Supplier')}
            </DialogTitle>
            <DialogDescription>
              {t('inventory.suppliers.subtitle', 'Manage your ingredient suppliers')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="supplierName">{t('inventory.suppliers.fields.name', 'Company Name')} *</Label>
                <Input
                  id="supplierName"
                  value={supplierFormData.name}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, name: e.target.value })}
                  placeholder={t('inventory.suppliers.placeholders.name', 'Enter company name')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="supplierCode">{t('inventory.suppliers.fields.code', 'Supplier Code')}</Label>
                <Input
                  id="supplierCode"
                  value={supplierFormData.code}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, code: e.target.value })}
                  placeholder={t('inventory.suppliers.placeholders.code', 'e.g., SUP-001')}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="contactPerson">{t('inventory.suppliers.fields.contactPerson', 'Contact Person')}</Label>
                <Input
                  id="contactPerson"
                  value={supplierFormData.contactPerson}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, contactPerson: e.target.value })}
                  placeholder={t('inventory.suppliers.placeholders.contactPerson', 'Contact person name')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="supplierPhone">{t('inventory.suppliers.fields.phone', 'Phone')}</Label>
                <Input
                  id="supplierPhone"
                  value={supplierFormData.phone}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, phone: e.target.value })}
                  placeholder={t('inventory.suppliers.placeholders.phone', '+998 XX XXX XX XX')}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="supplierEmail">{t('inventory.suppliers.fields.email', 'Email')}</Label>
              <Input
                id="supplierEmail"
                type="email"
                value={supplierFormData.email}
                onChange={(e) => setSupplierFormData({ ...supplierFormData, email: e.target.value })}
                placeholder={t('inventory.suppliers.placeholders.email', 'supplier@example.com')}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="supplierAddress">{t('inventory.suppliers.fields.address', 'Address')}</Label>
              <Input
                id="supplierAddress"
                value={supplierFormData.address}
                onChange={(e) => setSupplierFormData({ ...supplierFormData, address: e.target.value })}
                placeholder={t('inventory.suppliers.placeholders.address', 'Full address')}
              />
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="paymentTerms">{t('inventory.suppliers.fields.paymentTerms', 'Payment Terms')}</Label>
                <Select
                  value={supplierFormData.paymentTerms}
                  onValueChange={(value) => setSupplierFormData({ ...supplierFormData, paymentTerms: value })}
                >
                  <SelectTrigger id="paymentTerms">
                    <SelectValue placeholder={t('common.select', 'Select...')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="COD">{t('inventory.suppliers.paymentTerms.cod', 'Cash on Delivery')}</SelectItem>
                    <SelectItem value="NET15">{t('inventory.suppliers.paymentTerms.net15', 'Net 15 Days')}</SelectItem>
                    <SelectItem value="NET30">{t('inventory.suppliers.paymentTerms.net30', 'Net 30 Days')}</SelectItem>
                    <SelectItem value="NET60">{t('inventory.suppliers.paymentTerms.net60', 'Net 60 Days')}</SelectItem>
                    <SelectItem value="PREPAID">{t('inventory.suppliers.paymentTerms.prepaid', 'Prepaid')}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="creditLimit">{t('inventory.suppliers.fields.creditLimit', 'Credit Limit')}</Label>
                <Input
                  id="creditLimit"
                  type="number"
                  step="0.01"
                  value={supplierFormData.creditLimit}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, creditLimit: e.target.value })}
                  placeholder="0.00"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="currency">{t('inventory.suppliers.fields.currency', 'Currency')}</Label>
                <Select
                  value={supplierFormData.currency}
                  onValueChange={(value) => setSupplierFormData({ ...supplierFormData, currency: value })}
                >
                  <SelectTrigger id="currency">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="UZS">UZS</SelectItem>
                    <SelectItem value="USD">USD</SelectItem>
                    <SelectItem value="EUR">EUR</SelectItem>
                    <SelectItem value="RUB">RUB</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="supplierNotes">{t('inventory.suppliers.fields.notes', 'Notes')}</Label>
              <Input
                id="supplierNotes"
                value={supplierFormData.notes}
                onChange={(e) => setSupplierFormData({ ...supplierFormData, notes: e.target.value })}
                placeholder={t('inventory.suppliers.placeholders.notes', 'Additional notes...')}
              />
            </div>

            <div className="flex items-center space-x-2">
              <input
                type="checkbox"
                id="supplierActive"
                checked={supplierFormData.active}
                onChange={(e) => setSupplierFormData({ ...supplierFormData, active: e.target.checked })}
                className="h-4 w-4"
              />
              <Label htmlFor="supplierActive" className="font-normal cursor-pointer">
                {t('inventory.suppliers.fields.active', 'Active')}
              </Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSupplierModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveSupplier}>
              {editingSupplier ? t('common.save') : t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Add Batch Modal */}
      <Dialog open={batchModalOpen} onOpenChange={setBatchModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('inventory.expiry.addBatch', 'Add New Batch')}</DialogTitle>
            <DialogDescription>
              {t('inventory.expiry.addBatchDesc', 'Create a new batch with expiry tracking')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="batchIngredientId">{t('inventory.expiry.fields.ingredient', 'Ingredient')} *</Label>
                <Select
                  value={batchFormData.ingredientId}
                  onValueChange={(value) => setBatchFormData({ ...batchFormData, ingredientId: value })}
                >
                  <SelectTrigger id="batchIngredientId">
                    <SelectValue placeholder={t('inventory.expiry.placeholders.selectIngredient', 'Select ingredient')} />
                  </SelectTrigger>
                  <SelectContent>
                    {ingredients.filter(ing => ing.trackExpiry).map((ingredient) => (
                      <SelectItem key={ingredient.id} value={ingredient.id.toString()}>
                        {ingredient.name} ({ingredient.unit})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="batchNumber">{t('inventory.expiry.fields.batchNumber', 'Batch Number')} *</Label>
                <Input
                  id="batchNumber"
                  value={batchFormData.batchNumber}
                  onChange={(e) => setBatchFormData({ ...batchFormData, batchNumber: e.target.value })}
                  placeholder={t('inventory.expiry.placeholders.batchNumber', 'e.g., BATCH-001')}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="batchQuantity">{t('inventory.expiry.fields.quantity', 'Quantity')} *</Label>
                <Input
                  id="batchQuantity"
                  type="number"
                  step="0.001"
                  value={batchFormData.quantity}
                  onChange={(e) => setBatchFormData({ ...batchFormData, quantity: e.target.value })}
                  placeholder="0.000"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="costPerUnit">{t('inventory.expiry.fields.costPerUnit', 'Cost Per Unit')}</Label>
                <Input
                  id="costPerUnit"
                  type="number"
                  step="0.01"
                  value={batchFormData.costPerUnit}
                  onChange={(e) => setBatchFormData({ ...batchFormData, costPerUnit: e.target.value })}
                  placeholder="0.00"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="receivedDate">{t('inventory.expiry.fields.receivedDate', 'Received Date')} *</Label>
                <Input
                  id="receivedDate"
                  type="date"
                  value={batchFormData.receivedDate}
                  onChange={(e) => setBatchFormData({ ...batchFormData, receivedDate: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="expiryDate">{t('inventory.expiry.fields.expiryDate', 'Expiry Date')}</Label>
                <Input
                  id="expiryDate"
                  type="date"
                  value={batchFormData.expiryDate}
                  onChange={(e) => setBatchFormData({ ...batchFormData, expiryDate: e.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="batchSupplierId">{t('inventory.expiry.fields.supplier', 'Supplier')}</Label>
                <Select
                  value={batchFormData.supplierId || 'none'}
                  onValueChange={(value) => setBatchFormData({ ...batchFormData, supplierId: value === 'none' ? '' : value })}
                >
                  <SelectTrigger id="batchSupplierId">
                    <SelectValue placeholder={t('common.none', 'None')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">{t('common.none', 'None')}</SelectItem>
                    {suppliers.filter(s => s.active).map((supplier) => (
                      <SelectItem key={supplier.id} value={supplier.id.toString()}>
                        {supplier.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="poReference">{t('inventory.expiry.fields.poReference', 'PO Reference')}</Label>
                <Input
                  id="poReference"
                  value={batchFormData.poReference}
                  onChange={(e) => setBatchFormData({ ...batchFormData, poReference: e.target.value })}
                  placeholder={t('inventory.expiry.placeholders.poReference', 'e.g., PO-2024-001')}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="batchNotes">{t('common.notes', 'Notes')}</Label>
              <Input
                id="batchNotes"
                value={batchFormData.notes}
                onChange={(e) => setBatchFormData({ ...batchFormData, notes: e.target.value })}
                placeholder={t('common.placeholders.optionalNotes', 'Optional notes...')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBatchModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveBatch}>
              {t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Batch Details Modal */}
      <Dialog open={batchDetailModalOpen} onOpenChange={setBatchDetailModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {t('inventory.expiry.batchesFor', 'Batches for')} {selectedIngredient?.name}
            </DialogTitle>
            <DialogDescription>
              {t('inventory.expiry.batchesDesc', 'All active batches for this ingredient (FEFO order)')}
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('inventory.expiry.fields.batchNumber', 'Batch #')}</TableHead>
                  <TableHead>{t('inventory.expiry.fields.quantity', 'Quantity')}</TableHead>
                  <TableHead>{t('inventory.expiry.fields.receivedDate', 'Received')}</TableHead>
                  <TableHead>{t('inventory.expiry.fields.expiryDate', 'Expiry')}</TableHead>
                  <TableHead>{t('inventory.expiry.fields.daysRemaining', 'Days Left')}</TableHead>
                  <TableHead>{t('inventory.expiry.fields.status', 'Status')}</TableHead>
                  <TableHead className="text-right">{t('common.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {selectedIngredientBatches.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                      {t('inventory.expiry.noBatches', 'No batches found for this ingredient')}
                    </TableCell>
                  </TableRow>
                ) : (
                  selectedIngredientBatches.map((batch) => (
                    <TableRow key={batch.id} className={batch.isExpired ? 'bg-red-50' : batch.isExpiringSoon ? 'bg-yellow-50' : ''}>
                      <TableCell className="font-medium">{batch.batchNumber}</TableCell>
                      <TableCell>{batch.quantity} {selectedIngredient?.unit}</TableCell>
                      <TableCell>{batch.receivedDate}</TableCell>
                      <TableCell>{batch.expiryDate || '-'}</TableCell>
                      <TableCell className={batch.isExpired ? 'text-red-600 font-semibold' : batch.isExpiringSoon ? 'text-yellow-600 font-semibold' : ''}>
                        {batch.daysUntilExpiry !== null ? `${batch.daysUntilExpiry} ${t('common.days', 'days')}` : '-'}
                      </TableCell>
                      <TableCell>{getExpiryStatusBadge(batch)}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleWriteOff(batch)}
                          disabled={batch.status === 'WRITTEN_OFF'}
                        >
                          <Trash2 className="h-4 w-4 text-red-600" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
          <DialogFooter>
            <Button onClick={() => setBatchDetailModalOpen(false)}>
              {t('common.close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Write-Off Modal */}
      <Dialog open={writeOffModalOpen} onOpenChange={setWriteOffModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="text-red-600">
              {t('inventory.expiry.writeOffBatch', 'Write Off Batch')}
            </DialogTitle>
            <DialogDescription>
              {t('inventory.expiry.writeOffDesc', 'This will permanently mark this batch as written off and remove the stock.')}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4 space-y-4">
            <div className="p-4 border rounded-lg bg-muted/50">
              <div className="grid grid-cols-2 gap-2 text-sm">
                <div>{t('inventory.expiry.fields.batchNumber', 'Batch #')}:</div>
                <div className="font-medium">{selectedBatch?.batchNumber}</div>
                <div>{t('inventory.expiry.fields.quantity', 'Quantity')}:</div>
                <div className="font-medium">{selectedBatch?.quantity} {selectedBatch?.unit}</div>
                <div>{t('inventory.expiry.fields.expiryDate', 'Expiry Date')}:</div>
                <div className="font-medium">{selectedBatch?.expiryDate || '-'}</div>
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="writeOffReason">{t('inventory.expiry.fields.reason', 'Reason for Write-Off')} *</Label>
              <Input
                id="writeOffReason"
                value={writeOffReason}
                onChange={(e) => setWriteOffReason(e.target.value)}
                placeholder={t('inventory.expiry.placeholders.reason', 'e.g., Expired, Damaged, Quality issue')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWriteOffModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleConfirmWriteOff} disabled={!writeOffReason.trim()}>
              {t('inventory.expiry.confirmWriteOff', 'Confirm Write-Off')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Stock Count Create Modal */}
      <Dialog open={stockCountModalOpen} onOpenChange={setStockCountModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('inventory.stockCounts.createTitle', 'Create Stock Count')}</DialogTitle>
            <DialogDescription>
              {t('inventory.stockCounts.createDescription', 'Start a new physical inventory count')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="countType">{t('inventory.stockCounts.fields.type', 'Count Type')}</Label>
              <Select
                value={stockCountFormData.countType}
                onValueChange={(value) => setStockCountFormData({ ...stockCountFormData, countType: value })}
              >
                <SelectTrigger id="countType">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="FULL">{t('inventory.stockCounts.type.full', 'Full Count')}</SelectItem>
                  <SelectItem value="CYCLE">{t('inventory.stockCounts.type.cycle', 'Cycle Count')}</SelectItem>
                  <SelectItem value="SPOT_CHECK">{t('inventory.stockCounts.type.spotCheck', 'Spot Check')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="scheduledDate">{t('inventory.stockCounts.fields.scheduledDate', 'Scheduled Date')}</Label>
              <Input
                id="scheduledDate"
                type="date"
                value={stockCountFormData.scheduledDate}
                onChange={(e) => setStockCountFormData({ ...stockCountFormData, scheduledDate: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="countNotes">{t('common.notes', 'Notes')}</Label>
              <Input
                id="countNotes"
                value={stockCountFormData.notes}
                onChange={(e) => setStockCountFormData({ ...stockCountFormData, notes: e.target.value })}
                placeholder={t('common.placeholders.optionalNotes', 'Optional notes...')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStockCountModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleCreateStockCount}>
              {t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Stock Count Detail Modal */}
      <Dialog open={stockCountDetailModalOpen} onOpenChange={setStockCountDetailModalOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              {selectedStockCount?.countNumber}
              {selectedStockCount && getStockCountStatusBadge(selectedStockCount.status)}
            </DialogTitle>
            <DialogDescription>
              {selectedStockCount && getCountTypeBadge(selectedStockCount.countType)}
              {selectedStockCount?.scheduledDate && (
                <span className="ml-2">
                  {t('inventory.stockCounts.scheduled', 'Scheduled')}: {new Date(selectedStockCount.scheduledDate).toLocaleDateString()}
                </span>
              )}
            </DialogDescription>
          </DialogHeader>

          {selectedStockCount && (
            <div className="space-y-4 py-4">
              {/* Progress */}
              <div className="flex items-center gap-4">
                <div className="flex-1">
                  <div className="flex justify-between text-sm mb-1">
                    <span>{t('inventory.stockCounts.progress', 'Progress')}</span>
                    <span>{selectedStockCount.countedItems}/{selectedStockCount.totalItems} {t('inventory.stockCounts.items', 'items')}</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className="bg-blue-600 h-2 rounded-full"
                      style={{ width: `${selectedStockCount.totalItems > 0 ? (selectedStockCount.countedItems / selectedStockCount.totalItems) * 100 : 0}%` }}
                    />
                  </div>
                </div>
                {selectedStockCount.varianceCount > 0 && (
                  <div className="text-right">
                    <div className="text-sm text-muted-foreground">{t('inventory.stockCounts.variances', 'Variances')}</div>
                    <div className="text-red-600 font-medium">
                      {selectedStockCount.varianceCount} {t('inventory.stockCounts.items', 'items')}
                    </div>
                  </div>
                )}
              </div>

              {/* Items Table */}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.stockCounts.fields.ingredient', 'Ingredient')}</TableHead>
                    <TableHead className="text-right">{t('inventory.stockCounts.fields.systemQty', 'System Qty')}</TableHead>
                    <TableHead className="text-right">{t('inventory.stockCounts.fields.countedQty', 'Counted Qty')}</TableHead>
                    <TableHead className="text-right">{t('inventory.stockCounts.fields.variance', 'Variance')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.fields.status', 'Status')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {selectedStockCount.items?.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <div className="font-medium">{item.ingredientName}</div>
                        <div className="text-sm text-muted-foreground">{item.unit}</div>
                      </TableCell>
                      <TableCell className="text-right">{item.systemQuantity}</TableCell>
                      <TableCell className="text-right">
                        {selectedStockCount.status === 'IN_PROGRESS' && item.status === 'PENDING' ? (
                          <Input
                            type="number"
                            step="0.001"
                            className="w-24 text-right"
                            placeholder="0"
                            onBlur={(e) => {
                              if (e.target.value) {
                                handleRecordCount(item.id, parseFloat(e.target.value));
                              }
                            }}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' && e.target.value) {
                                handleRecordCount(item.id, parseFloat(e.target.value));
                              }
                            }}
                          />
                        ) : (
                          item.countedQuantity ?? '-'
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        {item.varianceQuantity != null ? (
                          <span className={item.varianceQuantity !== 0 ? 'text-red-600 font-medium' : ''}>
                            {item.varianceQuantity > 0 ? '+' : ''}{item.varianceQuantity}
                            {item.variancePercentage != null && ` (${item.variancePercentage}%)`}
                          </span>
                        ) : '-'}
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.status === 'COUNTED' ? 'success' : 'secondary'}>
                          {item.status}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Actions */}
              <DialogFooter>
                <Button variant="outline" onClick={() => setStockCountDetailModalOpen(false)}>
                  {t('common.close')}
                </Button>
                {selectedStockCount.status === 'IN_PROGRESS' && selectedStockCount.countedItems === selectedStockCount.totalItems && (
                  <Button onClick={() => handleSubmitForReview(selectedStockCount.id)}>
                    {t('inventory.stockCounts.submitForReview', 'Submit for Review')}
                  </Button>
                )}
                {selectedStockCount.status === 'PENDING_REVIEW' && (
                  <Button onClick={() => handleApproveStockCount(selectedStockCount.id)} className="bg-green-600 hover:bg-green-700">
                    {t('inventory.stockCounts.approveAndAdjust', 'Approve & Adjust Inventory')}
                  </Button>
                )}
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Record Waste Modal */}
      <Dialog open={wasteModalOpen} onOpenChange={setWasteModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('inventory.waste.recordWaste', 'Record Waste')}</DialogTitle>
            <DialogDescription>
              {t('inventory.waste.recordWasteDesc', 'Record a waste event for an ingredient')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="wasteIngredient">{t('inventory.fields.ingredient', 'Ingredient')} *</Label>
              <Select
                value={wasteFormData.ingredientId}
                onValueChange={(value) => setWasteFormData({ ...wasteFormData, ingredientId: value })}
              >
                <SelectTrigger id="wasteIngredient">
                  <SelectValue placeholder={t('inventory.waste.selectIngredient', 'Select ingredient')} />
                </SelectTrigger>
                <SelectContent>
                  {ingredients.map((ingredient) => (
                    <SelectItem key={ingredient.id} value={ingredient.id.toString()}>
                      {ingredient.name} ({ingredient.currentStock} {ingredient.unit})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="wasteQuantity">{t('inventory.waste.quantity', 'Quantity')} *</Label>
                <Input
                  id="wasteQuantity"
                  type="number"
                  step="0.001"
                  value={wasteFormData.quantity}
                  onChange={(e) => setWasteFormData({ ...wasteFormData, quantity: e.target.value })}
                  placeholder="0.00"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="wasteDate">{t('inventory.waste.date', 'Date')} *</Label>
                <Input
                  id="wasteDate"
                  type="date"
                  value={wasteFormData.wasteDate}
                  onChange={(e) => setWasteFormData({ ...wasteFormData, wasteDate: e.target.value })}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="wasteReason">{t('inventory.waste.reason', 'Reason')} *</Label>
              <Select
                value={wasteFormData.wasteReason}
                onValueChange={(value) => setWasteFormData({ ...wasteFormData, wasteReason: value })}
              >
                <SelectTrigger id="wasteReason">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {wasteReasons.map((reason) => (
                    <SelectItem key={reason.value} value={reason.value}>
                      {reason.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="wasteNotes">{t('inventory.waste.notes', 'Notes')}</Label>
              <Input
                id="wasteNotes"
                value={wasteFormData.notes}
                onChange={(e) => setWasteFormData({ ...wasteFormData, notes: e.target.value })}
                placeholder={t('inventory.waste.notesPlaceholder', 'Optional notes about this waste')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWasteModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              onClick={handleRecordWaste}
              disabled={!wasteFormData.ingredientId || !wasteFormData.quantity}
            >
              {t('inventory.waste.record', 'Record')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
