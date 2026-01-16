import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { reservationAPI, tablesAPI } from '../services/api';
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
  Filter,
  MoreVertical,
  Table2,
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
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [selectedReservation, setSelectedReservation] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showAssignTableModal, setShowAssignTableModal] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  // For now, hardcode restaurant ID (should come from auth context)
  const restaurantId = 1;

  useEffect(() => {
    loadReservations();
    loadTables();
  }, [currentMonth]);

  const loadReservations = async () => {
    setLoading(true);
    try {
      const startDate = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
      const endDate = format(endOfMonth(currentMonth), 'yyyy-MM-dd');
      const response = await reservationAPI.getByDateRange(restaurantId, startDate, endDate);
      setReservations(response.data.data || []);
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
      </div>

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
                            {reservation.tableName && (
                              <>
                                <span>•</span>
                                <Table2 className="h-3 w-3" />
                                {reservation.tableName}
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

                {selectedReservation.tableName ? (
                  <div className="flex items-center gap-3">
                    <Table2 className="h-5 w-5 text-gray-400" />
                    <span>{selectedReservation.tableName}</span>
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
    </div>
  );
}
