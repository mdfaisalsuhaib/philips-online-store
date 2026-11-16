package com.philips.onlinestore.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        boolean active,
        Long categoryId,
        String categoryName,
        String imageUrl
) {
}
