import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { promotionAPI, restaurantAPI, menuAPI } from '../services/api';
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
import {
  Plus,
  Edit,
  Trash2,
  Tag,
  Ticket,
  ToggleLeft,
  ToggleRight,
  Percent,
  DollarSign,
  Gift,
  Package,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';

export default function Promotions() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [promotions, setPromotions] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedPromotion, setSelectedPromotion] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const promotionTypes = [
    { value: 'PERCENTAGE', icon: Percent },
    { value: 'FIXED_AMOUNT', icon: DollarSign },
    { value: 'FREE_ITEM', icon: Gift },
    { value: 'BUY_X_GET_Y', icon: Package },
  ];

  const promotionScopes = ['ALL', 'CATEGORY', 'PRODUCT', 'ORDER_TYPE'];
  const orderTypes = ['DINE_IN', 'TAKEAWAY', 'DELIVERY'];
  const daysOfWeek = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    promotionType: 'PERCENTAGE',
    promotionScope: 'ALL',
    discountValue: 0,
    buyQuantity: null,
    getQuantity: null,
    freeProductId: null,
    startDate: new Date().toISOString().slice(0, 16),
    endDate: '',
    active: true,
    priority: 0,
    stackable: false,
    rule: {
      minOrderAmount: null,
      maxDiscountAmount: null,
      usageLimit: null,
      perCustomerLimit: null,
      minItems: null,
      applicableOrderTypes: [],
      applicableDays: [],
      startTime: null,
      endTime: null,
      firstOrderOnly: false,
    },
    promotionProducts: [],
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
      loadCategories();
      loadProducts();
    }
  }, [selectedRestaurant, currentPage]);

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
      setLoading(true);
      const response = await promotionAPI.getPromotions(parseInt(selectedRestaurant), { page: currentPage, size: 10 });
      const data = response.data.data || response.data;
      setPromotions(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (error) {
      console.error('Failed to load promotions:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadCategories = async () => {
    try {
      const response = await menuAPI.getCategories(parseInt(selectedRestaurant));
      setCategories(response.data.data || []);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  };

  const loadProducts = async () => {
    try {
      const response = await menuAPI.getProducts(parseInt(selectedRestaurant), { page: 0, size: 100 });
      const data = response.data.data || response.data;
      setProducts(data.content || data || []);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      if (!formData.name || formData.discountValue <= 0) {
        alert(t('promotions.messages.fillRequired'));
        return;
      }

      setLoading(true);
      const payload = {
        ...formData,
        startDate: formData.startDate ? new Date(formData.startDate).toISOString() : null,
        endDate: formData.endDate ? new Date(formData.endDate).toISOString() : null,
      };

      await promotionAPI.createPromotion(parseInt(selectedRestaurant), payload);
      setCreateModalOpen(false);
      resetForm();
      loadPromotions();
    } catch (error) {
      console.error('Failed to create promotion:', error);
      alert(t('promotions.messages.createError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const payload = {
        ...formData,
        startDate: formData.startDate ? new Date(formData.startDate).toISOString() : null,
        endDate: formData.endDate ? new Date(formData.endDate).toISOString() : null,
      };

      await promotionAPI.updatePromotion(selectedPromotion.id, payload);
      setEditModalOpen(false);
      resetForm();
      setSelectedPromotion(null);
      loadPromotions();
    } catch (error) {
      console.error('Failed to update promotion:', error);
      alert(t('promotions.messages.updateError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedPromotion) return;
    try {
      await promotionAPI.deletePromotion(selectedPromotion.id);
      setDeleteDialogOpen(false);
      setSelectedPromotion(null);
      loadPromotions();
    } catch (error) {
      console.error('Failed to delete promotion:', error);
      alert(t('promotions.messages.deleteError'));
    }
  };

  const handleToggle = async (id) => {
    try {
      await promotionAPI.togglePromotion(id);
      loadPromotions();
    } catch (error) {
      console.error('Failed to toggle promotion:', error);
    }
  };

  const handleEditClick = (promotion) => {
    setSelectedPromotion(promotion);
    setFormData({
      name: promotion.name,
      description: promotion.description || '',
      promotionType: promotion.promotionType,
      promotionScope: promotion.promotionScope,
      discountValue: promotion.discountValue,
      buyQuantity: promotion.buyQuantity,
      getQuantity: promotion.getQuantity,
      freeProductId: promotion.freeProductId,
      startDate: promotion.startDate ? promotion.startDate.slice(0, 16) : '',
      endDate: promotion.endDate ? promotion.endDate.slice(0, 16) : '',
      active: promotion.active,
      priority: promotion.priority,
      stackable: promotion.stackable,
      rule: promotion.rule || {
        minOrderAmount: null,
        maxDiscountAmount: null,
        usageLimit: null,
        perCustomerLimit: null,
        minItems: null,
        applicableOrderTypes: [],
        applicableDays: [],
        startTime: null,
        endTime: null,
        firstOrderOnly: false,
      },
      promotionProducts: promotion.promotionProducts || [],
    });
    setEditModalOpen(true);
  };

  const handleDeleteClick = (promotion) => {
    setSelectedPromotion(promotion);
    setDeleteDialogOpen(true);
  };

  const resetForm = () => {
    setFormData({
      name: '',
      description: '',
      promotionType: 'PERCENTAGE',
      promotionScope: 'ALL',
      discountValue: 0,
      buyQuantity: null,
      getQuantity: null,
      freeProductId: null,
      startDate: new Date().toISOString().slice(0, 16),
      endDate: '',
      active: true,
      priority: 0,
      stackable: false,
      rule: {
        minOrderAmount: null,
        maxDiscountAmount: null,
        usageLimit: null,
        perCustomerLimit: null,
        minItems: null,
        applicableOrderTypes: [],
        applicableDays: [],
        startTime: null,
        endTime: null,
        firstOrderOnly: false,
      },
      promotionProducts: [],
    });
  };

  const getTypeIcon = (type) => {
    const typeInfo = promotionTypes.find(t => t.value === type);
    return typeInfo ? typeInfo.icon : Tag;
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString();
  };

  const formatDiscountValue = (promo) => {
    switch (promo.promotionType) {
      case 'PERCENTAGE':
        return `${promo.discountValue}%`;
      case 'FIXED_AMOUNT':
        return `${promo.discountValue}`;
      case 'FREE_ITEM':
        return promo.freeProductName || t('promotions.freeItem');
      case 'BUY_X_GET_Y':
        return `${promo.buyQuantity}+${promo.getQuantity}`;
      default:
        return promo.discountValue;
    }
  };

  const renderFormFields = () => (
    <div className="space-y-4 py-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="name">{t('promotions.form.name')} *</Label>
          <Input
            id="name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder={t('promotions.form.namePlaceholder')}
            required
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="type">{t('promotions.form.type')} *</Label>
          <Select
            value={formData.promotionType}
            onValueChange={(value) => setFormData({ ...formData, promotionType: value })}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {promotionTypes.map(type => (
                <SelectItem key={type.value} value={type.value}>
                  {t(`promotions.types.${type.value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">{t('promotions.form.description')}</Label>
        <Textarea
          id="description"
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          placeholder={t('promotions.form.descriptionPlaceholder')}
          rows={3}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="discountValue">
            {formData.promotionType === 'PERCENTAGE'
              ? t('promotions.form.percentOff')
              : t('promotions.form.amountOff')} *
          </Label>
          <Input
            id="discountValue"
            type="number"
            value={formData.discountValue}
            onChange={(e) => setFormData({ ...formData, discountValue: parseFloat(e.target.value) || 0 })}
            min="0"
            max={formData.promotionType === 'PERCENTAGE' ? 100 : undefined}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="scope">{t('promotions.form.scope')}</Label>
          <Select
            value={formData.promotionScope}
            onValueChange={(value) => setFormData({ ...formData, promotionScope: value })}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {promotionScopes.map(scope => (
                <SelectItem key={scope} value={scope}>
                  {t(`promotions.scopes.${scope}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {formData.promotionType === 'BUY_X_GET_Y' && (
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="buyQuantity">{t('promotions.form.buyQuantity')}</Label>
            <Input
              id="buyQuantity"
              type="number"
              value={formData.buyQuantity || ''}
              onChange={(e) => setFormData({ ...formData, buyQuantity: parseInt(e.target.value) || null })}
              min="1"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="getQuantity">{t('promotions.form.getQuantity')}</Label>
            <Input
              id="getQuantity"
              type="number"
              value={formData.getQuantity || ''}
              onChange={(e) => setFormData({ ...formData, getQuantity: parseInt(e.target.value) || null })}
              min="1"
            />
          </div>
        </div>
      )}

      {formData.promotionType === 'FREE_ITEM' && (
        <div className="space-y-2">
          <Label htmlFor="freeProduct">{t('promotions.form.freeProduct')}</Label>
          <Select
            value={formData.freeProductId?.toString() || ''}
            onValueChange={(value) => setFormData({ ...formData, freeProductId: value ? Number(value) : null })}
          >
            <SelectTrigger>
              <SelectValue placeholder={t('common.select')} />
            </SelectTrigger>
            <SelectContent>
              {products.map(p => (
                <SelectItem key={p.id} value={p.id.toString()}>{p.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="startDate">{t('promotions.form.startDate')} *</Label>
          <Input
            id="startDate"
            type="datetime-local"
            value={formData.startDate}
            onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="endDate">{t('promotions.form.endDate')}</Label>
          <Input
            id="endDate"
            type="datetime-local"
            value={formData.endDate}
            onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
          />
        </div>
      </div>

      {/* Rules Section */}
      <div className="border-t pt-4 mt-4">
        <h4 className="font-medium mb-3">{t('promotions.form.rules')}</h4>
        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="minOrder">{t('promotions.form.minOrder')}</Label>
            <Input
              id="minOrder"
              type="number"
              value={formData.rule.minOrderAmount || ''}
              onChange={(e) => setFormData({
                ...formData,
                rule: { ...formData.rule, minOrderAmount: e.target.value ? parseFloat(e.target.value) : null }
              })}
              min="0"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="maxDiscount">{t('promotions.form.maxDiscount')}</Label>
            <Input
              id="maxDiscount"
              type="number"
              value={formData.rule.maxDiscountAmount || ''}
              onChange={(e) => setFormData({
                ...formData,
                rule: { ...formData.rule, maxDiscountAmount: e.target.value ? parseFloat(e.target.value) : null }
              })}
              min="0"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="usageLimit">{t('promotions.form.usageLimit')}</Label>
            <Input
              id="usageLimit"
              type="number"
              value={formData.rule.usageLimit || ''}
              onChange={(e) => setFormData({
                ...formData,
                rule: { ...formData.rule, usageLimit: e.target.value ? parseInt(e.target.value) : null }
              })}
              min="1"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="perCustomerLimit">{t('promotions.form.perCustomerLimit')}</Label>
            <Input
              id="perCustomerLimit"
              type="number"
              value={formData.rule.perCustomerLimit || ''}
              onChange={(e) => setFormData({
                ...formData,
                rule: { ...formData.rule, perCustomerLimit: e.target.value ? parseInt(e.target.value) : null }
              })}
              min="1"
            />
          </div>
          <div className="flex items-center space-x-2 pt-6">
            <input
              type="checkbox"
              id="firstOrderOnly"
              checked={formData.rule.firstOrderOnly}
              onChange={(e) => setFormData({
                ...formData,
                rule: { ...formData.rule, firstOrderOnly: e.target.checked }
              })}
              className="h-4 w-4"
            />
            <Label htmlFor="firstOrderOnly">{t('promotions.form.firstOrderOnly')}</Label>
          </div>
          <div className="flex items-center space-x-2 pt-6">
            <input
              type="checkbox"
              id="stackable"
              checked={formData.stackable}
              onChange={(e) => setFormData({ ...formData, stackable: e.target.checked })}
              className="h-4 w-4"
            />
            <Label htmlFor="stackable">{t('promotions.form.stackable')}</Label>
          </div>
        </div>

        <div className="mt-4 space-y-2">
          <Label>{t('promotions.form.orderTypes')}</Label>
          <div className="flex gap-4 flex-wrap">
            {orderTypes.map(type => (
              <div key={type} className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id={`orderType-${type}`}
                  checked={formData.rule.applicableOrderTypes?.includes(type)}
                  onChange={(e) => {
                    const types = formData.rule.applicableOrderTypes || [];
                    setFormData({
                      ...formData,
                      rule: {
                        ...formData.rule,
                        applicableOrderTypes: e.target.checked
                          ? [...types, type]
                          : types.filter(t => t !== type)
                      }
                    });
                  }}
                  className="h-4 w-4"
                />
                <Label htmlFor={`orderType-${type}`}>{t(`enums.orderTypes.${type}`)}</Label>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-4 space-y-2">
          <Label>{t('promotions.form.applicableDays')}</Label>
          <div className="flex gap-3 flex-wrap">
            {daysOfWeek.map(day => (
              <div key={day} className="flex items-center space-x-1">
                <input
                  type="checkbox"
                  id={`day-${day}`}
                  checked={formData.rule.applicableDays?.includes(day)}
                  onChange={(e) => {
                    const days = formData.rule.applicableDays || [];
                    setFormData({
                      ...formData,
                      rule: {
                        ...formData.rule,
                        applicableDays: e.target.checked
                          ? [...days, day]
                          : days.filter(d => d !== day)
                      }
                    });
                  }}
                  className="h-4 w-4"
                />
                <Label htmlFor={`day-${day}`} className="text-sm">{t(`enums.days.${day}`)}</Label>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );

  if (loading && promotions.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('promotions.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('promotions.subtitle')}</p>
        </div>
        <div className="flex gap-3">
          <Link to="/marketing/coupons">
            <Button variant="outline">
              <Ticket className="h-4 w-4 mr-2" />
              {t('promotions.manageCoupons')}
            </Button>
          </Link>
          <Button onClick={() => { resetForm(); setCreateModalOpen(true); }}>
            <Plus className="h-4 w-4 mr-2" />
            {t('promotions.addPromotion')}
          </Button>
          {restaurants.length > 1 && (
            <Select
              value={selectedRestaurant}
              onValueChange={setSelectedRestaurant}
            >
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder={t('common.selectRestaurant')} />
              </SelectTrigger>
              <SelectContent>
                {restaurants.map((r) => (
                  <SelectItem key={r.id} value={r.id.toString()}>
                    {r.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
      </div>

      {/* Promotions Table */}
      <Card>
        <CardHeader>
          <CardTitle>{t('promotions.listTitle')}</CardTitle>
          <CardDescription>{t('promotions.listDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {promotions.length === 0 ? (
            <div className="text-center py-12">
              <Tag className="mx-auto h-12 w-12 text-muted-foreground" />
              <h3 className="mt-2 text-sm font-medium">{t('promotions.noPromotions')}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{t('promotions.getStarted')}</p>
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('promotions.table.name')}</TableHead>
                    <TableHead>{t('promotions.table.type')}</TableHead>
                    <TableHead>{t('promotions.table.value')}</TableHead>
                    <TableHead>{t('promotions.table.dates')}</TableHead>
                    <TableHead>{t('promotions.table.usage')}</TableHead>
                    <TableHead>{t('promotions.table.status')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {promotions.map(promo => {
                    const TypeIcon = getTypeIcon(promo.promotionType);
                    return (
                      <TableRow key={promo.id}>
                        <TableCell>
                          <div className="flex items-center gap-3">
                            <div className="p-2 bg-primary/10 rounded-lg">
                              <TypeIcon className="w-5 h-5 text-primary" />
                            </div>
                            <div>
                              <div className="font-medium">{promo.name}</div>
                              <div className="text-sm text-muted-foreground line-clamp-1">{promo.description}</div>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">
                            {t(`promotions.types.${promo.promotionType}`)}
                          </Badge>
                        </TableCell>
                        <TableCell className="font-medium">
                          {formatDiscountValue(promo)}
                        </TableCell>
                        <TableCell>
                          <div className="text-sm">
                            <div>{formatDate(promo.startDate)}</div>
                            <div className="text-muted-foreground">
                              {t('promotions.to')} {promo.endDate ? formatDate(promo.endDate) : t('promotions.noEnd')}
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="text-sm">
                            <div>{promo.totalUsage || 0} {t('promotions.uses')}</div>
                            <div className="text-muted-foreground">{promo.couponCount || 0} {t('promotions.coupons')}</div>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant={promo.active && promo.currentlyValid ? 'default' : 'secondary'}
                            className="cursor-pointer"
                            onClick={() => handleToggle(promo.id)}
                          >
                            {promo.active && promo.currentlyValid ? (
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
                              onClick={() => handleEditClick(promo)}
                            >
                              <Edit className="h-4 w-4" />
                            </Button>
                            <Link to={`/marketing/coupons?promotionId=${promo.id}`}>
                              <Button size="sm" variant="ghost">
                                <Ticket className="h-4 w-4" />
                              </Button>
                            </Link>
                            <Button
                              size="sm"
                              variant="ghost"
                              className="text-red-600 hover:text-red-700"
                              onClick={() => handleDeleteClick(promo)}
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

      {/* Create Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('promotions.createPromotion')}</DialogTitle>
            <DialogDescription>{t('promotions.createDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate}>
            {renderFormFields()}
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

      {/* Edit Modal */}
      <Dialog open={editModalOpen} onOpenChange={setEditModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('promotions.editPromotion')}</DialogTitle>
            <DialogDescription>{t('promotions.editDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdate}>
            {renderFormFields()}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setEditModalOpen(false); resetForm(); setSelectedPromotion(null); }}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" disabled={loading}>
                {loading ? t('common.saving') : t('common.save')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('promotions.deletePromotion')}</DialogTitle>
            <DialogDescription>
              {t('promotions.deleteConfirmation', { name: selectedPromotion?.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteDialogOpen(false); setSelectedPromotion(null); }}>
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
