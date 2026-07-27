import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { notifyError, notifySuccess, notifyWarning } from '../../lib/errors';
import { tablesAPI } from '../../services/api';

/**
 * Create a table from the map (V184).
 *
 * <p>A table is not furniture: it needs a number and a capacity, and it will carry orders, a QR code
 * and history. So it is created as a real record through the Tables API rather than being drawn into
 * existence — the map only decides where it sits.
 *
 * <p>The record is created <b>immediately</b>, before the layout is saved. That is deliberate: the
 * server assigns the id, and the map has nothing to place without one. It also means cancelling the
 * edit session afterwards leaves a real, unplaced table behind — which the confirmation says out loud
 * rather than leaving the operator to discover it on the Tables page.
 */
export default function AddTableDialog({ restaurantId, existingNumbers = [], onClose, onCreated }) {
  const { t } = useTranslation();
  const [form, setForm] = useState({ tableNumber: '', tableName: '', capacity: 4, section: '' });
  const [saving, setSaving] = useState(false);

  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const number = form.tableNumber.trim();
    if (!number) {
      notifyWarning(t('floorPlan.tableNumberRequired', 'A table needs a number.'));
      return;
    }
    if (existingNumbers.includes(number)) {
      // Caught here as well as server-side: two tables called "12" are indistinguishable on the map
      // and on the bill, and the operator should find out before the round trip.
      notifyWarning(
        t('floorPlan.tableNumberTaken', 'Table {{number}} already exists.', { number })
      );
      return;
    }
    setSaving(true);
    try {
      const res = await tablesAPI.create({
        restaurantId,
        tableNumber: number,
        tableName: form.tableName.trim() || null,
        capacity: Number(form.capacity) || 1,
        section: form.section.trim() || null,
        active: true,
      });
      const created = res.data?.data || res.data;
      notifySuccess(
        t(
          'floorPlan.tableCreated',
          'Table {{number}} created. Save the layout to place it on this map.',
          { number }
        )
      );
      onCreated(created);
      onClose();
    } catch (error) {
      notifyError(error, { title: t('floorPlan.tableCreateFailed', 'Could not create the table') });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
      <form
        onSubmit={submit}
        data-testid="add-table-dialog"
        className="w-full max-w-sm rounded-lg bg-white p-5 shadow-xl"
      >
        <h2 className="text-lg font-semibold">{t('floorPlan.addTable', 'Add table')}</h2>

        <div className="mt-4 space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="at-number">
              {t('floorPlan.tableNumber', 'Table number')}
            </label>
            <input
              id="at-number"
              value={form.tableNumber}
              onChange={update('tableNumber')}
              maxLength={50}
              className="mt-1 w-full rounded border px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="at-name">
              {t('floorPlan.tableNameOptional', 'Name (optional)')}
            </label>
            <input
              id="at-name"
              value={form.tableName}
              onChange={update('tableName')}
              maxLength={100}
              className="mt-1 w-full rounded border px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700" htmlFor="at-capacity">
                {t('floorPlan.capacity', 'Capacity')}
              </label>
              <input
                id="at-capacity"
                type="number"
                min={1}
                value={form.capacity}
                onChange={update('capacity')}
                className="mt-1 w-full rounded border px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700" htmlFor="at-section">
                {t('floorPlan.sectionLabel', 'Section')}
              </label>
              <input
                id="at-section"
                value={form.section}
                onChange={update('section')}
                maxLength={100}
                className="mt-1 w-full rounded border px-3 py-2 text-sm"
              />
            </div>
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
            {saving ? t('common.saving', 'Saving...') : t('common.create', 'Create')}
          </button>
        </div>
      </form>
    </div>
  );
}
