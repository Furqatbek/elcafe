import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Users, RefreshCw } from 'lucide-react';
import { cn } from '../../lib/utils';
import usePOSStore from '../store/posStore';
import TouchButton from '../components/TouchButton';
import FloorPlanView from '../components/FloorPlanView';

/**
 * TableSelectionScreen - POS screen for selecting a table for dine-in orders
 * Shows floor plan layout with table status and availability
 */
const TableSelectionScreen = () => {
  const { t } = useTranslation();
  const [guestCount, setGuestCount] = useState(2);
  const [tempSelectedTable, setTempSelectedTable] = useState(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const {
    floorPlan,
    selectedTable,
    currentOrder,
    fetchFloorPlan,
    selectTable,
    setCurrentScreen,
    ui,
  } = usePOSStore();

  // Get restaurant ID from localStorage or default
  const restaurantId = localStorage.getItem('selectedRestaurantId') || 1;

  // Fetch floor plan on mount
  useEffect(() => {
    fetchFloorPlan(restaurantId);
  }, [restaurantId]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await fetchFloorPlan(restaurantId);
    setIsRefreshing(false);
  };

  const handleTableSelect = (table) => {
    setTempSelectedTable(table);
  };

  const handleConfirmSelection = () => {
    if (!tempSelectedTable) return;

    // Select table with guest count
    selectTable({
      ...tempSelectedTable,
      guestCount,
    });
  };

  const handleBack = () => {
    setCurrentScreen('start');
  };

  const handleGuestCountChange = (delta) => {
    setGuestCount((prev) => Math.max(1, Math.min(20, prev + delta)));
  };

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <TouchButton
              variant="ghost"
              size="lg"
              onClick={handleBack}
              icon={<ArrowLeft className="w-6 h-6" />}
            >
              {t('common.back', 'Back')}
            </TouchButton>

            <div>
              <h1 className="text-2xl font-bold text-gray-900">
                {t('pos.tables.selectTable', 'Select a Table')}
              </h1>
              <p className="text-gray-600">
                {t('pos.tables.selectTableDesc', 'Choose a table for your dine-in order')}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-4">
            {/* Guest Count Selector */}
            <div className="flex items-center gap-3 bg-gray-100 rounded-xl px-4 py-2">
              <Users className="w-5 h-5 text-gray-600" />
              <span className="text-gray-700 font-medium">
                {t('pos.tables.guests', 'Guests')}:
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleGuestCountChange(-1)}
                  disabled={guestCount <= 1}
                  className={cn(
                    'w-10 h-10 flex items-center justify-center rounded-lg text-2xl font-bold transition-colors',
                    guestCount > 1
                      ? 'bg-white text-gray-700 hover:bg-gray-200 active:bg-gray-300'
                      : 'bg-gray-200 text-gray-400 cursor-not-allowed'
                  )}
                >
                  -
                </button>
                <span className="w-10 text-center text-2xl font-bold text-gray-900">
                  {guestCount}
                </span>
                <button
                  onClick={() => handleGuestCountChange(1)}
                  disabled={guestCount >= 20}
                  className={cn(
                    'w-10 h-10 flex items-center justify-center rounded-lg text-2xl font-bold transition-colors',
                    guestCount < 20
                      ? 'bg-white text-gray-700 hover:bg-gray-200 active:bg-gray-300'
                      : 'bg-gray-200 text-gray-400 cursor-not-allowed'
                  )}
                >
                  +
                </button>
              </div>
            </div>

            {/* Refresh Button */}
            <TouchButton
              variant="outline"
              size="lg"
              onClick={handleRefresh}
              disabled={isRefreshing}
              icon={<RefreshCw className={cn('w-5 h-5', isRefreshing && 'animate-spin')} />}
            >
              {t('common.refresh', 'Refresh')}
            </TouchButton>
          </div>
        </div>
      </header>

      {/* Floor Plan */}
      <div className="flex-1 overflow-hidden">
        <FloorPlanView
          tables={floorPlan.tables}
          sections={floorPlan.sections}
          selectedTable={tempSelectedTable}
          onSelectTable={handleTableSelect}
        />
      </div>

      {/* Footer with Selection Summary */}
      <footer className="bg-white border-t border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Selected Table Info */}
          <div className="flex-1">
            {tempSelectedTable ? (
              <div className="flex items-center gap-4">
                <div className="w-16 h-16 bg-green-100 border-2 border-green-400 rounded-xl flex items-center justify-center">
                  <span className="text-2xl font-bold text-gray-900">
                    {tempSelectedTable.tableNumber}
                  </span>
                </div>
                <div>
                  <p className="text-lg font-semibold text-gray-900">
                    {t('pos.tables.tableSelected', 'Table {{number}} Selected', {
                      number: tempSelectedTable.tableNumber,
                    })}
                  </p>
                  <p className="text-gray-600">
                    {t('pos.tables.capacity', 'Capacity')}: {tempSelectedTable.capacity} |{' '}
                    {t('pos.tables.guests', 'Guests')}: {guestCount}
                    {tempSelectedTable.section && ` | ${tempSelectedTable.section}`}
                  </p>
                </div>
              </div>
            ) : (
              <p className="text-gray-500 text-lg">
                {t('pos.tables.selectTablePrompt', 'Tap on an available table to select it')}
              </p>
            )}
          </div>

          {/* Confirm Button */}
          <TouchButton
            variant="primary"
            size="xl"
            onClick={handleConfirmSelection}
            disabled={!tempSelectedTable}
            className="min-w-[200px]"
          >
            {t('pos.tables.confirmTable', 'Confirm Table')}
          </TouchButton>
        </div>
      </footer>
    </div>
  );
};

export default TableSelectionScreen;
