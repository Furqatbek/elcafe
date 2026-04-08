import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';
import { productionBatchAPI } from '../../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { Plus } from 'lucide-react';

const STATUS_COLORS = {
  DRAFT: 'secondary',
  IN_PROGRESS: 'outline',
  READY: 'default',
  SERVING: 'default',
  DEPLETED: 'secondary',
  EXPIRED: 'destructive',
  WASTED: 'destructive',
};

export default function ProductionBatches() {
  const { t } = useTranslation();
  const { selectedRestaurant } = useInventory();
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState(null);

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

  useEffect(() => {
    loadBatches();
  }, [selectedRestaurant, statusFilter]);

  const statusFilters = [null, 'READY', 'SERVING', 'DRAFT', 'IN_PROGRESS', 'DEPLETED', 'EXPIRED'];

  return (
    <InventoryLayout
      title={t('production.title', 'Production Batches')}
      subtitle={t('production.subtitle', 'Track batch-cooked items and prepared inventory')}
    >
      <div className="space-y-4">
        {/* Filter bar */}
        <div className="flex items-center justify-between">
          <div className="flex gap-2">
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
          <Button onClick={() => {/* TODO: open create dialog */}}>
            <Plus className="h-4 w-4 mr-2" />
            {t('production.createBatch', 'New Batch')}
          </Button>
        </div>

        {/* Batch list */}
        {loading ? (
          <div className="text-center py-8 text-muted-foreground">
            {t('common.loading', 'Loading...')}
          </div>
        ) : batches.length === 0 ? (
          <Card>
            <CardContent className="text-center py-8 text-muted-foreground">
              {t('production.noBatches', 'No production batches found')}
            </CardContent>
          </Card>
        ) : (
          <div className="border rounded-lg">
            <table className="w-full">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="text-left p-3 font-medium">{t('production.name', 'Batch Name')}</th>
                  <th className="text-left p-3 font-medium">{t('production.product', 'Product')}</th>
                  <th className="text-right p-3 font-medium">{t('production.outputQuantity', 'Yield')}</th>
                  <th className="text-right p-3 font-medium">{t('production.remainingQuantity', 'Remaining')}</th>
                  <th className="text-right p-3 font-medium">{t('production.costPerUnit', 'Cost/Unit')}</th>
                  <th className="text-center p-3 font-medium">{t('production.status', 'Status')}</th>
                  <th className="text-left p-3 font-medium">{t('production.preparedBy', 'Prepared By')}</th>
                </tr>
              </thead>
              <tbody>
                {batches.map((batch) => (
                  <tr key={batch.id} className="border-b hover:bg-muted/30 cursor-pointer">
                    <td className="p-3 font-medium">{batch.name}</td>
                    <td className="p-3 text-muted-foreground">{batch.productName || '—'}</td>
                    <td className="p-3 text-right">{batch.outputQuantity} {batch.outputUnit}</td>
                    <td className="p-3 text-right">{batch.remainingQuantity} {batch.outputUnit}</td>
                    <td className="p-3 text-right">
                      {batch.costPerUnit != null ? batch.costPerUnit.toLocaleString() : '—'}
                    </td>
                    <td className="p-3 text-center">
                      <Badge variant={STATUS_COLORS[batch.status] || 'secondary'}>
                        {t(`production.statuses.${batch.status}`, batch.status)}
                      </Badge>
                    </td>
                    <td className="p-3 text-muted-foreground">{batch.preparedBy || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </InventoryLayout>
  );
}
