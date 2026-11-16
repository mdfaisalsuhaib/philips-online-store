package com.philips.onlinestore.controller;

import com.philips.onlinestore.config.SecurityConfig;
import com.philips.onlinestore.dto.MockPaymentRequest;
import com.philips.onlinestore.dto.PaymentResponse;
import com.philips.onlinestore.entity.PaymentStatus;
import com.philips.onlinestore.exception.PaymentConflictException;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final String PAYMENT_URL = "/api/v1/orders/21/payments";
    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String IDEMPOTENCY_KEY = "payment-attempt-21";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpSecurityMocks() {
        when(jwtService.isTokenValid("customer-token")).thenReturn(true);
        when(jwtService.extractSubject("customer-token")).thenReturn(CUSTOMER_EMAIL);
        when(userDetailsService.loadUserByUsername(CUSTOMER_EMAIL))
                .thenReturn(User.withUsername(CUSTOMER_EMAIL)
                        .password("not-used")
                        .roles("CUSTOMER")
                        .build());
    }

    @Test
    void successfulPaymentReturnsPaymentResponse() throws Exception {
        when(paymentService.processPayment(
                eq(21L), eq(CUSTOMER_EMAIL), any(MockPaymentRequest.class), eq(IDEMPOTENCY_KEY)
        )).thenReturn(paymentResponse(PaymentStatus.SUCCESS));

        mockMvc.perform(paymentRequest("SUCCESS")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(51))
                .andExpect(jsonPath("$.orderId").value(21))
                .andExpect(jsonPath("$.amount").value(39.98))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.completedAt").value("2026-01-02T03:04:06Z"));

        verify(paymentService).processPayment(
                eq(21L),
                eq(CUSTOMER_EMAIL),
                eq(new MockPaymentRequest("SUCCESS")),
                eq(IDEMPOTENCY_KEY)
        );
    }

    @Test
    void failedPaymentReturnsFailedResponseNormally() throws Exception {
        when(paymentService.processPayment(
                eq(21L), eq(CUSTOMER_EMAIL), any(MockPaymentRequest.class), eq(IDEMPOTENCY_KEY)
        )).thenReturn(paymentResponse(PaymentStatus.FAILED));

        mockMvc.perform(paymentRequest("FAILURE")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(51))
                .andExpect(jsonPath("$.status").value("FAILED"));

        verify(paymentService).processPayment(
                eq(21L),
                eq(CUSTOMER_EMAIL),
                eq(new MockPaymentRequest("FAILURE")),
                eq(IDEMPOTENCY_KEY)
        );
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post(PAYMENT_URL)
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulatedOutcome\":\"SUCCESS\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void invalidRequestBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(paymentRequest("UNKNOWN")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void malformedRequestBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post(PAYMENT_URL)
                        .header("Authorization", "Bearer customer-token")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(paymentRequest("SUCCESS"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void serviceConflictExceptionUsesExistingExceptionHandling() throws Exception {
        when(paymentService.processPayment(
                eq(21L), eq(CUSTOMER_EMAIL), any(MockPaymentRequest.class), eq(IDEMPOTENCY_KEY)
        )).thenThrow(new PaymentConflictException("Order is not awaiting payment."));

        mockMvc.perform(paymentRequest("SUCCESS")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order is not awaiting payment."));
    }

    @Test
    void serviceNotFoundExceptionUsesExistingExceptionHandling() throws Exception {
        when(paymentService.processPayment(
                eq(21L), eq(CUSTOMER_EMAIL), any(MockPaymentRequest.class), eq(IDEMPOTENCY_KEY)
        )).thenThrow(new EntityNotFoundException("Order not found with id: 21"));

        mockMvc.perform(paymentRequest("SUCCESS")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: 21"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder paymentRequest(
            String simulatedOutcome
    ) {
        return post(PAYMENT_URL)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"simulatedOutcome":"%s"}
                        """.formatted(simulatedOutcome));
    }

    private PaymentResponse paymentResponse(PaymentStatus status) {
        return new PaymentResponse(
                51L,
                21L,
                new BigDecimal("39.98"),
                status,
                Instant.parse("2026-01-02T03:04:05Z"),
                Instant.parse("2026-01-02T03:04:06Z")
        );
    }
}
