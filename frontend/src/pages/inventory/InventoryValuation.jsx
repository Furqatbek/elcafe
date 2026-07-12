import { useEffect, useState } from 'react';
import { notifySuccess, notifyWarning } from '../../lib/errors';
import { useTranslation } from 'react-i18next';
import { valuationAPI } from '../../services/api';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';

// Format number to 2 decimal places
const formatNumber = (value) => {
  if (value == null) return '-';
  return Number(value).toFixed(2);
};
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { Label } from '../../components/ui/label';
import { Input } from '../../components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../components/ui/tabs';
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
  Hash,
  Package,
  Loader2,
  FileText,
  AlertTriangle,
  CheckCircle,
  ArrowUpDown,
} from 'lucide-react';

const VALUATION_METHODS = [
  { value: 'FIFO', labelKey: 'inventory.valuation.methods.FIFO' },
  { value: 'LIFO', labelKey: 'inventory.valuation.methods.LIFO' },
  { value: 'WEIGHTED_AVERAGE', labelKey: 'inventory.valuation.methods.WEIGHTED_AVERAGE' },
  { value: 'FEFO', labelKey: 'inventory.valuation.methods.FEFO' },
];

export default function InventoryValuation() {
  const { t } = useTranslation();
  const { selectedRestaurant } = useInventory();

  const [currentMethod, setCurrentMethod] = useState('WEIGHTED_AVERAGE');
  const [selectedMethod, setSelectedMethod] = useState('WEIGHTED_AVERAGE');
  const [inventoryValue, setInventoryValue] = useState(null);
  const [comparison, setComparison] = useState(null);
  const [comparisonReport, setComparisonReport] = useState(null);
  const [inventoryReport, setInventoryReport] = useState(null);
  const [varianceReport, setVarianceReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState('settings');

  // Date range for variance report
  const [startDate, setStartDate] = useState(() => {
    const date = new Date();
    date.setMonth(date.getMonth() - 1);
    return date.toISOString().split('T')[0];
  });
  const [endDate, setEndDate] = useState(() => new Date().toISOString().split('T')[0]);

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

  const loadComparisonReport = async () => {
    setLoading(true);
    try {
      const response = await valuationAPI.getComparisonReport(selectedRestaurant);
      setComparisonReport(response.data.data);
    } catch (error) {
      console.error('Failed to load comparison report:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadInventoryReport = async () => {
    setLoading(true);
    try {
      const response = await valuationAPI.getInventoryValuationReport(selectedRestaurant, currentMethod);
      setInventoryReport(response.data.data);
    } catch (error) {
      console.error('Failed to load inventory report:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadVarianceReport = async () => {
    setLoading(true);
    try {
      const start = new Date(startDate);
      start.setHours(0, 0, 0, 0);
      const end = new Date(endDate);
      end.setHours(23, 59, 59, 999);

      const response = await valuationAPI.getCostVarianceReport(
        selectedRestaurant,
        start.toISOString(),
        end.toISOString()
      );
      setVarianceReport(response.data.data);
    } catch (error) {
      console.error('Failed to load variance report:', error);
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
      notifySuccess(t('inventory.valuation.methodChanged'));
    } catch (error) {
      console.error('Failed to save valuation method:', error);
      notifyWarning(t('inventory.valuation.errors.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const getMethodDescription = (method) => {
    return t(`inventory.valuation.methodDescriptions.${method}`);
  };

  const formatVariance = (value) => {
    if (!value) return '-';
    const isPositive = value > 0;
    return (
      <span className={isPositive ? 'text-red-600' : 'text-green-600'}>
        {isPositive ? '+' : ''}{formatNumber(value)}
      </span>
    );
  };

  return (
    <InventoryLayout
      title={t('inventory.valuation.title')}
      subtitle={t('inventory.valuation.subtitle')}
    >
      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-6">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="settings">
            <Calculator className="h-4 w-4 mr-2" />
            {t('inventory.valuation.settings')}
          </TabsTrigger>
          <TabsTrigger value="comparison">
            <ArrowUpDown className="h-4 w-4 mr-2" />
            {t('inventory.valuation.reports.comparison.title')}
          </TabsTrigger>
          <TabsTrigger value="inventory">
            <Package className="h-4 w-4 mr-2" />
            {t('inventory.valuation.reports.inventory.title')}
          </TabsTrigger>
          <TabsTrigger value="variance">
            <BarChart3 className="h-4 w-4 mr-2" />
            {t('inventory.valuation.reports.variance.title')}
          </TabsTrigger>
        </TabsList>

        {/* Settings Tab */}
        <TabsContent value="settings">
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
                    {t(VALUATION_METHODS.find(m => m.value === currentMethod)?.labelKey) || currentMethod}
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
                            {t(method.labelKey)}
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
                  <Hash className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">
                    {loading ? (
                      <Loader2 className="h-6 w-6 animate-spin" />
                    ) : inventoryValue ? (
                      formatNumber(inventoryValue.totalValue)
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
                        {formatNumber(comparison.fifoValue)}
                      </div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">
                        {t('inventory.valuation.comparison.lifoValue')}
                      </div>
                      <div className="text-xl font-bold">
                        {formatNumber(comparison.lifoValue)}
                      </div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">
                        {t('inventory.valuation.comparison.wacValue')}
                      </div>
                      <div className="text-xl font-bold">
                        {formatNumber(comparison.weightedAverageValue)}
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
                              {formatNumber(item.costPerUnit)}
                            </TableCell>
                            <TableCell className="text-right font-medium">
                              {formatNumber(item.value)}
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
        </TabsContent>

        {/* Comparison Report Tab */}
        <TabsContent value="comparison">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>{t('inventory.valuation.reports.comparison.title')}</CardTitle>
                  <CardDescription>
                    {t('inventory.valuation.reports.comparison.description')}
                  </CardDescription>
                </div>
                <Button onClick={loadComparisonReport} disabled={loading}>
                  {loading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <RefreshCw className="h-4 w-4 mr-2" />}
                  {t('common.refresh')}
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {comparisonReport ? (
                <div className="space-y-6">
                  {/* Summary */}
                  <div className="grid gap-4 md:grid-cols-4">
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.comparison.fifoValue')}</div>
                      <div className="text-xl font-bold">{formatNumber(comparisonReport.fifoValue)}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.comparison.lifoValue')}</div>
                      <div className="text-xl font-bold">{formatNumber(comparisonReport.lifoValue)}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.comparison.wacValue')}</div>
                      <div className="text-xl font-bold">{formatNumber(comparisonReport.weightedAverageValue)}</div>
                    </div>
                    <div className="p-4 border rounded-lg bg-blue-50">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.currentMethod')}</div>
                      <div className="text-lg font-bold">{comparisonReport.currentMethod}</div>
                      <div className="text-sm">{formatNumber(comparisonReport.currentMethodValue)}</div>
                    </div>
                  </div>

                  {/* Recommendation */}
                  {comparisonReport.recommendation && (
                    <div className="p-4 border rounded-lg bg-yellow-50">
                      <div className="flex items-start gap-2">
                        <AlertTriangle className="h-5 w-5 text-yellow-600 mt-0.5" />
                        <div>
                          <div className="font-medium">{t('inventory.valuation.reports.comparison.recommendation')}</div>
                          <div className="text-sm text-muted-foreground">{comparisonReport.recommendation}</div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Differences */}
                  <div className="grid gap-4 md:grid-cols-3">
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.comparison.fifoVsLifo')}</div>
                      <div className="text-lg font-medium">{formatVariance(comparisonReport.fifoVsLifo)}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.comparison.fifoVsWac')}</div>
                      <div className="text-lg font-medium">{formatVariance(comparisonReport.fifoVsWac)}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.comparison.lifoVsWac')}</div>
                      <div className="text-lg font-medium">{formatVariance(comparisonReport.lifoVsWac)}</div>
                    </div>
                  </div>

                  {/* Ingredient Comparisons */}
                  {comparisonReport.ingredientComparisons?.length > 0 && (
                    <div>
                      <h4 className="font-medium mb-3">{t('inventory.valuation.reports.comparison.ingredientComparisons')}</h4>
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>{t('inventory.fields.name')}</TableHead>
                            <TableHead>{t('inventory.fields.category')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.comparison.fifoValue')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.comparison.lifoValue')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.comparison.wacValue')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.reports.comparison.maxVariance')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.reports.comparison.variancePercent')}</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {comparisonReport.ingredientComparisons.slice(0, 20).map((item) => (
                            <TableRow key={item.ingredientId}>
                              <TableCell className="font-medium">{item.ingredientName}</TableCell>
                              <TableCell>{item.category}</TableCell>
                              <TableCell className="text-right">{formatNumber(item.fifoValue)}</TableCell>
                              <TableCell className="text-right">{formatNumber(item.lifoValue)}</TableCell>
                              <TableCell className="text-right">{formatNumber(item.wacValue)}</TableCell>
                              <TableCell className="text-right">{formatNumber(item.maxVariance)}</TableCell>
                              <TableCell className="text-right">
                                <Badge variant={item.variancePercent > 10 ? 'destructive' : 'secondary'}>
                                  {item.variancePercent?.toFixed(1)}%
                                </Badge>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  <FileText className="h-12 w-12 mx-auto mb-4 opacity-50" />
                  <p>{t('common.clickRefresh')}</p>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Inventory Report Tab */}
        <TabsContent value="inventory">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>{t('inventory.valuation.reports.inventory.title')}</CardTitle>
                  <CardDescription>
                    {t('inventory.valuation.reports.inventory.description')}
                  </CardDescription>
                </div>
                <Button onClick={loadInventoryReport} disabled={loading}>
                  {loading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <RefreshCw className="h-4 w-4 mr-2" />}
                  {t('common.refresh')}
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {inventoryReport ? (
                <div className="space-y-6">
                  {/* Summary Stats */}
                  <div className="grid gap-4 md:grid-cols-4">
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.totalValue')}</div>
                      <div className="text-xl font-bold">{formatNumber(inventoryReport.totalInventoryValue)}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.stats.total')}</div>
                      <div className="text-xl font-bold">{inventoryReport.totalIngredients}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.inventory.ingredientsWithStock')}</div>
                      <div className="text-xl font-bold">{inventoryReport.ingredientsWithStock}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.inventory.averageCost')}</div>
                      <div className="text-xl font-bold">{formatNumber(inventoryReport.averageCostPerUnit)}</div>
                    </div>
                  </div>

                  {/* By Category */}
                  {inventoryReport.categoryValuations?.length > 0 && (
                    <div>
                      <h4 className="font-medium mb-3">{t('inventory.valuation.reports.inventory.byCategory')}</h4>
                      <div className="grid gap-4 md:grid-cols-3">
                        {inventoryReport.categoryValuations.map((cat) => (
                          <div key={cat.category} className="p-4 border rounded-lg">
                            <div className="flex justify-between items-start mb-2">
                              <div className="font-medium">{cat.category}</div>
                              <Badge variant="secondary">{cat.ingredientCount} {t('common.items')}</Badge>
                            </div>
                            <div className="text-xl font-bold">{formatNumber(cat.totalValue)}</div>
                            <div className="text-sm text-muted-foreground">{cat.percentageOfTotal?.toFixed(1)}% {t('inventory.valuation.reports.inventory.ofTotal')}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* By Ingredient */}
                  {inventoryReport.ingredientValuations?.length > 0 && (
                    <div>
                      <h4 className="font-medium mb-3">{t('inventory.valuation.reports.inventory.byIngredient')}</h4>
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>{t('inventory.fields.name')}</TableHead>
                            <TableHead>{t('inventory.fields.category')}</TableHead>
                            <TableHead className="text-right">{t('inventory.fields.currentStock')}</TableHead>
                            <TableHead className="text-right">{t('inventory.fields.costPerUnit')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.totalValue')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.reports.inventory.percentOfTotal')}</TableHead>
                            <TableHead className="text-right">{t('inventory.valuation.reports.inventory.activeBatches')}</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {inventoryReport.ingredientValuations.map((item) => (
                            <TableRow key={item.ingredientId}>
                              <TableCell className="font-medium">{item.ingredientName}</TableCell>
                              <TableCell>{item.category}</TableCell>
                              <TableCell className="text-right">{item.currentStock?.toFixed(2)} {item.unit}</TableCell>
                              <TableCell className="text-right">{formatNumber(item.costPerUnit)}</TableCell>
                              <TableCell className="text-right font-medium">{formatNumber(item.totalValue)}</TableCell>
                              <TableCell className="text-right">{item.percentageOfTotal?.toFixed(1)}%</TableCell>
                              <TableCell className="text-right">{item.activeBatches}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  <FileText className="h-12 w-12 mx-auto mb-4 opacity-50" />
                  <p>{t('common.clickRefresh')}</p>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Variance Report Tab */}
        <TabsContent value="variance">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>{t('inventory.valuation.reports.variance.title')}</CardTitle>
                  <CardDescription>
                    {t('inventory.valuation.reports.variance.description')}
                  </CardDescription>
                </div>
                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-2">
                    <Label>{t('common.from')}</Label>
                    <Input
                      type="date"
                      value={startDate}
                      onChange={(e) => setStartDate(e.target.value)}
                      className="w-40"
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <Label>{t('common.to')}</Label>
                    <Input
                      type="date"
                      value={endDate}
                      onChange={(e) => setEndDate(e.target.value)}
                      className="w-40"
                    />
                  </div>
                  <Button onClick={loadVarianceReport} disabled={loading}>
                    {loading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <BarChart3 className="h-4 w-4 mr-2" />}
                    {t('inventory.valuation.reports.variance.generate')}
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {varianceReport ? (
                <div className="space-y-6">
                  {/* Summary */}
                  <div className="grid gap-4 md:grid-cols-4">
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.variance.actualCost')}</div>
                      <div className="text-xl font-bold">{formatNumber(varianceReport.totalActualCost)}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.variance.standardCost')}</div>
                      <div className="text-xl font-bold">{formatNumber(varianceReport.totalStandardCost)}</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="text-sm text-muted-foreground mb-1">{t('inventory.valuation.reports.variance.totalVariance')}</div>
                      <div className="text-xl font-bold">{formatVariance(varianceReport.totalVariance)}</div>
                      <div className="text-sm text-muted-foreground">{varianceReport.variancePercent?.toFixed(1)}%</div>
                    </div>
                    <div className="p-4 border rounded-lg">
                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <div className="flex items-center gap-1 text-green-600">
                            <CheckCircle className="h-4 w-4" />
                            <span className="text-sm">{t('inventory.valuation.reports.variance.favorable')}</span>
                          </div>
                          <div className="font-bold">{varianceReport.favorableVariances}</div>
                          <div className="text-xs text-muted-foreground">{formatNumber(varianceReport.totalFavorable)}</div>
                        </div>
                        <div>
                          <div className="flex items-center gap-1 text-red-600">
                            <AlertTriangle className="h-4 w-4" />
                            <span className="text-sm">{t('inventory.valuation.reports.variance.unfavorable')}</span>
                          </div>
                          <div className="font-bold">{varianceReport.unfavorableVariances}</div>
                          <div className="text-xs text-muted-foreground">{formatNumber(varianceReport.totalUnfavorable)}</div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Ingredient Variances */}
                  {varianceReport.ingredientVariances?.length > 0 && (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t('inventory.fields.name')}</TableHead>
                          <TableHead>{t('inventory.fields.category')}</TableHead>
                          <TableHead className="text-right">{t('inventory.fields.quantity')}</TableHead>
                          <TableHead className="text-right">{t('inventory.valuation.reports.variance.actualCost')}</TableHead>
                          <TableHead className="text-right">{t('inventory.valuation.reports.variance.standardCost')}</TableHead>
                          <TableHead className="text-right">{t('inventory.valuation.reports.variance.totalVariance')}</TableHead>
                          <TableHead>{t('inventory.valuation.reports.variance.varianceReason')}</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {varianceReport.ingredientVariances.map((item) => (
                          <TableRow key={item.ingredientId}>
                            <TableCell className="font-medium">{item.ingredientName}</TableCell>
                            <TableCell>{item.category}</TableCell>
                            <TableCell className="text-right">{item.quantityConsumed?.toFixed(2)} {item.unit}</TableCell>
                            <TableCell className="text-right">{formatNumber(item.actualTotalCost)}</TableCell>
                            <TableCell className="text-right">{formatNumber(item.standardTotalCost)}</TableCell>
                            <TableCell className="text-right">
                              <div className="flex items-center justify-end gap-1">
                                {item.favorable ? (
                                  <CheckCircle className="h-4 w-4 text-green-600" />
                                ) : item.varianceAmount > 0 ? (
                                  <AlertTriangle className="h-4 w-4 text-red-600" />
                                ) : null}
                                {formatVariance(item.varianceAmount)}
                              </div>
                            </TableCell>
                            <TableCell className="text-sm text-muted-foreground max-w-[200px] truncate">
                              {item.varianceReason}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </div>
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  <FileText className="h-12 w-12 mx-auto mb-4 opacity-50" />
                  <p>{t('inventory.valuation.reports.variance.selectDateRange')}</p>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </InventoryLayout>
  );
}
