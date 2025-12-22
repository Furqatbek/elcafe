import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Heart, Coins } from 'lucide-react';
import NumericKeypad from './NumericKeypad';
import TouchButton from './TouchButton';

/**
 * TipSelectionComponent - Tip selection with percentage presets and custom amount
 * Shows suggested tip amounts based on subtotal
 */
const TipSelectionComponent = ({
  subtotal,
  selectedTip = 0,
  onTipChange,
  showKeypad = false,
  className = '',
}) => {
  const { t } = useTranslation();
  const [tipMode, setTipMode] = useState('preset'); // preset, custom
  const [customAmount, setCustomAmount] = useState('');

  // Tip percentage presets
  const tipPresets = [
    { percentage: 0, label: t('pos.payment.noTip', 'No Tip') },
    { percentage: 10, label: '10%' },
    { percentage: 15, label: '15%' },
    { percentage: 18, label: '18%' },
    { percentage: 20, label: '20%' },
    { percentage: 25, label: '25%' },
  ];

  const calculateTipFromPercentage = (percentage) => {
    return (subtotal * percentage) / 100;
  };

  const handlePresetSelect = (percentage) => {
    setTipMode('preset');
    const tipAmount = calculateTipFromPercentage(percentage);
    onTipChange(tipAmount);
  };

  const handleCustomTipChange = (value) => {
    setCustomAmount(value);
    const tipAmount = parseFloat(value) || 0;
    onTipChange(tipAmount);
  };

  const handleCustomMode = () => {
    setTipMode('custom');
    setCustomAmount(selectedTip > 0 ? selectedTip.toFixed(2) : '');
  };

  // Find which preset is selected (if any)
  const getSelectedPreset = () => {
    for (const preset of tipPresets) {
      const tipForPreset = calculateTipFromPercentage(preset.percentage);
      if (Math.abs(tipForPreset - selectedTip) < 0.01) {
        return preset.percentage;
      }
    }
    return null;
  };

  const selectedPreset = getSelectedPreset();
  const totalWithTip = subtotal + selectedTip;

  return (
    <div className={cn('space-y-4', className)}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Heart className="w-5 h-5 text-pink-500" />
          <span className="text-lg font-semibold text-gray-900">
            {t('pos.payment.addTip', 'Add a Tip')}
          </span>
        </div>
        {selectedTip > 0 && (
          <span className="text-lg font-bold text-green-600">
            +{selectedTip.toFixed(2)}
          </span>
        )}
      </div>

      {/* Tip Presets */}
      {tipMode === 'preset' && (
        <div className="grid grid-cols-3 gap-2">
          {tipPresets.map((preset) => {
            const tipAmount = calculateTipFromPercentage(preset.percentage);
            const isSelected = selectedPreset === preset.percentage;

            return (
              <button
                key={preset.percentage}
                onClick={() => handlePresetSelect(preset.percentage)}
                className={cn(
                  'flex flex-col items-center justify-center',
                  'min-h-[80px] p-3',
                  'rounded-xl border-2 transition-all',
                  isSelected
                    ? 'bg-green-100 border-green-500 text-green-800'
                    : 'bg-white border-gray-200 hover:border-green-300 hover:bg-green-50',
                  'focus:outline-none focus:ring-2 focus:ring-green-200'
                )}
              >
                <span className="text-lg font-bold">{preset.label}</span>
                {preset.percentage > 0 && (
                  <span className="text-sm text-gray-600">
                    {tipAmount.toFixed(2)}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* Custom Tip with Keypad */}
      {tipMode === 'custom' && showKeypad && (
        <div className="space-y-3">
          <NumericKeypad
            value={customAmount}
            onValueChange={handleCustomTipChange}
            label={t('pos.payment.customTipAmount', 'Custom Tip Amount')}
            placeholder="0.00"
            allowDecimal={true}
            maxLength={6}
          />
          <TouchButton
            variant="outline"
            size="medium"
            fullWidth
            onClick={() => setTipMode('preset')}
          >
            {t('pos.payment.backToPresets', 'Back to Presets')}
          </TouchButton>
        </div>
      )}

      {/* Custom Amount Button (when not showing keypad) */}
      {tipMode === 'preset' && (
        <TouchButton
          variant="outline"
          size="medium"
          fullWidth
          onClick={handleCustomMode}
          icon={<Coins className="w-5 h-5" />}
        >
          {t('pos.payment.customAmount', 'Custom Amount')}
        </TouchButton>
      )}

      {/* Custom Amount Input (simple mode without full keypad) */}
      {tipMode === 'custom' && !showKeypad && (
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <input
              type="number"
              step="0.01"
              min="0"
              value={customAmount}
              onChange={(e) => handleCustomTipChange(e.target.value)}
              placeholder="0.00"
              className={cn(
                'flex-1 text-3xl font-bold text-center',
                'p-4 rounded-xl border-2 border-gray-300',
                'focus:outline-none focus:border-green-500 focus:ring-2 focus:ring-green-200'
              )}
            />
          </div>
          <TouchButton
            variant="outline"
            size="medium"
            fullWidth
            onClick={() => setTipMode('preset')}
          >
            {t('pos.payment.backToPresets', 'Back to Presets')}
          </TouchButton>
        </div>
      )}

      {/* Total Summary */}
      <div className="bg-gray-50 rounded-xl p-4 border border-gray-200">
        <div className="flex justify-between items-center text-sm text-gray-600 mb-2">
          <span>{t('pos.cart.subtotal', 'Subtotal')}</span>
          <span>{subtotal.toFixed(2)}</span>
        </div>
        <div className="flex justify-between items-center text-sm text-gray-600 mb-2">
          <span>{t('pos.payment.tip', 'Tip')}</span>
          <span className={selectedTip > 0 ? 'text-green-600 font-medium' : ''}>
            {selectedTip.toFixed(2)}
          </span>
        </div>
        <div className="flex justify-between items-center text-lg font-bold text-gray-900 pt-2 border-t border-gray-200">
          <span>{t('pos.cart.total', 'Total')}</span>
          <span>{totalWithTip.toFixed(2)}</span>
        </div>
      </div>
    </div>
  );
};

export default TipSelectionComponent;
