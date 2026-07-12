import { lazy } from 'react';

/**
 * EH-2.3 (docs/ERROR_HANDLING_PLAN.md): drop-in for React.lazy that survives a stale-chunk load
 * failure — the "blank white page after a deploy" class. When a dynamic import fails because the
 * browser cached an old index.html that points at asset hashes the new deploy no longer has, we
 * force ONE full reload to pick up the fresh HTML + asset map. A sessionStorage one-shot guard
 * (per import site) prevents a reload loop: if it still fails after the reload, the error
 * propagates to the route ErrorBoundary and the user gets the error card instead of a blank page.
 */
export function lazyWithRetry(factory) {
  return lazy(async () => {
    const flag = `chunk-reload:${factory.toString()}`;
    try {
      const mod = await factory();
      sessionStorage.removeItem(flag); // success — reset the one-shot so a future deploy can retry
      return mod;
    } catch (err) {
      const isChunkError = /loading chunk|dynamically imported module|module script failed|failed to fetch|error loading/i
        .test(err?.message || '');
      if (isChunkError && sessionStorage.getItem(flag) !== '1') {
        sessionStorage.setItem(flag, '1');
        window.location.reload();
        return new Promise(() => {}); // render nothing until the reload takes over
      }
      throw err;
    }
  });
}
