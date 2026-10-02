import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Printer } from 'lucide-react';
import { printerAPI } from '../services/api';

/**
 * A slim strip shown when this venue's kitchen tickets have stopped coming out of the printer.
 *
 * The third rung of a ladder. The card on Printer Settings answers the question for anyone who went
 * looking; this reaches a manager who did not; the owner's Telegram reaches somebody who is not at a
 * screen at all. Each waits longer than the last, so the cheapest channel gets first chance.
 *
 * SHOWN ONLY WHEN TICKETS ARE ACTUALLY STUCK, never merely because no agent is connected. A venue
 * that does not use agent printing would otherwise carry a red bar across every screen forever, and a
 * banner that is always on is a banner nobody sees — including the day it means something.
 *
 * Not dismissible, for the same reason the plan-expiry banner is not: food is not reaching customers,
 * and the honest way to clear it is to fix the printer. It disappears on its own within a poll of the
 * queue draining.
 */
const POLL_MS = 30000;
const DEFAULT_BACKLOG_MINUTES = 5;

export function PrintQueueBanner({ t }) {
  const [status, setStatus] = useState(null);

  useEffect(() => {
    let ignore = false;

    const load = () => {
      // No venue argument: the endpoint falls back to the caller's own, which is what a staff member
      // in one restaurant always wants.
      printerAPI.getPrintAgentStatus()
        .then((res) => { if (!ignore) setStatus(res?.data?.data || null); })
        // A platform user with no venue of their own gets a 403. Stay silent rather than put an error
        // across the top of every page.
        .catch(() => { if (!ignore) setStatus(null); });
    };

    load();
    const timer = setInterval(load, POLL_MS);
    return () => { ignore = true; clearInterval(timer); };
  }, []);

  if (!status) return null;

  const threshold = status.backlogAfterMinutes ?? DEFAULT_BACKLOG_MINUTES;
  const waiting = status.oldestQueuedMinutes;
  const stuck = status.queuedJobs > 0 && waiting != null && waiting >= threshold;
  if (!stuck) return null;

  // Amber while something is still connected and could recover on its own; red when nothing is
  // listening, because then nobody in the kitchen is being told anything at all.
  const tone = status.state === 'OFFLINE' ? 'bg-red-600 text-white' : 'bg-amber-500 text-white';

  const message = status.state === 'OFFLINE' || status.state === 'STALE'
    ? t('printers.bannerNoAgent',
      'Kitchen tickets are not printing — the print agent is not responding. {{count}} waiting, oldest {{minutes}} min.',
      { count: status.queuedJobs, minutes: waiting })
    : t('printers.bannerStuck',
      'Kitchen tickets are not printing — check the printer. {{count}} waiting, oldest {{minutes}} min.',
      { count: status.queuedJobs, minutes: waiting });

  return (
    <div
      className={`flex items-center justify-center gap-3 px-4 py-2 text-sm font-medium ${tone}`}
      data-testid="print-queue-banner"
    >
      <Printer className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span>{message}</span>
      <Link to="/settings/printers" className="underline underline-offset-2 hover:opacity-90 whitespace-nowrap">
        {t('printers.bannerCheck', 'Check printers')}
      </Link>
    </div>
  );
}

export default function PrintQueueBannerConnected() {
  const { t } = useTranslation();
  return <PrintQueueBanner t={t} />;
}
