import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { ChevronLeft, Phone, User, MapPin, Hash, Loader2, CheckCircle } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';
import { useSearchParams } from 'react-router-dom';
import { customerAPI } from '../../services/api';

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
  const [isSearchingCustomer, setIsSearchingCustomer] = useState(false);
  const [foundCustomer, setFoundCustomer] = useState(null);
  const [customerSuggestions, setCustomerSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const searchTimeoutRef = useRef(null);

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

  // Search for existing customers by phone number (for DELIVERY and TAKEAWAY)
  const searchCustomerByPhone = useCallback(async (phone) => {
    if (!phone || phone.length < 3) {
      setFoundCustomer(null);
      setCustomerSuggestions([]);
      setShowSuggestions(false);
      return;
    }

    setIsSearchingCustomer(true);
    try {
      // Search for similar customers
      const response = await customerAPI.suggestByPhone(phone);
      const customers = response.data?.data || [];
      setCustomerSuggestions(customers);
      setShowSuggestions(customers.length > 0);

      // Check for exact match
      const exactMatch = customers.find(c => c.phone === phone);
      if (exactMatch) {
        setFoundCustomer(exactMatch);
        // Auto-fill customer data for exact match
        setFormData(prev => ({
          ...prev,
          name: exactMatch.firstName ? `${exactMatch.firstName} ${exactMatch.lastName || ''}`.trim() : prev.name,
          email: exactMatch.email || prev.email,
          address: exactMatch.defaultAddress || prev.address,
          city: exactMatch.city || prev.city,
        }));
        setShowSuggestions(false);
      } else {
        setFoundCustomer(null);
      }
    } catch (error) {
      console.error('Failed to search customer:', error);
      setFoundCustomer(null);
      setCustomerSuggestions([]);
    } finally {
      setIsSearchingCustomer(false);
    }
  }, []);

  // Select a customer from suggestions
  const handleSelectCustomer = useCallback((selectedCustomer) => {
    setFoundCustomer(selectedCustomer);
    setFormData(prev => ({
      ...prev,
      phone: selectedCustomer.phone || prev.phone,
      name: selectedCustomer.firstName ? `${selectedCustomer.firstName} ${selectedCustomer.lastName || ''}`.trim() : prev.name,
      email: selectedCustomer.email || prev.email,
      address: selectedCustomer.defaultAddress || prev.address,
      city: selectedCustomer.city || prev.city,
    }));
    setShowSuggestions(false);
    setCustomerSuggestions([]);
  }, []);

  const handleChange = useCallback((field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    setErrors(prev => ({ ...prev, [field]: null }));

    // Debounced search for customer when phone changes (only for DELIVERY/TAKEAWAY)
    if (field === 'phone') {
      setFoundCustomer(null);
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current);
      }
      searchTimeoutRef.current = setTimeout(() => {
        searchCustomerByPhone(value);
      }, 500);
    }
  }, [searchCustomerByPhone]);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current);
      }
    };
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
      // Dine-in only requires table number (customer info and guest count are optional)
      if (!formData.tableNumber || !String(formData.tableNumber).trim()) {
        newErrors.tableNumber = t('pos.details.errors.tableRequired', 'Table number is required');
      }
      // Guest count is optional, but if provided must be at least 1
      if (formData.guestCount && formData.guestCount < 1) {
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
      customerData.guestCount = formData.guestCount ? parseInt(formData.guestCount) : null;
    }

    setCustomerInfo(customerData);

    // Submit order first, then proceed based on order type
    setIsSubmitting(true);
    try {
      const result = await submitOrder(restaurantId);
      if (result.success) {
        // For DINE_IN orders, skip payment and go to confirmation
        // Payment happens later when customer is ready to leave
        if (currentOrder.type === 'DINE_IN') {
          setCurrentScreen('confirmation');
        } else {
          // For TAKEAWAY and DELIVERY, go to payment screen
          setCurrentScreen('payment');
        }
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
                <div className="space-y-2 relative">
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
                  {/* Customer search status indicator */}
                  {isSearchingCustomer && (
                    <div className="flex items-center gap-2 text-sm text-gray-500">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      {t('pos.details.searchingCustomer', 'Searching customer...')}
                    </div>
                  )}
                  {/* Customer suggestions dropdown */}
                  {showSuggestions && customerSuggestions.length > 0 && !isSearchingCustomer && (
                    <div className="absolute z-10 w-full mt-1 bg-white border-2 border-blue-200 rounded-lg shadow-lg max-h-60 overflow-y-auto">
                      <div className="p-2 text-xs text-gray-500 border-b">
                        {t('pos.details.selectCustomer', 'Select existing customer:')}
                      </div>
                      {customerSuggestions.map((cust) => (
                        <button
                          key={cust.id}
                          type="button"
                          className="w-full px-4 py-3 text-left hover:bg-blue-50 border-b border-gray-100 last:border-b-0 transition-colors"
                          onClick={() => handleSelectCustomer(cust)}
                        >
                          <div className="font-medium text-gray-900">
                            {cust.firstName} {cust.lastName}
                          </div>
                          <div className="text-sm text-gray-600 flex items-center gap-2">
                            <Phone className="w-3 h-3" />
                            {cust.phone}
                          </div>
                          {cust.defaultAddress && (
                            <div className="text-xs text-gray-500 mt-1">
                              {cust.defaultAddress}, {cust.city}
                            </div>
                          )}
                        </button>
                      ))}
                    </div>
                  )}
                  {foundCustomer && !isSearchingCustomer && !showSuggestions && (
                    <div className="flex items-center gap-2 text-sm text-green-600 bg-green-50 p-2 rounded-lg">
                      <CheckCircle className="w-4 h-4" />
                      {t('pos.details.customerFound', 'Customer found: {{name}}', {
                        name: `${foundCustomer.firstName || ''} ${foundCustomer.lastName || ''}`.trim() || foundCustomer.phone
                      })}
                    </div>
                  )}
                </div>

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
              currentOrder.type === 'DINE_IN'
                ? t('pos.details.createOrder', 'Create Order')
                : t('pos.details.proceedToPayment', 'Proceed to Payment')
            )}
          </TouchButton>
        </div>
      </div>
    </div>
  );
};

export default OrderDetailsScreen;
