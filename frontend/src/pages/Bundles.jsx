import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { bundleAPI, restaurantAPI, menuAPI } from '../services/api';
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
  Package,
  ToggleLeft,
  ToggleRight,
  ChevronLeft,
  ChevronRight,
  X,
  DollarSign,
  Clock,
  Percent
} from 'lucide-react';

export default function Bundles() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [bundles, setBundles] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedBundle, setSelectedBundle] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const daysOfWeek = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    imageUrl: '',
    bundlePrice: '',
    active: true,
    availableFrom: '',
    availableUntil: '',
    availableDays: '',
    maxPerOrder: '',
    displayOrder: 0,
    items: [],
    optionGroups: [],
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
      loadBundles();
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

  const loadBundles = async () => {
    try {
      setLoading(true);
      const response = await bundleAPI.getBundles(parseInt(selectedRestaurant), { page: currentPage, size: 10 });
      const data = response.data.data || response.data;
      setBundles(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (error) {
      console.error('Failed to load bundles:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadProducts = async () => {
    try {
      const response = await menuAPI.getProductsByRestaurant(parseInt(selectedRestaurant));
      const data = response.data.data || response.data;
      setProducts(Array.isArray(data) ? data : (data.content || []));
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      if (!formData.name || !formData.bundlePrice) {
        alert(t('bundles.messages.fillRequired'));
        return;
      }

      setLoading(true);
      const payload = {
        ...formData,
        bundlePrice: parseFloat(formData.bundlePrice),
        maxPerOrder: formData.maxPerOrder ? parseInt(formData.maxPerOrder) : null,
      };

      await bundleAPI.createBundle(parseInt(selectedRestaurant), payload);
      setCreateModalOpen(false);
      resetForm();
      loadBundles();
    } catch (error) {
      console.error('Failed to create bundle:', error);
      alert(t('bundles.messages.createError') + ': ' + (error.response?.data?.message || error.message));
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
        bundlePrice: parseFloat(formData.bundlePrice),
        maxPerOrder: formData.maxPerOrder ? parseInt(formData.maxPerOrder) : null,
      };

      await bundleAPI.updateBundle(selectedBundle.id, payload);
      setEditModalOpen(false);
      resetForm();
      setSelectedBundle(null);
      loadBundles();
    } catch (error) {
      console.error('Failed to update bundle:', error);
      alert(t('bundles.messages.updateError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedBundle) return;
    try {
      await bundleAPI.deleteBundle(selectedBundle.id);
      setDeleteDialogOpen(false);
      setSelectedBundle(null);
      loadBundles();
    } catch (error) {
      console.error('Failed to delete bundle:', error);
      alert(t('bundles.messages.deleteError'));
    }
  };

  const handleToggle = async (id) => {
    try {
      await bundleAPI.toggleBundle(id);
      loadBundles();
    } catch (error) {
      console.error('Failed to toggle bundle:', error);
    }
  };

  const handleEditClick = (bundle) => {
    setSelectedBundle(bundle);
    setFormData({
      name: bundle.name,
      description: bundle.description || '',
      imageUrl: bundle.imageUrl || '',
      bundlePrice: bundle.bundlePrice?.toString() || '',
      active: bundle.active,
      availableFrom: bundle.availableFrom || '',
      availableUntil: bundle.availableUntil || '',
      availableDays: bundle.availableDays || '',
      maxPerOrder: bundle.maxPerOrder?.toString() || '',
      displayOrder: bundle.displayOrder || 0,
      items: bundle.items?.map(item => ({
        productId: item.productId,
        quantity: item.quantity,
        isRequired: item.isRequired,
        isDefault: item.isDefault,
      })) || [],
      optionGroups: bundle.optionGroups?.map(group => ({
        name: group.name,
        description: group.description || '',
        minSelections: group.minSelections,
        maxSelections: group.maxSelections,
        isRequired: group.isRequired,
        options: group.options?.map(opt => ({
          productId: opt.productId,
          priceAdjustment: opt.priceAdjustment,
          isDefault: opt.isDefault,
        })) || [],
      })) || [],
    });
    setEditModalOpen(true);
  };

  const handleDeleteClick = (bundle) => {
    setSelectedBundle(bundle);
    setDeleteDialogOpen(true);
  };

  const resetForm = () => {
    setFormData({
      name: '',
      description: '',
      imageUrl: '',
      bundlePrice: '',
      active: true,
      availableFrom: '',
      availableUntil: '',
      availableDays: '',
      maxPerOrder: '',
      displayOrder: 0,
      items: [],
      optionGroups: [],
    });
  };

  const addItem = () => {
    setFormData({
      ...formData,
      items: [...formData.items, { productId: null, quantity: 1, isRequired: true, isDefault: true }]
    });
  };

  const removeItem = (index) => {
    setFormData({
      ...formData,
      items: formData.items.filter((_, i) => i !== index)
    });
  };

  const updateItem = (index, field, value) => {
    const updated = [...formData.items];
    updated[index] = { ...updated[index], [field]: value };
    setFormData({ ...formData, items: updated });
  };

  const addOptionGroup = () => {
    setFormData({
      ...formData,
      optionGroups: [...formData.optionGroups, {
        name: '',
        description: '',
        minSelections: 1,
        maxSelections: 1,
        isRequired: true,
        options: []
      }]
    });
  };

  const removeOptionGroup = (index) => {
    setFormData({
      ...formData,
      optionGroups: formData.optionGroups.filter((_, i) => i !== index)
    });
  };

  const updateOptionGroup = (index, field, value) => {
    const updated = [...formData.optionGroups];
    updated[index] = { ...updated[index], [field]: value };
    setFormData({ ...formData, optionGroups: updated });
  };

  const addOption = (groupIndex) => {
    const updated = [...formData.optionGroups];
    updated[groupIndex].options = [
      ...updated[groupIndex].options,
      { productId: null, priceAdjustment: 0, isDefault: false }
    ];
    setFormData({ ...formData, optionGroups: updated });
  };

  const removeOption = (groupIndex, optionIndex) => {
    const updated = [...formData.optionGroups];
    updated[groupIndex].options = updated[groupIndex].options.filter((_, i) => i !== optionIndex);
    setFormData({ ...formData, optionGroups: updated });
  };

  const updateOption = (groupIndex, optionIndex, field, value) => {
    const updated = [...formData.optionGroups];
    updated[groupIndex].options[optionIndex] = {
      ...updated[groupIndex].options[optionIndex],
      [field]: value
    };
    setFormData({ ...formData, optionGroups: updated });
  };

  const toggleDay = (day) => {
    const days = formData.availableDays ? formData.availableDays.split(',') : [];
    const index = days.indexOf(day);
    if (index > -1) {
      days.splice(index, 1);
    } else {
      days.push(day);
    }
    setFormData({ ...formData, availableDays: days.join(',') });
  };

  const formatPrice = (price) => {
    if (!price) return '-';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(price);
  };

  const renderFormFields = () => (
    <div className="space-y-4 py-4 max-h-[60vh] overflow-y-auto">
      {/* Basic Info */}
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="name">{t('bundles.form.name')} *</Label>
          <Input
            id="name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder={t('bundles.form.namePlaceholder')}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="bundlePrice">{t('bundles.form.bundlePrice')} *</Label>
          <div className="relative">
            <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              id="bundlePrice"
              type="number"
              step="0.01"
              value={formData.bundlePrice}
              onChange={(e) => setFormData({ ...formData, bundlePrice: e.target.value })}
              className="pl-9"
              required
            />
          </div>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">{t('bundles.form.description')}</Label>
        <Textarea
          id="description"
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          placeholder={t('bundles.form.descriptionPlaceholder')}
          rows={2}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="imageUrl">{t('bundles.form.imageUrl')}</Label>
        <Input
          id="imageUrl"
          value={formData.imageUrl}
          onChange={(e) => setFormData({ ...formData, imageUrl: e.target.value })}
          placeholder="https://..."
        />
      </div>

      {/* Time Restrictions */}
      <div className="border-t pt-4 mt-4">
        <h4 className="font-medium mb-3">{t('bundles.form.availability')}</h4>
        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label>{t('bundles.form.availableFrom')}</Label>
            <Input
              type="time"
              value={formData.availableFrom}
              onChange={(e) => setFormData({ ...formData, availableFrom: e.target.value })}
            />
          </div>
          <div className="space-y-2">
            <Label>{t('bundles.form.availableUntil')}</Label>
            <Input
              type="time"
              value={formData.availableUntil}
              onChange={(e) => setFormData({ ...formData, availableUntil: e.target.value })}
            />
          </div>
          <div className="space-y-2">
            <Label>{t('bundles.form.maxPerOrder')}</Label>
            <Input
              type="number"
              min="1"
              value={formData.maxPerOrder}
              onChange={(e) => setFormData({ ...formData, maxPerOrder: e.target.value })}
            />
          </div>
        </div>

        <div className="mt-4 space-y-2">
          <Label>{t('bundles.form.availableDays')}</Label>
          <div className="flex gap-2 flex-wrap">
            {daysOfWeek.map(day => (
              <Badge
                key={day}
                variant={formData.availableDays?.includes(day) ? 'default' : 'outline'}
                className="cursor-pointer"
                onClick={() => toggleDay(day)}
              >
                {t(`enums.days.${day}`)}
              </Badge>
            ))}
          </div>
          <p className="text-xs text-muted-foreground">{t('bundles.form.availableDaysHint')}</p>
        </div>
      </div>

      {/* Bundle Items */}
      <div className="border-t pt-4 mt-4">
        <div className="flex justify-between items-center mb-3">
          <h4 className="font-medium">{t('bundles.form.items')} *</h4>
          <Button type="button" variant="outline" size="sm" onClick={addItem}>
            <Plus className="h-4 w-4 mr-1" />
            {t('bundles.form.addItem')}
          </Button>
        </div>

        {formData.items.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            {t('bundles.form.noItems')}
          </p>
        ) : (
          <div className="space-y-3">
            {formData.items.map((item, index) => (
              <div key={index} className="flex items-center gap-3 p-3 border rounded-lg bg-muted/30">
                <div className="flex-1">
                  <Select
                    value={item.productId?.toString() || 'none'}
                    onValueChange={(value) => updateItem(index, 'productId', value === 'none' ? null : Number(value))}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t('bundles.form.selectProduct')} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">{t('common.select')}</SelectItem>
                      {products.map(p => (
                        <SelectItem key={p.id} value={p.id.toString()}>
                          {p.name} ({formatPrice(p.price)})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="w-20">
                  <Input
                    type="number"
                    min="1"
                    value={item.quantity}
                    onChange={(e) => updateItem(index, 'quantity', parseInt(e.target.value) || 1)}
                    placeholder={t('bundles.form.qty')}
                  />
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-red-600"
                  onClick={() => removeItem(index)}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Option Groups */}
      <div className="border-t pt-4 mt-4">
        <div className="flex justify-between items-center mb-3">
          <div>
            <h4 className="font-medium">{t('bundles.form.optionGroups')}</h4>
            <p className="text-xs text-muted-foreground">{t('bundles.form.optionGroupsHint')}</p>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={addOptionGroup}>
            <Plus className="h-4 w-4 mr-1" />
            {t('bundles.form.addOptionGroup')}
          </Button>
        </div>

        {formData.optionGroups.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            {t('bundles.form.noOptionGroups')}
          </p>
        ) : (
          <div className="space-y-4">
            {formData.optionGroups.map((group, groupIndex) => (
              <div key={groupIndex} className="p-4 border rounded-lg bg-muted/20">
                <div className="flex justify-between items-start mb-3">
                  <div className="flex-1 grid grid-cols-2 gap-3">
                    <Input
                      value={group.name}
                      onChange={(e) => updateOptionGroup(groupIndex, 'name', e.target.value)}
                      placeholder={t('bundles.form.groupName')}
                    />
                    <div className="flex gap-2">
                      <Input
                        type="number"
                        min="0"
                        value={group.minSelections}
                        onChange={(e) => updateOptionGroup(groupIndex, 'minSelections', parseInt(e.target.value) || 0)}
                        placeholder="Min"
                        className="w-20"
                      />
                      <Input
                        type="number"
                        min="1"
                        value={group.maxSelections}
                        onChange={(e) => updateOptionGroup(groupIndex, 'maxSelections', parseInt(e.target.value) || 1)}
                        placeholder="Max"
                        className="w-20"
                      />
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="text-red-600 ml-2"
                    onClick={() => removeOptionGroup(groupIndex)}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>

                <div className="space-y-2">
                  {group.options.map((option, optIndex) => (
                    <div key={optIndex} className="flex items-center gap-2 pl-4">
                      <Select
                        value={option.productId?.toString() || 'none'}
                        onValueChange={(value) => updateOption(groupIndex, optIndex, 'productId', value === 'none' ? null : Number(value))}
                      >
                        <SelectTrigger className="flex-1">
                          <SelectValue placeholder={t('bundles.form.selectProduct')} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="none">{t('common.select')}</SelectItem>
                          {products.map(p => (
                            <SelectItem key={p.id} value={p.id.toString()}>
                              {p.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <Input
                        type="number"
                        step="0.01"
                        value={option.priceAdjustment || 0}
                        onChange={(e) => updateOption(groupIndex, optIndex, 'priceAdjustment', parseFloat(e.target.value) || 0)}
                        placeholder="+/-"
                        className="w-24"
                      />
                      <label className="flex items-center gap-1 text-sm">
                        <input
                          type="checkbox"
                          checked={option.isDefault}
                          onChange={(e) => updateOption(groupIndex, optIndex, 'isDefault', e.target.checked)}
                        />
                        {t('bundles.form.default')}
                      </label>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="text-red-600"
                        onClick={() => removeOption(groupIndex, optIndex)}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="ml-4"
                    onClick={() => addOption(groupIndex)}
                  >
                    <Plus className="h-4 w-4 mr-1" />
                    {t('bundles.form.addOption')}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );

  if (loading && bundles.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('bundles.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('bundles.subtitle')}</p>
        </div>
        <div className="flex gap-3">
          <Button onClick={() => { resetForm(); setCreateModalOpen(true); }}>
            <Plus className="h-4 w-4 mr-2" />
            {t('bundles.addBundle')}
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

      {/* Bundles Table */}
      <Card>
        <CardHeader>
          <CardTitle>{t('bundles.listTitle')}</CardTitle>
          <CardDescription>{t('bundles.listDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {bundles.length === 0 ? (
            <div className="text-center py-12">
              <Package className="mx-auto h-12 w-12 text-muted-foreground" />
              <h3 className="mt-2 text-sm font-medium">{t('bundles.noBundles')}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{t('bundles.getStarted')}</p>
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('bundles.table.name')}</TableHead>
                    <TableHead>{t('bundles.table.price')}</TableHead>
                    <TableHead>{t('bundles.table.savings')}</TableHead>
                    <TableHead>{t('bundles.table.items')}</TableHead>
                    <TableHead>{t('bundles.table.status')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {bundles.map(bundle => (
                    <TableRow key={bundle.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          {bundle.imageUrl ? (
                            <img
                              src={bundle.imageUrl}
                              alt={bundle.name}
                              className="w-12 h-12 rounded-lg object-cover"
                            />
                          ) : (
                            <div className="w-12 h-12 bg-primary/10 rounded-lg flex items-center justify-center">
                              <Package className="w-6 h-6 text-primary" />
                            </div>
                          )}
                          <div>
                            <div className="font-medium">{bundle.name}</div>
                            <div className="text-sm text-muted-foreground line-clamp-1">{bundle.description}</div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="font-medium text-lg">{formatPrice(bundle.bundlePrice)}</div>
                        {bundle.originalPrice && (
                          <div className="text-sm text-muted-foreground line-through">
                            {formatPrice(bundle.originalPrice)}
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        {bundle.savingsAmount && bundle.savingsAmount > 0 ? (
                          <Badge variant="secondary" className="bg-green-100 text-green-800">
                            <Percent className="h-3 w-3 mr-1" />
                            {t('bundles.save')} {formatPrice(bundle.savingsAmount)}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="text-sm">
                          {bundle.items?.length || 0} {t('bundles.items')}
                          {bundle.optionGroups?.length > 0 && (
                            <span className="text-muted-foreground">
                              {' '}+ {bundle.optionGroups.length} {t('bundles.options')}
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="space-y-1">
                          <Badge
                            variant={bundle.active ? 'default' : 'secondary'}
                            className="cursor-pointer"
                            onClick={() => handleToggle(bundle.id)}
                          >
                            {bundle.active ? (
                              <><ToggleRight className="h-3 w-3 mr-1" /> {t('common.active')}</>
                            ) : (
                              <><ToggleLeft className="h-3 w-3 mr-1" /> {t('common.inactive')}</>
                            )}
                          </Badge>
                          {bundle.currentlyAvailable && (
                            <Badge variant="outline" className="ml-1">
                              <Clock className="h-3 w-3 mr-1" />
                              {t('bundles.available')}
                            </Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => handleEditClick(bundle)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600 hover:text-red-700"
                            onClick={() => handleDeleteClick(bundle)}
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

      {/* Create Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{t('bundles.createBundle')}</DialogTitle>
            <DialogDescription>{t('bundles.createDescription')}</DialogDescription>
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
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{t('bundles.editBundle')}</DialogTitle>
            <DialogDescription>{t('bundles.editDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdate}>
            {renderFormFields()}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setEditModalOpen(false); resetForm(); setSelectedBundle(null); }}>
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
            <DialogTitle>{t('bundles.deleteBundle')}</DialogTitle>
            <DialogDescription>
              {t('bundles.deleteConfirmation', { name: selectedBundle?.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteDialogOpen(false); setSelectedBundle(null); }}>
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
