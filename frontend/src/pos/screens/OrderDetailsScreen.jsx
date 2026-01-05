import React, { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { ChevronLeft, Phone, User, MapPin, Hash, Loader2 } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';
import { useSearchParams } from 'react-router-dom';

/**
 * InputField - Extracted component to prevent focus loss on re-render
 */
const InputField = React.memo(({ label, icon, value, onChange, type = 'text', required = false, placeholder, error }) => (
  <div>
    <label className="block text-sm font-semibold text-gray-700 mb-2">
      {label} {required && <span className="text-red-500">*</span>}
    </label>
    <div className="relative">
      {icon && (
        <div className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400">
          {icon}
        </div>
      )}
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={cn(
          'w-full min-h-[56px] px-4 py-3',
          icon && 'pl-12',
          'bg-white border-2 rounded-lg',
          'text-base text-gray-900 placeholder-gray-500',
          error
            ? 'border-red-400 focus:border-red-500'
            : 'border-gray-300 focus:border-blue-400',
          'focus:outline-none transition-colors'
        )}
      />
    </div>
    {error && (
      <p className="mt-2 text-sm text-red-600">{error}</p>
    )}
  </div>
));

InputField.displayName = 'InputField';

/**
 * OrderDetailsScreen - Collect order-type specific information
 * Delivery: customer info, address (all required)
 * Takeaway: customer name, phone (required)
 * Dine-in: table number, guest count required; customer info optional
 */
const OrderDetailsScreen = () => {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const restaurantId = searchParams.get('restaurantId') || '1';
  const { currentOrder, customer, selectedTable, setCustomerInfo, setCurrentScreen, submitOrder, setError } = usePOSStore();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [formData, setFormData] = useState({
    name: customer.name || '',
    phone: customer.phone || '',
    email: customer.email || '',
    address: customer.address?.street || '',
    city: customer.address?.city || '',
    deliveryInstructions: customer.deliveryInstructions || '',
    // Pre-fill table info from selectedTable if available
    tableNumber: customer.tableNumber || selectedTable?.tableNumber || '',
    guestCount: customer.guestCount || selectedTable?.guestCount || 2,
  });

  const [errors, setErrors] = useState({});

  const handleChange = useCallback((field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    setErrors(prev => ({ ...prev, [field]: null }));
  }, []);

  const validateForm = () => {
    const newErrors = {};

    // Validation by order type
    if (currentOrder.type === 'DELIVERY') {
      // Delivery requires name, phone, and address
      if (!formData.name.trim()) {
        newErrors.name = t('pos.details.errors.nameRequired', 'Name is required');
      }
      if (!formData.phone.trim()) {
        newErrors.phone = t('pos.details.errors.phoneRequired', 'Phone number is required');
      }
      if (!formData.address.trim()) {
        newErrors.address = t('pos.details.errors.addressRequired', 'Address is required');
      }
      if (!formData.city.trim()) {
        newErrors.city = t('pos.details.errors.cityRequired', 'City is required');
      }
    } else if (currentOrder.type === 'TAKEAWAY') {
      // Takeaway requires name and phone
      if (!formData.name.trim()) {
        newErrors.name = t('pos.details.errors.nameRequired', 'Name is required');
      }
      if (!formData.phone.trim()) {
        newErrors.phone = t('pos.details.errors.phoneRequired', 'Phone number is required');
      }
    } else if (currentOrder.type === 'DINE_IN') {
      // Dine-in only requires table number and guest count (customer info is optional)
      if (!formData.tableNumber || !String(formData.tableNumber).trim()) {
        newErrors.tableNumber = t('pos.details.errors.tableRequired', 'Table number is required');
      }
      if (!formData.guestCount || formData.guestCount < 1) {
        newErrors.guestCount = t('pos.details.errors.guestCountMin', 'Guest count must be at least 1');
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleCreateOrder = async () => {
    if (!validateForm()) {
      return;
    }

    // Save customer info to store
    const customerData = {
      name: formData.name || '',
      phone: formData.phone || '',
      email: formData.email || '',
    };

    if (currentOrder.type === 'DELIVERY') {
      customerData.address = {
        street: formData.address,
        city: formData.city,
      };
      customerData.deliveryInstructions = formData.deliveryInstructions;
    } else if (currentOrder.type === 'DINE_IN') {
      customerData.tableNumber = formData.tableNumber;
      customerData.guestCount = parseInt(formData.guestCount);
    }

    setCustomerInfo(customerData);

    // Submit order directly without payment
    setIsSubmitting(true);
    try {
      const result = await submitOrder(restaurantId);
      if (result.success) {
        setCurrentScreen('start');
      } else {
        setError(result.error || t('pos.errors.createOrderFailed', 'Failed to create order'));
      }
    } catch (error) {
      setError(error.message || t('pos.errors.createOrderFailed', 'Failed to create order'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-6 py-4 flex-shrink-0">
        <div className="flex items-center justify-between">
          <TouchButton
            variant="ghost"
            size="medium"
            onClick={() => setCurrentScreen('cart')}
            icon={<ChevronLeft className="w-6 h-6" />}
          >
            {t('pos.details.backToCart', 'Back to Cart')}
          </TouchButton>

          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-900">{t('pos.details.title', 'Order Details')}</h1>
            <p className="text-sm text-gray-600">{t('pos.cart.orderTypeLabel', '{{type}} Order', { type: currentOrder.type })}</p>
          </div>

          <div className="w-[140px]" /> {/* Spacer */}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-2xl mx-auto space-y-6">
          {/* Dine-In: Table Info First */}
          {currentOrder.type === 'DINE_IN' && (
            <div className="bg-white rounded-xl p-6 border-2 border-gray-200 space-y-4">
              <h2 className="text-xl font-bold text-gray-900 mb-4">{t('pos.details.tableInfo', 'Table Information')}</h2>

              <InputField
                label={t('pos.details.tableNumber', 'Table Number')}
                icon={<Hash className="w-5 h-5" />}
                value={formData.tableNumber}
                onChange={(v) => handleChange('tableNumber', v)}
                required
                placeholder={t('pos.details.tablePlaceholder', 'e.g., 12, A5')}
                error={errors.tableNumber}
              />

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">
                  {t('pos.details.numberOfGuests', 'Number of Guests')} <span className="text-red-500">*</span>
                </label>
                <div className="flex items-center gap-3">
                  <TouchButton
                    variant="secondary"
                    size="medium"
                    onClick={() => handleChange('guestCount', Math.max(1, formData.guestCount - 1))}
                    disabled={formData.guestCount <= 1}
                  >
                    -
                  </TouchButton>

                  <div className="flex-1 text-center">
                    <input
                      type="number"
                      value={formData.guestCount}
                      onChange={(e) => handleChange('guestCount', parseInt(e.target.value) || 1)}
                      min="1"
                      max="99"
                      className={cn(
                        'w-full min-h-[56px] px-4 py-3 text-center',
                        'bg-white border-2 rounded-lg',
                        'text-2xl font-bold text-gray-900',
                        errors.guestCount
                          ? 'border-red-400'
                          : 'border-gray-300 focus:border-blue-400',
                        'focus:outline-none'
                      )}
                    />
                  </div>

                  <TouchButton
                    variant="secondary"
                    size="medium"
                    onClick={() => handleChange('guestCount', Math.min(99, formData.guestCount + 1))}
                    disabled={formData.guestCount >= 99}
                  >
                    +
                  </TouchButton>
                </div>
                {errors.guestCount && (
                  <p className="mt-2 text-sm text-red-600">{errors.guestCount}</p>
                )}
              </div>
            </div>
          )}

          {/* Customer Info - Required for Delivery/Takeaway, Optional for Dine-In */}
          <div className="bg-white rounded-xl p-6 border-2 border-gray-200 space-y-4">
            <h2 className="text-xl font-bold text-gray-900 mb-4">
              {t('pos.details.customerInfo', 'Customer Information')}
              {currentOrder.type === 'DINE_IN' && (
                <span className="ml-2 text-sm font-normal text-gray-500">
                  ({t('common.optional', 'Optional')})
                </span>
              )}
            </h2>

            <InputField
              label={t('pos.details.customerName', 'Customer Name')}
              icon={<User className="w-5 h-5" />}
              value={formData.name}
              onChange={(v) => handleChange('name', v)}
              required={currentOrder.type !== 'DINE_IN'}
              placeholder={t('pos.details.namePlaceholder', 'Enter customer name')}
              error={errors.name}
            />

            {(currentOrder.type === 'DELIVERY' || currentOrder.type === 'TAKEAWAY') && (
              <>
                <InputField
                  label={t('pos.details.phoneNumber', 'Phone Number')}
                  icon={<Phone className="w-5 h-5" />}
                  value={formData.phone}
                  onChange={(v) => handleChange('phone', v)}
                  type="tel"
                  required
                  placeholder={t('pos.details.phonePlaceholder', '(555) 123-4567')}
                  error={errors.phone}
                />

                <InputField
                  label={t('pos.details.emailOptional', 'Email (Optional)')}
                  value={formData.email}
                  onChange={(v) => handleChange('email', v)}
                  type="email"
                  placeholder={t('pos.details.emailPlaceholder', 'customer@example.com')}
                />
              </>
            )}
          </div>

          {/* Delivery-Specific Fields */}
          {currentOrder.type === 'DELIVERY' && (
            <div className="bg-white rounded-xl p-6 border-2 border-gray-200 space-y-4">
              <h2 className="text-xl font-bold text-gray-900 mb-4">{t('pos.details.deliveryAddress', 'Delivery Address')}</h2>

              <InputField
                label={t('pos.details.streetAddress', 'Street Address')}
                icon={<MapPin className="w-5 h-5" />}
                value={formData.address}
                onChange={(v) => handleChange('address', v)}
                required
                placeholder={t('pos.details.addressPlaceholder', '123 Main Street')}
                error={errors.address}
              />

              <InputField
                label={t('pos.details.city', 'City')}
                value={formData.city}
                onChange={(v) => handleChange('city', v)}
                required
                placeholder={t('pos.details.cityPlaceholder', 'Tashkent')}
                error={errors.city}
              />

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">
                  {t('pos.details.deliveryInstructions', 'Delivery Instructions (Optional)')}
                </label>
                <textarea
                  value={formData.deliveryInstructions}
                  onChange={(e) => handleChange('deliveryInstructions', e.target.value)}
                  placeholder={t('pos.details.instructionsPlaceholder', 'E.g., Ring doorbell, Leave at door...')}
                  rows={3}
                  className={cn(
                    'w-full px-4 py-3 rounded-lg',
                    'bg-white border-2 border-gray-300',
                    'text-base text-gray-900 placeholder-gray-500',
                    'focus:border-blue-400 focus:outline-none',
                    'resize-none'
                  )}
                />
              </div>
            </div>
          )}

          {/* Order Summary Preview */}
          <div className="bg-blue-50 border-2 border-blue-200 rounded-xl p-6">
            <h3 className="text-lg font-bold text-blue-900 mb-3">{t('pos.cart.orderSummary', 'Order Summary')}</h3>
            <div className="space-y-2 text-blue-800">
              <div className="flex justify-between">
                <span>{t('pos.cart.items', 'Items')}:</span>
                <span className="font-semibold">
                  {currentOrder.items.reduce((sum, item) => sum + item.quantity, 0)}
                </span>
              </div>
              <div className="flex justify-between text-xl font-bold">
                <span>{t('pos.cart.total', 'Total')}:</span>
                <span>{currentOrder.total.toFixed(2)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Footer - Action Button */}
      <div className="bg-white border-t-2 border-gray-200 px-6 py-5 flex-shrink-0">
        <div className="max-w-2xl mx-auto">
          <TouchButton
            variant="success"
            size="large"
            fullWidth
            onClick={handleCreateOrder}
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                {t('pos.details.creatingOrder', 'Creating Order...')}
              </>
            ) : (
              t('pos.details.createOrder', 'Create Order')
            )}
          </TouchButton>
        </div>
      </div>
    </div>
  );
};

export default OrderDetailsScreen;
