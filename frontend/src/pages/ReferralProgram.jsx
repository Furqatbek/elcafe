import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { referralAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
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
  DialogFooter
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
  Users,
  Gift,
  Share2,
  Settings,
  ToggleLeft,
  ToggleRight,
  ChevronLeft,
  ChevronRight,
  Copy,
  Check,
  TrendingUp,
  UserPlus,
  Award
} from 'lucide-react';

export default function ReferralProgram() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('settings');

  // Settings state
  const [settings, setSettings] = useState(null);
  const [settingsModalOpen, setSettingsModalOpen] = useState(false);
  const [settingsForm, setSettingsForm] = useState({
    programActive: false,
    referrerRewardType: 'BONUS_POINTS',
    referrerRewardAmount: '100',
    refereeRewardType: 'BONUS_POINTS',
    refereeRewardAmount: '50',
    minOrderAmount: '',
    maxReferralsPerCustomer: '',
    rewardExpiresDays: '30',
    termsAndConditions: '',
  });

  // Referrals state
  const [referrals, setReferrals] = useState([]);
  const [referralPage, setReferralPage] = useState(0);
  const [referralTotalPages, setReferralTotalPages] = useState(0);

  // Codes state
  const [codes, setCodes] = useState([]);
  const [codePage, setCodePage] = useState(0);
  const [codeTotalPages, setCodeTotalPages] = useState(0);

  // Stats state
  const [stats, setStats] = useState(null);

  // Copy feedback
  const [copiedCode, setCopiedCode] = useState(null);

  const rewardTypes = [
    { value: 'BONUS_POINTS', label: t('referral.rewardTypes.bonusPoints') },
    { value: 'DISCOUNT_AMOUNT', label: t('referral.rewardTypes.discountAmount') },
    { value: 'DISCOUNT_PERCENT', label: t('referral.rewardTypes.discountPercent') },
    { value: 'FREE_ITEM', label: t('referral.rewardTypes.freeItem') },
  ];

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (user?.restaurantId && !selectedRestaurant) {
      setSelectedRestaurant(user.restaurantId.toString());
    }
  }, [user]);

  useEffect(() => {
    if (selectedRestaurant) {
      loadSettings();
      loadStats();
    }
  }, [selectedRestaurant]);

  useEffect(() => {
    if (selectedRestaurant && activeTab === 'referrals') {
      loadReferrals();
    }
  }, [selectedRestaurant, activeTab, referralPage]);

  useEffect(() => {
    if (selectedRestaurant && activeTab === 'codes') {
      loadCodes();
    }
  }, [selectedRestaurant, activeTab, codePage]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      setRestaurants(response.data?.data?.content || []);
    } catch (error) {
      console.error('Error loading restaurants:', error);
    }
  };

  const loadSettings = async () => {
    setLoading(true);
    try {
      const response = await referralAPI.getSettings(selectedRestaurant);
      const data = response.data?.data;
      setSettings(data);
      if (data) {
        setSettingsForm({
          programActive: data.programActive || false,
          referrerRewardType: data.referrerRewardType || 'BONUS_POINTS',
          referrerRewardAmount: data.referrerRewardAmount?.toString() || '100',
          refereeRewardType: data.refereeRewardType || 'BONUS_POINTS',
          refereeRewardAmount: data.refereeRewardAmount?.toString() || '50',
          minOrderAmount: data.minOrderAmount?.toString() || '',
          maxReferralsPerCustomer: data.maxReferralsPerCustomer?.toString() || '',
          rewardExpiresDays: data.rewardExpiresDays?.toString() || '30',
          termsAndConditions: data.termsAndConditions || '',
        });
      }
    } catch (error) {
      console.error('Error loading settings:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadReferrals = async () => {
    setLoading(true);
    try {
      const response = await referralAPI.getReferrals(selectedRestaurant, { page: referralPage, size: 10 });
      setReferrals(response.data?.data?.content || []);
      setReferralTotalPages(response.data?.data?.totalPages || 0);
    } catch (error) {
      console.error('Error loading referrals:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadCodes = async () => {
    setLoading(true);
    try {
      const response = await referralAPI.getCodes(selectedRestaurant, { page: codePage, size: 10 });
      setCodes(response.data?.data?.content || []);
      setCodeTotalPages(response.data?.data?.totalPages || 0);
    } catch (error) {
      console.error('Error loading codes:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadStats = async () => {
    try {
      const response = await referralAPI.getStats(selectedRestaurant);
      setStats(response.data?.data);
    } catch (error) {
      console.error('Error loading stats:', error);
    }
  };

  const handleSaveSettings = async () => {
    setLoading(true);
    try {
      const data = {
        programActive: settingsForm.programActive,
        referrerRewardType: settingsForm.referrerRewardType,
        referrerRewardAmount: parseFloat(settingsForm.referrerRewardAmount),
        refereeRewardType: settingsForm.refereeRewardType,
        refereeRewardAmount: parseFloat(settingsForm.refereeRewardAmount),
        minOrderAmount: settingsForm.minOrderAmount ? parseFloat(settingsForm.minOrderAmount) : null,
        maxReferralsPerCustomer: settingsForm.maxReferralsPerCustomer ? parseInt(settingsForm.maxReferralsPerCustomer) : null,
        rewardExpiresDays: parseInt(settingsForm.rewardExpiresDays) || 30,
        termsAndConditions: settingsForm.termsAndConditions,
      };
      await referralAPI.saveSettings(selectedRestaurant, data);
      setSettingsModalOpen(false);
      loadSettings();
      loadStats();
    } catch (error) {
      console.error('Error saving settings:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleProgram = async () => {
    setLoading(true);
    try {
      await referralAPI.toggleProgram(selectedRestaurant);
      loadSettings();
      loadStats();
    } catch (error) {
      console.error('Error toggling program:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCopyCode = (code) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString();
  };

  const getStatusBadge = (status) => {
    const statusColors = {
      PENDING: 'bg-yellow-100 text-yellow-800',
      COMPLETED: 'bg-green-100 text-green-800',
      EXPIRED: 'bg-gray-100 text-gray-800',
      CANCELLED: 'bg-red-100 text-red-800',
    };
    return (
      <Badge className={statusColors[status] || 'bg-gray-100 text-gray-800'}>
        {t(`referral.status.${status?.toLowerCase()}`) || status}
      </Badge>
    );
  };

  const getRewardTypeLabel = (type) => {
    return t(`referral.rewardTypes.${type?.toLowerCase().replace('_', '')}`) || type;
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold">{t('referral.title')}</h1>
          <p className="text-muted-foreground">{t('referral.subtitle')}</p>
        </div>
        <Select
          value={selectedRestaurant || ''}
          onValueChange={setSelectedRestaurant}
        >
          <SelectTrigger className="w-[250px]">
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

      {selectedRestaurant && (
        <>
          {/* Stats Cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <Card>
              <CardContent className="pt-6">
                <div className="flex items-center gap-4">
                  <div className="p-3 bg-blue-100 rounded-lg">
                    <Users className="h-6 w-6 text-blue-600" />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">{t('referral.stats.totalReferrals')}</p>
                    <p className="text-2xl font-bold">{stats?.totalReferrals || 0}</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="pt-6">
                <div className="flex items-center gap-4">
                  <div className="p-3 bg-green-100 rounded-lg">
                    <Check className="h-6 w-6 text-green-600" />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">{t('referral.stats.completed')}</p>
                    <p className="text-2xl font-bold">{stats?.completedReferrals || 0}</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="pt-6">
                <div className="flex items-center gap-4">
                  <div className="p-3 bg-purple-100 rounded-lg">
                    <Share2 className="h-6 w-6 text-purple-600" />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">{t('referral.stats.activeCodes')}</p>
                    <p className="text-2xl font-bold">{stats?.activeReferralCodes || 0}</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="pt-6">
                <div className="flex items-center gap-4">
                  <div className="p-3 bg-orange-100 rounded-lg">
                    <TrendingUp className="h-6 w-6 text-orange-600" />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">{t('referral.stats.thisMonth')}</p>
                    <p className="text-2xl font-bold">{stats?.referralsThisMonth || 0}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Tabs */}
          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList>
              <TabsTrigger value="settings" className="flex items-center gap-2">
                <Settings className="h-4 w-4" />
                {t('referral.tabs.settings')}
              </TabsTrigger>
              <TabsTrigger value="referrals" className="flex items-center gap-2">
                <UserPlus className="h-4 w-4" />
                {t('referral.tabs.referrals')}
              </TabsTrigger>
              <TabsTrigger value="codes" className="flex items-center gap-2">
                <Share2 className="h-4 w-4" />
                {t('referral.tabs.codes')}
              </TabsTrigger>
            </TabsList>

            {/* Settings Tab */}
            <TabsContent value="settings">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                  <div>
                    <CardTitle>{t('referral.settingsCard.title')}</CardTitle>
                    <CardDescription>{t('referral.settingsCard.description')}</CardDescription>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant={settings?.programActive ? "default" : "outline"}
                      onClick={handleToggleProgram}
                      disabled={loading || !settings}
                    >
                      {settings?.programActive ? (
                        <>
                          <ToggleRight className="h-4 w-4 mr-2" />
                          {t('referral.programActive')}
                        </>
                      ) : (
                        <>
                          <ToggleLeft className="h-4 w-4 mr-2" />
                          {t('referral.programInactive')}
                        </>
                      )}
                    </Button>
                    <Button onClick={() => setSettingsModalOpen(true)}>
                      {t('referral.configureSettings')}
                    </Button>
                  </div>
                </CardHeader>
                <CardContent>
                  {settings ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      <div className="space-y-4">
                        <h3 className="font-semibold flex items-center gap-2">
                          <Gift className="h-4 w-4" />
                          {t('referral.referrerReward')}
                        </h3>
                        <div className="pl-6 space-y-2">
                          <p><span className="text-muted-foreground">{t('referral.type')}:</span> {getRewardTypeLabel(settings.referrerRewardType)}</p>
                          <p><span className="text-muted-foreground">{t('referral.amount')}:</span> {settings.referrerRewardAmount}</p>
                        </div>
                      </div>

                      <div className="space-y-4">
                        <h3 className="font-semibold flex items-center gap-2">
                          <Award className="h-4 w-4" />
                          {t('referral.refereeReward')}
                        </h3>
                        <div className="pl-6 space-y-2">
                          <p><span className="text-muted-foreground">{t('referral.type')}:</span> {getRewardTypeLabel(settings.refereeRewardType)}</p>
                          <p><span className="text-muted-foreground">{t('referral.amount')}:</span> {settings.refereeRewardAmount}</p>
                        </div>
                      </div>

                      <div className="space-y-2">
                        <p><span className="text-muted-foreground">{t('referral.minOrderAmount')}:</span> {settings.minOrderAmount || t('common.none')}</p>
                        <p><span className="text-muted-foreground">{t('referral.maxReferrals')}:</span> {settings.maxReferralsPerCustomer || t('common.unlimited')}</p>
                        <p><span className="text-muted-foreground">{t('referral.rewardExpiry')}:</span> {settings.rewardExpiresDays} {t('common.days')}</p>
                      </div>

                      {stats?.topReferrerName && (
                        <div className="space-y-2">
                          <h3 className="font-semibold">{t('referral.topReferrer')}</h3>
                          <p>{stats.topReferrerName} ({stats.topReferrerCount} {t('referral.referrals')})</p>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="text-center py-8">
                      <p className="text-muted-foreground mb-4">{t('referral.noSettingsYet')}</p>
                      <Button onClick={() => setSettingsModalOpen(true)}>
                        {t('referral.setupProgram')}
                      </Button>
                    </div>
                  )}
                </CardContent>
              </Card>
            </TabsContent>

            {/* Referrals Tab */}
            <TabsContent value="referrals">
              <Card>
                <CardHeader>
                  <CardTitle>{t('referral.referralsCard.title')}</CardTitle>
                  <CardDescription>{t('referral.referralsCard.description')}</CardDescription>
                </CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('referral.table.referrer')}</TableHead>
                        <TableHead>{t('referral.table.referee')}</TableHead>
                        <TableHead>{t('referral.table.code')}</TableHead>
                        <TableHead>{t('referral.table.status')}</TableHead>
                        <TableHead>{t('referral.table.referrerReward')}</TableHead>
                        <TableHead>{t('referral.table.refereeReward')}</TableHead>
                        <TableHead>{t('referral.table.createdAt')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {referrals.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                            {t('referral.noReferrals')}
                          </TableCell>
                        </TableRow>
                      ) : (
                        referrals.map((referral) => (
                          <TableRow key={referral.id}>
                            <TableCell>
                              <div>
                                <p className="font-medium">{referral.referrerName}</p>
                                <p className="text-sm text-muted-foreground">{referral.referrerEmail}</p>
                              </div>
                            </TableCell>
                            <TableCell>
                              <div>
                                <p className="font-medium">{referral.refereeName}</p>
                                <p className="text-sm text-muted-foreground">{referral.refereeEmail}</p>
                              </div>
                            </TableCell>
                            <TableCell>
                              <code className="bg-muted px-2 py-1 rounded text-sm">{referral.referralCode}</code>
                            </TableCell>
                            <TableCell>{getStatusBadge(referral.status)}</TableCell>
                            <TableCell>
                              {referral.referrerRewardGiven ? (
                                <Badge variant="secondary">
                                  {referral.referrerRewardAmount} {getRewardTypeLabel(referral.referrerRewardType)}
                                </Badge>
                              ) : (
                                <span className="text-muted-foreground">{t('referral.pending')}</span>
                              )}
                            </TableCell>
                            <TableCell>
                              {referral.refereeRewardGiven ? (
                                <Badge variant="secondary">
                                  {referral.refereeRewardAmount} {getRewardTypeLabel(referral.refereeRewardType)}
                                </Badge>
                              ) : (
                                <span className="text-muted-foreground">{t('referral.pending')}</span>
                              )}
                            </TableCell>
                            <TableCell>{formatDate(referral.createdAt)}</TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>

                  {referralTotalPages > 1 && (
                    <div className="flex items-center justify-between mt-4">
                      <p className="text-sm text-muted-foreground">
                        {t('common.page')} {referralPage + 1} {t('common.of')} {referralTotalPages}
                      </p>
                      <div className="flex gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setReferralPage(p => Math.max(0, p - 1))}
                          disabled={referralPage === 0}
                        >
                          <ChevronLeft className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setReferralPage(p => p + 1)}
                          disabled={referralPage >= referralTotalPages - 1}
                        >
                          <ChevronRight className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            </TabsContent>

            {/* Codes Tab */}
            <TabsContent value="codes">
              <Card>
                <CardHeader>
                  <CardTitle>{t('referral.codesCard.title')}</CardTitle>
                  <CardDescription>{t('referral.codesCard.description')}</CardDescription>
                </CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('referral.table.customer')}</TableHead>
                        <TableHead>{t('referral.table.code')}</TableHead>
                        <TableHead>{t('referral.table.usageCount')}</TableHead>
                        <TableHead>{t('referral.table.status')}</TableHead>
                        <TableHead>{t('referral.table.createdAt')}</TableHead>
                        <TableHead>{t('referral.table.actions')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {codes.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                            {t('referral.noCodes')}
                          </TableCell>
                        </TableRow>
                      ) : (
                        codes.map((code) => (
                          <TableRow key={code.id}>
                            <TableCell>
                              <div>
                                <p className="font-medium">{code.customerName}</p>
                                <p className="text-sm text-muted-foreground">{code.customerEmail}</p>
                              </div>
                            </TableCell>
                            <TableCell>
                              <code className="bg-muted px-2 py-1 rounded text-sm font-mono">{code.code}</code>
                            </TableCell>
                            <TableCell>
                              {code.usageCount}
                              {code.maxUses && ` / ${code.maxUses}`}
                            </TableCell>
                            <TableCell>
                              <Badge variant={code.isValid ? "default" : "secondary"}>
                                {code.isValid ? t('referral.active') : t('referral.inactive')}
                              </Badge>
                            </TableCell>
                            <TableCell>{formatDate(code.createdAt)}</TableCell>
                            <TableCell>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleCopyCode(code.code)}
                              >
                                {copiedCode === code.code ? (
                                  <Check className="h-4 w-4 text-green-600" />
                                ) : (
                                  <Copy className="h-4 w-4" />
                                )}
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>

                  {codeTotalPages > 1 && (
                    <div className="flex items-center justify-between mt-4">
                      <p className="text-sm text-muted-foreground">
                        {t('common.page')} {codePage + 1} {t('common.of')} {codeTotalPages}
                      </p>
                      <div className="flex gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCodePage(p => Math.max(0, p - 1))}
                          disabled={codePage === 0}
                        >
                          <ChevronLeft className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCodePage(p => p + 1)}
                          disabled={codePage >= codeTotalPages - 1}
                        >
                          <ChevronRight className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </>
      )}

      {/* Settings Modal */}
      <Dialog open={settingsModalOpen} onOpenChange={setSettingsModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('referral.settingsModal.title')}</DialogTitle>
            <DialogDescription>{t('referral.settingsModal.description')}</DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('referral.referrerRewardType')}</Label>
                <Select
                  value={settingsForm.referrerRewardType}
                  onValueChange={(value) => setSettingsForm({ ...settingsForm, referrerRewardType: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {rewardTypes.map((type) => (
                      <SelectItem key={type.value} value={type.value}>
                        {type.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>{t('referral.referrerRewardAmount')}</Label>
                <Input
                  type="number"
                  value={settingsForm.referrerRewardAmount}
                  onChange={(e) => setSettingsForm({ ...settingsForm, referrerRewardAmount: e.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('referral.refereeRewardType')}</Label>
                <Select
                  value={settingsForm.refereeRewardType}
                  onValueChange={(value) => setSettingsForm({ ...settingsForm, refereeRewardType: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {rewardTypes.map((type) => (
                      <SelectItem key={type.value} value={type.value}>
                        {type.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>{t('referral.refereeRewardAmount')}</Label>
                <Input
                  type="number"
                  value={settingsForm.refereeRewardAmount}
                  onChange={(e) => setSettingsForm({ ...settingsForm, refereeRewardAmount: e.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('referral.minOrderAmount')}</Label>
                <Input
                  type="number"
                  placeholder={t('referral.optional')}
                  value={settingsForm.minOrderAmount}
                  onChange={(e) => setSettingsForm({ ...settingsForm, minOrderAmount: e.target.value })}
                />
              </div>

              <div className="space-y-2">
                <Label>{t('referral.maxReferralsPerCustomer')}</Label>
                <Input
                  type="number"
                  placeholder={t('referral.unlimited')}
                  value={settingsForm.maxReferralsPerCustomer}
                  onChange={(e) => setSettingsForm({ ...settingsForm, maxReferralsPerCustomer: e.target.value })}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label>{t('referral.rewardExpiresDays')}</Label>
              <Input
                type="number"
                value={settingsForm.rewardExpiresDays}
                onChange={(e) => setSettingsForm({ ...settingsForm, rewardExpiresDays: e.target.value })}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('referral.termsAndConditions')}</Label>
              <Textarea
                placeholder={t('referral.termsPlaceholder')}
                value={settingsForm.termsAndConditions}
                onChange={(e) => setSettingsForm({ ...settingsForm, termsAndConditions: e.target.value })}
                rows={4}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setSettingsModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveSettings} disabled={loading}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
