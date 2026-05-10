import React from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Minus, X } from 'lucide-react';
import Button from './Button';
import { fmtMoney, MONEY_STYLE } from '../theme';
import usePosStore, { getModifierGroups, draftTotal } from '../store';

export default function ModifierEditor({ theme }) {
  const { t } = useTranslation();
  const draft = usePosStore((s) => s.modifierDraft);
  const cancel = usePosStore((s) => s.cancelModifierDraft);
  const patch = usePosStore((s) => s.patchModifierDraft);
  const toggle = usePosStore((s) => s.toggleDraftOption);
  const addItem = usePosStore((s) => s.addItem);

  if (!draft) return null;
  const groups = getModifierGroups(draft.product);
  const requiredMissing = groups.some(
    (g) => g.required && !draft.modifiers.find((m) => m.groupId === g.id)
  );

  const onAdd = () => {
    addItem(
      { ...draft.product, price: Number(draft.product.price) || 0 },
      draft.modifiers,
      draft.qty,
      draft.note
    );
  };

  return (
    <div
      style={{
        background: theme.surface,
        border: `1px solid ${theme.primary}`,
        borderRadius: 12,
        padding: 12,
        marginBottom: 10,
        boxShadow: '0 4px 14px rgba(0,0,0,.06)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <span style={{ fontSize: 14, fontWeight: 700, color: theme.text }}>{draft.product.name}</span>
          <span style={{ fontSize: 11, fontWeight: 600, color: theme.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 }}>
            {t('pos.single.customize')}
          </span>
        </div>
        <button
          onClick={cancel}
          style={{
            width: 28,
            height: 28,
            borderRadius: 6,
            border: `1px solid ${theme.border}`,
            background: theme.surface,
            color: theme.text,
            cursor: 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          title={t('pos.single.cancelEsc')}
        >
          <X size={14} />
        </button>
      </div>

      {groups.map((g) => (
        <div key={g.id} style={{ marginBottom: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: theme.text, textTransform: 'uppercase', letterSpacing: 0.5 }}>
              {g.name}
            </span>
            {g.required && (
              <span style={{ fontSize: 10, color: theme.danger, fontWeight: 700 }}>{t('pos.single.required')}</span>
            )}
            {g.multi && (
              <span style={{ fontSize: 10, color: theme.textMuted, fontWeight: 700 }}>{t('pos.single.multi')}</span>
            )}
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {g.options.map((opt) => {
              const selected = !!draft.modifiers.find((m) => m.groupId === g.id && m.optionId === opt.id);
              const priceLabel = opt.absolute != null
                ? fmtMoney(opt.absolute)
                : opt.price > 0 ? `+${fmtMoney(opt.price)}` : '';
              return (
                <button
                  key={opt.id}
                  onClick={() => toggle(g, opt)}
                  style={{
                    height: 36,
                    padding: '0 12px',
                    borderRadius: 999,
                    border: `1px solid ${selected ? theme.primary : theme.border}`,
                    background: selected ? theme.primary : theme.surfaceAlt,
                    color: selected ? '#fff' : theme.text,
                    cursor: 'pointer',
                    fontSize: 13,
                    fontWeight: 600,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                  }}
                >
                  <span>{opt.name}</span>
                  {priceLabel && (
                    <span style={{ fontSize: 12, opacity: 0.85, ...MONEY_STYLE }}>{priceLabel}</span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      ))}

      <div style={{ marginBottom: 10 }}>
        <input
          value={draft.note}
          onChange={(e) => patch({ note: e.target.value })}
          placeholder={t('pos.single.modifierNotePh')}
          style={{
            width: '100%',
            height: 36,
            borderRadius: 8,
            border: `1px solid ${theme.border}`,
            background: theme.surfaceAlt,
            color: theme.text,
            padding: '0 10px',
            fontSize: 13,
            outline: 'none',
          }}
        />
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <button onClick={() => patch({ qty: Math.max(1, draft.qty - 1) })} style={stepBtn(theme)}>
            <Minus size={14} />
          </button>
          <span
            style={{
              minWidth: 44,
              height: 36,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 16,
              fontWeight: 700,
              color: theme.text,
              ...MONEY_STYLE,
            }}
          >
            {draft.qty}
          </span>
          <button onClick={() => patch({ qty: Math.min(99, draft.qty + 1) })} style={stepBtn(theme)}>
            <Plus size={14} />
          </button>
        </div>
        <Button
          theme={theme}
          variant="primary"
          size="lg"
          disabled={requiredMissing}
          onClick={onAdd}
          style={{ flex: 1 }}
        >
          {t('pos.single.addLine')} · {fmtMoney(draftTotal(draft))}
        </Button>
      </div>
    </div>
  );
}

function stepBtn(theme) {
  return {
    width: 36,
    height: 36,
    borderRadius: 8,
    border: `1px solid ${theme.border}`,
    background: theme.surface,
    color: theme.text,
    cursor: 'pointer',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
  };
}
