package com.philips.onlinestore.controller;

import com.philips.onlinestore.dto.MockPaymentRequest;
import com.philips.onlinestore.dto.PaymentResponse;
import com.philips.onlinestore.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentResponse processPayment(
            @PathVariable @Positive Long orderId,
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody MockPaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return paymentService.processPayment(
                orderId,
                principal.getUsername(),
                request,
                idempotencyKey
        );
    }
}
