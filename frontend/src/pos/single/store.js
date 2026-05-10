import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { posAPI } from '../../services/api';

const STORAGE_KEY = 'pos-single-page-store-v1';

const TYPE_SEED = {
  dinein: { table: null, guests: 1, customer: null },
  takeaway: { customer: { name: '', phone: '' } },
  delivery: { customer: { name: '', phone: '', address: '' }, courier: '' },
};

const TYPE_LABEL_DEFAULTS = {
  dinein: 'Dine-in',
  takeaway: 'Takeaway',
  delivery: 'Delivery',
};

function nowHHMM() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function uid(prefix = 'id') {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
}

export function modifierSignature(productId, modifiers) {
  const ids = (modifiers || []).map((m) => `${m.groupId}:${m.optionId}`).sort();
  return `${productId}|${ids.join(',')}`;
}

export function lineSignature(line) {
  return modifierSignature(line.productId, line.modifiers);
}

function emptyTicket(type, label) {
  const seed = TYPE_SEED[type] || {};
  return {
    id: uid('tk'),
    type,
    label: label || TYPE_LABEL_DEFAULTS[type],
    items: [],
    note: '',
    opened: nowHHMM(),
    paused: false,
    payment: { method: 'cash', tendered: 0 },
    ...seed,
  };
}

