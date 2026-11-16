package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.MockPaymentRequest;
import com.philips.onlinestore.dto.PaymentResponse;
import com.philips.onlinestore.entity.Order;
import com.philips.onlinestore.entity.OrderItem;
import com.philips.onlinestore.entity.OrderStatus;
import com.philips.onlinestore.entity.Payment;
import com.philips.onlinestore.entity.PaymentStatus;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.exception.InvalidPaymentRequestException;
import com.philips.onlinestore.exception.PaymentConflictException;
import com.philips.onlinestore.repository.OrderRepository;
import com.philips.onlinestore.repository.PaymentRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.service.impl.PaymentServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final Long ORDER_ID = 50L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Order order;
    private Product firstProduct;
    private Product secondProduct;

    @BeforeEach
    void setUp() {
        User user = new User("Customer", CUSTOMER_EMAIL, "encoded", UserRole.CUSTOMER);
        firstProduct = product(2L, 3);
        secondProduct = product(3L, 5);
        order = new Order(user, OrderStatus.PENDING_PAYMENT, new BigDecimal("19.99"));
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-01-02T03:04:05Z"));
        order.addItem(orderItem(firstProduct, 2));
        order.addItem(orderItem(secondProduct, 1));
        when(orderRepository.findByIdAndUser_EmailForUpdate(ORDER_ID, CUSTOMER_EMAIL))
                .thenReturn(Optional.of(order));
    }

    @Test
    void successfulPaymentPlacesOrderAndUsesOrderTotal() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        PaymentResponse response = paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "key-success"
        );

        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(new BigDecimal("19.99"), response.amount());
        assertEquals(ORDER_ID, response.orderId());
        assertNotNull(response.completedAt());
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void failedPaymentCancelsOrderAndRestoresStock() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(firstProduct));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(secondProduct));

        PaymentResponse response = paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("FAILURE"), "key-failed"
        );

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.FAILED, response.status());
        assertEquals(5, firstProduct.getStockQuantity());
        assertEquals(6, secondProduct.getStockQuantity());
        assertNotNull(response.completedAt());
        verify(productRepository).findByIdForUpdate(2L);
        verify(productRepository).findByIdForUpdate(3L);
    }

    @Test
    void failedPaymentRestoresStockOnlyOnceForSameKey() {
        AtomicReference<Payment> savedPayment = new AtomicReference<>();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = persisted(invocation.getArgument(0));
            savedPayment.set(payment);
            return payment;
        });
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(ORDER_ID, "key-failed"))
                .thenAnswer(invocation -> Optional.ofNullable(savedPayment.get()));
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(firstProduct));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(secondProduct));

        paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("FAILURE"), "key-failed"
        );
        PaymentResponse repeatedResponse = paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "key-failed"
        );

        assertEquals(PaymentStatus.FAILED, repeatedResponse.status());
        assertEquals(5, firstProduct.getStockQuantity());
        assertEquals(6, secondProduct.getStockQuantity());
        verify(paymentRepository).save(any(Payment.class));
        verify(productRepository).findByIdForUpdate(2L);
        verify(productRepository).findByIdForUpdate(3L);
    }

    @Test
    void sameIdempotencyKeyReturnsExistingPaymentResult() {
        Payment existingPayment = persisted(new Payment(
                order, new BigDecimal("19.99"), PaymentStatus.SUCCESS, "key-repeat"
        ));
        order.setStatus(OrderStatus.PLACED);
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(ORDER_ID, "key-repeat"))
                .thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("FAILURE"), "key-repeat"
        );

        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(OrderStatus.PLACED, order.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void differentKeyCannotPayAnAlreadyPaidOrder() {
        order.setStatus(OrderStatus.PLACED);

        assertThrows(PaymentConflictException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "another-key"
        ));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void cancelledOrderCannotBePaidAgainWithDifferentKey() {
        order.setStatus(OrderStatus.CANCELLED);

        assertThrows(PaymentConflictException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "another-key"
        ));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void rejectsZeroValueOrderBecausePaymentsRequirePositiveAmounts() {
        order.setTotalAmount(BigDecimal.ZERO);

        assertThrows(PaymentConflictException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "zero-total"
        ));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void customerCannotPayAnotherCustomersOrder() {
        when(orderRepository.findByIdAndUser_EmailForUpdate(ORDER_ID, CUSTOMER_EMAIL))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "private-key"
        ));

        verify(paymentRepository, never()).findByOrder_IdAndIdempotencyKey(any(), any());
    }

    @Test
    void rejectsBlankAndInvalidIdempotencyKeysAndOutcomes() {
        assertThrows(InvalidPaymentRequestException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), " "
        ));
        assertThrows(InvalidPaymentRequestException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "x".repeat(129)
        ));
        assertThrows(InvalidPaymentRequestException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("UNKNOWN"), "valid-key"
        ));

        verify(orderRepository, times(1)).findByIdAndUser_EmailForUpdate(any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void orderLockSerializesConcurrentPaymentAttempts() throws Exception {
        Method lockQuery = OrderRepository.class.getMethod(
                "findByIdAndUser_EmailForUpdate", Long.class, String.class
        );
        Lock lock = lockQuery.getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        PaymentResponse first = paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "first-key"
        );

        assertEquals(PaymentStatus.SUCCESS, first.status());
        assertThrows(PaymentConflictException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), "second-key"
        ));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void transactionFailureDoesNotPartiallyRestoreStockAndPaymentMethodIsTransactional() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(firstProduct));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> paymentService.processPayment(
                ORDER_ID, CUSTOMER_EMAIL, new MockPaymentRequest("FAILURE"), "rollback-key"
        ));

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(3, firstProduct.getStockQuantity());
        assertEquals(5, secondProduct.getStockQuantity());
        Transactional transaction = getPaymentMethod().getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertFalse(transaction.readOnly());
    }

    private Method getPaymentMethod() {
        try {
            return PaymentServiceImpl.class.getMethod(
                    "processPayment", Long.class, String.class, MockPaymentRequest.class, String.class
            );
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private Payment persisted(Payment payment) {
        ReflectionTestUtils.setField(payment, "id", 100L);
        ReflectionTestUtils.setField(payment, "createdAt", Instant.parse("2026-01-02T03:04:06Z"));
        return payment;
    }

    private Product product(Long id, int stock) {
        Product product = new Product("Product " + id, "SKU-" + id, null, BigDecimal.ONE, stock, null);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private OrderItem orderItem(Product product, int quantity) {
        return new OrderItem(product, product.getName(), product.getSku(), BigDecimal.ONE, quantity);
    }
}
