import React, { useState } from 'react';
import { cn } from '../../lib/utils';
import { Delete } from 'lucide-react';

/**
 * NumericKeypad - Touch-optimized numeric input for payments, quantities, phone numbers
 * Large buttons (80x80px), decimal support, backspace
 */
const NumericKeypad = ({
  value = '',
  onValueChange,
  maxLength = 10,
  allowDecimal = true,
  label = '',
  placeholder = '0.00',
  className = '',
}) => {
  const [displayValue, setDisplayValue] = useState(value);

  const handleKeyPress = (key) => {
    // Haptic feedback
    if ('vibrate' in navigator) {
      navigator.vibrate(5);
    }

    let newValue = displayValue;

    if (key === 'backspace') {
      newValue = displayValue.slice(0, -1);
    } else if (key === 'clear') {
      newValue = '';
    } else if (key === '.') {
      if (!allowDecimal) return;
      if (displayValue.includes('.')) return;
      if (displayValue === '') {
        newValue = '0.';
      } else {
        newValue = displayValue + '.';
      }
    } else {
      // Number key
      if (displayValue.length >= maxLength) return;
      newValue = displayValue + key;
    }

    setDisplayValue(newValue);
    onValueChange(newValue);
  };

  const keys = [
    ['1', '2', '3'],
    ['4', '5', '6'],
    ['7', '8', '9'],
    [allowDecimal ? '.' : '', '0', 'backspace'],
  ];

  return (
    <div className={cn('flex flex-col gap-4', className)}>
      {/* Display */}
      <div className="bg-gray-100 rounded-lg p-6 border-2 border-gray-300">
        {label && (
          <label className="block text-sm font-medium text-gray-600 mb-2">
            {label}
          </label>
        )}
        <div className="text-right">
          <span className="text-5xl font-bold text-gray-900 tabular-nums">
            {displayValue || placeholder}
          </span>
        </div>
      </div>

      {/* Keypad */}
      <div className="grid grid-cols-3 gap-3">
        {keys.map((row, rowIndex) => (
          <React.Fragment key={rowIndex}>
            {row.map((key, keyIndex) => {
              if (key === '') {
                return <div key={`empty-${rowIndex}-${keyIndex}`} />;
              }

              if (key === 'backspace') {
                return (
                  <button
                    key={key}
                    onClick={() => handleKeyPress(key)}
                    className={cn(
                      'flex items-center justify-center',
                      'min-h-[80px] min-w-[80px]',
                      'bg-red-500 hover:bg-red-600 active:bg-red-700',
                      'text-white font-bold text-2xl',
                      'rounded-xl shadow-md',
                      'transition-all duration-150 active:scale-95',
                      'focus:outline-none focus:ring-4 focus:ring-red-200'
                    )}
                  >
                    <Delete className="w-8 h-8" />
                  </button>
                );
              }

              return (
                <button
                  key={key}
                  onClick={() => handleKeyPress(key)}
                  className={cn(
                    'flex items-center justify-center',
                    'min-h-[80px] min-w-[80px]',
                    'bg-white hover:bg-gray-50 active:bg-gray-100',
                    'text-gray-900 font-bold text-3xl',
                    'rounded-xl shadow-md border-2 border-gray-200',
                    'transition-all duration-150 active:scale-95',
                    'focus:outline-none focus:ring-4 focus:ring-blue-200'
                  )}
                >
                  {key}
                </button>
              );
            })}
          </React.Fragment>
        ))}
      </div>

      {/* Clear Button */}
      <button
        onClick={() => handleKeyPress('clear')}
        className={cn(
          'w-full min-h-[56px]',
          'bg-gray-200 hover:bg-gray-300 active:bg-gray-400',
          'text-gray-900 font-semibold text-lg',
          'rounded-lg',
          'transition-all duration-150 active:scale-98',
          'focus:outline-none focus:ring-4 focus:ring-gray-300'
        )}
      >
        Clear
      </button>
    </div>
  );
};

export default NumericKeypad;
