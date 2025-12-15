import React from 'react';
import { cn } from '../../lib/utils';

/**
 * TouchButton - Touch-optimized button component for POS interface
 * Minimum 48x48px touch target, haptic feedback, clear visual states
 */
const TouchButton = React.forwardRef(({
  children,
  variant = 'primary',
  size = 'medium',
  fullWidth = false,
  disabled = false,
  loading = false,
  icon = null,
  className = '',
  onClick,
  ...props
}, ref) => {

  const baseStyles = 'inline-flex items-center justify-center font-semibold rounded-lg transition-all duration-200 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 focus:outline-none focus:ring-4';

  const variants = {
    primary: 'bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800 focus:ring-blue-200',
    secondary: 'bg-gray-200 text-gray-900 hover:bg-gray-300 active:bg-gray-400 focus:ring-gray-200',
    success: 'bg-green-500 text-white hover:bg-green-600 active:bg-green-700 focus:ring-green-200',
    danger: 'bg-red-500 text-white hover:bg-red-600 active:bg-red-700 focus:ring-red-200',
    warning: 'bg-amber-500 text-white hover:bg-amber-600 active:bg-amber-700 focus:ring-amber-200',
    outline: 'bg-white border-2 border-gray-300 text-gray-900 hover:border-gray-400 hover:bg-gray-50 active:bg-gray-100 focus:ring-gray-200',
    ghost: 'bg-transparent text-gray-700 hover:bg-gray-100 active:bg-gray-200 focus:ring-gray-200',
  };

  const sizes = {
    small: 'min-h-[48px] min-w-[48px] px-4 py-3 text-sm gap-2',
    medium: 'min-h-[56px] min-w-[56px] px-6 py-4 text-base gap-2',
    large: 'min-h-[64px] min-w-[64px] px-8 py-5 text-lg gap-3',
    xl: 'min-h-[80px] min-w-[80px] px-10 py-6 text-xl gap-3',
  };

  const handleClick = (e) => {
    if (disabled || loading) return;

    // Haptic feedback for touch devices
    if ('vibrate' in navigator) {
      navigator.vibrate(10);
    }

    onClick?.(e);
  };

  return (
    <button
      ref={ref}
      className={cn(
        baseStyles,
        variants[variant],
        sizes[size],
        fullWidth && 'w-full',
        className
      )}
      disabled={disabled || loading}
      onClick={handleClick}
      {...props}
    >
      {loading && (
        <svg
          className="animate-spin h-5 w-5"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
      )}
      {!loading && icon && <span className="flex-shrink-0">{icon}</span>}
      <span>{children}</span>
    </button>
  );
});

TouchButton.displayName = 'TouchButton';

export default TouchButton;
