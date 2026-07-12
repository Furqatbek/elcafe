import { useState, useEffect } from 'react';
import { notifyError, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { happyHourAPI, restaurantAPI, menuAPI } from '../services/api';
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
  Clock,
  ToggleLeft,
  ToggleRight,
  Percent,
  ChevronLeft,
  ChevronRight,
  Wine,
  X
} from 'lucide-react';

export default function HappyHours() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [happyHours, setHappyHours] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedHappyHour, setSelectedHappyHour] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const daysOfWeek = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    discountPercent: 10,
    active: true,
    priority: 0,
    schedules: [],
    productTargets: [],
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
      loadHappyHours();
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

  const loadHappyHours = async () => {
    try {
      setLoading(true);
      const response = await happyHourAPI.getHappyHours(parseInt(selectedRestaurant), { page: currentPage, size: 10 });
      const data = response.data.data || response.data;
      setHappyHours(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (error) {
      console.error('Failed to load happy hours:', error);
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
      if (!formData.name || formData.discountPercent <= 0 || formData.schedules.length === 0) {
        notifyWarning(t('happyHour.messages.fillRequired'));
        return;
      }

      setLoading(true);
      await happyHourAPI.createHappyHour(parseInt(selectedRestaurant), formData);
      setCreateModalOpen(false);
      resetForm();
      loadHappyHours();
    } catch (error) {
      console.error('Failed to create happy hour:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      await happyHourAPI.updateHappyHour(selectedHappyHour.id, formData);
      setEditModalOpen(false);
      resetForm();
      setSelectedHappyHour(null);
      loadHappyHours();
    } catch (error) {
      console.error('Failed to update happy hour:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedHappyHour) return;
    try {
      await happyHourAPI.deleteHappyHour(selectedHappyHour.id);
      setDeleteDialogOpen(false);
      setSelectedHappyHour(null);
      loadHappyHours();
    } catch (error) {
      console.error('Failed to delete happy hour:', error);
      notifyWarning(t('happyHour.messages.deleteError'));
    }
  };

  const handleToggle = async (id) => {
    try {
      await happyHourAPI.toggleHappyHour(id);
      loadHappyHours();
    } catch (error) {
      console.error('Failed to toggle happy hour:', error);
    }
  };

  const handleEditClick = (happyHour) => {
    setSelectedHappyHour(happyHour);
    setFormData({
      name: happyHour.name,
      description: happyHour.description || '',
      discountPercent: happyHour.discountPercent,
      active: happyHour.active,
      priority: happyHour.priority || 0,
      schedules: happyHour.schedules?.map(s => ({
        dayOfWeek: s.dayOfWeek,
        startTime: s.startTime,
        endTime: s.endTime,
      })) || [],
      productTargets: happyHour.productTargets?.map(pt => ({
        productId: pt.productId,
        categoryId: pt.categoryId,
      })) || [],
    });
    setEditModalOpen(true);
  };

  const handleDeleteClick = (happyHour) => {
    setSelectedHappyHour(happyHour);
    setDeleteDialogOpen(true);
  };

  const resetForm = () => {
    setFormData({
      name: '',
      description: '',
      discountPercent: 10,
      active: true,
      priority: 0,
      schedules: [],
      productTargets: [],
    });
  };

  const addSchedule = () => {
    setFormData({
      ...formData,
      schedules: [
        ...formData.schedules,
        { dayOfWeek: 'MON', startTime: '17:00', endTime: '19:00' }
      ]
    });
  };

  const removeSchedule = (index) => {
    setFormData({
      ...formData,
      schedules: formData.schedules.filter((_, i) => i !== index)
    });
  };

  const updateSchedule = (index, field, value) => {
    const updated = [...formData.schedules];
    updated[index] = { ...updated[index], [field]: value };
    setFormData({ ...formData, schedules: updated });
  };

  const addProductTarget = () => {
    setFormData({
      ...formData,
      productTargets: [
        ...formData.productTargets,
        { productId: null, categoryId: null }
      ]
    });
  };

  const removeProductTarget = (index) => {
    setFormData({
      ...formData,
      productTargets: formData.productTargets.filter((_, i) => i !== index)
    });
  };

  const updateProductTarget = (index, field, value) => {
    const updated = [...formData.productTargets];
    updated[index] = {
      ...updated[index],
      [field]: value ? Number(value) : null,
      // Clear the other field when one is set
      ...(field === 'productId' && value ? { categoryId: null } : {}),
      ...(field === 'categoryId' && value ? { productId: null } : {})
    };
    setFormData({ ...formData, productTargets: updated });
  };

  const formatSchedules = (schedules) => {
    if (!schedules || schedules.length === 0) return '-';

    // Group by time
    const grouped = {};
    schedules.forEach(s => {
      const timeKey = `${s.startTime}-${s.endTime}`;
      if (!grouped[timeKey]) {
        grouped[timeKey] = [];
      }
      grouped[timeKey].push(s.dayOfWeek);
    });

    return Object.entries(grouped).map(([time, days]) => (
      <div key={time} className="text-sm">
        <span className="font-medium">{days.map(d => t(`enums.days.${d}`)).join(', ')}</span>
        <span className="text-muted-foreground ml-2">{time}</span>
      </div>
    ));
  };

  const renderFormFields = () => (
    <div className="space-y-4 py-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="name">{t('happyHour.form.name')} *</Label>
          <Input
            id="name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder={t('happyHour.form.namePlaceholder')}
            required
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="discountPercent">{t('happyHour.form.discountPercent')} *</Label>
          <div className="relative">
            <Input
              id="discountPercent"
              type="number"
              value={formData.discountPercent}
              onChange={(e) => setFormData({ ...formData, discountPercent: parseFloat(e.target.value) || 0 })}
              min="0.01"
              max="100"
              step="0.01"
              className="pr-8"
            />
            <Percent className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          </div>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">{t('happyHour.form.description')}</Label>
        <Textarea
          id="description"
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          placeholder={t('happyHour.form.descriptionPlaceholder')}
          rows={2}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="priority">{t('happyHour.form.priority')}</Label>
        <Input
          id="priority"
          type="number"
          value={formData.priority}
          onChange={(e) => setFormData({ ...formData, priority: parseInt(e.target.value) || 0 })}
          min="0"
        />
        <p className="text-xs text-muted-foreground">{t('happyHour.form.priorityHint')}</p>
      </div>

      {/* Schedules Section */}
      <div className="border-t pt-4 mt-4">
        <div className="flex justify-between items-center mb-3">
          <h4 className="font-medium">{t('happyHour.form.schedules')} *</h4>
          <Button type="button" variant="outline" size="sm" onClick={addSchedule}>
            <Plus className="h-4 w-4 mr-1" />
            {t('happyHour.form.addSchedule')}
          </Button>
        </div>

        {formData.schedules.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            {t('happyHour.form.noSchedules')}
          </p>
        ) : (
          <div className="space-y-3">
            {formData.schedules.map((schedule, index) => (
              <div key={index} className="flex items-center gap-3 p-3 border rounded-lg bg-muted/30">
                <Select
                  value={schedule.dayOfWeek}
                  onValueChange={(value) => updateSchedule(index, 'dayOfWeek', value)}
                >
                  <SelectTrigger className="w-[120px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {daysOfWeek.map(day => (
                      <SelectItem key={day} value={day}>
                        {t(`enums.days.${day}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <div className="flex items-center gap-2">
                  <Input
                    type="time"
                    value={schedule.startTime}
                    onChange={(e) => updateSchedule(index, 'startTime', e.target.value)}
                    className="w-[130px]"
                  />
                  <span className="text-muted-foreground">{t('happyHour.form.to')}</span>
                  <Input
                    type="time"
                    value={schedule.endTime}
                    onChange={(e) => updateSchedule(index, 'endTime', e.target.value)}
                    className="w-[130px]"
                  />
                </div>

                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-red-600 hover:text-red-700 ml-auto"
                  onClick={() => removeSchedule(index)}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Product Targets Section */}
      <div className="border-t pt-4 mt-4">
        <div className="flex justify-between items-center mb-3">
          <div>
            <h4 className="font-medium">{t('happyHour.form.productTargets')}</h4>
            <p className="text-xs text-muted-foreground">{t('happyHour.form.productTargetsHint')}</p>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={addProductTarget}>
            <Plus className="h-4 w-4 mr-1" />
            {t('happyHour.form.addTarget')}
          </Button>
        </div>

        {formData.productTargets.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            {t('happyHour.form.allProducts')}
          </p>
        ) : (
          <div className="space-y-3">
            {formData.productTargets.map((target, index) => (
              <div key={index} className="flex items-center gap-3 p-3 border rounded-lg bg-muted/30">
                <div className="flex-1">
                  <Label className="text-xs text-muted-foreground">{t('happyHour.form.category')}</Label>
                  <Select
                    value={target.categoryId?.toString() || 'none'}
                    onValueChange={(value) => updateProductTarget(index, 'categoryId', value === 'none' ? null : value)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t('common.select')} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">{t('common.none')}</SelectItem>
                      {categories.map(c => (
                        <SelectItem key={c.id} value={c.id.toString()}>{c.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <span className="text-muted-foreground">{t('common.or')}</span>

                <div className="flex-1">
                  <Label className="text-xs text-muted-foreground">{t('happyHour.form.product')}</Label>
                  <Select
                    value={target.productId?.toString() || 'none'}
                    onValueChange={(value) => updateProductTarget(index, 'productId', value === 'none' ? null : value)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t('common.select')} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">{t('common.none')}</SelectItem>
                      {products.map(p => (
                        <SelectItem key={p.id} value={p.id.toString()}>{p.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-red-600 hover:text-red-700"
                  onClick={() => removeProductTarget(index)}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );

  if (loading && happyHours.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('happyHour.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('happyHour.subtitle')}</p>
        </div>
        <div className="flex gap-3">
          <Button onClick={() => { resetForm(); setCreateModalOpen(true); }}>
            <Plus className="h-4 w-4 mr-2" />
            {t('happyHour.addHappyHour')}
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

      {/* Happy Hours Table */}
      <Card>
        <CardHeader>
          <CardTitle>{t('happyHour.listTitle')}</CardTitle>
          <CardDescription>{t('happyHour.listDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {happyHours.length === 0 ? (
            <div className="text-center py-12">
              <Wine className="mx-auto h-12 w-12 text-muted-foreground" />
              <h3 className="mt-2 text-sm font-medium">{t('happyHour.noHappyHours')}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{t('happyHour.getStarted')}</p>
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('happyHour.table.name')}</TableHead>
                    <TableHead>{t('happyHour.table.discount')}</TableHead>
                    <TableHead>{t('happyHour.table.schedule')}</TableHead>
                    <TableHead>{t('happyHour.table.targets')}</TableHead>
                    <TableHead>{t('happyHour.table.status')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {happyHours.map(hh => (
                    <TableRow key={hh.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <div className="p-2 bg-primary/10 rounded-lg">
                            <Wine className="w-5 h-5 text-primary" />
                          </div>
                          <div>
                            <div className="font-medium">{hh.name}</div>
                            <div className="text-sm text-muted-foreground line-clamp-1">{hh.description}</div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="text-lg">
                          {hh.discountPercent}%
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="space-y-1">
                          {formatSchedules(hh.schedules)}
                        </div>
                      </TableCell>
                      <TableCell>
                        {hh.productTargets && hh.productTargets.length > 0 ? (
                          <div className="space-y-1">
                            {hh.productTargets.slice(0, 2).map((pt, i) => (
                              <Badge key={i} variant="secondary" className="text-xs">
                                {pt.categoryName || pt.productName}
                              </Badge>
                            ))}
                            {hh.productTargets.length > 2 && (
                              <span className="text-xs text-muted-foreground">
                                +{hh.productTargets.length - 2} {t('common.more')}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-sm text-muted-foreground">{t('happyHour.allProducts')}</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="space-y-1">
                          <Badge
                            variant={hh.active ? 'default' : 'secondary'}
                            className="cursor-pointer"
                            onClick={() => handleToggle(hh.id)}
                          >
                            {hh.active ? (
                              <><ToggleRight className="h-3 w-3 mr-1" /> {t('common.active')}</>
                            ) : (
                              <><ToggleLeft className="h-3 w-3 mr-1" /> {t('common.inactive')}</>
                            )}
                          </Badge>
                          {hh.currentlyActive && (
                            <Badge variant="success" className="bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-100">
                              <Clock className="h-3 w-3 mr-1" />
                              {t('happyHour.nowActive')}
                            </Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => handleEditClick(hh)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600 hover:text-red-700"
                            onClick={() => handleDeleteClick(hh)}
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
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('happyHour.createHappyHour')}</DialogTitle>
            <DialogDescription>{t('happyHour.createDescription')}</DialogDescription>
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
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('happyHour.editHappyHour')}</DialogTitle>
            <DialogDescription>{t('happyHour.editDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdate}>
            {renderFormFields()}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setEditModalOpen(false); resetForm(); setSelectedHappyHour(null); }}>
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
            <DialogTitle>{t('happyHour.deleteHappyHour')}</DialogTitle>
            <DialogDescription>
              {t('happyHour.deleteConfirmation', { name: selectedHappyHour?.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteDialogOpen(false); setSelectedHappyHour(null); }}>
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
