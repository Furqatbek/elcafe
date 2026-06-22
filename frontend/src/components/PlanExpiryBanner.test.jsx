import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PlanExpiryBanner from './PlanExpiryBanner';
import { usePlan } from '../hooks/usePlan';

// Render the i18n fallback strings (the component passes English defaults) and interpolate {{n}},
// so assertions read against real wording without loading the translation bundles.
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

vi.mock('../hooks/usePlan', () => ({ usePlan: vi.fn() }));

// future flags just quiet the v7 migration warnings react-router prints under test.
const Wrapper = ({ children }) => (
  <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{children}</MemoryRouter>
);
const renderBanner = () => render(<PlanExpiryBanner />, { wrapper: Wrapper });

// Defaults for a healthy plan; each test overrides only what it exercises.
const planState = (overrides) =>
  usePlan.mockReturnValue({
    planCode: 'pro',
    daysUntilExpiry: null,
    isTrial: false,
    inGracePeriod: false,
    isReadOnly: false,
    ...overrides,
  });

beforeEach(() => {
  usePlan.mockReset();
});

describe('PlanExpiryBanner', () => {
  it('renders nothing when there is no plan', () => {
    planState({ planCode: null });
    const { container } = renderBanner();
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when expiry is comfortably far out', () => {
    planState({ daysUntilExpiry: 30 });
    const { container } = renderBanner();
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the full trial countdown even outside the final week', () => {
    planState({ isTrial: true, daysUntilExpiry: 14 });
    renderBanner();
    expect(screen.getByText('Your trial ends in 14 day(s).')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Renew' })).toHaveAttribute('href', '/subscription');
  });

  it('warns a paid plan approaching expiry', () => {
    planState({ daysUntilExpiry: 3 });
    renderBanner();
    expect(screen.getByText('Your plan expires in 3 day(s).')).toBeInTheDocument();
  });

  it('counts down the grace window remaining', () => {
    // 1 day past expiry, still in grace: GRACE_DAYS(3) + (-1) = 2 days left.
    planState({ daysUntilExpiry: -1, inGracePeriod: true });
    renderBanner();
    expect(screen.getByText(/Grace period/)).toHaveTextContent('2 day(s) left');
  });

  it('shows the expired read-only message for a paid plan', () => {
    planState({ daysUntilExpiry: -5, isReadOnly: true });
    renderBanner();
    expect(screen.getByText('Your plan has expired. The app is read-only — renew to resume.')).toBeInTheDocument();
  });

  it('shows the trial-specific read-only message', () => {
    planState({ daysUntilExpiry: -5, isTrial: true, isReadOnly: true });
    renderBanner();
    expect(screen.getByText('Your trial has ended. The app is read-only — renew to resume.')).toBeInTheDocument();
  });
});
