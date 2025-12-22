import React from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Users, Clock, Utensils, AlertCircle, CheckCircle, Check } from 'lucide-react';

/**
 * TableCard - Interactive table component for floor plan display
 * Shows table status, capacity, and current order info
 * Supports multi-selection mode with visual checkmark
 */
const TableCard = ({
  table,
  onSelect,
  isSelected = false,
  multiSelect = false,
  className = '',
  style = {},
}) => {
  const { t } = useTranslation();

  const {
    id,
    tableNumber,
    tableName,
    status,
    capacity,
    section,
    currentOrderNumber,
    width = 100,
    height = 100,
  } = table;

  const getStatusConfig = (tableStatus) => {
    const configs = {
      AVAILABLE: {
        label: t('pos.tables.available', 'Available'),
        bgColor: 'bg-green-100',
        borderColor: 'border-green-400',
        textColor: 'text-green-700',
        icon: <CheckCircle className="w-4 h-4" />,
        selectable: true,
      },
      OCCUPIED: {
        label: t('pos.tables.occupied', 'Occupied'),
        bgColor: 'bg-red-100',
        borderColor: 'border-red-400',
        textColor: 'text-red-700',
        icon: <Utensils className="w-4 h-4" />,
        selectable: false,
      },
      RESERVED: {
        label: t('pos.tables.reserved', 'Reserved'),
        bgColor: 'bg-yellow-100',
        borderColor: 'border-yellow-400',
        textColor: 'text-yellow-700',
        icon: <Clock className="w-4 h-4" />,
        selectable: false,
      },
      CLEANING: {
        label: t('pos.tables.cleaning', 'Cleaning'),
        bgColor: 'bg-blue-100',
        borderColor: 'border-blue-400',
        textColor: 'text-blue-700',
        icon: <Clock className="w-4 h-4" />,
        selectable: false,
      },
      OUT_OF_SERVICE: {
        label: t('pos.tables.outOfService', 'Out of Service'),
        bgColor: 'bg-gray-100',
        borderColor: 'border-gray-400',
        textColor: 'text-gray-700',
        icon: <AlertCircle className="w-4 h-4" />,
        selectable: false,
      },
    };
    return configs[tableStatus] || configs.AVAILABLE;
  };

  const statusConfig = getStatusConfig(status);

  const handleSelect = () => {
    if (!statusConfig.selectable) return;

    // Haptic feedback
    if ('vibrate' in navigator) {
      navigator.vibrate(10);
    }

    onSelect(table);
  };

  return (
    <button
      onClick={handleSelect}
      disabled={!statusConfig.selectable}
      className={cn(
        'absolute flex flex-col items-center justify-center rounded-lg border-2 transition-all duration-200',
        statusConfig.bgColor,
        statusConfig.borderColor,
        statusConfig.selectable && 'hover:shadow-lg hover:scale-105 cursor-pointer',
        !statusConfig.selectable && 'cursor-not-allowed opacity-80',
        isSelected && 'ring-4 ring-blue-500 ring-offset-2 scale-105',
        'focus:outline-none focus:ring-4 focus:ring-blue-200',
        className
      )}
      style={{
        width: `${width}px`,
        height: `${height}px`,
        ...style,
      }}
    >
      {/* Table Number */}
      <span className="text-2xl font-bold text-gray-900">
        {tableNumber}
      </span>

      {/* Table Name (if different from number) */}
      {tableName && tableName !== tableNumber && (
        <span className="text-xs text-gray-600 mt-0.5 line-clamp-1 px-1">
          {tableName}
        </span>
      )}

      {/* Capacity */}
      <div className="flex items-center gap-1 mt-1 text-gray-600">
        <Users className="w-3 h-3" />
        <span className="text-xs">{capacity}</span>
      </div>

      {/* Status Badge */}
      <div className={cn(
        'flex items-center gap-1 mt-1 px-2 py-0.5 rounded-full text-xs font-medium',
        statusConfig.textColor
      )}>
        {statusConfig.icon}
        <span className="hidden sm:inline">{statusConfig.label}</span>
      </div>

      {/* Current Order Number (if occupied) */}
      {currentOrderNumber && (
        <div className="absolute -top-2 -right-2 bg-blue-600 text-white text-xs font-bold px-2 py-1 rounded-full">
          #{currentOrderNumber}
        </div>
      )}

      {/* Selection Checkmark (for multi-select mode) */}
      {isSelected && multiSelect && (
        <div className="absolute -top-2 -left-2 w-7 h-7 bg-blue-600 text-white rounded-full flex items-center justify-center shadow-lg">
          <Check className="w-5 h-5" />
        </div>
      )}
    </button>
  );
};

export default TableCard;
