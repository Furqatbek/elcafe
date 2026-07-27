import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import CustomerProfile from './CustomerProfile';
import { customerAPI } from '../services/api';

// Covers what this page must never get wrong on the surface: showing a guest's data only once it has
// actually arrived, offering a way out when it hasn't, and asking for the right customer and the newest
// window. The cursor mechanics behind "load older messages" are pinned on the backend — see the note at
// the bottom of this file for why they cannot be driven from here.

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k, d) => (typeof d === 'string' ? d : k) }),
}));
vi.mock('../lib/errors', () => ({
  notifyError: vi.fn(), notifySuccess: vi.fn(), notifyWarning: vi.fn(),
}));
vi.mock('react-router-dom', () => ({
  useParams: () => ({ customerId: '7' }),
  useNavigate: () => vi.fn(),
}));
vi.mock('../services/api', () => ({
  customerAPI: {
    getProfile: vi.fn(),
    getTimeline: vi.fn(),
    addPreference: vi.fn(),
    deletePreference: vi.fn(),
  },
}));

const profile = {
  id: 7,
  firstName: 'Dilnoza',
  lastName: 'Karimova',
  phone: '+998901112233',
  preferences: [
    { id: 1, preferenceType: 'ALLERGY', value: 'walnuts' },
    { id: 2, preferenceType: 'LIKE', value: 'choy' },
  ],
  purchases: { orderCount: 4, lifetimeSpend: 120000, averageOrderValue: 30000, topItems: [] },
  loyalty: { currentBalance: 5000, tierName: 'Gold' },
};

const entry = (text, timestamp, channel = 'INSTAGRAM', direction = 'OUT') =>
  ({ text, timestamp, channel, direction });

describe('CustomerProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    customerAPI.getProfile.mockResolvedValue({ data: { data: profile } });
    customerAPI.getTimeline.mockResolvedValue({ data: { data: { entries: [], hasMore: false } } });
  });

  it('shows the guest once loaded, with their preferences', async () => {
    render(<CustomerProfile />);

    await screen.findByText('Dilnoza Karimova');
    expect(screen.getByText('walnuts')).toBeInTheDocument();
    expect(screen.getByText('choy')).toBeInTheDocument();
  });

  it('a failed load offers a way back instead of rendering an empty profile', async () => {
    customerAPI.getProfile.mockRejectedValue(new Error('boom'));
    render(<CustomerProfile />);

    await screen.findByText('Back to customers');
    expect(screen.queryByText('Dilnoza Karimova')).not.toBeInTheDocument();
  });

  it('the first timeline request asks for the newest window, with no cursor', async () => {
    render(<CustomerProfile />);

    await waitFor(() => expect(customerAPI.getTimeline).toHaveBeenCalled());
    const [, params] = customerAPI.getTimeline.mock.calls[0];
    expect(params.before).toBeUndefined();
  });

  it('the profile request is scoped to the customer in the route', async () => {
    render(<CustomerProfile />);

    await waitFor(() => expect(customerAPI.getProfile).toHaveBeenCalledWith('7'));
    expect(customerAPI.getTimeline).toHaveBeenCalledWith('7', expect.objectContaining({ limit: 20 }));
  });

  // NOTE: the load-more path (cursor threading and appending rather than replacing) is NOT covered
  // here. It lives inside the Conversations tab, and Radix only mounts the active tab's content while
  // these tabs cannot be switched with fireEvent — so the button is not in the DOM to click. The
  // equivalent guarantees are pinned on the backend instead, where the cursor is actually implemented:
  // see CustomerProfileServiceTest's cursorReturnsOnlyOlderMessages and reportsHasMoreAndCursor.
});
