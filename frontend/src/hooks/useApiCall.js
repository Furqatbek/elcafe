import { useState, useEffect, useCallback, useRef } from 'react';
import { getAppError } from '../lib/errors';

/**
 * EH-2.4 (docs/ERROR_HANDLING_PLAN.md): the standard data-fetch hook. Owns loading / error / data
 * so every page gets consistent states instead of the 398 silent `catch → console.error` blocks.
 * `error` is a normalized AppError (from lib/errors), ready for <QueryState> or errorMessage().
 *
 *   const { data, loading, error, refetch } = useApiCall(() => api.getThings(), [dep]);
 *
 * Runs on mount and whenever `deps` change (unless {immediate:false}); `refetch(...args)` re-runs
 * on demand and rejects so callers can chain. A stale response from a superseded call is dropped.
 */
export function useApiCall(fn, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState(null);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const callSeq = useRef(0);

  const run = useCallback(async (...args) => {
    const seq = ++callSeq.current;
    setLoading(true);
    setError(null);
    try {
      const res = await fnRef.current(...args);
      if (seq === callSeq.current) {
        setData(res);
        setLoading(false);
      }
      return res;
    } catch (e) {
      if (seq === callSeq.current) {
        setError(getAppError(e));
        setLoading(false);
      }
      throw e;
    }
  }, []);

  useEffect(() => {
    if (immediate) run().catch(() => {}); // the error is captured in state; swallow the reject here
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, loading, error, refetch: run, setData };
}
