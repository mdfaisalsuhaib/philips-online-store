package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.MockPaymentRequest;
import com.philips.onlinestore.dto.PaymentResponse;

public interface PaymentService {

    PaymentResponse processPayment(
            Long orderId,
            String customerEmail,
            MockPaymentRequest request,
            String idempotencyKey
    );
}
