import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { initTelegramWebApp } from '../../services/telegram';

/**
 * Landing route for the customer bot's Mini App button (bot → /order/tg?restaurantId=NN).
 *
 * The bot can only append a query param to the configured Mini App URL, so this thin bridge reads
 * restaurantId from the query, boots the Telegram WebApp chrome, and forwards into the existing
 * self-service takeaway menu — reusing the whole menu → cart → checkout → KDS flow unchanged.
 */
export default function TelegramEntryPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [invalid, setInvalid] = useState(false);

  const restaurantId = params.get('restaurantId');

  useEffect(() => {
    initTelegramWebApp();
    if (!restaurantId || !/^\d+$/.test(restaurantId)) {
      setInvalid(true);
      return;
    }
    // Reuse the proven takeaway flow; replace history so Back doesn't loop back through this bridge.
    navigate(`/menu/${restaurantId}/takeaway`, { replace: true });
  }, [restaurantId, navigate]);

  if (invalid) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
          <h2 className="text-xl font-semibold text-gray-800 mb-2">
            {t('selfService.somethingWentWrong', 'Something went wrong')}
          </h2>
          <p className="text-gray-600">
            {t('telegram.openFromBot', "Please open the menu from the restaurant's Telegram bot.")}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="text-center">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600 mx-auto mb-3"></div>
        <p className="text-gray-600">{t('common.loading', 'Loading...')}</p>
      </div>
    </div>
  );
}
