package com.elcafe.modules.order.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the cancellation cutoff sits, spelled out state by state.
 *
 * <p>Worth its own test because the answer is a commercial position, not an implementation detail:
 * before the line a customer changes their mind for free, after it the restaurant has bought
 * ingredients and spent a cook's time on a meal nobody will eat. It was previously enumerated at each
 * call site and the enumerations had drifted — one of them let a customer cancel an order a courier
 * was already carrying.
 */
class OrderStatusTest {

    /** Nothing has been cooked yet. A customer changing their mind here costs the venue nothing. */
    private static final Set<OrderStatus> FREE_TO_CANCEL =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.NEW, OrderStatus.PLACED, OrderStatus.ACCEPTED);

    /** Already over, one way or the other. Not "the kitchen started" — there is nothing to protect. */
    private static final Set<OrderStatus> ALREADY_FINISHED =
            EnumSet.of(OrderStatus.REJECTED, OrderStatus.CANCELLED);

    @Test
    @DisplayName("the line is PREPARING: accepted is still free, cooking is not")
    void theCutoffIsPreparing() {
        assertThat(OrderStatus.ACCEPTED.kitchenHasStarted()).isFalse();
        assertThat(OrderStatus.PREPARING.kitchenHasStarted()).isTrue();
    }

    @Test
    @DisplayName("food already with a courier is past the line, which is where it used to leak")
    void deliveryStatesAreProtected() {
        // The states the consumer guard forgot. An order a courier is carrying was cancellable, with
        // a full refund, right up to the moment it was handed over.
        assertThat(OrderStatus.COURIER_ASSIGNED.kitchenHasStarted()).isTrue();
        assertThat(OrderStatus.PICKED_UP.kitchenHasStarted()).isTrue();
        assertThat(OrderStatus.COMPLETED.kitchenHasStarted()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("every status has a deliberate answer, including any added later")
    void everyStatusIsAccountedFor(OrderStatus status) {
        // Exhaustive rather than a list of interesting cases: a new status added to the enum without
        // a decision here is a new hole in the cutoff, and this is what makes that impossible to miss.
        boolean expected = !FREE_TO_CANCEL.contains(status) && !ALREADY_FINISHED.contains(status);

        assertThat(status.kitchenHasStarted())
                .as("%s: is the venue's food committed by this point?", status)
                .isEqualTo(expected);
    }
}
