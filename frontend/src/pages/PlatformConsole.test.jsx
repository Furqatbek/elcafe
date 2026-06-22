import { describe, it, expect, vi, beforeEach } from 'vitest';
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

// Radix Select needs jsdom APIs we don't polyfill and isn't what these tests exercise — stub it to
// plain elements so the rest of the page renders.
vi.mock('../components/ui/select', () => ({
  Select: ({ children }) => <div>{children}</div>,
  SelectTrigger: ({ children }) => <div>{children}</div>,
  SelectValue: ({ placeholder }) => <span>{placeholder}</span>,
  SelectContent: ({ children }) => <div>{children}</div>,
  SelectItem: ({ children }) => <div>{children}</div>,
}));

const tenant = (over) => ({
  restaurantId: 7, name: 'Cafe X', active: true, planCode: 'pro', planName: 'Pro',
  isTrial: false, planExpiresAt: null, daysUntilExpiry: 10, inGracePeriod: false, readOnly: false, ...over,
});

const pageOf = (content) => ({ data: { data: { content, page: { totalPages: 1, number: 0 } } } });

beforeEach(() => {
  vi.clearAllMocks();
  billingAPI.getPlans.mockResolvedValue({ data: { data: [{ code: 'pro', name: 'Pro' }] } });
});

// future flags quiet the v7 migration warnings react-router prints under test.
const Wrapper = ({ children }) => (
  <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{children}</MemoryRouter>
);
const renderPage = () => render(<PlatformConsole />, { wrapper: Wrapper });

describe('PlatformConsole', () => {
  it('lists tenants with status and expiry', async () => {
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant()]));
    renderPage();

    expect(await screen.findByText('Cafe X')).toBeInTheDocument();
    expect(screen.getByText('#7')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('10d left')).toBeInTheDocument();
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
    platformAPI.listTenants.mockResolvedValue(pageOf([tenant({ active: false })]));
    renderPage();
    await screen.findByText('Cafe X');

    expect(screen.getByText('Suspended')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Reactivate/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Suspend/ })).toBeNull();
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
