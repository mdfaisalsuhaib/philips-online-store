package com.philips.onlinestore.dto;

import com.philips.onlinestore.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        Instant completedAt
) {
}
