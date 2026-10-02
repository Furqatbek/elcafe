import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PrintQueueBanner } from './PrintQueueBanner';
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
const show = () => render(<MemoryRouter><PrintQueueBanner t={t} /></MemoryRouter>);

describe('PrintQueueBanner', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => vi.useRealTimers());

  it('warns across every screen once tickets have been stuck for a while', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'BACKLOG', queuedJobs: 4, oldestQueuedMinutes: 20, backlogAfterMinutes: 5,
    }));
    show();

    expect(await screen.findByTestId('print-queue-banner')).toBeInTheDocument();
    expect(screen.getByText(/check the printer/i)).toBeInTheDocument();
    expect(screen.getByText(/4 waiting, oldest 20 min/i)).toBeInTheDocument();
  });

  it('stays silent when no agent is connected but nothing is waiting', async () => {
    // The case that decides whether this feature is worth having. A venue not using agent printing
    // would otherwise carry a red bar across every page forever, and then nobody sees the day it
    // means something.
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'OFFLINE', queuedJobs: 0, oldestQueuedMinutes: null, backlogAfterMinutes: 5,
    }));
    const { container } = show();

    await waitFor(() => expect(printerAPI.getPrintAgentStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('stays silent for a ticket that has only just been queued', async () => {
    // A ticket exists the moment an order does. A bar across every screen each time the kitchen is
    // briefly busy would train everyone to look past it.
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'ONLINE', queuedJobs: 2, oldestQueuedMinutes: 1, backlogAfterMinutes: 5,
    }));
    const { container } = show();

    await waitFor(() => expect(printerAPI.getPrintAgentStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('says the agent is unreachable rather than blaming the printer', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'OFFLINE', queuedJobs: 6, oldestQueuedMinutes: 30, backlogAfterMinutes: 5,
    }));
    show();

    // Nothing is connected, so there is no point sending anyone to look at paper.
    expect(await screen.findByText(/print agent is not responding/i)).toBeInTheDocument();
    expect(screen.queryByText(/check the printer\./i)).toBeNull();
  });

  it('is red when nothing is listening at all', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'OFFLINE', queuedJobs: 6, oldestQueuedMinutes: 30, backlogAfterMinutes: 5,
    }));
    show();

    // Nobody in the kitchen is being told anything, which is worse than a printer that is merely
    // behind, so the colour says so.
    expect(await screen.findByTestId('print-queue-banner')).toHaveClass('bg-red-600');
  });

  it('is amber while something is still connected and could recover', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'BACKLOG', queuedJobs: 2, oldestQueuedMinutes: 9, backlogAfterMinutes: 5,
    }));
    show();

    expect(await screen.findByTestId('print-queue-banner')).toHaveClass('bg-amber-500');
  });

  it('uses the threshold the backend reports, not one of its own', async () => {
    // A venue that raised its threshold to half an hour must not be warned at five minutes because
    // the frontend kept its own copy of the number.
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'BACKLOG', queuedJobs: 2, oldestQueuedMinutes: 9, backlogAfterMinutes: 30,
    }));
    const { container } = show();

    await waitFor(() => expect(printerAPI.getPrintAgentStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('offers the way to the screen that explains it', async () => {
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'BACKLOG', queuedJobs: 4, oldestQueuedMinutes: 20, backlogAfterMinutes: 5,
    }));
    show();

    expect(await screen.findByRole('link', { name: /check printers/i }))
      .toHaveAttribute('href', '/settings/printers');
  });

  it('renders nothing when the status cannot be read', async () => {
    printerAPI.getPrintAgentStatus.mockRejectedValue(new Error('forbidden'));
    const { container } = show();

    await waitFor(() => expect(printerAPI.getPrintAgentStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('keeps checking, so it clears itself once printing resumes', async () => {
    vi.useFakeTimers();
    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'BACKLOG', queuedJobs: 4, oldestQueuedMinutes: 20, backlogAfterMinutes: 5,
    }));
    let view;
    await act(async () => { view = show(); });
    expect(view.container).not.toBeEmptyDOMElement();

    printerAPI.getPrintAgentStatus.mockResolvedValue(envelope({
      state: 'ONLINE', queuedJobs: 0, oldestQueuedMinutes: null, backlogAfterMinutes: 5,
    }));
    await act(async () => { await vi.advanceTimersByTimeAsync(31000); });

    // It cannot be dismissed, so clearing itself is the only way it goes away — and it has to.
    expect(view.container).toBeEmptyDOMElement();
  });
});
