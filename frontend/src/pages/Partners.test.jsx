import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Partners from './Partners';
import { partnerAPI, restaurantAPI } from '../services/api';
import { copyToClipboard } from '../utils/passwordGenerator';

// Benign never-resolving defaults so a test that forgets to stub a call hangs on that call rather
// than leaking a rejected promise into an unrelated assertion.
vi.mock('../services/api', () => {
  const pending = () => vi.fn(() => new Promise(() => {}));
  return {
    partnerAPI: {
      getAll: pending(),
      create: pending(),
      rotateKey: pending(),
      setActive: pending(),
      grantRestaurant: pending(),
      revokeRestaurant: pending(),
      retryDeadLetters: pending(),
      setCustomerFee: pending(),
    },
    restaurantAPI: { getAll: pending() },
  };
});

vi.mock('../lib/errors', () => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
}));

vi.mock('../utils/passwordGenerator', () => ({
  copyToClipboard: vi.fn(() => Promise.resolve(true)),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, defaultValue) => (typeof defaultValue === 'string' ? defaultValue : key),
  }),
}));

const envelope = (data) => ({ data: { data } });

const PARTNER = {
  id: 7,
  name: 'Test Aggregator',
  slug: 'test-agg',
  apiKeyPrefix: 'elc_ab12',
  contactEmail: 'ops@aggregator.test',
  active: true,
  restaurants: [
    {
      restaurantId: 3,
      restaurantName: 'Partner Cafe',
      canReadMenu: true,
      canPushOrders: true,
      active: true,
      priceAdjustmentType: 'PERCENT',
      priceAdjustmentValue: 15,
      priceRounding: 500,
      priceRules: [],
    },
  ],
};

const RESTAURANTS = [
  { id: 3, name: 'Partner Cafe' },
  { id: 4, name: 'Other Cafe' },
];

