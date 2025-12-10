import React, { useState } from 'react';
import { cn } from '../../lib/utils';
import { ChevronLeft, Phone, User, MapPin, Users, Hash } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';

/**
 * OrderDetailsScreen - Collect order-type specific information
 * Delivery: customer info, address
 * Takeaway: customer name, phone
 * Dine-in: table number, guest count
 */
const OrderDetailsScreen = () => {
  const { currentOrder, customer, setCustomerInfo, setCurrentScreen } = usePOSStore();

  const [formData, setFormData] = useState({
    name: customer.name || '',
    phone: customer.phone || '',
    email: customer.email || '',
    address: customer.address?.street || '',
    city: customer.address?.city || '',
    zipCode: customer.address?.zipCode || '',
    deliveryInstructions: customer.deliveryInstructions || '',
    tableNumber: customer.tableNumber || '',
    guestCount: customer.guestCount || 1,
  });

  const [errors, setErrors] = useState({});

  const handleChange = (field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    // Clear error for this field
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: null }));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    // Common validations
    if (!formData.name.trim()) {
      newErrors.name = 'Name is required';
    }

    if (currentOrder.type === 'DELIVERY') {
      if (!formData.phone.trim()) {
        newErrors.phone = 'Phone number is required';
      }
      if (!formData.address.trim()) {
        newErrors.address = 'Address is required';
      }
      if (!formData.city.trim()) {
        newErrors.city = 'City is required';
      }
      if (!formData.zipCode.trim()) {
        newErrors.zipCode = 'ZIP code is required';
      }
    } else if (currentOrder.type === 'TAKEAWAY') {
      if (!formData.phone.trim()) {
        newErrors.phone = 'Phone number is required';
      }
    } else if (currentOrder.type === 'DINE_IN') {
      if (!formData.tableNumber.trim()) {
        newErrors.tableNumber = 'Table number is required';
      }
      if (!formData.guestCount || formData.guestCount < 1) {
        newErrors.guestCount = 'Guest count must be at least 1';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleProceedToPayment = () => {
    if (!validateForm()) {
      return;
    }

    // Save customer info to store
    const customerData = {
      name: formData.name,
      phone: formData.phone,
      email: formData.email,
    };

    if (currentOrder.type === 'DELIVERY') {
      customerData.address = {
        street: formData.address,
        city: formData.city,
        zipCode: formData.zipCode,
      };
      customerData.deliveryInstructions = formData.deliveryInstructions;
    } else if (currentOrder.type === 'DINE_IN') {
      customerData.tableNumber = formData.tableNumber;
      customerData.guestCount = parseInt(formData.guestCount);
    }

    setCustomerInfo(customerData);
    setCurrentScreen('payment');
  };

  const InputField = ({ label, icon, field, type = 'text', required = false, placeholder }) => (
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
          value={formData[field]}
          onChange={(e) => handleChange(field, e.target.value)}
          placeholder={placeholder}
          className={cn(
            'w-full min-h-[56px] px-4 py-3',
            icon && 'pl-12',
            'bg-white border-2 rounded-lg',
            'text-base text-gray-900 placeholder-gray-500',
            errors[field]
              ? 'border-red-400 focus:border-red-500'
              : 'border-gray-300 focus:border-blue-400',
            'focus:outline-none transition-colors'
          )}
        />
      </div>
      {errors[field] && (
        <p className="mt-2 text-sm text-red-600">{errors[field]}</p>
      )}
    </div>
  );

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
            Back to Cart
          </TouchButton>

          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-900">Order Details</h1>
            <p className="text-sm text-gray-600">{currentOrder.type} Order</p>
          </div>

          <div className="w-[140px]" /> {/* Spacer */}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-2xl mx-auto space-y-6">
          {/* Common Fields - Customer Info */}
          <div className="bg-white rounded-xl p-6 border-2 border-gray-200 space-y-4">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Customer Information</h2>

            <InputField
              label="Customer Name"
              icon={<User className="w-5 h-5" />}
              field="name"
              required
              placeholder="Enter customer name"
            />

            {(currentOrder.type === 'DELIVERY' || currentOrder.type === 'TAKEAWAY') && (
              <>
                <InputField
                  label="Phone Number"
                  icon={<Phone className="w-5 h-5" />}
                  field="phone"
                  type="tel"
                  required
                  placeholder="(555) 123-4567"
                />

                <InputField
                  label="Email (Optional)"
                  field="email"
                  type="email"
                  placeholder="customer@example.com"
                />
              </>
            )}
          </div>

          {/* Delivery-Specific Fields */}
          {currentOrder.type === 'DELIVERY' && (
            <div className="bg-white rounded-xl p-6 border-2 border-gray-200 space-y-4">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Delivery Address</h2>

              <InputField
                label="Street Address"
                icon={<MapPin className="w-5 h-5" />}
                field="address"
                required
                placeholder="123 Main Street"
              />

              <div className="grid grid-cols-2 gap-4">
                <InputField
                  label="City"
                  field="city"
                  required
                  placeholder="New York"
                />

                <InputField
                  label="ZIP Code"
                  field="zipCode"
                  required
                  placeholder="10001"
                />
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">
                  Delivery Instructions (Optional)
                </label>
                <textarea
                  value={formData.deliveryInstructions}
                  onChange={(e) => handleChange('deliveryInstructions', e.target.value)}
                  placeholder="E.g., Ring doorbell, Leave at door..."
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

          {/* Dine-In Specific Fields */}
          {currentOrder.type === 'DINE_IN' && (
            <div className="bg-white rounded-xl p-6 border-2 border-gray-200 space-y-4">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Table Information</h2>

              <InputField
                label="Table Number"
                icon={<Hash className="w-5 h-5" />}
                field="tableNumber"
                required
                placeholder="e.g., 12, A5"
              />

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">
                  Number of Guests <span className="text-red-500">*</span>
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

          {/* Order Summary Preview */}
          <div className="bg-blue-50 border-2 border-blue-200 rounded-xl p-6">
            <h3 className="text-lg font-bold text-blue-900 mb-3">Order Summary</h3>
            <div className="space-y-2 text-blue-800">
              <div className="flex justify-between">
                <span>Items:</span>
                <span className="font-semibold">
                  {currentOrder.items.reduce((sum, item) => sum + item.quantity, 0)}
                </span>
              </div>
              <div className="flex justify-between text-xl font-bold">
                <span>Total:</span>
                <span>${currentOrder.total.toFixed(2)}</span>
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
            onClick={handleProceedToPayment}
          >
            Proceed to Payment
          </TouchButton>
        </div>
      </div>
    </div>
  );
};

export default OrderDetailsScreen;