const usePosStore = create(
  persist(
    (set, get) => ({
      tickets: [],
      activeId: null,
      parked: [],
      modifierDraft: null, // { productId, product, qty, modifiers, note } when editing
      density: 'balanced', // 'compact' | 'balanced' | 'spacious'
      gridCols: 5,
      cartWidth: 340,

      newTicket: (type = 'dinein', label) => {
        const ticket = emptyTicket(type, label);
        set((s) => ({
          tickets: [...s.tickets, ticket],
          activeId: ticket.id,
          modifierDraft: null,
        }));
        return ticket.id;
      },

      setActive: (id) => set({ activeId: id, modifierDraft: null }),

      closeTicket: (id) =>
        set((s) => {
          const tickets = s.tickets.filter((t) => t.id !== id);
          const activeId =
            s.activeId === id ? (tickets[0] ? tickets[0].id : null) : s.activeId;
          return { tickets, activeId };
        }),

      pauseActive: () =>
        set((s) => {
          const t = s.tickets.find((x) => x.id === s.activeId);
          if (!t) return {};
          const tickets = s.tickets.filter((x) => x.id !== t.id);
          const activeId = tickets[0] ? tickets[0].id : null;
          return {
            tickets,
            parked: [...s.parked, { ...t, paused: true }],
            activeId,
            modifierDraft: null,
          };
        }),

      resumeParked: (id) =>
        set((s) => {
          const t = s.parked.find((x) => x.id === id);
          if (!t) return {};
          return {
            parked: s.parked.filter((x) => x.id !== id),
            tickets: [...s.tickets, { ...t, paused: false }],
            activeId: t.id,
          };
        }),

      setType: (type) =>
        set((s) => {
          const seed = TYPE_SEED[type] || {};
          const tickets = s.tickets.map((t) => {
            if (t.id !== s.activeId) return t;
            return {
              ...t,
              type,
              label: t.label && t.label !== TYPE_LABEL_DEFAULTS[t.type] ? t.label : TYPE_LABEL_DEFAULTS[type],
              ...seed,
            };
          });
          return { tickets };
        }),

      patchActive: (partial) =>
        set((s) => ({
          tickets: s.tickets.map((t) => (t.id === s.activeId ? { ...t, ...partial } : t)),
        })),

      addItem: (product, modifiers = [], qty = 1, note = '') =>
        set((s) => {
          if (!s.activeId) return {};
          const tickets = s.tickets.map((t) => {
            if (t.id !== s.activeId) return t;
            const sig = modifierSignature(product.id, modifiers);
            const idx = t.items.findIndex((it) => lineSignature(it) === sig && (it.note || '') === (note || ''));
            if (idx >= 0) {
              const items = [...t.items];
              items[idx] = { ...items[idx], qty: items[idx].qty + qty };
              return { ...t, items };
            }
            const newLine = {
              lineId: uid('ln'),
              productId: product.id,
              name: product.name,
              qty,
              basePrice: Number(product.price) || 0,
              modifiers,
              note,
            };
            return { ...t, items: [...t.items, newLine] };
          });
          return { tickets, modifierDraft: null };
        }),

      setQty: (lineId, qty) =>
        set((s) => {
          const tickets = s.tickets.map((t) => {
            if (t.id !== s.activeId) return t;
            const next = qty <= 0 ? t.items.filter((l) => l.lineId !== lineId) :
              t.items.map((l) => (l.lineId === lineId ? { ...l, qty } : l));
            return { ...t, items: next };
          });
          return { tickets };
        }),

      removeItem: (lineId) => get().setQty(lineId, 0),

      setItemNote: (lineId, note) =>
        set((s) => ({
          tickets: s.tickets.map((t) =>
            t.id !== s.activeId
              ? t
              : { ...t, items: t.items.map((l) => (l.lineId === lineId ? { ...l, note } : l)) }
          ),
        })),

      openModifierDraft: (product) => {
        const groups = getModifierGroups(product);
        // Preselect first option of each required group
        const seedMods = [];
        for (const g of groups) {
          if (g.required && g.options.length > 0) {
            seedMods.push({
              groupId: g.id,
              optionId: g.options[0].id,
              name: `${g.name}: ${g.options[0].name}`,
              delta: Number(g.options[0].price || 0),
            });
          }
        }
        set({
          modifierDraft: {
            product,
            qty: 1,
            modifiers: seedMods,
            note: '',
          },
        });
      },

      cancelModifierDraft: () => set({ modifierDraft: null }),

      patchModifierDraft: (partial) =>
        set((s) =>
          s.modifierDraft ? { modifierDraft: { ...s.modifierDraft, ...partial } } : {}
        ),

      toggleDraftOption: (group, option) =>
        set((s) => {
          if (!s.modifierDraft) return {};
          const mods = [...s.modifierDraft.modifiers];
          const existingIdx = mods.findIndex(
            (m) => m.groupId === group.id && m.optionId === option.id
          );
          if (group.multi) {
            if (existingIdx >= 0) {
              mods.splice(existingIdx, 1);
            } else {
              mods.push({
                groupId: group.id,
                optionId: option.id,
                name: `${group.name}: ${option.name}`,
                delta: Number(option.price || 0),
              });
            }
          } else {
            // Single-select: replace any in this group
            for (let i = mods.length - 1; i >= 0; i--) {
              if (mods[i].groupId === group.id) mods.splice(i, 1);
            }
            mods.push({
              groupId: group.id,
              optionId: option.id,
              name: `${group.name}: ${option.name}`,
              delta: Number(option.price || 0),
            });
          }
          return { modifierDraft: { ...s.modifierDraft, modifiers: mods } };
        }),

      setPayment: (partial) =>
        set((s) => ({
          tickets: s.tickets.map((t) =>
            t.id === s.activeId
              ? { ...t, payment: { ...(t.payment || { method: 'cash', tendered: 0 }), ...partial } }
              : t
          ),
        })),

      setPaymentMode: (on) =>
        set((s) => ({
          tickets: s.tickets.map((t) =>
            t.id === s.activeId ? { ...t, paymentOpen: on } : t
          ),
        })),

      // Submits the active ticket to the backend, closing it on success.
      // Returns { success, orderNumber?, error? } so the caller can show
      // a confirmation. The active ticket is the source of truth for the
      // payload; persisted state stays untouched on failure so the cashier
      // can retry.
      chargeActive: async (restaurantId, opts = {}) => {
        const state = get();
        const ticket = state.tickets.find((t) => t.id === state.activeId);
        if (!ticket) return { success: false, error: 'No active ticket' };
        if (!ticket.items.length) return { success: false, error: 'Ticket is empty' };
        const restaurantCity = (opts.restaurantCity || '').trim();

        const subtotal = ticketSubtotal(ticket);
        const tax = ticketTax(ticket);
        const total = ticketTotal(ticket);
        const method = ticket.payment?.method || 'cash';
        const tendered = ticket.payment?.tendered || 0;
        const change = method === 'cash' ? Math.max(0, tendered - total) : 0;

        const orderType =
          ticket.type === 'dinein' ? 'DINE_IN'
            : ticket.type === 'takeaway' ? 'TAKEAWAY'
              : 'DELIVERY';

        const customer = ticket.customer || {};
        const payload = {
          restaurantId,
          orderType,
          orderSource: 'WALK_IN',
          customerInfo: {
            name: customer.name || (orderType === 'DINE_IN' ? 'Walk-in' : ''),
            phone: customer.phone || '',
            email: null,
          },
          items: ticket.items.map((line) => {
            const sizeMod = (line.modifiers || []).find((m) => m.groupId === 'size');
            return {
              productId: line.productId,
              variantId: sizeMod ? sizeMod.optionId : null,
              variantName: sizeMod ? sizeMod.name.replace(/^[^:]+:\s*/, '') : null,
              quantity: line.qty,
              price: lineUnitPrice(line),
              modifiers: (line.modifiers || [])
                .filter((m) => m.groupId !== 'size')
                .map((m) => ({ name: m.name, price: m.delta || 0 })),
              notes: line.note || null,
            };
          }),
          orderNotes: ticket.note || null,
          paymentMethod: method.toUpperCase(),
          subtotal,
          tax,
          deliveryFee: 0,
          total,
          amountTendered: method === 'cash' ? tendered : total,
          changeDue: change,
        };

        if (orderType === 'DINE_IN') {
          payload.dineInInfo = {
            tableNumber: ticket.table != null ? Number(ticket.table) || ticket.table : null,
            tableIds: ticket.tableId ? [ticket.tableId] : [],
            guestCount: ticket.guests || 1,
          };
        } else if (orderType === 'DELIVERY') {
          // Backend requires both street and city to be non-blank. The POS
          // collects a single address line, so we send it as `street` and
          // fall back to the restaurant's own city for `city`. If the
          // restaurant has no city configured we reuse the address text so
          // the @NotBlank validation still passes.
          const street = (customer.address || '').trim();
          if (!street) {
            const msg = 'Delivery address is required';
            set((s) => ({
              tickets: s.tickets.map((tk) =>
                tk.id === ticket.id ? { ...tk, submitError: msg } : tk
              ),
            }));
            return { success: false, error: msg };
          }
          payload.deliveryInfo = {
            street,
            city: restaurantCity || street,
            state: null,
            zipCode: null,
            deliveryInstructions: null,
          };
        }

        // Mark the ticket as submitting so the UI can disable the button.
        set((s) => ({
          tickets: s.tickets.map((t) =>
            t.id === ticket.id ? { ...t, submitting: true, submitError: null } : t
          ),
        }));

        try {
          const response = await posAPI.createOrder(payload);
          const data = response.data?.data || {};
          const tickets = get().tickets.filter((t) => t.id !== ticket.id);
          const activeId = tickets[0] ? tickets[0].id : null;
          set({ tickets, activeId, modifierDraft: null });
          return { success: true, orderNumber: data.orderNumber, orderId: data.id };
        } catch (error) {
          const errorMessage =
            error.response?.data?.message ||
            error.response?.data?.errors?.reason ||
            error.message ||
            'Failed to create order';
          set((s) => ({
            tickets: s.tickets.map((t) =>
              t.id === ticket.id ? { ...t, submitting: false, submitError: errorMessage } : t
            ),
          }));
          return { success: false, error: errorMessage };
        }
      },

      clearSubmitError: () =>
        set((s) => ({
          tickets: s.tickets.map((t) =>
            t.id === s.activeId ? { ...t, submitError: null } : t
          ),
        })),

      setDensity: (density) => set({ density }),
      setGridCols: (n) => set({ gridCols: Math.max(3, Math.min(8, n)) }),
      setCartWidth: (w) => set({ cartWidth: Math.max(280, Math.min(420, w)) }),
    }),
    {
      name: STORAGE_KEY,
      partialize: (s) => ({
        tickets: s.tickets,
        activeId: s.activeId,
        parked: s.parked,
        density: s.density,
        gridCols: s.gridCols,
        cartWidth: s.cartWidth,
      }),
    }
  )
);

