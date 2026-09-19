package com.elcafe.modules.partner.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * When the recompute runs, and what happens when it goes wrong.
 *
 * <p>The thing being pinned here is that recomputing a menu flag can never take down a stock
 * movement. Running inline would put it in the caller's transaction, where a failure marks that
 * transaction rollback-only and a {@code catch} does not undo the mark — the deduction then fails at
 * commit, and on the partner path that means losing an order we had already printed. Deferring past
 * the commit is what makes the best-effort handling honest, so the deferral is a tested property, not
 * an implementation detail.
 */
@ExtendWith(MockitoExtension.class)
class ProductAvailabilityServiceTest {

    @Mock private ProductAvailabilityRecomputer recomputer;
    @InjectMocks private ProductAvailabilityService service;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("with no transaction to wait for, the recompute runs straight away")
    void withoutTransaction_runsInline() {
        service.onIngredientsChanged(List.of(1L, 2L));

        verify(recomputer).recomputeForIngredients(Set.of(1L, 2L));
    }

    @Test
    @DisplayName("inside a transaction, nothing runs until that transaction commits")
    void insideTransaction_defersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.onIngredientsChanged(List.of(1L));
        verifyNoInteractions(recomputer);

        commit();

        verify(recomputer).recomputeForIngredients(Set.of(1L));
    }

    @Test
    @DisplayName("several movements in one transaction become one recompute, not one each")
    void severalMovements_coalesceIntoOneRecompute() {
        TransactionSynchronizationManager.initSynchronization();

        // A delivery receipt restocking a shelf's worth of ingredients, one call per line.
        service.onIngredientsChanged(List.of(1L));
        service.onIngredientsChanged(List.of(2L, 3L));
        service.onIngredientsChanged(List.of(1L));

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        commit();

        verify(recomputer).recomputeForIngredients(Set.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("a rolled back stock movement does not move the menu")
    void rollback_recomputesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        service.onIngredientsChanged(List.of(1L));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(recomputer);
    }

    @Test
    @DisplayName("a rolled back transaction leaves nothing bound to the thread")
    void rollback_releasesTheThreadBinding() {
        TransactionSynchronizationManager.initSynchronization();
        service.onIngredientsChanged(List.of(1L));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();

        // The next transaction on this pooled thread starts clean: a fresh batch, a fresh callback.
        TransactionSynchronizationManager.initSynchronization();
        service.onIngredientsChanged(List.of(2L));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        commit();

        verify(recomputer).recomputeForIngredients(Set.of(2L));
    }

    @Test
    @DisplayName("a failed recompute is swallowed: the stock movement has already committed and stands")
    void failedRecompute_doesNotEscape() {
        doThrow(new IllegalStateException("db is on fire"))
                .when(recomputer).recomputeForIngredients(any());

        TransactionSynchronizationManager.initSynchronization();
        service.onIngredientsChanged(List.of(1L));

        assertThatCode(this::commit).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nothing moved, nothing scheduled")
    void emptyInput_schedulesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        service.onIngredientsChanged(List.of());
        service.onIngredientsChanged(null);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(recomputer);
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }
}
