package com.philips.onlinestore.service.impl;

import com.philips.onlinestore.dto.MockPaymentRequest;
import com.philips.onlinestore.dto.PaymentResponse;
import com.philips.onlinestore.entity.Order;
import com.philips.onlinestore.entity.OrderItem;
import com.philips.onlinestore.entity.OrderStatus;
import com.philips.onlinestore.entity.Payment;
import com.philips.onlinestore.entity.PaymentStatus;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.exception.InvalidPaymentRequestException;
import com.philips.onlinestore.exception.PaymentConflictException;
import com.philips.onlinestore.repository.OrderRepository;
import com.philips.onlinestore.repository.PaymentRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;

    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            ProductRepository productRepository
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(
            Long orderId,
            String customerEmail,
            MockPaymentRequest request,
            String idempotencyKey
    ) {
        validateCustomerIdentity(customerEmail);
        validateIdempotencyKey(idempotencyKey);

        Order order = orderRepository.findByIdAndUser_EmailForUpdate(orderId, customerEmail)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + orderId));

        Payment existingPayment = paymentRepository
                .findByOrder_IdAndIdempotencyKey(orderId, idempotencyKey)
                .orElse(null);
        if (existingPayment != null) {
            return toResponse(existingPayment);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentConflictException("Order is not awaiting payment.");
        }
        if (order.getTotalAmount().signum() <= 0) {
            throw new PaymentConflictException("Order total must be positive to process payment.");
        }

        PaymentStatus requestedStatus = paymentStatus(request);
        Payment payment = new Payment(order, order.getTotalAmount(), requestedStatus, idempotencyKey);
        payment.setCompletedAt(Instant.now());
        Payment savedPayment = paymentRepository.save(payment);

        if (requestedStatus == PaymentStatus.SUCCESS) {
            order.setStatus(OrderStatus.PLACED);
        } else {
            restoreStock(order);
            order.setStatus(OrderStatus.CANCELLED);
        }

        return toResponse(savedPayment);
    }

    private void restoreStock(Order order) {
        Map<Long, Product> lockedProducts = new LinkedHashMap<>();
        order.getItems().stream()
                .map(OrderItem::getProduct)
                .map(Product::getId)
                .distinct()
                .sorted()
                .forEach(productId -> {
                    Product product = productRepository.findByIdForUpdate(productId)
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Product not found with id: " + productId
                            ));
                    lockedProducts.put(productId, product);
                });

        order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> {
                    Product product = lockedProducts.get(item.getProduct().getId());
                    product.setStockQuantity(Math.addExact(product.getStockQuantity(), item.getQuantity()));
                });
    }

    private void validateCustomerIdentity(String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128
                || !idempotencyKey.equals(idempotencyKey.trim())
                || idempotencyKey.chars().anyMatch(character -> character < 0x21 || character > 0x7e)) {
            throw new InvalidPaymentRequestException(
                    "Idempotency-Key must contain 1 to 128 printable ASCII characters without surrounding spaces."
            );
        }
    }

    private PaymentStatus paymentStatus(MockPaymentRequest request) {
        if (request == null || request.simulatedOutcome() == null) {
            throw new InvalidPaymentRequestException("A simulated payment outcome is required.");
        }
        return switch (request.simulatedOutcome()) {
            case "SUCCESS" -> PaymentStatus.SUCCESS;
            case "FAILURE" -> PaymentStatus.FAILED;
            default -> throw new InvalidPaymentRequestException(
                    "Simulated payment outcome must be SUCCESS or FAILURE."
            );
        };
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getCompletedAt()
        );
    }
}
