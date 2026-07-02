import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import SuspensionGate from './SuspensionGate';
import { useSubscriptionStore } from '../store/subscriptionStore';
import { useAuthStore } from '../store/authStore';

// English fallbacks so assertions read against real wording.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key, dflt) => (typeof dflt === 'string' ? dflt : key) }),
}));

const setState = (suspended, isAuthenticated) =>
  act(() => {
    useSubscriptionStore.setState({ suspended });
    useAuthStore.setState({ isAuthenticated });
  });

afterEach(() => {
  setState(false, false);
  vi.restoreAllMocks();
});

// The gate reads the current route (the subscription page is exempt), so it needs a router.
const renderAt = (path = '/') => render(
  <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <SuspensionGate />
    <Routes>
      <Route path="/subscription" element={<div>subscription page</div>} />
      <Route path="*" element={<div>app</div>} />
    </Routes>
  </MemoryRouter>,
);

describe('SuspensionGate', () => {
  it('renders nothing when not suspended', () => {
    setState(false, true);
    renderAt();
    expect(screen.queryByRole('alertdialog')).toBeNull();
  });

  it('renders nothing when suspended but not authenticated (e.g. the login page)', () => {
    setState(true, false);
    renderAt();
    expect(screen.queryByRole('alertdialog')).toBeNull();
  });

  it('takes over with a message and actions when suspended and authenticated', () => {
    setState(true, true);
    renderAt();
    expect(screen.getByText('Access suspended')).toBeInTheDocument();
    expect(screen.getByText(/has been suspended/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View subscription' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
  });

  it('exempts the subscription page, mirroring the backend billing allowlist', () => {
    setState(true, true);
    renderAt('/subscription');
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(screen.getByText('subscription page')).toBeInTheDocument();
  });

  it('View subscription navigates to the exempt page, clearing the overlay', () => {
    setState(true, true);
    renderAt('/');
    fireEvent.click(screen.getByRole('button', { name: 'View subscription' }));
    expect(screen.getByText('subscription page')).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).toBeNull();
  });

  it('Retry clears the flag and reloads for a fresh server answer', () => {
    setState(true, true);
    // jsdom's location.reload is not implemented — swap in a spy-carrying stand-in.
    const original = window.location;
    delete window.location;
    window.location = { ...original, reload: vi.fn() };
    try {
      renderAt();
      fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
      expect(useSubscriptionStore.getState().suspended).toBe(false);
      expect(window.location.reload).toHaveBeenCalled();
    } finally {
      window.location = original;
    }
  });

  it('Log out clears the flag, logs out, and lands on the admin login', () => {
    setState(true, true);
    const logout = vi.fn();
    act(() => useAuthStore.setState({ logout }));
    const original = window.location;
    delete window.location;
    window.location = { ...original, href: '/' };
    try {
      renderAt();
      fireEvent.click(screen.getByRole('button', { name: 'Log out' }));
      expect(useSubscriptionStore.getState().suspended).toBe(false);
      expect(logout).toHaveBeenCalled();
      expect(window.location.href).toBe('/admin/login');
    } finally {
      window.location = original;
    }
  });
});
