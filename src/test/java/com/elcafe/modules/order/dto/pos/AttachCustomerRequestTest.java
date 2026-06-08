package com.elcafe.modules.order.dto.pos;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttachCustomerRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("rejects request with no identifier")
    void rejectsEmpty() {
        AttachCustomerRequest req = AttachCustomerRequest.builder().build();
        Set<ConstraintViolation<AttachCustomerRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("rejects request with multiple identifiers")
    void rejectsMultiple() {
        AttachCustomerRequest req = AttachCustomerRequest.builder()
                .customerId(1L)
                .qrCode("CST-X")
                .build();
        Set<ConstraintViolation<AttachCustomerRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("accepts customerId only")
    void acceptsCustomerId() {
        AttachCustomerRequest req = AttachCustomerRequest.builder().customerId(1L).build();
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("accepts qrCode only")
    void acceptsQrCode() {
        AttachCustomerRequest req = AttachCustomerRequest.builder().qrCode("CST-ABC").build();
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("accepts phone only")
    void acceptsPhone() {
        AttachCustomerRequest req = AttachCustomerRequest.builder().phone("+998901234567").build();
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("treats blank string as absent")
    void treatsBlankAsAbsent() {
        AttachCustomerRequest req = AttachCustomerRequest.builder()
                .customerId(1L)
                .qrCode("   ")
                .build();
        assertThat(validator.validate(req)).isEmpty();
    }
}
