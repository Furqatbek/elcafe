import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import StaffRegistrations from './StaffRegistrations';
import { staffReportAPI } from '../services/api';

// The page exists to make one judgement legible: whether an employee's till registrations are real
// guests or typo'd phone numbers. So the tests are about the reach column and when it raises a flag —
// a warning next to everybody is the same as no warning at all.

vi.mock('react-i18next', async () => {
  const en = (await import('../i18n/locales/en.json')).default;
  const lookup = (key) =>
    key.split('.').reduce((acc, part) => (acc == null ? undefined : acc[part]), en);
  return {
    useTranslation: () => ({
      t: (k, d) => {
        const resolved = lookup(k);
        return typeof resolved === 'string' ? resolved : typeof d === 'string' ? d : k;
      },
    }),
  };
});
vi.mock('../lib/errors', () => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
}));
vi.mock('../services/api', () => ({
  staffReportAPI: { registrations: vi.fn() },
}));

const row = (overrides) => ({
  userId: 10,
  staffName: 'Aziza Yusupova',
  role: 'CASHIER',
  registrations: 10,
  reached: 9,
  reachRatePercent: 90,
  bonusGranted: 100000,
  daily: [
    { date: '2026-07-20', registrations: 2 },
    { date: '2026-07-22', registrations: 8 },
  ],
  ...overrides,
});

const report = (staff, totals = {}) => ({
  data: {
    data: {
      from: '2026-07-01',
      to: '2026-07-30',
      totalRegistrations: staff.reduce((n, s) => n + s.registrations, 0),
      totalReached: staff.reduce((n, s) => n + s.reached, 0),
      totalBonusGranted: 100000,
      staff,
      ...totals,
    },
  },
});

describe('StaffRegistrations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    staffReportAPI.registrations.mockResolvedValue(report([row()]));
  });

  it('shows each employee with their registrations, reach and bonus', async () => {
    render(<StaffRegistrations />);

    const line = await screen.findByTestId('staff-row-10');
    expect(within(line).getByText('Aziza Yusupova')).toBeInTheDocument();
    expect(within(line).getByText('CASHIER')).toBeInTheDocument();
    expect(within(line).getByText('9/10 (90%)')).toBeInTheDocument();
  });

  /** The whole point of the report: forty signups nobody can contact should not read as forty signups. */
  it('flags an employee whose registered numbers mostly cannot be reached', async () => {
    staffReportAPI.registrations.mockResolvedValue(
      report([row({ registrations: 40, reached: 4, reachRatePercent: 10 })])
    );
    render(<StaffRegistrations />);

    const reach = await screen.findByTestId('reach-10');
    expect(reach).toHaveAttribute('data-concerning', 'true');
  });

  it('leaves a healthy reach rate unflagged', async () => {
    render(<StaffRegistrations />);

    const reach = await screen.findByTestId('reach-10');
    expect(reach).toHaveAttribute('data-concerning', 'false');
  });

  /**
   * One-out-of-two is 50% and means nothing. Flagging it would put a warning beside half the staff on
   * a quiet week and teach everyone to ignore the colour.
   */
  it('does not flag a tiny sample, however bad the ratio looks', async () => {
    staffReportAPI.registrations.mockResolvedValue(
      report([row({ registrations: 2, reached: 0, reachRatePercent: 0 })])
    );
    render(<StaffRegistrations />);

    const reach = await screen.findByTestId('reach-10');
    expect(reach).toHaveAttribute('data-concerning', 'false');
    expect(within(reach).getByText(/0\/2/)).toBeInTheDocument();
  });

  it('asks for the last 30 days by default', async () => {
    render(<StaffRegistrations />);

    await waitFor(() => expect(staffReportAPI.registrations).toHaveBeenCalled());
    const { from, to } = staffReportAPI.registrations.mock.calls[0][0];
    const days = (new Date(to) - new Date(from)) / 86400000;
    expect(days).toBe(29); // 30 days inclusive of both ends
  });

  it('a period with no till registrations says so instead of rendering an empty table', async () => {
    staffReportAPI.registrations.mockResolvedValue(report([]));
    render(<StaffRegistrations />);

    await screen.findByText('No guests were registered at the till in this period.');
    expect(screen.queryByTestId('staff-row-10')).not.toBeInTheDocument();
  });

  /** A row for a departed employee is exactly the row an audit needs; it must not be dropped. */
  it('renders a departed employee identified by id', async () => {
    staffReportAPI.registrations.mockResolvedValue(
      report([row({ userId: 99, staffName: '#99', role: null })])
    );
    render(<StaffRegistrations />);

    const line = await screen.findByTestId('staff-row-99');
    expect(within(line).getByText('#99')).toBeInTheDocument();
  });
});