describe('Partners', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    partnerAPI.getAll.mockResolvedValue(envelope([PARTNER]));
    restaurantAPI.getAll.mockResolvedValue(envelope(RESTAURANTS));
  });

  it('lists partners with their key prefix and granted venues', async () => {
    render(<Partners />);

    expect(await screen.findByText('Test Aggregator')).toBeInTheDocument();
    expect(screen.getByText('test-agg')).toBeInTheDocument();
    expect(screen.getByText(/elc_ab12/)).toBeInTheDocument();
    expect(screen.getByText('Partner Cafe')).toBeInTheDocument();
    expect(screen.getByText('menu + orders')).toBeInTheDocument();
  });

  it('shows the empty state when no partners exist', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([]));
    render(<Partners />);

    expect(await screen.findByText('No delivery partners yet')).toBeInTheDocument();
  });

  it('reveals the generated API key exactly once after creating a partner', async () => {
    partnerAPI.create.mockResolvedValue(envelope({
      partnerId: 8,
      name: 'New Aggregator',
      slug: 'new-agg',
      apiKey: 'elc_super_secret_key_value',
    }));

    render(<Partners />);
    fireEvent.click(await screen.findByText('Add Partner'));

    fireEvent.change(screen.getByPlaceholderText('e.g. Wolt, Yandex Eats'),
      { target: { value: 'New Aggregator' } });
    fireEvent.change(screen.getByPlaceholderText('lowercase-handle'),
      { target: { value: 'new-agg' } });
    fireEvent.click(screen.getByText('Save'));

    // The key is shown because this response is the only place it will ever exist.
    const revealed = await screen.findByTestId('revealed-api-key');
    expect(revealed).toHaveTextContent('elc_super_secret_key_value');

    expect(partnerAPI.create).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'New Aggregator', slug: 'new-agg' }));
  });

  it('refuses to submit without a name and slug', async () => {
    const { notifyWarning } = await import('../lib/errors');
    render(<Partners />);
    fireEvent.click(await screen.findByText('Add Partner'));
    fireEvent.click(screen.getByText('Save'));

    expect(notifyWarning).toHaveBeenCalled();
    expect(partnerAPI.create).not.toHaveBeenCalled();
  });

  it('copies the revealed key to the clipboard', async () => {
    partnerAPI.create.mockResolvedValue(envelope({
      partnerId: 8, name: 'New Aggregator', slug: 'new-agg', apiKey: 'elc_copy_me',
    }));

    render(<Partners />);
    fireEvent.click(await screen.findByText('Add Partner'));
    fireEvent.change(screen.getByPlaceholderText('e.g. Wolt, Yandex Eats'),
      { target: { value: 'New Aggregator' } });
    fireEvent.change(screen.getByPlaceholderText('lowercase-handle'),
      { target: { value: 'new-agg' } });
    fireEvent.click(screen.getByText('Save'));

    fireEvent.click(await screen.findByText('Copy'));

    await waitFor(() => expect(copyToClipboard).toHaveBeenCalledWith('elc_copy_me'));
    expect(await screen.findByText('Copied')).toBeInTheDocument();
  });

  it('toggles a partner active flag', async () => {
    partnerAPI.setActive.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    fireEvent.click(await screen.findByText('Active'));

    await waitFor(() => expect(partnerAPI.setActive).toHaveBeenCalledWith(7, false));
  });

  it('grants a venue read-only unless orders are explicitly allowed', async () => {
    partnerAPI.grantRestaurant.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    // Only the ungranted venue is offered.
    const select = await screen.findByLabelText('Grant a venue');
    fireEvent.change(select, { target: { value: '4' } });
    fireEvent.click(screen.getByText('Grant'));

    await waitFor(() => expect(partnerAPI.grantRestaurant).toHaveBeenCalledWith(
      7, 4, { canReadMenu: true, canPushOrders: false }));
  });

  it('grants order-push only when the operator ticks it', async () => {
    partnerAPI.grantRestaurant.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    fireEvent.change(await screen.findByLabelText('Grant a venue'), { target: { value: '4' } });
    fireEvent.click(screen.getByLabelText('Allow orders', { selector: 'input' }));
    fireEvent.click(screen.getByText('Grant'));

    await waitFor(() => expect(partnerAPI.grantRestaurant).toHaveBeenCalledWith(
      7, 4, { canReadMenu: true, canPushOrders: true }));
  });

  it('asks before revoking a venue, and does nothing if declined', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<Partners />);

    fireEvent.click(await screen.findByText('Revoke'));

    expect(confirmSpy).toHaveBeenCalled();
    expect(partnerAPI.revokeRestaurant).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('revokes a venue once confirmed', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    partnerAPI.revokeRestaurant.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    fireEvent.click(await screen.findByText('Revoke'));

    await waitFor(() => expect(partnerAPI.revokeRestaurant).toHaveBeenCalledWith(7, 3));
    confirmSpy.mockRestore();
  });

  it('warns before rotating, because the old key dies immediately', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    partnerAPI.rotateKey.mockResolvedValue(envelope({
      partnerId: 7, name: 'Test Aggregator', slug: 'test-agg', apiKey: 'elc_rotated_key',
    }));
    render(<Partners />);

    fireEvent.click(await screen.findByText('Rotate key'));

    expect(confirmSpy).toHaveBeenCalled();
    expect(await screen.findByTestId('revealed-api-key')).toHaveTextContent('elc_rotated_key');
    confirmSpy.mockRestore();
  });

  it('shows the venue markup as a summary chip', async () => {
    render(<Partners />);

    expect(await screen.findByText('+15%')).toBeInTheDocument();
  });

  it('saves a markup without altering the venue capabilities', async () => {
    partnerAPI.grantRestaurant.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    fireEvent.click(await screen.findByText('+15%'));
    fireEvent.change(screen.getByLabelText('Markup'), { target: { value: '20' } });
    fireEvent.click(screen.getByText('Save'));

    // Capabilities ride along unchanged: editing a price must never quietly widen or narrow what the
    // partner is allowed to do at this venue.
    await waitFor(() => expect(partnerAPI.grantRestaurant).toHaveBeenCalledWith(7, 3, {
      canReadMenu: true,
      canPushOrders: true,
      priceAdjustmentType: 'PERCENT',
      priceAdjustmentValue: 20,
      priceRounding: 500,
    }));
  });

  it('clears the markup value when switching back to base price', async () => {
    partnerAPI.grantRestaurant.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    fireEvent.click(await screen.findByText('+15%'));
    fireEvent.change(screen.getByLabelText('Markup type'), { target: { value: 'NONE' } });
    fireEvent.click(screen.getByText('Save'));

    // A leftover value behind NONE would reappear the moment someone flipped the type back.
    await waitFor(() => expect(partnerAPI.grantRestaurant).toHaveBeenCalledWith(7, 3,
      expect.objectContaining({ priceAdjustmentType: 'NONE', priceAdjustmentValue: 0 })));
  });

  it('renders a base-price partner without a markup chip', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([{
      ...PARTNER,
      restaurants: [{ ...PARTNER.restaurants[0], priceAdjustmentType: 'NONE', priceAdjustmentValue: 0 }],
    }]));
    render(<Partners />);

    expect(await screen.findByText('base price')).toBeInTheDocument();
  });

  it('surfaces undelivered messages and requeues them on click', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([{ ...PARTNER, deadLetteredEvents: 3 }]));
    partnerAPI.retryDeadLetters.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    // An undelivered message means the partner stopped hearing something we promised to tell them;
    // nobody goes looking for a problem they have not been shown.
    fireEvent.click(await screen.findByText('{{count}} undelivered — retry'));

    await waitFor(() => expect(partnerAPI.retryDeadLetters).toHaveBeenCalledWith(7));
  });

  it('shows no outbox warning when nothing is stuck', async () => {
    render(<Partners />);

    await screen.findByText('Test Aggregator');
    expect(screen.queryByText(/undelivered/)).not.toBeInTheDocument();
  });

  it('shows what the customer really pays once a partner fee is known', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([{ ...PARTNER, customerFeePercent: 8 }]));
    render(<Partners />);

    fireEvent.click(await screen.findByText('+15%'));

    // The whole point: an owner setting +15% against a 30 000 dish is choosing 34 500 published —
    // and, once the partner's own 8% lands on top, about 37 260 for the person eating it.
    expect(await screen.findByText('34,500')).toBeInTheDocument();
    expect(screen.getByText('37,260')).toBeInTheDocument();
  });

  it('names the fee and says the venue is still paid on the published price', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([{ ...PARTNER, customerFeePercent: 8 }]));
    render(<Partners />);

    fireEvent.click(await screen.findByText('+15%'));

    // A second, larger number beside an owner's own markup reads as "they are marking my price up"
    // unless something says otherwise. It is the partner's own line on the customer's bill, and the
    // venue is paid on what it published — which is the sentence an owner will ask to have repeated.
    expect(await screen.findByText(/service fee/)).toBeInTheDocument();
    expect(screen.getByText(/you are paid on that price/)).toBeInTheDocument();
  });

  it('shows the recorded service fee on the partner, and offers to change it', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([{ ...PARTNER, customerFeePercent: 8 }]));
    partnerAPI.setCustomerFee.mockResolvedValue(envelope(PARTNER));
    render(<Partners />);

    fireEvent.click(await screen.findByText(/Service fee \{\{fee\}\}%/));
    fireEvent.change(screen.getByLabelText('Service fee percent'), { target: { value: '9.5' } });
    fireEvent.click(screen.getAllByText('Save')[0]);

    await waitFor(() => expect(partnerAPI.setCustomerFee).toHaveBeenCalledWith(7, 9.5));
  });

  it('says the fee is unrecorded rather than showing a zero', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([{ ...PARTNER, customerFeePercent: 0 }]));
    render(<Partners />);

    // "0%" would read as a partner who charges nothing. Nobody has told us, which is a different
    // thing and the reason the markup editor shows no customer price at all.
    expect(await screen.findByText(/none recorded/)).toBeInTheDocument();
  });

  it('shows no second number when the partner adds nothing we know of', async () => {
    partnerAPI.getAll.mockResolvedValue(envelope([{ ...PARTNER, customerFeePercent: 0 }]));
    render(<Partners />);

    fireEvent.click(await screen.findByText('+15%'));

    // Zero means "none, or nobody has told us". Inventing a customer price from that would be
    // arithmetic on a number we do not have.
    expect(await screen.findByText('34,500')).toBeInTheDocument();
    expect(screen.queryByText(/their customer pays/)).not.toBeInTheDocument();
  });
});
