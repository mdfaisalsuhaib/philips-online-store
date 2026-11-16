package com.philips.onlinestore.dto;

import com.philips.onlinestore.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderResponseTest {

    @Test
    void storesOrderAndItemResponseValues() {
        OrderItemResponse item = new OrderItemResponse(
                4L,
                9L,
                "Headphones",
                "PH-100",
                new BigDecimal("19.99"),
                2,
                new BigDecimal("39.98")
        );
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");

        OrderResponse response = new OrderResponse(
                12L,
                OrderStatus.PLACED,
                new BigDecimal("39.98"),
                createdAt,
                List.of(item)
        );

        assertEquals(12L, response.id());
        assertEquals(OrderStatus.PLACED, response.status());
        assertEquals(new BigDecimal("39.98"), response.totalAmount());
        assertEquals(createdAt, response.createdAt());
        assertEquals(List.of(item), response.items());
    }

    @Test
    void makesItemListImmutable() {
        List<OrderItemResponse> items = new ArrayList<>();
        OrderResponse response = new OrderResponse(
                12L,
                OrderStatus.CANCELLED,
                BigDecimal.ZERO,
                Instant.parse("2026-01-02T03:04:05Z"),
                items
        );

        items.add(new OrderItemResponse(
                4L, 9L, "Headphones", "PH-100",
                new BigDecimal("19.99"), 1, new BigDecimal("19.99")
        ));

        assertEquals(List.of(), response.items());
        assertThrows(UnsupportedOperationException.class, () -> response.items().clear());
    }
}
