import { useTranslation } from 'react-i18next';
import { Lock } from 'lucide-react';
import { useSubscriptionStore } from '../store/subscriptionStore';
import { useAuthStore } from '../store/authStore';
import { Button } from './ui/button';

/**
 * Full-screen takeover shown when the backend subscription access gate (Phase 2) has 402'd the tenant
 * as suspended. The gate blocks every staff action, so a banner would leave a broken app underneath —
 * this replaces it. Only renders for an authenticated staff session; a reactivated tenant clears it by
 * retrying (the flag resets on login/logout, so a fresh session never shows a stale overlay).
 */
export default function SuspensionGate() {
  const { t } = useTranslation();
  const suspended = useSubscriptionStore((s) => s.suspended);
  const setSuspended = useSubscriptionStore((s) => s.setSuspended);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const logout = useAuthStore((s) => s.logout);

  if (!suspended || !isAuthenticated) return null;

  const retry = () => {
    setSuspended(false);
    window.location.reload();
  };
  const signOut = () => {
    setSuspended(false);
    logout();
    window.location.href = '/admin/login';
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-gray-900/80 p-4" role="alertdialog" aria-modal="true">
      <div className="w-full max-w-md rounded-lg bg-white p-8 text-center shadow-xl">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-red-100">
          <Lock className="h-7 w-7 text-red-600" />
        </div>
        <h1 className="mb-2 text-xl font-semibold text-gray-900">
          {t('suspended.title', 'Access suspended')}
        </h1>
        <p className="mb-6 text-sm text-gray-600">
          {t('suspended.body', "This restaurant's access has been suspended. Please contact support to restore it.")}
        </p>
        <div className="flex justify-center gap-3">
          <Button variant="outline" onClick={retry}>{t('suspended.retry', 'Retry')}</Button>
          <Button variant="destructive" onClick={signOut}>{t('suspended.logout', 'Log out')}</Button>
        </div>
      </div>
    </div>
  );
}
