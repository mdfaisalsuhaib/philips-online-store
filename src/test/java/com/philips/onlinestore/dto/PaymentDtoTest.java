package com.philips.onlinestore.dto;

import com.philips.onlinestore.entity.PaymentStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentDtoTest {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsSuccessOutcome() {
        MockPaymentRequest request = new MockPaymentRequest("SUCCESS");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void acceptsFailureOutcome() {
        MockPaymentRequest request = new MockPaymentRequest("FAILURE");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingOrUnsupportedOutcome() {
        assertTrue(validator.validate(new MockPaymentRequest(null)).size() > 0);
        assertTrue(validator.validate(new MockPaymentRequest("PENDING")).size() > 0);
        assertTrue(validator.validate(new MockPaymentRequest("success")).size() > 0);
    }

    @Test
    void paymentResponseExposesExpectedFields() {
        Instant createdAt = Instant.parse("2026-11-04T17:00:00Z");
        Instant completedAt = Instant.parse("2026-11-04T17:00:01Z");
        PaymentResponse response = new PaymentResponse(
                51L,
                123L,
                new BigDecimal("59.98"),
                PaymentStatus.SUCCESS,
                createdAt,
                completedAt
        );

        assertEquals(51L, response.paymentId());
        assertEquals(123L, response.orderId());
        assertEquals(new BigDecimal("59.98"), response.amount());
        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(createdAt, response.createdAt());
        assertEquals(completedAt, response.completedAt());
    }
}
