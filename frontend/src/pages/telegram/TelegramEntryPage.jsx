import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { initTelegramWebApp } from '../../services/telegram';
import { useTelegramOrder } from './TelegramOrderContext';

/**
 * Entry route for the customer bot's Mini App button (bot → /order/tg?restaurantId=NN).
 * Boots the Telegram WebApp, logs the customer in from signed initData, then hands off to the
 * ordering page — or explains what to do when it can't (opened outside Telegram, or no shared contact).
 */
export default function TelegramEntryPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { login } = useTelegramOrder();
  const [state, setState] = useState('loading'); // loading | registration | not_telegram | error
  const ran = useRef(false);

  const restaurantId = params.get('restaurantId');

  useEffect(() => {
    if (ran.current) return; // StrictMode double-invoke guard: log in exactly once
    ran.current = true;

    initTelegramWebApp();
    if (!restaurantId || !/^\d+$/.test(restaurantId)) {
      setState('error');
      return;
    }
    login(restaurantId)
      .then((r) => {
        if (r.ok) navigate('/tg/order', { replace: true });
        else setState(r.reason === 'REGISTRATION_REQUIRED' ? 'registration'
          : r.reason === 'NOT_IN_TELEGRAM' ? 'not_telegram' : 'error');
      })
      .catch(() => setState('error'));
  }, [restaurantId, login, navigate]);

  if (state === 'loading') {
    return (
      <Centered>
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600 mx-auto mb-3" />
        <p className="text-gray-600">{t('common.loading', 'Loading...')}</p>
      </Centered>
    );
  }

  const messages = {
    registration: {
      title: t('telegram.shareContactTitle', 'One quick step'),
      body: t('telegram.shareContact', 'Please open the bot chat and tap “Share contact”, then reopen the menu.'),
    },
    not_telegram: {
      title: t('telegram.openInTelegram', 'Open in Telegram'),
      body: t('telegram.openFromBot', "Please open the menu from the restaurant's Telegram bot."),
    },
    error: {
      title: t('selfService.somethingWentWrong', 'Something went wrong'),
      body: t('telegram.openFromBot', "Please open the menu from the restaurant's Telegram bot."),
    },
  };
  const msg = messages[state] || messages.error;

  return (
    <Centered>
      <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
        <h2 className="text-xl font-semibold text-gray-800 mb-2">{msg.title}</h2>
        <p className="text-gray-600">{msg.body}</p>
      </div>
    </Centered>
  );
}

function Centered({ children }) {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="text-center w-full flex flex-col items-center">{children}</div>
    </div>
  );
}
