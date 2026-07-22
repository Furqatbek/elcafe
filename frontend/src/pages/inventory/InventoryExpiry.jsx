import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryBatchAPI } from '../../services/api';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { Label } from '../../components/ui/label';
import { Input } from '../../components/ui/input';
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
  Trash2,
  RefreshCw,
  Calendar,
  Clock,
  XCircle,
  Layers,
} from 'lucide-react';

export default function InventoryExpiry() {
  const { t } = useTranslation();
  const { selectedRestaurant, ingredients, suppliers, loadIngredients } = useInventory();

  // Local state
  const [expirySummary, setExpirySummary] = useState(null);
  const [expiringBatches, setExpiringBatches] = useState([]);
  const [expiredBatches, setExpiredBatches] = useState([]);
  const [selectedIngredientBatches, setSelectedIngredientBatches] = useState([]);
  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [batchDetailModalOpen, setBatchDetailModalOpen] = useState(false);
  const [writeOffModalOpen, setWriteOffModalOpen] = useState(false);
  const [selectedBatch, setSelectedBatch] = useState(null);
  const [selectedIngredient, setSelectedIngredient] = useState(null);
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

  useEffect(() => {
    if (selectedRestaurant) {
      loadExpirySummary();
      loadExpiringBatches();
      loadExpiredBatches();
    }
  }, [selectedRestaurant]);

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

  const loadBatchesForIngredient = async (ingredientId) => {
    try {
      const response = await inventoryBatchAPI.getBatchesByIngredient(ingredientId);
      setSelectedIngredientBatches(response.data.data || []);
    } catch (error) {
      console.error('Failed to load batches:', error);
    }
  };

  const handleAddBatch = () => {
    setBatchFormData({
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
    setBatchModalOpen(true);
  };

  const handleSaveBatch = async () => {
    try {
      await inventoryBatchAPI.create({
        ingredientId: parseInt(batchFormData.ingredientId),
        batchNumber: batchFormData.batchNumber,
        quantity: parseFloat(batchFormData.quantity),
        receivedDate: batchFormData.receivedDate,
        expiryDate: batchFormData.expiryDate || null,
        costPerUnit: batchFormData.costPerUnit ? parseFloat(batchFormData.costPerUnit) : null,
        supplierId: batchFormData.supplierId ? parseInt(batchFormData.supplierId) : null,
        poReference: batchFormData.poReference || null,
        notes: batchFormData.notes || null,
      });
      setBatchModalOpen(false);
      loadExpirySummary();
      loadExpiringBatches();
      loadExpiredBatches();
      loadIngredients();
    } catch (error) {
      console.error('Failed to save batch:', error);
      alert('Failed to save batch');
    }
  };

  const handleWriteOff = (batch) => {
    setSelectedBatch(batch);
    setWriteOffReason('');
    setWriteOffModalOpen(true);
  };

  const handleConfirmWriteOff = async () => {
    if (!selectedBatch) return;
    try {
      await inventoryBatchAPI.writeOff(selectedBatch.id, writeOffReason || 'Expired');
      setWriteOffModalOpen(false);
      loadExpirySummary();
      loadExpiringBatches();
      loadExpiredBatches();
      loadIngredients();
    } catch (error) {
      console.error('Failed to write off batch:', error);
      alert('Failed to write off batch');
    }
  };

  const handleMarkExpiredBatches = async () => {
    try {
      await inventoryBatchAPI.markExpired(selectedRestaurant);
      loadExpirySummary();
      loadExpiringBatches();
      loadExpiredBatches();
    } catch (error) {
      console.error('Failed to mark expired batches:', error);
    }
  };

  const handleViewBatches = (ingredient) => {
    setSelectedIngredient(ingredient);
    loadBatchesForIngredient(ingredient.id);
    setBatchDetailModalOpen(true);
  };

  const getExpiryStatusBadge = (batch) => {
    if (batch.status === 'EXPIRED') {
      return <Badge className="bg-red-100 text-red-800">{t('inventory.expiry.status.expired', 'Expired')}</Badge>;
    }
    if (batch.daysUntilExpiry <= 3) {
      return <Badge className="bg-red-100 text-red-800">{t('inventory.expiry.status.critical', 'Critical')}</Badge>;
    }
    if (batch.daysUntilExpiry <= 7) {
      return <Badge className="bg-yellow-100 text-yellow-800">{t('inventory.expiry.status.warning', 'Warning')}</Badge>;
    }
    return <Badge className="bg-green-100 text-green-800">{t('inventory.expiry.status.good', 'Good')}</Badge>;
  };

  const refreshAll = () => {
    loadExpirySummary();
    loadExpiringBatches();
    loadExpiredBatches();
  };

  return (
    <InventoryLayout>
      <div className="space-y-6">
        {/* Stats Cards */}
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

        {/* Actions */}
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
              <Button onClick={refreshAll} variant="outline" size="icon">
                <RefreshCw className="h-4 w-4" />
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Expired Batches */}
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
                        <Button variant="destructive" size="sm" onClick={() => handleWriteOff(batch)}>
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

        {/* Expiring Soon */}
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
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('inventory.fields.name', 'Name')}</TableHead>
                  <TableHead>{t('inventory.expiry.fields.shelfLife', 'Shelf Life')}</TableHead>
                  <TableHead>{t('inventory.expiry.fields.alertDays', 'Alert Days')}</TableHead>
                  <TableHead className="text-right">{t('common.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {ingredients.filter(ing => ing.trackExpiry).length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center py-8 text-muted-foreground">
                      {t('inventory.expiry.noExpiryTracking', 'No ingredients have expiry tracking enabled.')}
                    </TableCell>
                  </TableRow>
                ) : (
                  ingredients.filter(ing => ing.trackExpiry).map((ingredient) => (
                    <TableRow key={ingredient.id}>
                      <TableCell className="font-medium">{ingredient.name}</TableCell>
                      <TableCell>
                        {ingredient.defaultShelfLifeDays ? `${ingredient.defaultShelfLifeDays} days` : '-'}
                      </TableCell>
                      <TableCell>{ingredient.expiryAlertDays} days</TableCell>
                      <TableCell className="text-right">
                        <Button variant="ghost" size="sm" onClick={() => handleViewBatches(ingredient)}>
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
      </div>

      {/* Add Batch Modal */}
      <Dialog open={batchModalOpen} onOpenChange={setBatchModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('inventory.expiry.addBatch', 'Add Batch')}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('inventory.fields.ingredient', 'Ingredient')}</Label>
              <Select
                value={batchFormData.ingredientId}
                onValueChange={(value) => setBatchFormData({ ...batchFormData, ingredientId: value })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t('inventory.placeholders.selectIngredient')} />
                </SelectTrigger>
                <SelectContent>
                  {ingredients.filter(i => i.trackExpiry).map((ing) => (
                    <SelectItem key={ing.id} value={ing.id.toString()}>{ing.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('inventory.expiry.fields.batchNumber', 'Batch Number')}</Label>
                <Input
                  value={batchFormData.batchNumber}
                  onChange={(e) => setBatchFormData({ ...batchFormData, batchNumber: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.expiry.fields.quantity', 'Quantity')}</Label>
                <Input
                  type="number"
                  step="0.001"
                  value={batchFormData.quantity}
                  onChange={(e) => setBatchFormData({ ...batchFormData, quantity: e.target.value })}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('inventory.expiry.fields.receivedDate', 'Received Date')}</Label>
                <Input
                  type="date"
                  value={batchFormData.receivedDate}
                  onChange={(e) => setBatchFormData({ ...batchFormData, receivedDate: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.expiry.fields.expiryDate', 'Expiry Date')}</Label>
                <Input
                  type="date"
                  value={batchFormData.expiryDate}
                  onChange={(e) => setBatchFormData({ ...batchFormData, expiryDate: e.target.value })}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('inventory.fields.costPerUnit', 'Cost per Unit')}</Label>
                <Input
                  type="number"
                  step="0.01"
                  value={batchFormData.costPerUnit}
                  onChange={(e) => setBatchFormData({ ...batchFormData, costPerUnit: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.fields.supplier', 'Supplier')}</Label>
                <Select
                  value={batchFormData.supplierId || 'none'}
                  onValueChange={(value) => setBatchFormData({ ...batchFormData, supplierId: value === 'none' ? '' : value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">{t('common.none', 'None')}</SelectItem>
                    {suppliers.map((s) => (
                      <SelectItem key={s.id} value={s.id.toString()}>{s.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBatchModalOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleSaveBatch} disabled={!batchFormData.ingredientId || !batchFormData.quantity}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Write Off Modal */}
      <Dialog open={writeOffModalOpen} onOpenChange={setWriteOffModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('inventory.expiry.writeOff', 'Write Off Batch')}</DialogTitle>
            <DialogDescription>
              {selectedBatch && `${selectedBatch.ingredientName} - ${selectedBatch.batchNumber}`}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label>{t('inventory.expiry.writeOffReason', 'Reason')}</Label>
            <Input
              value={writeOffReason}
              onChange={(e) => setWriteOffReason(e.target.value)}
              placeholder={t('inventory.expiry.writeOffReasonPlaceholder', 'e.g., Expired, Spoiled')}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWriteOffModalOpen(false)}>{t('common.cancel')}</Button>
            <Button variant="destructive" onClick={handleConfirmWriteOff}>
              {t('inventory.expiry.confirmWriteOff', 'Confirm Write Off')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Batch Detail Modal */}
      <Dialog open={batchDetailModalOpen} onOpenChange={setBatchDetailModalOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              {selectedIngredient && `${t('inventory.expiry.batchesFor', 'Batches for')} ${selectedIngredient.name}`}
            </DialogTitle>
          </DialogHeader>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('inventory.expiry.fields.batchNumber', 'Batch #')}</TableHead>
                <TableHead>{t('inventory.expiry.fields.quantity', 'Quantity')}</TableHead>
                <TableHead>{t('inventory.expiry.fields.expiryDate', 'Expiry')}</TableHead>
                <TableHead>{t('inventory.expiry.fields.status', 'Status')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {selectedIngredientBatches.map((batch) => (
                <TableRow key={batch.id}>
                  <TableCell>{batch.batchNumber}</TableCell>
                  <TableCell>{batch.quantity} {batch.unit}</TableCell>
                  <TableCell>{batch.expiryDate || '-'}</TableCell>
                  <TableCell>{getExpiryStatusBadge(batch)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
