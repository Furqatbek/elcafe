import { useState, useEffect } from 'react';
import { Clock, Gift, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { posAPI } from '../../services/api';

/**
 * HappyHourBanner - Displays active happy hour info and allows applying discount
 *
 * Props:
 * - restaurantId: Restaurant ID to check for active happy hours
 * - orderId: Current order ID (if exists) for discount preview
 * - onApply: Callback when happy hour discount is applied
 * - onDismiss: Callback when banner is dismissed
 * - compact: Show compact version (for header)
 */
export default function HappyHourBanner({
  restaurantId,
  orderId,
  onApply,
  onDismiss,
  compact = false,
  currentDiscountType = null,
}) {
  const [happyHour, setHappyHour] = useState(null);
  const [discountPreview, setDiscountPreview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState(null);
  const [dismissed, setDismissed] = useState(false);

  // Fetch active happy hour
  useEffect(() => {
    if (!restaurantId) return;

    const fetchHappyHour = async () => {
      try {
        setLoading(true);
        const response = await posAPI.getActiveHappyHour(restaurantId);
        setHappyHour(response.data?.data || null);
        setError(null);
      } catch (err) {
        console.error('Failed to fetch happy hour:', err);
        setHappyHour(null);
      } finally {
        setLoading(false);
      }
    };

    fetchHappyHour();

    // Refresh every 5 minutes to check if happy hour changed
    const interval = setInterval(fetchHappyHour, 5 * 60 * 1000);
    return () => clearInterval(interval);
  }, [restaurantId]);

  // Fetch discount preview when order exists
  useEffect(() => {
    if (!orderId || !happyHour) {
      setDiscountPreview(null);
      return;
    }

    const fetchPreview = async () => {
      try {
        const response = await posAPI.previewHappyHourDiscount(orderId);
        setDiscountPreview(response.data?.data || null);
      } catch (err) {
        console.error('Failed to fetch discount preview:', err);
        setDiscountPreview(null);
      }
    };

    fetchPreview();
  }, [orderId, happyHour]);

  const handleApply = async () => {
    if (!orderId || !happyHour) return;

    try {
      setApplying(true);
      setError(null);
      await posAPI.applyHappyHourDiscount(orderId);
      if (onApply) {
        onApply(happyHour);
      }
    } catch (err) {
      console.error('Failed to apply happy hour:', err);
      setError(err.response?.data?.message || 'Failed to apply happy hour discount');
    } finally {
      setApplying(false);
    }
  };

  const handleDismiss = () => {
    setDismissed(true);
    if (onDismiss) {
      onDismiss();
    }
  };

  // Calculate time remaining
  const getTimeRemaining = () => {
    if (!happyHour?.endTime) return null;

    const now = new Date();
    const [hours, minutes] = happyHour.endTime.split(':').map(Number);
    const endTime = new Date();
    endTime.setHours(hours, minutes, 0, 0);

    if (endTime < now) return null;

    const diff = endTime - now;
    const hoursLeft = Math.floor(diff / (1000 * 60 * 60));
    const minutesLeft = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));

    if (hoursLeft > 0) {
      return `${hoursLeft}h ${minutesLeft}m remaining`;
    }
    return `${minutesLeft} minutes remaining`;
  };

  // Don't show if no happy hour, loading, or dismissed
  if (loading || !happyHour || dismissed) {
    return null;
  }

  // Don't show if happy hour discount already applied
  if (currentDiscountType === 'HAPPY_HOUR') {
    return null;
  }

  const timeRemaining = getTimeRemaining();

  // Compact version for header
  if (compact) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 bg-gradient-to-r from-orange-500 to-pink-500 text-white rounded-lg text-sm">
        <Gift className="w-4 h-4" />
        <span className="font-medium">{happyHour.name}</span>
        <span className="bg-white/20 px-2 py-0.5 rounded-full text-xs">
          {happyHour.discountPercent}% OFF
        </span>
        {timeRemaining && (
          <span className="text-xs opacity-80">{timeRemaining}</span>
        )}
      </div>
    );
  }

  // Full banner
  return (
    <div className="relative bg-gradient-to-r from-orange-500 to-pink-500 text-white rounded-xl p-4 shadow-lg">
      {/* Dismiss button */}
      <button
        onClick={handleDismiss}
        className="absolute top-2 right-2 p-1 hover:bg-white/20 rounded-full transition-colors"
      >
        <X className="w-4 h-4" />
      </button>

      <div className="flex items-start gap-4">
        {/* Icon */}
        <div className="flex-shrink-0 w-12 h-12 bg-white/20 rounded-full flex items-center justify-center">
          <Gift className="w-6 h-6" />
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="font-bold text-lg">{happyHour.name}</h3>
            <span className="bg-white/20 px-2 py-0.5 rounded-full text-sm font-medium">
              {happyHour.discountPercent}% OFF
            </span>
          </div>

          {happyHour.description && (
            <p className="text-sm text-white/80 mb-2">{happyHour.description}</p>
          )}

          <div className="flex items-center gap-4 text-sm">
            {happyHour.startTime && happyHour.endTime && (
              <div className="flex items-center gap-1">
                <Clock className="w-4 h-4" />
                <span>{happyHour.startTime} - {happyHour.endTime}</span>
              </div>
            )}
            {timeRemaining && (
              <div className="flex items-center gap-1 text-yellow-200">
                <span>{timeRemaining}</span>
              </div>
            )}
          </div>

          {/* Discount preview */}
          {discountPreview && discountPreview.hasActiveHappyHour && (
            <div className="mt-2 p-2 bg-white/10 rounded-lg">
              <div className="flex items-center justify-between">
                <span className="text-sm">Estimated savings:</span>
                <span className="font-bold text-lg">
                  {parseFloat(discountPreview.discountAmount).toLocaleString('en-US', {
                    minimumFractionDigits: 0,
                    maximumFractionDigits: 0,
                  })} UZS
                </span>
              </div>
            </div>
          )}

          {error && (
            <p className="mt-2 text-sm text-yellow-200 bg-red-500/20 px-2 py-1 rounded">
              {error}
            </p>
          )}
        </div>

        {/* Apply button */}
        {orderId && (
          <div className="flex-shrink-0">
            <Button
              onClick={handleApply}
              disabled={applying || !discountPreview?.hasActiveHappyHour}
              className="bg-white text-orange-600 hover:bg-white/90 font-bold px-6"
            >
              {applying ? 'Applying...' : 'Apply'}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
