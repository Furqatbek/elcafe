import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryAPI } from '../../services/api';
import { formatDateTime } from '../../utils/dateUtils';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Checkbox } from '../../components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../../components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../../components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../components/ui/table';
import {
  Package,
  Plus,
  Edit,
  Trash2,
  AlertTriangle,
  TrendingDown,
  CheckCircle,
  Search,
  TrendingUp,
  History,
  ShoppingCart,
  Settings2,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export default function InventoryIngredients() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { selectedRestaurant, ingredients, setIngredients, loadIngredients, suppliers, loading } = useInventory();

  // Local state
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

  // Filter ingredients
  useEffect(() => {
    filterIngredients();
  }, [ingredients, searchTerm, filterStatus]);

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

  // Stats
  const activeCount = ingredients.filter((ing) => ing.active).length;
  const lowStockCount = ingredients.filter((ing) => ing.currentStock <= ing.minimumStock).length;
  const reorderCount = ingredients.filter((ing) => ing.currentStock <= ing.reorderLevel && ing.currentStock > ing.minimumStock).length;

  const getStockStatus = (ingredient) => {
    if (!ingredient.active) return { label: t('common.inactive'), color: 'bg-gray-100 text-gray-800' };
    if (ingredient.currentStock <= ingredient.minimumStock) return { label: t('inventory.stats.lowStock'), color: 'bg-red-100 text-red-800' };
    if (ingredient.currentStock <= ingredient.reorderLevel) return { label: t('inventory.stats.reorder'), color: 'bg-yellow-100 text-yellow-800' };
    return { label: t('common.active'), color: 'bg-green-100 text-green-800' };
  };

  // Handlers
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

  const handleCreatePO = (ingredient) => {
    // Navigate to Purchase Orders page with pre-filled data
    const poData = {
      restaurantId: selectedRestaurant,
      supplierId: ingredient.supplierId,
      ingredientId: ingredient.id,
      ingredientName: ingredient.name,
      sku: ingredient.sku,
      unit: ingredient.unit,
      unitPrice: ingredient.costPerUnit,
      reorderQuantity: ingredient.reorderQuantity || Math.max(0, ingredient.reorderLevel - ingredient.currentStock),
    };
    // Navigate to purchase orders with state
    navigate('/purchase-orders', { state: { prefillData: poData } });
  };

  const isLowStock = (ingredient) => {
    return ingredient.currentStock <= ingredient.reorderLevel;
  };

  return (
    <InventoryLayout>
      <div className="space-y-6">
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
                            {isLowStock(ingredient) && ingredient.supplierId && (
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleCreatePO(ingredient)}
                                title={t("inventory.buttons.createPO", "Create Purchase Order")}
                                className="text-orange-600 hover:text-orange-800 hover:bg-orange-50"
                              >
                                <ShoppingCart className="h-4 w-4" />
                              </Button>
                            )}
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
                              onClick={() => handleStockAction(ingredient, 'adjust')}
                              title={t("common.buttons.adjustStock")}
                            >
                              <Settings2 className="h-4 w-4 text-purple-600" />
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
      </div>

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
                <Label htmlFor="minimumStock">{t('inventory.fields.minimumStock')}</Label>
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
                <Label htmlFor="reorderLevel">{t('inventory.fields.reorderLevel')}</Label>
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
                placeholder={t("common.placeholders.decimalValue")}
              />
            </div>

            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="active"
                  checked={formData.active}
                  onCheckedChange={(checked) => setFormData({ ...formData, active: checked })}
                />
                <Label htmlFor="active">{t('inventory.fields.active')}</Label>
              </div>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="trackInventory"
                  checked={formData.trackInventory}
                  onCheckedChange={(checked) => setFormData({ ...formData, trackInventory: checked })}
                />
                <Label htmlFor="trackInventory">{t('inventory.fields.trackInventory', 'Track Inventory')}</Label>
              </div>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="trackExpiry"
                  checked={formData.trackExpiry}
                  onCheckedChange={(checked) => setFormData({ ...formData, trackExpiry: checked })}
                />
                <Label htmlFor="trackExpiry">{t('inventory.fields.trackExpiry', 'Track Expiry')}</Label>
              </div>
            </div>

            {formData.trackExpiry && (
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="defaultShelfLifeDays">{t('inventory.fields.defaultShelfLife', 'Default Shelf Life (days)')}</Label>
                  <Input
                    id="defaultShelfLifeDays"
                    type="number"
                    value={formData.defaultShelfLifeDays}
                    onChange={(e) => setFormData({ ...formData, defaultShelfLifeDays: e.target.value })}
                    placeholder="30"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="expiryAlertDays">{t('inventory.fields.expiryAlertDays', 'Expiry Alert (days before)')}</Label>
                  <Input
                    id="expiryAlertDays"
                    type="number"
                    value={formData.expiryAlertDays}
                    onChange={(e) => setFormData({ ...formData, expiryAlertDays: e.target.value })}
                    placeholder="7"
                  />
                </div>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSave} disabled={!formData.name}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Stock Modal */}
      <Dialog open={stockModalOpen} onOpenChange={setStockModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {stockAction === 'add' ? t('inventory.addStock') : t('inventory.adjustStock')}
            </DialogTitle>
            <DialogDescription>
              {selectedIngredient && t('inventory.currentStockLabel', { currentStock: selectedIngredient.currentStock, unit: selectedIngredient.unit })}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            {stockAction === 'add' ? (
              <div className="space-y-2">
                <Label htmlFor="quantity">{t('inventory.quantityToAdd')}</Label>
                <Input
                  id="quantity"
                  type="number"
                  step="0.001"
                  value={stockFormData.quantity}
                  onChange={(e) => setStockFormData({ ...stockFormData, quantity: e.target.value })}
                />
              </div>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="newQuantity">{t('inventory.newQuantity')}</Label>
                <Input
                  id="newQuantity"
                  type="number"
                  step="0.001"
                  value={stockFormData.newQuantity}
                  onChange={(e) => setStockFormData({ ...stockFormData, newQuantity: e.target.value })}
                />
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="notes">{t('common.notes')}</Label>
              <Input
                id="notes"
                value={stockFormData.notes}
                onChange={(e) => setStockFormData({ ...stockFormData, notes: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="performedBy">{t('inventory.performedBy')}</Label>
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
              {t('inventory.updateStock')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Transaction History Modal */}
      <Dialog open={transactionModalOpen} onOpenChange={setTransactionModalOpen}>
        <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('inventory.transactionHistory')}</DialogTitle>
            <DialogDescription>
              {selectedIngredient && t('inventory.transactionHistoryDesc')}
            </DialogDescription>
          </DialogHeader>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('common.date')}</TableHead>
                <TableHead>{t('common.type')}</TableHead>
                <TableHead>{t('inventory.fields.quantity')}</TableHead>
                <TableHead>{t('inventory.performedBy')}</TableHead>
                <TableHead>{t('common.notes')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {transactions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                    {t('inventory.noTransactions')}
                  </TableCell>
                </TableRow>
              ) : (
                transactions.map((tx) => (
                  <TableRow key={tx.id}>
                    <TableCell>{formatDateTime(tx.createdAt)}</TableCell>
                    <TableCell>
                      <Badge variant={tx.transactionType === 'STOCK_IN' ? 'success' : tx.transactionType === 'STOCK_OUT' ? 'destructive' : 'secondary'}>
                        {tx.transactionType}
                      </Badge>
                    </TableCell>
                    <TableCell className={tx.quantity >= 0 ? 'text-green-600' : 'text-red-600'}>
                      {tx.quantity >= 0 ? '+' : ''}{tx.quantity}
                    </TableCell>
                    <TableCell>{tx.performedBy || '-'}</TableCell>
                    <TableCell>{tx.notes || '-'}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
