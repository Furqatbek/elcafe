import { useState, useEffect } from 'react';
import { notifyError } from '../../lib/errors';
import { useTranslation } from 'react-i18next';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';
import { productionBatchAPI, menuAPI } from '../../services/api';
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
  Plus,
  ChefHat,
  Play,
  CheckCircle,
  Trash2,
  Eye,
  ArrowRight,
  RefreshCw,
  X,
} from 'lucide-react';

const STATUS_VARIANTS = {
  DRAFT: 'secondary',
  IN_PROGRESS: 'outline',
  READY: 'default',
  SERVING: 'default',
  DEPLETED: 'secondary',
  EXPIRED: 'destructive',
  WASTED: 'destructive',
};

const STATUS_FLOW = ['DRAFT', 'IN_PROGRESS', 'READY', 'SERVING', 'DEPLETED'];

export default function ProductionBatches() {
  const { t } = useTranslation();
  const { selectedRestaurant, ingredients, loadIngredients } = useInventory();

  const [batches, setBatches] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState(null);

  // Dialogs
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [completeOpen, setCompleteOpen] = useState(false);
  const [wasteOpen, setWasteOpen] = useState(false);
  const [addInputOpen, setAddInputOpen] = useState(false);
  const [ingredientSearch, setIngredientSearch] = useState('');

  // Selected batch for detail/actions
  const [selectedBatch, setSelectedBatch] = useState(null);

  // Form state
  const [createForm, setCreateForm] = useState({
    name: '', productId: '', outputUnit: 'L', preparedBy: '',
    expiresAt: '', notes: '', loadRecipe: false,
  });
  const [completeForm, setCompleteForm] = useState({ outputQuantity: '', outputUnit: '', notes: '' });
  const [wasteForm, setWasteForm] = useState({ quantity: '', reason: '' });
  const [inputForm, setInputForm] = useState({ ingredientId: '', actualQuantity: '', unit: '', notes: '' });

  // --- Data loading ---

  const loadBatches = async () => {
    if (!selectedRestaurant) return;
    setLoading(true);
    try {
      const params = statusFilter ? { status: statusFilter } : {};
      const response = await productionBatchAPI.getByRestaurant(selectedRestaurant, params);
      setBatches(response.data.data || []);
    } catch (error) {
      console.error('Failed to load production batches:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadProducts = async () => {
    if (!selectedRestaurant) return;
    try {
      const response = await menuAPI.getProductsByRestaurant(selectedRestaurant);
      setProducts(response.data.data || response.data || []);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const loadBatchDetail = async (batchId) => {
    try {
      const response = await productionBatchAPI.getById(batchId);
      setSelectedBatch(response.data.data || response.data);
    } catch (error) {
      console.error('Failed to load batch detail:', error);
    }
  };

  useEffect(() => {
    loadBatches();
    loadProducts();
  }, [selectedRestaurant, statusFilter]);

  // --- Handlers ---

  const handleCreateBatch = async () => {
    try {
      await productionBatchAPI.create({
        restaurantId: selectedRestaurant,
        name: createForm.name,
        productId: createForm.productId ? parseInt(createForm.productId) : null,
        outputUnit: createForm.outputUnit,
        preparedBy: createForm.preparedBy || null,
        expiresAt: createForm.expiresAt || null,
        notes: createForm.notes || null,
        loadRecipe: createForm.loadRecipe,
      });
      setCreateOpen(false);
      setCreateForm({ name: '', productId: '', outputUnit: 'L', preparedBy: '', expiresAt: '', notes: '', loadRecipe: false });
      loadBatches();
    } catch (error) {
      console.error('Failed to create batch:', error);
      notifyError(error);
    }
  };

  const handleStartBatch = async (batchId) => {
    try {
      await productionBatchAPI.start(batchId);
      loadBatches();
      if (selectedBatch?.id === batchId) loadBatchDetail(batchId);
    } catch (error) {
      console.error('Failed to start batch:', error);
      notifyError(error);
    }
  };

  const handleCompleteBatch = async () => {
    if (!selectedBatch) return;
    try {
      await productionBatchAPI.complete(selectedBatch.id, {
        outputQuantity: parseFloat(completeForm.outputQuantity),
        outputUnit: completeForm.outputUnit || null,
        notes: completeForm.notes || null,
      });
      setCompleteOpen(false);
      setCompleteForm({ outputQuantity: '', outputUnit: '', notes: '' });
      loadBatches();
      loadBatchDetail(selectedBatch.id);
    } catch (error) {
      console.error('Failed to complete batch:', error);
      notifyError(error);
    }
  };

  const handleRecordWaste = async () => {
    if (!selectedBatch) return;
    try {
      await productionBatchAPI.recordWaste(selectedBatch.id, {
        quantity: parseFloat(wasteForm.quantity),
        reason: wasteForm.reason,
      });
      setWasteOpen(false);
      setWasteForm({ quantity: '', reason: '' });
      loadBatches();
      loadBatchDetail(selectedBatch.id);
    } catch (error) {
      console.error('Failed to record waste:', error);
      notifyError(error);
    }
  };

  const handleAddInput = async () => {
    if (!selectedBatch) return;
    try {
      await productionBatchAPI.addInput(selectedBatch.id, {
        ingredientId: parseInt(inputForm.ingredientId),
        actualQuantity: parseFloat(inputForm.actualQuantity),
        unit: inputForm.unit || null,
        notes: inputForm.notes || null,
      });
      setAddInputOpen(false);
      setInputForm({ ingredientId: '', actualQuantity: '', unit: '', notes: '' });
      loadBatchDetail(selectedBatch.id);
    } catch (error) {
      console.error('Failed to add input:', error);
      notifyError(error);
    }
  };

  const handleReloadRecipe = async () => {
    if (!selectedBatch) return;
    try {
      await productionBatchAPI.reloadRecipe(selectedBatch.id);
      loadBatchDetail(selectedBatch.id);
    } catch (error) {
      console.error('Failed to reload recipe:', error);
      notifyError(error);
    }
  };

  const handleDeleteBatch = async (batchId) => {
    try {
      await productionBatchAPI.delete(batchId);
      loadBatches();
      if (selectedBatch?.id === batchId) {
        setDetailOpen(false);
        setSelectedBatch(null);
      }
    } catch (error) {
      console.error('Failed to delete batch:', error);
      notifyError(error);
    }
  };

  const openDetail = (batch) => {
    loadBatchDetail(batch.id);
    setDetailOpen(true);
  };

  const openComplete = () => {
    setCompleteForm({
      outputQuantity: '',
      outputUnit: selectedBatch?.outputUnit || '',
      notes: '',
    });
    setCompleteOpen(true);
  };

  // --- Helpers ---

  const getRemainingPercent = (batch) => {
    if (!batch.outputQuantity || batch.outputQuantity === 0) return 0;
    return Math.round((batch.remainingQuantity / batch.outputQuantity) * 100);
  };

  const formatNumber = (n) => (n != null ? Number(n).toLocaleString() : '--');

  const statusFilters = [null, 'READY', 'SERVING', 'DRAFT', 'IN_PROGRESS', 'DEPLETED', 'EXPIRED'];

  // --- Render ---

  return (
    <InventoryLayout
      title={t('production.title', 'Production Batches')}
      subtitle={t('production.subtitle', 'Track batch-cooked items and prepared inventory')}
    >
      <div className="space-y-4">
        {/* Filter + New Batch */}
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div className="flex gap-1.5 flex-wrap">
            {statusFilters.map((status) => (
              <Button
                key={status || 'all'}
                variant={statusFilter === status ? 'default' : 'outline'}
                size="sm"
                onClick={() => setStatusFilter(status)}
              >
                {status ? t(`production.statuses.${status}`, status) : t('common.all', 'All')}
              </Button>
            ))}
          </div>
          <Button onClick={() => { loadIngredients(); setCreateOpen(true); }}>
            <Plus className="h-4 w-4 mr-2" />
            {t('production.createBatch', 'New Batch')}
          </Button>
        </div>

        {/* Batch Table */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ChefHat className="h-5 w-5" />
              {t('production.title', 'Production Batches')}
            </CardTitle>
            <CardDescription>{t('production.subtitle', 'Track batch-cooked items and prepared inventory')}</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('production.name', 'Batch Name')}</TableHead>
                  <TableHead>{t('production.product', 'Product')}</TableHead>
                  <TableHead className="text-right">{t('production.outputQuantity', 'Yield')}</TableHead>
                  <TableHead>{t('production.remainingQuantity', 'Remaining')}</TableHead>
                  <TableHead className="text-right">{t('production.costPerUnit', 'Cost/Unit')}</TableHead>
                  <TableHead className="text-center">{t('production.status', 'Status')}</TableHead>
                  <TableHead>{t('production.preparedBy', 'Prepared By')}</TableHead>
                  <TableHead className="text-right">{t('common.actions', 'Actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                      {t('common.loading', 'Loading...')}
                    </TableCell>
                  </TableRow>
                ) : batches.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                      {t('production.noBatches', 'No production batches found')}
                    </TableCell>
                  </TableRow>
                ) : (
                  batches.map((batch) => (
                    <TableRow key={batch.id} className="cursor-pointer hover:bg-muted/30" onClick={() => openDetail(batch)}>
                      <TableCell className="font-medium">{batch.name}</TableCell>
                      <TableCell className="text-muted-foreground">{batch.productName || '--'}</TableCell>
                      <TableCell className="text-right">{formatNumber(batch.outputQuantity)} {batch.outputUnit}</TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <div className="w-20 bg-gray-200 rounded-full h-2">
                            <div
                              className={`h-2 rounded-full ${getRemainingPercent(batch) > 30 ? 'bg-green-500' : getRemainingPercent(batch) > 0 ? 'bg-yellow-500' : 'bg-gray-400'}`}
                              style={{ width: `${getRemainingPercent(batch)}%` }}
                            />
                          </div>
                          <span className="text-sm">{formatNumber(batch.remainingQuantity)}</span>
                        </div>
                      </TableCell>
                      <TableCell className="text-right">{formatNumber(batch.costPerUnit)}</TableCell>
                      <TableCell className="text-center">
                        <Badge variant={STATUS_VARIANTS[batch.status] || 'secondary'}>
                          {t(`production.statuses.${batch.status}`, batch.status)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{batch.preparedBy || '--'}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex gap-1 justify-end" onClick={(e) => e.stopPropagation()}>
                          <Button variant="ghost" size="icon" onClick={() => openDetail(batch)} title="View">
                            <Eye className="h-4 w-4" />
                          </Button>
                          {batch.status === 'DRAFT' && (
                            <Button variant="ghost" size="icon" onClick={() => handleStartBatch(batch.id)} title="Start">
                              <Play className="h-4 w-4 text-blue-600" />
                            </Button>
                          )}
                          {batch.status === 'DRAFT' && (
                            <Button variant="ghost" size="icon" onClick={() => handleDeleteBatch(batch.id)} title="Delete">
                              <Trash2 className="h-4 w-4 text-red-600" />
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
      </div>

      {/* ═══ CREATE BATCH DIALOG ═══ */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('production.createBatch', 'New Batch')}</DialogTitle>
            <DialogDescription>{t('production.createBatchDesc', 'Start a new production batch for batch-cooked items')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('production.name', 'Batch Name')} *</Label>
              <Input
                value={createForm.name}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                placeholder={t('production.namePlaceholder', 'e.g. Shurva Morning Batch')}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('production.product', 'Product')}</Label>
              <Select
                value={createForm.productId}
                onValueChange={(value) => setCreateForm({ ...createForm, productId: value })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t('production.selectProduct', 'Select product (optional)')} />
                </SelectTrigger>
                <SelectContent>
                  {products.map((p) => (
                    <SelectItem key={p.id} value={p.id.toString()}>{p.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('production.outputUnit', 'Unit')} *</Label>
                <Select
                  value={createForm.outputUnit}
                  onValueChange={(value) => setCreateForm({ ...createForm, outputUnit: value })}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="L">L (liter)</SelectItem>
                    <SelectItem value="kg">kg (kilogram)</SelectItem>
                    <SelectItem value="pieces">pieces</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t('production.preparedBy', 'Prepared By')}</Label>
                <Input
                  value={createForm.preparedBy}
                  onChange={(e) => setCreateForm({ ...createForm, preparedBy: e.target.value })}
                  placeholder="Chef name"
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('production.expiresAt', 'Expires At')}</Label>
              <Input
                type="datetime-local"
                value={createForm.expiresAt}
                onChange={(e) => setCreateForm({ ...createForm, expiresAt: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('production.notes', 'Notes')}</Label>
              <Input
                value={createForm.notes}
                onChange={(e) => setCreateForm({ ...createForm, notes: e.target.value })}
                placeholder={t('production.notesPlaceholder', 'Optional notes')}
              />
            </div>
            {createForm.productId && (
              <div className="flex items-center space-x-2 border rounded-lg p-3">
                <input
                  type="checkbox"
                  id="loadRecipe"
                  checked={createForm.loadRecipe}
                  onChange={(e) => setCreateForm({ ...createForm, loadRecipe: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="loadRecipe" className="font-medium">
                  {t('production.loadRecipe', 'Load from Recipe')}
                </Label>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button onClick={handleCreateBatch} disabled={!createForm.name}>
              {t('common.create', 'Create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ═══ BATCH DETAIL DIALOG ═══ */}
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
          {selectedBatch && (
            <>
              <DialogHeader>
                <DialogTitle className="flex items-center gap-3">
                  {selectedBatch.name}
                  <Badge variant={STATUS_VARIANTS[selectedBatch.status] || 'secondary'}>
                    {t(`production.statuses.${selectedBatch.status}`, selectedBatch.status)}
                  </Badge>
                </DialogTitle>
                <DialogDescription>
                  {selectedBatch.batchNumber} {selectedBatch.productName ? `| ${selectedBatch.productName}` : ''}
                </DialogDescription>
              </DialogHeader>

              {/* Status Flow */}
              <div className="flex items-center gap-1 py-3 overflow-x-auto">
                {STATUS_FLOW.map((status, idx) => (
                  <div key={status} className="flex items-center gap-1">
                    <div className={`px-2.5 py-1 rounded-full text-xs font-medium whitespace-nowrap ${
                      selectedBatch.status === status
                        ? 'bg-primary text-primary-foreground'
                        : STATUS_FLOW.indexOf(selectedBatch.status) > idx
                        ? 'bg-green-100 text-green-700'
                        : 'bg-muted text-muted-foreground'
                    }`}>
                      {t(`production.statuses.${status}`, status)}
                    </div>
                    {idx < STATUS_FLOW.length - 1 && <ArrowRight className="h-3 w-3 text-muted-foreground flex-shrink-0" />}
                  </div>
                ))}
              </div>

              {/* Summary stats */}
              <div className="grid grid-cols-3 gap-4">
                <div className="border rounded-lg p-3 text-center">
                  <div className="text-sm text-muted-foreground">{t('production.outputQuantity', 'Yield')}</div>
                  <div className="text-xl font-bold">{formatNumber(selectedBatch.outputQuantity)} {selectedBatch.outputUnit}</div>
                </div>
                <div className="border rounded-lg p-3 text-center">
                  <div className="text-sm text-muted-foreground">{t('production.remainingQuantity', 'Remaining')}</div>
                  <div className="text-xl font-bold">{formatNumber(selectedBatch.remainingQuantity)} {selectedBatch.outputUnit}</div>
                  {selectedBatch.outputQuantity > 0 && (
                    <div className="mt-1">
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full ${getRemainingPercent(selectedBatch) > 30 ? 'bg-green-500' : 'bg-yellow-500'}`}
                          style={{ width: `${getRemainingPercent(selectedBatch)}%` }}
                        />
                      </div>
                    </div>
                  )}
                </div>
                <div className="border rounded-lg p-3 text-center">
                  <div className="text-sm text-muted-foreground">{t('production.costPerUnit', 'Cost/Unit')}</div>
                  <div className="text-xl font-bold">{formatNumber(selectedBatch.costPerUnit)}</div>
                  <div className="text-xs text-muted-foreground">{t('production.totalCost', 'Total')}: {formatNumber(selectedBatch.totalInputCost)}</div>
                </div>
              </div>

              {/* Input ingredients */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h4 className="font-medium">{t('production.inputs', 'Input Ingredients')}</h4>
                  {(selectedBatch.status === 'DRAFT' || selectedBatch.status === 'IN_PROGRESS') && (
                    <div className="flex gap-2">
                      {selectedBatch.productName && (
                        <Button size="sm" variant="outline" onClick={handleReloadRecipe}>
                          <RefreshCw className="h-3 w-3 mr-1" /> {t('production.reloadRecipe', 'Reload from Recipe')}
                        </Button>
                      )}
                      <Button size="sm" variant="outline" onClick={() => { loadIngredients(); setAddInputOpen(true); }}>
                        <Plus className="h-3 w-3 mr-1" /> {t('production.addInput', 'Add Input')}
                      </Button>
                    </div>
                  )}
                </div>
                {selectedBatch.inputs && selectedBatch.inputs.length > 0 ? (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('production.ingredient', 'Ingredient')}</TableHead>
                        <TableHead className="text-right">{t('production.plannedQuantity', 'Planned')}</TableHead>
                        <TableHead className="text-right">{t('production.actualQuantity', 'Actual')}</TableHead>
                        <TableHead className="text-right">{t('production.costPerUnit', 'Cost/Unit')}</TableHead>
                        <TableHead className="text-right">{t('production.totalCost', 'Total')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {selectedBatch.inputs.map((input) => (
                        <TableRow key={input.id}>
                          <TableCell className="font-medium">{input.ingredientName}</TableCell>
                          <TableCell className="text-right text-muted-foreground">
                            {input.plannedQuantity != null ? `${formatNumber(input.plannedQuantity)} ${input.unit}` : '--'}
                          </TableCell>
                          <TableCell className="text-right">{formatNumber(input.actualQuantity)} {input.unit}</TableCell>
                          <TableCell className="text-right">{formatNumber(input.costPerUnit)}</TableCell>
                          <TableCell className="text-right font-medium">{formatNumber(input.totalCost)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : (
                  <p className="text-sm text-muted-foreground py-3">{t('production.noInputs', 'No inputs added yet')}</p>
                )}
              </div>

              {/* Batch info */}
              <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm border-t pt-3">
                {selectedBatch.preparedBy && (
                  <>
                    <span className="text-muted-foreground">{t('production.preparedBy', 'Prepared By')}</span>
                    <span>{selectedBatch.preparedBy}</span>
                  </>
                )}
                {selectedBatch.startedAt && (
                  <>
                    <span className="text-muted-foreground">{t('production.startedAt', 'Started')}</span>
                    <span>{new Date(selectedBatch.startedAt).toLocaleString()}</span>
                  </>
                )}
                {selectedBatch.completedAt && (
                  <>
                    <span className="text-muted-foreground">{t('production.completedAt', 'Completed')}</span>
                    <span>{new Date(selectedBatch.completedAt).toLocaleString()}</span>
                  </>
                )}
                {selectedBatch.expiresAt && (
                  <>
                    <span className="text-muted-foreground">{t('production.expiresAt', 'Expires')}</span>
                    <span>{new Date(selectedBatch.expiresAt).toLocaleString()}</span>
                  </>
                )}
                {selectedBatch.notes && (
                  <>
                    <span className="text-muted-foreground">{t('production.notes', 'Notes')}</span>
                    <span>{selectedBatch.notes}</span>
                  </>
                )}
              </div>

              {/* Actions */}
              <DialogFooter className="flex-wrap gap-2">
                {selectedBatch.status === 'DRAFT' && (
                  <>
                    <Button variant="outline" onClick={() => handleStartBatch(selectedBatch.id)}>
                      <Play className="h-4 w-4 mr-2" /> {t('production.start', 'Start Production')}
                    </Button>
                    <Button
                      onClick={openComplete}
                      disabled={!selectedBatch.inputs || selectedBatch.inputs.length === 0}
                      title={!selectedBatch.inputs || selectedBatch.inputs.length === 0 ? t('production.addInputsFirst', 'Add ingredient inputs before completing') : ''}
                    >
                      <CheckCircle className="h-4 w-4 mr-2" /> {t('production.complete', 'Complete Batch')}
                    </Button>
                  </>
                )}
                {selectedBatch.status === 'IN_PROGRESS' && (
                  <Button
                    onClick={openComplete}
                    disabled={!selectedBatch.inputs || selectedBatch.inputs.length === 0}
                    title={!selectedBatch.inputs || selectedBatch.inputs.length === 0 ? t('production.addInputsFirst', 'Add ingredient inputs before completing') : ''}
                  >
                    <CheckCircle className="h-4 w-4 mr-2" /> {t('production.complete', 'Complete Batch')}
                  </Button>
                )}
                {(selectedBatch.status === 'READY' || selectedBatch.status === 'SERVING') && (
                  <Button variant="outline" onClick={() => setWasteOpen(true)}>
                    <Trash2 className="h-4 w-4 mr-2" /> {t('production.recordWaste', 'Record Waste')}
                  </Button>
                )}
                {selectedBatch.status === 'DRAFT' && (
                  <Button variant="destructive" onClick={() => { handleDeleteBatch(selectedBatch.id); }}>
                    <X className="h-4 w-4 mr-2" /> {t('common.delete', 'Delete')}
                  </Button>
                )}
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* ═══ COMPLETE BATCH DIALOG ═══ */}
      <Dialog open={completeOpen} onOpenChange={setCompleteOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('production.complete', 'Complete Batch')}</DialogTitle>
            <DialogDescription>{t('production.completeDesc', 'Enter the actual output yield after cooking')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('production.outputQuantity', 'Yield')} *</Label>
              <Input
                type="number"
                step="0.001"
                min="0"
                value={completeForm.outputQuantity}
                onChange={(e) => setCompleteForm({ ...completeForm, outputQuantity: e.target.value })}
                placeholder="e.g. 8.5"
              />
            </div>
            <div className="space-y-2">
              <Label>{t('production.outputUnit', 'Unit')}</Label>
              <Select
                value={completeForm.outputUnit || selectedBatch?.outputUnit || 'L'}
                onValueChange={(value) => setCompleteForm({ ...completeForm, outputUnit: value })}
              >
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="L">L (liter)</SelectItem>
                  <SelectItem value="kg">kg (kilogram)</SelectItem>
                  <SelectItem value="pieces">pieces</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {completeForm.outputQuantity && selectedBatch?.totalInputCost > 0 && (
              <div className="border rounded-lg p-3 bg-muted/50">
                <div className="text-sm text-muted-foreground">{t('production.estimatedCostPerUnit', 'Estimated Cost/Unit')}</div>
                <div className="text-lg font-bold">
                  {(selectedBatch.totalInputCost / parseFloat(completeForm.outputQuantity)).toLocaleString(undefined, { maximumFractionDigits: 2 })}
                </div>
              </div>
            )}
            <div className="space-y-2">
              <Label>{t('production.notes', 'Notes')}</Label>
              <Input
                value={completeForm.notes}
                onChange={(e) => setCompleteForm({ ...completeForm, notes: e.target.value })}
                placeholder={t('production.completeNotesPlaceholder', 'e.g. Evaporation reduced from expected 10L')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCompleteOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button onClick={handleCompleteBatch} disabled={!completeForm.outputQuantity}>
              <CheckCircle className="h-4 w-4 mr-2" /> {t('production.complete', 'Complete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ═══ WASTE DIALOG ═══ */}
      <Dialog open={wasteOpen} onOpenChange={setWasteOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('production.recordWaste', 'Record Waste')}</DialogTitle>
            <DialogDescription>
              {selectedBatch && `${t('production.remainingQuantity', 'Remaining')}: ${formatNumber(selectedBatch.remainingQuantity)} ${selectedBatch.outputUnit}`}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('production.wasteQuantity', 'Quantity to waste')} *</Label>
              <Input
                type="number"
                step="0.001"
                min="0"
                max={selectedBatch?.remainingQuantity}
                value={wasteForm.quantity}
                onChange={(e) => setWasteForm({ ...wasteForm, quantity: e.target.value })}
                placeholder="0.00"
              />
            </div>
            <div className="space-y-2">
              <Label>{t('production.wasteReason', 'Reason')} *</Label>
              <Select
                value={wasteForm.reason}
                onValueChange={(value) => setWasteForm({ ...wasteForm, reason: value })}
              >
                <SelectTrigger><SelectValue placeholder={t('production.selectReason', 'Select reason')} /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="End of day leftover">End of day leftover</SelectItem>
                  <SelectItem value="Expired">Expired</SelectItem>
                  <SelectItem value="Quality issue">Quality issue</SelectItem>
                  <SelectItem value="Spillage">Spillage</SelectItem>
                  <SelectItem value="Other">Other</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWasteOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button variant="destructive" onClick={handleRecordWaste} disabled={!wasteForm.quantity || !wasteForm.reason}>
              <Trash2 className="h-4 w-4 mr-2" /> {t('production.recordWaste', 'Record Waste')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ═══ ADD INPUT DIALOG ═══ */}
      <Dialog open={addInputOpen} onOpenChange={setAddInputOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('production.addInput', 'Add Ingredient Input')}</DialogTitle>
            <DialogDescription>{t('production.addInputDesc', 'Record the actual quantity of an ingredient used')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('production.ingredient', 'Ingredient')} *</Label>
              <Input
                placeholder={t('packaging.searchIngredient', 'Search ingredients...')}
                value={ingredientSearch}
                onChange={(e) => setIngredientSearch(e.target.value)}
                className="mb-1"
              />
              <Select
                value={inputForm.ingredientId}
                onValueChange={(value) => setInputForm({ ...inputForm, ingredientId: value })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t('production.selectIngredient', 'Select ingredient')} />
                </SelectTrigger>
                <SelectContent>
                  {ingredients
                    .filter(ing => ing.name.toLowerCase().includes(ingredientSearch.toLowerCase()))
                    .map((ing) => (
                    <SelectItem key={ing.id} value={ing.id.toString()}>
                      {ing.name} ({ing.currentStock} {ing.unit})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('production.actualQuantity', 'Actual Quantity')} *</Label>
                <Input
                  type="number"
                  step="0.001"
                  min="0"
                  value={inputForm.actualQuantity}
                  onChange={(e) => setInputForm({ ...inputForm, actualQuantity: e.target.value })}
                  placeholder="0.00"
                />
              </div>
              <div className="space-y-2">
                <Label>{t('production.unit', 'Unit')}</Label>
                <Input
                  value={inputForm.unit}
                  onChange={(e) => setInputForm({ ...inputForm, unit: e.target.value })}
                  placeholder="kg, L, etc."
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('production.notes', 'Notes')}</Label>
              <Input
                value={inputForm.notes}
                onChange={(e) => setInputForm({ ...inputForm, notes: e.target.value })}
                placeholder={t('production.inputNotesPlaceholder', 'e.g. Used bone-in cuts')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddInputOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button onClick={handleAddInput} disabled={!inputForm.ingredientId || !inputForm.actualQuantity}>
              <Plus className="h-4 w-4 mr-2" /> {t('production.addInput', 'Add Input')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
