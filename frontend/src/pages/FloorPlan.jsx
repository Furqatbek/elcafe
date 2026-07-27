import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Grid3x3, Loader2, Map, Pencil, Plus, Save, Square, Trash2, X } from 'lucide-react';
import { notifyError, notifySuccess, notifyWarning } from '../lib/errors';
import { floorPlanAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { getCurrentRestaurantId } from '../utils/restaurant';
import websocketService from '../services/websocket';
import FloorCanvas from '../components/floor/FloorCanvas';
import TableDetailsDrawer from '../components/floor/TableDetailsDrawer';
import QuickReserveDialog from '../components/floor/QuickReserveDialog';
import AddTableDialog from '../components/floor/AddTableDialog';
import { OBJECT_DEFAULT_SIZE, OBJECT_TYPES, SHAPES } from '../components/floor/shapes';

/** Mirrors FloorPlanController's write gate. The server enforces it; this only hides what would 403. */
const EDIT_ROLES = ['ADMIN', 'OWNER', 'MANAGER', 'SUPER_ADMIN'];

/**
 * How often the map re-reads occupancy when nothing has pushed. The WebSocket carries every order
 * transition, so this is a safety net for a dropped socket rather than the primary mechanism — long
 * enough not to hammer the API, short enough that a host never trusts a stale room for a whole service.
 */
const FALLBACK_REFRESH_MS = 30000;

/** A negative id marks a shape that only exists in the browser — the server assigns the real one on save. */
let draftIdSeq = -1;
const nextDraftId = () => draftIdSeq--;

export default function FloorPlanPage() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const restaurantId = getCurrentRestaurantId() || user?.restaurantId;
  const canEdit = EDIT_ROLES.includes(user?.role);

  const [plans, setPlans] = useState([]);
  const [activePlanId, setActivePlanId] = useState(null);
  const [plan, setPlan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [editMode, setEditMode] = useState(false);
  const [snapToGrid, setSnapToGrid] = useState(true);
  const [selected, setSelected] = useState(null);
  const [drawingSection, setDrawingSection] = useState(false);
  const [draftPolygon, setDraftPolygon] = useState([]);

  const [drawerTable, setDrawerTable] = useState(null);
  const [reserveTable, setReserveTable] = useState(null);
  const [addingTable, setAddingTable] = useState(false);

  // Read inside callbacks that must not re-subscribe the socket every time the map changes.
  const activePlanRef = useRef(null);
  const editModeRef = useRef(false);
  useEffect(() => {
    activePlanRef.current = activePlanId;
  }, [activePlanId]);
  useEffect(() => {
    editModeRef.current = editMode;
  }, [editMode]);

  /**
   * `t` is read through a ref rather than closed over. It is a fresh function on every render, so
   * putting it in a dependency array makes {@link loadPlan} unstable — and an unstable loader turns
   * the mount effect below into a fetch-on-every-render loop, which is an API hammer, not a page.
   */
  const tRef = useRef(t);
  tRef.current = t;

  const loadPlan = useCallback(async (planId, { silent = false } = {}) => {
    if (!silent) setLoading(true);
    try {
      const res = await floorPlanAPI.get(planId);
      const loaded = res.data?.data || res.data;
      setPlan(loaded);
      if (loaded?.id) setActivePlanId(loaded.id);
      return loaded;
    } catch (error) {
      notifyError(error, {
        title: tRef.current('floorPlan.loadFailed', 'Could not load the floor map'),
      });
      return null;
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await floorPlanAPI.list();
        const list = res.data?.data || res.data || [];
        if (cancelled) return;
        setPlans(list);
      } catch (error) {
        if (!cancelled) {
          notifyError(error, {
            title: tRef.current('floorPlan.loadFailed', 'Could not load the floor map'),
          });
        }
      }
      if (!cancelled) await loadPlan(null);
    })();
    return () => {
      cancelled = true;
    };
  }, [loadPlan]);

  /**
   * Live updates. The broadcast is a signal, not a payload, so the map re-reads the plan it is
   * actually showing — a restaurant can have several maps open on several tablets, and pushing a whole
   * plan would send three rooms nobody is looking at.
   *
   * <p>An in-progress edit is never overwritten: a repaint mid-drag would throw away unsaved work.
   */
  useEffect(() => {
    if (!restaurantId) return undefined;
    const destination = `/topic/restaurant/${restaurantId}/floor`;
    let active = true;

    const onEvent = () => {
      if (!active || editModeRef.current) return;
      loadPlan(activePlanRef.current, { silent: true });
    };

    websocketService
      .connect()
      .then(() => {
        if (active) websocketService.subscribe(destination, onEvent);
      })
      .catch(() => {
        // The fallback poll below covers this; a failed socket must not blank the page.
      });

    const timer = setInterval(() => {
      if (!editModeRef.current) loadPlan(activePlanRef.current, { silent: true });
    }, FALLBACK_REFRESH_MS);

    return () => {
      active = false;
      clearInterval(timer);
      websocketService.unsubscribe(destination);
    };
  }, [restaurantId, loadPlan]);

  const stats = useMemo(() => {
    const tables = plan?.tables || [];
    const occupied = tables.filter((x) => x.occupancy).length;
    return { total: tables.length, occupied, free: tables.length - occupied };
  }, [plan]);

  // -------------------------------------------------------------------------------------------
  // Editing
  // -------------------------------------------------------------------------------------------

  const mutateItem = useCallback((kind, id, patch) => {
    setPlan((current) => {
      if (!current) return current;
      const key = kind === 'table' ? 'tables' : kind === 'object' ? 'objects' : 'sections';
      return {
        ...current,
        [key]: (current[key] || []).map((item) => (item.id === id ? { ...item, ...patch } : item)),
      };
    });
  }, []);

  const addObject = (objectType) => {
    const size = OBJECT_DEFAULT_SIZE[objectType] || OBJECT_DEFAULT_SIZE.OTHER;
    const id = nextDraftId();
    setPlan((current) => ({
      ...current,
      objects: [
        ...(current?.objects || []),
        {
          id,
          objectType,
          label: '',
          positionX: 60,
          positionY: 60,
          ...size,
          rotationDeg: 0,
          shape: objectType === 'PLANT' ? 'OVAL' : 'RECTANGLE',
          zIndex: 0,
        },
      ],
    }));
    setSelected({ kind: 'object', id });
  };

  /**
   * A freshly created table joins the draft at a default size and position, and the single Save
   * places it — `saveLayout` sets `floorPlanId` for every table in the request.
   */
  const placeNewTable = (created) => {
    if (!created?.id) return;
    setPlan((current) => ({
      ...current,
      tables: [
        ...(current?.tables || []),
        {
          ...created,
          positionX: 80,
          positionY: 80,
          width: 100,
          height: 100,
          rotationDeg: 0,
          shape: 'RECTANGLE',
          zIndex: 0,
          occupancy: null,
        },
      ],
    }));
    setSelected({ kind: 'table', id: created.id });
  };

  const removeSelected = () => {
    if (!selected) return;
    if (selected.kind === 'table') {
      // Tables are not the map's to delete — they carry orders, QR codes and history. Removing one
      // from the plan unplaces it; deleting it for real belongs on the Tables page.
      notifyWarning(
        t('floorPlan.cannotDeleteTable', 'Tables are managed on the Tables page. Move it instead.')
      );
      return;
    }
    const key = selected.kind === 'object' ? 'objects' : 'sections';
    setPlan((current) => ({
      ...current,
      [key]: (current?.[key] || []).filter((item) => item.id !== selected.id),
    }));
    setSelected(null);
  };

  const finishPolygon = () => {
    if (draftPolygon.length < 3) {
      notifyWarning(t('floorPlan.polygonTooShort', 'A section needs at least three points.'));
      return;
    }
    const id = nextDraftId();
    setPlan((current) => ({
      ...current,
      sections: [
        ...(current?.sections || []),
        {
          id,
          name: t('floorPlan.newSection', 'New section'),
          polygon: draftPolygon,
          fillColor: '#e0f2fe',
          zIndex: 0,
        },
      ],
    }));
    setDraftPolygon([]);
    setDrawingSection(false);
    setSelected({ kind: 'section', id });
  };

  const saveLayout = async () => {
    if (!plan?.id) return;
    setSaving(true);
    try {
      const payload = {
        tables: (plan.tables || []).map((x) => ({
          id: x.id,
          positionX: x.positionX,
          positionY: x.positionY,
          width: x.width,
          height: x.height,
          rotationDeg: x.rotationDeg,
          shape: x.shape,
          zIndex: x.zIndex,
        })),
        // Drafts lose their negative placeholder id — the server treats a null id as "new".
        objects: (plan.objects || []).map((x) => ({
          id: x.id > 0 ? x.id : null,
          objectType: x.objectType,
          label: x.label,
          positionX: x.positionX,
          positionY: x.positionY,
          width: x.width,
          height: x.height,
          rotationDeg: x.rotationDeg,
          shape: x.shape,
          zIndex: x.zIndex,
        })),
        sections: (plan.sections || []).map((x) => ({
          id: x.id > 0 ? x.id : null,
          name: x.name,
          polygon: x.polygon,
          fillColor: x.fillColor,
          zIndex: x.zIndex,
        })),
      };
      const res = await floorPlanAPI.saveLayout(plan.id, payload);
      setPlan(res.data?.data || res.data);
      setEditMode(false);
      setSelected(null);
      notifySuccess(t('floorPlan.saved', 'Floor map saved.'));
    } catch (error) {
      notifyError(error, { title: t('floorPlan.saveFailed', 'Could not save the floor map') });
    } finally {
      setSaving(false);
    }
  };

  const cancelEdit = async () => {
    setEditMode(false);
    setSelected(null);
    setDrawingSection(false);
    setDraftPolygon([]);
    await loadPlan(activePlanId); // discard local edits by re-reading the stored map
  };

  const selectedItem = useMemo(() => {
    if (!selected || !plan) return null;
    const key = selected.kind === 'table' ? 'tables' : selected.kind === 'object' ? 'objects' : 'sections';
    return (plan[key] || []).find((x) => x.id === selected.id) || null;
  }, [selected, plan]);

  if (loading && !plan) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  return (
    <div className="p-4">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold">
            <Map className="h-6 w-6" />
            {t('floorPlan.title', 'Floor map')}
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            {t('floorPlan.summary', '{{total}} tables · {{occupied}} occupied · {{free}} free', stats)}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {plans.length > 1 && (
            <select
              aria-label={t('floorPlan.switchMap', 'Switch map')}
              value={activePlanId || ''}
              onChange={(e) => loadPlan(Number(e.target.value))}
              disabled={editMode}
              className="rounded border px-3 py-2 text-sm"
            >
              {plans.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          )}

          {canEdit && !editMode && (
            <button
              type="button"
              onClick={() => setEditMode(true)}
              className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium hover:bg-gray-50"
            >
              <Pencil className="h-4 w-4" />
              {t('floorPlan.edit', 'Edit layout')}
            </button>
          )}

          {editMode && (
            <>
              <button
                type="button"
                onClick={cancelEdit}
                className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm hover:bg-gray-50"
              >
                <X className="h-4 w-4" />
                {t('common.cancel', 'Cancel')}
              </button>
              <button
                type="button"
                onClick={saveLayout}
                disabled={saving}
                className="flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                {t('common.save', 'Save')}
              </button>
            </>
          )}
        </div>
      </div>

      {editMode && (
        <div className="mb-3 flex flex-wrap items-center gap-2 rounded-lg border bg-gray-50 p-3">
          <span className="text-sm font-medium text-gray-700">{t('floorPlan.add', 'Add')}:</span>
          {/* A table is a real record with a number and a capacity, so it goes through a dialog
              rather than being dropped like furniture. */}
          <button
            type="button"
            onClick={() => setAddingTable(true)}
            className="flex items-center gap-1 rounded border border-blue-300 bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100"
          >
            <Plus className="h-3 w-3" />
            {t('floorPlan.table', 'Table')}
          </button>
          {OBJECT_TYPES.map((type) => (
            <button
              key={type}
              type="button"
              onClick={() => addObject(type)}
              className="flex items-center gap-1 rounded border bg-white px-2 py-1 text-xs hover:bg-gray-100"
            >
              <Plus className="h-3 w-3" />
              {t(`floorPlan.object.${type}`, type)}
            </button>
          ))}

          <span className="mx-2 h-5 w-px bg-gray-300" />

          {drawingSection ? (
            <>
              <button
                type="button"
                onClick={finishPolygon}
                className="rounded bg-blue-600 px-2 py-1 text-xs font-medium text-white"
              >
                {t('floorPlan.finishSection', 'Finish section ({{n}} points)', {
                  n: draftPolygon.length,
                })}
              </button>
              <button
                type="button"
                onClick={() => {
                  setDrawingSection(false);
                  setDraftPolygon([]);
                }}
                className="rounded border bg-white px-2 py-1 text-xs"
              >
                {t('common.cancel', 'Cancel')}
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => setDrawingSection(true)}
              className="flex items-center gap-1 rounded border bg-white px-2 py-1 text-xs hover:bg-gray-100"
            >
              <Square className="h-3 w-3" />
              {t('floorPlan.drawSection', 'Draw section')}
            </button>
          )}

          <span className="mx-2 h-5 w-px bg-gray-300" />

          <label className="flex items-center gap-1 text-xs text-gray-700">
            <input
              type="checkbox"
              checked={snapToGrid}
              onChange={(e) => setSnapToGrid(e.target.checked)}
            />
            <Grid3x3 className="h-3 w-3" />
            {t('floorPlan.snap', 'Snap to grid')}
          </label>
        </div>
      )}

      <div className="flex flex-col gap-4 lg:flex-row">
        <div className="min-w-0 flex-1">
          <FloorCanvas
            plan={plan}
            editMode={editMode}
            snapToGrid={snapToGrid}
            selected={selected}
            onSelect={setSelected}
            onTableClick={setDrawerTable}
            onGeometryChange={mutateItem}
            drawingSection={drawingSection}
            draftPolygon={draftPolygon}
            onPolygonPoint={(p) => setDraftPolygon((current) => [...current, p])}
          />

          {!editMode && (
            <div className="mt-3 flex flex-wrap gap-4 text-xs text-gray-600">
              <Legend color="#bbf7d0" border="#16a34a" label={t('floorPlan.status.free', 'Free')} />
              <Legend
                color="#fecaca"
                border="#dc2626"
                label={t('floorPlan.status.occupied', 'Occupied')}
              />
              <Legend
                color="#fde68a"
                border="#d97706"
                label={t('floorPlan.status.reserved', 'Reserved')}
              />
              <Legend
                color="#e0e7ff"
                border="#6366f1"
                label={t('floorPlan.status.cleaning', 'Cleaning')}
              />
            </div>
          )}
        </div>

        {editMode && selectedItem && (
          <Inspector
            kind={selected.kind}
            item={selectedItem}
            onChange={(patch) => mutateItem(selected.kind, selected.id, patch)}
            onDelete={removeSelected}
          />
        )}
      </div>

      {drawerTable && (
        <TableDetailsDrawer
          table={
            // Re-read from the live plan so the drawer follows the map instead of freezing the
            // snapshot taken at click time — otherwise a party that pays keeps showing as seated.
            (plan?.tables || []).find((x) => x.id === drawerTable.id) || drawerTable
          }
          onClose={() => setDrawerTable(null)}
          onQuickReserve={(table) => {
            setReserveTable(table);
            setDrawerTable(null);
          }}
        />
      )}

      {addingTable && (
        <AddTableDialog
          restaurantId={restaurantId}
          existingNumbers={(plan?.tables || []).map((x) => x.tableNumber)}
          onClose={() => setAddingTable(false)}
          onCreated={placeNewTable}
        />
      )}

      {reserveTable && (
        <QuickReserveDialog
          table={reserveTable}
          restaurantId={restaurantId}
          onClose={() => setReserveTable(null)}
          onReserved={() => loadPlan(activePlanId, { silent: true })}
        />
      )}
    </div>
  );
}

