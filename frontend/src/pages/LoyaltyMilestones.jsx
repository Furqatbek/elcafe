import { useState, useEffect } from 'react';
import { notifyError, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { milestoneAPI, restaurantAPI, menuAPI } from '../services/api';
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
  Stamp,
  Gift,
  Percent,
  DollarSign,
  Coins,
  ToggleLeft,
  ToggleRight,
  Repeat,
  ArrowRight,
} from 'lucide-react';

const REWARD_TYPES = [
  { value: 'FREE_ITEM', icon: Gift },
  { value: 'DISCOUNT_PERCENTAGE', icon: Percent },
  { value: 'DISCOUNT_FIXED', icon: DollarSign },
  { value: 'BONUS_POINTS', icon: Coins },
];

export default function LoyaltyMilestones() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [milestones, setMilestones] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedMilestone, setSelectedMilestone] = useState(null);

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    requiredVisits: 10,
    rewardType: 'FREE_ITEM',
    rewardValue: null,
    rewardProductId: null,
    minOrderAmount: null,
    isRepeating: true,
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
      loadMilestones();
      loadProducts();
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const data = response.data.data?.content || response.data.data || [];
      setRestaurants(Array.isArray(data) ? data : []);
      if (!selectedRestaurant && data.length > 0 && !user?.restaurantId) {
        setSelectedRestaurant(data[0].id.toString());
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadMilestones = async () => {
    try {
      setLoading(true);
      const response = await milestoneAPI.getMilestones(parseInt(selectedRestaurant));
      const data = response.data.data || response.data || [];
      setMilestones(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Failed to load milestones:', error);
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
      if (!formData.name || !formData.requiredVisits) {
        notifyWarning(t('milestones.messages.fillRequired'));
        return;
      }
      setLoading(true);
      await milestoneAPI.createMilestone(parseInt(selectedRestaurant), formData);
      setCreateModalOpen(false);
      resetForm();
      loadMilestones();
    } catch (error) {
      console.error('Failed to create milestone:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      await milestoneAPI.updateMilestone(selectedMilestone.id, formData);
      setEditModalOpen(false);
      resetForm();
      setSelectedMilestone(null);
      loadMilestones();
    } catch (error) {
      console.error('Failed to update milestone:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedMilestone) return;
    try {
      await milestoneAPI.deleteMilestone(selectedMilestone.id);
      setDeleteDialogOpen(false);
      setSelectedMilestone(null);
      loadMilestones();
    } catch (error) {
      console.error('Failed to delete milestone:', error);
      notifyWarning(t('milestones.messages.deleteError'));
    }
  };

  const handleToggle = async (milestone) => {
    try {
      await milestoneAPI.updateMilestone(milestone.id, { active: !milestone.active });
      loadMilestones();
    } catch (error) {
      console.error('Failed to toggle milestone:', error);
    }
  };

  const handleEditClick = (milestone) => {
    setSelectedMilestone(milestone);
    setFormData({
      name: milestone.name,
      description: milestone.description || '',
      requiredVisits: milestone.requiredVisits,
      rewardType: milestone.rewardType,
      rewardValue: milestone.rewardValue,
      rewardProductId: milestone.rewardProductId,
      minOrderAmount: milestone.minOrderAmount,
      isRepeating: milestone.isRepeating,
    });
    setEditModalOpen(true);
  };

  const handleDeleteClick = (milestone) => {
    setSelectedMilestone(milestone);
    setDeleteDialogOpen(true);
  };

  const resetForm = () => {
    setFormData({
      name: '',
      description: '',
      requiredVisits: 10,
      rewardType: 'FREE_ITEM',
      rewardValue: null,
      rewardProductId: null,
      minOrderAmount: null,
      isRepeating: true,
    });
  };

  const getRewardIcon = (type) => {
    const info = REWARD_TYPES.find(r => r.value === type);
    return info ? info.icon : Gift;
  };

  const formatRewardValue = (milestone) => {
    switch (milestone.rewardType) {
      case 'FREE_ITEM':
        return milestone.rewardProductName || t('milestones.rewardTypes.FREE_ITEM');
      case 'DISCOUNT_PERCENTAGE':
        return `${milestone.rewardValue}%`;
      case 'DISCOUNT_FIXED':
        return milestone.rewardValue;
      case 'BONUS_POINTS':
        return `${milestone.rewardValue} ${t('milestones.points')}`;
      default:
        return milestone.rewardValue;
    }
  };

  const renderFormFields = () => (
    <div className="space-y-4 py-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="name">{t('milestones.form.name')} *</Label>
          <Input
            id="name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder={t('milestones.form.namePlaceholder')}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="requiredVisits">{t('milestones.form.requiredVisits')} *</Label>
          <Input
            id="requiredVisits"
            type="number"
            value={formData.requiredVisits}
            onChange={(e) => setFormData({ ...formData, requiredVisits: parseInt(e.target.value) || 1 })}
            min="1"
            required
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">{t('milestones.form.description')}</Label>
        <Textarea
          id="description"
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          placeholder={t('milestones.form.descriptionPlaceholder')}
          rows={2}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="rewardType">{t('milestones.form.rewardType')} *</Label>
          <Select
            value={formData.rewardType}
            onValueChange={(value) => setFormData({ ...formData, rewardType: value, rewardValue: null, rewardProductId: null })}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {REWARD_TYPES.map(type => (
                <SelectItem key={type.value} value={type.value}>
                  {t(`milestones.rewardTypes.${type.value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {formData.rewardType === 'FREE_ITEM' && (
          <div className="space-y-2">
            <Label htmlFor="rewardProduct">{t('milestones.form.rewardProduct')}</Label>
            <Select
              value={formData.rewardProductId?.toString() || ''}
              onValueChange={(value) => setFormData({ ...formData, rewardProductId: value ? Number(value) : null })}
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

        {(formData.rewardType === 'DISCOUNT_PERCENTAGE' || formData.rewardType === 'DISCOUNT_FIXED' || formData.rewardType === 'BONUS_POINTS') && (
          <div className="space-y-2">
            <Label htmlFor="rewardValue">{t('milestones.form.rewardValue')} *</Label>
            <Input
              id="rewardValue"
              type="number"
              value={formData.rewardValue || ''}
              onChange={(e) => setFormData({ ...formData, rewardValue: e.target.value ? parseFloat(e.target.value) : null })}
              min="0"
              max={formData.rewardType === 'DISCOUNT_PERCENTAGE' ? 100 : undefined}
            />
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="minOrderAmount">{t('milestones.form.minOrderAmount')}</Label>
          <Input
            id="minOrderAmount"
            type="number"
            value={formData.minOrderAmount || ''}
            onChange={(e) => setFormData({ ...formData, minOrderAmount: e.target.value ? parseFloat(e.target.value) : null })}
            min="0"
            placeholder={t('milestones.form.minOrderAmountPlaceholder')}
          />
        </div>
        <div className="flex items-center space-x-2 pt-7">
          <input
            type="checkbox"
            id="isRepeating"
            checked={formData.isRepeating}
            onChange={(e) => setFormData({ ...formData, isRepeating: e.target.checked })}
            className="h-4 w-4"
          />
          <Label htmlFor="isRepeating">{t('milestones.form.isRepeating')}</Label>
        </div>
      </div>
    </div>
  );

  if (loading && milestones.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('milestones.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('milestones.subtitle')}</p>
        </div>
        <div className="flex gap-3">
          <Button onClick={() => { resetForm(); setCreateModalOpen(true); }}>
            <Plus className="h-4 w-4 mr-2" />
            {t('milestones.addMilestone')}
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

      {/* Milestones Table */}
      <Card>
        <CardHeader>
          <CardTitle>{t('milestones.listTitle')}</CardTitle>
          <CardDescription>{t('milestones.listDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {milestones.length === 0 ? (
            <div className="text-center py-12">
              <Stamp className="mx-auto h-12 w-12 text-muted-foreground" />
              <h3 className="mt-2 text-sm font-medium">{t('milestones.noMilestones')}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{t('milestones.getStarted')}</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('milestones.table.name')}</TableHead>
                  <TableHead>{t('milestones.table.visits')}</TableHead>
                  <TableHead>{t('milestones.table.reward')}</TableHead>
                  <TableHead>{t('milestones.table.minOrder')}</TableHead>
                  <TableHead>{t('milestones.table.repeating')}</TableHead>
                  <TableHead>{t('milestones.table.status')}</TableHead>
                  <TableHead>{t('common.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {milestones.map(milestone => {
                  const RewardIcon = getRewardIcon(milestone.rewardType);
                  return (
                    <TableRow key={milestone.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <div className="p-2 bg-primary/10 rounded-lg">
                            <Stamp className="w-5 h-5 text-primary" />
                          </div>
                          <div>
                            <div className="font-medium">{milestone.name}</div>
                            <div className="text-sm text-muted-foreground line-clamp-1">{milestone.description}</div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1">
                          <span className="font-semibold text-lg">{milestone.requiredVisits}</span>
                          <span className="text-sm text-muted-foreground">{t('milestones.visits')}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <RewardIcon className="h-4 w-4 text-muted-foreground" />
                          <div>
                            <Badge variant="outline">
                              {t(`milestones.rewardTypes.${milestone.rewardType}`)}
                            </Badge>
                            <div className="text-sm font-medium mt-0.5">
                              {formatRewardValue(milestone)}
                            </div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {milestone.minOrderAmount > 0 ? (
                          <span className="font-medium">{milestone.minOrderAmount}</span>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </TableCell>
                      <TableCell>
                        {milestone.isRepeating ? (
                          <Badge variant="outline" className="gap-1">
                            <Repeat className="h-3 w-3" />
                            {t('milestones.repeating')}
                          </Badge>
                        ) : (
                          <Badge variant="secondary">
                            {t('milestones.oneTime')}
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={milestone.active ? 'default' : 'secondary'}
                          className="cursor-pointer"
                          onClick={() => handleToggle(milestone)}
                        >
                          {milestone.active ? (
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
                            onClick={() => handleEditClick(milestone)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600 hover:text-red-700"
                            onClick={() => handleDeleteClick(milestone)}
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

      {/* Create Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('milestones.createMilestone')}</DialogTitle>
            <DialogDescription>{t('milestones.createDescription')}</DialogDescription>
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
            <DialogTitle>{t('milestones.editMilestone')}</DialogTitle>
            <DialogDescription>{t('milestones.editDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdate}>
            {renderFormFields()}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setEditModalOpen(false); resetForm(); setSelectedMilestone(null); }}>
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
            <DialogTitle>{t('milestones.deleteMilestone')}</DialogTitle>
            <DialogDescription>
              {t('milestones.deleteConfirmation', { name: selectedMilestone?.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteDialogOpen(false); setSelectedMilestone(null); }}>
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
