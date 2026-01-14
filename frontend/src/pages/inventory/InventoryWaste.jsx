import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { wasteAPI } from '../../services/api';
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
  Trash2,
  AlertTriangle,
  TrendingDown,
  History,
} from 'lucide-react';

export default function InventoryWaste() {
  const { t } = useTranslation();
  const { selectedRestaurant, ingredients, loadIngredients } = useInventory();

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
    if (selectedRestaurant) {
      loadWasteRecords();
      loadWasteReasons();
    }
  }, [selectedRestaurant]);

  const loadWasteRecords = async () => {
    try {
      const endDate = new Date().toISOString().split('T')[0];
      const startDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const response = await wasteAPI.getAll(selectedRestaurant, { startDate, endDate });
      setWasteRecords(response.data.data || []);

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

  return (
    <InventoryLayout>
      <div className="space-y-6">
        {/* Stats */}
        <div className="grid gap-4 md:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.waste.totalWaste', 'Total Waste (30 days)')}</CardTitle>
              <Trash2 className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-red-600">{wasteReport?.totalWasteCost?.toFixed(2) || '0.00'}</div>
              <p className="text-xs text-muted-foreground">{wasteReport?.recordCount || 0} {t('inventory.waste.records', 'records')}</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.waste.mostCommonReason', 'Most Common Reason')}</CardTitle>
              <AlertTriangle className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{wasteReport?.mostCommonReason || '-'}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.waste.totalQuantity', 'Total Quantity')}</CardTitle>
              <TrendingDown className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{wasteReport?.totalWasteQuantity?.toFixed(2) || '0'}</div>
              <p className="text-xs text-muted-foreground">{t('inventory.waste.units', 'units wasted')}</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.waste.avgPerRecord', 'Avg Cost per Record')}</CardTitle>
              <History className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {wasteReport?.recordCount > 0 ? (wasteReport.totalWasteCost / wasteReport.recordCount).toFixed(2) : '0.00'}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Records Table */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Trash2 className="h-5 w-5" />
                  {t('inventory.waste.records', 'Waste Records')}
                </CardTitle>
                <CardDescription>{t('inventory.waste.recordsDesc', 'Track and analyze waste across your inventory')}</CardDescription>
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
                      {t('inventory.waste.noRecords', 'No waste records found.')}
                    </TableCell>
                  </TableRow>
                ) : (
                  wasteRecords.map((record) => (
                    <TableRow key={record.id}>
                      <TableCell>{record.wasteDate}</TableCell>
                      <TableCell className="font-medium">{record.ingredientName}</TableCell>
                      <TableCell>{record.quantity} {record.unit}</TableCell>
                      <TableCell>{getWasteReasonBadge(record.wasteReason)}</TableCell>
                      <TableCell className="text-red-600 font-medium">{record.totalCost?.toFixed(2) || '0.00'}</TableCell>
                      <TableCell>{record.recordedBy || '-'}</TableCell>
                      <TableCell className="text-right">
                        <Button variant="ghost" size="icon" onClick={() => handleDeleteWaste(record.id)}>
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

        {/* Waste by Reason */}
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
                      <TableCell className="text-right text-red-600">{item.totalCost?.toFixed(2)}</TableCell>
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
                      <TableCell className="text-right text-red-600">{item.totalCost?.toFixed(2)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>

      {/* Record Waste Modal */}
      <Dialog open={wasteModalOpen} onOpenChange={setWasteModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('inventory.waste.recordWaste', 'Record Waste')}</DialogTitle>
            <DialogDescription>{t('inventory.waste.recordWasteDesc', 'Record a waste event for an ingredient')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('inventory.fields.ingredient', 'Ingredient')} *</Label>
              <Select
                value={wasteFormData.ingredientId}
                onValueChange={(value) => setWasteFormData({ ...wasteFormData, ingredientId: value })}
              >
                <SelectTrigger>
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
                <Label>{t('inventory.waste.quantity', 'Quantity')} *</Label>
                <Input
                  type="number"
                  step="0.001"
                  value={wasteFormData.quantity}
                  onChange={(e) => setWasteFormData({ ...wasteFormData, quantity: e.target.value })}
                  placeholder="0.00"
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.waste.date', 'Date')} *</Label>
                <Input
                  type="date"
                  value={wasteFormData.wasteDate}
                  onChange={(e) => setWasteFormData({ ...wasteFormData, wasteDate: e.target.value })}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('inventory.waste.reason', 'Reason')} *</Label>
              <Select
                value={wasteFormData.wasteReason}
                onValueChange={(value) => setWasteFormData({ ...wasteFormData, wasteReason: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {wasteReasons.map((reason) => (
                    <SelectItem key={reason.value} value={reason.value}>{reason.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('inventory.waste.notes', 'Notes')}</Label>
              <Input
                value={wasteFormData.notes}
                onChange={(e) => setWasteFormData({ ...wasteFormData, notes: e.target.value })}
                placeholder={t('inventory.waste.notesPlaceholder', 'Optional notes about this waste')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWasteModalOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleRecordWaste} disabled={!wasteFormData.ingredientId || !wasteFormData.quantity}>
              {t('inventory.waste.record', 'Record')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
