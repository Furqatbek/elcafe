import React, { useState, useEffect } from 'react';
import { restaurantAPI } from '../services/api';
import { useTranslation } from 'react-i18next';
import { Clock, Save, Plus, Trash2, Calendar } from 'lucide-react';

const DAYS_OF_WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

const WorkingHours = () => {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [workingHours, setWorkingHours] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadWorkingHours();
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantsData = response?.data?.data?.content || response?.data?.data || [];
      setRestaurants(Array.isArray(restaurantsData) ? restaurantsData : []);

      if (restaurantsData.length > 0) {
        setSelectedRestaurant(restaurantsData[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
      setRestaurants([]);
    }
  };

  const loadWorkingHours = async () => {
    try {
      setLoading(true);
      const response = await restaurantAPI.getBusinessHours(selectedRestaurant);
      const hours = response?.data?.data || {};

      // Convert array to object indexed by day
      const hoursMap = {};
      if (Array.isArray(hours)) {
        hours.forEach(item => {
          hoursMap[item.dayOfWeek] = item;
        });
      }

      setWorkingHours(hoursMap);
    } catch (error) {
      console.error('Failed to load business hours:', error);
      // Initialize with default structure
      const defaultHours = {};
      DAYS_OF_WEEK.forEach(day => {
        defaultHours[day] = {
          dayOfWeek: day,
          isOpen: true,
          openTime: '09:00',
          closeTime: '22:00'
        };
      });
      setWorkingHours(defaultHours);
    } finally {
      setLoading(false);
    }
  };

  const handleDayChange = (day, field, value) => {
    setWorkingHours(prev => ({
      ...prev,
      [day]: {
        ...(prev[day] || { dayOfWeek: day, isOpen: true, openTime: '09:00', closeTime: '22:00' }),
        [field]: value
      }
    }));
  };

  const handleSave = async () => {
    if (!selectedRestaurant) {
      alert('Please select a restaurant');
      return;
    }

    try {
      setSaving(true);
      const hoursArray = Object.values(workingHours);
      await restaurantAPI.updateBusinessHours(selectedRestaurant, hoursArray);
      alert(t('workingHours.messages.saveSuccess') || 'Business hours saved successfully');
    } catch (error) {
      console.error('Failed to save business hours:', error);
      alert(t('workingHours.messages.saveError') || 'Failed to save business hours');
    } finally {
      setSaving(false);
    }
  };

  const copyToAllDays = (day) => {
    const sourceDay = workingHours[day];
    if (!sourceDay) return;

    const newHours = {};
    DAYS_OF_WEEK.forEach(d => {
      newHours[d] = {
        dayOfWeek: d,
        isOpen: sourceDay.isOpen,
        openTime: sourceDay.openTime,
        closeTime: sourceDay.closeTime
      };
    });
    setWorkingHours(newHours);
  };

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2 flex items-center gap-2">
          <Clock size={28} />
          {t('workingHours.title') || 'Restaurant Working Hours'}
        </h1>
        <p className="text-gray-600">
          {t('workingHours.subtitle') || 'Configure operating hours for each restaurant branch'}
        </p>
      </div>

      {/* Restaurant Selector */}
      <div className="mb-6 bg-white rounded-lg shadow p-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          {t('common.selectRestaurant') || 'Select Restaurant'}
        </label>
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => setSelectedRestaurant(Number(e.target.value))}
          className="w-full md:w-96 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">{t('common.selectRestaurant') || 'Select Restaurant'}</option>
          {restaurants.map(restaurant => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
      </div>

      {/* Working Hours Grid */}
      {selectedRestaurant && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="p-6">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Calendar size={20} />
                {t('workingHours.weeklySchedule') || 'Weekly Schedule'}
              </h2>
              <button
                onClick={handleSave}
                disabled={saving}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                <Save size={18} />
                {saving ? (t('common.saving') || 'Saving...') : (t('common.save') || 'Save')}
              </button>
            </div>

            {loading ? (
              <div className="text-center py-8 text-gray-500">{t('common.loading') || 'Loading...'}</div>
            ) : (
              <div className="space-y-4">
                {DAYS_OF_WEEK.map(day => {
                  const dayData = workingHours[day] || {
                    dayOfWeek: day,
                    isOpen: true,
                    openTime: '09:00',
                    closeTime: '22:00'
                  };

                  return (
                    <div key={day} className="flex items-center gap-4 p-4 border border-gray-200 rounded-lg">
                      <div className="w-32">
                        <span className="font-medium text-gray-900">
                          {t(`days.${day}`) || day}
                        </span>
                      </div>

                      <div className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={dayData.isOpen}
                          onChange={(e) => handleDayChange(day, 'isOpen', e.target.checked)}
                          className="h-4 w-4 text-blue-600 rounded"
                        />
                        <span className="text-sm text-gray-600">
                          {t('workingHours.open') || 'Open'}
                        </span>
                      </div>

                      {dayData.isOpen && (
                        <>
                          <div className="flex items-center gap-2">
                            <label className="text-sm text-gray-600">
                              {t('workingHours.from') || 'From'}:
                            </label>
                            <input
                              type="time"
                              value={dayData.openTime}
                              onChange={(e) => handleDayChange(day, 'openTime', e.target.value)}
                              className="px-3 py-1 border border-gray-300 rounded"
                            />
                          </div>

                          <div className="flex items-center gap-2">
                            <label className="text-sm text-gray-600">
                              {t('workingHours.to') || 'To'}:
                            </label>
                            <input
                              type="time"
                              value={dayData.closeTime}
                              onChange={(e) => handleDayChange(day, 'closeTime', e.target.value)}
                              className="px-3 py-1 border border-gray-300 rounded"
                            />
                          </div>

                          <button
                            onClick={() => copyToAllDays(day)}
                            className="ml-auto px-3 py-1 text-sm text-blue-600 hover:bg-blue-50 rounded"
                            title={t('workingHours.copyToAll') || 'Copy to all days'}
                          >
                            {t('workingHours.copyToAll') || 'Copy to all'}
                          </button>
                        </>
                      )}

                      {!dayData.isOpen && (
                        <span className="text-red-600 font-medium ml-4">
                          {t('workingHours.closed') || 'Closed'}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default WorkingHours;
