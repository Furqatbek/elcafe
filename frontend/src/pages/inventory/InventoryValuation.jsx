import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { valuationAPI } from '../../services/api';
import { useInventory } from '../../context/InventoryContext';
import { formatCurrency } from '../../utils/formatters';
import InventoryLayout from './InventoryLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { Label } from '../../components/ui/label';
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
  Calculator,
  RefreshCw,
  TrendingUp,
  TrendingDown,
  BarChart3,
  DollarSign,
  Package,
  Loader2,
} from 'lucide-react';

const VALUATION_METHODS = [
  { value: 'FIFO', label: 'FIFO (First-In-First-Out)' },
  { value: 'LIFO', label: 'LIFO (Last-In-First-Out)' },
  { value: 'WEIGHTED_AVERAGE', label: 'Weighted Average Cost' },
  { value: 'FEFO', label: 'FEFO (First-Expired-First-Out)' },
];

export default function InventoryValuation() {
  const { t } = useTranslation();
  const { selectedRestaurant } = useInventory();

  const [currentMethod, setCurrentMethod] = useState('WEIGHTED_AVERAGE');
  const [selectedMethod, setSelectedMethod] = useState('WEIGHTED_AVERAGE');
  const [inventoryValue, setInventoryValue] = useState(null);
  const [comparison, setComparison] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (selectedRestaurant) {
      loadValuationSettings();
    }
  }, [selectedRestaurant]);

  const loadValuationSettings = async () => {
    try {
      const response = await valuationAPI.getValuationMethod(selectedRestaurant);
      const method = response.data.data?.valuationMethod || 'WEIGHTED_AVERAGE';
      setCurrentMethod(method);
      setSelectedMethod(method);
      loadInventoryValue(method);
    } catch (error) {
      console.error('Failed to load valuation settings:', error);
    }
  };

  const loadInventoryValue = async (method) => {
    setLoading(true);
    try {
      const response = await valuationAPI.calculateInventoryValue(selectedRestaurant, method);
      setInventoryValue(response.data.data);
    } catch (error) {
      console.error('Failed to calculate inventory value:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadComparison = async () => {
    setLoading(true);
    try {
      const response = await valuationAPI.compareValuationMethods(selectedRestaurant);
      setComparison(response.data.data);
    } catch (error) {
      console.error('Failed to compare valuation methods:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveMethod = async () => {
    setSaving(true);
    try {
      await valuationAPI.setValuationMethod({
        restaurantId: selectedRestaurant,
        valuationMethod: selectedMethod,
        createdBy: 'admin',
      });
      setCurrentMethod(selectedMethod);
      loadInventoryValue(selectedMethod);
      alert(t('inventory.valuation.methodChanged'));
    } catch (error) {
      console.error('Failed to save valuation method:', error);
      alert(t('inventory.valuation.errors.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const getMethodDescription = (method) => {
    return t(`inventory.valuation.methodDescriptions.${method}`);
  };

  return (
    <InventoryLayout
      title={t('inventory.valuation.title')}
      subtitle={t('inventory.valuation.subtitle')}
    >
      <div className="space-y-6">
        {/* Settings Card */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Calculator className="h-5 w-5" />
              {t('inventory.valuation.settings')}
            </CardTitle>
            <CardDescription>
              {t('inventory.valuation.settingsDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-2 mb-4">
              <Badge variant="outline" className="text-sm">
                {t('inventory.valuation.currentMethod')}:
              </Badge>
              <Badge className="bg-blue-100 text-blue-800">
                {VALUATION_METHODS.find(m => m.value === currentMethod)?.label || currentMethod}
              </Badge>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>{t('inventory.valuation.changeMethod')}</Label>
                <Select value={selectedMethod} onValueChange={setSelectedMethod}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {VALUATION_METHODS.map((method) => (
                      <SelectItem key={method.value} value={method.value}>
                        {method.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-sm text-muted-foreground">
                  {getMethodDescription(selectedMethod)}
                </p>
              </div>
              <div className="flex items-end">
                <Button
                  onClick={handleSaveMethod}
                  disabled={saving || selectedMethod === currentMethod}
                >
                  {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                  {t('common.save')}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Value Summary */}
        <div className="grid gap-4 md:grid-cols-3">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t('inventory.valuation.totalValue')}
              </CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {loading ? (
                  <Loader2 className="h-6 w-6 animate-spin" />
                ) : inventoryValue ? (
                  formatCurrency(inventoryValue.totalValue)
                ) : (
                  '-'
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                {t(`inventory.valuation.methods.${currentMethod}`)}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t('inventory.stats.total')}
              </CardTitle>
              <Package className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {inventoryValue?.ingredientValuations?.length || 0}
              </div>
              <p className="text-xs text-muted-foreground">
                {t('inventory.stats.active')}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t('inventory.valuation.compareValues')}
              </CardTitle>
              <BarChart3 className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <Button
                variant="outline"
                size="sm"
                onClick={loadComparison}
                disabled={loading}
                className="mt-2"
              >
                {loading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <RefreshCw className="h-4 w-4 mr-2" />
                )}
                {t('inventory.valuation.compareValues')}
              </Button>
            </CardContent>
          </Card>
        </div>

        {/* Comparison Results */}
        {comparison && (
          <Card>
            <CardHeader>
              <CardTitle>{t('inventory.valuation.comparison.title')}</CardTitle>
              <CardDescription>
                {t('inventory.valuation.comparison.description')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 md:grid-cols-3">
                <div className="p-4 border rounded-lg">
                  <div className="text-sm text-muted-foreground mb-1">
                    {t('inventory.valuation.comparison.fifoValue')}
                  </div>
                  <div className="text-xl font-bold">
                    {formatCurrency(comparison.fifoValue)}
                  </div>
                </div>
                <div className="p-4 border rounded-lg">
                  <div className="text-sm text-muted-foreground mb-1">
                    {t('inventory.valuation.comparison.lifoValue')}
                  </div>
                  <div className="text-xl font-bold">
                    {formatCurrency(comparison.lifoValue)}
                  </div>
                </div>
                <div className="p-4 border rounded-lg">
                  <div className="text-sm text-muted-foreground mb-1">
                    {t('inventory.valuation.comparison.wacValue')}
                  </div>
                  <div className="text-xl font-bold">
                    {formatCurrency(comparison.weightedAverageValue)}
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Ingredient Values Table */}
        {inventoryValue?.ingredientValuations && (
          <Card>
            <CardHeader>
              <CardTitle>{t('inventory.valuation.inventoryValue')}</CardTitle>
              <CardDescription>
                {t('inventory.valuation.subtitle')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.fields.name')}</TableHead>
                    <TableHead className="text-right">{t('inventory.fields.currentStock')}</TableHead>
                    <TableHead className="text-right">{t('inventory.fields.costPerUnit')}</TableHead>
                    <TableHead className="text-right">{t('inventory.valuation.totalValue')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {inventoryValue.ingredientValuations.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4} className="text-center py-8 text-muted-foreground">
                        {t('inventory.noIngredients')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    inventoryValue.ingredientValuations.map((item) => (
                      <TableRow key={item.ingredientId}>
                        <TableCell className="font-medium">{item.ingredientName}</TableCell>
                        <TableCell className="text-right">
                          {item.quantity?.toFixed(2)} {item.unit}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatCurrency(item.costPerUnit)}
                        </TableCell>
                        <TableCell className="text-right font-medium">
                          {formatCurrency(item.value)}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>
    </InventoryLayout>
  );
}
