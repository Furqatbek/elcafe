import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PartnerCancelledNotice } from './SelfServiceOrders';

// The page pulls the API layer in at import time; nothing here calls it.
vi.mock('../services/api', () => ({
  orderAPI: { getSelfServiceOrders: vi.fn(), getExternalOrders: vi.fn() },
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
