package com.philips.onlinestore.dto;

import com.philips.onlinestore.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    public OrderResponse {
        items = List.copyOf(items);
    }
}
