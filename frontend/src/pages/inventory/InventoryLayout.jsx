import { useTranslation } from 'react-i18next';
import { useNavigate, useLocation } from 'react-router-dom';
import { useInventory } from '../../context/InventoryContext';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../../components/ui/select';
import {
  Package,
  UtensilsCrossed,
  Calendar,
  ClipboardCheck,
  Trash2,
  Truck,
  Bell,
  Calculator,
} from 'lucide-react';

const navItems = [
  { path: '/kitchen/inventory', label: 'inventory.tabs.ingredients', icon: Package },
  { path: '/kitchen/recipes', label: 'inventory.tabs.recipes', icon: UtensilsCrossed },
  { path: '/kitchen/expiry', label: 'inventory.tabs.expiry', icon: Calendar },
  { path: '/kitchen/stock-counts', label: 'inventory.tabs.stockCounts', icon: ClipboardCheck },
  { path: '/kitchen/waste', label: 'inventory.tabs.waste', icon: Trash2 },
  { path: '/kitchen/suppliers', label: 'inventory.tabs.suppliers', icon: Truck },
  { path: '/kitchen/stock-alerts', label: 'inventory.tabs.stockAlerts', icon: Bell },
  { path: '/kitchen/valuation', label: 'inventory.tabs.valuation', icon: Calculator },
];

export default function InventoryLayout({ children, title, subtitle }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { restaurants, selectedRestaurant, setSelectedRestaurant } = useInventory();

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {title || t('inventory.title')}
          </h1>
          <p className="text-muted-foreground">
            {subtitle || t('inventory.subtitle')}
          </p>
        </div>
        <Select
          value={selectedRestaurant?.toString() || ''}
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
      </div>

      {/* Navigation Tabs */}
      <div className="flex space-x-1 border-b pb-2 overflow-x-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname === item.path;
          return (
            <Button
              key={item.path}
              variant={isActive ? 'default' : 'ghost'}
              className="flex items-center gap-2 whitespace-nowrap"
              onClick={() => navigate(item.path)}
            >
              <Icon className="h-4 w-4" />
              {t(item.label)}
            </Button>
          );
        })}
      </div>

      {/* Page Content */}
      {children}
    </div>
  );
}
