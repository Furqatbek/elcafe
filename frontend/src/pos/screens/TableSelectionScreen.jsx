import { useEffect, useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Users, RefreshCw, Layers, X } from 'lucide-react';
import { cn } from '../../lib/utils';
import usePOSStore from '../store/posStore';
import TouchButton from '../components/TouchButton';
import FloorPlanView from '../components/FloorPlanView';

/**
 * TableSelectionScreen - POS screen for selecting tables for dine-in orders
 * Supports selecting multiple tables for large parties
 */
const TableSelectionScreen = () => {
  const { t } = useTranslation();
  const [guestCount, setGuestCount] = useState(2);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const {
    floorPlan,
    selectedTables,
    fetchFloorPlan,
    toggleTableSelection,
    confirmTableSelection,
    clearSelectedTables,
    setCurrentScreen,
  } = usePOSStore();

  // Get restaurant ID from localStorage or default
  const restaurantId = localStorage.getItem('selectedRestaurantId') || 1;

  // Fetch floor plan on mount
  useEffect(() => {
    fetchFloorPlan(restaurantId);
  }, [restaurantId]);

  // Calculate totals for selected tables
  const selectionSummary = useMemo(() => {
    if (selectedTables.length === 0) {
      return { tableNumbers: '', totalCapacity: 0 };
    }
    const tableNumbers = selectedTables.map((t) => t.tableNumber).join(', ');
    const totalCapacity = selectedTables.reduce((sum, t) => sum + (t.capacity || 0), 0);
    return { tableNumbers, totalCapacity };
  }, [selectedTables]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await fetchFloorPlan(restaurantId);
    setIsRefreshing(false);
  };

  const handleTableSelect = (table) => {
    toggleTableSelection(table);
  };

  const handleConfirmSelection = () => {
    if (selectedTables.length === 0) return;
    confirmTableSelection(guestCount);
  };

  const handleBack = () => {
    clearSelectedTables();
    setCurrentScreen('start');
  };

  const handleGuestCountChange = (delta) => {
    setGuestCount((prev) => Math.max(1, Math.min(50, prev + delta)));
  };

  const handleRemoveTable = (tableId) => {
    const table = selectedTables.find((t) => t.id === tableId);
    if (table) {
      toggleTableSelection(table);
    }
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
                {t('pos.tables.selectTables', 'Select Tables')}
              </h1>
              <p className="text-gray-600">
                {t('pos.tables.selectTablesDesc', 'Choose one or more tables for your dine-in order')}
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
                  disabled={guestCount >= 50}
                  className={cn(
                    'w-10 h-10 flex items-center justify-center rounded-lg text-2xl font-bold transition-colors',
                    guestCount < 50
                      ? 'bg-white text-gray-700 hover:bg-gray-200 active:bg-gray-300'
                      : 'bg-gray-200 text-gray-400 cursor-not-allowed'
                  )}
                >
                  +
                </button>
              </div>
            </div>

            {/* Clear Selection Button */}
            {selectedTables.length > 0 && (
              <TouchButton
                variant="outline"
                size="lg"
                onClick={clearSelectedTables}
              >
                {t('pos.tables.clearSelection', 'Clear')}
              </TouchButton>
            )}

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
          selectedTables={selectedTables}
          onSelectTable={handleTableSelect}
          multiSelect={true}
        />
      </div>

      {/* Footer with Selection Summary */}
      <footer className="bg-white border-t border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Selected Tables Info */}
          <div className="flex-1">
            {selectedTables.length > 0 ? (
              <div className="flex items-center gap-4">
                {/* Selected Table Badges */}
                <div className="flex items-center gap-2 flex-wrap">
                  {selectedTables.map((table) => (
                    <div
                      key={table.id}
                      className="flex items-center gap-2 bg-blue-100 border-2 border-blue-400 rounded-xl px-3 py-2"
                    >
                      <span className="text-lg font-bold text-blue-900">
                        {table.tableNumber}
                      </span>
                      <span className="text-sm text-blue-700">
                        ({table.capacity})
                      </span>
                      <button
                        onClick={() => handleRemoveTable(table.id)}
                        className="ml-1 text-blue-600 hover:text-blue-800 hover:bg-blue-200 rounded-full p-1"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>

                {/* Summary */}
                <div className="ml-4 border-l border-gray-300 pl-4">
                  <p className="text-lg font-semibold text-gray-900">
                    {selectedTables.length === 1
                      ? t('pos.tables.tableSelected', 'Table {{number}} Selected', {
                          number: selectionSummary.tableNumbers,
                        })
                      : t('pos.tables.tablesSelected', '{{count}} Tables Selected', {
                          count: selectedTables.length,
                        })}
                  </p>
                  <p className="text-gray-600">
                    {t('pos.tables.totalCapacity', 'Total Capacity')}: {selectionSummary.totalCapacity} |{' '}
                    {t('pos.tables.guests', 'Guests')}: {guestCount}
                  </p>
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-3 text-gray-500">
                <Layers className="w-6 h-6" />
                <p className="text-lg">
                  {t('pos.tables.selectTablesPrompt', 'Tap on available tables to select them (multiple allowed)')}
                </p>
              </div>
            )}
          </div>

          {/* Confirm Button */}
          <TouchButton
            variant="primary"
            size="xl"
            onClick={handleConfirmSelection}
            disabled={selectedTables.length === 0}
            className="min-w-[200px]"
          >
            {selectedTables.length > 1
              ? t('pos.tables.confirmTables', 'Confirm {{count}} Tables', { count: selectedTables.length })
              : t('pos.tables.confirmTable', 'Confirm Table')}
          </TouchButton>
        </div>
      </footer>
    </div>
  );
};

export default TableSelectionScreen;
