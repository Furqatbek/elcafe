import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { WifiOff } from 'lucide-react';
import { useNotificationStore } from '../store/notificationStore';

/**
 * EH-2.5 (docs/ERROR_HANDLING_PLAN.md): a slim banner shown when the realtime (STOMP) connection
 * drops, so staff on order/kitchen/POS screens know live updates have paused and to refresh if
 * needed — instead of silently seeing stale data. Reads the connection status the WS hook already
 * mirrors into the notification store (setWsConnected).
 *
 * Only shows AFTER the first successful connect (so it doesn't flash during initial handshake),
 * and only once disconnected for a moment (debounced) so a brief reconnect blip stays quiet.
 */
export default function WsConnectionBanner() {
  const { t } = useTranslation();
  const wsConnected = useNotificationStore((s) => s.wsConnected);
  const everConnected = useRef(false);
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (wsConnected) {
      everConnected.current = true;
      setShow(false);
      return;
    }
    if (!everConnected.current) return; // never connected yet — stay quiet during first handshake
    const timer = setTimeout(() => setShow(true), 3000); // debounce brief blips
    return () => clearTimeout(timer);
  }, [wsConnected]);

  if (!show) return null;

  return (
    <div
      role="status"
      className="flex items-center justify-center gap-2 bg-amber-100 text-amber-900 text-sm py-1.5 px-4 border-b border-amber-200"
    >
      <WifiOff className="h-4 w-4" />
      {t('ws.disconnected', 'Live updates paused — reconnecting. Refresh if data looks stale.')}
    </div>
  );
}
