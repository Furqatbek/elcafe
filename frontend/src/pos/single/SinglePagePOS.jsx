import React, { useEffect, useState } from 'react';
import { posAPI, tablesAPI } from '../../services/api';
import Header from './components/Header';
import CategoriesRail from './components/CategoriesRail';
import ProductGrid from './components/ProductGrid';
import TicketRail from './components/TicketRail';
import Divider from './components/Divider';
import { THEMES } from './theme';
import usePosStore, { productHasModifiers } from './store';

export default function SinglePagePOS() {
  const themeKey = usePosStore((s) => s.theme);
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
  const [selectedCategory, setSelectedCategory] = useState(null);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const restaurantId = localStorage.getItem('selectedRestaurantId') || '1';

  useEffect(() => {
    let cancel = false;
    const load = async () => {
      setLoading(true);
      try {
        const [catsRes, prodsRes] = await Promise.all([
          posAPI.getCategories(restaurantId),
          posAPI.getProducts(restaurantId),
        ]);
        if (cancel) return;
        const cats = catsRes.data?.data || catsRes.data || [];
        const prods = prodsRes.data?.data || prodsRes.data || [];
        setCategories(cats);
        setProducts(prods);
        try {
          const tRes = await tablesAPI.getAll(restaurantId);
          if (!cancel) setTables(tRes.data?.data || tRes.data || []);
        } catch {
          /* tables optional */
        }
      } catch (e) {
        if (!cancel) setError(e?.response?.data?.message || e.message || 'Failed to load menu');
      } finally {
        if (!cancel) setLoading(false);
      }
    };
    load();
    return () => {
      cancel = true;
    };
  }, [restaurantId]);

  // Seed an initial ticket if none exists once tickets are loaded
  useEffect(() => {
    if (!loading && tickets.length === 0) {
      newTicket('dinein');
    }
  }, [loading]); // eslint-disable-line react-hooks/exhaustive-deps

  const active = tickets.find((t) => t.id === activeId);

  const onSelectProduct = (product) => {
    if (productHasModifiers(product)) {
      // Make sure ticket isn't in payment mode
      if (active?.paymentOpen) setPaymentMode(false);
      openDraft(product);
    } else {
      if (active?.paymentOpen) setPaymentMode(false);
      addItem(product, [], 1);
    }
  };

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
          {/* Density / cols toolbar */}
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
            <span>Density</span>
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
                {d}
              </button>
            ))}
            <span style={{ marginLeft: 8 }}>Cols</span>
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
            {loading && (
              <span style={{ marginLeft: 'auto', color: theme.textMuted }}>Loading menu…</span>
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
        <TicketRail theme={theme} ticket={active} tables={tables} width={cartWidth} />
      </div>
    </div>
  );
}
