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
} from 'lucide-react';

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
      await stockCountAPI.submitForReview(id);
      const response = await stockCountAPI.getById(id);
      setSelectedStockCount(response.data.data);
      loadStockCounts();
    } catch (error) {
      console.error('Failed to submit for review:', error);
    }
  };

  const handleApproveStockCount = async (id) => {
    try {
      await stockCountAPI.approve(id);
      setStockCountDetailModalOpen(false);
      loadStockCounts();
      loadIngredients();
    } catch (error) {
      console.error('Failed to approve stock count:', error);
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
              <DialogFooter>
                <Button variant="outline" onClick={() => setStockCountDetailModalOpen(false)}>{t('common.close')}</Button>
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
    </InventoryLayout>
  );
}
