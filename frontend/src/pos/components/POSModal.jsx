import React, { useEffect } from 'react';
import { cn } from '../../lib/utils';
import { X } from 'lucide-react';
import * as Dialog from '@radix-ui/react-dialog';

/**
 * POSModal - Full-screen or large modal for POS workflows
 * Touch-optimized close button, backdrop, keyboard-free operation
 */
const POSModal = ({
  open,
  onOpenChange,
  title,
  children,
  footer,
  size = 'large', // 'medium' | 'large' | 'full'
  showCloseButton = true,
  className = '',
}) => {
  // Prevent body scroll when modal is open
  useEffect(() => {
    if (open) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [open]);

  const sizeClasses = {
    medium: 'max-w-2xl',
    large: 'max-w-4xl',
    full: 'max-w-full m-0 h-screen',
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        {/* Backdrop */}
        <Dialog.Overlay className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[1300] animate-in fade-in" />

        {/* Modal Content */}
        <Dialog.Content
          className={cn(
            'fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2',
            'w-[calc(100vw-2rem)] bg-white rounded-2xl shadow-2xl',
            'z-[1301] animate-in fade-in zoom-in-95',
            'flex flex-col',
            size === 'full' ? 'h-screen rounded-none' : 'max-h-[calc(100vh-2rem)]',
            sizeClasses[size],
            className
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-5 border-b border-gray-200 flex-shrink-0">
            <Dialog.Title className="text-2xl font-bold text-gray-900">
              {title}
            </Dialog.Title>

            {showCloseButton && (
              <Dialog.Close asChild>
                <button
                  className={cn(
                    'flex items-center justify-center',
                    'min-h-[48px] min-w-[48px]',
                    'text-gray-500 hover:text-gray-700 hover:bg-gray-100',
                    'rounded-lg transition-colors active:scale-95',
                    'focus:outline-none focus:ring-4 focus:ring-gray-200'
                  )}
                  aria-label="Close"
                >
                  <X className="w-6 h-6" />
                </button>
              </Dialog.Close>
            )}
          </div>

          {/* Body */}
          <div className="flex-1 overflow-y-auto px-6 py-6">
            {children}
          </div>

          {/* Footer */}
          {footer && (
            <div className="flex-shrink-0 px-6 py-5 border-t border-gray-200 bg-gray-50">
              {footer}
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
};

export default POSModal;
