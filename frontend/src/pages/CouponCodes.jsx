import { useState, useEffect } from 'react';
import { notifyError, notifySuccess, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { useSearchParams, Link } from 'react-router-dom';
import { promotionAPI, restaurantAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
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
import {
  Plus,
  Trash2,
  Copy,
  ToggleLeft,
  ToggleRight,
  Tag,
  Ticket,
  Download,
  RefreshCw,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';

export default function CouponCodes() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [searchParams] = useSearchParams();
  const [coupons, setCoupons] = useState([]);
  const [promotions, setPromotions] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [selectedPromotion, setSelectedPromotion] = useState(searchParams.get('promotionId') || '');
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [generateModalOpen, setGenerateModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedCoupon, setSelectedCoupon] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formData, setFormData] = useState({
    code: '',
    promotionId: '',
    singleUse: false,
    maxUses: null,
    validFrom: '',
    validUntil: '',
    active: true,
  });

  const [generateData, setGenerateData] = useState({
    promotionId: '',
    count: 10,
    prefix: '',
    codeLength: 8,
    singleUse: true,
    maxUses: null,
    validFrom: '',
    validUntil: '',
  });

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
      loadPromotions();
    }
  }, [selectedRestaurant]);

  useEffect(() => {
    if (selectedRestaurant) {
      loadCoupons();
    }
  }, [selectedRestaurant, selectedPromotion, currentPage]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantsData = response.data.data?.content || response.data.data || [];
      setRestaurants(Array.isArray(restaurantsData) ? restaurantsData : []);

      if (!selectedRestaurant && restaurantsData.length > 0 && !user?.restaurantId) {
        setSelectedRestaurant(restaurantsData[0].id.toString());
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadPromotions = async () => {
    try {
      const response = await promotionAPI.getPromotions(parseInt(selectedRestaurant), { page: 0, size: 100 });
      const data = response.data.data || response.data;
      setPromotions(data.content || []);
    } catch (error) {
      console.error('Failed to load promotions:', error);
    }
  };

  const loadCoupons = async () => {
    try {
      setLoading(true);
      let response;
      if (selectedPromotion) {
        response = await promotionAPI.getCouponsByPromotion(selectedPromotion, { page: currentPage, size: 20 });
      } else {
        response = await promotionAPI.getCoupons(parseInt(selectedRestaurant), { page: currentPage, size: 20 });
      }
      const data = response.data.data || response.data;
      setCoupons(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (error) {
      console.error('Failed to load coupons:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      if (!formData.code || !formData.promotionId) {
        notifyWarning(t('coupons.messages.fillRequired'));
        return;
      }

      setLoading(true);
      const payload = {
        ...formData,
        validFrom: formData.validFrom ? new Date(formData.validFrom).toISOString() : null,
        validUntil: formData.validUntil ? new Date(formData.validUntil).toISOString() : null,
      };

      await promotionAPI.createCoupon(payload);
      setCreateModalOpen(false);
      resetForm();
      loadCoupons();
    } catch (error) {
      console.error('Failed to create coupon:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async (e) => {
    e.preventDefault();
    try {
      if (!generateData.promotionId || generateData.count < 1) {
        notifyWarning(t('coupons.messages.fillRequired'));
        return;
      }

      setLoading(true);
      const payload = {
        ...generateData,
        validFrom: generateData.validFrom ? new Date(generateData.validFrom).toISOString() : null,
        validUntil: generateData.validUntil ? new Date(generateData.validUntil).toISOString() : null,
      };

      const response = await promotionAPI.generateCoupons(payload);
      const generated = response.data.data || response.data;
      notifySuccess(t('coupons.messages.generateSuccess', { count: generated.length }));
      setGenerateModalOpen(false);
      resetGenerateForm();
      loadCoupons();
    } catch (error) {
      console.error('Failed to generate coupons:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedCoupon) return;
    try {
      await promotionAPI.deleteCoupon(selectedCoupon.id);
      setDeleteDialogOpen(false);
      setSelectedCoupon(null);
      loadCoupons();
    } catch (error) {
      console.error('Failed to delete coupon:', error);
      notifyWarning(t('coupons.messages.deleteError'));
    }
  };

  const handleToggle = async (id) => {
    try {
      await promotionAPI.toggleCoupon(id);
      loadCoupons();
    } catch (error) {
      console.error('Failed to toggle coupon:', error);
    }
  };

  const copyToClipboard = (code) => {
    navigator.clipboard.writeText(code);
    notifySuccess(t('coupons.messages.copied'));
  };

  const exportCoupons = () => {
    const headers = [
      t('coupons.export.code'),
      t('coupons.export.promotion'),
      t('coupons.export.singleUse'),
      t('coupons.export.maxUses'),
      t('coupons.export.usedCount'),
      t('coupons.export.validFrom'),
      t('coupons.export.validUntil'),
      t('coupons.export.active')
    ];

    const csvContent = [
      headers.join(','),
      ...coupons.map(c => [
        c.code,
        c.promotionName,
        c.singleUse ? t('common.yes') : t('common.no'),
        c.maxUses || t('coupons.unlimited'),
        c.usedCount,
        c.validFrom || '-',
        c.validUntil || '-',
        c.active ? t('common.yes') : t('common.no')
      ].join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `coupons-${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
  };

  const resetForm = () => {
    setFormData({
      code: '',
      promotionId: selectedPromotion || '',
      singleUse: false,
      maxUses: null,
      validFrom: '',
      validUntil: '',
      active: true,
    });
  };

  const resetGenerateForm = () => {
    setGenerateData({
      promotionId: selectedPromotion || '',
      count: 10,
      prefix: '',
      codeLength: 8,
      singleUse: true,
      maxUses: null,
      validFrom: '',
      validUntil: '',
    });
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString();
  };

  if (loading && coupons.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('coupons.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('coupons.subtitle')}</p>
        </div>
        <div className="flex gap-3">
          <Link to="/marketing/promotions">
            <Button variant="outline">
              <Tag className="h-4 w-4 mr-2" />
              {t('coupons.managePromotions')}
            </Button>
          </Link>
          <Button
            variant="outline"
            onClick={exportCoupons}
            disabled={coupons.length === 0}
          >
            <Download className="h-4 w-4 mr-2" />
            {t('coupons.export.button')}
          </Button>
          <Button
            variant="secondary"
            onClick={() => { resetGenerateForm(); setGenerateModalOpen(true); }}
          >
            <RefreshCw className="h-4 w-4 mr-2" />
            {t('coupons.generateBatch')}
          </Button>
          <Button onClick={() => { resetForm(); setCreateModalOpen(true); }}>
            <Plus className="h-4 w-4 mr-2" />
            {t('coupons.addCoupon')}
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4">
        {restaurants.length > 1 && (
          <Select
            value={selectedRestaurant}
            onValueChange={(value) => {
              setSelectedRestaurant(value);
              setSelectedPromotion('');
              setCurrentPage(0);
            }}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('common.selectRestaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map(r => (
                <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}

        <Select
          value={selectedPromotion || 'all'}
          onValueChange={(value) => {
            setSelectedPromotion(value === 'all' ? '' : value);
            setCurrentPage(0);
          }}
        >
          <SelectTrigger className="w-[250px]">
            <SelectValue placeholder={t('coupons.allPromotions')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('coupons.allPromotions')}</SelectItem>
            {promotions.map(p => (
              <SelectItem key={p.id} value={p.id.toString()}>{p.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Coupons Table */}
      <Card>
        <CardHeader>
          <CardTitle>{t('coupons.listTitle')}</CardTitle>
          <CardDescription>{t('coupons.listDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {coupons.length === 0 ? (
            <div className="text-center py-12">
              <Ticket className="mx-auto h-12 w-12 text-muted-foreground" />
              <h3 className="mt-2 text-sm font-medium">{t('coupons.noCoupons')}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{t('coupons.getStarted')}</p>
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('coupons.table.code')}</TableHead>
                    <TableHead>{t('coupons.table.promotion')}</TableHead>
                    <TableHead>{t('coupons.table.usage')}</TableHead>
                    <TableHead>{t('coupons.table.validity')}</TableHead>
                    <TableHead>{t('coupons.table.status')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {coupons.map(coupon => (
                    <TableRow key={coupon.id}>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <code className="bg-muted px-2 py-1 rounded text-sm font-mono">{coupon.code}</code>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => copyToClipboard(coupon.code)}
                          >
                            <Copy className="h-3 w-3" />
                          </Button>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="font-medium">{coupon.promotionName}</div>
                      </TableCell>
                      <TableCell>
                        <div className="text-sm">
                          <div>{coupon.usedCount} / {coupon.singleUse ? '1' : (coupon.maxUses || '∞')}</div>
                          <div className="text-muted-foreground text-xs">
                            {coupon.singleUse ? t('coupons.singleUse') : t('coupons.multiUse')}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="text-sm">
                          {coupon.validFrom || coupon.validUntil ? (
                            <div>
                              {formatDate(coupon.validFrom)} - {formatDate(coupon.validUntil)}
                            </div>
                          ) : (
                            <span className="text-muted-foreground">{t('coupons.noExpiry')}</span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={coupon.active && coupon.currentlyValid ? 'default' : 'secondary'}
                          className="cursor-pointer"
                          onClick={() => handleToggle(coupon.id)}
                        >
                          {coupon.active && coupon.currentlyValid ? (
                            <><ToggleRight className="h-3 w-3 mr-1" /> {t('common.active')}</>
                          ) : (
                            <><ToggleLeft className="h-3 w-3 mr-1" /> {t('common.inactive')}</>
                          )}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => copyToClipboard(coupon.code)}
                          >
                            <Copy className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600 hover:text-red-700"
                            onClick={() => {
                              setSelectedCoupon(coupon);
                              setDeleteDialogOpen(true);
                            }}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="flex justify-center items-center gap-2 mt-4">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setCurrentPage(p => Math.max(0, p - 1))}
                    disabled={currentPage === 0}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm">
                    {t('common.page')} {currentPage + 1} / {totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))}
                    disabled={currentPage >= totalPages - 1}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>

      {/* Create Single Coupon Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('coupons.createCoupon')}</DialogTitle>
            <DialogDescription>{t('coupons.createDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="code">{t('coupons.form.code')} *</Label>
                <Input
                  id="code"
                  value={formData.code}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                  placeholder={t('coupons.form.codePlaceholder')}
                  className="uppercase"
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="promotion">{t('coupons.form.promotion')} *</Label>
                <Select
                  value={formData.promotionId?.toString() || ''}
                  onValueChange={(value) => setFormData({ ...formData, promotionId: Number(value) })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t('common.select')} />
                  </SelectTrigger>
                  <SelectContent>
                    {promotions.map(p => (
                      <SelectItem key={p.id} value={p.id.toString()}>{p.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="singleUse"
                    checked={formData.singleUse}
                    onChange={(e) => setFormData({ ...formData, singleUse: e.target.checked })}
                    className="h-4 w-4"
                  />
                  <Label htmlFor="singleUse">{t('coupons.form.singleUse')}</Label>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="maxUses">{t('coupons.form.maxUses')}</Label>
                  <Input
                    id="maxUses"
                    type="number"
                    value={formData.maxUses || ''}
                    onChange={(e) => setFormData({ ...formData, maxUses: e.target.value ? parseInt(e.target.value) : null })}
                    min="1"
                    disabled={formData.singleUse}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="validFrom">{t('coupons.form.validFrom')}</Label>
                  <Input
                    id="validFrom"
                    type="datetime-local"
                    value={formData.validFrom}
                    onChange={(e) => setFormData({ ...formData, validFrom: e.target.value })}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="validUntil">{t('coupons.form.validUntil')}</Label>
                  <Input
                    id="validUntil"
                    type="datetime-local"
                    value={formData.validUntil}
                    onChange={(e) => setFormData({ ...formData, validUntil: e.target.value })}
                  />
                </div>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateModalOpen(false); resetForm(); }}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" disabled={loading}>
                {loading ? t('common.creating') : t('common.create')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Generate Batch Modal */}
      <Dialog open={generateModalOpen} onOpenChange={setGenerateModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('coupons.generateBatch')}</DialogTitle>
            <DialogDescription>{t('coupons.generateDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleGenerate}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="gen-promotion">{t('coupons.form.promotion')} *</Label>
                <Select
                  value={generateData.promotionId?.toString() || ''}
                  onValueChange={(value) => setGenerateData({ ...generateData, promotionId: Number(value) })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t('common.select')} />
                  </SelectTrigger>
                  <SelectContent>
                    {promotions.map(p => (
                      <SelectItem key={p.id} value={p.id.toString()}>{p.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="count">{t('coupons.form.count')} *</Label>
                  <Input
                    id="count"
                    type="number"
                    value={generateData.count}
                    onChange={(e) => setGenerateData({ ...generateData, count: parseInt(e.target.value) || 1 })}
                    min="1"
                    max="1000"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="prefix">{t('coupons.form.prefix')}</Label>
                  <Input
                    id="prefix"
                    value={generateData.prefix}
                    onChange={(e) => setGenerateData({ ...generateData, prefix: e.target.value.toUpperCase() })}
                    placeholder={t('coupons.form.prefixPlaceholder')}
                    className="uppercase"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="codeLength">{t('coupons.form.codeLength')}</Label>
                  <Input
                    id="codeLength"
                    type="number"
                    value={generateData.codeLength}
                    onChange={(e) => setGenerateData({ ...generateData, codeLength: parseInt(e.target.value) || 8 })}
                    min="4"
                    max="16"
                  />
                </div>

                <div className="flex items-center space-x-2 pt-6">
                  <input
                    type="checkbox"
                    id="gen-singleUse"
                    checked={generateData.singleUse}
                    onChange={(e) => setGenerateData({ ...generateData, singleUse: e.target.checked })}
                    className="h-4 w-4"
                  />
                  <Label htmlFor="gen-singleUse">{t('coupons.form.singleUse')}</Label>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="gen-validFrom">{t('coupons.form.validFrom')}</Label>
                  <Input
                    id="gen-validFrom"
                    type="datetime-local"
                    value={generateData.validFrom}
                    onChange={(e) => setGenerateData({ ...generateData, validFrom: e.target.value })}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="gen-validUntil">{t('coupons.form.validUntil')}</Label>
                  <Input
                    id="gen-validUntil"
                    type="datetime-local"
                    value={generateData.validUntil}
                    onChange={(e) => setGenerateData({ ...generateData, validUntil: e.target.value })}
                  />
                </div>
              </div>

              <div className="bg-muted p-3 rounded-md text-sm">
                {t('coupons.generatePreview', {
                  count: generateData.count,
                  format: `${generateData.prefix || ''}${'X'.repeat(generateData.codeLength)}`
                })}
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setGenerateModalOpen(false); resetGenerateForm(); }}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" disabled={loading}>
                {loading ? t('coupons.generating') : t('coupons.generate')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('coupons.deleteCoupon')}</DialogTitle>
            <DialogDescription>
              {t('coupons.deleteConfirmation', { code: selectedCoupon?.code })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteDialogOpen(false); setSelectedCoupon(null); }}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDelete}>
              {t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
