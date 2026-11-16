package com.philips.onlinestore.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OrderEntityTest {

    @Test
    void addItemSetsOrderRelationshipAndStoresPriceSnapshots() {
        Product product = new Product(
                "Current Product Name",
                "CURRENT-SKU",
                null,
                new BigDecimal("19.99"),
                4,
                null
        );
        Order order = new Order(
                new User("Customer", "customer@example.com", "encoded", UserRole.CUSTOMER),
                OrderStatus.PLACED,
                new BigDecimal("39.98")
        );
        OrderItem item = new OrderItem(
                product,
                "Purchased Product Name",
                "PURCHASED-SKU",
                new BigDecimal("19.99"),
                2
        );

        order.addItem(item);

        assertEquals(order, item.getOrder());
        assertEquals(product, item.getProduct());
        assertEquals("Purchased Product Name", item.getProductName());
        assertEquals("PURCHASED-SKU", item.getSku());
        assertEquals(new BigDecimal("19.99"), item.getUnitPrice());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("39.98"), item.getSubtotal());
        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertEquals(new BigDecimal("39.98"), order.getTotalAmount());
    }

    @Test
    void setsCreatedAtWhenOrderIsPersisted() {
        Order order = new Order(
                new User("Customer", "customer@example.com", "encoded", UserRole.CUSTOMER),
                OrderStatus.PLACED,
                BigDecimal.ZERO
        );

        Instant beforePersist = Instant.now();
        order.setCreatedAt();

        assertNotNull(order.getCreatedAt());
        assertFalse(order.getCreatedAt().isBefore(beforePersist));
    }

    @Test
    void preservesExplicitCreationTimestamp() {
        Order order = new Order(
                new User("Customer", "customer@example.com", "encoded", UserRole.CUSTOMER),
                OrderStatus.CANCELLED,
                BigDecimal.ZERO
        );
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        org.springframework.test.util.ReflectionTestUtils.setField(order, "createdAt", createdAt);

        order.setCreatedAt();

        assertEquals(createdAt, order.getCreatedAt());
    }
}
