import {
  useEffect,
  useState,
  useCallback,
  useMemo,
  useRef,
} from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI, posAPI, tablesAPI, restaurantAPI, menuAPI } from '../services/api';
import { useWebSocketNotifications } from '../hooks/useWebSocketNotifications';
import { useToast } from '../hooks/useToast';
import { useCurrentRestaurantId } from '../utils/restaurant';
import {
  Search,
  X,
  RefreshCw,
  ArrowRightLeft,
  AlertTriangle,
  Loader2,
  Plus,
  Minus,
  Trash2,
  CreditCard,
  Banknote,
  Wallet,
  ChevronRight,
  Copy,
} from 'lucide-react';

/* -------------------------------------------------------------------------- */
/* Visual tokens — every visual decision is configurable from this block.     */
/* -------------------------------------------------------------------------- */

const TILE_HEIGHT = 70;
const TILE_MIN_WIDTH = 96;
const TILE_GAP = 8;
const SECTION_GAP = 18;
const DRAWER_WIDTH = 420;
const TAX_RATE = 0.12;
const TOAST_DURATION_MS = 3200;
const POLL_INTERVAL_MS = 30000;

const STATUS = {
  AVAILABLE:      { dot: '#10b981', bg: '#ecfdf5', text: '#047857', border: '#a7f3d0', label: 'Open' },
  OCCUPIED:       { dot: '#f97316', bg: '#fff7ed', text: '#9a3412', border: '#fed7aa', label: 'Occupied' },
  RESERVED:       { dot: '#3b82f6', bg: '#eff6ff', text: '#1d4ed8', border: '#bfdbfe', label: 'Reserved' },
  CLEANING:       { dot: '#eab308', bg: '#fefce8', text: '#854d0e', border: '#fde68a', label: 'Cleaning' },
  OUT_OF_SERVICE: { dot: '#94a3b8', bg: '#f8fafc', text: '#475569', border: '#cbd5e1', label: 'Out' },
};

const PHASE = {
  seated:           { color: '#94a3b8', label: 'Seated' },
  ordering:         { color: '#0ea5e9', label: 'Ordering' },
  preparing:        { color: '#f59e0b', label: 'Preparing' },
  served:           { color: '#6366f1', label: 'Served' },
  bill_requested:   { color: '#ec4899', label: 'Bill requested' },
  paid_unreleased:  { color: '#dc2626', label: 'Paid · release' },
};

const PAGE_BG = '#f8fafc';
const SURFACE = '#fff';
const TEXT = '#0f172a';
const MUTED = '#64748b';
const BORDER = '#e2e8f0';

const MONEY = { fontVariantNumeric: 'tabular-nums' };

/* -------------------------------------------------------------------------- */
/* Formatters                                                                 */
/* -------------------------------------------------------------------------- */

function fmtMoney(n) {
  const v = Math.round(Number(n) || 0);
  return v.toLocaleString('en-US').replace(/,/g, ' ');
}

function fmtMin(min) {
  if (!Number.isFinite(min) || min < 0) return '—';
  if (min < 60) return `${Math.floor(min)}m`;
  const h = Math.floor(min / 60);
  const m = Math.floor(min % 60);
  return `${h}h ${m}m`;
}

function minutesSince(iso) {
  if (!iso) return null;
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return null;
  return Math.max(0, Math.round((Date.now() - ts) / 60000));
}

/* -------------------------------------------------------------------------- */
/* API wrapper — translates axios errors into the structured taxonomy.        */
/* -------------------------------------------------------------------------- */

function classifyAxiosError(err) {
  // Code-derived classification first
  if (err?.code === 'ECONNABORTED') {
    return { kind: 'timeout', http: 0, retryable: true };
  }
  if (err?.code === 'ERR_NETWORK' || err?.message === 'Network Error') {
    return { kind: 'network', http: 0, retryable: true };
  }
  const status = err?.response?.status ?? 0;
  if (status === 0) return { kind: 'network', http: 0, retryable: true };
  if (status === 403) return { kind: 'permission', http: 403, retryable: false };
  if (status === 409) return { kind: 'conflict', http: 409, retryable: false };
  if (status === 422 || status === 400) return { kind: 'validation', http: status, retryable: false };
  if (status >= 500) return { kind: 'server', http: status, retryable: true };
  return { kind: 'server', http: status, retryable: false };
}

// Canonical default copy from the spec — surfaced when the server
// didn't include a useful message of its own.
const DEFAULT_ERROR_MESSAGES = {
  network:    'Could not reach the server. Check your connection.',
  server:     'The server hit an error. Please try again.',
  timeout:    'Request timed out after 10 seconds.',
  conflict:   'This table was just updated by someone else. Refresh and try again.',
  permission: "You don't have permission to do that.",
  validation: 'Some fields are invalid.',
};

function buildStructuredError(op, err, fallbackMessages) {
  const cls = classifyAxiosError(err);
  const serverMsg =
    err?.response?.data?.message ||
    err?.response?.data?.errors?.reason ||
    err?.message;
  const message =
    serverMsg ||
    fallbackMessages?.[cls.kind] ||
    DEFAULT_ERROR_MESSAGES[cls.kind] ||
    'Operation failed';
  return {
    op,
    kind: cls.kind,
    http: cls.http,
    retryable: cls.retryable,
    message,
    timestamp: new Date().toISOString(),
    raw: err?.response?.data ?? null,
  };
}

async function callApi(op, fn, fallbackMessages) {
  try {
    const res = await fn();
    return { ok: true, data: res?.data?.data ?? res?.data ?? null };
  } catch (err) {
    return { ok: false, error: buildStructuredError(op, err, fallbackMessages) };
  }
}

/* -------------------------------------------------------------------------- */
/* TableVM selector                                                           */
/* -------------------------------------------------------------------------- */

function deriveOrderPhase(order, table) {
  if (!order) return null;
  const status = String(order.status || '').toUpperCase();
  const paymentDone =
    !!order.fullyPaid ||
    String(order.paymentStatus || '').toUpperCase() === 'PAID' ||
    String(order.paymentStatus || '').toUpperCase() === 'COMPLETED';

  if (paymentDone && table?.status === 'OCCUPIED') return 'paid_unreleased';
  if (order.billRequested) return 'bill_requested';
  if (status === 'READY' || status === 'DELIVERED' || status === 'SERVED') return 'served';
  if (status === 'PREPARING' || status === 'ACCEPTED') return 'preparing';
  if (status === 'NEW') {
    return Array.isArray(order.items) && order.items.length > 0 ? 'ordering' : 'seated';
  }
  return 'seated';
}

function buildTableVMs(rawTables, ordersByTable) {
  return (rawTables || []).map((t) => {
    const orders = ordersByTable?.[t.id] || [];
    const primary = orders[0] || null;
    const status = (t.status || 'AVAILABLE').toUpperCase();
    const numParsed = parseInt(String(t.tableNumber ?? t.tableName ?? ''), 10);
    const num = Number.isFinite(numParsed) ? numParsed : t.id;
    const label = t.tableName || `${t.tableNumber}`;
    const floor = t.floor ?? t.level ?? 1;

    const vm = {
      id: t.id,
      num,
      label,
      section: (t.section || '').trim() || 'Main',
      floor,
      capacity: t.capacity ?? 0,
      status,
      raw: t,
    };

    if (status === 'OCCUPIED' && primary) {
      const phase = deriveOrderPhase(primary, t);
      const seatedMin = minutesSince(primary.createdAt);
      const idleMin = phase === 'paid_unreleased'
        ? minutesSince(primary.paidAt || primary.completedAt || primary.updatedAt)
        : null;
      vm.phase = phase;
      vm.guests = primary.guestCount ?? null;
      vm.seatedMin = seatedMin;
      vm.idleMin = idleMin;
      vm.items = (primary.items || []).map((it) => ({
        id: it.id,
        name: it.productName || it.name,
        qty: it.quantity,
        price: it.unitPrice ?? 0,
        lineTotal: it.totalPrice ?? (it.unitPrice || 0) * (it.quantity || 0),
        variant: it.variantName || null,
        note: it.specialInstructions || it.notes || null,
      }));
      vm.subtotal = primary.subtotal ?? 0;
      vm.tax = primary.tax ?? 0;
      vm.total = primary.total ?? primary.totalAmount ?? 0;
      vm.waiter = primary.waiter ? {
        firstName: primary.waiter.firstName || primary.waiter.name || '',
        lastName: primary.waiter.lastName || '',
      } : null;
      vm.orderId = primary.id;
      vm.orderNum = primary.orderNumber;
      vm.paymentStatus = primary.paymentStatus || (primary.fullyPaid ? 'PAID' : null);
    }

    if (status === 'RESERVED') {
      const r = t.activeReservation || t.reservation || null;
      if (r) {
        vm.reservation = {
          name: r.customerName || r.name || '',
          etaMin: r.minutesUntil ?? minutesSince(r.reservationTime) ?? null,
          guests: r.guestCount ?? r.partySize ?? null,
        };
      }
    }

    return vm;
  });
}

