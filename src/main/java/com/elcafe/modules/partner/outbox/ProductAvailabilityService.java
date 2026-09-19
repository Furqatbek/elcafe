package com.elcafe.modules.partner.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The seam between "stock moved" and "the menu changed".
 *
 * <p>Inventory calls this from inside the transaction that moved the stock; the actual recompute runs
 * in {@link ProductAvailabilityRecomputer} <b>after that transaction commits</b>. Both halves of that
 * are deliberate.
 *
 * <p><b>Why not simply recompute inline.</b> Recomputing joins the caller's transaction, so a failure
 * in it — a lock timeout on a {@code products} row, a bug in our own code — marks that transaction
 * rollback-only. Catching the exception here would not undo the mark: the stock deduction would go on
 * to fail at commit with an {@code UnexpectedRollbackException}, and on the partner path that means
 * losing an order we had already printed. Deriving a menu flag is not worth that risk to the record
 * of food leaving the shelf. After the commit there is no longer a transaction to poison, so the
 * best-effort catch below is honest rather than a comfort.
 *
 * <p><b>What we give up.</b> The flag lands in a second transaction, so a crash in the gap leaves it
 * stale. That is survivable precisely because it is not the only guard: the partner order path checks
 * {@code isOrderable()} against live data on every order, so a stale menu means a refused order, never
 * an accepted one the kitchen cannot cook. The next stock movement for the same ingredient corrects
 * the flag.
 *
 * <p>Synchronous on the committing thread rather than {@code @Async}, because the tenant filter is
 * driven by a thread-local and a background thread would run the recompute unscoped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAvailabilityService {

    /**
     * Thread-bound key for the ids collected during one transaction. A delivery receipt restocks
     * twenty ingredients in a loop; without this it would queue twenty separate recomputes for what
     * is really one event.
     */
    private static final Object PENDING_INGREDIENTS = new Object();

    private final ProductAvailabilityRecomputer recomputer;

    /** Stock just moved for these ingredients; work out what that means for the menu. */
    public void onIngredientsChanged(Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Nothing to wait for and nothing we could poison — a scheduled job or a direct call.
            safeRecompute(new HashSet<>(ingredientIds));
            return;
        }

        @SuppressWarnings("unchecked")
        Set<Long> pending = (Set<Long>) TransactionSynchronizationManager.getResource(PENDING_INGREDIENTS);
        if (pending == null) {
            pending = new HashSet<>();
            TransactionSynchronizationManager.bindResource(PENDING_INGREDIENTS, pending);
            registerFlush(pending);
        }
        pending.addAll(ingredientIds);
    }

    private void registerFlush(Set<Long> batch) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // afterCompletion rather than afterCommit so the binding is released on a rollback
                // too, instead of leaking onto a pooled thread.
                TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_INGREDIENTS);
                if (status == STATUS_COMMITTED) {
                    safeRecompute(batch);
                }
            }
        });
    }

    private void safeRecompute(Set<Long> ingredientIds) {
        try {
            recomputer.recomputeForIngredients(ingredientIds);
        } catch (Exception e) {
            // The stock movement is already committed and stands. A menu flag we failed to derive is
            // corrected by the next movement, and the order path re-checks availability regardless.
            log.error("Failed to recompute availability for ingredients {}: {}",
                    ingredientIds, e.getMessage(), e);
        }
    }
}
