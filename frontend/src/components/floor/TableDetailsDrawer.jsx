import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CalendarPlus, ExternalLink, Receipt, User, X } from 'lucide-react';
import { seatedDuration, tableColors } from './shapes';

/**
 * What a clicked table shows (V184): who is at it, what they are spending, and the two things staff
 * actually want to do next — open the guest, or hold the table for someone.
 *
 * <p>The guest link is the point of the whole panel. Seeing "table 12 is occupied" is what the old
 * card grid already did; being one click from that guest's history, allergies and spend is what makes
 * the map worth opening.
 */
export default function TableDetailsDrawer({ table, onClose, onQuickReserve, currency = 'UZS' }) {
  const { t } = useTranslation();
  if (!table) return null;

  const colors = tableColors(table);
  const occupancy = table.occupancy;
  const duration = seatedDuration(occupancy?.seatedSince);

  const formatMoney = (value) => {
    if (value === null || value === undefined) return '—';
    const n = Number(value);
    if (Number.isNaN(n)) return '—';
    return `${n.toLocaleString()} ${currency}`;
  };

  return (
    <aside
      data-testid="table-drawer"
      className="fixed right-0 top-0 z-50 flex h-full w-full max-w-sm flex-col border-l bg-white shadow-xl"
      role="dialog"
      aria-label={t('floorPlan.tableDetails', 'Table details')}
    >
      <header className="flex items-start justify-between border-b p-4">
        <div>
          <h2 className="text-lg font-semibold">
            {t('floorPlan.table', 'Table')} {table.tableNumber}
            {table.tableName ? ` · ${table.tableName}` : ''}
          </h2>
          <p className="mt-1 text-sm text-gray-500">
            {t('floorPlan.seats', '{{count}} seats', { count: table.capacity || 0 })}
            {table.section ? ` · ${table.section}` : ''}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label={t('common.close', 'Close')}
          className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
        >
          <X className="h-5 w-5" />
        </button>
      </header>

      <div className="flex-1 overflow-y-auto p-4">
        <span
          className="inline-block rounded-full px-3 py-1 text-xs font-medium"
          style={{ backgroundColor: colors.fill, color: colors.text }}
        >
          {occupancy
            ? t('floorPlan.status.occupied', 'Occupied')
            : t(`floorPlan.status.${colors.state}`, colors.state)}
        </span>

        {occupancy ? (
          <div className="mt-4 space-y-4">
            <div className="rounded-lg border p-3">
              <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
                <Receipt className="h-4 w-4" />
                {t('floorPlan.currentOrder', 'Current order')}
              </div>
              <dl className="mt-2 space-y-1 text-sm">
                <div className="flex justify-between">
                  <dt className="text-gray-500">{t('floorPlan.orderNumber', 'Order')}</dt>
                  <dd className="font-medium">{occupancy.orderNumber}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">{t('floorPlan.total', 'Total')}</dt>
                  <dd className="font-medium">{formatMoney(occupancy.orderTotal)}</dd>
                </div>
                {duration && (
                  <div className="flex justify-between">
                    <dt className="text-gray-500">{t('floorPlan.seatedFor', 'Seated for')}</dt>
                    <dd className="font-medium">{duration}</dd>
                  </div>
                )}
              </dl>
            </div>

            <div className="rounded-lg border p-3">
              <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
                <User className="h-4 w-4" />
                {t('floorPlan.guest', 'Guest')}
              </div>
              {occupancy.customerId ? (
                <>
                  <p className="mt-2 text-sm font-medium">{occupancy.customerName}</p>
                  <Link
                    to={`/customers/${occupancy.customerId}`}
                    className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline"
                  >
                    {t('floorPlan.openProfile', 'Open guest profile')}
                    <ExternalLink className="h-3.5 w-3.5" />
                  </Link>
                </>
              ) : (
                /* A walk-in: nobody typed a name at seating, and inventing one would be worse than
                   saying so. Capture happens at payment, where the bonus offer already lives. */
                <p className="mt-2 text-sm text-gray-500">
                  {t('floorPlan.walkIn', 'Walk-in — no guest record linked to this order.')}
                </p>
              )}
            </div>
          </div>
        ) : (
          <p className="mt-4 text-sm text-gray-500">
            {t('floorPlan.tableFree', 'Nobody is seated here right now.')}
          </p>
        )}

        {table.mergedIntoTableId && (
          <p className="mt-4 rounded bg-purple-50 p-2 text-xs text-purple-800">
            {t('floorPlan.mergedInto', 'Merged into table #{{id}} — the party is counted there.', {
              id: table.mergedIntoTableId,
            })}
          </p>
        )}
      </div>

      <footer className="border-t p-4">
        <button
          type="button"
          onClick={() => onQuickReserve(table)}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <CalendarPlus className="h-4 w-4" />
          {t('floorPlan.quickReserve', 'Quick reservation')}
        </button>
      </footer>
    </aside>
  );
}
