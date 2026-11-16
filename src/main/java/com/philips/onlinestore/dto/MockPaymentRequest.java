package com.philips.onlinestore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MockPaymentRequest(
        @NotBlank
        @Pattern(regexp = "SUCCESS|FAILURE", message = "must be SUCCESS or FAILURE")
        String simulatedOutcome
) {
}