function groupBySection(vms) {
  const map = new Map();
  for (const vm of vms) {
    if (!map.has(vm.section)) map.set(vm.section, []);
    map.get(vm.section).push(vm);
  }
  for (const list of map.values()) {
    list.sort((a, b) => (a.num || 0) - (b.num || 0));
  }
  return Array.from(map.entries())
    .map(([name, items]) => ({ name, items, floor: items[0]?.floor ?? 1 }))
    .sort((a, b) => {
      if (a.floor !== b.floor) return a.floor - b.floor;
      return a.name.localeCompare(b.name);
    });
}

function sectionColor(name, idx) {
  const palette = ['#2563eb', '#7c3aed', '#0891b2', '#16a34a', '#ea580c', '#db2777', '#0284c7', '#65a30d'];
  if (!name) return palette[idx % palette.length];
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return palette[h % palette.length];
}

/* -------------------------------------------------------------------------- */
/* Small UI atoms                                                              */
/* -------------------------------------------------------------------------- */

function StatusDot({ status, size = 8 }) {
  const c = STATUS[status]?.dot || STATUS.AVAILABLE.dot;
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: 999,
        background: c,
        display: 'inline-block',
        flexShrink: 0,
      }}
    />
  );
}

function PhasePill({ phase }) {
  if (!phase) return null;
  const conf = PHASE[phase];
  if (!conf) return null;
  const isUrgent = phase === 'paid_unreleased';
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '2px 8px',
        borderRadius: 999,
        background: isUrgent ? '#fee2e2' : '#fff',
        border: `1px solid ${conf.color}`,
        color: conf.color,
        fontSize: 11,
        fontWeight: 600,
        lineHeight: 1.4,
        whiteSpace: 'nowrap',
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: 999, background: conf.color }} />
      {conf.label}
    </span>
  );
}

function StatChip({ label, value, accent }) {
  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '6px 10px',
        background: SURFACE,
        border: `1px solid ${BORDER}`,
        borderRadius: 8,
        fontSize: 12,
        color: MUTED,
        fontWeight: 600,
      }}
    >
      {accent && <span style={{ width: 8, height: 8, borderRadius: 999, background: accent }} />}
      <span>{label}</span>
      <span style={{ color: TEXT, fontWeight: 700, ...MONEY }}>{value}</span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* SearchJump — global / and Esc hotkeys                                       */
/* -------------------------------------------------------------------------- */

