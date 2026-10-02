import { useState, useEffect } from 'react';
import { printerAPI } from '../services/api';
import { useTranslation } from 'react-i18next';
import { CheckCircle, AlertTriangle, WifiOff, Clock } from 'lucide-react';

/**
 * Whether this venue's kitchen tickets are reaching a printer.
 *
 * Four states rather than two, because "not printing" has two different causes and they need
 * different people. STALE and OFFLINE mean go and look at the computer; BACKLOG means the agent is
 * fine and the printer is not — paper, power, a jam. Collapsing them into one red light would send
 * somebody to the wrong end of the room.
 *
 * Polls rather than subscribing. The thing being reported on is a WebSocket that may itself be the
 * broken part, so a screen that learned about it over the same WebSocket would go quiet exactly when
 * it had something to say.
 */
const POLL_MS = 20000;

const STATES = {
  ONLINE: {
    icon: CheckCircle,
    tone: 'border-green-300 bg-green-50 text-green-900',
    dot: 'text-green-600',
  },
  BACKLOG: {
    icon: AlertTriangle,
    tone: 'border-amber-300 bg-amber-50 text-amber-900',
    dot: 'text-amber-600',
  },
  STALE: {
    icon: Clock,
    tone: 'border-amber-300 bg-amber-50 text-amber-900',
    dot: 'text-amber-600',
  },
  OFFLINE: {
    icon: WifiOff,
    tone: 'border-red-300 bg-red-50 text-red-900',
    dot: 'text-red-600',
  },
};

export function PrintAgentStatus({ restaurantId, t }) {
  const [status, setStatus] = useState(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    if (!restaurantId) {
      setStatus(null);
      return undefined;
    }
    let ignore = false;

    const load = () => {
      printerAPI.getPrintAgentStatus(restaurantId)
        .then((res) => {
          if (ignore) return;
          setStatus(res?.data?.data || null);
          setUnavailable(false);
        })
        // A platform user with no venue of their own gets a 403 here. Render nothing rather than an
        // error: this card sits above the printer list people actually came to manage.
        .catch(() => { if (!ignore) setUnavailable(true); });
    };

    load();
    const timer = setInterval(load, POLL_MS);
    return () => { ignore = true; clearInterval(timer); };
  }, [restaurantId]);

  if (unavailable || !status?.state) return null;

  const shape = STATES[status.state] || STATES.OFFLINE;
  const Icon = shape.icon;

  const headline = {
    ONLINE: t('printers.agentOnline', 'Kitchen print agent connected'),
    BACKLOG: t('printers.agentBacklog', 'Agent connected, but tickets are not printing'),
    STALE: t('printers.agentStale', 'Print agent has gone quiet'),
    OFFLINE: t('printers.agentOffline', 'No print agent connected'),
  }[status.state];

  const explanation = {
    ONLINE: null,
    BACKLOG: t('printers.agentBacklogHelp',
      'The computer is online, so check the printer itself — paper, power, or a jam.'),
    STALE: t('printers.agentStaleHelp',
      'We have stopped hearing from it. Check the kitchen computer and its internet connection.'),
    OFFLINE: t('printers.agentOfflineHelp',
      'Tickets are being saved and will print as soon as an agent connects. Check that the kitchen computer is on and the agent is running.'),
  }[status.state];

  return (
    <div className={`mb-4 rounded-lg border p-4 ${shape.tone}`} data-testid="print-agent-status">
      <div className="flex items-start gap-3">
        <Icon size={20} className={`mt-0.5 shrink-0 ${shape.dot}`} aria-hidden="true" />
        <div className="min-w-0">
          <div className="font-medium">{headline}</div>
          {explanation && <div className="mt-1 text-sm">{explanation}</div>}

          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm">
            {status.queuedJobs > 0 && (
              <span>
                {t('printers.agentQueued', 'Waiting to print')}: <strong>{status.queuedJobs}</strong>
                {status.oldestQueuedMinutes != null && status.oldestQueuedMinutes > 0 && (
                  <> ({t('printers.agentOldest', 'oldest {{minutes}} min', {
                    minutes: status.oldestQueuedMinutes,
                  })})</>
                )}
              </span>
            )}
            {status.deadLetterJobs > 0 && (
              <span>
                {t('printers.agentDeadLettered', 'Given up on')}: <strong>{status.deadLetterJobs}</strong>
              </span>
            )}
            {status.agentId && (
              <span className="opacity-75">
                {status.agentId}
                {status.secondsSinceLastSeen != null && (
                  <> · {t('printers.agentLastSeen', 'last heard {{seconds}}s ago', {
                    seconds: status.secondsSinceLastSeen,
                  })}</>
                )}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default PrintAgentStatus;
