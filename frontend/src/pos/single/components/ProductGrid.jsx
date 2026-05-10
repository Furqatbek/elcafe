import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import ProductTile from './ProductTile';
import { categoryColorFor } from '../theme';
import usePosStore, { productHasModifiers } from '../store';

export default function ProductGrid({
  theme,
  products,
  categories,
  selectedCategory,
  search,
  density,
  cols,
  onSelectProduct,
}) {
  const { t } = useTranslation();
  const tickets = usePosStore((s) => s.tickets);
  const activeId = usePosStore((s) => s.activeId);
  const active = tickets.find((t) => t.id === activeId);

  const [bursts, setBursts] = useState({}); // productId -> timeout id
  const lastQtyRef = React.useRef({});

  const qtyByProduct = useMemo(() => {
    const m = {};
    if (!active) return m;
    for (const it of active.items) {
      m[it.productId] = (m[it.productId] || 0) + it.qty;
    }
    return m;
  }, [active]);

  // Burst when qty increases
  useEffect(() => {
    const last = lastQtyRef.current;
    const newBursts = {};
    for (const pid of Object.keys(qtyByProduct)) {
      if ((qtyByProduct[pid] || 0) > (last[pid] || 0)) {
        newBursts[pid] = true;
      }
    }
    if (Object.keys(newBursts).length > 0) {
      setBursts((b) => ({ ...b, ...newBursts }));
      const t = setTimeout(() => {
        setBursts((b) => {
          const c = { ...b };
          for (const k of Object.keys(newBursts)) delete c[k];
          return c;
        });
      }, 260);
      lastQtyRef.current = { ...qtyByProduct };
      return () => clearTimeout(t);
    }
    lastQtyRef.current = { ...qtyByProduct };
  }, [qtyByProduct]);

  const colorByCategory = useMemo(() => {
    const m = {};
    categories.forEach((c, i) => {
      m[c.id] = categoryColorFor(c, i);
    });
    return m;
  }, [categories]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return products.filter((p) => {
      if (selectedCategory && p.categoryId !== selectedCategory) return false;
      if (q) {
        const hay = `${p.name} ${p.description || ''}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [products, selectedCategory, search]);

  return (
    <div
      style={{
        flex: 1,
        overflowY: 'auto',
        padding: 12,
        background: theme.bg,
      }}
    >
      {filtered.length === 0 ? (
        <div
          style={{
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: theme.textMuted,
            fontSize: 14,
          }}
        >
          {t('pos.single.noMatches')}
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`,
            gap: 10,
          }}
        >
          {filtered.map((p) => (
            <ProductTile
              key={p.id}
              theme={theme}
              product={p}
              color={colorByCategory[p.categoryId] || theme.primary}
              qty={qtyByProduct[p.id] || 0}
              hasOptions={productHasModifiers(p)}
              density={density}
              burst={!!bursts[p.id]}
              onClick={() => onSelectProduct(p)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
