import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { PartnerCancelledNotice, OwedTicketsPanel } from './SelfServiceOrders';
import { orderAPI } from '../services/api';

vi.mock('../services/api', () => ({
  orderAPI: {
    getSelfServiceOrders: vi.fn(),
    getExternalOrders: vi.fn(),
    getOwedTickets: vi.fn(() => new Promise(() => {})),
  },
  restaurantAPI: { getAll: vi.fn() },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key, fallback) => fallback || key }),
}));

const t = (key, fallback) => fallback || key;

describe('PartnerCancelledNotice', () => {
  it('tells the venue no courier is coming, and that they are owed for the ticket', () => {
    // The order still reads PREPARING and always will — the aggregator cancelled on their side and
    // we refused, correctly. Without this line the meal is cooked and waits under a lamp for nobody.
    render(<PartnerCancelledNotice order={{ partnerCancelRefusedAt: '2026-09-19T12:00:00Z' }} t={t} />);

    const notice = screen.getByTestId('partner-cancelled-notice');
    expect(notice.textContent).toMatch(/no courier is coming/i);
    expect(notice.textContent).toMatch(/owed for this ticket/i);
  });

  it('carries the partner’s own reason when they gave one', () => {
    render(
      <PartnerCancelledNotice
        order={{
          partnerCancelRefusedAt: '2026-09-19T12:00:00Z',
          partnerCancelRefusedReason: 'Customer unreachable',
        }}
        t={t}
      />,
    );

    expect(screen.getByTestId('partner-cancelled-notice').textContent)
      .toMatch(/Customer unreachable/);
  });

  it('says nothing about an ordinary order', () => {
    // Almost every order is this one. A warning that appears on all of them is a warning nobody
    // reads by the end of a shift.
    const { container } = render(<PartnerCancelledNotice order={{ status: 'PREPARING' }} t={t} />);

    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId('partner-cancelled-notice')).toBeNull();
  });
});

const envelope = (data) => ({ data: { data } });

const OWED = {
  restaurantId: 1,
  ticketCount: 2,
  foodValueTotal: 68000,
  byPartner: [{ partnerName: 'ZBR', ticketCount: 2, foodValueTotal: 68000 }],
  tickets: [
    {
      orderId: 3922, orderNumber: 'ORD-20260920-0007', partnerName: 'ZBR',
      externalOrderId: '88213', refusedAt: '2026-09-20T09:14:00Z',
      stage: 'PREPARING', reason: 'Customer unreachable', foodValue: 38000,
    },
    {
      orderId: 3930, orderNumber: 'ORD-20260920-0011', partnerName: 'ZBR',
      externalOrderId: '88240', refusedAt: '2026-09-20T12:02:00Z',
      stage: 'READY', reason: null, foodValue: 30000,
    },
  ],
};

describe('OwedTicketsPanel', () => {
  beforeEach(() => vi.clearAllMocks());

  it('gives the venue its own total, and the tickets behind it', async () => {
    orderAPI.getOwedTickets.mockResolvedValue(envelope(OWED));
    render(<OwedTicketsPanel restaurantId="1" t={t} />);

    // The number a restaurant should be able to read for itself rather than be told by the partner.
    expect(await screen.findByText('68,000')).toBeInTheDocument();
    expect(screen.getByText('ORD-20260920-0007')).toBeInTheDocument();
    expect(screen.getByText(/88240/)).toBeInTheDocument();
  });

  it('says plainly that the total is not a debt anyone has agreed to', async () => {
    orderAPI.getOwedTickets.mockResolvedValue(envelope(OWED));
    render(<OwedTicketsPanel restaurantId="1" t={t} />);

    // A money figure on a venue's screen reads as a receivable unless it says otherwise, and who
    // bears these is still open between the two companies.
    expect(await screen.findByText(/not an amount anyone has yet agreed to pay/))
      .toBeInTheDocument();
  });

  it('stays silent when nothing is owed', async () => {
    orderAPI.getOwedTickets.mockResolvedValue(envelope({ ticketCount: 0, tickets: [] }));
    const { container } = render(<OwedTicketsPanel restaurantId="1" t={t} />);

    // Which is almost always. A card that is permanently zero is one people stop reading.
    await waitFor(() => expect(orderAPI.getOwedTickets).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('does not ask, or show, when no single venue is selected', async () => {
    render(<OwedTicketsPanel restaurantId="all" t={t} />);

    // The figure is per venue; summing two restaurants would produce a number nobody can settle.
    expect(orderAPI.getOwedTickets).not.toHaveBeenCalled();
    expect(screen.queryByTestId('owed-tickets-panel')).toBeNull();
  });

  it('a failed lookup does not take the orders page with it', async () => {
    orderAPI.getOwedTickets.mockRejectedValue(new Error('gateway'));
    const { container } = render(<OwedTicketsPanel restaurantId="1" t={t} />);

    await waitFor(() => expect(orderAPI.getOwedTickets).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
