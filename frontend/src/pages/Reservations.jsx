import { useEffect, useState, useRef, useCallback } from 'react';
import { notifyError, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { reservationAPI, tablesAPI } from '../services/api';
import { useCurrentRestaurantId } from '../utils/restaurant';
import { useNotificationStore } from '../store/notificationStore';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '../components/ui/dialog';
import {
  Calendar,
  Clock,
  Users,
  Phone,
  Mail,
  CheckCircle,
  XCircle,
  UserCheck,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Search,
  Table2,
  Plus,
  MessageSquare,
  Settings,
  ToggleLeft,
  ToggleRight,
  Bell,
} from 'lucide-react';
import { format, startOfMonth, endOfMonth, eachDayOfInterval, isSameDay, addMonths, subMonths, isToday, parseISO } from 'date-fns';

const statusColors = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  CONFIRMED: 'bg-green-100 text-green-800',
  DEPOSIT_PENDING: 'bg-orange-100 text-orange-800',
  SEATED: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-gray-100 text-gray-800',
  CANCELLED: 'bg-red-100 text-red-800',
  NO_SHOW: 'bg-red-100 text-red-800',
};

const statusIcons = {
  PENDING: Clock,
  CONFIRMED: CheckCircle,
  DEPOSIT_PENDING: AlertTriangle,
  SEATED: UserCheck,
  COMPLETED: CheckCircle,
  CANCELLED: XCircle,
  NO_SHOW: AlertTriangle,
};

