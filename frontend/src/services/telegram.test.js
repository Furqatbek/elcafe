import { afterEach, describe, expect, it, vi } from 'vitest';
import { getInitData, initTelegramWebApp, isInTelegram } from './telegram';

describe('telegram webapp helper', () => {
  afterEach(() => {
    delete window.Telegram;
  });

  it('reports not-in-telegram and no-ops when the SDK is absent', () => {
    expect(isInTelegram()).toBe(false);
    expect(getInitData()).toBe('');
    expect(initTelegramWebApp()).toBe(false);
  });

  it('treats an empty initData as not-in-telegram', () => {
    window.Telegram = { WebApp: { initData: '', ready: vi.fn(), expand: vi.fn() } };
    expect(isInTelegram()).toBe(false);
  });

  it('detects a real launch and calls ready()/expand()', () => {
    const ready = vi.fn();
    const expand = vi.fn();
    window.Telegram = { WebApp: { initData: 'user=%7B%22id%22%3A1%7D&hash=abc', ready, expand } };

    expect(isInTelegram()).toBe(true);
    expect(getInitData()).toContain('hash=abc');
    expect(initTelegramWebApp()).toBe(true);
    expect(ready).toHaveBeenCalledOnce();
    expect(expand).toHaveBeenCalledOnce();
  });

  it('still returns true if expand is missing (older Telegram clients)', () => {
    const ready = vi.fn();
    window.Telegram = { WebApp: { initData: 'x=1', ready } };
    expect(initTelegramWebApp()).toBe(true);
    expect(ready).toHaveBeenCalledOnce();
  });
});
