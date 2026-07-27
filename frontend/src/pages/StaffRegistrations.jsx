import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Loader2, UserCheck } from 'lucide-react';
import { notifyError } from '../lib/errors';
import { staffReportAPI } from '../services/api';

/**
 * Who registered how many guests at the till, and whether those guests turned out to be reachable.
 *
 * <p>This page is the visible half of a deliberate trade. The counter registration flow skips phone
 * verification so the queue keeps moving — the employee is the trust anchor instead — and that was only
 * acceptable on the condition the path stayed reviewable. `registered_by_user_id` has been stamped since
 * V182; this is the first thing that reads it.
 *
 * <p>Reach rate is given equal billing with volume on purpose. A cashier with forty registrations and
 * four reachable phones is a completely different story from one with forty and thirty-eight, and only
 * the second number tells them apart.
 */

/** Below this, a low reach rate is more likely noise than a pattern worth acting on. */
const REACH_SAMPLE_FLOOR = 5;
/** Under this share of reachable guests, the row is worth a second look. */
const REACH_CONCERN_PERCENT = 50;

const isoDate = (d) => d.toISOString().slice(0, 10);

export default function StaffRegistrations() {
  const { t } = useTranslation();
  const today = useMemo(() => new Date(), []);
  const [range, setRange] = useState(() => {
    const start = new Date(today);
    start.setDate(start.getDate() - 29);
    return { from: isoDate(start), to: isoDate(today) };
  });
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);

  const tRef = useRef(t);
  tRef.current = t;

  const load = useCallback(async (from, to) => {
    setLoading(true);
    try {
      const res = await staffReportAPI.registrations({ from, to });
      setReport(res.data?.data || res.data);
    } catch (error) {
      notifyError(error, {
        title: tRef.current('staffReport.loadFailed', 'Could not load the registration report'),
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(range.from, range.to);
  }, [load, range.from, range.to]);

  const formatMoney = (value) => {
    const n = Number(value ?? 0);
    return Number.isNaN(n) ? '—' : n.toLocaleString();
  };

  const overallReach =
    report && report.totalRegistrations > 0
      ? Math.round((report.totalReached / report.totalRegistrations) * 100)
      : null;

  return (
    <div className="p-4">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold">
            <UserCheck className="h-6 w-6" />
            {t('staffReport.title', 'Till registrations by employee')}
          </h1>
          <p className="mt-1 max-w-2xl text-sm text-gray-500">
            {t(
              'staffReport.subtitle',
              'Guests registered at the counter, where no phone verification happens. Reach shows how many of those numbers an SMS actually got to.'
            )}
          </p>
        </div>

        <div className="flex flex-wrap items-end gap-2">
          <label className="text-xs font-medium text-gray-600">
            {t('staffReport.from', 'From')}
            <input
              type="date"
              value={range.from}
              max={range.to}
              onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
              className="mt-1 block rounded border px-2 py-1 text-sm"
            />
          </label>
          <label className="text-xs font-medium text-gray-600">
            {t('staffReport.to', 'To')}
            <input
              type="date"
              value={range.to}
              min={range.from}
              onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
              className="mt-1 block rounded border px-2 py-1 text-sm"
            />
          </label>
        </div>
      </div>

      {loading && !report ? (
        <div className="flex h-48 items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
        </div>
      ) : (
        <>
          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Stat
              label={t('staffReport.totalRegistrations', 'Registrations')}
              value={report?.totalRegistrations ?? 0}
            />
            <Stat
              label={t('staffReport.totalReached', 'Reachable')}
              value={
                overallReach === null
                  ? '—'
                  : `${report.totalReached} (${overallReach}%)`
              }
              hint={t('staffReport.reachedHint', 'An SMS was delivered to this many of them.')}
            />
            <Stat
              label={t('staffReport.totalBonus', 'Bonus credited')}
              value={formatMoney(report?.totalBonusGranted)}
            />
          </div>

          {!report?.staff?.length ? (
            <p className="rounded-lg border bg-white p-6 text-sm text-gray-500">
              {t('staffReport.empty', 'No guests were registered at the till in this period.')}
            </p>
          ) : (
            <div className="overflow-x-auto rounded-lg border bg-white">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-left text-xs uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-4 py-2">{t('staffReport.employee', 'Employee')}</th>
                    <th className="px-4 py-2 text-right">
                      {t('staffReport.registrations', 'Registrations')}
                    </th>
                    <th className="px-4 py-2 text-right">{t('staffReport.reach', 'Reach')}</th>
                    <th className="px-4 py-2 text-right">{t('staffReport.bonus', 'Bonus')}</th>
                    <th className="px-4 py-2">{t('staffReport.busiestDays', 'Busiest days')}</th>
                  </tr>
                </thead>
                <tbody>
                  {report.staff.map((row) => (
                    <tr key={row.userId} data-testid={`staff-row-${row.userId}`} className="border-t">
                      <td className="px-4 py-2">
                        <div className="font-medium">{row.staffName}</div>
                        {row.role && <div className="text-xs text-gray-500">{row.role}</div>}
                      </td>
                      <td className="px-4 py-2 text-right font-medium">{row.registrations}</td>
                      <td className="px-4 py-2 text-right">
                        <ReachCell row={row} t={t} />
                      </td>
                      <td className="px-4 py-2 text-right">{formatMoney(row.bonusGranted)}</td>
                      <td className="px-4 py-2">
                        <BusiestDays daily={row.daily} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className="mt-3 max-w-3xl text-xs text-gray-500">
            {t(
              'staffReport.footnote',
              'Self-registrations from the online menu are not counted here — those phones went through an OTP. A low reach rate usually means mistyped numbers rather than anything worse, but it does mean those guests can never be contacted again.'
            )}
          </p>
        </>
      )}
    </div>
  );
}

function Stat({ label, value, hint }) {
  return (
    <div className="rounded-lg border bg-white p-4">
      <div className="text-xs uppercase tracking-wide text-gray-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold">{value}</div>
      {hint && <div className="mt-1 text-xs text-gray-400">{hint}</div>}
    </div>
  );
}

/**
 * Reach, with a flag when it looks wrong — but only once there are enough registrations for the rate to
 * mean anything. Flagging one-out-of-two would put a warning next to half the staff on a quiet week and
 * teach everyone to ignore it.
 */
function ReachCell({ row, t }) {
  const rate = row.reachRatePercent;
  const concerning =
    rate !== null &&
    rate !== undefined &&
    row.registrations >= REACH_SAMPLE_FLOOR &&
    rate < REACH_CONCERN_PERCENT;

  return (
    <span
      data-testid={`reach-${row.userId}`}
      data-concerning={concerning ? 'true' : 'false'}
      className={`inline-flex items-center justify-end gap-1 ${
        concerning ? 'font-medium text-amber-700' : ''
      }`}
      title={
        concerning
          ? t('staffReport.reachWarning', 'Most of these numbers have never received an SMS.')
          : undefined
      }
    >
      {concerning && <AlertTriangle className="h-3.5 w-3.5" />}
      {row.reached}/{row.registrations}
      {rate !== null && rate !== undefined ? ` (${rate}%)` : ''}
    </span>
  );
}

/** The top three days, so a spike reads at a glance without a chart. */
function BusiestDays({ daily }) {
  if (!daily?.length) return <span className="text-gray-400">—</span>;
  const top = [...daily].sort((a, b) => b.registrations - a.registrations).slice(0, 3);
  return (
    <div className="flex flex-wrap gap-1">
      {top.map((d) => (
        <span key={d.date} className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-700">
          {d.date} · {d.registrations}
        </span>
      ))}
    </div>
  );
}