export default function Reservations() {
  const { t } = useTranslation();
  const [reservations, setReservations] = useState([]);
  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(true);
  const [newReservationAlert, setNewReservationAlert] = useState(null);
  const previousReservationIds = useRef(new Set());
  const { clearUnreadReservations, addUnreadReservation } = useNotificationStore();

  // Clear unread count when user visits this page
  useEffect(() => {
    clearUnreadReservations();
  }, [clearUnreadReservations]);
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [selectedReservation, setSelectedReservation] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showAssignTableModal, setShowAssignTableModal] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [creating, setCreating] = useState(false);
  const [createForm, setCreateForm] = useState({
    customerName: '',
    customerPhone: '',
    reservationDate: format(new Date(), 'yyyy-MM-dd'),
    reservationTime: '12:00',
    partySize: 2,
    tableId: null,
    specialRequests: '',
  });
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [settings, setSettings] = useState(null);
  const [settingsLoading, setSettingsLoading] = useState(false);
  const [savingSettings, setSavingSettings] = useState(false);

  const restaurantId = useCurrentRestaurantId();

  useEffect(() => {
    if (!restaurantId) return;
    loadReservations();
    loadTables();
  }, [currentMonth, restaurantId]);

  // Auto-refresh reservations every 30 seconds for real-time updates
  useEffect(() => {
    const interval = setInterval(() => {
      loadReservationsQuietly();
    }, 30000);

    return () => clearInterval(interval);
  }, [currentMonth]);

  // Silent reload without loading state (for polling)
  const loadReservationsQuietly = useCallback(async () => {
    try {
      const startDate = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
      const endDate = format(endOfMonth(currentMonth), 'yyyy-MM-dd');
      const response = await reservationAPI.getByDateRange(restaurantId, startDate, endDate);
      const newReservations = response.data.data || [];

      // Detect new reservations
      const currentIds = new Set(newReservations.map(r => r.id));
      const newOnes = newReservations.filter(r => !previousReservationIds.current.has(r.id));

      if (newOnes.length > 0 && previousReservationIds.current.size > 0) {
        // Show alert for new reservation
        const newest = newOnes[0];
        setNewReservationAlert({
          id: newest.id,
          customerName: newest.customerName,
          reservationDate: newest.reservationDate,
          reservationTime: newest.reservationTime,
          partySize: newest.partySize,
        });

        // Add to unread count (for nav badge)
        addUnreadReservation();

        // Play notification sound
        try {
          const audio = new Audio('/notification.mp3');
          audio.volume = 0.5;
          audio.play().catch(() => {});
        } catch (_e) { /* audio not supported */ }

        // Show browser notification if permitted
        if (Notification.permission === 'granted') {
          new Notification(t('reservations.newReservation', 'New Reservation'), {
            body: `${newest.customerName} - ${newest.reservationDate} ${newest.reservationTime?.slice(0, 5)} (${newest.partySize} guests)`,
            icon: '/favicon.ico',
          });
        }

        // Auto-dismiss alert after 10 seconds
        setTimeout(() => setNewReservationAlert(null), 10000);
      }

      previousReservationIds.current = currentIds;
      setReservations(newReservations);
    } catch (error) {
      console.error('Failed to refresh reservations:', error);
    }
  }, [currentMonth, t]);

  // Request notification permission on mount
  useEffect(() => {
    if (Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }, []);

  const loadReservations = async () => {
    setLoading(true);
    try {
      const startDate = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
      const endDate = format(endOfMonth(currentMonth), 'yyyy-MM-dd');
      const response = await reservationAPI.getByDateRange(restaurantId, startDate, endDate);
      const data = response.data.data || [];
      setReservations(data);
      // Initialize the set with existing IDs to avoid false alerts on first load
      previousReservationIds.current = new Set(data.map(r => r.id));
    } catch (error) {
      console.error('Failed to load reservations:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadTables = async () => {
    try {
      const response = await tablesAPI.getAll(restaurantId);
      setTables(response.data.data || []);
    } catch (error) {
      console.error('Failed to load tables:', error);
    }
  };

  const getReservationsForDate = (date) => {
    const dateStr = format(date, 'yyyy-MM-dd');
    return reservations.filter(r => r.reservationDate === dateStr);
  };

  const filteredReservations = getReservationsForDate(selectedDate).filter(r => {
    if (searchTerm) {
      const search = searchTerm.toLowerCase();
      if (!r.customerName?.toLowerCase().includes(search) &&
          !r.customerPhone?.toLowerCase().includes(search) &&
          !r.confirmationCode?.toLowerCase().includes(search)) {
        return false;
      }
    }
    if (statusFilter !== 'all' && r.status !== statusFilter) {
      return false;
    }
    return true;
  });

  const handleConfirm = async (id) => {
    try {
      await reservationAPI.confirm(id);
      loadReservations();
    } catch (error) {
      console.error('Failed to confirm reservation:', error);
    }
  };

  const handleCancel = async (id) => {
    if (!confirm('Are you sure you want to cancel this reservation?')) return;
    try {
      await reservationAPI.cancel(id, 'Cancelled by staff');
      loadReservations();
      setShowDetailsModal(false);
    } catch (error) {
      console.error('Failed to cancel reservation:', error);
    }
  };

  const handleCheckIn = async (id) => {
    try {
      await reservationAPI.checkIn(id);
      loadReservations();
      setShowDetailsModal(false);
    } catch (error) {
      console.error('Failed to check in:', error);
    }
  };

  const handleComplete = async (id) => {
    try {
      await reservationAPI.complete(id);
      loadReservations();
      setShowDetailsModal(false);
    } catch (error) {
      console.error('Failed to complete reservation:', error);
    }
  };

  const handleNoShow = async (id) => {
    if (!confirm('Mark this reservation as no-show?')) return;
    try {
      await reservationAPI.markNoShow(id);
      loadReservations();
      setShowDetailsModal(false);
    } catch (error) {
      console.error('Failed to mark no-show:', error);
    }
  };

  const handleAssignTable = async (reservationId, tableId) => {
    try {
      await reservationAPI.assignTable(reservationId, tableId);
      loadReservations();
      setShowAssignTableModal(false);
    } catch (error) {
      console.error('Failed to assign table:', error);
    }
  };

  const handleCreateReservation = async (e) => {
    e.preventDefault();
    if (!createForm.customerName || !createForm.customerPhone) {
      notifyWarning('Please fill in required fields');
      return;
    }
    setCreating(true);
    try {
      await reservationAPI.create(restaurantId, {
        customerName: createForm.customerName,
        customerPhone: createForm.customerPhone,
        reservationDate: createForm.reservationDate,
        reservationTime: createForm.reservationTime,
        partySize: parseInt(createForm.partySize),
        tableId: createForm.tableId || null,
        specialRequests: createForm.specialRequests || null,
      });
      loadReservations();
      setShowCreateModal(false);
      // Reset form
      setCreateForm({
        customerName: '',
        customerPhone: '',
        reservationDate: format(new Date(), 'yyyy-MM-dd'),
        reservationTime: '12:00',
        partySize: 2,
        tableId: null,
        specialRequests: '',
      });
      // Select the date of the new reservation
      setSelectedDate(parseISO(createForm.reservationDate));
    } catch (error) {
      console.error('Failed to create reservation:', error);
      notifyError(error);
    } finally {
      setCreating(false);
    }
  };

  const openCreateModal = () => {
    setCreateForm(prev => ({
      ...prev,
      reservationDate: format(selectedDate, 'yyyy-MM-dd'),
    }));
    setShowCreateModal(true);
  };

  const loadSettings = async () => {
    setSettingsLoading(true);
    try {
      const response = await reservationAPI.getSettings(restaurantId);
      setSettings(response.data.data);
    } catch (error) {
      console.error('Failed to load settings:', error);
    } finally {
      setSettingsLoading(false);
    }
  };

  const openSettingsModal = () => {
    loadSettings();
    setShowSettingsModal(true);
  };

  const handleSaveSettings = async () => {
    setSavingSettings(true);
    try {
      const response = await reservationAPI.updateSettings(restaurantId, settings);
      setSettings(response.data.data);
      setShowSettingsModal(false);
    } catch (error) {
      console.error('Failed to save settings:', error);
      notifyError(error);
    } finally {
      setSavingSettings(false);
    }
  };

  const updateSetting = (key, value) => {
    setSettings(prev => ({ ...prev, [key]: value }));
  };

  const daysInMonth = eachDayOfInterval({
    start: startOfMonth(currentMonth),
    end: endOfMonth(currentMonth),
  });

  const StatusIcon = ({ status }) => {
    const Icon = statusIcons[status] || Clock;
    return <Icon className="h-4 w-4" />;
  };

  if (loading) {
    return <div className="flex items-center justify-center h-96">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('reservations.title', 'Reservations')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('reservations.subtitle', 'Manage table reservations and bookings')}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={openSettingsModal}>
            <Settings className="h-4 w-4 mr-2" />
            {t('reservations.settings', 'Settings')}
          </Button>
          <Button onClick={openCreateModal}>
            <Plus className="h-4 w-4 mr-2" />
            {t('reservations.newReservation', 'New Reservation')}
          </Button>
        </div>
      </div>

      {/* New Reservation Alert */}
      {newReservationAlert && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 animate-pulse">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="bg-green-100 rounded-full p-2">
                <Bell className="h-5 w-5 text-green-600" />
              </div>
              <div>
                <div className="font-medium text-green-800">
                  {t('reservations.newReservationReceived', 'New Reservation Received!')}
                </div>
                <div className="text-sm text-green-600">
                  {newReservationAlert.customerName} - {newReservationAlert.reservationDate} {newReservationAlert.reservationTime?.slice(0, 5)} ({newReservationAlert.partySize} {t('reservations.guests', 'guests')})
                </div>
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setNewReservationAlert(null)}
              className="text-green-600 hover:text-green-800"
            >
              <XCircle className="h-5 w-5" />
            </Button>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Calendar */}
        <div className="bg-white rounded-lg border shadow-sm p-4">
          <div className="flex items-center justify-between mb-4">
            <Button variant="ghost" size="sm" onClick={() => setCurrentMonth(subMonths(currentMonth, 1))}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <h3 className="font-semibold">{format(currentMonth, 'MMMM yyyy')}</h3>
            <Button variant="ghost" size="sm" onClick={() => setCurrentMonth(addMonths(currentMonth, 1))}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>

          <div className="grid grid-cols-7 gap-1 text-center text-xs text-gray-500 mb-2">
            {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(day => (
              <div key={day} className="py-1">{day}</div>
            ))}
          </div>

          <div className="grid grid-cols-7 gap-1">
            {/* Empty cells for days before start of month */}
            {Array.from({ length: startOfMonth(currentMonth).getDay() }).map((_, i) => (
              <div key={`empty-${i}`} className="h-10" />
            ))}

            {daysInMonth.map(day => {
              const dayReservations = getReservationsForDate(day);
              const hasReservations = dayReservations.length > 0;
              const isSelected = isSameDay(day, selectedDate);

              return (
                <button
                  key={day.toISOString()}
                  onClick={() => setSelectedDate(day)}
                  className={`h-10 rounded-lg text-sm relative transition-colors ${
                    isSelected
                      ? 'bg-blue-600 text-white'
                      : isToday(day)
                      ? 'bg-blue-100 text-blue-800'
                      : 'hover:bg-gray-100'
                  }`}
                >
                  {format(day, 'd')}
                  {hasReservations && (
                    <span className={`absolute bottom-1 left-1/2 -translate-x-1/2 w-1.5 h-1.5 rounded-full ${
                      isSelected ? 'bg-white' : 'bg-blue-600'
                    }`} />
                  )}
                </button>
              );
            })}
          </div>

          {/* Today button */}
          <Button
            variant="outline"
            size="sm"
            className="w-full mt-4"
            onClick={() => {
              setCurrentMonth(new Date());
              setSelectedDate(new Date());
            }}
          >
            {t('common.today', 'Today')}
          </Button>
        </div>

        {/* Reservations List */}
        <div className="lg:col-span-2 bg-white rounded-lg border shadow-sm">
          <div className="p-4 border-b">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold flex items-center gap-2">
                <Calendar className="h-5 w-5" />
                {format(selectedDate, 'EEEE, MMMM d, yyyy')}
              </h3>
              <span className="text-sm text-gray-500">
                {filteredReservations.length} {t('reservations.reservations', 'reservations')}
              </span>
            </div>

            <div className="flex gap-2">
              <div className="flex-1 relative">
                <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                <Input
                  placeholder={t('common.search', 'Search...')}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="px-3 py-2 border rounded-md text-sm"
              >
                <option value="all">{t('common.all', 'All')}</option>
                <option value="PENDING">{t('reservations.status.pending', 'Pending')}</option>
                <option value="CONFIRMED">{t('reservations.status.confirmed', 'Confirmed')}</option>
                <option value="SEATED">{t('reservations.status.seated', 'Seated')}</option>
                <option value="COMPLETED">{t('reservations.status.completed', 'Completed')}</option>
                <option value="CANCELLED">{t('reservations.status.cancelled', 'Cancelled')}</option>
                <option value="NO_SHOW">{t('reservations.status.noShow', 'No-Show')}</option>
              </select>
            </div>
          </div>

          <div className="divide-y max-h-[600px] overflow-y-auto">
            {filteredReservations.length === 0 ? (
              <div className="p-8 text-center text-gray-500">
                <Calendar className="h-12 w-12 mx-auto mb-3 text-gray-300" />
                <p>{t('reservations.noReservations', 'No reservations for this date')}</p>
              </div>
            ) : (
              filteredReservations
                .sort((a, b) => a.reservationTime.localeCompare(b.reservationTime))
                .map(reservation => (
                  <div
                    key={reservation.id}
                    className="p-4 hover:bg-gray-50 cursor-pointer transition-colors"
                    onClick={() => {
                      setSelectedReservation(reservation);
                      setShowDetailsModal(true);
                    }}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex items-start gap-3">
                        <div className="bg-blue-50 rounded-lg p-2 text-center min-w-[60px]">
                          <div className="text-lg font-bold text-blue-600">
                            {reservation.reservationTime?.slice(0, 5)}
                          </div>
                        </div>
                        <div>
                          <div className="font-medium">{reservation.customerName}</div>
                          <div className="text-sm text-gray-500 flex items-center gap-2">
                            <Users className="h-3 w-3" />
                            {reservation.partySize} {t('reservations.guests', 'guests')}
                            {(reservation.tableNumber || reservation.tableName) && (
                              <>
                                <span>•</span>
                                <Table2 className="h-3 w-3" />
                                #{reservation.tableNumber}{reservation.tableName && ` - ${reservation.tableName}`}
                              </>
                            )}
                          </div>
                          <div className="text-xs text-gray-400 mt-1">
                            {reservation.confirmationCode}
                          </div>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium ${statusColors[reservation.status]}`}>
                          <StatusIcon status={reservation.status} />
                          {reservation.status}
                        </span>
                        {reservation.status === 'PENDING' && (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleConfirm(reservation.id);
                            }}
                          >
                            {t('reservations.confirm', 'Confirm')}
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                ))
            )}
          </div>
        </div>
      </div>

      {/* Reservation Details Modal */}
      <Dialog open={showDetailsModal} onOpenChange={setShowDetailsModal}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('reservations.details', 'Reservation Details')}</DialogTitle>
          </DialogHeader>
          {selectedReservation && (
            <div className="space-y-4">
              <div className="bg-gray-50 rounded-lg p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium ${statusColors[selectedReservation.status]}`}>
                    <StatusIcon status={selectedReservation.status} />
                    {selectedReservation.status}
                  </span>
                  <code className="text-sm bg-white px-2 py-1 rounded">
                    {selectedReservation.confirmationCode}
                  </code>
                </div>
                <div className="text-2xl font-bold">
                  {selectedReservation.reservationTime?.slice(0, 5)}
                </div>
                <div className="text-gray-500">
                  {selectedReservation.reservationDate}
                </div>
              </div>

              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <Users className="h-5 w-5 text-gray-400" />
                  <div>
                    <div className="font-medium">{selectedReservation.customerName}</div>
                    <div className="text-sm text-gray-500">
                      {selectedReservation.partySize} {t('reservations.guests', 'guests')}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <Phone className="h-5 w-5 text-gray-400" />
                  <span>{selectedReservation.customerPhone}</span>
                </div>

                {selectedReservation.customerEmail && (
                  <div className="flex items-center gap-3">
                    <Mail className="h-5 w-5 text-gray-400" />
                    <span>{selectedReservation.customerEmail}</span>
                  </div>
                )}

                {(selectedReservation.tableNumber || selectedReservation.tableName) ? (
                  <div className="flex items-center gap-3">
                    <Table2 className="h-5 w-5 text-gray-400" />
                    <span>
                      #{selectedReservation.tableNumber}
                      {selectedReservation.tableName && ` - ${selectedReservation.tableName}`}
                    </span>
                  </div>
                ) : (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setShowAssignTableModal(true)}
                  >
                    <Table2 className="h-4 w-4 mr-2" />
                    {t('reservations.assignTable', 'Assign Table')}
                  </Button>
                )}

                {selectedReservation.specialRequests && (
                  <div className="bg-yellow-50 p-3 rounded-lg">
                    <div className="text-sm font-medium text-yellow-800">
                      {t('reservations.specialRequests', 'Special Requests')}
                    </div>
                    <div className="text-sm text-yellow-700">
                      {selectedReservation.specialRequests}
                    </div>
                  </div>
                )}
              </div>

              <div className="flex flex-wrap gap-2 pt-4 border-t">
                {selectedReservation.status === 'PENDING' && (
                  <>
                    <Button onClick={() => handleConfirm(selectedReservation.id)}>
                      <CheckCircle className="h-4 w-4 mr-2" />
                      {t('reservations.confirm', 'Confirm')}
                    </Button>
                    <Button variant="destructive" onClick={() => handleCancel(selectedReservation.id)}>
                      <XCircle className="h-4 w-4 mr-2" />
                      {t('common.cancel', 'Cancel')}
                    </Button>
                  </>
                )}
                {selectedReservation.status === 'CONFIRMED' && (
                  <>
                    <Button onClick={() => handleCheckIn(selectedReservation.id)}>
                      <UserCheck className="h-4 w-4 mr-2" />
                      {t('reservations.checkIn', 'Check In')}
                    </Button>
                    <Button variant="outline" onClick={() => handleNoShow(selectedReservation.id)}>
                      <AlertTriangle className="h-4 w-4 mr-2" />
                      {t('reservations.noShow', 'No-Show')}
                    </Button>
                    <Button variant="destructive" onClick={() => handleCancel(selectedReservation.id)}>
                      <XCircle className="h-4 w-4 mr-2" />
                      {t('common.cancel', 'Cancel')}
                    </Button>
                  </>
                )}
                {selectedReservation.status === 'SEATED' && (
                  <Button onClick={() => handleComplete(selectedReservation.id)}>
                    <CheckCircle className="h-4 w-4 mr-2" />
                    {t('reservations.complete', 'Complete')}
                  </Button>
                )}
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Assign Table Modal */}
      <Dialog open={showAssignTableModal} onOpenChange={setShowAssignTableModal}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('reservations.assignTable', 'Assign Table')}</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-2">
            {tables.map(table => (
              <Button
                key={table.id}
                variant="outline"
                className="h-auto py-3 flex-col"
                onClick={() => handleAssignTable(selectedReservation?.id, table.id)}
              >
                <Table2 className="h-5 w-5 mb-1" />
                <span className="font-medium">{table.name}</span>
                <span className="text-xs text-gray-500">
                  {table.capacity} {t('reservations.seats', 'seats')}
                </span>
              </Button>
            ))}
          </div>
        </DialogContent>
      </Dialog>

      {/* Create Reservation Modal */}
      <Dialog open={showCreateModal} onOpenChange={setShowCreateModal}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('reservations.newReservation', 'New Reservation')}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleCreateReservation} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('reservations.customerName', 'Customer Name')} *
                </label>
                <Input
                  required
                  value={createForm.customerName}
                  onChange={(e) => setCreateForm({ ...createForm, customerName: e.target.value })}
                  placeholder="John Doe"
                />
              </div>

              <div className="col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <Phone className="inline h-4 w-4 mr-1" />
                  {t('reservations.phone', 'Phone')} *
                </label>
                <Input
                  required
                  type="tel"
                  value={createForm.customerPhone}
                  onChange={(e) => setCreateForm({ ...createForm, customerPhone: e.target.value })}
                  placeholder="+998 90 123 45 67"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <Calendar className="inline h-4 w-4 mr-1" />
                  {t('reservations.date', 'Date')} *
                </label>
                <Input
                  required
                  type="date"
                  value={createForm.reservationDate}
                  onChange={(e) => setCreateForm({ ...createForm, reservationDate: e.target.value })}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <Clock className="inline h-4 w-4 mr-1" />
                  {t('reservations.time', 'Time')} *
                </label>
                <Input
                  required
                  type="time"
                  value={createForm.reservationTime}
                  onChange={(e) => setCreateForm({ ...createForm, reservationTime: e.target.value })}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <Users className="inline h-4 w-4 mr-1" />
                  {t('reservations.partySize', 'Party Size')} *
                </label>
                <Input
                  required
                  type="number"
                  min="1"
                  max="50"
                  value={createForm.partySize}
                  onChange={(e) => setCreateForm({ ...createForm, partySize: e.target.value })}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <Table2 className="inline h-4 w-4 mr-1" />
                  {t('reservations.table', 'Table')}
                </label>
                <select
                  value={createForm.tableId || ''}
                  onChange={(e) => setCreateForm({ ...createForm, tableId: e.target.value ? parseInt(e.target.value) : null })}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  <option value="">{t('reservations.noTable', 'No table assigned')}</option>
                  {tables.map(table => (
                    <option key={table.id} value={table.id}>
                      #{table.tableNumber} - {table.name} ({table.capacity} seats)
                    </option>
                  ))}
                </select>
              </div>

              <div className="col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <MessageSquare className="inline h-4 w-4 mr-1" />
                  {t('reservations.specialRequests', 'Special Requests')}
                </label>
                <textarea
                  value={createForm.specialRequests}
                  onChange={(e) => setCreateForm({ ...createForm, specialRequests: e.target.value })}
                  rows={2}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                  placeholder="Any special requests or notes..."
                />
              </div>
            </div>

            <div className="flex gap-2 pt-4 border-t">
              <Button type="button" variant="outline" className="flex-1" onClick={() => setShowCreateModal(false)}>
                {t('common.cancel', 'Cancel')}
              </Button>
              <Button type="submit" className="flex-1" disabled={creating}>
                {creating ? (
                  <>{t('common.creating', 'Creating...')}</>
                ) : (
                  <>
                    <Plus className="h-4 w-4 mr-2" />
                    {t('reservations.create', 'Create')}
                  </>
                )}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Settings Modal */}
      <Dialog open={showSettingsModal} onOpenChange={setShowSettingsModal}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Settings className="h-5 w-5" />
              {t('reservations.settingsTitle', 'Reservation Settings')}
            </DialogTitle>
          </DialogHeader>

          {settingsLoading ? (
            <div className="py-8 text-center text-gray-500">
              {t('common.loading', 'Loading...')}
            </div>
          ) : settings ? (
            <div className="space-y-6">
              {/* Enable/Disable Reservations */}
              <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
                <div>
                  <div className="font-medium">{t('reservations.enableOnlineReservations', 'Online Reservations')}</div>
                  <div className="text-sm text-gray-500">
                    {t('reservations.enableDescription', 'Allow customers to make reservations online')}
                  </div>
                </div>
                <button
                  onClick={() => updateSetting('enabled', !settings.enabled)}
                  className={`p-1 rounded-full transition-colors ${settings.enabled ? 'text-green-600' : 'text-gray-400'}`}
                >
                  {settings.enabled ? (
                    <ToggleRight className="h-8 w-8" />
                  ) : (
                    <ToggleLeft className="h-8 w-8" />
                  )}
                </button>
              </div>

              {/* Booking Window */}
              <div className="border rounded-lg p-4 space-y-4">
                <h4 className="font-medium text-gray-900">{t('reservations.bookingWindow', 'Booking Window')}</h4>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('reservations.advanceDays', 'Advance Days')}
                    </label>
                    <Input
                      type="number"
                      min="1"
                      max="365"
                      value={settings.advanceDays || ''}
                      onChange={(e) => updateSetting('advanceDays', parseInt(e.target.value) || null)}
                    />
                    <p className="text-xs text-gray-500 mt-1">
                      {t('reservations.advanceDaysDesc', 'How many days in advance customers can book')}
                    </p>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('reservations.minAdvanceHours', 'Min Advance Hours')}
                    </label>
                    <Input
                      type="number"
                      min="0"
                      max="72"
                      value={settings.minAdvanceHours || ''}
                      onChange={(e) => updateSetting('minAdvanceHours', parseInt(e.target.value) || null)}
                    />
                    <p className="text-xs text-gray-500 mt-1">
                      {t('reservations.minAdvanceHoursDesc', 'Minimum hours before reservation time')}
                    </p>
                  </div>
                </div>
              </div>

              {/* Time Slots */}
              <div className="border rounded-lg p-4 space-y-4">
                <h4 className="font-medium text-gray-900">{t('reservations.timeSlots', 'Time Slots')}</h4>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('reservations.slotDuration', 'Slot Duration (minutes)')}
                    </label>
                    <select
                      value={settings.slotDurationMinutes || 60}
                      onChange={(e) => updateSetting('slotDurationMinutes', parseInt(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md text-sm"
                    >
                      <option value={15}>15 {t('common.minutes', 'minutes')}</option>
                      <option value={30}>30 {t('common.minutes', 'minutes')}</option>
                      <option value={45}>45 {t('common.minutes', 'minutes')}</option>
                      <option value={60}>60 {t('common.minutes', 'minutes')}</option>
                      <option value={90}>90 {t('common.minutes', 'minutes')}</option>
                      <option value={120}>120 {t('common.minutes', 'minutes')}</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('reservations.maxPerSlot', 'Max Reservations Per Slot')}
                    </label>
                    <Input
                      type="number"
                      min="1"
                      max="100"
                      value={settings.maxReservationsPerSlot || ''}
                      onChange={(e) => updateSetting('maxReservationsPerSlot', parseInt(e.target.value) || null)}
                    />
                  </div>
                </div>
              </div>

              {/* Party Size */}
              <div className="border rounded-lg p-4 space-y-4">
                <h4 className="font-medium text-gray-900">{t('reservations.partySizeTitle', 'Party Size Limits')}</h4>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('reservations.minPartySize', 'Minimum Party Size')}
                    </label>
                    <Input
                      type="number"
                      min="1"
                      max="50"
                      value={settings.minPartySize || ''}
                      onChange={(e) => updateSetting('minPartySize', parseInt(e.target.value) || null)}
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      {t('reservations.maxPartySize', 'Maximum Party Size')}
                    </label>
                    <Input
                      type="number"
                      min="1"
                      max="100"
                      value={settings.maxPartySize || ''}
                      onChange={(e) => updateSetting('maxPartySize', parseInt(e.target.value) || null)}
                    />
                  </div>
                </div>
              </div>

              {/* Confirmation & Reminders */}
              <div className="border rounded-lg p-4 space-y-4">
                <h4 className="font-medium text-gray-900">{t('reservations.confirmationTitle', 'Confirmation & Reminders')}</h4>

                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="font-medium text-sm">{t('reservations.autoConfirm', 'Auto-Confirm Reservations')}</div>
                      <div className="text-xs text-gray-500">
                        {t('reservations.autoConfirmDesc', 'Automatically confirm reservations without manual approval')}
                      </div>
                    </div>
                    <button
                      onClick={() => updateSetting('autoConfirm', !settings.autoConfirm)}
                      className={`p-1 rounded-full transition-colors ${settings.autoConfirm ? 'text-green-600' : 'text-gray-400'}`}
                    >
                      {settings.autoConfirm ? <ToggleRight className="h-6 w-6" /> : <ToggleLeft className="h-6 w-6" />}
                    </button>
                  </div>

                  <div className="flex items-center justify-between">
                    <div>
                      <div className="font-medium text-sm">{t('reservations.sendReminders', 'Send Reminders')}</div>
                      <div className="text-xs text-gray-500">
                        {t('reservations.sendRemindersDesc', 'Send reminder notifications to customers')}
                      </div>
                    </div>
                    <button
                      onClick={() => updateSetting('sendReminders', !settings.sendReminders)}
                      className={`p-1 rounded-full transition-colors ${settings.sendReminders ? 'text-green-600' : 'text-gray-400'}`}
                    >
                      {settings.sendReminders ? <ToggleRight className="h-6 w-6" /> : <ToggleLeft className="h-6 w-6" />}
                    </button>
                  </div>

                  {settings.sendReminders && (
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {t('reservations.reminderHours', 'Reminder Hours Before')}
                      </label>
                      <Input
                        type="number"
                        min="1"
                        max="72"
                        value={settings.reminderHoursBefore || ''}
                        onChange={(e) => updateSetting('reminderHoursBefore', parseInt(e.target.value) || null)}
                      />
                    </div>
                  )}
                </div>
              </div>

              {/* Cancellation */}
              <div className="border rounded-lg p-4 space-y-4">
                <h4 className="font-medium text-gray-900">{t('reservations.cancellationTitle', 'Cancellation Policy')}</h4>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    {t('reservations.cancellationHours', 'Cancellation Deadline (hours before)')}
                  </label>
                  <Input
                    type="number"
                    min="0"
                    max="72"
                    value={settings.cancellationHours || ''}
                    onChange={(e) => updateSetting('cancellationHours', parseInt(e.target.value) || null)}
                  />
                  <p className="text-xs text-gray-500 mt-1">
                    {t('reservations.cancellationHoursDesc', 'Hours before reservation when cancellation is still allowed')}
                  </p>
                </div>
              </div>

              {/* Deposit */}
              <div className="border rounded-lg p-4 space-y-4">
                <div className="flex items-center justify-between">
                  <h4 className="font-medium text-gray-900">{t('reservations.depositTitle', 'Deposit')}</h4>
                  <button
                    onClick={() => updateSetting('depositRequired', !settings.depositRequired)}
                    className={`p-1 rounded-full transition-colors ${settings.depositRequired ? 'text-green-600' : 'text-gray-400'}`}
                  >
                    {settings.depositRequired ? <ToggleRight className="h-6 w-6" /> : <ToggleLeft className="h-6 w-6" />}
                  </button>
                </div>

                {settings.depositRequired && (
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {t('reservations.depositAmount', 'Fixed Amount')}
                      </label>
                      <Input
                        type="number"
                        min="0"
                        step="0.01"
                        value={settings.depositAmount || ''}
                        onChange={(e) => updateSetting('depositAmount', parseFloat(e.target.value) || null)}
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {t('reservations.depositPercent', 'Or Percentage (%)')}
                      </label>
                      <Input
                        type="number"
                        min="0"
                        max="100"
                        value={settings.depositPercent || ''}
                        onChange={(e) => updateSetting('depositPercent', parseFloat(e.target.value) || null)}
                      />
                    </div>
                  </div>
                )}
              </div>

              {/* Notes for Customers */}
              <div className="border rounded-lg p-4 space-y-4">
                <h4 className="font-medium text-gray-900">{t('reservations.notesTitle', 'Notes for Customers')}</h4>
                <textarea
                  value={settings.notesForCustomers || ''}
                  onChange={(e) => updateSetting('notesForCustomers', e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                  placeholder={t('reservations.notesPlaceholder', 'Special instructions or policies to display to customers...')}
                />
              </div>

              {/* Save Button */}
              <div className="flex gap-2 pt-4 border-t">
                <Button variant="outline" className="flex-1" onClick={() => setShowSettingsModal(false)}>
                  {t('common.cancel', 'Cancel')}
                </Button>
                <Button className="flex-1" onClick={handleSaveSettings} disabled={savingSettings}>
                  {savingSettings ? t('common.saving', 'Saving...') : t('common.save', 'Save Settings')}
                </Button>
              </div>
            </div>
          ) : (
            <div className="py-8 text-center text-gray-500">
              {t('reservations.noSettings', 'Failed to load settings')}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
