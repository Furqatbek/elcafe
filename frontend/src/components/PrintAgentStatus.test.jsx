import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import PrintAgentStatus from './PrintAgentStatus';
import { printerAPI } from '../services/api';

vi.mock('../services/api', () => ({
  printerAPI: { getPrintAgentStatus: vi.fn(() => new Promise(() => {})) },
}));

const t = (key, fallback, vars) => {
  const text = typeof fallback === 'string' ? fallback : key;
  if (!vars) return text;
  return Object.entries(vars).reduce((out, [k, v]) => out.replace(`{{${k}}}`, v), text);
};

const envelope = (data) => ({ data: { data } });

describe('PrintAgentStatus', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => vi.useRealTimers());

  it('says tickets are printing when the agent is online', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'ONLINE', agentId: 'agent-abc', secondsSinceLastSeen: 4,
      queuedJobs: 0, deadLetterJobs: 0, backlogAfterMinutes: 5,
    }));
    render(<PrintAgentStatus restaurantId={3} t={t} />);

    expect(await screen.findByText(/print agent connected/i)).toBeInTheDocument();
  });

  it('sends someone to the computer when the agent has gone quiet', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'STALE', agentId: 'agent-abc', secondsSinceLastSeen: 600,
      queuedJobs: 3, oldestQueuedMinutes: 9, deadLetterJobs: 0, backlogAfterMinutes: 5,
    }));
    render(<PrintAgentStatus restaurantId={3} t={t} />);

    // The kitchen machine is off or offline. Naming the agent and the wait is what lets somebody
    // walk to the right computer instead of starting with the printer.
    expect(await screen.findByText(/gone quiet/i)).toBeInTheDocument();
    expect(screen.getByText(/kitchen computer/i)).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('sends someone to the printer when the agent is fine but nothing prints', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'BACKLOG', agentId: 'agent-abc', secondsSinceLastSeen: 3,
      queuedJobs: 4, oldestQueuedMinutes: 20, deadLetterJobs: 0, backlogAfterMinutes: 5,
    }));
    render(<PrintAgentStatus restaurantId={3} t={t} />);

    // The distinction that earns this component its four states: a healthy agent in front of a jammed
    // printer. One red light for both would send the wrong person to the wrong end of the room.
    expect(await screen.findByText(/not printing/i)).toBeInTheDocument();
    expect(screen.getByText(/paper, power, or a jam/i)).toBeInTheDocument();
  });

  it('reassures that nothing is lost while no agent is connected', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'OFFLINE', queuedJobs: 7, oldestQueuedMinutes: 42,
      deadLetterJobs: 0, backlogAfterMinutes: 5,
    }));
    render(<PrintAgentStatus restaurantId={3} t={t} />);

    // Tickets do queue and do flush on reconnect, and a venue that does not know that will start
    // writing orders out by hand.
    expect(await screen.findByText(/no print agent connected/i)).toBeInTheDocument();
    expect(screen.getByText(/will print as soon as an agent connects/i)).toBeInTheDocument();
  });

  it('counts tickets we have given up on separately', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'ONLINE', agentId: 'agent-abc', secondsSinceLastSeen: 2,
      queuedJobs: 0, deadLetterJobs: 2, backlogAfterMinutes: 5,
    }));
    render(<PrintAgentStatus restaurantId={3} t={t} />);

    // These will never print on their own. Shown even when the state is ONLINE, because the agent
    // being healthy now says nothing about the tickets it already failed.
    expect(await screen.findByText(/given up on/i)).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('does not ask, or show, without a venue', () => {
    render(<PrintAgentStatus restaurantId={null} t={t} />);

    expect(printerAPI.getPrintAgentStatus).not.toHaveBeenCalled();
    expect(screen.queryByTestId('print-agent-status')).toBeNull();
  });

  it('renders nothing when the status cannot be read', async () => {
    // A platform user with no venue of their own gets a 403 here. This card sits above the printer
    // list people actually came to manage, and must not take it down.
    printerAPI.getPrintAgentStatus.mockRejectedValue(new Error('forbidden'));
    const { container } = render(<PrintAgentStatus restaurantId={3} t={t} />);

    await waitFor(() => expect(printerAPI.getPrintAgentStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('keeps polling, because the thing it reports on is the channel it would subscribe over', async () => {
    vi.useFakeTimers();
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'ONLINE', queuedJobs: 0, deadLetterJobs: 0, backlogAfterMinutes: 5,
    }));
    await act(async () => { render(<PrintAgentStatus restaurantId={3} t={t} />); });

    expect(printerAPI.getPrintAgentStatus).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(21000); });
    expect(printerAPI.getPrintAgentStatus).toHaveBeenCalledTimes(2);
  });
});
