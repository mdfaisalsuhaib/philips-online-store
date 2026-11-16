package com.philips.onlinestore.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