// --- Helpers exported for components ---

export function getModifierGroups(product) {
  if (!product) return [];
  const groups = [];

  // Variants → Size group (required, single-select)
  if (Array.isArray(product.variants) && product.variants.length > 0) {
    groups.push({
      id: 'size',
      name: 'Size',
      required: true,
      multi: false,
      options: product.variants.map((v) => ({
        id: String(v.id),
        name: v.name,
        // Variant prices are absolute, not deltas — we render absolute and treat as override below.
        price: 0,
        absolute: Number(v.price) || 0,
      })),
    });
  }

  // Add-ons → Extras (optional, multi)
  if (Array.isArray(product.addOns) && product.addOns.length > 0) {
    groups.push({
      id: 'extras',
      name: 'Extras',
      required: false,
      multi: true,
      options: product.addOns.map((a) => ({
        id: String(a.id),
        name: a.name,
        price: Number(a.price) || 0,
      })),
    });
  }

  // Generic modifierGroups support if present on product
  if (Array.isArray(product.modifierGroups)) {
    for (const g of product.modifierGroups) {
      groups.push({
        id: String(g.id),
        name: g.name,
        required: !!g.required,
        multi: !!g.multi,
        options: (g.options || []).map((o) => ({
          id: String(o.id),
          name: o.name,
          price: Number(o.price || 0),
        })),
      });
    }
  }

  return groups;
}

