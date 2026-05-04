import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { shiftAPI, waiterAPI } from '../services/api';
import { Clock, Coffee, LogOut, UserCheck, AlertCircle } from 'lucide-react';

/**
 * MobileClockIn — mobile-friendly standalone page for employees to
 * clock in/out via PIN code. No sidebar, fullscreen, touch-optimized.
 *
 * Route: /admin/shift/clock?restaurant={id}
 */
export default function MobileClockIn() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const restaurantId = searchParams.get('restaurant') || localStorage.getItem('selectedRestaurantId') || '1';

  const [pin, setPin] = useState('');
  const [employee, setEmployee] = useState(null);
  const [activeShift, setActiveShift] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [now, setNow] = useState(Date.now());

  // Live timer
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);

  const handlePinInput = (digit) => {
    if (pin.length < 4) setPin(pin + digit);
  };

  const handlePinClear = () => setPin('');

  const handlePinSubmit = async () => {
    if (pin.length < 4) return;
    setError(null);
    setLoading(true);
    try {
      // Authenticate via waiter PIN
      const authRes = await waiterAPI.auth(pin);
      const waiterData = authRes.data.data || authRes.data;
      setEmployee(waiterData);

      // Check for active shift
      const shiftsRes = await shiftAPI.getActive(restaurantId);
      const shifts = shiftsRes.data.data || [];
      const myShift = shifts.find(s => s.employeeId === waiterData.id || s.employeeId === waiterData.userId);
      setActiveShift(myShift || null);
    } catch (e) {
      setError(t('shift.mobile.invalidPin', 'Invalid PIN. Please try again.'));
      setPin('');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (pin.length === 4) handlePinSubmit();
  }, [pin]);

  const handleClockIn = async () => {
    setLoading(true);
    setError(null);
    try {
      await shiftAPI.clockIn(restaurantId, {
        employeeId: employee.id || employee.userId,
        waiterId: employee.id,
      });
      // Reload active shift
      const shiftsRes = await shiftAPI.getActive(restaurantId);
      const shifts = shiftsRes.data.data || [];
      const myShift = shifts.find(s => s.employeeId === (employee.id || employee.userId));
      setActiveShift(myShift || null);
    } catch (e) {
      setError(e.response?.data?.message || 'Failed to clock in');
    } finally {
      setLoading(false);
    }
  };

  const handleClockOut = async () => {
    if (!activeShift) return;
    setLoading(true);
    try {
      await shiftAPI.clockOut(restaurantId, activeShift.id, {});
      setActiveShift(null);
      setEmployee(null);
      setPin('');
    } catch (e) {
      setError(e.response?.data?.message || 'Failed to clock out');
    } finally {
      setLoading(false);
    }
  };

  const handleBreak = async () => {
    if (!activeShift) return;
    setLoading(true);
    try {
      if (activeShift.status === 'ON_BREAK') {
        await shiftAPI.endBreak(restaurantId, activeShift.id);
      } else {
        await shiftAPI.startBreak(restaurantId, activeShift.id);
      }
      // Reload
      const shiftsRes = await shiftAPI.getActive(restaurantId);
      const shifts = shiftsRes.data.data || [];
      const myShift = shifts.find(s => s.employeeId === (employee.id || employee.userId));
      setActiveShift(myShift || null);
    } catch (e) {
      setError(e.response?.data?.message || 'Failed');
    } finally {
      setLoading(false);
    }
  };

  const formatDuration = (clockIn) => {
    if (!clockIn) return '--';
    const min = Math.floor((now - new Date(clockIn).getTime()) / 60000);
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${h}h ${m}m`;
  };

  const getGreeting = () => {
    const h = new Date().getHours();
    if (h < 12) return t('shift.mobile.goodMorning', 'Good Morning');
    if (h < 17) return t('shift.mobile.goodAfternoon', 'Good Afternoon');
    return t('shift.mobile.goodEvening', 'Good Evening');
  };

  // --- Render: Active shift view ---
  if (employee && activeShift) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="max-w-sm w-full text-center space-y-6">
          <div className="bg-white rounded-2xl shadow-lg p-8 space-y-6">
            <div>
              <p className="text-lg text-gray-500">{employee.name || employee.fullName}</p>
              <h1 className="text-3xl font-bold text-gray-900">
                {activeShift.status === 'ON_BREAK'
                  ? t('shift.mobile.onBreak', 'On Break')
                  : t('shift.mobile.active', 'Active')}
              </h1>
            </div>

            <div className="py-4">
              <p className="text-sm text-gray-500">{t('shift.mobile.since', 'Since')}</p>
              <p className="text-2xl font-bold text-gray-900">
                {activeShift.clockIn ? new Date(activeShift.clockIn).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '--'}
              </p>
              <p className="text-4xl font-black text-blue-600 mt-2">
                {formatDuration(activeShift.clockIn)}
              </p>
            </div>

            {error && <p className="text-red-500 text-sm">{error}</p>}

            <div className="space-y-3">
              <button
                onClick={handleBreak}
                disabled={loading}
                className="w-full py-4 rounded-xl text-lg font-semibold bg-amber-100 text-amber-700 hover:bg-amber-200 transition-colors flex items-center justify-center gap-2"
              >
                {activeShift.status === 'ON_BREAK' ? (
                  <><UserCheck className="w-5 h-5" /> {t('shift.mobile.endBreak', 'End Break')}</>
                ) : (
                  <><Coffee className="w-5 h-5" /> {t('shift.mobile.takeBreak', 'Take Break')}</>
                )}
              </button>

              <button
                onClick={handleClockOut}
                disabled={loading}
                className="w-full py-4 rounded-xl text-lg font-semibold bg-red-100 text-red-700 hover:bg-red-200 transition-colors flex items-center justify-center gap-2"
              >
                <LogOut className="w-5 h-5" /> {t('shift.mobile.clockOut', 'Clock Out')}
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // --- Render: Authenticated but no shift ---
  if (employee && !activeShift) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="max-w-sm w-full text-center space-y-6">
          <div className="bg-white rounded-2xl shadow-lg p-8 space-y-6">
            <div>
              <p className="text-lg text-gray-500">{getGreeting()}</p>
              <h1 className="text-3xl font-bold text-gray-900">{employee.name || employee.fullName}</h1>
            </div>

            {error && <p className="text-red-500 text-sm">{error}</p>}

            <button
              onClick={handleClockIn}
              disabled={loading}
              className="w-full py-6 rounded-2xl text-2xl font-bold bg-green-600 text-white hover:bg-green-700 active:bg-green-800 transition-colors flex items-center justify-center gap-3 shadow-lg"
            >
              <Clock className="w-7 h-7" /> {t('shift.mobile.clockIn', 'Clock In')}
            </button>
          </div>
        </div>
      </div>
    );
  }

  // --- Render: PIN entry ---
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="max-w-sm w-full text-center space-y-6">
        <div className="bg-white rounded-2xl shadow-lg p-8 space-y-6">
          <div>
            <Clock className="w-12 h-12 text-blue-600 mx-auto mb-2" />
            <h1 className="text-2xl font-bold text-gray-900">
              {t('shift.mobile.enterPin', 'Enter Your PIN')}
            </h1>
          </div>

          {/* PIN dots */}
          <div className="flex justify-center gap-4">
            {[0, 1, 2, 3].map(i => (
              <div
                key={i}
                className={`w-5 h-5 rounded-full border-2 ${
                  i < pin.length ? 'bg-blue-600 border-blue-600' : 'border-gray-300'
                }`}
              />
            ))}
          </div>

          {error && (
            <p className="text-red-500 text-sm flex items-center justify-center gap-1">
              <AlertCircle className="w-4 h-4" /> {error}
            </p>
          )}

          {/* Numpad */}
          <div className="grid grid-cols-3 gap-3">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9, null, 0, 'clear'].map((key, idx) => (
              <button
                key={idx}
                onClick={() => {
                  if (key === 'clear') handlePinClear();
                  else if (key !== null) handlePinInput(key.toString());
                }}
                disabled={key === null || loading}
                className={`h-16 rounded-xl text-2xl font-bold transition-colors ${
                  key === null
                    ? 'invisible'
                    : key === 'clear'
                    ? 'bg-gray-200 text-gray-600 hover:bg-gray-300'
                    : 'bg-gray-100 text-gray-900 hover:bg-gray-200 active:bg-blue-100'
                }`}
              >
                {key === 'clear' ? '⌫' : key}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
