package com.elcafe.modules.order.validator;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.exception.InvalidOrderStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTransitionValidatorTest {

    private OrderStatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderStatusTransitionValidator();
    }

    @Nested
    @DisplayName("validateTransition")
    class ValidateTransition {

        @Test
        @DisplayName("PENDING → READY is valid (POS flow)")
        void pending_toReady_valid() {
            assertDoesNotThrow(() -> validator.validateTransition(OrderStatus.PENDING, OrderStatus.READY));
        }

        @Test
        @DisplayName("PENDING → CANCELLED is valid")
        void pending_toCancelled_valid() {
            assertDoesNotThrow(() -> validator.validateTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("ACCEPTED → PREPARING is valid")
        void accepted_toPreparing_valid() {
            assertDoesNotThrow(() -> validator.validateTransition(OrderStatus.ACCEPTED, OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("PREPARING → READY is valid")
        void preparing_toReady_valid() {
            assertDoesNotThrow(() -> validator.validateTransition(OrderStatus.PREPARING, OrderStatus.READY));
        }

        @Test
        @DisplayName("READY → COMPLETED is valid")
        void ready_toCompleted_valid() {
            assertDoesNotThrow(() -> validator.validateTransition(OrderStatus.READY, OrderStatus.COMPLETED));
        }

        @Test
        @DisplayName("NEW → ACCEPTED is valid")
        void new_toAccepted_valid() {
            assertDoesNotThrow(() -> validator.validateTransition(OrderStatus.NEW, OrderStatus.ACCEPTED));
        }

        @Test
        @DisplayName("COMPLETED → anything throws")
        void completed_toAnything_throws() {
            assertThrows(InvalidOrderStatusTransitionException.class,
                    () -> validator.validateTransition(OrderStatus.COMPLETED, OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("CANCELLED → anything throws")
        void cancelled_toAnything_throws() {
            assertThrows(InvalidOrderStatusTransitionException.class,
                    () -> validator.validateTransition(OrderStatus.CANCELLED, OrderStatus.NEW));
        }

        @Test
        @DisplayName("Same status → throws")
        void sameStatus_throws() {
            assertThrows(InvalidOrderStatusTransitionException.class,
                    () -> validator.validateTransition(OrderStatus.PREPARING, OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("Invalid transition throws with descriptive message")
        void invalidTransition_throwsWithMessage() {
            InvalidOrderStatusTransitionException ex = assertThrows(
                    InvalidOrderStatusTransitionException.class,
                    () -> validator.validateTransition(OrderStatus.READY, OrderStatus.NEW));
            assertTrue(ex.getMessage().contains("READY"));
            assertTrue(ex.getMessage().contains("NEW"));
        }
    }

    @Nested
    @DisplayName("isTransitionAllowed")
    class IsTransitionAllowed {

        @Test
        @DisplayName("Valid transition returns true")
        void validTransition_returnsTrue() {
            assertTrue(validator.isTransitionAllowed(OrderStatus.PENDING, OrderStatus.READY));
        }

        @Test
        @DisplayName("Invalid transition returns false")
        void invalidTransition_returnsFalse() {
            assertFalse(validator.isTransitionAllowed(OrderStatus.COMPLETED, OrderStatus.NEW));
        }

        @Test
        @DisplayName("Same status returns false")
        void sameStatus_returnsFalse() {
            assertFalse(validator.isTransitionAllowed(OrderStatus.NEW, OrderStatus.NEW));
        }
    }

    @Nested
    @DisplayName("isTerminalStatus")
    class IsTerminalStatus {

        @Test
        @DisplayName("COMPLETED is terminal")
        void completed_isTerminal() {
            assertTrue(validator.isTerminalStatus(OrderStatus.COMPLETED));
        }

        @Test
        @DisplayName("CANCELLED is terminal")
        void cancelled_isTerminal() {
            assertTrue(validator.isTerminalStatus(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("REJECTED is terminal")
        void rejected_isTerminal() {
            assertTrue(validator.isTerminalStatus(OrderStatus.REJECTED));
        }

        @Test
        @DisplayName("DELIVERED is terminal")
        void delivered_isTerminal() {
            assertTrue(validator.isTerminalStatus(OrderStatus.DELIVERED));
        }

        @Test
        @DisplayName("PREPARING is not terminal")
        void preparing_isNotTerminal() {
            assertFalse(validator.isTerminalStatus(OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("NEW is not terminal")
        void new_isNotTerminal() {
            assertFalse(validator.isTerminalStatus(OrderStatus.NEW));
        }
    }

    @Nested
    @DisplayName("getAllowedTransitions")
    class GetAllowedTransitions {

        @Test
        @DisplayName("PENDING allows READY, PLACED, CANCELLED")
        void pending_allowsThree() {
            Set<OrderStatus> allowed = validator.getAllowedTransitions(OrderStatus.PENDING);
            assertEquals(3, allowed.size());
            assertTrue(allowed.contains(OrderStatus.READY));
            assertTrue(allowed.contains(OrderStatus.PLACED));
            assertTrue(allowed.contains(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("COMPLETED allows nothing")
        void completed_allowsNothing() {
            Set<OrderStatus> allowed = validator.getAllowedTransitions(OrderStatus.COMPLETED);
            assertTrue(allowed.isEmpty());
        }
    }

    @Nested
    @DisplayName("canBeCancelled")
    class CanBeCancelled {

        @Test
        @DisplayName("PENDING can be cancelled")
        void pending_canBeCancelled() {
            assertTrue(validator.canBeCancelled(OrderStatus.PENDING));
        }

        @Test
        @DisplayName("ACCEPTED can be cancelled")
        void accepted_canBeCancelled() {
            assertTrue(validator.canBeCancelled(OrderStatus.ACCEPTED));
        }

        @Test
        @DisplayName("PREPARING cannot be cancelled")
        void preparing_cannotBeCancelled() {
            assertFalse(validator.canBeCancelled(OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("COMPLETED cannot be cancelled")
        void completed_cannotBeCancelled() {
            assertFalse(validator.canBeCancelled(OrderStatus.COMPLETED));
        }
    }
}
