package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse placeOrder();

    List<OrderResponse> getMyOrders();

    OrderResponse getMyOrderById(Long orderId);
}
