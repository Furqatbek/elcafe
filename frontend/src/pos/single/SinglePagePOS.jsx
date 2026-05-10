import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { posAPI, tablesAPI, shiftAPI, restaurantAPI } from '../../services/api';
import Header from './components/Header';
import CategoriesRail from './components/CategoriesRail';
import ProductGrid from './components/ProductGrid';
import TicketRail from './components/TicketRail';
import Divider from './components/Divider';
import Button from './components/Button';
import { THEMES } from './theme';
import usePosStore, { productHasModifiers } from './store';

const THEME_KEY = 'blue';

export default function SinglePagePOS() {
  const { t } = useTranslation();
  const themeKey = THEME_KEY;
  const tickets = usePosStore((s) => s.tickets);
  const activeId = usePosStore((s) => s.activeId);
  const setActive = usePosStore((s) => s.setActive);
  const newTicket = usePosStore((s) => s.newTicket);
  const setPaymentMode = usePosStore((s) => s.setPaymentMode);
  const cancelDraft = usePosStore((s) => s.cancelModifierDraft);
  const openDraft = usePosStore((s) => s.openModifierDraft);
  const addItem = usePosStore((s) => s.addItem);
  const cartWidth = usePosStore((s) => s.cartWidth);
  const density = usePosStore((s) => s.density);
  const setDensity = usePosStore((s) => s.setDensity);
  const gridCols = usePosStore((s) => s.gridCols);
  const setGridCols = usePosStore((s) => s.setGridCols);

  const theme = THEMES[themeKey] || THEMES.blue;

  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [tables, setTables] = useState([]);
  const [restaurant, setRestaurant] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState(null);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);

  // Shift gating
  const [shiftLoading, setShiftLoading] = useState(true);
  const [activeShift, setActiveShift] = useState(null);
  const [clockingIn, setClockingIn] = useState(false);
  const [clockInError, setClockInError] = useState(null);

  const restaurantId = localStorage.getItem('selectedRestaurantId') || '1';

  // Set default restaurantId on mount if not already set
  useEffect(() => {
    if (!localStorage.getItem('selectedRestaurantId')) {
      localStorage.setItem('selectedRestaurantId', '1');
    }
  }, []);

  // Active-shift check (matches the legacy POS's clock-in gate)
  useEffect(() => {
    let cancel = false;
    const check = async () => {
      try {
        const res = await shiftAPI.getActive(restaurantId);
        // The backend returns List<ShiftSummaryDTO> directly (no { data: [...] }
        // envelope), but tolerate both shapes in case other endpoints wrap.
        const body = res.data;
        const list = Array.isArray(body)
          ? body
          : Array.isArray(body?.data)
            ? body.data
            : body && typeof body === 'object'
              ? [body.data || body]
              : [];
        if (!cancel) setActiveShift(list.find(Boolean) || null);
      } catch (e) {
        if (!cancel) setActiveShift(null);
      } finally {
        if (!cancel) setShiftLoading(false);
      }
    };
    check();
    return () => { cancel = true; };
  }, [restaurantId]);

  // Load menu + tables from the real backend.
  useEffect(() => {
    if (!activeShift) return;
    let cancel = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const [catsRes, prodsRes, tablesRes, restRes] = await Promise.all([
          posAPI.getCategories(restaurantId),
          posAPI.getProducts(restaurantId),
          tablesAPI.getAll(restaurantId).catch(() => ({ data: { data: [] } })),
          restaurantAPI.getById(restaurantId).catch(() => ({ data: { data: null } })),
        ]);
        if (cancel) return;
        setCategories(catsRes.data?.data || catsRes.data || []);
        setProducts(prodsRes.data?.data || prodsRes.data || []);
        setTables(tablesRes.data?.data || tablesRes.data || []);
        setRestaurant(restRes.data?.data || restRes.data || null);
      } catch (e) {
        if (!cancel) setError(e?.response?.data?.message || e.message || 'Failed to load menu');
      } finally {
        if (!cancel) setLoading(false);
      }
    };
    load();
    return () => { cancel = true; };
  }, [restaurantId, activeShift]);

  const active = tickets.find((t) => t.id === activeId);

  const onSelectProduct = (product) => {
    if (productHasModifiers(product)) {
      if (active?.paymentOpen) setPaymentMode(false);
      openDraft(product);
    } else {
      if (active?.paymentOpen) setPaymentMode(false);
      addItem(product, [], 1);
    }
  };

  const handleCharged = (result) => {
    if (result?.orderNumber) {
      setToast(t('pos.single.orderCreated', { number: result.orderNumber }));
    } else {
      setToast(t('pos.single.orderCreatedNoNum'));
    }
  };

  // Auto-dismiss the toast
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2500);
    return () => clearTimeout(t);
  }, [toast]);

  // Hotkeys
  useEffect(() => {
    const onKey = (e) => {
      if (e.target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target.tagName)) return;
      const meta = e.metaKey || e.ctrlKey;
      if (meta && /^[1-9]$/.test(e.key)) {
        const idx = parseInt(e.key, 10) - 1;
        const t = tickets[idx];
        if (t) {
          e.preventDefault();
          setActive(t.id);
        }
      } else if (!meta && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        newTicket('dinein');
      } else if (!meta && e.key.toLowerCase() === 'p') {
        if (active && active.items.length > 0) {
          e.preventDefault();
          setPaymentMode(true);
        }
      } else if (e.key === 'Escape') {
        cancelDraft();
        if (active?.paymentOpen) setPaymentMode(false);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [tickets, active, newTicket, setActive, setPaymentMode, cancelDraft]);

  const handleClockIn = async () => {
    setClockingIn(true);
    setClockInError(null);
    try {
      const res = await shiftAPI.clockIn(restaurantId, {});
      // Backend returns EmployeeShift directly; fall through to the wrapped
      // shape just in case.
      const shift = res.data?.data || res.data || {};
      setActiveShift(shift);
    } catch (e) {
      setClockInError(e.response?.data?.message || e.message || 'Failed to clock in');
    } finally {
      setClockingIn(false);
    }
  };

  if (shiftLoading) {
    return <CenteredMessage theme={theme} text={t('pos.single.checkingShift')} />;
  }

  if (!activeShift) {
    return (
      <div
        style={{
          height: '100vh',
          width: '100vw',
          background: theme.bg,
          color: theme.text,
          fontFamily: '"Geist", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div
          style={{
            background: theme.surface,
            border: `1px solid ${theme.border}`,
            borderRadius: 12,
            padding: 32,
            maxWidth: 380,
            width: '100%',
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: 22, fontWeight: 800, marginBottom: 8 }}>{t('pos.single.noActiveShift')}</div>
          <div style={{ fontSize: 14, color: theme.textMuted, marginBottom: 20 }}>
            {t('pos.single.noActiveShiftHint')}
          </div>
          {clockInError && (
            <div style={{ color: theme.danger, fontSize: 13, fontWeight: 600, marginBottom: 12 }}>
              {clockInError}
            </div>
          )}
          <Button
            theme={theme}
            variant="primary"
            size="xl"
            onClick={handleClockIn}
            disabled={clockingIn}
            style={{ width: '100%' }}
          >
            {clockingIn ? t('pos.single.clockingIn') : t('pos.single.clockIn')}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        height: '100vh',
        width: '100vw',
        background: theme.bg,
        color: theme.text,
        fontFamily: '"Geist", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <Header theme={theme} themeKey={themeKey} />

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        <CategoriesRail
          theme={theme}
          categories={categories}
          products={products}
          selected={selectedCategory}
          onSelect={setSelectedCategory}
          search={search}
          onSearch={setSearch}
        />

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <div
            style={{
              height: 40,
              flexShrink: 0,
              padding: '0 12px',
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              borderBottom: `1px solid ${theme.border}`,
              background: theme.surface,
              fontSize: 12,
              color: theme.textMuted,
              fontWeight: 600,
            }}
          >
            <span>{t('pos.single.density')}</span>
            {['compact', 'balanced', 'spacious'].map((d) => (
              <button
                key={d}
                onClick={() => setDensity(d)}
                style={{
                  height: 26,
                  padding: '0 8px',
                  border: 'none',
                  borderRadius: 6,
                  background: density === d ? theme.primarySoft : 'transparent',
                  color: density === d ? theme.primary : theme.textMuted,
                  fontWeight: 700,
                  cursor: 'pointer',
                  textTransform: 'capitalize',
                  fontSize: 12,
                }}
              >
                {t(`pos.single.density${d.charAt(0).toUpperCase()}${d.slice(1)}`)}
              </button>
            ))}
            <span style={{ marginLeft: 8 }}>{t('pos.single.cols')}</span>
            <input
              type="range"
              min={3}
              max={8}
              value={gridCols}
              onChange={(e) => setGridCols(Number(e.target.value))}
              style={{ width: 100 }}
            />
            <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700, color: theme.text }}>
              {gridCols}
            </span>
            {error && (
              <span style={{ marginLeft: 'auto', color: theme.danger, fontWeight: 700 }}>{error}</span>
            )}
            {loading && !error && (
              <span style={{ marginLeft: 'auto', color: theme.textMuted }}>{t('pos.single.loadingMenu')}</span>
            )}
          </div>

          <ProductGrid
            theme={theme}
            products={products}
            categories={categories}
            selectedCategory={selectedCategory}
            search={search}
            density={density}
            cols={gridCols}
            onSelectProduct={onSelectProduct}
          />
        </div>

        <Divider theme={theme} />
        <TicketRail
          theme={theme}
          ticket={active}
          tables={tables}
          width={cartWidth}
          restaurantId={restaurantId}
          restaurantCity={restaurant?.city || ''}
          onCharged={handleCharged}
        />
      </div>

      {toast && (
        <div
          style={{
            position: 'fixed',
            bottom: 24,
            left: '50%',
            transform: 'translateX(-50%)',
            background: theme.success,
            color: '#fff',
            padding: '12px 20px',
            borderRadius: 10,
            fontWeight: 700,
            fontSize: 14,
            boxShadow: '0 8px 24px rgba(0,0,0,.18)',
            zIndex: 50,
          }}
        >
          {toast}
        </div>
      )}
    </div>
  );
}

function CenteredMessage({ theme, text }) {
  return (
    <div
      style={{
        height: '100vh',
        width: '100vw',
        background: theme.bg,
        color: theme.textMuted,
        fontFamily: '"Geist", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 14,
      }}
    >
      {text}
    </div>
  );
}
