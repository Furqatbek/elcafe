import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import WsConnectionBanner from './WsConnectionBanner';
import { useNotificationStore } from '../store/notificationStore';

// Pins EH-2.5: the banner stays quiet during the first handshake, appears (debounced) after a drop
// that follows a successful connect, and clears on reconnect.

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (k, d) => d || k }) }));

const setConnected = (v) => act(() => useNotificationStore.getState().setWsConnected(v));

describe('WsConnectionBanner', () => {
  beforeEach(() => { vi.useFakeTimers(); useNotificationStore.setState({ wsConnected: false }); });
  afterEach(() => { vi.useRealTimers(); });

  it('stays hidden before the first successful connect', () => {
    render(<WsConnectionBanner />);
    act(() => vi.advanceTimersByTime(5000));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('shows (after debounce) once disconnected following a connect, and hides on reconnect', () => {
    render(<WsConnectionBanner />);
    setConnected(true);           // first handshake
    setConnected(false);          // dropped
    expect(screen.queryByRole('status')).not.toBeInTheDocument(); // debounced, not yet
    act(() => vi.advanceTimersByTime(3100));
    expect(screen.getByRole('status')).toBeInTheDocument();
    setConnected(true);           // reconnect
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
