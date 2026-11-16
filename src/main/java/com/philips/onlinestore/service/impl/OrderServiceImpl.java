package com.philips.onlinestore.service.impl;

import com.philips.onlinestore.dto.OrderItemResponse;
import com.philips.onlinestore.dto.OrderResponse;
import com.philips.onlinestore.entity.Cart;
import com.philips.onlinestore.entity.CartItem;
import com.philips.onlinestore.entity.Order;
import com.philips.onlinestore.entity.OrderItem;
import com.philips.onlinestore.entity.OrderStatus;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.exception.EmptyCartException;
import com.philips.onlinestore.exception.InactiveProductException;
import com.philips.onlinestore.exception.InsufficientStockException;
import com.philips.onlinestore.repository.CartRepository;
import com.philips.onlinestore.repository.OrderRepository;
import com.philips.onlinestore.repository.UserRepository;
import com.philips.onlinestore.service.OrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public OrderServiceImpl(
            CartRepository cartRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder() {
        User user = getAuthenticatedUser();
        Cart cart = cartRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new EmptyCartException("Cannot place an order with an empty cart."));
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cannot place an order with an empty cart.");
        }

        List<CartItem> cartItems = cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (CartItem cartItem : cartItems) {
            lockAndRefreshProduct(cartItem.getProduct());
        }

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            if (!product.isActive()) {
                throw new InactiveProductException("Product is inactive: " + product.getId());
            }
            if (cartItem.getQuantity() > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.getId()
                                + "; available quantity: " + product.getStockQuantity()
                );
            }
        }

        BigDecimal totalAmount = cartItems.stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order(user, OrderStatus.PENDING_PAYMENT, totalAmount);
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            int quantity = cartItem.getQuantity();
            order.addItem(new OrderItem(
                    product,
                    product.getName(),
                    product.getSku(),
                    product.getPrice(),
                    quantity
            ));
        }

        Order savedOrder = orderRepository.save(order);
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
        }
        cart.getItems().clear();
        return toResponse(savedOrder);
    }

    @Override
    public List<OrderResponse> getMyOrders() {
        User user = getAuthenticatedUser();
        return orderRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public OrderResponse getMyOrderById(Long orderId) {
        User user = getAuthenticatedUser();
        Order order = orderRepository.findByIdAndUser_Id(orderId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + orderId));
        return toResponse(order);
    }

    private void lockAndRefreshProduct(Product product) {
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Authenticated user was not found."));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getSku(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items
        );
    }
}