export function productHasModifiers(product) {
  return getModifierGroups(product).length > 0;
}

export function lineUnitPrice(line) {
  // If a 'size' modifier with absolute price was chosen, use that as base
  const sizeMod = (line.modifiers || []).find((m) => m.groupId === 'size');
  let base = line.basePrice;
  if (sizeMod && sizeMod.absolute != null) base = sizeMod.absolute;
  const extras = (line.modifiers || []).reduce((sum, m) => sum + (m.delta || 0), 0);
  return base + extras;
}

export function lineTotal(line) {
  return lineUnitPrice(line) * line.qty;
}

export function ticketSubtotal(ticket) {
  if (!ticket) return 0;
  return ticket.items.reduce((sum, l) => sum + lineTotal(l), 0);
}

export function ticketTax(ticket, rate = 0.12) {
  return ticketSubtotal(ticket) * rate;
}

export function ticketTotal(ticket, rate = 0.12) {
  return ticketSubtotal(ticket) + ticketTax(ticket, rate);
}

export function draftUnitPrice(draft) {
  if (!draft) return 0;
  const sizeMod = draft.modifiers.find((m) => m.groupId === 'size');
  let base = Number(draft.product.price) || 0;
  if (sizeMod) {
    const opt = (draft.product.variants || []).find((v) => String(v.id) === sizeMod.optionId);
    if (opt) base = Number(opt.price) || 0;
  }
  const extras = draft.modifiers
    .filter((m) => m.groupId !== 'size')
    .reduce((sum, m) => sum + (m.delta || 0), 0);
  return base + extras;
}

export function draftTotal(draft) {
  return draftUnitPrice(draft) * (draft?.qty || 1);
}

export default usePosStore;
