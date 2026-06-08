import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { loyaltyAPI, restaurantAPI, customerAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Textarea } from '../components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import {
  Plus,
  Edit,
  Trash2,
  Wallet,
  Award,
  Search,
  TrendingUp,
} from 'lucide-react';

const GLOBAL_RESTAURANT = '__global__';

const EMPTY_CONFIG = {
  bonusRateType: 'PERCENTAGE',
  bonusRateValue: 5,
  maxBonusPaymentPercentage: 50,
  minOrderAmountForBonus: 0,
  birthdayBonusAmount: 0,
  firstOrderBonusAmount: 0,
  reactivationBonusAmount: 0,
  reactivationDaysThreshold: 30,
  bonusExpiryDays: null,
  enabled: true,
};

const EMPTY_TIER = {
  name: '',
  level: 1,
  minTotalSpend: 0,
  minOrderCount: 0,
  bonusMultiplier: 1,
  benefitsDescription: '',
  color: '#6366f1',
  icon: '',
};

export default function LoyaltySettings() {
  const { t } = useTranslation();

  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(GLOBAL_RESTAURANT);

  // General
  const [config, setConfig] = useState(EMPTY_CONFIG);
  const [configLoading, setConfigLoading] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);

  // Stages
  const [tiers, setTiers] = useState([]);
  const [tierLoading, setTierLoading] = useState(false);
  const [tierModalOpen, setTierModalOpen] = useState(false);
  const [tierForm, setTierForm] = useState(EMPTY_TIER);
  const [editingTierId, setEditingTierId] = useState(null);
  const [tierToDelete, setTierToDelete] = useState(null);

  // Customers
  const [customerQuery, setCustomerQuery] = useState('');
  const [customerResults, setCustomerResults] = useState([]);
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [customerLoyalty, setCustomerLoyalty] = useState(null);
  const [customerTx, setCustomerTx] = useState([]);
  const [customerLoading, setCustomerLoading] = useState(false);

  useEffect(() => {
    loadRestaurants();
    loadTiers();
  }, []);

  useEffect(() => {
    loadConfig();
  }, [selectedRestaurant]);

  const restaurantParam = () =>
    selectedRestaurant === GLOBAL_RESTAURANT ? null : parseInt(selectedRestaurant, 10);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const data = response.data.data?.content || response.data.data || [];
      setRestaurants(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadConfig = async () => {
    try {
      setConfigLoading(true);
      const response = await loyaltyAPI.getConfig(restaurantParam());
      const data = response.data.data;
      setConfig(data ? { ...EMPTY_CONFIG, ...data } : { ...EMPTY_CONFIG });
    } catch (error) {
      console.error('Failed to load loyalty config:', error);
      setConfig({ ...EMPTY_CONFIG });
    } finally {
      setConfigLoading(false);
    }
  };

  const handleSaveConfig = async (e) => {
    e.preventDefault();
    try {
      setConfigSaving(true);
      const payload = {
        restaurantId: restaurantParam(),
        bonusRateType: config.bonusRateType,
        bonusRateValue: parseFloat(config.bonusRateValue) || 0,
        maxBonusPaymentPercentage: parseInt(config.maxBonusPaymentPercentage, 10) || 0,
        minOrderAmountForBonus: parseFloat(config.minOrderAmountForBonus) || 0,
        birthdayBonusAmount: parseFloat(config.birthdayBonusAmount) || 0,
        firstOrderBonusAmount: parseFloat(config.firstOrderBonusAmount) || 0,
        reactivationBonusAmount: parseFloat(config.reactivationBonusAmount) || 0,
        reactivationDaysThreshold: parseInt(config.reactivationDaysThreshold, 10) || 0,
        bonusExpiryDays: config.bonusExpiryDays != null && config.bonusExpiryDays !== ''
          ? parseInt(config.bonusExpiryDays, 10)
          : null,
        enabled: !!config.enabled,
      };
      await loyaltyAPI.upsertConfig(payload);
      alert(t('loyalty.messages.configSaved', 'Loyalty config saved'));
      loadConfig();
    } catch (error) {
      console.error('Failed to save loyalty config:', error);
      alert(t('loyalty.messages.configSaveError', 'Failed to save loyalty config') +
        ': ' + (error.response?.data?.message || error.message));
    } finally {
      setConfigSaving(false);
    }
  };

  const loadTiers = async () => {
    try {
      setTierLoading(true);
      const response = await loyaltyAPI.getTiers();
      const data = response.data.data || [];
      setTiers(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Failed to load tiers:', error);
    } finally {
      setTierLoading(false);
    }
  };

  const openCreateTier = () => {
    setEditingTierId(null);
    setTierForm({ ...EMPTY_TIER, level: (tiers.length || 0) + 1 });
    setTierModalOpen(true);
  };

  const openEditTier = (tier) => {
    setEditingTierId(tier.id);
    setTierForm({
      name: tier.name || '',
      level: tier.level || 1,
      minTotalSpend: tier.minTotalSpend ?? 0,
      minOrderCount: tier.minOrderCount ?? 0,
      bonusMultiplier: tier.bonusMultiplier ?? 1,
      benefitsDescription: tier.benefitsDescription || '',
      color: tier.color || '#6366f1',
      icon: tier.icon || '',
    });
    setTierModalOpen(true);
  };

  const handleSaveTier = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        name: tierForm.name.trim(),
        level: parseInt(tierForm.level, 10),
        minTotalSpend: parseFloat(tierForm.minTotalSpend) || 0,
        minOrderCount: parseInt(tierForm.minOrderCount, 10) || 0,
        bonusMultiplier: parseFloat(tierForm.bonusMultiplier) || 1,
        benefitsDescription: tierForm.benefitsDescription,
        color: tierForm.color,
        icon: tierForm.icon,
      };
      if (!payload.name) {
        alert(t('loyalty.messages.tierNameRequired', 'Tier name is required'));
        return;
      }
      if (editingTierId) {
        await loyaltyAPI.updateTier(editingTierId, payload);
      } else {
        await loyaltyAPI.createTier(payload);
      }
      setTierModalOpen(false);
      setEditingTierId(null);
      loadTiers();
    } catch (error) {
      console.error('Failed to save tier:', error);
      alert(t('loyalty.messages.tierSaveError', 'Failed to save tier') +
        ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleDeleteTier = async () => {
    if (!tierToDelete) return;
    try {
      await loyaltyAPI.deleteTier(tierToDelete.id);
      setTierToDelete(null);
      loadTiers();
    } catch (error) {
      console.error('Failed to delete tier:', error);
      alert(error.response?.data?.message || error.message ||
        t('loyalty.messages.tierDeleteError', 'Failed to delete tier'));
    }
  };

  const handleCustomerSearch = async (e) => {
    e?.preventDefault();
    const q = customerQuery.trim();
    if (!q) {
      setCustomerResults([]);
      return;
    }
    try {
      const response = await customerAPI.suggestByPhone(q);
      const data = response.data.data || [];
      setCustomerResults(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Customer search failed:', error);
      setCustomerResults([]);
    }
  };

  const handlePickCustomer = async (customer) => {
    setSelectedCustomer(customer);
    setCustomerLoyalty(null);
    setCustomerTx([]);
    try {
      setCustomerLoading(true);
      const [loyaltyRes, txRes] = await Promise.all([
        loyaltyAPI.getCustomerLoyalty(customer.id),
        loyaltyAPI.getCustomerTransactions(customer.id, { page: 0, size: 20 }),
      ]);
      setCustomerLoyalty(loyaltyRes.data.data);
      const txPage = txRes.data.data;
      setCustomerTx(txPage?.content || []);
    } catch (error) {
      console.error('Failed to load customer loyalty:', error);
    } finally {
      setCustomerLoading(false);
    }
  };

  // Only admins (and owner via AdminRoute) should reach this page, but keep
  // the restaurant selector visible when the user can manage multiple
  // restaurants — useful for chain operators.
  const showRestaurantPicker = restaurants.length > 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">
          {t('loyalty.title', 'Loyalty & wallet settings')}
        </h1>
        <p className="text-muted-foreground mt-1">
          {t('loyalty.subtitle', 'Configure earn rate, stages, and inspect customer wallets')}
        </p>
      </div>

      <Tabs defaultValue="general" className="space-y-6">
        <TabsList>
          <TabsTrigger value="general">{t('loyalty.tabs.general', 'General')}</TabsTrigger>
          <TabsTrigger value="stages">{t('loyalty.tabs.stages', 'Stages')}</TabsTrigger>
          <TabsTrigger value="customers">{t('loyalty.tabs.customers', 'Customers')}</TabsTrigger>
        </TabsList>

        {/* ============ GENERAL ============ */}
        <TabsContent value="general">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('loyalty.config.title', 'Loyalty configuration')}</CardTitle>
                <CardDescription>
                  {t('loyalty.config.description', 'Controls how bonuses are earned, capped, and granted')}
                </CardDescription>
              </div>
              {showRestaurantPicker && (
                <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
                  <SelectTrigger className="w-[260px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={GLOBAL_RESTAURANT}>
                      {t('loyalty.config.globalConfig', 'Global (all restaurants)')}
                    </SelectItem>
                    {restaurants.map((r) => (
                      <SelectItem key={r.id} value={r.id.toString()}>
                        {r.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </CardHeader>
            <CardContent>
              {configLoading ? (
                <div className="py-12 text-center text-muted-foreground">
                  {t('common.loading', 'Loading...')}
                </div>
              ) : (
                <form onSubmit={handleSaveConfig} className="space-y-6">
                  <div className="flex items-center gap-3">
                    <input
                      id="enabled"
                      type="checkbox"
                      className="h-4 w-4"
                      checked={!!config.enabled}
                      onChange={(e) => setConfig({ ...config, enabled: e.target.checked })}
                    />
                    <Label htmlFor="enabled">
                      {t('loyalty.config.enabled', 'Loyalty program enabled')}
                    </Label>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label>{t('loyalty.config.bonusRateType', 'Bonus rate type')}</Label>
                      <Select
                        value={config.bonusRateType}
                        onValueChange={(v) => setConfig({ ...config, bonusRateType: v })}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="PERCENTAGE">
                            {t('loyalty.config.bonusRateTypes.PERCENTAGE', 'Percentage of order')}
                          </SelectItem>
                          <SelectItem value="FIXED_AMOUNT">
                            {t('loyalty.config.bonusRateTypes.FIXED_AMOUNT', 'Fixed amount per order')}
                          </SelectItem>
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="bonusRateValue">
                        {config.bonusRateType === 'PERCENTAGE'
                          ? t('loyalty.config.bonusRatePercent', 'Bonus rate (% of order)')
                          : t('loyalty.config.bonusRateFixed', 'Bonus per order')}
                      </Label>
                      <Input
                        id="bonusRateValue"
                        type="number"
                        step="0.01"
                        min="0"
                        value={config.bonusRateValue ?? ''}
                        onChange={(e) => setConfig({ ...config, bonusRateValue: e.target.value })}
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="maxBonusPaymentPercentage">
                        {t('loyalty.config.maxBonusPaymentPercentage', 'Max wallet payment (% of order)')}
                      </Label>
                      <Input
                        id="maxBonusPaymentPercentage"
                        type="number"
                        min="0"
                        max="100"
                        value={config.maxBonusPaymentPercentage ?? ''}
                        onChange={(e) => setConfig({ ...config, maxBonusPaymentPercentage: e.target.value })}
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="minOrderAmountForBonus">
                        {t('loyalty.config.minOrderAmountForBonus', 'Minimum order amount for bonus')}
                      </Label>
                      <Input
                        id="minOrderAmountForBonus"
                        type="number"
                        step="0.01"
                        min="0"
                        value={config.minOrderAmountForBonus ?? ''}
                        onChange={(e) => setConfig({ ...config, minOrderAmountForBonus: e.target.value })}
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="firstOrderBonusAmount">
                        {t('loyalty.config.firstOrderBonusAmount', 'First-order bonus')}
                      </Label>
                      <Input
                        id="firstOrderBonusAmount"
                        type="number"
                        step="0.01"
                        min="0"
                        value={config.firstOrderBonusAmount ?? ''}
                        onChange={(e) => setConfig({ ...config, firstOrderBonusAmount: e.target.value })}
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="birthdayBonusAmount">
                        {t('loyalty.config.birthdayBonusAmount', 'Birthday bonus')}
                      </Label>
                      <Input
                        id="birthdayBonusAmount"
                        type="number"
                        step="0.01"
                        min="0"
                        value={config.birthdayBonusAmount ?? ''}
                        onChange={(e) => setConfig({ ...config, birthdayBonusAmount: e.target.value })}
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="reactivationBonusAmount">
                        {t('loyalty.config.reactivationBonusAmount', 'Reactivation bonus')}
                      </Label>
                      <Input
                        id="reactivationBonusAmount"
                        type="number"
                        step="0.01"
                        min="0"
                        value={config.reactivationBonusAmount ?? ''}
                        onChange={(e) => setConfig({ ...config, reactivationBonusAmount: e.target.value })}
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="reactivationDaysThreshold">
                        {t('loyalty.config.reactivationDaysThreshold', 'Reactivation threshold (days)')}
                      </Label>
                      <Input
                        id="reactivationDaysThreshold"
                        type="number"
                        min="0"
                        value={config.reactivationDaysThreshold ?? ''}
                        onChange={(e) => setConfig({ ...config, reactivationDaysThreshold: e.target.value })}
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="bonusExpiryDays">
                        {t('loyalty.config.bonusExpiryDays', 'Bonus expires after (days, blank = never)')}
                      </Label>
                      <Input
                        id="bonusExpiryDays"
                        type="number"
                        min="0"
                        value={config.bonusExpiryDays ?? ''}
                        onChange={(e) => setConfig({ ...config, bonusExpiryDays: e.target.value })}
                      />
                    </div>
                  </div>

                  <div className="flex justify-end">
                    <Button type="submit" disabled={configSaving}>
                      {configSaving
                        ? t('common.saving', 'Saving...')
                        : t('common.save', 'Save')}
                    </Button>
                  </div>
                </form>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ============ STAGES ============ */}
        <TabsContent value="stages">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('loyalty.stages.title', 'Stages')}</CardTitle>
                <CardDescription>
                  {t('loyalty.stages.description',
                    'Customers are promoted to a stage when their total spend AND order count meet its thresholds. Bonus earnings are multiplied by the stage multiplier.')}
                </CardDescription>
              </div>
              <Button onClick={openCreateTier}>
                <Plus className="h-4 w-4 mr-2" />
                {t('loyalty.stages.addStage', 'Add stage')}
              </Button>
            </CardHeader>
            <CardContent>
              {tierLoading ? (
                <div className="py-12 text-center text-muted-foreground">
                  {t('common.loading', 'Loading...')}
                </div>
              ) : tiers.length === 0 ? (
                <div className="text-center py-12">
                  <Award className="mx-auto h-12 w-12 text-muted-foreground" />
                  <h3 className="mt-2 text-sm font-medium">
                    {t('loyalty.stages.empty', 'No stages defined yet')}
                  </h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {t('loyalty.stages.emptyHint', 'Add stages to give frequent customers a higher earn rate.')}
                  </p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('loyalty.stages.cols.level', 'Level')}</TableHead>
                      <TableHead>{t('loyalty.stages.cols.name', 'Name')}</TableHead>
                      <TableHead>{t('loyalty.stages.cols.minSpend', 'Min spend')}</TableHead>
                      <TableHead>{t('loyalty.stages.cols.minOrders', 'Min orders')}</TableHead>
                      <TableHead>{t('loyalty.stages.cols.multiplier', 'Multiplier')}</TableHead>
                      <TableHead>{t('loyalty.stages.cols.customers', 'Customers')}</TableHead>
                      <TableHead>{t('common.actions', 'Actions')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {tiers
                      .slice()
                      .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
                      .map((tier) => {
                        const hasCustomers = (tier.customerCount ?? 0) > 0;
                        return (
                          <TableRow key={tier.id}>
                            <TableCell>
                              <Badge variant="outline">{tier.level}</Badge>
                            </TableCell>
                            <TableCell>
                              <div className="flex items-center gap-2">
                                {tier.color && (
                                  <span
                                    className="inline-block w-3 h-3 rounded-full"
                                    style={{ backgroundColor: tier.color }}
                                  />
                                )}
                                <span className="font-medium">{tier.name}</span>
                              </div>
                            </TableCell>
                            <TableCell>{tier.minTotalSpend ?? 0}</TableCell>
                            <TableCell>{tier.minOrderCount ?? 0}</TableCell>
                            <TableCell>×{tier.bonusMultiplier ?? 1}</TableCell>
                            <TableCell>{tier.customerCount ?? 0}</TableCell>
                            <TableCell>
                              <div className="flex gap-2">
                                <Button size="sm" variant="ghost" onClick={() => openEditTier(tier)}>
                                  <Edit className="h-4 w-4" />
                                </Button>
                                <Button
                                  size="sm"
                                  variant="ghost"
                                  className="text-red-600 hover:text-red-700 disabled:opacity-40"
                                  disabled={hasCustomers}
                                  title={hasCustomers
                                    ? t('loyalty.stages.deleteBlocked',
                                        '{{count}} customer(s) on this stage — reassign first',
                                        { count: tier.customerCount })
                                    : ''}
                                  onClick={() => setTierToDelete(tier)}
                                >
                                  <Trash2 className="h-4 w-4" />
                                </Button>
                              </div>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ============ CUSTOMERS ============ */}
        <TabsContent value="customers">
          <Card>
            <CardHeader>
              <CardTitle>{t('loyalty.customers.title', 'Customer wallets')}</CardTitle>
              <CardDescription>
                {t('loyalty.customers.description',
                  'Look up a customer by phone to see their wallet balance, current stage, and bonus transaction history.')}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <form onSubmit={handleCustomerSearch} className="flex gap-2">
                <Input
                  placeholder={t('loyalty.customers.searchPlaceholder', 'Phone number…')}
                  value={customerQuery}
                  onChange={(e) => setCustomerQuery(e.target.value)}
                />
                <Button type="submit" variant="outline">
                  <Search className="h-4 w-4 mr-2" />
                  {t('common.search', 'Search')}
                </Button>
              </form>

              {customerResults.length > 0 && !selectedCustomer && (
                <div className="border rounded-md divide-y">
                  {customerResults.map((c) => (
                    <button
                      key={c.id}
                      type="button"
                      className="w-full text-left px-3 py-2 hover:bg-muted transition"
                      onClick={() => handlePickCustomer(c)}
                    >
                      <div className="font-medium">{c.firstName} {c.lastName}</div>
                      <div className="text-sm text-muted-foreground">{c.phone}</div>
                    </button>
                  ))}
                </div>
              )}

              {selectedCustomer && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="text-lg font-medium">
                        {selectedCustomer.firstName} {selectedCustomer.lastName}
                      </div>
                      <div className="text-sm text-muted-foreground">{selectedCustomer.phone}</div>
                    </div>
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setSelectedCustomer(null);
                        setCustomerLoyalty(null);
                        setCustomerTx([]);
                      }}
                    >
                      {t('loyalty.customers.clearSelection', 'Clear')}
                    </Button>
                  </div>

                  {customerLoading ? (
                    <div className="text-muted-foreground">{t('common.loading', 'Loading...')}</div>
                  ) : customerLoyalty ? (
                    <>
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        <StatTile
                          icon={Wallet}
                          label={t('loyalty.customers.balance', 'Wallet balance')}
                          value={customerLoyalty.currentBalance ?? 0}
                        />
                        <StatTile
                          icon={TrendingUp}
                          label={t('loyalty.customers.lifetimeEarned', 'Lifetime earned')}
                          value={customerLoyalty.lifetimeEarned ?? 0}
                        />
                        <StatTile
                          icon={TrendingUp}
                          label={t('loyalty.customers.lifetimeSpent', 'Lifetime spent (bonus)')}
                          value={customerLoyalty.lifetimeSpent ?? 0}
                        />
                        <StatTile
                          icon={Award}
                          label={t('loyalty.customers.tier', 'Stage')}
                          value={customerLoyalty.tier?.name || t('loyalty.customers.noTier', 'No stage')}
                          isText
                        />
                      </div>

                      <div>
                        <h3 className="font-medium mb-2">
                          {t('loyalty.customers.ledger', 'Bonus transactions')}
                        </h3>
                        {customerTx.length === 0 ? (
                          <div className="text-sm text-muted-foreground">
                            {t('loyalty.customers.noTransactions', 'No bonus transactions yet')}
                          </div>
                        ) : (
                          <Table>
                            <TableHeader>
                              <TableRow>
                                <TableHead>{t('loyalty.customers.txCols.date', 'Date')}</TableHead>
                                <TableHead>{t('loyalty.customers.txCols.type', 'Type')}</TableHead>
                                <TableHead>{t('loyalty.customers.txCols.amount', 'Amount')}</TableHead>
                                <TableHead>{t('loyalty.customers.txCols.balanceAfter', 'Balance after')}</TableHead>
                                <TableHead>{t('loyalty.customers.txCols.order', 'Order')}</TableHead>
                                <TableHead>{t('loyalty.customers.txCols.description', 'Description')}</TableHead>
                              </TableRow>
                            </TableHeader>
                            <TableBody>
                              {customerTx.map((tx) => (
                                <TableRow key={tx.id}>
                                  <TableCell className="whitespace-nowrap">
                                    {tx.createdAt
                                      ? new Date(tx.createdAt).toLocaleString()
                                      : '-'}
                                  </TableCell>
                                  <TableCell>
                                    <Badge variant="outline">{tx.transactionType}</Badge>
                                  </TableCell>
                                  <TableCell>{tx.amount}</TableCell>
                                  <TableCell>{tx.balanceAfter}</TableCell>
                                  <TableCell>
                                    {tx.orderNumber || (tx.orderId ? `#${tx.orderId}` : '-')}
                                  </TableCell>
                                  <TableCell className="max-w-md truncate">{tx.description}</TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        )}
                      </div>
                    </>
                  ) : (
                    <div className="text-muted-foreground">
                      {t('loyalty.customers.noLoyaltyRecord', 'This customer has no loyalty record yet.')}
                    </div>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Create / Edit Stage modal */}
      <Dialog open={tierModalOpen} onOpenChange={setTierModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              {editingTierId
                ? t('loyalty.stages.editTier', 'Edit stage')
                : t('loyalty.stages.addStage', 'Add stage')}
            </DialogTitle>
            <DialogDescription>
              {t('loyalty.stages.formDescription',
                'A customer becomes eligible for this stage once both thresholds are met.')}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSaveTier}>
            <div className="space-y-4 py-2">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>{t('loyalty.stages.cols.name', 'Name')} *</Label>
                  <Input
                    value={tierForm.name}
                    onChange={(e) => setTierForm({ ...tierForm, name: e.target.value })}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label>{t('loyalty.stages.cols.level', 'Level')} *</Label>
                  <Input
                    type="number"
                    min="1"
                    value={tierForm.level}
                    onChange={(e) => setTierForm({ ...tierForm, level: e.target.value })}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label>{t('loyalty.stages.cols.minSpend', 'Min spend')}</Label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={tierForm.minTotalSpend}
                    onChange={(e) => setTierForm({ ...tierForm, minTotalSpend: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label>{t('loyalty.stages.cols.minOrders', 'Min orders')}</Label>
                  <Input
                    type="number"
                    min="0"
                    value={tierForm.minOrderCount}
                    onChange={(e) => setTierForm({ ...tierForm, minOrderCount: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label>{t('loyalty.stages.cols.multiplier', 'Bonus multiplier')}</Label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={tierForm.bonusMultiplier}
                    onChange={(e) => setTierForm({ ...tierForm, bonusMultiplier: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label>{t('loyalty.stages.color', 'Color')}</Label>
                  <Input
                    type="color"
                    value={tierForm.color || '#6366f1'}
                    onChange={(e) => setTierForm({ ...tierForm, color: e.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label>{t('loyalty.stages.benefits', 'Benefits description')}</Label>
                <Textarea
                  rows={3}
                  value={tierForm.benefitsDescription}
                  onChange={(e) => setTierForm({ ...tierForm, benefitsDescription: e.target.value })}
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setTierModalOpen(false)}>
                {t('common.cancel', 'Cancel')}
              </Button>
              <Button type="submit">
                {editingTierId ? t('common.save', 'Save') : t('common.create', 'Create')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <Dialog open={!!tierToDelete} onOpenChange={(open) => !open && setTierToDelete(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('loyalty.stages.deleteTitle', 'Delete stage')}</DialogTitle>
            <DialogDescription>
              {t('loyalty.stages.deleteConfirm',
                'Delete stage "{{name}}"? This cannot be undone.',
                { name: tierToDelete?.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setTierToDelete(null)}>
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDeleteTier}>
              {t('common.delete', 'Delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function StatTile({ icon: Icon, label, value, isText }) {
  return (
    <div className="border rounded-lg p-4 flex items-center gap-3">
      <div className="p-2 bg-primary/10 rounded-lg">
        <Icon className="w-5 h-5 text-primary" />
      </div>
      <div>
        <div className="text-xs text-muted-foreground">{label}</div>
        <div className={isText ? 'font-medium' : 'text-lg font-semibold'}>{value}</div>
      </div>
    </div>
  );
}
