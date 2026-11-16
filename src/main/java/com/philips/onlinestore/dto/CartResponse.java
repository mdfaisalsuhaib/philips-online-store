package com.philips.onlinestore.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        BigDecimal total
) {
    public CartResponse {
        items = List.copyOf(items);
    }
}
