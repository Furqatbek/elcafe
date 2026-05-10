import React from 'react';
import { Search } from 'lucide-react';
import { categoryColorFor } from '../theme';

export default function CategoriesRail({
  theme,
  categories,
  products,
  selected,
  onSelect,
  search,
  onSearch,
}) {
  const counts = React.useMemo(() => {
    const m = {};
    for (const p of products) {
      const k = p.categoryId || 'uncat';
      m[k] = (m[k] || 0) + 1;
    }
    return m;
  }, [products]);

  return (
    <div
      style={{
        width: 220,
        flexShrink: 0,
        background: theme.surface,
        borderRight: `1px solid ${theme.border}`,
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div style={{ padding: 12, borderBottom: `1px solid ${theme.border}` }}>
        <div style={{ position: 'relative' }}>
          <Search
            size={16}
            style={{
              position: 'absolute',
              left: 10,
              top: '50%',
              transform: 'translateY(-50%)',
              color: theme.textMuted,
            }}
          />
          <input
            value={search}
            onChange={(e) => onSearch(e.target.value)}
            placeholder="Search items…"
            style={{
              width: '100%',
              height: 40,
              borderRadius: 10,
              border: `1px solid ${theme.border}`,
              background: theme.surfaceAlt,
              color: theme.text,
              padding: '0 10px 0 32px',
              fontSize: 14,
              outline: 'none',
            }}
          />
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
        <CategoryRow
          theme={theme}
          color={theme.primary}
          name="All items"
          count={products.length}
          active={!selected}
          onClick={() => onSelect(null)}
        />
        {categories.map((c, i) => (
          <CategoryRow
            key={c.id}
            theme={theme}
            color={categoryColorFor(c, i)}
            name={c.name}
            count={counts[c.id] || 0}
            active={selected === c.id}
            onClick={() => onSelect(c.id)}
          />
        ))}
      </div>
    </div>
  );
}

function CategoryRow({ theme, color, name, count, active, onClick }) {
  return (
    <button
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: '100%',
        height: 44,
        padding: '0 10px',
        marginBottom: 4,
        borderRadius: 10,
        border: 'none',
        background: active ? theme.primarySoft : 'transparent',
        color: active ? theme.primary : theme.text,
        fontWeight: active ? 700 : 600,
        cursor: 'pointer',
        textAlign: 'left',
        fontSize: 14,
      }}
    >
      <span
        style={{
          width: 12,
          height: 12,
          borderRadius: 3,
          background: color,
          flexShrink: 0,
        }}
      />
      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {name}
      </span>
      <span
        style={{
          fontSize: 12,
          color: theme.textMuted,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {count}
      </span>
    </button>
  );
}
