import React, { useState, useRef, useEffect } from 'react';
import { Plus, Minus, Trash2 } from 'lucide-react';
import { fmtMoney, MONEY_STYLE } from '../theme';
import { lineTotal } from '../store';

export default function CartLine({ theme, line, onSetQty, onRemove, onSetNote }) {
  const [editingQty, setEditingQty] = useState(false);
  const [qtyDraft, setQtyDraft] = useState(String(line.qty));
  const [editingNote, setEditingNote] = useState(false);
  const [noteDraft, setNoteDraft] = useState(line.note || '');
  const inputRef = useRef(null);

  useEffect(() => {
    if (editingQty && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [editingQty]);

  const commitQty = () => {
    const n = parseInt(qtyDraft, 10);
    if (Number.isFinite(n) && n >= 0) onSetQty(n);
    setEditingQty(false);
  };

  const modSummary = (line.modifiers || [])
    .map((m) => m.name.replace(/^[^:]+:\s*/, ''))
    .join(' · ');

  return (
    <div
      style={{
        background: theme.surface,
        border: `1px solid ${theme.border}`,
        borderRadius: 10,
        padding: 10,
        display: 'grid',
        gridTemplateColumns: '1fr auto',
        gap: 8,
      }}
    >
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: theme.text,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {line.name}
        </div>
        {modSummary && (
          <div style={{ fontSize: 12, color: theme.textMuted, marginTop: 2 }}>{modSummary}</div>
        )}
        {editingNote ? (
          <input
            value={noteDraft}
            autoFocus
            onChange={(e) => setNoteDraft(e.target.value)}
            onBlur={() => {
              onSetNote(noteDraft);
              setEditingNote(false);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') e.target.blur();
              if (e.key === 'Escape') {
                setNoteDraft(line.note || '');
                setEditingNote(false);
              }
            }}
            placeholder="Note…"
            style={{
              marginTop: 4,
              width: '100%',
              height: 28,
              fontSize: 12,
              borderRadius: 6,
              border: `1px solid ${theme.border}`,
              background: theme.surfaceAlt,
              color: theme.text,
              padding: '0 8px',
              outline: 'none',
            }}
          />
        ) : (
          <button
            onClick={() => setEditingNote(true)}
            style={{
              marginTop: 4,
              fontSize: 12,
              color: line.note ? theme.text : theme.textMuted,
              fontStyle: line.note ? 'normal' : 'italic',
              background: 'transparent',
              border: 'none',
              padding: 0,
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            {line.note ? `“${line.note}”` : '+ note'}
          </button>
        )}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
        <span style={{ fontSize: 14, fontWeight: 700, color: theme.text, ...MONEY_STYLE }}>
          {fmtMoney(lineTotal(line))}
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <button
            onClick={() => onSetQty(line.qty - 1)}
            style={iconBtn(theme)}
            title="Decrease"
          >
            <Minus size={14} />
          </button>
          {editingQty ? (
            <input
              ref={inputRef}
              type="number"
              min={0}
              value={qtyDraft}
              onChange={(e) => setQtyDraft(e.target.value)}
              onBlur={commitQty}
              onKeyDown={(e) => {
                if (e.key === 'Enter') e.target.blur();
                if (e.key === 'Escape') setEditingQty(false);
              }}
              style={{
                width: 44,
                height: 28,
                textAlign: 'center',
                fontSize: 14,
                fontWeight: 700,
                borderRadius: 6,
                border: `1px solid ${theme.primary}`,
                background: theme.surface,
                color: theme.text,
                ...MONEY_STYLE,
                outline: 'none',
              }}
            />
          ) : (
            <button
              onClick={() => {
                setQtyDraft(String(line.qty));
                setEditingQty(true);
              }}
              onContextMenu={(e) => {
                e.preventDefault();
                setQtyDraft(String(line.qty));
                setEditingQty(true);
              }}
              style={{
                width: 44,
                height: 28,
                fontSize: 14,
                fontWeight: 700,
                color: theme.text,
                background: theme.surfaceAlt,
                border: `1px solid ${theme.border}`,
                borderRadius: 6,
                cursor: 'pointer',
                ...MONEY_STYLE,
              }}
              title="Tap to edit"
            >
              {line.qty}
            </button>
          )}
          <button
            onClick={() => onSetQty(line.qty + 1)}
            style={iconBtn(theme)}
            title="Increase"
          >
            <Plus size={14} />
          </button>
          <button
            onClick={onRemove}
            style={{ ...iconBtn(theme), color: theme.danger }}
            title="Remove"
          >
            <Trash2 size={14} />
          </button>
        </div>
      </div>
    </div>
  );
}

function iconBtn(theme) {
  return {
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
  };
}
