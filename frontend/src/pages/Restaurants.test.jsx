import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import Restaurants from './Restaurants';
import { restaurantAPI } from '../services/api';

// Pins the EH-3 conversion: the platform restaurant list owns loading/error/empty via
// useApiCall + <QueryState>. A failed load shows an error + retry (not a misleading empty grid),
// the retry recovers, and a genuinely empty result shows the empty message — not an error.

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (k, d) => d || k }) }));
vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));
vi.mock('../services/api', () => ({
  restaurantAPI: { getAll: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
}));
vi.mock('../lib/errors', () => ({
  getAppError: (e) => ({ message: e?.message || 'Load failed' }),
  errorMessage: (e) => e?.message || 'err',
  notifyError: vi.fn(),
}));

describe('Restaurants page — load states', () => {
  beforeEach(() => vi.clearAllMocks());

  it('failed load → error + retry, then retry recovers with the list', async () => {
    restaurantAPI.getAll
      .mockRejectedValueOnce(new Error('Boom'))
      .mockResolvedValueOnce({ data: { data: { content: [
        { id: 1, name: 'Kofe House', address: 'A', city: 'B', state: 'C', zipCode: 'D' },
      ] } } });

    render(<Restaurants />);

    const retry = await screen.findByText('Try again');   // error state, not a blank grid
    expect(screen.getByText('Boom')).toBeInTheDocument();

    fireEvent.click(retry);

    expect(await screen.findByText('Kofe House')).toBeInTheDocument();
  });

  it('empty result → empty message, not an error', async () => {
    restaurantAPI.getAll.mockResolvedValueOnce({ data: { data: { content: [] } } });

    render(<Restaurants />);

    expect(await screen.findByText('common.noData')).toBeInTheDocument();
    expect(screen.queryByText('Try again')).not.toBeInTheDocument();
  });
});
