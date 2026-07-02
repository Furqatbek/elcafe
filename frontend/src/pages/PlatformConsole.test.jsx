import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PlatformConsole from './PlatformConsole';
import { platformAPI, billingAPI } from '../services/api';

vi.mock('../services/api', () => ({
  platformAPI: {
    listTenants: vi.fn(),
    changePlan: vi.fn(),
    extend: vi.fn(),
    suspend: vi.fn(),
    reactivate: vi.fn(),
    cancel: vi.fn(),
  },
  billingAPI: { getPlans: vi.fn() },
}));

vi.mock('../hooks/useToast', () => ({ useToast: () => ({ toast: vi.fn() }) }));

// English fallbacks with {{n}} interpolation, so assertions read against real wording.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, defaultValue, opts) => {
      let s = typeof defaultValue === 'string' ? defaultValue : key;
      if (opts && typeof opts === 'object') {
        for (const [k, v] of Object.entries(opts)) {
          s = s.replace(new RegExp(`{{\\s*${k}\\s*}}`, 'g'), String(v));
        }
      }
      return s;
    },
  }),
}));

// Radix Select/Dialog/Checkbox need jsdom APIs we don't polyfill and aren't what these tests
// exercise — stub them to plain elements so the page (and the plan dialog) renders.
vi.mock('../components/ui/select', () => ({
  Select: ({ children }) => <div>{children}</div>,
  SelectTrigger: ({ children }) => <div>{children}</div>,
  SelectValue: ({ placeholder }) => <span>{placeholder}</span>,
  SelectContent: ({ children }) => <div>{children}</div>,
  SelectItem: ({ children }) => <div>{children}</div>,
}));
vi.mock('../components/ui/dialog', () => ({
  Dialog: ({ open, children }) => (open ? <div role="dialog">{children}</div> : null),
  DialogContent: ({ children }) => <div>{children}</div>,
  DialogHeader: ({ children }) => <div>{children}</div>,
  DialogTitle: ({ children }) => <h2>{children}</h2>,
  DialogFooter: ({ children }) => <div>{children}</div>,
}));
vi.mock('../components/ui/checkbox', () => ({
  Checkbox: ({ id, checked, onCheckedChange }) => (
    <input id={id} type="checkbox" checked={checked} onChange={(e) => onCheckedChange(e.target.checked)} />
  ),
}));

const tenant = (over) => ({
  restaurantId: 7, name: 'Cafe X', active: true, planCode: 'pro', planName: 'Pro',
  isTrial: false, planExpiresAt: null, daysUntilExpiry: 10, inGracePeriod: false, readOnly: false,
  subscriptionStatus: 'ACTIVE', ...over,
});

const pageOf = (content) => ({ data: { data: { content, page: { totalPages: 1, number: 0 } } } });

beforeEach(() => {
  vi.clearAllMocks();
  billingAPI.getPlans.mockResolvedValue({ data: { data: [{ code: 'pro', name: 'Pro' }, { code: 'start', name: 'Start' }] } });
});

afterEach(() => vi.restoreAllMocks());

// future flags quiet the v7 migration warnings react-router prints under test.
const Wrapper = ({ children }) => (
  <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{children}</MemoryRouter>
);
const renderPage = () => render(<PlatformConsole />, { wrapper: Wrapper });

describe('PlatformConsole', () => {
  it('lists tenants with lifecycle status and expiry', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant()]));
    renderPage();

    expect(await screen.findByText('Cafe X')).toBeInTheDocument();
    expect(screen.getByText('#7')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('10d left')).toBeInTheDocument();
  });

  it('renders the persisted lifecycle status, distinguishing Cancelled from Suspended', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([
      tenant({ restaurantId: 7, name: 'Cancelled Cafe', active: false, subscriptionStatus: 'CANCELLED' }),
      tenant({ restaurantId: 8, name: 'Paused Cafe', active: false, subscriptionStatus: 'SUSPENDED' }),
    ]));
    renderPage();

    expect(await screen.findByText('Cancelled')).toBeInTheDocument();
    expect(screen.getByText('Suspended')).toBeInTheDocument();
  });

  it('suspends an active tenant', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant()]));
    platformAPI.suspend.mockResolvedValue({});
    renderPage();
    await screen.findByText('Cafe X');

    fireEvent.click(screen.getByRole('button', { name: /Suspend/ }));

    await waitFor(() => expect(platformAPI.suspend).toHaveBeenCalledWith(7));
  });

  it('offers Reactivate (not Suspend) for a suspended tenant', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant({ active: false, subscriptionStatus: 'SUSPENDED' })]));
    renderPage();
    await screen.findByText('Cafe X');

    expect(screen.getByText('Suspended')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Reactivate/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Suspend/ })).toBeNull();
  });

  it('cancels a subscription after confirmation', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant()]));
    platformAPI.cancel.mockResolvedValue({});
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByText('Cafe X');

    fireEvent.click(screen.getByRole('button', { name: /Cancel/ }));

    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() => expect(platformAPI.cancel).toHaveBeenCalledWith(7));
  });

  it('does not cancel when the confirmation is declined', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant()]));
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderPage();
    await screen.findByText('Cafe X');

    fireEvent.click(screen.getByRole('button', { name: /Cancel/ }));

    expect(platformAPI.cancel).not.toHaveBeenCalled();
  });

  it('hides the Cancel action for an already-cancelled tenant', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant({ active: false, subscriptionStatus: 'CANCELLED' })]));
    renderPage();
    await screen.findByText('Cafe X');

    expect(screen.queryByRole('button', { name: /Cancel/ })).toBeNull();
    // A cancelled tenant is inactive, so Reactivate (the documented revival path) is offered.
    expect(screen.getByRole('button', { name: /Reactivate/ })).toBeInTheDocument();
  });

  it('plan dialog preserves the current expiry and trial flag on apply (no silent wipe)', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([
      tenant({ planExpiresAt: '2026-08-01T10:30:00', isTrial: true, subscriptionStatus: 'TRIAL' }),
    ]));
    platformAPI.changePlan.mockResolvedValue({});
    renderPage();
    await screen.findByText('Cafe X');

    fireEvent.click(screen.getByRole('button', { name: /Change plan/ }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    // Prefilled from the tenant row: LocalDateTime → datetime-local value.
    expect(screen.getByLabelText('Expires at')).toHaveValue('2026-08-01T10:30');

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(platformAPI.changePlan).toHaveBeenCalledWith(7, {
      planCode: 'pro',
      planExpiresAt: '2026-08-01T10:30',
      isTrial: true,
    }));
  });

  it('plan dialog sends an edited expiry and trial flag', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant()]));
    platformAPI.changePlan.mockResolvedValue({});
    renderPage();
    await screen.findByText('Cafe X');

    fireEvent.click(screen.getByRole('button', { name: /Change plan/ }));
    await screen.findByRole('dialog');
    fireEvent.change(screen.getByLabelText('Expires at'), { target: { value: '2026-09-15T00:00' } });
    fireEvent.click(screen.getByLabelText('Trial assignment'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(platformAPI.changePlan).toHaveBeenCalledWith(7, {
      planCode: 'pro',
      planExpiresAt: '2026-09-15T00:00',
      isTrial: true,
    }));
  });

  it('passes the search term to the API', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant()]));
    renderPage();
    await screen.findByText('Cafe X');

    fireEvent.change(screen.getByPlaceholderText('Search by name'), { target: { value: 'cafe' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => expect(platformAPI.listTenants)
      .toHaveBeenCalledWith(expect.objectContaining({ search: 'cafe', page: 0 })));
  });
});
