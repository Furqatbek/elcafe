import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useApiCall } from './useApiCall';

// Pins EH-2.4: the fetch hook owns loading/error/data, normalizes errors (error.app / getAppError),
// and drops stale responses so a fast refetch can't be overwritten by a slow earlier call.

describe('useApiCall', () => {
  it('runs immediately and exposes data on success', async () => {
    const fn = vi.fn().mockResolvedValue({ ok: 1 });
    const { result } = renderHook(() => useApiCall(fn, []));
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({ ok: 1 });
    expect(result.current.error).toBeNull();
  });

  it('captures a normalized AppError on failure without throwing to the caller', async () => {
    const fn = vi.fn().mockRejectedValue({ response: { status: 404, data: { error: 'NOT_FOUND' } } });
    const { result } = renderHook(() => useApiCall(fn, []));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toMatchObject({ code: 'NOT_FOUND', status: 404 });
    expect(result.current.data).toBeNull();
  });

  it('does not fetch on mount when immediate:false; refetch triggers it', async () => {
    const fn = vi.fn().mockResolvedValue('x');
    const { result } = renderHook(() => useApiCall(fn, [], { immediate: false }));
    expect(fn).not.toHaveBeenCalled();
    expect(result.current.loading).toBe(false);
    await act(async () => { await result.current.refetch(); });
    expect(fn).toHaveBeenCalledTimes(1);
    expect(result.current.data).toBe('x');
  });

  it('keeps only the latest call result when two overlap', async () => {
    let resolveSlow;
    const slow = new Promise((res) => { resolveSlow = res; });
    const fn = vi.fn()
      .mockImplementationOnce(() => slow)          // first (slow) call
      .mockImplementationOnce(() => Promise.resolve('fast')); // second (fast) call
    const { result } = renderHook(() => useApiCall(fn, [], { immediate: false }));

    let firstP;
    act(() => { firstP = result.current.refetch().catch(() => {}); });
    await act(async () => { await result.current.refetch(); });   // fast wins
    expect(result.current.data).toBe('fast');

    await act(async () => { resolveSlow('slow'); await firstP; }); // slow resolves late — ignored
    expect(result.current.data).toBe('fast');
  });
});
