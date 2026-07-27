import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { notifyError, notifySuccess, notifyWarning } from '../../lib/errors';
import { reservationAPI } from '../../services/api';

/**
 * Hold a table from the map (V184), in the fewest fields the backend will accept.
 *
 * <p>Deliberately not the full reservation form — that already exists on the Reservations page. This is
 * the "somebody just walked up and asked for 8pm" case, where a host standing at the door needs four
 * fields and a button, not a page navigation that loses the map they were looking at.
 *
 * <p>The table is pre-filled from the shape that was clicked and cannot be changed here: picking a
 * different table from a dialog opened by clicking a table is how a reservation lands on the wrong one.
 */
export default function QuickReserveDialog({ table, restaurantId, onClose, onReserved }) {
  const { t } = useTranslation();
  const today = new Date().toISOString().slice(0, 10);
  const [form, setForm] = useState({
    customerName: '',
    customerPhone: '',
    reservationDate: today,
    reservationTime: '',
    partySize: table?.capacity || 2,
    specialRequests: '',
  });
  const [saving, setSaving] = useState(false);

  if (!table) return null;

  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    if (!form.customerName.trim() || !form.customerPhone.trim() || !form.reservationTime) {
      // A client-side validation miss is not a server error — notifyWarning takes plain text,
      // notifyError expects an error object and would render "Something went wrong" instead.
      notifyWarning(t('floorPlan.reserveMissing', 'Name, phone and time are required.'));
      return;
    }
    setSaving(true);
    try {
      await reservationAPI.create(restaurantId, {
        ...form,
        partySize: Number(form.partySize) || 1,
        tableId: table.id,
        source: 'FLOOR_MAP',
      });
      notifySuccess(
        t('floorPlan.reserveSuccess', 'Table {{table}} reserved.', { table: table.tableNumber })
      );
      onReserved?.();
      onClose();
    } catch (error) {
      notifyError(error, t('floorPlan.reserveFailed', 'Could not create the reservation.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
      <form
        onSubmit={submit}
        data-testid="quick-reserve-dialog"
        className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl"
      >
        <h2 className="text-lg font-semibold">
          {t('floorPlan.quickReserveFor', 'Reserve table {{table}}', { table: table.tableNumber })}
        </h2>

        <div className="mt-4 space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="qr-name">
              {t('floorPlan.guestName', 'Guest name')}
            </label>
            <input
              id="qr-name"
              value={form.customerName}
              onChange={update('customerName')}
              className="mt-1 w-full rounded border px-3 py-2 text-sm"
              maxLength={100}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="qr-phone">
              {t('floorPlan.guestPhone', 'Phone')}
            </label>
            <input
              id="qr-phone"
              value={form.customerPhone}
              onChange={update('customerPhone')}
              className="mt-1 w-full rounded border px-3 py-2 text-sm"
              maxLength={20}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700" htmlFor="qr-date">
                {t('floorPlan.date', 'Date')}
              </label>
              <input
                id="qr-date"
                type="date"
                value={form.reservationDate}
                onChange={update('reservationDate')}
                min={today}
                className="mt-1 w-full rounded border px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700" htmlFor="qr-time">
                {t('floorPlan.time', 'Time')}
              </label>
              <input
                id="qr-time"
                type="time"
                value={form.reservationTime}
                onChange={update('reservationTime')}
                className="mt-1 w-full rounded border px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="qr-party">
              {t('floorPlan.partySize', 'Party size')}
            </label>
            <input
              id="qr-party"
              type="number"
              min={1}
              max={100}
              value={form.partySize}
              onChange={update('partySize')}
              className="mt-1 w-full rounded border px-3 py-2 text-sm"
            />
            {Number(form.partySize) > (table.capacity || 0) && (
              /* A warning, not a block — pulling an extra chair over is normal, and refusing the
                 booking because the stored capacity says 4 would just push staff back to paper. */
              <p className="mt-1 text-xs text-amber-600">
                {t('floorPlan.overCapacity', 'Larger than this table’s {{capacity}} seats.', {
                  capacity: table.capacity || 0,
                })}
              </p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="qr-notes">
              {t('floorPlan.notes', 'Notes')}
            </label>
            <textarea
              id="qr-notes"
              value={form.specialRequests}
              onChange={update('specialRequests')}
              rows={2}
              maxLength={500}
              className="mt-1 w-full rounded border px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded border px-4 py-2 text-sm hover:bg-gray-50"
          >
            {t('common.cancel', 'Cancel')}
          </button>
          <button
            type="submit"
            disabled={saving}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? t('common.saving', 'Saving...') : t('floorPlan.reserve', 'Reserve')}
          </button>
        </div>
      </form>
    </div>
  );
}
