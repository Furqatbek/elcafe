/**
 * Thin wrapper around the Telegram Mini App (WebApp) SDK, which is loaded from telegram.org in
 * order.html. Every function degrades to a safe no-op outside Telegram, so the same self-service app
 * keeps running unchanged in a normal browser (the QR flow) and only lights up the Telegram chrome
 * when actually launched from a bot.
 */

/** The live Telegram.WebApp object, or null when not running inside Telegram. */
export function getTelegram() {
  if (typeof window === 'undefined') return null;
  return (window.Telegram && window.Telegram.WebApp) || null;
}

/**
 * True only when the page was actually launched from Telegram. The SDK object can exist without a
 * launch, so we key off initData, which Telegram populates only for a real Mini App session.
 */
export function isInTelegram() {
  const wa = getTelegram();
  return !!(wa && wa.initData && wa.initData.length > 0);
}

/** Raw, still-encoded Telegram.WebApp.initData (empty string when not in Telegram). */
export function getInitData() {
  const wa = getTelegram();
  return wa ? wa.initData || '' : '';
}

/**
 * Announce readiness and take the full viewport height. Safe to call from anywhere and on any route;
 * returns false when not inside Telegram so callers can branch if they care.
 */
export function initTelegramWebApp() {
  const wa = getTelegram();
  if (!wa) return false;
  try {
    wa.ready();
    if (typeof wa.expand === 'function') wa.expand();
  } catch (_) {
    // The WebApp SDK is best-effort chrome — never let a version/shape difference break the app.
  }
  return true;
}

/**
 * Open an external URL (e.g. a Payme/Click hosted checkout) from inside the Mini App. Uses Telegram's
 * openLink when available so it opens in the in-app browser, falling back to window.open elsewhere.
 */
export function openLink(url) {
  if (!url) return;
  const wa = getTelegram();
  if (wa && typeof wa.openLink === 'function') {
    wa.openLink(url);
  } else if (typeof window !== 'undefined') {
    window.open(url, '_blank', 'noopener');
  }
}
