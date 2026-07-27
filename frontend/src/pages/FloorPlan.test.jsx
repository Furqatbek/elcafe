import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import FloorPlan from './FloorPlan';
import { floorPlanAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';

// What this page must never get wrong: showing the room's live state honestly, and not offering an
// edit affordance to somebody the server will refuse. The geometry maths (snap, rotation wrap, corner
// radius) is pinned separately in shapes.test.js, where it can be asserted as arithmetic rather than
// through jsdom, which has no SVG layout engine.

// Resolves keys through the REAL en.json rather than echoing the inline default. That makes this file
// double as a check that every key the page asks for actually exists: a missing one renders the raw
// fallback and the assertions below stop matching.
vi.mock('react-i18next', async () => {
  const en = (await import('../i18n/locales/en.json')).default;
  const lookup = (key) =>
    key.split('.').reduce((acc, part) => (acc == null ? undefined : acc[part]), en);
  return {
    useTranslation: () => ({
      t: (k, d, opts) => {
        const resolved = lookup(k);
        const base = typeof resolved === 'string' ? resolved : typeof d === 'string' ? d : k;
        if (!opts) return base;
        return Object.entries(opts).reduce(
          (acc, [key, value]) => acc.replaceAll(`{{${key}}}`, String(value)),
          base
        );
      },
    }),
  };
});
vi.mock('../lib/errors', () => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
}));
vi.mock('react-router-dom', () => ({
  Link: ({ to, children }) => <a href={to}>{children}</a>,
}));
vi.mock('../services/websocket', () => ({
  default: {
    connect: vi.fn(() => Promise.resolve()),
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
  },
}));
vi.mock('../services/api', () => ({
  floorPlanAPI: {
    list: vi.fn(),
    get: vi.fn(),
    saveLayout: vi.fn(),
  },
  reservationAPI: { create: vi.fn() },
  tablesAPI: { create: vi.fn() },
}));
vi.mock('../utils/restaurant', () => ({ getCurrentRestaurantId: () => 4 }));

const occupiedTable = {
  id: 1,
  tableNumber: '12',
  capacity: 4,
  status: 'AVAILABLE',
  positionX: 40,
  positionY: 40,
  width: 100,
  height: 100,
  shape: 'ROUNDED',
  rotationDeg: 0,
  zIndex: 0,
  occupancy: {
    orderId: 900,
    orderNumber: 'ORD-900',
    seatedSince: '2026-07-20T18:30:00Z',
    orderTotal: 125000,
    customerId: 31,
    customerName: 'Nodira Karimova',
  },
};

const walkInTable = {
  ...occupiedTable,
  id: 2,
  tableNumber: '13',
  occupancy: {
    orderId: 901,
    orderNumber: 'ORD-901',
    seatedSince: '2026-07-20T19:00:00Z',
    orderTotal: 40000,
    customerId: null,
    customerName: null,
  },
};

/** Stale stored status, no open order — the map must still draw it free. */
const staleTable = {
  ...occupiedTable,
  id: 3,
  tableNumber: '14',
  status: 'OCCUPIED',
  occupancy: null,
};

const plan = {
  id: 11,
  name: 'Main floor',
  isDefault: true,
  canvasWidth: 1200,
  canvasHeight: 800,
  sections: [{ id: 5, name: 'Bar corner', polygon: [{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 100, y: 60 }], fillColor: '#eee', zIndex: 0 }],
  objects: [{ id: 7, objectType: 'SOFA', label: 'Sofa A', positionX: 300, positionY: 200, width: 140, height: 60, shape: 'RECTANGLE', rotationDeg: 0, zIndex: 0 }],
  tables: [occupiedTable, walkInTable, staleTable],
};

const setRole = (role) => useAuthStore.setState({ user: { id: 1, role, restaurantId: 4 } });

