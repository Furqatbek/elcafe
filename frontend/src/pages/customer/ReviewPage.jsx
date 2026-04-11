import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { reviewAPI } from '../../services/api';
import { Star, CheckCircle, Send } from 'lucide-react';

/**
 * Customer-facing review page. Accessible via link:
 * /admin/review?restaurant={id}&order={orderNumber}&name={customerName}
 *
 * No auth required — public endpoint.
 */
export default function ReviewPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const restaurantId = searchParams.get('restaurant');
  const orderNumber = searchParams.get('order') || '';
  const customerName = searchParams.get('name') || '';

  const [rating, setRating] = useState(0);
  const [hoverRating, setHoverRating] = useState(0);
  const [comment, setComment] = useState('');
  const [name, setName] = useState(customerName);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (rating === 0) return;
    setSubmitting(true);
    setError(null);
    try {
      await reviewAPI.submit({
        restaurantId: parseInt(restaurantId),
        orderNumber: orderNumber || null,
        customerName: name || null,
        rating,
        comment: comment.trim() || null,
      });
      setSubmitted(true);
    } catch (err) {
      const msg = err.response?.data?.message || err.message;
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  // Submitted — thank you screen
  if (submitted) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full text-center">
          <CheckCircle className="w-20 h-20 text-green-500 mx-auto mb-6" />
          <h1 className="text-3xl font-bold text-gray-900 mb-3">
            {t('review.thankYou', 'Thank you!')}
          </h1>
          <p className="text-lg text-gray-500">
            {t('review.thankYouMessage', 'Your feedback helps us improve.')}
          </p>
          {/* Show stars submitted */}
          <div className="flex justify-center gap-1 mt-6">
            {[1, 2, 3, 4, 5].map((star) => (
              <Star
                key={star}
                className={`w-10 h-10 ${star <= rating ? 'text-yellow-400 fill-yellow-400' : 'text-gray-300'}`}
              />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="max-w-md w-full">
        <div className="bg-white rounded-2xl shadow-lg p-8 space-y-6">
          {/* Header */}
          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-900">
              {t('review.howWasExperience', 'How was your experience?')}
            </h1>
            {orderNumber && (
              <p className="text-gray-500 mt-1">
                {t('review.order', 'Order')}: <span className="font-medium">{orderNumber}</span>
              </p>
            )}
          </div>

          {/* Star Rating */}
          <div className="flex justify-center gap-2">
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={star}
                type="button"
                onClick={() => setRating(star)}
                onMouseEnter={() => setHoverRating(star)}
                onMouseLeave={() => setHoverRating(0)}
                className="focus:outline-none transition-transform hover:scale-110"
              >
                <Star
                  className={`w-12 h-12 transition-colors ${
                    star <= (hoverRating || rating)
                      ? 'text-yellow-400 fill-yellow-400'
                      : 'text-gray-300'
                  }`}
                />
              </button>
            ))}
          </div>

          {/* Rating label */}
          {rating > 0 && (
            <p className={`text-center text-lg font-medium ${rating <= 2 ? 'text-red-500' : rating <= 3 ? 'text-yellow-600' : 'text-green-600'}`}>
              {rating === 1 && t('review.rating1', 'Poor')}
              {rating === 2 && t('review.rating2', 'Below Average')}
              {rating === 3 && t('review.rating3', 'Average')}
              {rating === 4 && t('review.rating4', 'Good')}
              {rating === 5 && t('review.rating5', 'Excellent!')}
            </p>
          )}

          {/* Name */}
          {!customerName && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                {t('review.yourName', 'Your Name (optional)')}
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={t('review.namePlaceholder', 'John')}
                className="w-full border border-gray-300 rounded-lg px-4 py-3 text-base focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
              />
            </div>
          )}

          {/* Comment */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('review.comment', 'Leave a comment (optional)')}
            </label>
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={3}
              placeholder={t('review.commentPlaceholder', 'Tell us about your experience...')}
              className="w-full border border-gray-300 rounded-lg px-4 py-3 text-base focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none resize-none"
            />
          </div>

          {/* Error */}
          {error && (
            <p className="text-red-500 text-sm text-center">{error}</p>
          )}

          {/* Submit */}
          <button
            onClick={handleSubmit}
            disabled={rating === 0 || submitting}
            className={`w-full py-4 rounded-xl text-lg font-semibold flex items-center justify-center gap-2 transition-colors ${
              rating === 0
                ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
                : 'bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800'
            }`}
          >
            <Send className="w-5 h-5" />
            {submitting
              ? t('review.submitting', 'Submitting...')
              : t('review.submit', 'Submit Review')}
          </button>
        </div>
      </div>
    </div>
  );
}
