import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Cookie,
  Plus,
  Search,
  AlertTriangle,
  TrendingDown,
  TrendingUp,
  Package,
  Filter,
  Download,
  Upload,
  BarChart3,
} from 'lucide-react';

export default function KitchenInventory() {
  const { t } = useTranslation();
  const [searchQuery, setSearchQuery] = useState('');
  const [filterCategory, setFilterCategory] = useState('ALL');
  const [filterStatus, setFilterStatus] = useState('ALL');

  // Placeholder inventory items for demonstration
  const inventoryItems = [
    {
      id: 1,
      name: 'Tomatoes',
      category: 'Vegetables',
      currentStock: 25,
      unit: 'kg',
      minStock: 20,
      maxStock: 100,
      status: 'Normal',
      lastRestocked: '2025-12-20',
      supplier: 'Fresh Farms Co.',
    },
    {
      id: 2,
      name: 'Mozzarella Cheese',
      category: 'Dairy',
      currentStock: 8,
      unit: 'kg',
      minStock: 10,
      maxStock: 50,
      status: 'Low',
      lastRestocked: '2025-12-18',
      supplier: 'Dairy Express',
    },
    {
      id: 3,
      name: 'Olive Oil',
      category: 'Oils',
      currentStock: 2,
      unit: 'liters',
      minStock: 5,
      maxStock: 30,
      status: 'Critical',
      lastRestocked: '2025-12-15',
      supplier: 'Mediterranean Imports',
    },
    {
      id: 4,
      name: 'All-Purpose Flour',
      category: 'Dry Goods',
      currentStock: 45,
      unit: 'kg',
      minStock: 20,
      maxStock: 100,
      status: 'Normal',
      lastRestocked: '2025-12-19',
      supplier: 'Grain Wholesalers',
    },
  ];

  const getStatusColor = (status) => {
    switch (status) {
      case 'Critical': return 'bg-red-100 text-red-800 border-red-300';
      case 'Low': return 'bg-orange-100 text-orange-800 border-orange-300';
      case 'Normal': return 'bg-green-100 text-green-800 border-green-300';
      case 'Overstock': return 'bg-blue-100 text-blue-800 border-blue-300';
      default: return 'bg-gray-100 text-gray-800 border-gray-300';
    }
  };

  const getStockPercentage = (current, min, max) => {
    return ((current - min) / (max - min)) * 100;
  };

  const stats = {
    totalItems: inventoryItems.length,
    criticalItems: inventoryItems.filter(i => i.status === 'Critical').length,
    lowStock: inventoryItems.filter(i => i.status === 'Low').length,
    normalStock: inventoryItems.filter(i => i.status === 'Normal').length,
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <Cookie className="h-8 w-8" />
            {t('kitchen.inventory.title') || 'Kitchen Inventory'}
          </h1>
          <p className="text-muted-foreground mt-1">
            {t('kitchen.inventory.subtitle') || 'Track and manage your kitchen stock levels'}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" className="flex items-center gap-2">
            <Download className="h-4 w-4" />
            {t('common.export') || 'Export'}
          </Button>
          <Button className="flex items-center gap-2">
            <Plus className="h-4 w-4" />
            {t('kitchen.inventory.addItem') || 'Add Item'}
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('kitchen.inventory.stats.total') || 'Total Items'}
            </CardTitle>
            <Package className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalItems}</div>
            <p className="text-xs text-muted-foreground">
              {t('kitchen.inventory.stats.tracked') || 'Items tracked'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('kitchen.inventory.stats.critical') || 'Critical Stock'}
            </CardTitle>
            <AlertTriangle className="h-4 w-4 text-red-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">{stats.criticalItems}</div>
            <p className="text-xs text-muted-foreground">
              {t('kitchen.inventory.stats.needsRestock') || 'Needs immediate restock'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('kitchen.inventory.stats.low') || 'Low Stock'}
            </CardTitle>
            <TrendingDown className="h-4 w-4 text-orange-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-orange-600">{stats.lowStock}</div>
            <p className="text-xs text-muted-foreground">
              {t('kitchen.inventory.stats.belowMin') || 'Below minimum level'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('kitchen.inventory.stats.normal') || 'Normal Stock'}
            </CardTitle>
            <TrendingUp className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">{stats.normalStock}</div>
            <p className="text-xs text-muted-foreground">
              {t('kitchen.inventory.stats.adequate') || 'Adequate levels'}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-wrap items-end gap-4">
            <div className="flex-1 min-w-[200px]">
              <Label htmlFor="search" className="mb-2 flex items-center gap-2">
                <Search className="h-4 w-4" />
                {t('common.search') || 'Search'}
              </Label>
              <Input
                id="search"
                type="text"
                placeholder={t('kitchen.inventory.searchPlaceholder') || 'Search items...'}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>

            <div className="min-w-[180px]">
              <Label htmlFor="category" className="mb-2 flex items-center gap-2">
                <Filter className="h-4 w-4" />
                {t('common.category') || 'Category'}
              </Label>
              <Select value={filterCategory} onValueChange={setFilterCategory}>
                <SelectTrigger id="category">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">{t('common.all') || 'All Categories'}</SelectItem>
                  <SelectItem value="Vegetables">{t('kitchen.inventory.categories.vegetables') || 'Vegetables'}</SelectItem>
                  <SelectItem value="Dairy">{t('kitchen.inventory.categories.dairy') || 'Dairy'}</SelectItem>
                  <SelectItem value="Meats">{t('kitchen.inventory.categories.meats') || 'Meats'}</SelectItem>
                  <SelectItem value="Dry Goods">{t('kitchen.inventory.categories.dryGoods') || 'Dry Goods'}</SelectItem>
                  <SelectItem value="Oils">{t('kitchen.inventory.categories.oils') || 'Oils'}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="min-w-[180px]">
              <Label htmlFor="status" className="mb-2 flex items-center gap-2">
                <BarChart3 className="h-4 w-4" />
                {t('common.status') || 'Status'}
              </Label>
              <Select value={filterStatus} onValueChange={setFilterStatus}>
                <SelectTrigger id="status">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">{t('common.all') || 'All Status'}</SelectItem>
                  <SelectItem value="Critical">{t('kitchen.inventory.status.critical') || 'Critical'}</SelectItem>
                  <SelectItem value="Low">{t('kitchen.inventory.status.low') || 'Low'}</SelectItem>
                  <SelectItem value="Normal">{t('kitchen.inventory.status.normal') || 'Normal'}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Coming Soon Notice */}
      <Card className="bg-gradient-to-r from-purple-50 to-pink-50 border-purple-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-purple-900">
            <Package className="h-5 w-5" />
            {t('common.comingSoon') || 'Coming Soon'}
          </CardTitle>
          <CardDescription>
            {t('kitchen.inventory.comingSoonDesc') || 'The Inventory Management feature is under development. You will soon be able to track stock levels, set reorder points, manage suppliers, and receive automated alerts for low stock items.'}
          </CardDescription>
        </CardHeader>
      </Card>

      {/* Inventory Items Table */}
      <Card>
        <CardHeader>
          <CardTitle>{t('kitchen.inventory.items') || 'Inventory Items'}</CardTitle>
          <CardDescription>
            {t('kitchen.inventory.itemsDesc') || 'Monitor your current stock levels and manage inventory'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {inventoryItems.map((item) => (
              <Card key={item.id} className="border-l-4 border-l-gray-300">
                <CardContent className="pt-6">
                  <div className="flex items-start justify-between">
                    <div className="flex-1 space-y-3">
                      <div className="flex items-center gap-3">
                        <h3 className="text-lg font-semibold">{item.name}</h3>
                        <Badge variant="secondary" className="text-xs">
                          {item.category}
                        </Badge>
                        <Badge className={getStatusColor(item.status)}>
                          {item.status}
                        </Badge>
                      </div>

                      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                        <div>
                          <span className="text-muted-foreground">Current Stock:</span>
                          <div className="font-semibold">{item.currentStock} {item.unit}</div>
                        </div>
                        <div>
                          <span className="text-muted-foreground">Min / Max:</span>
                          <div className="font-semibold">{item.minStock} / {item.maxStock} {item.unit}</div>
                        </div>
                        <div>
                          <span className="text-muted-foreground">Last Restocked:</span>
                          <div className="font-semibold">{item.lastRestocked}</div>
                        </div>
                        <div>
                          <span className="text-muted-foreground">Supplier:</span>
                          <div className="font-semibold text-blue-600">{item.supplier}</div>
                        </div>
                      </div>

                      {/* Stock Level Bar */}
                      <div className="space-y-1">
                        <div className="flex justify-between text-xs text-muted-foreground">
                          <span>Stock Level</span>
                          <span>{((item.currentStock / item.maxStock) * 100).toFixed(0)}%</span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                          <div
                            className={`h-2 rounded-full transition-all ${
                              item.status === 'Critical' ? 'bg-red-500' :
                              item.status === 'Low' ? 'bg-orange-500' :
                              'bg-green-500'
                            }`}
                            style={{ width: `${Math.min(100, (item.currentStock / item.maxStock) * 100)}%` }}
                          />
                        </div>
                      </div>
                    </div>

                    <div className="flex gap-2 ml-4">
                      <Button variant="outline" size="sm">
                        <Upload className="h-4 w-4 mr-2" />
                        Restock
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
