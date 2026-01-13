import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  isPushSupported,
  getPermissionStatus,
  registerServiceWorker,
  subscribeToPush,
  isSubscribed
} from '../services/pushNotification';

/**
 * Push Notification Permission Prompt
 * Shows a prompt to users asking them to enable push notifications
 */
const PushPermissionPrompt = ({ isAdmin = false, onSubscribed }) => {
  const { t } = useTranslation();
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [supported, setSupported] = useState(true);

  useEffect(() => {
    checkPushStatus();
  }, []);

  const checkPushStatus = async () => {
    // Check if push is supported
    if (!isPushSupported()) {
      setSupported(false);
      return;
    }

    // Check permission status
    const permission = getPermissionStatus();
    if (permission === 'denied') {
      setSupported(false);
      return;
    }

    // Check if already subscribed
    const subscribed = await isSubscribed();
    if (subscribed) {
      setShow(false);
      return;
    }

    // Check if user dismissed the prompt recently
    const dismissedAt = localStorage.getItem('pushPromptDismissed');
    if (dismissedAt) {
      const dismissedTime = new Date(dismissedAt).getTime();
      const hoursSinceDismissed = (Date.now() - dismissedTime) / (1000 * 60 * 60);
      if (hoursSinceDismissed < 24) {
        return; // Don't show for 24 hours after dismissal
      }
    }

    // Show the prompt
    setShow(true);
  };

  const handleEnable = async () => {
    setLoading(true);
    setError(null);

    try {
      // Register service worker first
      await registerServiceWorker();

      // Subscribe to push notifications
      await subscribeToPush(isAdmin);

      setShow(false);
      if (onSubscribed) {
        onSubscribed();
      }
    } catch (err) {
      console.error('Failed to enable push notifications:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDismiss = () => {
    localStorage.setItem('pushPromptDismissed', new Date().toISOString());
    setShow(false);
  };

  if (!show || !supported) {
    return null;
  }

  return (
    <div className="fixed bottom-4 right-4 z-50 max-w-sm bg-white rounded-lg shadow-lg border border-gray-200 p-4">
      <div className="flex items-start">
        <div className="flex-shrink-0">
          <svg
            className="h-6 w-6 text-blue-600"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
            />
          </svg>
        </div>
        <div className="ml-3 flex-1">
          <h3 className="text-sm font-medium text-gray-900">
            {t('push.promptTitle', 'Enable Notifications')}
          </h3>
          <p className="mt-1 text-sm text-gray-500">
            {t('push.promptMessage', 'Get notified about orders, promotions, and updates.')}
          </p>

          {error && (
            <p className="mt-2 text-sm text-red-600">
              {error}
            </p>
          )}

          <div className="mt-4 flex space-x-3">
            <button
              onClick={handleEnable}
              disabled={loading}
              className="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md shadow-sm text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
            >
              {loading ? (
                <>
                  <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {t('common.enabling', 'Enabling...')}
                </>
              ) : (
                t('push.enable', 'Enable')
              )}
            </button>
            <button
              onClick={handleDismiss}
              disabled={loading}
              className="inline-flex items-center px-3 py-2 border border-gray-300 text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {t('common.later', 'Later')}
            </button>
          </div>
        </div>
        <button
          onClick={handleDismiss}
          className="flex-shrink-0 ml-1 text-gray-400 hover:text-gray-500"
        >
          <span className="sr-only">{t('common.close', 'Close')}</span>
          <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
            <path
              fillRule="evenodd"
              d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
              clipRule="evenodd"
            />
          </svg>
        </button>
      </div>
    </div>
  );
};

export default PushPermissionPrompt;
