import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X, Delete } from 'lucide-react';

/**
 * WeightInputModal - Numeric keypad for entering the weight of a sell-by-weight product.
 *
 * Props:
 *   product    - Product object (must have price, name, weightUnit, minWeight, maxWeight)
 *   onConfirm  - (weightAmount: number) => void
 *   onClose    - () => void
 */
const WeightInputModal = ({ product, onConfirm, onClose }) => {
  const { t } = useTranslation();
  const [input, setInput] = useState('0');

  const unit = product.weightUnit || 'KG';
  const minWeight = product.minWeight || 0.01;
  const maxWeight = product.maxWeight || 99;
  const pricePerUnit = product.price;

  const numericValue = parseFloat(input) || 0;
  const total = (pricePerUnit * numericValue).toFixed(2);
  const isValid = numericValue >= minWeight && numericValue <= maxWeight;

  const handleKey = (key) => {
    if (key === 'backspace') {
      setInput(prev => {
        const next = prev.slice(0, -1);
        return next === '' || next === '-' ? '0' : next;
      });
      return;
    }

    setInput(prev => {
      if (prev === '0' && key !== '.') return key;
      // Allow only one decimal point
      if (key === '.' && prev.includes('.')) return prev;
      // Max 4 decimal places
      const dotIndex = prev.indexOf('.');
      if (dotIndex !== -1 && prev.length - dotIndex > 4) return prev;
      // Max total length of 7
      if (prev.replace('.', '').length >= 7) return prev;
      return prev + key;
    });
  };

  const handleConfirm = () => {
    if (!isValid) return;
    onConfirm(numericValue);
  };

  const keys = [
    ['1', '2', '3'],
    ['4', '5', '6'],
    ['7', '8', '9'],
    ['.', '0', 'backspace'],
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm flex flex-col overflow-hidden">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <div>
            <h2 className="font-bold text-lg text-gray-900">{product.name}</h2>
            <p className="text-sm text-gray-500">
              {pricePerUnit.toFixed(2)} {t('pos.weight.perUnit', 'per')} {unit}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Weight display */}
        <div className="px-5 pt-5 pb-3 text-center">
          <div className="bg-gray-100 rounded-xl px-4 py-3 flex items-baseline justify-center gap-2">
            <span className="text-4xl font-bold text-gray-900 tracking-wide font-mono">
              {input}
            </span>
            <span className="text-xl font-semibold text-gray-500">{unit}</span>
          </div>

          {/* Price preview */}
          <div className="mt-3 text-lg font-semibold text-gray-700">
            {isValid
              ? <span className="text-blue-600">{total}</span>
              : numericValue > maxWeight
                ? <span className="text-red-500 text-sm">{t('pos.weight.maxExceeded', 'Max: {{max}} {{unit}}', { max: maxWeight, unit })}</span>
                : <span className="text-gray-400">{t('pos.weight.enterWeight', 'Enter weight')}</span>
            }
          </div>
        </div>

        {/* Numeric keypad */}
        <div className="px-4 pb-4 grid grid-cols-3 gap-2">
          {keys.flat().map((key) => (
            <button
              key={key}
              onClick={() => handleKey(key)}
              className={`
                h-14 rounded-xl text-xl font-semibold transition-all active:scale-95
                ${key === 'backspace'
                  ? 'bg-red-50 text-red-500 hover:bg-red-100'
                  : 'bg-gray-100 text-gray-900 hover:bg-gray-200'}
              `}
            >
              {key === 'backspace' ? <Delete className="w-5 h-5 mx-auto" /> : key}
            </button>
          ))}
        </div>

        {/* Confirm */}
        <div className="px-4 pb-5">
          <button
            onClick={handleConfirm}
            disabled={!isValid}
            className="w-full h-14 rounded-xl text-white font-bold text-lg bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:opacity-40 disabled:cursor-not-allowed transition-all active:scale-99"
          >
            {t('pos.weight.addToCart', 'Add {{weight}} {{unit}} to cart', { weight: numericValue, unit })}
          </button>
        </div>
      </div>
    </div>
  );
};

export default WeightInputModal;