function SearchJump({ value, onChange, t }) {
  const inputRef = useRef(null);

  useEffect(() => {
    const onKey = (e) => {
      const tag = (e.target?.tagName || '').toUpperCase();
      const editable = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || e.target?.isContentEditable;
      if (e.key === '/' && !editable) {
        e.preventDefault();
        inputRef.current?.focus();
      } else if (e.key === 'Escape') {
        if (document.activeElement === inputRef.current) {
          inputRef.current?.blur();
          if (value) onChange('');
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onChange, value]);

  return (
    <div
      style={{
        position: 'relative',
        flex: 1,
        minWidth: 220,
        maxWidth: 480,
      }}
    >
      <Search
        size={16}
        style={{
          position: 'absolute',
          left: 12,
          top: '50%',
          transform: 'translateY(-50%)',
          color: MUTED,
          pointerEvents: 'none',
        }}
      />
      <input
        ref={inputRef}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t('orders.searchPlaceholder', 'Jump to table…  press / to focus')}
        style={{
          width: '100%',
          height: 40,
          paddingLeft: 36,
          paddingRight: value ? 36 : 12,
          borderRadius: 10,
          border: `1px solid ${BORDER}`,
          background: SURFACE,
          color: TEXT,
          fontSize: 14,
          fontWeight: 500,
          outline: 'none',
        }}
      />
      {value && (
        <button
          onClick={() => onChange('')}
          style={{
            position: 'absolute',
            right: 6,
            top: '50%',
            transform: 'translateY(-50%)',
            width: 28,
            height: 28,
            border: 'none',
            background: 'transparent',
            color: MUTED,
            cursor: 'pointer',
            borderRadius: 6,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          aria-label={t('common.clear', 'Clear')}
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* TableTile — compact ~70px                                                   */
/* -------------------------------------------------------------------------- */

function TableTile({ vm, onClick, t }) {
  const conf = STATUS[vm.status] || STATUS.AVAILABLE;
  const urgent = vm.phase === 'paid_unreleased';

  const lines = (() => {
    if (vm.status === 'OCCUPIED') {
      const top = fmtMoney(vm.total || 0);
      let bottom;
      if (vm.phase === 'paid_unreleased') {
        bottom = `${t('orders.paid', 'PAID')} · ${fmtMin(vm.idleMin ?? 0)}`;
      } else {
        bottom = fmtMin(vm.seatedMin ?? 0);
      }
      return { top, bottom };
    }
    if (vm.status === 'RESERVED') {
      const r = vm.reservation;
      return {
        top: r?.name || t('orders.reservedShort', 'Reserved'),
        bottom: r?.etaMin != null
          ? t('orders.etaIn', 'ETA {{m}}m', { m: r.etaMin })
          : '',
      };
    }
    if (vm.status === 'AVAILABLE') {
      return {
        top: t('orders.tile.seatsN', '{{n}} seats', { n: vm.capacity }),
        bottom: '',
      };
    }
    return { top: t(`orders.status.${vm.status}`, conf.label), bottom: '' };
  })();

  return (
    <button
      type="button"
      onClick={onClick}
      data-paid-unreleased={urgent ? '1' : undefined}
      className={urgent ? 'orders-tile-pulse' : undefined}
      style={{
        position: 'relative',
        height: TILE_HEIGHT,
        padding: '6px 8px',
        background: conf.bg,
        border: `1px solid ${urgent ? '#dc2626' : conf.border}`,
        borderRadius: 10,
        textAlign: 'left',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        overflow: 'hidden',
        transition: 'transform 80ms ease, border-color 120ms ease',
      }}
      onMouseDown={(e) => (e.currentTarget.style.transform = 'scale(0.97)')}
      onMouseUp={(e) => (e.currentTarget.style.transform = 'scale(1)')}
      onMouseLeave={(e) => (e.currentTarget.style.transform = 'scale(1)')}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <span
          style={{
            fontSize: 13,
            fontWeight: 700,
            color: conf.text,
            ...MONEY,
            lineHeight: 1.15,
            // The tile is compact — clip long names rather than blow the
            // header out, the full label is always available in the drawer.
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            maxWidth: 'calc(100% - 14px)',
          }}
          title={vm.label}
        >
          {vm.label}
        </span>
        <StatusDot status={vm.status} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <span
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: conf.text,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            ...MONEY,
          }}
        >
          {lines.top}
        </span>
        {lines.bottom && (
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: urgent ? '#b91c1c' : conf.text,
              opacity: urgent ? 1 : 0.7,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              ...MONEY,
            }}
          >
            {lines.bottom}
          </span>
        )}
      </div>
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* SectionGroup — sticky header + tile grid                                    */
/* -------------------------------------------------------------------------- */

function SectionGroup({ section, color, onTileClick, t }) {
  const occupied = section.items.filter((vm) => vm.status === 'OCCUPIED').length;
  const total = section.items.length;
  const releaseCount = section.items.filter((vm) => vm.phase === 'paid_unreleased').length;
  const sectionRevenue = section.items.reduce(
    (sum, vm) => sum + (vm.total || 0),
    0
  );

  return (
    <section style={{ marginBottom: SECTION_GAP }}>
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 5,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '8px 12px',
          background: PAGE_BG,
          borderBottom: `1px solid ${BORDER}`,
          marginBottom: 8,
        }}
      >
        <span
          style={{
            width: 10,
            height: 10,
            borderRadius: 999,
            background: color,
            flexShrink: 0,
          }}
        />
        <h2
          style={{
            margin: 0,
            fontSize: 13,
            fontWeight: 800,
            color: TEXT,
            letterSpacing: 0.5,
            textTransform: 'uppercase',
            whiteSpace: 'nowrap',
          }}
        >
          {section.name}
        </h2>
        <span style={{ fontSize: 11, color: MUTED, fontWeight: 600 }}>
          {t('orders.floorN', 'Floor {{n}}', { n: section.floor })}
        </span>
        <span style={{ flex: 1 }} />
        {releaseCount > 0 && (
          <span
            className="orders-pulse-text"
            style={{
              padding: '2px 8px',
              borderRadius: 999,
              background: '#fee2e2',
              border: '1px solid #fecaca',
              color: '#b91c1c',
              fontSize: 11,
              fontWeight: 800,
              ...MONEY,
            }}
          >
            {releaseCount} {t('orders.releaseShort', 'release')}
          </span>
        )}
        <span style={{ fontSize: 11, color: MUTED, fontWeight: 600, ...MONEY }}>
          {occupied}/{total}
        </span>
        <span style={{ fontSize: 11, color: TEXT, fontWeight: 700, ...MONEY }}>
          {fmtMoney(sectionRevenue)}
        </span>
      </header>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(auto-fill, minmax(${TILE_MIN_WIDTH}px, 1fr))`,
          gap: TILE_GAP,
        }}
      >
        {section.items.map((vm) => (
          <TableTile key={vm.id} vm={vm} t={t} onClick={() => onTileClick(vm)} />
        ))}
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* ConfirmDialog                                                              */
/* -------------------------------------------------------------------------- */

function ConfirmDialog({ open, onClose, onConfirm, title, message, danger, busy, confirmLabel, cancelLabel, t }) {
  if (!open) return null;
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(15, 23, 42, 0.45)',
        zIndex: 70,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: SURFACE,
          borderRadius: 14,
          width: 'min(92vw, 420px)',
          padding: 22,
          boxShadow: '0 30px 80px rgba(0,0,0,.25)',
        }}
      >
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 12 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: danger ? '#fee2e2' : '#e0f2fe',
              color: danger ? '#dc2626' : '#0369a1',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <AlertTriangle size={18} />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: TEXT }}>{title}</h3>
            <p style={{ margin: '6px 0 0', fontSize: 13, color: MUTED, lineHeight: 1.45 }}>{message}</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 8 }}>
          <button
            onClick={onClose}
            disabled={busy}
            style={btnStyle('ghost')}
          >
            {cancelLabel || t('common.cancel', 'Cancel')}
          </button>
          <button
            onClick={onConfirm}
            disabled={busy}
            style={btnStyle(danger ? 'danger' : 'primary')}
          >
            {busy ? <Loader2 size={14} className="orders-spin" /> : null}
            {confirmLabel || t('common.confirm', 'Confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* ErrorModal — structured error with retry                                    */
/* -------------------------------------------------------------------------- */

function ErrorModal({ error, onClose, onRetry, retrying, t }) {
  if (!error) return null;
  const handleCopy = () => {
    try {
      navigator.clipboard.writeText(JSON.stringify(error, null, 2));
    } catch {
      /* clipboard not available */
    }
  };
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(15, 23, 42, 0.45)',
        zIndex: 80,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: SURFACE,
          borderRadius: 14,
          width: 'min(92vw, 460px)',
          padding: 22,
          boxShadow: '0 30px 80px rgba(0,0,0,.25)',
        }}
      >
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 12 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: '#fee2e2',
              color: '#dc2626',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <AlertTriangle size={18} />
          </div>
          <div style={{ minWidth: 0 }}>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: TEXT }}>
              {t(`orders.error.kind.${error.kind}`, error.kind.charAt(0).toUpperCase() + error.kind.slice(1))}
            </h3>
            <p style={{ margin: '6px 0 0', fontSize: 13, color: TEXT, lineHeight: 1.45, wordBreak: 'break-word' }}>
              {error.message}
            </p>
          </div>
        </div>

        <dl
          style={{
            margin: '12px 0 0',
            padding: '10px 12px',
            background: PAGE_BG,
            border: `1px solid ${BORDER}`,
            borderRadius: 10,
            fontSize: 12,
            color: MUTED,
            display: 'grid',
            gridTemplateColumns: 'auto 1fr',
            rowGap: 4,
            columnGap: 12,
            ...MONEY,
          }}
        >
          <dt>{t('orders.error.opLabel', 'Operation')}</dt>
          <dd style={{ margin: 0, color: TEXT, fontWeight: 600 }}>{error.op}</dd>
          <dt>{t('orders.error.codeLabel', 'HTTP code')}</dt>
          <dd style={{ margin: 0, color: TEXT, fontWeight: 600 }}>{error.http || '—'}</dd>
          <dt>{t('orders.error.timeLabel', 'Time')}</dt>
          <dd style={{ margin: 0, color: TEXT, fontWeight: 600 }}>{error.timestamp}</dd>
        </dl>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', marginTop: 16, alignItems: 'center' }}>
          <button onClick={handleCopy} style={btnStyle('ghost')}>
            <Copy size={13} /> {t('orders.error.copy', 'Copy details')}
          </button>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={onClose} style={btnStyle('ghost')}>
              {t('common.close', 'Close')}
            </button>
            {error.retryable && (
              <button onClick={onRetry} disabled={retrying} style={btnStyle('primary')}>
                {retrying ? <Loader2 size={14} className="orders-spin" /> : null}
                {t('orders.error.retry', 'Try again')}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Inline payment form                                                        */
/* -------------------------------------------------------------------------- */

const QUICK_TENDERS = [50000, 100000, 200000, 500000];

function InlinePaymentForm({ vm, onCancel, onCharge, processing, t }) {
  const total = vm?.total || 0;
  const [method, setMethod] = useState('CASH');
  const [tendered, setTendered] = useState(0);

  const change = method === 'CASH' ? Math.max(0, tendered - total) : 0;
  const short = method === 'CASH' ? Math.max(0, total - tendered) : 0;
  const canCharge = total > 0 && (method !== 'CASH' || tendered >= total) && !processing;

  return (
    <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <button
        onClick={onCancel}
        style={{
          alignSelf: 'flex-start',
          background: 'transparent',
          border: 'none',
          color: MUTED,
          fontSize: 12,
          fontWeight: 600,
          cursor: 'pointer',
          padding: 0,
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
        }}
      >
        <X size={12} /> {t('orders.payment.back', 'Back to order')}
      </button>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <Row label={t('orders.payment.subtotal', 'Subtotal')} value={fmtMoney(vm.subtotal || 0)} />
        <Row
          label={t('orders.payment.tax', 'Tax {{percent}}%', { percent: Math.round(TAX_RATE * 100) })}
          value={fmtMoney(vm.tax || 0)}
        />
        <Row
          label={t('orders.payment.total', 'Total')}
          value={fmtMoney(total)}
          large
          bold
        />
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr',
          gap: 4,
          background: PAGE_BG,
          border: `1px solid ${BORDER}`,
          borderRadius: 10,
          padding: 4,
        }}
      >
        {[
          { key: 'CASH', label: t('orders.payment.cash', 'Cash'), Icon: Banknote },
          { key: 'CARD', label: t('orders.payment.card', 'Card'), Icon: CreditCard },
          { key: 'MOBILE', label: t('orders.payment.mobile', 'Mobile'), Icon: Wallet },
        ].map(({ key, label, Icon }) => {
          const active = method === key;
          return (
            <button
              key={key}
              onClick={() => setMethod(key)}
              style={{
                height: 36,
                borderRadius: 8,
                border: 'none',
                background: active ? SURFACE : 'transparent',
                color: active ? TEXT : MUTED,
                fontWeight: 700,
                fontSize: 13,
                cursor: 'pointer',
                boxShadow: active ? '0 1px 2px rgba(0,0,0,.08)' : 'none',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6,
              }}
            >
              <Icon size={14} /> {label}
            </button>
          );
        })}
      </div>

      {method === 'CASH' && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6 }}>
            {QUICK_TENDERS.map((amt) => (
              <button
                key={amt}
                onClick={() => setTendered(amt)}
                style={btnStyle(tendered === amt ? 'primary' : 'outline')}
              >
                <span style={MONEY}>{Math.round(amt / 1000)}k</span>
              </button>
            ))}
            <button onClick={() => setTendered(total)} style={btnStyle(tendered === total ? 'primary' : 'outline')}>
              {t('orders.payment.exact', 'Exact')}
            </button>
          </div>
          <input
            type="number"
            min={0}
            value={tendered || ''}
            onChange={(e) => setTendered(Number(e.target.value) || 0)}
            placeholder={t('orders.payment.cashReceived', 'Cash received')}
            style={{
              width: '100%',
              height: 44,
              borderRadius: 10,
              border: `1px solid ${BORDER}`,
              background: PAGE_BG,
              color: TEXT,
              padding: '0 12px',
              fontSize: 16,
              fontWeight: 600,
              outline: 'none',
              ...MONEY,
            }}
          />
          {short > 0 ? (
            <Row label={t('orders.payment.short', 'Short by')} value={fmtMoney(short)} muted />
          ) : (
            <Row label={t('orders.payment.change', 'Change')} value={fmtMoney(change)} muted={change === 0} />
          )}
        </>
      )}

      <button
        onClick={() => onCharge({ method, tendered, total })}
        disabled={!canCharge}
        style={{ ...btnStyle('success'), height: 52, fontSize: 15 }}
      >
        {processing ? (
          <Loader2 size={16} className="orders-spin" />
        ) : null}
        {t('orders.payment.charge', 'Charge {{amount}}', { amount: fmtMoney(total) })}
      </button>
    </div>
  );
}

function Row({ label, value, muted, bold, large }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        fontSize: large ? 18 : 13,
        fontWeight: bold ? 800 : 500,
        color: muted ? MUTED : TEXT,
      }}
    >
      <span>{label}</span>
      <span style={MONEY}>{value}</span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* TableDetailDrawer                                                          */
/* -------------------------------------------------------------------------- */

function ProductPicker({ products, loading, busyAction, onPick, onClose, t }) {
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return products;
    return products.filter((p) => {
      const hay = `${p.name || ''} ${p.categoryName || ''}`.toLowerCase();
      return hay.includes(q);
    });
  }, [products, query]);

  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: SURFACE,
        zIndex: 5,
        display: 'flex',
        flexDirection: 'column',
        animation: 'orders-fade-in 160ms ease',
      }}
    >
      <header
        style={{
          padding: 12,
          borderBottom: `1px solid ${BORDER}`,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <div style={{ position: 'relative', flex: 1 }}>
          <Search
            size={14}
            style={{
              position: 'absolute',
              left: 10,
              top: '50%',
              transform: 'translateY(-50%)',
              color: MUTED,
              pointerEvents: 'none',
            }}
          />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('orders.picker.searchPlaceholder', 'Search products…')}
            style={{
              width: '100%',
              height: 36,
              paddingLeft: 32,
              paddingRight: 10,
              borderRadius: 8,
              border: `1px solid ${BORDER}`,
              background: PAGE_BG,
              color: TEXT,
              fontSize: 14,
              outline: 'none',
            }}
          />
        </div>
        <button
          onClick={onClose}
          aria-label={t('common.close', 'Close')}
          style={{ ...iconBtnStyle(), width: 36, height: 36 }}
        >
          <X size={14} />
        </button>
      </header>

      <div style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
        {loading ? (
          <div style={{ padding: 40, textAlign: 'center', color: MUTED }}>
            <Loader2 size={18} className="orders-spin" />
            <div style={{ marginTop: 6, fontSize: 12 }}>{t('orders.loading', 'Loading…')}</div>
          </div>
        ) : filtered.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: MUTED, fontSize: 13 }}>
            {query
              ? t('orders.picker.noMatch', 'No products match “{{q}}”', { q: query })
              : t('orders.picker.noProducts', 'No products available')}
          </div>
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
            {filtered.map((p) => {
              const busy = busyAction === `ADD_ITEM_${p.id}`;
              return (
                <li key={p.id}>
                  <button
                    onClick={() => onPick(p)}
                    disabled={busy}
                    style={{
                      display: 'flex',
                      width: '100%',
                      gap: 10,
                      alignItems: 'center',
                      padding: '10px 12px',
                      background: PAGE_BG,
                      border: `1px solid ${BORDER}`,
                      borderRadius: 8,
                      cursor: 'pointer',
                      textAlign: 'left',
                      opacity: busy ? 0.6 : 1,
                    }}
                  >
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: TEXT, lineHeight: 1.3 }}>
                        {p.name}
                      </div>
                      {p.categoryName && (
                        <div style={{ fontSize: 11, color: MUTED, marginTop: 2 }}>
                          {p.categoryName}
                        </div>
                      )}
                    </div>
                    <span style={{ fontSize: 13, fontWeight: 700, color: TEXT, ...MONEY, whiteSpace: 'nowrap' }}>
                      {fmtMoney(p.price || 0)}
                    </span>
                    {busy && <Loader2 size={14} className="orders-spin" />}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

function TableDetailDrawer({
  vm,
  open,
  onClose,
  onAction,
  busyAction,
  busyItemId,
  paymentMode,
  setPaymentMode,
  pickerOpen,
  setPickerOpen,
  products,
  productsLoading,
  flashItemId,
  t,
}) {
  if (!vm) return null;
  const conf = STATUS[vm.status] || STATUS.AVAILABLE;

  return (
    <>
      <div
        onClick={onClose}
        style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(15, 23, 42, 0.35)',
          opacity: open ? 1 : 0,
          pointerEvents: open ? 'auto' : 'none',
          transition: 'opacity 200ms ease',
          zIndex: 60,
        }}
      />
      <aside
        style={{
          position: 'fixed',
          top: 0,
          right: 0,
          height: '100vh',
          width: DRAWER_WIDTH,
          maxWidth: '95vw',
          background: SURFACE,
          borderLeft: `1px solid ${BORDER}`,
          boxShadow: '-12px 0 40px rgba(0,0,0,.08)',
          transform: open ? 'translateX(0)' : 'translateX(110%)',
          transition: 'transform 200ms ease',
          zIndex: 65,
          display: 'flex',
          flexDirection: 'column',
        }}
        aria-hidden={!open}
      >
        {/* Header */}
        <header
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            padding: 16,
            borderBottom: `1px solid ${BORDER}`,
          }}
        >
          <div
            style={{
              minWidth: 56,
              maxWidth: 88,
              height: 56,
              flexShrink: 0,
              borderRadius: 12,
              padding: '0 8px',
              background: conf.bg,
              border: `1px solid ${conf.border}`,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              color: conf.text,
              overflow: 'hidden',
            }}
            title={vm.label}
          >
            <span
              style={{
                fontSize: 15,
                fontWeight: 800,
                lineHeight: 1.1,
                textAlign: 'center',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: '100%',
              }}
            >
              {vm.label}
            </span>
            <span style={{ fontSize: 9, fontWeight: 700, marginTop: 2, opacity: 0.75, textTransform: 'uppercase' }}>
              {t('orders.tableShort', 'Tbl')}
            </span>
          </div>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <h2 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: TEXT, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                {vm.label}
              </h2>
            </div>
            <div style={{ fontSize: 12, color: MUTED, marginBottom: 6 }}>
              {vm.section} · {t('orders.capN', 'cap. {{n}}', { n: vm.capacity })}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '2px 8px',
                  borderRadius: 999,
                  background: conf.bg,
                  border: `1px solid ${conf.border}`,
                  color: conf.text,
                  fontSize: 11,
                  fontWeight: 700,
                }}
              >
                <StatusDot status={vm.status} size={6} />
                {t(`orders.status.${vm.status}`, conf.label)}
              </span>
              <PhasePill phase={vm.phase} />
              {vm.guests != null && (
                <span style={{ fontSize: 11, color: MUTED, fontWeight: 600 }}>
                  {vm.guests} {t('orders.guests', 'guests')}
                </span>
              )}
              {vm.seatedMin != null && (
                <span style={{ fontSize: 11, color: MUTED, fontWeight: 600, ...MONEY }}>
                  · {fmtMin(vm.seatedMin)}
                </span>
              )}
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label={t('common.close', 'Close')}
            style={{
              width: 32,
              height: 32,
              border: 'none',
              background: 'transparent',
              color: MUTED,
              borderRadius: 6,
              cursor: 'pointer',
            }}
          >
            <X size={18} />
          </button>
        </header>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {paymentMode ? (
            <InlinePaymentForm
              vm={vm}
              onCancel={() => setPaymentMode(false)}
              onCharge={(data) => onAction('PAY', data)}
              processing={busyAction === 'PAY'}
              t={t}
            />
          ) : (
            <DrawerBody
              vm={vm}
              flashItemId={flashItemId}
              busyItemId={busyItemId}
              onItemAction={onAction}
              onOpenAddItem={() => setPickerOpen(true)}
              t={t}
            />
          )}
        </div>

        {/* Action bar */}
        {!paymentMode && !pickerOpen && (
          <DrawerActionBar
            vm={vm}
            onAction={onAction}
            busyAction={busyAction}
            onPay={() => setPaymentMode(true)}
            t={t}
          />
        )}

        {/* Product picker overlay (in-drawer, slides over body) */}
        {pickerOpen && (
          <ProductPicker
            products={products}
            loading={productsLoading}
            busyAction={busyAction}
            onPick={(product) => onAction('ADD_ITEM', { product })}
            onClose={() => setPickerOpen(false)}
            t={t}
          />
        )}
      </aside>
    </>
  );
}

function DrawerBody({ vm, flashItemId, busyItemId, onItemAction, onOpenAddItem, t }) {
  if (vm.status === 'AVAILABLE') {
    return (
      <div style={emptyBodyStyle()}>
        <div style={{ fontSize: 32, color: STATUS.AVAILABLE.dot, marginBottom: 6 }}>•</div>
        <div style={{ fontSize: 14, fontWeight: 700, color: TEXT }}>
          {t('orders.empty.openReady', 'Open and ready')}
        </div>
        <div style={{ fontSize: 12, color: MUTED, marginTop: 4, maxWidth: 280, lineHeight: 1.45 }}>
          {t('orders.empty.openReadyHint', 'Use the Seat guests action below to start a new dine-in order on this table.')}
        </div>
      </div>
    );
  }
  if (vm.status === 'RESERVED') {
    const r = vm.reservation || {};
    return (
      <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <Field label={t('orders.field.name', 'Name')} value={r.name || '—'} />
        <Field
          label={t('orders.field.eta', 'ETA')}
          value={r.etaMin != null ? `${r.etaMin}m` : '—'}
        />
        <Field
          label={t('orders.field.guests', 'Guests')}
          value={r.guests != null ? String(r.guests) : '—'}
        />
      </div>
    );
  }
  if (vm.status === 'CLEANING' || vm.status === 'OUT_OF_SERVICE') {
    return (
      <div style={emptyBodyStyle()}>
        <div style={{ fontSize: 14, fontWeight: 700, color: TEXT }}>
          {t(`orders.status.${vm.status}`, STATUS[vm.status]?.label)}
        </div>
        <div style={{ fontSize: 12, color: MUTED, marginTop: 6, maxWidth: 280, lineHeight: 1.45 }}>
          {vm.status === 'CLEANING'
            ? t('orders.empty.cleaningHint', 'Mark the table available once it is ready for guests.')
            : t('orders.empty.outHint', 'Mark the table available to bring it back into service.')}
        </div>
      </div>
    );
  }

  // OCCUPIED
  return (
    <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
          fontSize: 12,
          color: MUTED,
        }}
      >
        {vm.orderNum != null && (
          <span style={{ fontWeight: 700, color: TEXT, ...MONEY }}>#{vm.orderNum}</span>
        )}
        {vm.waiter && (vm.waiter.firstName || vm.waiter.lastName) && (
          <span>
            {t('orders.field.waiter', 'Waiter')}: {vm.waiter.firstName} {vm.waiter.lastName}
          </span>
        )}
      </div>

      {vm.items && vm.items.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {vm.items.map((it) => {
            const flash = flashItemId === it.id;
            const busy = busyItemId === it.id;
            return (
              <div
                key={it.id}
                style={{
                  padding: 10,
                  background: flash ? '#fef9c3' : PAGE_BG,
                  border: `1px solid ${flash ? '#facc15' : BORDER}`,
                  borderRadius: 8,
                  transition: 'background 400ms ease, border-color 400ms ease',
                  opacity: busy ? 0.65 : 1,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'flex-start' }}>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: TEXT, lineHeight: 1.3 }}>
                      {it.name}
                      {it.variant ? <span style={{ color: MUTED }}> · {it.variant}</span> : null}
                    </div>
                    {it.note && (
                      <div style={{ fontSize: 11, color: '#9a3412', fontStyle: 'italic', marginTop: 2 }}>
                        {it.note}
                      </div>
                    )}
                  </div>
                  <span style={{ fontSize: 13, fontWeight: 700, color: TEXT, ...MONEY, whiteSpace: 'nowrap' }}>
                    {fmtMoney(it.lineTotal)}
                  </span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <button
                    onClick={() => onItemAction('UPDATE_QTY', { itemId: it.id, qty: Math.max(0, (it.qty || 1) - 1) })}
                    disabled={busy}
                    title={t('orders.itemDecrease', 'Decrease')}
                    style={iconBtnStyle()}
                  >
                    <Minus size={13} />
                  </button>
                  <span
                    style={{
                      minWidth: 36,
                      textAlign: 'center',
                      fontSize: 13,
                      fontWeight: 800,
                      color: TEXT,
                      ...MONEY,
                    }}
                  >
                    {it.qty}
                  </span>
                  <button
                    onClick={() => onItemAction('UPDATE_QTY', { itemId: it.id, qty: (it.qty || 1) + 1 })}
                    disabled={busy}
                    title={t('orders.itemIncrease', 'Increase')}
                    style={iconBtnStyle()}
                  >
                    <Plus size={13} />
                  </button>
                  <span style={{ flex: 1 }} />
                  <button
                    onClick={() => onItemAction('REMOVE_ITEM', { itemId: it.id })}
                    disabled={busy}
                    title={t('orders.itemRemove', 'Remove')}
                    style={{ ...iconBtnStyle(), color: '#b91c1c' }}
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            );
          })}
          <button
            onClick={onOpenAddItem}
            style={{
              ...btnStyle('outline'),
              justifyContent: 'flex-start',
              fontWeight: 600,
              borderStyle: 'dashed',
              color: MUTED,
            }}
          >
            <Plus size={14} />
            {t('orders.action.addItem', 'Add item')}
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{ ...emptyBodyStyle(), padding: 12, minHeight: 0 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: MUTED }}>
              {t('orders.noItems', 'No items yet')}
            </div>
          </div>
          <button
            onClick={onOpenAddItem}
            style={{
              ...btnStyle('outline'),
              justifyContent: 'center',
              fontWeight: 600,
              borderStyle: 'dashed',
            }}
          >
            <Plus size={14} />
            {t('orders.action.addItem', 'Add item')}
          </button>
        </div>
      )}

      <div
        style={{
          padding: 12,
          background: PAGE_BG,
          border: `1px solid ${BORDER}`,
          borderRadius: 10,
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
        }}
      >
        <Row label={t('orders.payment.subtotal', 'Subtotal')} value={fmtMoney(vm.subtotal || 0)} />
        {vm.tax > 0 && (
          <Row label={t('orders.payment.tax', 'Tax {{percent}}%', { percent: Math.round(TAX_RATE * 100) })} value={fmtMoney(vm.tax)} />
        )}
        <Row label={t('orders.payment.total', 'Total')} value={fmtMoney(vm.total || 0)} bold large />
      </div>
    </div>
  );
}

function Field({ label, value }) {
  return (
    <div style={{ padding: '10px 12px', background: PAGE_BG, border: `1px solid ${BORDER}`, borderRadius: 10 }}>
      <div style={{ fontSize: 11, color: MUTED, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 2 }}>{label}</div>
      <div style={{ fontSize: 14, fontWeight: 600, color: TEXT }}>{value}</div>
    </div>
  );
}

function emptyBodyStyle() {
  return {
    padding: 32,
    textAlign: 'center',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 240,
  };
}

function DrawerActionBar({ vm, onAction, busyAction, onPay, t }) {
  const wrap = (children) => (
    <footer
      style={{
        padding: 12,
        borderTop: `1px solid ${BORDER}`,
        background: SURFACE,
        display: 'flex',
        gap: 8,
        flexWrap: 'wrap',
      }}
    >
      {children}
    </footer>
  );

  if (vm.status === 'AVAILABLE') {
    return wrap(
      <>
        <button
          onClick={() => onAction('SEAT')}
          disabled={!!busyAction}
          style={{ ...btnStyle('primary'), flex: 1 }}
        >
          {busyAction === 'SEAT' ? <Loader2 size={14} className="orders-spin" /> : null}
          {t('orders.action.seat', 'Seat guests')}
        </button>
      </>
    );
  }

  if (vm.phase === 'paid_unreleased') {
    return wrap(
      <button
        onClick={() => onAction('RELEASE')}
        disabled={!!busyAction}
        style={{ ...btnStyle('danger'), flex: 1, height: 52, fontSize: 15 }}
      >
        {busyAction === 'RELEASE' ? <Loader2 size={16} className="orders-spin" /> : <DoorIcon />}
        {t('orders.action.release', 'Release table')}
      </button>
    );
  }

  if (vm.status === 'OCCUPIED') {
    return wrap(
      <>
        <button
          onClick={() => onAction('GO_POS')}
          disabled={!!busyAction}
          style={btnStyle('outline')}
        >
          {t('orders.action.editOrder', 'Edit order')}
          <ChevronRight size={14} />
        </button>
        <button
          onClick={() => onAction('CHANGE_TABLE')}
          disabled={!!busyAction}
          style={btnStyle('outline')}
        >
          <ArrowRightLeft size={14} />
          {t('orders.action.changeTable', 'Change table')}
        </button>
        <button
          onClick={() => onAction('CANCEL_ORDER')}
          disabled={!!busyAction}
          style={btnStyle('ghostDanger')}
        >
          <Trash2 size={14} />
          {t('orders.action.cancelOrder', 'Cancel order')}
        </button>
        <button
          onClick={onPay}
          disabled={!!busyAction || (vm.total || 0) <= 0}
          style={{ ...btnStyle('success'), flex: 1 }}
        >
          {t('orders.action.charge', 'Charge {{amount}}', { amount: fmtMoney(vm.total || 0) })}
        </button>
      </>
    );
  }

  if (vm.status === 'RESERVED' || vm.status === 'CLEANING' || vm.status === 'OUT_OF_SERVICE') {
    return wrap(
      <button
        onClick={() => onAction('MARK_AVAILABLE')}
        disabled={!!busyAction}
        style={{ ...btnStyle('primary'), flex: 1 }}
      >
        {busyAction === 'MARK_AVAILABLE' ? <Loader2 size={14} className="orders-spin" /> : null}
        {t('orders.action.markAvailable', 'Mark available')}
      </button>
    );
  }

  return null;
}

function DoorIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M13 4h3a2 2 0 0 1 2 2v14" />
      <path d="M2 20h20" />
      <path d="M14 12v.01" />
      <path d="M10 4H6a2 2 0 0 0-2 2v14" />
    </svg>
  );
}

/* -------------------------------------------------------------------------- */
/* Button style helper                                                        */
/* -------------------------------------------------------------------------- */

function iconBtnStyle() {
  return {
    width: 30,
    height: 30,
    borderRadius: 6,
    border: `1px solid ${BORDER}`,
    background: SURFACE,
    color: TEXT,
    cursor: 'pointer',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'background 120ms ease',
  };
}

function btnStyle(variant) {
  const base = {
    height: 40,
    padding: '0 14px',
    fontSize: 13,
    fontWeight: 700,
    borderRadius: 10,
    border: '1px solid transparent',
    cursor: 'pointer',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    transition: 'background 120ms ease, transform 80ms ease',
    whiteSpace: 'nowrap',
  };
  switch (variant) {
    case 'primary':
      return { ...base, background: '#2563eb', color: '#fff', borderColor: '#2563eb' };
    case 'success':
      return { ...base, background: '#16a34a', color: '#fff', borderColor: '#16a34a' };
    case 'danger':
      return { ...base, background: '#dc2626', color: '#fff', borderColor: '#dc2626' };
    case 'outline':
      return { ...base, background: SURFACE, color: TEXT, borderColor: BORDER };
    case 'ghost':
      return { ...base, background: 'transparent', color: TEXT, borderColor: 'transparent' };
    case 'ghostDanger':
      return { ...base, background: 'transparent', color: '#b91c1c', borderColor: 'transparent' };
    default:
      return { ...base, background: SURFACE, color: TEXT, borderColor: BORDER };
  }
}

/* -------------------------------------------------------------------------- */
/* Inline keyframes / global styles for this page                              */
/* -------------------------------------------------------------------------- */

const ORDERS_GLOBAL_STYLES = `
@keyframes orders-pulse-ring {
  0% { box-shadow: 0 0 0 0 rgba(220,38,38,0.55); }
  70% { box-shadow: 0 0 0 8px rgba(220,38,38,0); }
  100% { box-shadow: 0 0 0 0 rgba(220,38,38,0); }
}
@keyframes orders-pulse-text {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
@keyframes orders-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes orders-fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}
.orders-tile-pulse {
  animation: orders-pulse-ring 1.6s infinite;
}
.orders-pulse-text {
  animation: orders-pulse-text 1.6s ease-in-out infinite;
}
.orders-spin {
  animation: orders-spin 0.9s linear infinite;
}
`;

/* -------------------------------------------------------------------------- */
/* Main page                                                                  */
/* -------------------------------------------------------------------------- */

export default function Orders() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const initialRestaurantId = useCurrentRestaurantId();

  const [restaurants, setRestaurants] = useState([]);
  const [restaurantId, setRestaurantId] = useState(initialRestaurantId);
  const [tables, setTables] = useState([]);
  const [ordersByTable, setOrdersByTable] = useState({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search, setSearch] = useState('');

  const [selectedId, setSelectedId] = useState(null);
  const [paymentMode, setPaymentMode] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [busyAction, setBusyAction] = useState(null);
  const [busyItemId, setBusyItemId] = useState(null);
  const [flashItemId, _setFlashItemId] = useState(null);

  const [confirm, setConfirm] = useState(null); // { title, message, danger, onConfirm, busy }
  const [errorState, setErrorState] = useState(null); // { error, retry }
  const [retrying, setRetrying] = useState(false);

  // Lazy product cache for the in-drawer Add item picker
  const [productsByRestaurant, setProductsByRestaurant] = useState({});
  const [productsLoading, setProductsLoading] = useState(false);

  const tablesRef = useRef(tables);
  const ordersRef = useRef(ordersByTable);
  tablesRef.current = tables;
  ordersRef.current = ordersByTable;

  // Sync resolved restaurant id when the helper resolves later (auth, /restaurants/active fallback)
  useEffect(() => {
    if (!restaurantId && initialRestaurantId) {
      setRestaurantId(initialRestaurantId);
    }
  }, [initialRestaurantId, restaurantId]);

  // Load restaurants for the optional switcher
  useEffect(() => {
    let cancel = false;
    restaurantAPI.getAll().then((res) => {
      if (cancel) return;
      const list = res.data?.data?.content || res.data?.data || res.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
    }).catch(() => {});
    return () => { cancel = true; };
  }, []);

  /* Loader -------------------------------------------------------------- */

  const loadAll = useCallback(async (mode = 'background') => {
    if (!restaurantId) return;
    if (mode === 'initial') setLoading(true);
    setRefreshing(true);
    try {
      const [tRes, oRes] = await Promise.all([
        tablesAPI.getAll(restaurantId),
        posAPI.getOpenDineInOrders(restaurantId),
      ]);
      const tablesData = tRes.data?.data?.content || tRes.data?.data || tRes.data || [];
      const sorted = (Array.isArray(tablesData) ? tablesData : []).sort((a, b) => {
        const na = parseInt(a.tableNumber, 10) || 0;
        const nb = parseInt(b.tableNumber, 10) || 0;
        return na - nb;
      });
      setTables(sorted);

      const orders = oRes.data?.data || oRes.data || [];
      const byTable = {};
      if (Array.isArray(orders)) {
        for (const order of orders) {
          let id = null;
          if (order.dineInInfo?.tableIds?.length > 0) id = Number(order.dineInInfo.tableIds[0]);
          else if (order.diningTable?.id) id = Number(order.diningTable.id);
          else if (order.tableIds) id = parseInt(String(order.tableIds).split(',')[0], 10);
          if (id && Number.isFinite(id)) {
            (byTable[id] = byTable[id] || []).push(order);
          }
        }
      }
      setOrdersByTable(byTable);
    } catch (e) {
      // Don't surface the modal for a passive load — that would be too noisy.
      // The error will surface on the next mutation.
      // eslint-disable-next-line no-console
      console.error('[orders] load failed', e);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [restaurantId]);

  useEffect(() => {
    if (restaurantId) loadAll('initial');
  }, [restaurantId, loadAll]);

  /* WebSocket + polling fallback --------------------------------------- */

  const anyModalOpen = paymentMode || pickerOpen || !!confirm || !!errorState;

  useWebSocketNotifications({
    restaurantId: restaurantId || null,
    enabled: !!restaurantId,
    toastEnabled: false,
    soundEnabled: false,
    browserNotificationEnabled: false,
    onOrderEvent: useCallback(() => {
      if (!restaurantId) return;
      if (anyModalOpen) return;
      // Drawer-aware reconcile is just a passive reload — selectors recompute,
      // local state sticks because TableVMs are derived.
      loadAll('background');
    }, [restaurantId, anyModalOpen, loadAll]),
  });

  useEffect(() => {
    if (!restaurantId || anyModalOpen) return;
    const id = setInterval(() => loadAll('background'), POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [restaurantId, anyModalOpen, loadAll]);

  /* Selectors ----------------------------------------------------------- */

  const tableVms = useMemo(() => buildTableVMs(tables, ordersByTable), [tables, ordersByTable]);

  const filteredVms = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return tableVms;
    return tableVms.filter((vm) => {
      const num = String(vm.num).toLowerCase();
      const label = String(vm.label || '').toLowerCase();
      const section = String(vm.section || '').toLowerCase();
      return num.includes(q) || label.includes(q) || section.includes(q);
    });
  }, [tableVms, search]);

  const sections = useMemo(() => groupBySection(filteredVms), [filteredVms]);

  const stats = useMemo(() => {
    let occupied = 0, reserved = 0, paidUnreleased = 0;
    for (const vm of tableVms) {
      if (vm.status === 'OCCUPIED') occupied++;
      else if (vm.status === 'RESERVED') reserved++;
      if (vm.phase === 'paid_unreleased') paidUnreleased++;
    }
    return {
      total: tableVms.length,
      occupied,
      reserved,
      open: tableVms.filter((v) => v.status === 'AVAILABLE').length,
      paidUnreleased,
    };
  }, [tableVms]);

  const selected = useMemo(() => {
    if (!selectedId) return null;
    return tableVms.find((v) => v.id === selectedId) || null;
  }, [tableVms, selectedId]);

  /* Action contracts ---------------------------------------------------- */

  const lastOpRef = useRef(null);
  const newTableIdRef = useRef(null);

  const closeError = () => setErrorState(null);

  const runOp = async (label, opts) => {
    const { op, fn, fallbackMessages, onSuccess, onFailure, successToast, optimistic, revert } = opts;
    setBusyAction(label);
    if (optimistic) optimistic();
    const res = await callApi(op, fn, fallbackMessages);
    setBusyAction(null);
    if (res.ok) {
      if (successToast) {
        toast({ title: successToast, duration: TOAST_DURATION_MS });
      }
      if (onSuccess) onSuccess(res);
      loadAll('background');
      lastOpRef.current = null;
      return res;
    }
    if (revert) revert();
    if (onFailure) onFailure(res);
    lastOpRef.current = { label, opts };
    setErrorState({ error: res.error });
    return res;
  };

  const retryLast = async () => {
    if (!lastOpRef.current) return;
    setRetrying(true);
    setErrorState(null);
    const { label, opts } = lastOpRef.current;
    await runOp(label, opts);
    setRetrying(false);
  };

  // Optimistic helpers
  const patchTable = (id, patch) => {
    setTables((prev) => prev.map((t) => (t.id === id ? { ...t, ...patch } : t)));
  };
  const patchOrder = (orderId, patch) => {
    setOrdersByTable((prev) => {
      const next = { ...prev };
      for (const k of Object.keys(next)) {
        next[k] = next[k].map((o) => (o.id === orderId ? { ...o, ...patch } : o));
      }
      return next;
    });
  };
  const removeOrder = (orderId) => {
    setOrdersByTable((prev) => {
      const next = {};
      for (const k of Object.keys(prev)) {
        const filtered = prev[k].filter((o) => o.id !== orderId);
        if (filtered.length) next[k] = filtered;
      }
      return next;
    });
  };

  const recomputeTotals = (order) => {
    const subtotal = (order.items || []).reduce(
      (s, it) => s + (it.totalPrice ?? (it.unitPrice || 0) * (it.quantity || 0)),
      0
    );
    const tax = subtotal * TAX_RATE;
    return { ...order, subtotal, tax, total: subtotal + tax };
  };
  const patchOrderItem = (orderId, itemId, patch) => {
    setOrdersByTable((prev) => {
      const next = {};
      for (const k of Object.keys(prev)) {
        next[k] = prev[k].map((o) => {
          if (o.id !== orderId) return o;
          const items = (o.items || []).map((it) =>
            it.id === itemId ? { ...it, ...patch } : it
          );
          return recomputeTotals({ ...o, items });
        });
      }
      return next;
    });
  };
  const patchOrderRemoveItem = (orderId, itemId) => {
    setOrdersByTable((prev) => {
      const next = {};
      for (const k of Object.keys(prev)) {
        next[k] = prev[k].map((o) => {
          if (o.id !== orderId) return o;
          const items = (o.items || []).filter((it) => it.id !== itemId);
          return recomputeTotals({ ...o, items });
        });
      }
      return next;
    });
  };
  const patchOrderAddItem = (orderId, product) => {
    setOrdersByTable((prev) => {
      const next = {};
      for (const k of Object.keys(prev)) {
        next[k] = prev[k].map((o) => {
          if (o.id !== orderId) return o;
          const tempId = `tmp_${Date.now()}_${product.id}`;
          const newItem = {
            id: tempId,
            productName: product.name,
            quantity: 1,
            unitPrice: product.price || 0,
            totalPrice: product.price || 0,
          };
          const items = [...(o.items || []), newItem];
          return recomputeTotals({ ...o, items });
        });
      }
      return next;
    });
  };

  // Lazy product loader for the in-drawer picker. Cached per restaurant so
  // repeated openings don't re-hit the API. Triggered on picker open.
  const ensureProductsLoaded = useCallback(async () => {
    if (!restaurantId) return;
    if (productsByRestaurant[restaurantId]) return;
    setProductsLoading(true);
    try {
      const res = await menuAPI.getProductsByRestaurant(restaurantId);
      const list = res.data?.data || res.data || [];
      setProductsByRestaurant((prev) => ({ ...prev, [restaurantId]: list }));
    } catch (e) {
      // Silent — the empty list and a "No products available" message in
      // the picker is informative enough; mutation paths still surface
      // the ErrorModal via callApi.
      // eslint-disable-next-line no-console
      console.warn('[orders] menu load failed', e);
    } finally {
      setProductsLoading(false);
    }
  }, [restaurantId, productsByRestaurant]);

  useEffect(() => {
    if (pickerOpen) ensureProductsLoaded();
  }, [pickerOpen, ensureProductsLoaded]);

  // Action dispatcher used by the drawer
  const doAction = (action, payload) => {
    if (!selected) return;
    switch (action) {
      case 'SEAT':
        // Send straight to the POS new-order flow; we don't build an in-page
        // order-creation form here.
        try { localStorage.setItem('preselectedTableId', String(selected.id)); } catch (_e) { /* ignore — localStorage may be unavailable */ }
        try { localStorage.setItem('selectedRestaurantId', String(restaurantId)); } catch (_e) { /* ignore — localStorage may be unavailable */ }
        window.open('/admin/pos', '_blank', 'noopener,noreferrer');
        break;

      case 'GO_POS':
        try { localStorage.setItem('preselectedTableId', String(selected.id)); } catch (_e) { /* ignore — localStorage may be unavailable */ }
        try { localStorage.setItem('selectedRestaurantId', String(restaurantId)); } catch (_e) { /* ignore — localStorage may be unavailable */ }
        window.open('/admin/pos', '_blank', 'noopener,noreferrer');
        break;

      case 'PAY': {
        const orderId = selected.orderId;
        if (!orderId) return;
        const total = payload.total ?? selected.total ?? 0;
        runOp('PAY', {
          op: 'processPayment',
          fn: () => posAPI.processPayment(orderId, {
            method: payload.method,
            amount: total,
            amountTendered: payload.method === 'CASH' ? payload.tendered : total,
          }),
          successToast: t('orders.toast.paymentProcessed', 'Payment processed'),
          fallbackMessages: { server: t('orders.errorMsg.payment', 'Payment failed.') },
          onSuccess: () => {
            // Drawer stays open; switch back from payment mode so the user
            // can see the new "Release table" CTA.
            setPaymentMode(false);
            patchOrder(orderId, { paymentStatus: 'PAID', fullyPaid: true });
          },
        });
        break;
      }

      case 'RELEASE': {
        const orderId = selected.orderId;
        if (!orderId) return;
        setConfirm({
          title: t('orders.confirm.releaseTitle', 'Release table?'),
          message: t('orders.confirm.releaseMsg', 'This closes the order and frees the table for new guests.'),
          danger: false,
          confirmLabel: t('orders.action.release', 'Release table'),
          onConfirm: async () => {
            setConfirm((c) => (c ? { ...c, busy: true } : c));
            await runOp('RELEASE', {
              op: 'closeOrder',
              fn: () => posAPI.closeOrder(orderId),
              successToast: t('orders.toast.tableReleased', 'Table released'),
              fallbackMessages: { server: t('orders.errorMsg.release', 'Release failed.') },
              optimistic: () => {
                patchTable(selected.id, { status: 'AVAILABLE' });
                removeOrder(orderId);
              },
              revert: () => loadAll('background'),
              onSuccess: () => {
                setConfirm(null);
                setSelectedId(null);
              },
              onFailure: () => setConfirm(null),
            });
          },
        });
        break;
      }

      case 'CANCEL_ORDER': {
        const orderId = selected.orderId;
        if (!orderId) return;
        setConfirm({
          title: t('orders.confirm.cancelTitle', 'Cancel this order?'),
          message: t('orders.confirm.cancelMsg', 'The order will be voided and the table freed. This cannot be undone.'),
          danger: true,
          confirmLabel: t('orders.action.cancelOrder', 'Cancel order'),
          onConfirm: async () => {
            setConfirm((c) => (c ? { ...c, busy: true } : c));
            await runOp('CANCEL_ORDER', {
              op: 'updateStatus',
              fn: () => orderAPI.updateStatus(orderId, 'CANCELLED', 'Cancelled from /orders'),
              successToast: t('orders.toast.orderCancelled', 'Order cancelled'),
              fallbackMessages: { server: t('orders.errorMsg.cancel', 'Cancel failed.') },
              optimistic: () => {
                patchTable(selected.id, { status: 'AVAILABLE' });
                removeOrder(orderId);
              },
              revert: () => loadAll('background'),
              onSuccess: () => {
                setConfirm(null);
                setSelectedId(null);
              },
              onFailure: () => setConfirm(null),
            });
          },
        });
        break;
      }

      case 'CHANGE_TABLE': {
        const orderId = selected.orderId;
        if (!orderId) return;
        const candidates = tablesRef.current.filter(
          (t) => t.id !== selected.id && (t.status === 'AVAILABLE')
        );
        if (candidates.length === 0) {
          toast({
            title: t('orders.toast.noFreeTables', 'No free tables to move to'),
            duration: TOAST_DURATION_MS,
          });
          return;
        }
        setConfirm({
          title: t('orders.confirm.changeTableTitle', 'Move order to another table?'),
          message: (
            <select
              defaultValue=""
              onChange={(e) => {
                const newId = parseInt(e.target.value, 10);
                newTableIdRef.current = Number.isFinite(newId) ? newId : null;
              }}
              style={{
                width: '100%',
                marginTop: 8,
                height: 40,
                padding: '0 10px',
                borderRadius: 8,
                border: `1px solid ${BORDER}`,
                background: SURFACE,
                color: TEXT,
                fontSize: 14,
              }}
            >
              <option value="">{t('orders.confirm.pickTable', 'Pick a free table…')}</option>
              {candidates.map((tt) => {
                // Prefer the human-set name; fall back to the number so
                // tables without a configured name still render.
                const display = tt.tableName || tt.tableNumber;
                return (
                  <option key={tt.id} value={tt.id}>
                    {display}{tt.section ? ` · ${tt.section}` : ''}
                  </option>
                );
              })}
            </select>
          ),
          danger: false,
          confirmLabel: t('orders.action.moveOrder', 'Move order'),
          onConfirm: async () => {
            const newId = newTableIdRef.current;
            if (!newId) {
              toast({
                title: t('orders.toast.pickFirst', 'Pick a table first'),
                duration: TOAST_DURATION_MS,
              });
              return;
            }
            setConfirm((c) => (c ? { ...c, busy: true } : c));
            const newTable = tablesRef.current.find((tt) => tt.id === newId);
            await runOp('CHANGE_TABLE', {
              op: 'changeTable',
              fn: () => posAPI.changeTable(orderId, newId),
              successToast: t('orders.toast.orderMoved', 'Order moved to {{name}}', {
                name: newTable?.tableName || newTable?.tableNumber || '?',
              }),
              fallbackMessages: { server: t('orders.errorMsg.move', 'Move failed.') },
              optimistic: () => {
                patchTable(selected.id, { status: 'AVAILABLE' });
                patchTable(newId, { status: 'OCCUPIED' });
              },
              revert: () => loadAll('background'),
              onSuccess: () => {
                setConfirm(null);
                setSelectedId(newId);
              },
              onFailure: () => setConfirm(null),
            });
          },
        });
        break;
      }

      case 'UPDATE_QTY': {
        const orderId = selected.orderId;
        const { itemId, qty } = payload || {};
        if (!orderId || !itemId) return;
        // 0 → delegate to remove (with confirmation)
        if (qty <= 0) {
          doAction('REMOVE_ITEM', { itemId });
          return;
        }
        const prevOrder = (ordersRef.current[selected.id] || []).find((o) => o.id === orderId);
        const prevItem = prevOrder?.items?.find((i) => i.id === itemId);
        const prevQty = prevItem?.quantity ?? 1;
        const unitPrice = prevItem?.unitPrice ?? 0;
        setBusyItemId(itemId);
        runOp(`UPDATE_QTY_${itemId}`, {
          op: 'updateItemQuantity',
          fn: () => posAPI.updateItemQuantity(orderId, itemId, qty),
          fallbackMessages: { server: t('orders.errorMsg.updateQty', 'Update failed.') },
          optimistic: () =>
            patchOrderItem(orderId, itemId, { quantity: qty, totalPrice: unitPrice * qty }),
          revert: () =>
            patchOrderItem(orderId, itemId, { quantity: prevQty, totalPrice: unitPrice * prevQty }),
          onSuccess: () => setBusyItemId(null),
          onFailure: () => setBusyItemId(null),
        });
        break;
      }

      case 'REMOVE_ITEM': {
        const orderId = selected.orderId;
        const { itemId } = payload || {};
        if (!orderId || !itemId) return;
        const prevOrder = (ordersRef.current[selected.id] || []).find((o) => o.id === orderId);
        const prevItem = prevOrder?.items?.find((i) => i.id === itemId);
        if (!prevItem) return;
        setConfirm({
          title: t('orders.confirm.removeItemTitle', 'Remove item?'),
          message: t('orders.confirm.removeItemMsg', 'This removes “{{name}}” from the order.', {
            name: prevItem.productName || prevItem.name || t('orders.itemFallback', 'this item'),
          }),
          danger: true,
          confirmLabel: t('orders.action.removeItem', 'Remove'),
          onConfirm: async () => {
            setConfirm((c) => (c ? { ...c, busy: true } : c));
            setBusyItemId(itemId);
            await runOp(`REMOVE_ITEM_${itemId}`, {
              op: 'removeItemFromOrder',
              fn: () => posAPI.removeItemFromOrder(orderId, itemId),
              successToast: t('orders.toast.itemRemoved', 'Item removed'),
              fallbackMessages: { server: t('orders.errorMsg.removeItem', 'Remove failed.') },
              optimistic: () => patchOrderRemoveItem(orderId, itemId),
              revert: () => loadAll('background'),
              onSuccess: () => {
                setConfirm(null);
                setBusyItemId(null);
              },
              onFailure: () => {
                setConfirm(null);
                setBusyItemId(null);
              },
            });
          },
        });
        break;
      }

      case 'ADD_ITEM': {
        const orderId = selected.orderId;
        const { product } = payload || {};
        if (!orderId || !product) return;
        runOp(`ADD_ITEM_${product.id}`, {
          op: 'addItemToOrder',
          fn: () => posAPI.addItemToOrder(orderId, {
            productId: product.id,
            quantity: 1,
            specialInstructions: '',
          }),
          successToast: t('orders.toast.itemAdded', '{{name}} added', { name: product.name }),
          fallbackMessages: { server: t('orders.errorMsg.addItem', 'Could not add item.') },
          optimistic: () => patchOrderAddItem(orderId, product),
          revert: () => loadAll('background'),
          onSuccess: () => {
            // Stay in the picker so the cashier can add more in one go.
            // Closing it is one tap away.
          },
        });
        break;
      }

      case 'MARK_AVAILABLE': {
        runOp('MARK_AVAILABLE', {
          op: 'updateTableStatus',
          fn: () => tablesAPI.updateStatus(selected.id, 'AVAILABLE'),
          successToast: t('orders.toast.tableAvailable', 'Table available'),
          fallbackMessages: { server: t('orders.errorMsg.markAvailable', 'Update failed.') },
          optimistic: () => patchTable(selected.id, { status: 'AVAILABLE' }),
          revert: () => loadAll('background'),
          onSuccess: () => setSelectedId(null),
        });
        break;
      }

      default:
        break;
    }
  };

  /* Render -------------------------------------------------------------- */

  const showRestaurantSwitcher = restaurants.length > 1;

  return (
    <>
      <style>{ORDERS_GLOBAL_STYLES}</style>

      <div
        style={{
          height: '100%',
          minHeight: '100vh',
          background: PAGE_BG,
          color: TEXT,
          display: 'flex',
          flexDirection: 'column',
          fontFamily:
            'Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
        }}
      >
        {/* Top bar */}
        <header
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 10,
            background: SURFACE,
            borderBottom: `1px solid ${BORDER}`,
            padding: '10px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <h1 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: TEXT }}>
            {t('orders.title', 'Tables')}
          </h1>

          {stats.paidUnreleased > 0 && (
            <span
              className="orders-pulse-text"
              style={{
                padding: '4px 10px',
                borderRadius: 999,
                background: '#fee2e2',
                border: '1px solid #fecaca',
                color: '#b91c1c',
                fontSize: 12,
                fontWeight: 800,
                ...MONEY,
              }}
            >
              {t('orders.needRelease', '{{n}} need release', { n: stats.paidUnreleased })}
            </span>
          )}

          <SearchJump value={search} onChange={setSearch} t={t} />

          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <StatChip label={t('orders.stat.total', 'Total')} value={stats.total} />
            <StatChip label={t('orders.stat.open', 'Open')} value={stats.open} accent={STATUS.AVAILABLE.dot} />
            <StatChip label={t('orders.stat.occupied', 'Occupied')} value={stats.occupied} accent={STATUS.OCCUPIED.dot} />
            <StatChip label={t('orders.stat.reserved', 'Reserved')} value={stats.reserved} accent={STATUS.RESERVED.dot} />
          </div>

          {showRestaurantSwitcher && (
            <select
              value={restaurantId || ''}
              onChange={(e) => {
                const id = parseInt(e.target.value, 10);
                if (Number.isFinite(id)) {
                  setRestaurantId(id);
                  try { localStorage.setItem('selectedRestaurantId', String(id)); } catch (_e) { /* ignore — localStorage may be unavailable */ }
                }
              }}
              style={{
                height: 40,
                padding: '0 10px',
                borderRadius: 10,
                border: `1px solid ${BORDER}`,
                background: SURFACE,
                color: TEXT,
                fontSize: 13,
                fontWeight: 600,
              }}
            >
              {restaurants.map((r) => (
                <option key={r.id} value={r.id}>{r.name}</option>
              ))}
            </select>
          )}

          <button
            onClick={() => loadAll('background')}
            disabled={refreshing}
            aria-label={t('common.refresh', 'Refresh')}
            style={{
              ...btnStyle('outline'),
              width: 40,
              padding: 0,
            }}
          >
            <RefreshCw size={14} className={refreshing ? 'orders-spin' : undefined} />
          </button>
        </header>

        {/* Scroll area */}
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '12px 16px 32px',
          }}
        >
          {loading ? (
            <div style={{ padding: 80, textAlign: 'center', color: MUTED }}>
              <Loader2 size={20} className="orders-spin" />
              <div style={{ marginTop: 8, fontSize: 13 }}>{t('orders.loading', 'Loading…')}</div>
            </div>
          ) : sections.length === 0 ? (
            <div style={{ padding: 80, textAlign: 'center', color: MUTED }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: TEXT }}>
                {search
                  ? t('orders.empty.noMatch', 'No tables match “{{q}}”', { q: search })
                  : t('orders.empty.noTables', 'No tables configured')}
              </div>
              {search && (
                <button onClick={() => setSearch('')} style={{ ...btnStyle('outline'), marginTop: 16 }}>
                  {t('common.clear', 'Clear search')}
                </button>
              )}
            </div>
          ) : (
            sections.map((section, idx) => (
              <SectionGroup
                key={section.name}
                section={section}
                color={sectionColor(section.name, idx)}
                onTileClick={(vm) => {
                  setPaymentMode(false);
                  setSelectedId(vm.id);
                }}
                t={t}
              />
            ))
          )}
        </div>

        <TableDetailDrawer
          vm={selected}
          open={!!selected}
          onClose={() => {
            setSelectedId(null);
            setPaymentMode(false);
            setPickerOpen(false);
          }}
          onAction={doAction}
          busyAction={busyAction}
          busyItemId={busyItemId}
          paymentMode={paymentMode}
          setPaymentMode={setPaymentMode}
          pickerOpen={pickerOpen}
          setPickerOpen={setPickerOpen}
          products={(restaurantId && productsByRestaurant[restaurantId]) || []}
          productsLoading={productsLoading}
          flashItemId={flashItemId}
          t={t}
        />

        <ConfirmDialog
          open={!!confirm}
          onClose={() => setConfirm(null)}
          onConfirm={() => confirm?.onConfirm?.()}
          title={confirm?.title}
          message={confirm?.message}
          danger={confirm?.danger}
          busy={confirm?.busy}
          confirmLabel={confirm?.confirmLabel}
          cancelLabel={confirm?.cancelLabel}
          t={t}
        />

        <ErrorModal
          error={errorState?.error}
          onClose={closeError}
          onRetry={retryLast}
          retrying={retrying}
          t={t}
        />
      </div>
    </>
  );
}

