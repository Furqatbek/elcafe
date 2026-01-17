import { useEffect, useState } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { reservationPublicAPI } from '../../services/api';
import LanguageSelector from './LanguageSelector';
import {
  Calendar,
  Clock,
  Users,
  User,
  Phone,
  MessageSquare,
  CheckCircle,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Loader2,
  MapPin,
  Square,
} from 'lucide-react';

export default function ReservationPage() {
  const { restaurantId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [step, setStep] = useState(1); // 1: Date/Time, 2: Table Selection, 3: Details, 4: Confirmation
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Form data
  const [selectedDate, setSelectedDate] = useState(null);
  const [selectedTime, setSelectedTime] = useState(null);
  const [partySize, setPartySize] = useState(2);
  const [availability, setAvailability] = useState(null);
  const [tables, setTables] = useState([]);
  const [selectedTable, setSelectedTable] = useState(null);
  const [loadingTables, setLoadingTables] = useState(false);
  const [formData, setFormData] = useState({
    customerName: '',
    customerPhone: '',
    specialRequests: '',
  });

  // Calendar state
  const [currentMonth, setCurrentMonth] = useState(new Date());

  useEffect(() => {
    if (selectedDate) {
      loadAvailability();
    }
  }, [selectedDate, partySize]);

  useEffect(() => {
    if (selectedDate && selectedTime) {
      loadTables();
    }
  }, [selectedDate, selectedTime, partySize]);

  const loadTables = async () => {
    if (!restaurantId || !selectedDate || !selectedTime) return;

    try {
      setLoadingTables(true);
      const response = await reservationPublicAPI.getTables(
        restaurantId,
        selectedDate.toISOString().split('T')[0],
        selectedTime,
        partySize
      );
      setTables(response.data.data || []);
    } catch (err) {
      console.error('Failed to load tables:', err);
    } finally {
      setLoadingTables(false);
    }
  };

  const loadAvailability = async () => {
    if (!restaurantId || !selectedDate) return;

    try {
      setLoading(true);
      setError(null);
      const response = await reservationPublicAPI.checkAvailability(
        restaurantId,
        selectedDate.toISOString().split('T')[0],
        partySize
      );
      setAvailability(response.data.data);
    } catch (err) {
      setError('Failed to load availability');
      console.error('Failed to load availability:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!selectedDate || !selectedTime) {
      setError('Please select a date and time');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);

      const response = await reservationPublicAPI.createReservation(restaurantId, {
        reservationDate: selectedDate.toISOString().split('T')[0],
        reservationTime: selectedTime,
        partySize,
        tableId: selectedTable?.id || null,
        customerName: formData.customerName,
        customerPhone: formData.customerPhone,
        specialRequests: formData.specialRequests || null,
        source: 'ONLINE',
      });

      setSuccess(response.data.data);
      setStep(4);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create reservation');
      console.error('Failed to create reservation:', err);
    } finally {
      setSubmitting(false);
    }
  };

  const getDaysInMonth = (date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const days = [];

    // Add empty slots for days before the first day of the month
    for (let i = 0; i < firstDay.getDay(); i++) {
      days.push(null);
    }

    // Add days of the month
    for (let i = 1; i <= lastDay.getDate(); i++) {
      days.push(new Date(year, month, i));
    }

    return days;
  };

  const isDateDisabled = (date) => {
    if (!date) return true;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return date < today;
  };

  const isDateSelected = (date) => {
    if (!date || !selectedDate) return false;
    return date.toDateString() === selectedDate.toDateString();
  };

  const formatTime = (time) => {
    const [hours, minutes] = time.split(':');
    const hour = parseInt(hours);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    const displayHour = hour % 12 || 12;
    return `${displayHour}:${minutes} ${ampm}`;
  };

  // Confirmation step
  if (step === 4 && success) {
    return (
      <div className="min-h-screen bg-gray-50 py-8 px-4">
        <div className="max-w-md mx-auto">
          <div className="bg-white rounded-lg shadow-lg p-6 text-center">
            <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <CheckCircle className="h-8 w-8 text-green-600" />
            </div>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Reservation Confirmed!</h2>
            <p className="text-gray-600 mb-6">
              Your table has been reserved. We've sent a confirmation to your phone.
            </p>

            <div className="bg-gray-50 rounded-lg p-4 mb-6 text-left">
              <div className="text-sm text-gray-500 mb-1">Confirmation Code</div>
              <div className="text-2xl font-bold text-blue-600 tracking-wider">
                {success.confirmationCode}
              </div>
            </div>

            <div className="space-y-3 text-left mb-6">
              <div className="flex items-center gap-3">
                <Calendar className="h-5 w-5 text-gray-400" />
                <span>
                  {new Date(success.reservationDate).toLocaleDateString('en-US', {
                    weekday: 'long',
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })}
                </span>
              </div>
              <div className="flex items-center gap-3">
                <Clock className="h-5 w-5 text-gray-400" />
                <span>{formatTime(success.reservationTime)}</span>
              </div>
              <div className="flex items-center gap-3">
                <Users className="h-5 w-5 text-gray-400" />
                <span>{success.partySize} guests</span>
              </div>
            </div>

            {success.status === 'DEPOSIT_PENDING' && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-6">
                <div className="flex items-start gap-3">
                  <AlertCircle className="h-5 w-5 text-yellow-600 mt-0.5" />
                  <div className="text-left">
                    <div className="font-medium text-yellow-800">Deposit Required</div>
                    <div className="text-sm text-yellow-600">
                      Please pay the deposit to confirm your reservation.
                    </div>
                  </div>
                </div>
              </div>
            )}

            <p className="text-sm text-gray-500">
              Save your confirmation code. You can use it to check or modify your reservation.
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-blue-600 shadow-sm">
        <div className="max-w-lg mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-white">{t('customerReservation.title')}</h1>
            </div>
            <LanguageSelector />
          </div>
        </div>
      </div>

      {/* Progress */}
      <div className="bg-white border-b">
        <div className="max-w-lg mx-auto px-4 py-3">
          <div className="flex items-center justify-between">
            <div className={`flex items-center gap-2 ${step >= 1 ? 'text-blue-600' : 'text-gray-400'}`}>
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium ${
                step >= 1 ? 'bg-blue-600 text-white' : 'bg-gray-200'
              }`}>1</div>
              <span className="text-xs font-medium hidden sm:inline">{t('customerReservation.step1')}</span>
            </div>
            <div className="flex-1 h-0.5 mx-1 bg-gray-200">
              <div className={`h-full bg-blue-600 transition-all ${step >= 2 ? 'w-full' : 'w-0'}`} />
            </div>
            <div className={`flex items-center gap-2 ${step >= 2 ? 'text-blue-600' : 'text-gray-400'}`}>
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium ${
                step >= 2 ? 'bg-blue-600 text-white' : 'bg-gray-200'
              }`}>2</div>
              <span className="text-xs font-medium hidden sm:inline">{t('customerReservation.step2')}</span>
            </div>
            <div className="flex-1 h-0.5 mx-1 bg-gray-200">
              <div className={`h-full bg-blue-600 transition-all ${step >= 3 ? 'w-full' : 'w-0'}`} />
            </div>
            <div className={`flex items-center gap-2 ${step >= 3 ? 'text-blue-600' : 'text-gray-400'}`}>
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium ${
                step >= 3 ? 'bg-blue-600 text-white' : 'bg-gray-200'
              }`}>3</div>
              <span className="text-xs font-medium hidden sm:inline">{t('customerReservation.step3')}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-6">
        {error && (
          <div className="mb-4 bg-red-50 border border-red-200 rounded-lg p-4">
            <div className="flex items-center gap-2 text-red-800">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="space-y-6">
            {/* Party Size */}
            <div className="bg-white rounded-lg shadow-sm p-4">
              <label className="block text-sm font-medium text-gray-700 mb-3">
                <Users className="inline h-4 w-4 mr-2" />
                Party Size
              </label>
              <div className="flex flex-wrap gap-2">
                {[1, 2, 3, 4, 5, 6, 7, 8].map((size) => (
                  <button
                    key={size}
                    type="button"
                    onClick={() => setPartySize(size)}
                    className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                      partySize === size
                        ? 'bg-blue-600 text-white'
                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                  >
                    {size} {size === 1 ? 'guest' : 'guests'}
                  </button>
                ))}
              </div>
            </div>

            {/* Calendar */}
            <div className="bg-white rounded-lg shadow-sm p-4">
              <label className="block text-sm font-medium text-gray-700 mb-3">
                <Calendar className="inline h-4 w-4 mr-2" />
                Select Date
              </label>

              {/* Month Navigation */}
              <div className="flex items-center justify-between mb-4">
                <button
                  type="button"
                  onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1))}
                  className="p-2 hover:bg-gray-100 rounded-lg"
                >
                  <ChevronLeft className="h-5 w-5" />
                </button>
                <span className="font-medium">
                  {currentMonth.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
                </span>
                <button
                  type="button"
                  onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1))}
                  className="p-2 hover:bg-gray-100 rounded-lg"
                >
                  <ChevronRight className="h-5 w-5" />
                </button>
              </div>

              {/* Calendar Grid */}
              <div className="grid grid-cols-7 gap-1">
                {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => (
                  <div key={day} className="text-center text-xs font-medium text-gray-500 py-2">
                    {day}
                  </div>
                ))}
                {getDaysInMonth(currentMonth).map((date, index) => (
                  <button
                    key={index}
                    type="button"
                    disabled={isDateDisabled(date)}
                    onClick={() => date && !isDateDisabled(date) && setSelectedDate(date)}
                    className={`aspect-square flex items-center justify-center text-sm rounded-lg transition-colors ${
                      !date
                        ? ''
                        : isDateDisabled(date)
                        ? 'text-gray-300 cursor-not-allowed'
                        : isDateSelected(date)
                        ? 'bg-blue-600 text-white'
                        : 'hover:bg-gray-100'
                    }`}
                  >
                    {date?.getDate()}
                  </button>
                ))}
              </div>
            </div>

            {/* Time Slots */}
            {selectedDate && (
              <div className="bg-white rounded-lg shadow-sm p-4">
                <label className="block text-sm font-medium text-gray-700 mb-3">
                  <Clock className="inline h-4 w-4 mr-2" />
                  Select Time
                </label>

                {loading ? (
                  <div className="flex items-center justify-center py-8">
                    <Loader2 className="h-6 w-6 animate-spin text-blue-600" />
                  </div>
                ) : availability?.timeSlots?.length > 0 ? (
                  <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
                    {availability.timeSlots.map((slot) => (
                      <button
                        key={slot.time}
                        type="button"
                        disabled={!slot.available}
                        onClick={() => slot.available && setSelectedTime(slot.time)}
                        className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                          !slot.available
                            ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
                            : selectedTime === slot.time
                            ? 'bg-blue-600 text-white'
                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                        }`}
                      >
                        {formatTime(slot.time)}
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-8 text-gray-500">
                    No available time slots for this date
                  </div>
                )}
              </div>
            )}

            {/* Continue Button */}
            <button
              type="button"
              disabled={!selectedDate || !selectedTime}
              onClick={() => setStep(2)}
              className="w-full bg-blue-600 text-white py-3 px-4 rounded-lg font-medium disabled:bg-gray-300 disabled:cursor-not-allowed hover:bg-blue-700 transition-colors"
            >
              Continue
            </button>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-6">
            {/* Selected Date/Time Summary */}
            <div className="bg-blue-50 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium text-blue-900">
                    {selectedDate?.toLocaleDateString('en-US', {
                      weekday: 'long',
                      month: 'long',
                      day: 'numeric',
                    })}
                  </div>
                  <div className="text-sm text-blue-700">
                    {formatTime(selectedTime)} - {partySize} guests
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="text-blue-600 text-sm font-medium hover:underline"
                >
                  Change
                </button>
              </div>
            </div>

            {/* Table Selection */}
            <div className="bg-white rounded-lg shadow-sm p-4">
              <label className="block text-sm font-medium text-gray-700 mb-3">
                <MapPin className="inline h-4 w-4 mr-2" />
                Select Your Table (Optional)
              </label>

              {loadingTables ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="h-6 w-6 animate-spin text-blue-600" />
                </div>
              ) : tables.length > 0 ? (
                <div className="space-y-4">
                  {/* Table Legend */}
                  <div className="flex flex-wrap gap-3 text-xs">
                    <span className="flex items-center gap-1">
                      <div className="w-3 h-3 rounded bg-green-500"></div>
                      Available
                    </span>
                    <span className="flex items-center gap-1">
                      <div className="w-3 h-3 rounded bg-red-500"></div>
                      Reserved
                    </span>
                    <span className="flex items-center gap-1">
                      <div className="w-3 h-3 rounded bg-gray-300"></div>
                      Unavailable
                    </span>
                  </div>

                  {/* Tables Grid */}
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                    {tables.map((table) => {
                      const isAvailable = table.availableForReservation;
                      const isSelected = selectedTable?.id === table.id;

                      return (
                        <button
                          key={table.id}
                          type="button"
                          disabled={!isAvailable}
                          onClick={() => setSelectedTable(isSelected ? null : table)}
                          className={`p-3 rounded-lg border-2 text-left transition-all ${
                            isSelected
                              ? 'border-blue-600 bg-blue-50'
                              : isAvailable
                              ? 'border-gray-200 hover:border-blue-300 bg-white'
                              : 'border-gray-100 bg-gray-50 cursor-not-allowed opacity-60'
                          }`}
                        >
                          <div className="flex items-center gap-2 mb-1">
                            <div className={`w-3 h-3 rounded ${
                              isAvailable ? 'bg-green-500' : 'bg-red-500'
                            }`}></div>
                            <span className="font-medium text-sm">
                              #{table.tableNumber}{table.tableName && ` - ${table.tableName}`}
                            </span>
                          </div>
                          <div className="text-xs text-gray-500">
                            {table.capacity} seats
                            {table.section && ` • ${table.section}`}
                          </div>
                          {!isAvailable && (
                            <div className="text-xs text-red-600 mt-1">
                              {table.statusReason === 'TOO_SMALL' ? 'Too small' :
                               table.statusReason === 'RESERVED' ? 'Reserved' :
                               table.statusReason === 'OCCUPIED' ? 'Occupied' : 'Unavailable'}
                            </div>
                          )}
                        </button>
                      );
                    })}
                  </div>

                  {selectedTable && (
                    <div className="bg-green-50 rounded-lg p-3 text-sm text-green-800">
                      Selected: #{selectedTable.tableNumber}{selectedTable.tableName && ` - ${selectedTable.tableName}`} ({selectedTable.capacity} seats)
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-center py-8 text-gray-500">
                  <MapPin className="h-12 w-12 mx-auto mb-3 text-gray-300" />
                  <p>No tables available for selection</p>
                  <p className="text-sm">A table will be assigned for you</p>
                </div>
              )}
            </div>

            {/* Navigation Buttons */}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="flex-1 bg-gray-100 text-gray-700 py-3 px-4 rounded-lg font-medium hover:bg-gray-200 transition-colors"
              >
                Back
              </button>
              <button
                type="button"
                onClick={() => setStep(3)}
                className="flex-1 bg-blue-600 text-white py-3 px-4 rounded-lg font-medium hover:bg-blue-700 transition-colors"
              >
                Continue
              </button>
            </div>
          </div>
        )}

        {step === 3 && (
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Selected Date/Time/Table Summary */}
            <div className="bg-blue-50 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium text-blue-900">
                    {selectedDate?.toLocaleDateString('en-US', {
                      weekday: 'long',
                      month: 'long',
                      day: 'numeric',
                    })}
                  </div>
                  <div className="text-sm text-blue-700">
                    {formatTime(selectedTime)} - {partySize} guests
                  </div>
                  {selectedTable && (
                    <div className="text-sm text-blue-700 mt-1">
                      <MapPin className="inline h-3 w-3 mr-1" />
                      #{selectedTable.tableNumber}{selectedTable.tableName && ` - ${selectedTable.tableName}`}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="text-blue-600 text-sm font-medium hover:underline"
                >
                  Change
                </button>
              </div>
            </div>

            {/* Contact Information */}
            <div className="bg-white rounded-lg shadow-sm p-4 space-y-4">
              <h3 className="font-medium text-gray-900">Contact Information</h3>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <User className="inline h-4 w-4 mr-1" />
                  Name *
                </label>
                <input
                  type="text"
                  required
                  value={formData.customerName}
                  onChange={(e) => setFormData({ ...formData, customerName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="Your name"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <Phone className="inline h-4 w-4 mr-1" />
                  Phone *
                </label>
                <input
                  type="tel"
                  required
                  value={formData.customerPhone}
                  onChange={(e) => setFormData({ ...formData, customerPhone: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="Your phone number"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  <MessageSquare className="inline h-4 w-4 mr-1" />
                  Special Requests (optional)
                </label>
                <textarea
                  value={formData.specialRequests}
                  onChange={(e) => setFormData({ ...formData, specialRequests: e.target.value })}
                  rows={3}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="Allergies, seating preferences, special occasions..."
                />
              </div>
            </div>

            {/* Deposit Notice */}
            {availability?.depositRequired && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                <div className="flex items-start gap-3">
                  <AlertCircle className="h-5 w-5 text-yellow-600 mt-0.5" />
                  <div>
                    <div className="font-medium text-yellow-800">Deposit Required</div>
                    <div className="text-sm text-yellow-600">
                      A deposit of {availability.depositAmount?.toFixed(2)} is required to confirm your reservation.
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Submit Buttons */}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setStep(2)}
                className="flex-1 bg-gray-100 text-gray-700 py-3 px-4 rounded-lg font-medium hover:bg-gray-200 transition-colors"
              >
                Back
              </button>
              <button
                type="submit"
                disabled={submitting || !formData.customerName || !formData.customerPhone}
                className="flex-1 bg-blue-600 text-white py-3 px-4 rounded-lg font-medium disabled:bg-gray-300 hover:bg-blue-700 transition-colors flex items-center justify-center gap-2"
              >
                {submitting ? (
                  <>
                    <Loader2 className="h-5 w-5 animate-spin" />
                    Reserving...
                  </>
                ) : (
                  'Complete Reservation'
                )}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