describe('FloorPlan', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setRole('MANAGER');
    floorPlanAPI.list.mockResolvedValue({ data: { data: [{ id: 11, name: 'Main floor' }] } });
    floorPlanAPI.get.mockResolvedValue({ data: { data: plan } });
  });

  afterEach(() => {
    useAuthStore.setState({ user: null });
  });

  it('opens the default map and draws its tables, furniture and sections', async () => {
    render(<FloorPlan />);

    await screen.findByTestId('table-1');
    expect(floorPlanAPI.get).toHaveBeenCalledWith(null); // no remembered choice -> the default
    expect(screen.getByTestId('table-2')).toBeInTheDocument();
    expect(screen.getByTestId('object-7')).toBeInTheDocument();
    expect(screen.getByTestId('section-5')).toBeInTheDocument();
  });

  /**
   * The whole reason occupancy is derived from live orders. A table left marked OCCUPIED after the
   * bill closed must render free, or the host turns guests away from an empty room.
   */
  it('draws a table with no open order as free even when its stored status says occupied', async () => {
    render(<FloorPlan />);

    await screen.findByTestId('table-3');
    expect(screen.getByTestId('table-3')).toHaveAttribute('data-state', 'free');
    expect(screen.getByTestId('table-1')).toHaveAttribute('data-state', 'occupied');
  });

  it('counts occupied and free from live occupancy, not from stored status', async () => {
    render(<FloorPlan />);
    await screen.findByTestId('table-1');
    expect(screen.getByText('3 tables · 2 occupied · 1 free')).toBeInTheDocument();
  });

  it('clicking an occupied table opens its guest, one click from the full profile', async () => {
    render(<FloorPlan />);

    fireEvent.click((await screen.findByTestId('table-1')).querySelector('rect'));

    // Scoped to the drawer: the guest's name also appears on the table itself, which is the point —
    // a host reads the room without clicking anything.
    const drawer = await screen.findByTestId('table-drawer');
    expect(within(drawer).getByText('Nodira Karimova')).toBeInTheDocument();
    expect(within(drawer).getByText('ORD-900')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open guest profile/ })).toHaveAttribute(
      'href',
      '/customers/31'
    );
  });

  /** A walk-in has no customer record. Inventing a link would 404; claiming a name would be a lie. */
  it('a walk-in shows the order alone, with no guest link', async () => {
    render(<FloorPlan />);

    fireEvent.click((await screen.findByTestId('table-2')).querySelector('rect'));

    await screen.findByTestId('table-drawer');
    expect(screen.getByText(/Walk-in/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Open guest profile/ })).not.toBeInTheDocument();
  });

  it('the drawer offers a quick reservation for the clicked table', async () => {
    render(<FloorPlan />);

    fireEvent.click((await screen.findByTestId('table-1')).querySelector('rect'));
    fireEvent.click(await screen.findByText('Quick reservation'));

    await screen.findByTestId('quick-reserve-dialog');
    expect(screen.getByText('Reserve table 12')).toBeInTheDocument();
  });

  /**
   * The server refuses a layout write from a waiter (FloorPlanController + RbacGateAnnotationTest);
   * showing them the button would just produce a 403 they cannot act on.
   */
  it('a waiter sees the room but is offered no way to edit it', async () => {
    setRole('WAITER');
    render(<FloorPlan />);

    await screen.findByTestId('table-1');
    expect(screen.queryByText('Edit layout')).not.toBeInTheDocument();
  });

  it('a manager can enter edit mode and gets the furniture palette and section tool', async () => {
    render(<FloorPlan />);

    fireEvent.click(await screen.findByText('Edit layout'));

    expect(screen.getByText('Sofa')).toBeInTheDocument();
    expect(screen.getByText('Chair')).toBeInTheDocument();
    expect(screen.getByText('Draw section')).toBeInTheDocument();
    expect(screen.getByText('Snap to grid')).toBeInTheDocument();
  });

  it('adding a sofa puts it on the map and selects it for editing', async () => {
    render(<FloorPlan />);
    fireEvent.click(await screen.findByText('Edit layout'));

    fireEvent.click(screen.getByText('Sofa'));

    // The inspector opens on the new object, offering the corner picker the operator asked for.
    expect(screen.getByText('Corners')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Oval' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rounded' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Squared' })).toBeInTheDocument();
  });

  it('saving sends every shape on the map, with drafts carrying a null id', async () => {
    floorPlanAPI.saveLayout.mockResolvedValue({ data: { data: plan } });
    render(<FloorPlan />);
    fireEvent.click(await screen.findByText('Edit layout'));
    fireEvent.click(screen.getByText('Chair'));
    fireEvent.click(screen.getByText('Save'));

    await waitFor(() => expect(floorPlanAPI.saveLayout).toHaveBeenCalled());
    const [planId, payload] = floorPlanAPI.saveLayout.mock.calls[0];
    expect(planId).toBe(11);
    expect(payload.tables).toHaveLength(3);
    // The existing sofa keeps its id; the new chair is sent as new rather than as a negative id the
    // server would try (and fail) to look up.
    expect(payload.objects.map((o) => o.id)).toEqual([7, null]);
    expect(payload.objects[1].objectType).toBe('CHAIR');
    expect(payload.sections[0].id).toBe(5);
  });

  /**
   * A table is a real record, not furniture: it goes through the Tables API so it gets a number, a
   * capacity and an id, then joins the draft to be placed by the same Save as everything else.
   */
  it('adding a table creates the record, then places it on the map when saved', async () => {
    const { tablesAPI } = await import('../services/api');
    tablesAPI.create.mockResolvedValue({
      data: { data: { id: 99, tableNumber: '20', capacity: 6, status: 'AVAILABLE' } },
    });
    floorPlanAPI.saveLayout.mockResolvedValue({ data: { data: plan } });

    render(<FloorPlan />);
    fireEvent.click(await screen.findByText('Edit layout'));
    fireEvent.click(screen.getByRole('button', { name: /^Table$/ }));

    const dialog = await screen.findByTestId('add-table-dialog');
    fireEvent.change(within(dialog).getByLabelText('Table number'), { target: { value: '20' } });
    fireEvent.change(within(dialog).getByLabelText('Capacity'), { target: { value: '6' } });
    fireEvent.click(within(dialog).getByText('Create'));

    await waitFor(() => expect(tablesAPI.create).toHaveBeenCalled());
    expect(tablesAPI.create.mock.calls[0][0]).toMatchObject({
      restaurantId: 4,
      tableNumber: '20',
      capacity: 6,
    });

    await screen.findByTestId('table-99');
    fireEvent.click(screen.getByText('Save'));
    await waitFor(() => expect(floorPlanAPI.saveLayout).toHaveBeenCalled());
    expect(floorPlanAPI.saveLayout.mock.calls[0][1].tables.map((x) => x.id)).toContain(99);
  });

  /** Two tables called "12" are indistinguishable on the map and on the bill. */
  it('refuses a duplicate table number without a round trip', async () => {
    const { tablesAPI } = await import('../services/api');
    const { notifyWarning } = await import('../lib/errors');

    render(<FloorPlan />);
    fireEvent.click(await screen.findByText('Edit layout'));
    fireEvent.click(screen.getByRole('button', { name: /^Table$/ }));

    const dialog = await screen.findByTestId('add-table-dialog');
    fireEvent.change(within(dialog).getByLabelText('Table number'), { target: { value: '12' } });
    fireEvent.click(within(dialog).getByText('Create'));

    expect(notifyWarning).toHaveBeenCalled();
    expect(tablesAPI.create).not.toHaveBeenCalled();
  });

  it('a section needs three points before it can be finished', async () => {
    const { notifyWarning } = await import('../lib/errors');
    render(<FloorPlan />);
    fireEvent.click(await screen.findByText('Edit layout'));
    fireEvent.click(screen.getByText('Draw section'));

    fireEvent.click(screen.getByText('Finish section (0 points)'));

    expect(notifyWarning).toHaveBeenCalled();
    expect(screen.queryByText('Section')).not.toBeInTheDocument();
  });

  it('subscribes to its own restaurant’s floor stream, never a bare topic', async () => {
    const ws = (await import('../services/websocket')).default;
    render(<FloorPlan />);

    await waitFor(() => expect(ws.subscribe).toHaveBeenCalled());
    expect(ws.subscribe.mock.calls[0][0]).toBe('/topic/restaurant/4/floor');
  });
});
