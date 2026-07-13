import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import LinkedItems from './LinkedItems';
import { linkedItemAPI } from '../services/api';

// Pins the EH-3 conversion: the linked-items load owns loading/error/empty via useApiCall +
// <QueryState>. A failed load shows an error + retry (Retry re-fetches) instead of the old
// "no linked items" empty that hid failures; a genuinely empty result shows the empty message.
// (Assertions stay on the error/empty states — the success view renders a Radix Select we don't
// need to exercise here.)

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (k, d) => d || k }) }));
vi.mock('react-router-dom', () => ({
  useParams: () => ({ productId: '5' }),
  useNavigate: () => vi.fn(),
}));
vi.mock('../services/api', () => ({ linkedItemAPI: { getLinkedItems: vi.fn() } }));
vi.mock('../lib/errors', () => ({
  getAppError: (e) => ({ message: e?.message || 'Load failed' }),
  errorMessage: (e) => e?.message || 'err',
}));

describe('LinkedItems page — load states', () => {
  beforeEach(() => vi.clearAllMocks());

  it('failed load → error + retry, and Retry re-fetches', async () => {
    linkedItemAPI.getLinkedItems.mockRejectedValue(new Error('Boom'));

    render(<LinkedItems />);

    const retry = await screen.findByText('Try again');
    expect(screen.getByText('Boom')).toBeInTheDocument();
    expect(linkedItemAPI.getLinkedItems).toHaveBeenCalledTimes(1);

    fireEvent.click(retry);

    await waitFor(() => expect(linkedItemAPI.getLinkedItems).toHaveBeenCalledTimes(2));
  });

  it('empty result → empty message, not an error', async () => {
    linkedItemAPI.getLinkedItems.mockResolvedValueOnce({ data: { data: [] } });

    render(<LinkedItems />);

    expect(await screen.findByText('No linked items found for this product')).toBeInTheDocument();
    expect(screen.queryByText('Try again')).not.toBeInTheDocument();
  });
});
