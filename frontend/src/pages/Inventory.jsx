import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
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
} from 'lucide-react';

export default function Inventory() {
  const { t } = useTranslation();
  const [ingredients, setIngredients] = useState([]);
  const [filteredIngredients, setFilteredIngredients] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');
  const [modalOpen, setModalOpen] = useState(false);
  const [stockModalOpen, setStockModalOpen] = useState(false);
  const [transactionModalOpen, setTransactionModalOpen] = useState(false);
  const [selectedIngredient, setSelectedIngredient] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [editingIngredient, setEditingIngredient] = useState(null);
  const [stockAction, setStockAction] = useState('add'); // 'add' or 'adjust'
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
    supplier: '',
    sku: '',
    active: true,
    trackInventory: true,
  });

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadIngredients();
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

  const filterIngredients = () => {
    let filtered = ingredients;

    // Filter by search term
    if (searchTerm) {
      filtered = filtered.filter(
        (ing) =>
          ing.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
          ing.sku?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          ing.supplier?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    // Filter by status
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
      supplier: '',
      sku: '',
      active: true,
      trackInventory: true,
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
      supplier: ingredient.supplier || '',
      sku: ingredient.sku || '',
      active: ingredient.active,
      trackInventory: ingredient.trackInventory,
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
          <Button onClick={loadIngredients} variant="outline" size="icon">
            <RefreshCw className="h-4 w-4" />
          </Button>
          <Button onClick={handleAddNew}>
            <Plus className="h-4 w-4 mr-2" />
            {t('inventory.addIngredient')}
          </Button>
        </div>
      </div>

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
                      <TableCell>{ingredient.supplier || '-'}</TableCell>
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

      {/* Add/Edit Modal */}
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
                <Label htmlFor="supplier">{t('inventory.fields.supplier')}</Label>
                <Input
                  id="supplier"
                  value={formData.supplier}
                  onChange={(e) => setFormData({ ...formData, supplier: e.target.value })}
                  placeholder={t('inventory.placeholders.supplier')}
                />
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
            </div>
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
                      <TableCell>{new Date(transaction.createdAt).toLocaleString()}</TableCell>
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
    </div>
  );
}
