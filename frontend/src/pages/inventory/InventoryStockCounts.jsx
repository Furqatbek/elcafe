import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { stockCountAPI } from '../../services/api';
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
  Plus,
  ClipboardCheck,
  Play,
  Eye,
  FileCheck,
  XCircle,
  AlertTriangle,
  CheckCircle,
  RefreshCw,
} from 'lucide-react';
import { Checkbox } from '../../components/ui/checkbox';
import { Textarea } from '../../components/ui/textarea';

export default function InventoryStockCounts() {
  const { t } = useTranslation();
  const { selectedRestaurant, ingredients, loadIngredients } = useInventory();

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

  // Cancel dialog state
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelling, setCancelling] = useState(false);

  // Approve dialog state
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [adjustInventory, setAdjustInventory] = useState(true);
  const [approveNotes, setApproveNotes] = useState('');
  const [approving, setApproving] = useState(false);

  // Variance reason dialog state
  const [varianceDialogOpen, setVarianceDialogOpen] = useState(false);
  const [selectedItem, setSelectedItem] = useState(null);
  const [varianceReason, setVarianceReason] = useState('');
  const [varianceNotes, setVarianceNotes] = useState('');

  // Variance reasons enum
  const varianceReasons = [
    { value: 'THEFT', label: t('inventory.stockCounts.varianceReasons.theft', 'Theft') },
    { value: 'DAMAGE', label: t('inventory.stockCounts.varianceReasons.damage', 'Damage') },
    { value: 'SPOILAGE', label: t('inventory.stockCounts.varianceReasons.spoilage', 'Spoilage') },
    { value: 'COUNTING_ERROR', label: t('inventory.stockCounts.varianceReasons.countingError', 'Counting Error') },
    { value: 'SYSTEM_ERROR', label: t('inventory.stockCounts.varianceReasons.systemError', 'System Error') },
    { value: 'UNRECORDED_USAGE', label: t('inventory.stockCounts.varianceReasons.unrecordedUsage', 'Unrecorded Usage') },
    { value: 'UNRECORDED_RECEIPT', label: t('inventory.stockCounts.varianceReasons.unrecordedReceipt', 'Unrecorded Receipt') },
    { value: 'SHRINKAGE', label: t('inventory.stockCounts.varianceReasons.shrinkage', 'Shrinkage') },
    { value: 'OTHER', label: t('inventory.stockCounts.varianceReasons.other', 'Other') },
  ];

  useEffect(() => {
    if (selectedRestaurant) {
      loadStockCounts();
    }
  }, [selectedRestaurant]);

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

  const handleViewStockCount = async (stockCount) => {
    try {
      const response = await stockCountAPI.getById(stockCount.id);
      setSelectedStockCount(response.data.data);
      setStockCountDetailModalOpen(true);
    } catch (error) {
      console.error('Failed to load stock count details:', error);
    }
  };

  const handleRecordCount = async (itemId, quantity, notes = '') => {
    try {
      await stockCountAPI.recordCount({
        itemId,
        countedQuantity: quantity,
        countedBy: 'Admin',
        notes,
      });
      if (selectedStockCount) {
        const response = await stockCountAPI.getById(selectedStockCount.id);
        setSelectedStockCount(response.data.data);
      }
      loadStockCounts();
    } catch (error) {
      console.error('Failed to record count:', error);
    }
  };

  const handleSubmitForReview = async (id) => {
    try {
      await stockCountAPI.submitForReview(id, 'Admin');
      const response = await stockCountAPI.getById(id);
      setSelectedStockCount(response.data.data);
      loadStockCounts();
    } catch (error) {
      console.error('Failed to submit for review:', error);
    }
  };

  const handleOpenApproveDialog = () => {
    setAdjustInventory(true);
    setApproveNotes('');
    setApproveDialogOpen(true);
  };

  const handleApproveStockCount = async () => {
    try {
      setApproving(true);
      await stockCountAPI.approve(selectedStockCount.id, {
        approvedBy: 'Admin',
        adjustInventory: adjustInventory,
        notes: approveNotes,
      });
      setApproveDialogOpen(false);
      setStockCountDetailModalOpen(false);
      loadStockCounts();
      if (adjustInventory) {
        loadIngredients();
      }
    } catch (error) {
      console.error('Failed to approve stock count:', error);
    } finally {
      setApproving(false);
    }
  };

  const handleOpenCancelDialog = (stockCount) => {
    setSelectedStockCount(stockCount);
    setCancelReason('');
    setCancelDialogOpen(true);
  };

  const handleCancelStockCount = async () => {
    try {
      setCancelling(true);
      await stockCountAPI.cancel(selectedStockCount.id, cancelReason, 'Admin');
      setCancelDialogOpen(false);
      setStockCountDetailModalOpen(false);
      loadStockCounts();
    } catch (error) {
      console.error('Failed to cancel stock count:', error);
    } finally {
      setCancelling(false);
    }
  };

  const handleOpenVarianceDialog = (item) => {
    setSelectedItem(item);
    setVarianceReason(item.varianceReason || '');
    setVarianceNotes(item.varianceNotes || '');
    setVarianceDialogOpen(true);
  };

  const handleSetVarianceReason = async () => {
    try {
      await stockCountAPI.setVarianceReason({
        itemId: selectedItem.id,
        reason: varianceReason,
        notes: varianceNotes,
      });
      setVarianceDialogOpen(false);
      // Reload stock count details
      if (selectedStockCount) {
        const response = await stockCountAPI.getById(selectedStockCount.id);
        setSelectedStockCount(response.data.data);
      }
    } catch (error) {
      console.error('Failed to set variance reason:', error);
    }
  };

  const getCountTypeBadge = (type) => {
    const types = {
      FULL: { label: t('inventory.stockCounts.type.full', 'Full Count'), variant: 'default' },
      CYCLE: { label: t('inventory.stockCounts.type.cycle', 'Cycle Count'), variant: 'secondary' },
      SPOT_CHECK: { label: t('inventory.stockCounts.type.spotCheck', 'Spot Check'), variant: 'outline' },
    };
    const config = types[type] || types.FULL;
    return <Badge variant={config.variant}>{config.label}</Badge>;
  };

  const getStatusBadge = (status) => {
    const statuses = {
      DRAFT: { label: t('inventory.stockCounts.status.draft', 'Draft'), color: 'bg-gray-100 text-gray-800' },
      IN_PROGRESS: { label: t('inventory.stockCounts.status.inProgress', 'In Progress'), color: 'bg-yellow-100 text-yellow-800' },
      PENDING_REVIEW: { label: t('inventory.stockCounts.status.pendingReview', 'Pending Review'), color: 'bg-orange-100 text-orange-800' },
      APPROVED: { label: t('inventory.stockCounts.status.approved', 'Approved'), color: 'bg-green-100 text-green-800' },
      CANCELLED: { label: t('inventory.stockCounts.status.cancelled', 'Cancelled'), color: 'bg-red-100 text-red-800' },
    };
    const config = statuses[status] || statuses.DRAFT;
    return <Badge className={config.color}>{config.label}</Badge>;
  };

  return (
    <InventoryLayout>
      <div className="space-y-6">
        {/* Stats */}
        <div className="grid gap-4 md:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.stockCounts.stats.total', 'Total Counts')}</CardTitle>
              <ClipboardCheck className="h-4 w-4 text-blue-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stockCounts.length}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.stockCounts.stats.inProgress', 'In Progress')}</CardTitle>
              <Play className="h-4 w-4 text-yellow-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stockCounts.filter(sc => sc.status === 'IN_PROGRESS').length}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.stockCounts.stats.pendingReview', 'Pending Review')}</CardTitle>
              <Eye className="h-4 w-4 text-orange-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stockCounts.filter(sc => sc.status === 'PENDING_REVIEW').length}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.stockCounts.stats.approved', 'Approved')}</CardTitle>
              <FileCheck className="h-4 w-4 text-green-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stockCounts.filter(sc => sc.status === 'APPROVED').length}</div>
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

        {/* Table */}
        <Card>
          <CardHeader>
            <CardTitle>{t('inventory.stockCounts.title', 'Stock Counts')}</CardTitle>
            <CardDescription>{t('inventory.stockCounts.description', 'Physical inventory counts and audits')}</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('inventory.stockCounts.countNumber', 'Count #')}</TableHead>
                  <TableHead>{t('inventory.stockCounts.type', 'Type')}</TableHead>
                  <TableHead>{t('inventory.stockCounts.status', 'Status')}</TableHead>
                  <TableHead>{t('inventory.stockCounts.progress', 'Progress')}</TableHead>
                  <TableHead>{t('inventory.stockCounts.scheduledDate', 'Scheduled')}</TableHead>
                  <TableHead className="text-right">{t('common.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {stockCounts.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                      {t('inventory.stockCounts.noData', 'No stock counts found')}
                    </TableCell>
                  </TableRow>
                ) : (
                  stockCounts.map((count) => (
                    <TableRow key={count.id}>
                      <TableCell className="font-medium">{count.countNumber}</TableCell>
                      <TableCell>{getCountTypeBadge(count.countType)}</TableCell>
                      <TableCell>{getStatusBadge(count.status)}</TableCell>
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
                      <TableCell>{count.scheduledDate}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          {count.status === 'DRAFT' && (
                            <Button variant="outline" size="sm" onClick={() => handleStartStockCount(count.id)}>
                              <Play className="h-4 w-4 mr-1" />
                              {t('common.start', 'Start')}
                            </Button>
                          )}
                          <Button variant="ghost" size="sm" onClick={() => handleViewStockCount(count)}>
                            <Eye className="h-4 w-4 mr-1" />
                            {t('common.view', 'View')}
                          </Button>
                          {(count.status === 'DRAFT' || count.status === 'IN_PROGRESS') && (
                            <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700 hover:bg-red-50" onClick={() => handleOpenCancelDialog(count)}>
                              <XCircle className="h-4 w-4 mr-1" />
                              {t('common.cancel')}
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

      {/* Create Modal */}
      <Dialog open={stockCountModalOpen} onOpenChange={setStockCountModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('inventory.stockCounts.createTitle', 'Create Stock Count')}</DialogTitle>
            <DialogDescription>{t('inventory.stockCounts.createDescription', 'Start a new physical inventory count')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('inventory.stockCounts.fields.type', 'Count Type')}</Label>
              <Select
                value={stockCountFormData.countType}
                onValueChange={(value) => setStockCountFormData({ ...stockCountFormData, countType: value })}
              >
                <SelectTrigger>
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
              <Label>{t('inventory.stockCounts.fields.scheduledDate', 'Scheduled Date')}</Label>
              <Input
                type="date"
                value={stockCountFormData.scheduledDate}
                onChange={(e) => setStockCountFormData({ ...stockCountFormData, scheduledDate: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('common.notes', 'Notes')}</Label>
              <Input
                value={stockCountFormData.notes}
                onChange={(e) => setStockCountFormData({ ...stockCountFormData, notes: e.target.value })}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStockCountModalOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleCreateStockCount}>{t('common.create', 'Create')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Detail Modal */}
      <Dialog open={stockCountDetailModalOpen} onOpenChange={setStockCountDetailModalOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {selectedStockCount && `${t('inventory.stockCounts.countNumber', 'Count #')} ${selectedStockCount.countNumber}`}
            </DialogTitle>
            <DialogDescription>
              {selectedStockCount && getStatusBadge(selectedStockCount.status)}
            </DialogDescription>
          </DialogHeader>
          {selectedStockCount && (
            <div className="space-y-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.stockCounts.fields.ingredient', 'Ingredient')}</TableHead>
                    <TableHead className="text-right">{t('inventory.stockCounts.fields.systemQty', 'System Qty')}</TableHead>
                    <TableHead className="text-right">{t('inventory.stockCounts.fields.countedQty', 'Counted Qty')}</TableHead>
                    <TableHead className="text-right">{t('inventory.stockCounts.fields.variance', 'Variance')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.fields.reason', 'Reason')}</TableHead>
                    <TableHead>{t('inventory.stockCounts.fields.status', 'Status')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(selectedStockCount.items || []).map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.ingredientName}</TableCell>
                      <TableCell className="text-right">{item.systemQuantity}</TableCell>
                      <TableCell className="text-right">
                        {selectedStockCount.status === 'IN_PROGRESS' && item.status !== 'COUNTED' ? (
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
                          </span>
                        ) : '-'}
                      </TableCell>
                      <TableCell>
                        {item.varianceQuantity != null && item.varianceQuantity !== 0 ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenVarianceDialog(item)}
                            className={item.varianceReason ? 'text-green-600' : 'text-orange-600'}
                          >
                            {item.varianceReason ? (
                              <>
                                <CheckCircle className="h-4 w-4 mr-1" />
                                {varianceReasons.find(r => r.value === item.varianceReason)?.label || item.varianceReason}
                              </>
                            ) : (
                              <>
                                <AlertTriangle className="h-4 w-4 mr-1" />
                                {t('inventory.stockCounts.setReason', 'Set Reason')}
                              </>
                            )}
                          </Button>
                        ) : '-'}
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.status === 'COUNTED' ? 'success' : 'secondary'}>
                          {item.status === 'PENDING' && t('inventory.stockCounts.itemStatus.pending', 'Pending')}
                          {item.status === 'COUNTED' && t('inventory.stockCounts.itemStatus.counted', 'Counted')}
                          {item.status === 'RECOUNTED' && t('inventory.stockCounts.itemStatus.recounted', 'Recounted')}
                          {item.status === 'VERIFIED' && t('inventory.stockCounts.itemStatus.verified', 'Verified')}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <DialogFooter className="flex-col sm:flex-row gap-2">
                <div className="flex gap-2 w-full sm:w-auto">
                  <Button variant="outline" onClick={() => setStockCountDetailModalOpen(false)}>
                    {t('common.close')}
                  </Button>
                  {(selectedStockCount.status === 'DRAFT' || selectedStockCount.status === 'IN_PROGRESS') && (
                    <Button variant="destructive" onClick={() => handleOpenCancelDialog(selectedStockCount)}>
                      <XCircle className="h-4 w-4 mr-1" />
                      {t('common.cancel')}
                    </Button>
                  )}
                </div>
                <div className="flex gap-2 w-full sm:w-auto">
                  {selectedStockCount.status === 'IN_PROGRESS' && selectedStockCount.countedItems === selectedStockCount.totalItems && (
                    <Button onClick={() => handleSubmitForReview(selectedStockCount.id)}>
                      <FileCheck className="h-4 w-4 mr-1" />
                      {t('inventory.stockCounts.submitForReview', 'Submit for Review')}
                    </Button>
                  )}
                  {selectedStockCount.status === 'PENDING_REVIEW' && (
                    <Button onClick={handleOpenApproveDialog} className="bg-green-600 hover:bg-green-700">
                      <CheckCircle className="h-4 w-4 mr-1" />
                      {t('inventory.stockCounts.approveAndAdjust', 'Approve & Adjust Inventory')}
                    </Button>
                  )}
                </div>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Cancel Dialog */}
      <Dialog open={cancelDialogOpen} onOpenChange={setCancelDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('inventory.stockCounts.cancelTitle', 'Cancel Stock Count')}</DialogTitle>
            <DialogDescription>
              {t('inventory.stockCounts.cancelDescription', 'Are you sure you want to cancel this stock count? This action cannot be undone.')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('inventory.stockCounts.cancelReason', 'Reason for cancellation')}</Label>
              <Textarea
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder={t('inventory.stockCounts.cancelReasonPlaceholder', 'Enter the reason for cancelling this stock count...')}
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelDialogOpen(false)} disabled={cancelling}>
              {t('common.close')}
            </Button>
            <Button variant="destructive" onClick={handleCancelStockCount} disabled={cancelling || !cancelReason.trim()}>
              {cancelling ? (
                <>
                  <RefreshCw className="h-4 w-4 mr-1 animate-spin" />
                  {t('common.cancelling', 'Cancelling...')}
                </>
              ) : (
                <>
                  <XCircle className="h-4 w-4 mr-1" />
                  {t('inventory.stockCounts.confirmCancel', 'Confirm Cancel')}
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Approve Dialog */}
      <Dialog open={approveDialogOpen} onOpenChange={setApproveDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('inventory.stockCounts.approveTitle', 'Approve Stock Count')}</DialogTitle>
            <DialogDescription>
              {t('inventory.stockCounts.approveDescription', 'Review and approve this stock count. You can optionally adjust inventory levels based on the counted quantities.')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="flex items-center space-x-2">
              <Checkbox
                id="adjustInventory"
                checked={adjustInventory}
                onCheckedChange={setAdjustInventory}
              />
              <Label htmlFor="adjustInventory" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                {t('inventory.stockCounts.adjustInventoryLabel', 'Adjust inventory levels to match counted quantities')}
              </Label>
            </div>
            {adjustInventory && (
              <div className="bg-amber-50 border border-amber-200 rounded-md p-3 text-sm text-amber-800">
                <AlertTriangle className="h-4 w-4 inline mr-2" />
                {t('inventory.stockCounts.adjustInventoryWarning', 'This will update the system inventory quantities to match the physical count. Make sure all variances have been reviewed.')}
              </div>
            )}
            <div className="space-y-2">
              <Label>{t('inventory.stockCounts.approveNotes', 'Notes (optional)')}</Label>
              <Textarea
                value={approveNotes}
                onChange={(e) => setApproveNotes(e.target.value)}
                placeholder={t('inventory.stockCounts.approveNotesPlaceholder', 'Add any notes about this approval...')}
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setApproveDialogOpen(false)} disabled={approving}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleApproveStockCount} disabled={approving} className="bg-green-600 hover:bg-green-700">
              {approving ? (
                <>
                  <RefreshCw className="h-4 w-4 mr-1 animate-spin" />
                  {t('common.approving', 'Approving...')}
                </>
              ) : (
                <>
                  <CheckCircle className="h-4 w-4 mr-1" />
                  {t('inventory.stockCounts.confirmApprove', 'Approve')}
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Variance Reason Dialog */}
      <Dialog open={varianceDialogOpen} onOpenChange={setVarianceDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('inventory.stockCounts.varianceReasonTitle', 'Set Variance Reason')}</DialogTitle>
            <DialogDescription>
              {selectedItem && (
                <>
                  {t('inventory.stockCounts.varianceReasonDesc', 'Specify the reason for the variance on {{ingredient}}', { ingredient: selectedItem.ingredientName })}
                  <div className="mt-2 text-sm">
                    <span className="font-medium">{t('inventory.stockCounts.fields.variance', 'Variance')}: </span>
                    <span className={selectedItem.varianceQuantity !== 0 ? 'text-red-600 font-medium' : ''}>
                      {selectedItem.varianceQuantity > 0 ? '+' : ''}{selectedItem.varianceQuantity}
                    </span>
                  </div>
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('inventory.stockCounts.varianceReasonLabel', 'Reason')}</Label>
              <Select value={varianceReason} onValueChange={setVarianceReason}>
                <SelectTrigger>
                  <SelectValue placeholder={t('inventory.stockCounts.selectReason', 'Select a reason')} />
                </SelectTrigger>
                <SelectContent>
                  {varianceReasons.map((reason) => (
                    <SelectItem key={reason.value} value={reason.value}>
                      {reason.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('inventory.stockCounts.varianceNotesLabel', 'Additional Notes (optional)')}</Label>
              <Textarea
                value={varianceNotes}
                onChange={(e) => setVarianceNotes(e.target.value)}
                placeholder={t('inventory.stockCounts.varianceNotesPlaceholder', 'Add any additional details...')}
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setVarianceDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSetVarianceReason} disabled={!varianceReason}>
              <CheckCircle className="h-4 w-4 mr-1" />
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
