package com.philips.onlinestore.controller;

import com.philips.onlinestore.dto.OrderResponse;
import com.philips.onlinestore.service.OrderService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder() {
        OrderResponse response = orderService.placeOrder();
        URI location = URI.create("/api/v1/orders/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<OrderResponse> getMyOrders() {
        return orderService.getMyOrders();
    }

    @GetMapping("/{orderId}")
    public OrderResponse getMyOrderById(@PathVariable @Positive Long orderId) {
        return orderService.getMyOrderById(orderId);
    }
}
