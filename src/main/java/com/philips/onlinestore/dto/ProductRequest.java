package com.philips.onlinestore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank
        @Size(max = 150)
        String name,
        @NotBlank
        @Size(max = 64)
        String sku,
        @Size(max = 10000)
        String description,
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,
        @NotNull
        @PositiveOrZero
        Integer stockQuantity,
        @NotNull
        Boolean active,
        @NotNull
        @Positive
        Long categoryId
) {
}
