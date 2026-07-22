import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { workingHoursAPI, restaurantAPI, operatorAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
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
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Textarea } from '../components/ui/textarea';
import {
  Plus,
  Edit,
  Trash2,
  Clock,
  Calendar,
  User,
  Search
} from 'lucide-react';

const DAYS_OF_WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export default function EmployeeSchedule() {
  const { t } = useTranslation();
  const [workingHours, setWorkingHours] = useState([]);
  const [filteredWorkingHours, setFilteredWorkingHours] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [selectedEmployee, setSelectedEmployee] = useState('all');
  const [selectedDay, setSelectedDay] = useState('all');
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedSchedule, setSelectedSchedule] = useState(null);
  const [formData, setFormData] = useState({
    restaurantId: '',
    userId: '',
    dayOfWeek: 'MONDAY',
    startTime: '09:00',
    endTime: '17:00',
    notes: '',
    active: true
  });

  useEffect(() => {
    loadRestaurants();
    loadEmployees();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadWorkingHours();
    }
  }, [selectedRestaurant]);

  useEffect(() => {
    filterWorkingHours();
  }, [searchTerm, selectedEmployee, selectedDay, workingHours]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantList = response.data.data.content || [];
      setRestaurants(restaurantList);
      if (restaurantList.length > 0) {
        setSelectedRestaurant(restaurantList[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadEmployees = async () => {
    try {
      const response = await operatorAPI.getAll({ page: 0, size: 1000 });
      const employeeList = response.data.data.content || [];
      setEmployees(employeeList);
    } catch (error) {
      console.error('Failed to load employees:', error);
    }
  };

  const loadWorkingHours = async () => {
    if (!selectedRestaurant) return;

    setLoading(true);
    try {
      const response = await workingHoursAPI.getByRestaurant(selectedRestaurant);
      setWorkingHours(response.data.data || []);
    } catch (error) {
      console.error('Failed to load working hours:', error);
    } finally {
      setLoading(false);
    }
  };

  const filterWorkingHours = () => {
    let filtered = [...workingHours];

    // Filter by search term
    if (searchTerm) {
      filtered = filtered.filter(wh =>
        wh.userName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        wh.notes?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    // Filter by employee
    if (selectedEmployee !== 'all') {
      filtered = filtered.filter(wh => wh.userId === parseInt(selectedEmployee));
    }

    // Filter by day
    if (selectedDay !== 'all') {
      filtered = filtered.filter(wh => wh.dayOfWeek === selectedDay);
    }

    setFilteredWorkingHours(filtered);
  };

  const handleCreateSchedule = async (e) => {
    e.preventDefault();

    try {
      await workingHoursAPI.create({
        ...formData,
        restaurantId: selectedRestaurant,
        userId: parseInt(formData.userId)
      });
      setCreateModalOpen(false);
      resetForm();
      loadWorkingHours();
    } catch (error) {
      console.error('Failed to create schedule:', error);
      alert(t('schedule.messages.createError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleEditSchedule = async (e) => {
    e.preventDefault();

    try {
      await workingHoursAPI.update(selectedSchedule.id, {
        dayOfWeek: formData.dayOfWeek,
        startTime: formData.startTime,
        endTime: formData.endTime,
        notes: formData.notes,
        active: formData.active
      });
      setEditModalOpen(false);
      resetForm();
      setSelectedSchedule(null);
      loadWorkingHours();
    } catch (error) {
      console.error('Failed to update schedule:', error);
      alert(t('schedule.messages.updateError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleDeleteSchedule = async () => {
    if (!selectedSchedule) return;

    try {
      await workingHoursAPI.delete(selectedSchedule.id);
      setDeleteDialogOpen(false);
      setSelectedSchedule(null);
      loadWorkingHours();
    } catch (error) {
      console.error('Failed to delete schedule:', error);
      alert(t('schedule.messages.deleteError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const openEditModal = (schedule) => {
    setSelectedSchedule(schedule);
    setFormData({
      restaurantId: schedule.restaurantId,
      userId: schedule.userId,
      dayOfWeek: schedule.dayOfWeek,
      startTime: schedule.startTime,
      endTime: schedule.endTime,
      notes: schedule.notes || '',
      active: schedule.active
    });
    setEditModalOpen(true);
  };

  const openDeleteDialog = (schedule) => {
    setSelectedSchedule(schedule);
    setDeleteDialogOpen(true);
  };

  const resetForm = () => {
    setFormData({
      restaurantId: '',
      userId: '',
      dayOfWeek: 'MONDAY',
      startTime: '09:00',
      endTime: '17:00',
      notes: '',
      active: true
    });
  };

  // Group working hours by day and employee
  const groupedByDay = {};
  DAYS_OF_WEEK.forEach(day => {
    groupedByDay[day] = filteredWorkingHours.filter(wh => wh.dayOfWeek === day);
  });

  if (loading && workingHours.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('schedule.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('schedule.subtitle')}
          </p>
        </div>
        <div className="flex gap-3">
          <Button onClick={() => setCreateModalOpen(true)}>
            <Plus className="h-4 w-4 mr-2" />
            {t('schedule.newSchedule')}
          </Button>
          <Select
            value={selectedRestaurant?.toString()}
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
      </div>

      {/* Filters */}
      <div className="flex gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t('schedule.searchPlaceholder')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-10"
          />
        </div>
        <Select value={selectedEmployee} onValueChange={setSelectedEmployee}>
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder={t('schedule.allEmployees')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('schedule.allEmployees')}</SelectItem>
            {employees.map((emp) => (
              <SelectItem key={emp.id} value={emp.id.toString()}>
                {emp.firstName} {emp.lastName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={selectedDay} onValueChange={setSelectedDay}>
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder={t('schedule.allDays')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('schedule.allDays')}</SelectItem>
            {DAYS_OF_WEEK.map((day) => (
              <SelectItem key={day} value={day}>
                {t(`schedule.days.${day}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">
              {t('schedule.stats.totalSchedules')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{workingHours.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">
              {t('schedule.stats.activeEmployees')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {new Set(workingHours.map(wh => wh.userId)).size}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">
              {t('schedule.stats.filteredResults')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{filteredWorkingHours.length}</div>
          </CardContent>
        </Card>
      </div>

      {/* Working Hours Table */}
      {filteredWorkingHours.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-center text-muted-foreground">
              {t('schedule.noSchedules')}
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>{t('schedule.scheduleList')}</CardTitle>
            <CardDescription>{t('schedule.scheduleDescription')}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-left p-3">{t('schedule.employee')}</th>
                    <th className="text-left p-3">{t('schedule.day')}</th>
                    <th className="text-left p-3">{t('schedule.shift')}</th>
                    <th className="text-left p-3">{t('schedule.notes')}</th>
                    <th className="text-left p-3">{t('common.status')}</th>
                    <th className="text-right p-3">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredWorkingHours.map((schedule) => (
                    <tr key={schedule.id} className="border-b hover:bg-muted/50">
                      <td className="p-3">
                        <div className="flex items-center gap-2">
                          <User className="h-4 w-4 text-muted-foreground" />
                          <span className="font-medium">{schedule.userName}</span>
                        </div>
                      </td>
                      <td className="p-3">
                        <div className="flex items-center gap-2">
                          <Calendar className="h-4 w-4 text-muted-foreground" />
                          <span>{t(`schedule.days.${schedule.dayOfWeek}`)}</span>
                        </div>
                      </td>
                      <td className="p-3">
                        <div className="flex items-center gap-2">
                          <Clock className="h-4 w-4 text-muted-foreground" />
                          <span>{schedule.startTime} - {schedule.endTime}</span>
                        </div>
                      </td>
                      <td className="p-3">
                        <span className="text-sm text-muted-foreground">
                          {schedule.notes || '-'}
                        </span>
                      </td>
                      <td className="p-3">
                        <Badge variant={schedule.active ? 'default' : 'secondary'}>
                          {schedule.active ? t('common.active') : t('common.inactive')}
                        </Badge>
                      </td>
                      <td className="p-3">
                        <div className="flex justify-end gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => openEditModal(schedule)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            onClick={() => openDeleteDialog(schedule)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Create Schedule Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('schedule.createSchedule')}</DialogTitle>
            <DialogDescription>
              {t('schedule.createDescription')}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateSchedule}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="userId">{t('schedule.employee')} *</Label>
                <Select
                  value={formData.userId.toString()}
                  onValueChange={(value) => setFormData({ ...formData, userId: value })}
                  required
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t('schedule.selectEmployee')} />
                  </SelectTrigger>
                  <SelectContent>
                    {employees.map((emp) => (
                      <SelectItem key={emp.id} value={emp.id.toString()}>
                        {emp.firstName} {emp.lastName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="dayOfWeek">{t('schedule.day')} *</Label>
                <Select
                  value={formData.dayOfWeek}
                  onValueChange={(value) => setFormData({ ...formData, dayOfWeek: value })}
                  required
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DAYS_OF_WEEK.map((day) => (
                      <SelectItem key={day} value={day}>
                        {t(`schedule.days.${day}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="startTime">{t('schedule.startTime')} *</Label>
                  <Input
                    id="startTime"
                    type="time"
                    value={formData.startTime}
                    onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="endTime">{t('schedule.endTime')} *</Label>
                  <Input
                    id="endTime"
                    type="time"
                    value={formData.endTime}
                    onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="notes">{t('schedule.notes')}</Label>
                <Textarea
                  id="notes"
                  value={formData.notes}
                  onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                  rows={3}
                  placeholder={t('schedule.notesPlaceholder')}
                />
              </div>

              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="active"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="active">{t('schedule.active')}</Label>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateModalOpen(false); resetForm(); }}>
                {t('common.cancel')}
              </Button>
              <Button type="submit">
                {t('common.create')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Edit Schedule Modal */}
      <Dialog open={editModalOpen} onOpenChange={setEditModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('schedule.editSchedule')}</DialogTitle>
            <DialogDescription>
              {t('schedule.updateDescription')}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleEditSchedule}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="edit-dayOfWeek">{t('schedule.day')} *</Label>
                <Select
                  value={formData.dayOfWeek}
                  onValueChange={(value) => setFormData({ ...formData, dayOfWeek: value })}
                  required
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DAYS_OF_WEEK.map((day) => (
                      <SelectItem key={day} value={day}>
                        {t(`schedule.days.${day}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="edit-startTime">{t('schedule.startTime')} *</Label>
                  <Input
                    id="edit-startTime"
                    type="time"
                    value={formData.startTime}
                    onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="edit-endTime">{t('schedule.endTime')} *</Label>
                  <Input
                    id="edit-endTime"
                    type="time"
                    value={formData.endTime}
                    onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-notes">{t('schedule.notes')}</Label>
                <Textarea
                  id="edit-notes"
                  value={formData.notes}
                  onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                  rows={3}
                  placeholder={t('schedule.notesPlaceholder')}
                />
              </div>

              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="edit-active"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="edit-active">{t('schedule.active')}</Label>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setEditModalOpen(false); resetForm(); setSelectedSchedule(null); }}>
                {t('common.cancel')}
              </Button>
              <Button type="submit">
                {t('common.save')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('schedule.deleteSchedule')}</DialogTitle>
            <DialogDescription>
              {t('schedule.confirmDelete')}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteDialogOpen(false); setSelectedSchedule(null); }}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDeleteSchedule}>
              {t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
