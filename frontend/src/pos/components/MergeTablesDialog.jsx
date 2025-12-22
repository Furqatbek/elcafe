import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X, Link2, AlertTriangle } from 'lucide-react';
import TouchButton from './TouchButton';
import { tablesAPI } from '../../services/api';

/**
 * MergeTablesDialog - Confirmation dialog for merging tables
 * Shows selected tables and allows choosing the main table
 */
const MergeTablesDialog = ({
  isOpen,
  onClose,
  selectedTables = [],
  onMergeComplete,
}) => {
  const { t } = useTranslation();
  const [mainTableId, setMainTableId] = useState(selectedTables[0]?.id || null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  if (!isOpen || selectedTables.length < 2) return null;

  const totalCapacity = selectedTables.reduce((sum, t) => sum + (t.capacity || 0), 0);

  const handleMerge = async () => {
    if (!mainTableId) {
      setError(t('pos.tables.merge.selectMain', 'Please select a main table'));
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const tableIdsToMerge = selectedTables
        .filter(t => t.id !== mainTableId)
        .map(t => t.id);

      await tablesAPI.merge({
        mainTableId,
        tableIdsToMerge,
      });

      if (onMergeComplete) {
        onMergeComplete();
      }
      onClose();
    } catch (err) {
      console.error('Failed to merge tables:', err);
      setError(err.response?.data?.message || t('pos.tables.merge.error', 'Failed to merge tables'));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-purple-100 rounded-full flex items-center justify-center">
              <Link2 className="w-5 h-5 text-purple-600" />
            </div>
            <h2 className="text-xl font-bold text-gray-900">
              {t('pos.tables.merge.title', 'Merge Tables')}
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-100 rounded-full transition-colors"
          >
            <X className="w-5 h-5 text-gray-500" />
          </button>
        </div>

        {/* Content */}
        <div className="px-6 py-4 space-y-4">
          <p className="text-gray-600">
            {t('pos.tables.merge.description', 'Select the main table. Other tables will be merged into it.')}
          </p>

          {/* Selected Tables */}
          <div className="space-y-2">
            {selectedTables.map((table) => (
              <label
                key={table.id}
                className={`flex items-center gap-3 p-3 rounded-lg border-2 cursor-pointer transition-colors ${
                  mainTableId === table.id
                    ? 'border-purple-500 bg-purple-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <input
                  type="radio"
                  name="mainTable"
                  value={table.id}
                  checked={mainTableId === table.id}
                  onChange={() => setMainTableId(table.id)}
                  className="w-4 h-4 text-purple-600 focus:ring-purple-500"
                />
                <div className="flex-1">
                  <span className="font-bold text-gray-900">
                    {t('pos.tables.table', 'Table')} {table.tableNumber}
                  </span>
                  {mainTableId === table.id && (
                    <span className="ml-2 text-xs font-medium text-purple-600 bg-purple-100 px-2 py-0.5 rounded-full">
                      {t('pos.tables.merge.main', 'Main')}
                    </span>
                  )}
                </div>
                <span className="text-sm text-gray-500">
                  {t('pos.tables.capacity', 'Capacity')}: {table.capacity}
                </span>
              </label>
            ))}
          </div>

          {/* Summary */}
          <div className="bg-gray-50 rounded-lg p-4">
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">{t('pos.tables.merge.tablesCount', 'Tables to merge')}</span>
              <span className="font-bold text-gray-900">{selectedTables.length}</span>
            </div>
            <div className="flex justify-between text-sm mt-2">
              <span className="text-gray-600">{t('pos.tables.merge.totalCapacity', 'Combined capacity')}</span>
              <span className="font-bold text-gray-900">{totalCapacity}</span>
            </div>
          </div>

          {/* Warning */}
          <div className="flex items-start gap-3 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
            <AlertTriangle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
            <p className="text-sm text-yellow-800">
              {t('pos.tables.merge.warning', 'Merged tables will share the same order and can be split later.')}
            </p>
          </div>

          {/* Error */}
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
              {error}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex gap-3 px-6 py-4 border-t border-gray-200 bg-gray-50">
          <TouchButton
            variant="secondary"
            size="large"
            onClick={onClose}
            disabled={isLoading}
            fullWidth
          >
            {t('common.cancel', 'Cancel')}
          </TouchButton>
          <TouchButton
            variant="primary"
            size="large"
            onClick={handleMerge}
            loading={isLoading}
            fullWidth
          >
            {t('pos.tables.merge.confirm', 'Merge Tables')}
          </TouchButton>
        </div>
      </div>
    </div>
  );
};

export default MergeTablesDialog;