function Legend({ color, border, label }) {
  return (
    <span className="flex items-center gap-1">
      <span
        className="inline-block h-3 w-4 rounded-sm"
        style={{ backgroundColor: color, border: `1.5px solid ${border}` }}
      />
      {label}
    </span>
  );
}

/** The properties panel for whatever is selected — the shape picker the operator asked for lives here. */
function Inspector({ kind, item, onChange, onDelete }) {
  const { t } = useTranslation();
  return (
    <div className="w-full shrink-0 rounded-lg border bg-white p-4 lg:w-72">
      <h3 className="mb-3 text-sm font-semibold">
        {kind === 'table'
          ? `${t('floorPlan.table', 'Table')} ${item.tableNumber}`
          : kind === 'object'
            ? t(`floorPlan.object.${item.objectType}`, item.objectType)
            : t('floorPlan.section', 'Section')}
      </h3>

      {kind === 'section' ? (
        <>
          <Field label={t('floorPlan.sectionName', 'Name')}>
            <input
              value={item.name || ''}
              onChange={(e) => onChange({ name: e.target.value })}
              maxLength={100}
              className="w-full rounded border px-2 py-1 text-sm"
            />
          </Field>
          <Field label={t('floorPlan.fillColor', 'Fill colour')}>
            <input
              type="color"
              value={item.fillColor || '#e0f2fe'}
              onChange={(e) => onChange({ fillColor: e.target.value })}
              className="h-8 w-full rounded border"
            />
          </Field>
        </>
      ) : (
        <>
          {kind === 'object' && (
            <Field label={t('floorPlan.label', 'Label')}>
              <input
                value={item.label || ''}
                onChange={(e) => onChange({ label: e.target.value })}
                maxLength={100}
                className="w-full rounded border px-2 py-1 text-sm"
              />
            </Field>
          )}

          <Field label={t('floorPlan.corners', 'Corners')}>
            <div className="grid grid-cols-2 gap-1">
              {SHAPES.map((shape) => (
                <button
                  key={shape}
                  type="button"
                  aria-pressed={item.shape === shape}
                  onClick={() => onChange({ shape })}
                  className={`rounded border px-2 py-1 text-xs ${
                    item.shape === shape ? 'border-blue-600 bg-blue-50 font-medium' : 'hover:bg-gray-50'
                  }`}
                >
                  {t(`floorPlan.shape.${shape}`, shape)}
                </button>
              ))}
            </div>
          </Field>

          <div className="grid grid-cols-2 gap-2">
            <Field label={t('floorPlan.width', 'Width')}>
              <input
                type="number"
                min={1}
                max={5000}
                value={item.width ?? ''}
                onChange={(e) => onChange({ width: Number(e.target.value) })}
                className="w-full rounded border px-2 py-1 text-sm"
              />
            </Field>
            <Field label={t('floorPlan.height', 'Height')}>
              <input
                type="number"
                min={1}
                max={5000}
                value={item.height ?? ''}
                onChange={(e) => onChange({ height: Number(e.target.value) })}
                className="w-full rounded border px-2 py-1 text-sm"
              />
            </Field>
          </div>

          <Field label={t('floorPlan.rotation', 'Rotation')}>
            <input
              type="range"
              min={0}
              max={359}
              value={item.rotationDeg || 0}
              onChange={(e) => onChange({ rotationDeg: Number(e.target.value) })}
              className="w-full"
            />
            <span className="text-xs text-gray-500">{item.rotationDeg || 0}°</span>
          </Field>
        </>
      )}

      <button
        type="button"
        onClick={onDelete}
        className="mt-3 flex w-full items-center justify-center gap-1 rounded border border-red-200 px-2 py-1 text-xs text-red-600 hover:bg-red-50"
      >
        <Trash2 className="h-3 w-3" />
        {t('common.delete', 'Delete')}
      </button>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <div className="mb-3">
      <label className="mb-1 block text-xs font-medium text-gray-600">{label}</label>
      {children}
    </div>
  );
}
