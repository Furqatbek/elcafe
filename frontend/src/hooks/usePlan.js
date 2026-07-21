import { useAuthStore } from '../store/authStore';

/**
 * Plan-awareness selector over the billing status loaded into the auth store (GET /billing/me).
 *
 * Phase 1 / mini-phase A3 — observability only: this exposes the caller's tier, feature set, and
 * expiry/read-only state so the UI can react, but no sidebar filtering or route guarding happens
 * yet (that is the A4 gating sweep). `hasFeature` is the contract A4 will use to hide modules.
 */
export function usePlan() {
  const plan = useAuthStore((state) => state.plan);
  const features = plan?.featureCodes || [];

  return {
    plan,
    planCode: plan?.planCode || null,
    planName: plan?.planName || null,
    features,
    hasFeature: (code) => features.includes(code),
    daysUntilExpiry: plan?.daysUntilExpiry ?? null,
    // A1: absolute expiry (ISO string|null) and the caller's own restaurant id, sourced straight
    // from the billing status (GET /billing/me). restaurantId is the trusted value the self-serve
    // plan switch must use — never a user-entered id.
    planExpiresAt: plan?.planExpiresAt ?? null,
    restaurantId: plan?.restaurantId ?? null,
    isTrial: !!plan?.isTrial,
    inGracePeriod: !!plan?.inGracePeriod,
    isReadOnly: !!plan?.readOnly,
  };
}
