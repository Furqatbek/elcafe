import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import InstagramMarketing from './InstagramMarketing';
import { instagramAPI } from '../services/api';
import { notifySuccess, notifyWarning } from '../lib/errors';

// Pins the subscriber search/pagination contract:
//   * paginating a filtered list keeps the query (hits searchSubscribers, never the unfiltered
//     getSubscribers) — the "pagination silently discards the active search query" bug; and
//   * clearing the box returns to the unfiltered list instead of staying stuck on the last committed
//     query — setAppliedQuery is async, so the clear must load the unfiltered list explicitly.
// Break either behaviour (drop the query from loadSubscribers, or stop resetting it on clear) and the
// matching test goes red.

// t(key) and t(key, 'default') return a string; t(key, { interpolation }) must NOT return the
// options object (React can't render it) — fall back to the key.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k, o) => (typeof o === 'string' ? o : k) }),
}));
vi.mock('../lib/errors', () => ({
  notifyError: vi.fn(), notifySuccess: vi.fn(), notifyWarning: vi.fn(),
}));
vi.mock('../services/api', () => ({
  instagramAPI: {
    getSubscribers: vi.fn(),
    searchSubscribers: vi.fn(),
    sendDm: vi.fn(),
  },
}));

const pageOf = (rows, totalPages) => ({ data: { content: rows, totalPages } });
const row = (id, phone) => ({
  id,
  igsid: `ig-${id}`,
  username: `user${id}`,
  displayName: `User ${id}`,
  phone,
  conversationState: 'REGISTERED',
  isActive: true,
  isBlocked: false,
  subscribedAt: '2026-01-01T00:00:00Z',
});

describe('InstagramMarketing — subscriber search + pagination', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Unfiltered list has multiple pages; a "998" search returns its own multi-page result set.
    instagramAPI.getSubscribers.mockResolvedValue(pageOf([row(1, '111')], 3));
    instagramAPI.searchSubscribers.mockResolvedValue(pageOf([row(2, '998')], 2));
  });

  const search = async (term) => {
    fireEvent.change(
      screen.getByPlaceholderText('instagram.subscribers.searchPlaceholder'),
      { target: { value: term } },
    );
    fireEvent.click(screen.getByText('common.search'));
  };

  it('paginating a filtered list keeps the query, not the unfiltered endpoint', async () => {
    const { container } = render(<InstagramMarketing />);
    await screen.findByText('User 1');                 // mount loaded the unfiltered first page

    await search('998');
    await screen.findByText('User 2');
    expect(instagramAPI.searchSubscribers).toHaveBeenCalledWith('998', { page: 0, size: 15 });

    // Click "next" — it must page the SEARCH, not fall back to all subscribers.
    const nextBtn = container.querySelector('.lucide-chevron-right').closest('button');
    fireEvent.click(nextBtn);

    await waitFor(() =>
      expect(instagramAPI.searchSubscribers).toHaveBeenCalledWith('998', { page: 1, size: 15 }));
    // The unfiltered endpoint was hit once (on mount) and never by pagination.
    expect(instagramAPI.getSubscribers).toHaveBeenCalledTimes(1);
  });

  it('clearing the search returns to the unfiltered list, not the stuck query', async () => {
    render(<InstagramMarketing />);
    await screen.findByText('User 1');

    await search('998');
    await screen.findByText('User 2');
    expect(instagramAPI.searchSubscribers).toHaveBeenCalledTimes(1);

    // Clear (X): the committed query must be dropped and the unfiltered list reloaded.
    fireEvent.click(screen.getByText('common.clear'));

    await waitFor(() =>
      expect(instagramAPI.getSubscribers).toHaveBeenCalledWith({ page: 0, size: 15 }));
    // getSubscribers hit twice total (mount + clear); the clear did NOT re-run the search.
    expect(instagramAPI.getSubscribers).toHaveBeenCalledTimes(2);
    expect(instagramAPI.searchSubscribers).toHaveBeenCalledTimes(1);
  });
});

// The DM endpoint answers 200 {"sent": false} when there is no active config or the circuit breaker
// is open. A green "sent" toast there would tell the operator a rejected message went out and discard
// the text they typed. These pin that the UI branches on data.sent — success only when truly sent.
describe('InstagramMarketing — DM send outcome honours data.sent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    instagramAPI.getSubscribers.mockResolvedValue(pageOf([row(1, '111')], 1));
  });

  const openDmAndSend = async (text) => {
    fireEvent.click(screen.getByTitle('instagram.dm.sendButton'));   // row action opens the dialog
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByPlaceholderText('instagram.dm.messagePlaceholder'),
      { target: { value: text } });
    fireEvent.click(within(dialog).getByRole('button', { name: /instagram\.dm\.sendButton/ }));
    return dialog;
  };

  it('a DM the backend reports as NOT sent warns and keeps the dialog + text', async () => {
    instagramAPI.sendDm.mockResolvedValue({ data: { sent: false, reason: 'CIRCUIT_OPEN', retryable: true } });
    render(<InstagramMarketing />);
    await screen.findByText('User 1');

    await openDmAndSend('please deliver');

    await waitFor(() => expect(notifyWarning).toHaveBeenCalled());
    expect(notifySuccess).not.toHaveBeenCalled();          // no green "sent" for a rejected message
    expect(screen.getByRole('dialog')).toBeInTheDocument(); // dialog stays open…
    expect(screen.getByDisplayValue('please deliver')).toBeInTheDocument(); // …text not discarded
  });

  it('a DM the backend accepts shows success and closes the dialog', async () => {
    instagramAPI.sendDm.mockResolvedValue({ data: { sent: true } });
    render(<InstagramMarketing />);
    await screen.findByText('User 1');

    await openDmAndSend('hello');

    await waitFor(() => expect(notifySuccess).toHaveBeenCalled());
    expect(notifyWarning).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
