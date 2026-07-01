import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
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

afterEach(() => setState(false, false));

describe('SuspensionGate', () => {
  it('renders nothing when not suspended', () => {
    setState(false, true);
    const { container } = render(<SuspensionGate />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when suspended but not authenticated (e.g. the login page)', () => {
    setState(true, false);
    const { container } = render(<SuspensionGate />);
    expect(container).toBeEmptyDOMElement();
  });

  it('takes over with a message and actions when suspended and authenticated', () => {
    setState(true, true);
    render(<SuspensionGate />);
    expect(screen.getByText('Access suspended')).toBeInTheDocument();
    expect(screen.getByText(/has been suspended/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
  });
});
