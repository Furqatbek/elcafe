import React, { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import TableCard from './TableCard';
import { Grid, LayoutGrid, Filter } from 'lucide-react';

/**
 * FloorPlanView - Visual table layout component
 * Displays tables in either floor plan (positioned) or grid view
 * Supports both single and multi-table selection
 */
const FloorPlanView = ({
  tables,
  sections = [],
  selectedTable, // Legacy single selection (deprecated)
  selectedTables = [], // Multi-selection (preferred)
  onSelectTable,
  multiSelect = false,
  className = '',
}) => {
  const { t } = useTranslation();
  const [viewMode, setViewMode] = useState('grid'); // 'floor' | 'grid'
  const [selectedSection, setSelectedSection] = useState(null);

  // Normalize selection to array for consistent handling
  const selectedTableIds = useMemo(() => {
    if (selectedTables && selectedTables.length > 0) {
      return new Set(selectedTables.map((t) => t.id));
    }
    if (selectedTable) {
      return new Set([selectedTable.id]);
    }
    return new Set();
  }, [selectedTable, selectedTables]);

  // Filter tables by section and sort by table number
  const filteredTables = useMemo(() => {
    let result = tables;
    if (selectedSection) {
      result = tables.filter((table) => table.section === selectedSection);
    }
    // Sort by table number (numeric comparison)
    return [...result].sort((a, b) => {
      const numA = parseInt(a.tableNumber) || 0;
      const numB = parseInt(b.tableNumber) || 0;
      return numA - numB;
    });
  }, [tables, selectedSection]);

  // Check if any table has position data for floor plan view
  const hasPositionData = useMemo(() => {
    return tables.some((table) => table.positionX != null && table.positionY != null);
  }, [tables]);

  // Calculate floor plan bounds
  const floorPlanBounds = useMemo(() => {
    if (!hasPositionData) return { width: 800, height: 600 };

    let maxX = 0;
    let maxY = 0;

    tables.forEach((table) => {
      if (table.positionX != null && table.positionY != null) {
        maxX = Math.max(maxX, table.positionX + (table.width || 100));
        maxY = Math.max(maxY, table.positionY + (table.height || 100));
      }
    });

    return {
      width: Math.max(maxX + 50, 800),
      height: Math.max(maxY + 50, 600),
    };
  }, [tables, hasPositionData]);

  // Get table statistics
  const tableStats = useMemo(() => {
    const stats = { available: 0, occupied: 0, reserved: 0, total: 0, selected: selectedTableIds.size };
    filteredTables.forEach((table) => {
      stats.total++;
      if (table.status === 'AVAILABLE') stats.available++;
      else if (table.status === 'OCCUPIED') stats.occupied++;
      else if (table.status === 'RESERVED') stats.reserved++;
    });
    return stats;
  }, [filteredTables, selectedTableIds]);

  const isTableSelected = (tableId) => selectedTableIds.has(tableId);

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header with controls */}
      <div className="flex items-center justify-between p-4 bg-white border-b border-gray-200">
        {/* Section Filter */}
        <div className="flex items-center gap-2">
          {sections.length > 0 && (
            <>
              <Filter className="w-5 h-5 text-gray-400" />
              <select
                value={selectedSection || ''}
                onChange={(e) => setSelectedSection(e.target.value || null)}
                className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">{t('pos.tables.allSections', 'All Sections')}</option>
                {sections.map((section) => (
                  <option key={section} value={section}>
                    {section}
                  </option>
                ))}
              </select>
            </>
          )}
        </div>

        {/* Stats */}
        <div className="flex items-center gap-4 text-sm">
          <div className="flex items-center gap-2">
            <span className="w-3 h-3 rounded-full bg-green-500" />
            <span>{t('pos.tables.available', 'Available')}: {tableStats.available}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-3 h-3 rounded-full bg-red-500" />
            <span>{t('pos.tables.occupied', 'Occupied')}: {tableStats.occupied}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-3 h-3 rounded-full bg-yellow-500" />
            <span>{t('pos.tables.reserved', 'Reserved')}: {tableStats.reserved}</span>
          </div>
          {multiSelect && tableStats.selected > 0 && (
            <div className="flex items-center gap-2 font-medium text-blue-600">
              <span className="w-3 h-3 rounded-full bg-blue-500" />
              <span>{t('pos.tables.selected', 'Selected')}: {tableStats.selected}</span>
            </div>
          )}
        </div>

        {/* View Toggle */}
        {hasPositionData && (
          <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
            <button
              onClick={() => setViewMode('grid')}
              className={cn(
                'p-2 rounded-md transition-colors',
                viewMode === 'grid' ? 'bg-white shadow-sm text-blue-600' : 'text-gray-500 hover:text-gray-700'
              )}
              title={t('pos.tables.gridView', 'Grid View')}
            >
              <Grid className="w-5 h-5" />
            </button>
            <button
              onClick={() => setViewMode('floor')}
              className={cn(
                'p-2 rounded-md transition-colors',
                viewMode === 'floor' ? 'bg-white shadow-sm text-blue-600' : 'text-gray-500 hover:text-gray-700'
              )}
              title={t('pos.tables.floorPlan', 'Floor Plan')}
            >
              <LayoutGrid className="w-5 h-5" />
            </button>
          </div>
        )}
      </div>

      {/* Tables Display */}
      <div className="flex-1 overflow-auto p-4 bg-gray-50">
        {viewMode === 'grid' || !hasPositionData ? (
          // Grid View
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {filteredTables.map((table) => (
              <div key={table.id} className="relative" style={{ height: '120px' }}>
                <TableCard
                  table={{ ...table, width: '100%', height: '100%' }}
                  onSelect={onSelectTable}
                  isSelected={isTableSelected(table.id)}
                  multiSelect={multiSelect}
                  className="!absolute !relative w-full h-full"
                  style={{ position: 'relative', width: '100%', height: '100%' }}
                />
              </div>
            ))}
          </div>
        ) : (
          // Floor Plan View
          <div
            className="relative bg-white rounded-xl border-2 border-gray-200 shadow-inner mx-auto"
            style={{
              width: `${floorPlanBounds.width}px`,
              height: `${floorPlanBounds.height}px`,
              minWidth: '100%',
            }}
          >
            {/* Grid lines for visual reference */}
            <div className="absolute inset-0 opacity-10">
              <svg width="100%" height="100%">
                <defs>
                  <pattern id="grid" width="50" height="50" patternUnits="userSpaceOnUse">
                    <path d="M 50 0 L 0 0 0 50" fill="none" stroke="gray" strokeWidth="0.5" />
                  </pattern>
                </defs>
                <rect width="100%" height="100%" fill="url(#grid)" />
              </svg>
            </div>

            {/* Tables */}
            {filteredTables.map((table) => (
              <TableCard
                key={table.id}
                table={table}
                onSelect={onSelectTable}
                isSelected={isTableSelected(table.id)}
                multiSelect={multiSelect}
                style={{
                  left: `${table.positionX || 0}px`,
                  top: `${table.positionY || 0}px`,
                }}
              />
            ))}
          </div>
        )}

        {/* Empty State */}
        {filteredTables.length === 0 && (
          <div className="flex flex-col items-center justify-center h-64 text-gray-500">
            <LayoutGrid className="w-16 h-16 mb-4 opacity-50" />
            <p className="text-lg font-medium">
              {t('pos.tables.noTables', 'No tables found')}
            </p>
            <p className="text-sm">
              {selectedSection
                ? t('pos.tables.noTablesInSection', 'No tables in this section')
                : t('pos.tables.addTablesHint', 'Add tables in the settings')}
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default FloorPlanView;
