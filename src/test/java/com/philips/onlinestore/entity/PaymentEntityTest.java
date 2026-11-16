package com.philips.onlinestore.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentEntityTest {

    @Test
    void storesPaymentAttemptDetails() {
        Order order = new Order(
                new User("Customer", "customer@example.com", "encoded", UserRole.CUSTOMER),
                OrderStatus.PLACED,
                new BigDecimal("19.99")
        );
        Payment payment = new Payment(
                order,
                new BigDecimal("19.99"),
                PaymentStatus.PENDING,
                "attempt-123"
        );

        assertEquals(order, payment.getOrder());
        assertEquals(new BigDecimal("19.99"), payment.getAmount());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("attempt-123", payment.getIdempotencyKey());
        assertNull(payment.getCompletedAt());
    }

    @Test
    void setsCreatedAtWhenPaymentIsPersisted() {
        Payment payment = new Payment(
                new Order(
                        new User("Customer", "customer@example.com", "encoded", UserRole.CUSTOMER),
                        OrderStatus.PLACED,
                        new BigDecimal("19.99")
                ),
                new BigDecimal("19.99"),
                PaymentStatus.PENDING,
                "attempt-123"
        );

        Instant beforePersist = Instant.now();
        ReflectionTestUtils.invokeMethod(payment, "setCreatedAt");

        assertNotNull(payment.getCreatedAt());
        assertFalse(payment.getCreatedAt().isBefore(beforePersist));
    }

    @Test
    void supportsCompletingPaymentAttempt() {
        Payment payment = new Payment(
                new Order(
                        new User("Customer", "customer@example.com", "encoded", UserRole.CUSTOMER),
                        OrderStatus.PLACED,
                        new BigDecimal("19.99")
                ),
                new BigDecimal("19.99"),
                PaymentStatus.PENDING,
                "attempt-123"
        );
        Instant completedAt = Instant.parse("2026-01-02T03:04:05Z");

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCompletedAt(completedAt);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(completedAt, payment.getCompletedAt());
    }
}
