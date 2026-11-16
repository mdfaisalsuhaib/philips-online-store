package com.philips.onlinestore.controller;

import com.philips.onlinestore.config.SecurityConfig;
import com.philips.onlinestore.dto.OrderItemResponse;
import com.philips.onlinestore.dto.OrderResponse;
import com.philips.onlinestore.entity.OrderStatus;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpSecurityMocks() {
        when(jwtService.isTokenValid("customer-token")).thenReturn(true);
        when(jwtService.extractSubject("customer-token")).thenReturn("customer@example.com");
        when(userDetailsService.loadUserByUsername("customer@example.com"))
                .thenReturn(User.withUsername("customer@example.com")
                        .password("not-used")
                        .roles("CUSTOMER")
                        .build());
    }

    @Test
    void placeOrderReturnsCreatedOrderAndLocation() throws Exception {
        when(orderService.placeOrder()).thenReturn(orderResponse());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/21"))
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items[0].productName").value("Headphones"));

        verify(orderService).placeOrder();
    }

    @Test
    void getMyOrdersReturnsServiceResults() throws Exception {
        when(orderService.getMyOrders()).thenReturn(List.of(orderResponse()));

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(21))
                .andExpect(jsonPath("$[0].totalAmount").value(39.98));

        verify(orderService).getMyOrders();
    }

    @Test
    void getOrderByIdReturnsDetails() throws Exception {
        when(orderService.getMyOrderById(21L)).thenReturn(orderResponse());

        mockMvc.perform(get("/api/v1/orders/21")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.items[0].sku").value("PH-100"));

        verify(orderService).getMyOrderById(21L);
    }

    @Test
    void getOrderByIdRejectsNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/v1/orders/0")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isBadRequest());
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(
                21L,
                OrderStatus.PENDING_PAYMENT,
                new BigDecimal("39.98"),
                Instant.parse("2026-01-02T03:04:05Z"),
                List.of(new OrderItemResponse(
                        31L,
                        7L,
                        "Headphones",
                        "PH-100",
                        new BigDecimal("19.99"),
                        2,
                        new BigDecimal("39.98")
                ))
        );
    }
}
