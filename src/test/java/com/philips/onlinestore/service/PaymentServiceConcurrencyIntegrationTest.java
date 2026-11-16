package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.MockPaymentRequest;
import com.philips.onlinestore.dto.PaymentResponse;
import com.philips.onlinestore.entity.Category;
import com.philips.onlinestore.entity.Order;
import com.philips.onlinestore.entity.OrderItem;
import com.philips.onlinestore.entity.OrderStatus;
import com.philips.onlinestore.entity.PaymentStatus;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.exception.PaymentConflictException;
import com.philips.onlinestore.repository.CategoryRepository;
import com.philips.onlinestore.repository.OrderRepository;
import com.philips.onlinestore.repository.PaymentRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=always",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "jwt.expiration-ms=900000"
})
class PaymentServiceConcurrencyIntegrationTest {

    private static final String CUSTOMER_EMAIL = "payment-customer@example.com";
    private static final BigDecimal ITEM_PRICE = new BigDecimal("10.00");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    private Long orderId;
    private Product product;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.saveAndFlush(
                new User("Customer", CUSTOMER_EMAIL, "encoded", UserRole.CUSTOMER)
        );
        Category category = categoryRepository.saveAndFlush(new Category("Payments", null));
        product = productRepository.saveAndFlush(
                new Product("Payment product", "PAY-1", null, ITEM_PRICE, 4, category)
        );
        Order order = new Order(user, OrderStatus.PENDING_PAYMENT, ITEM_PRICE);
        order.addItem(new OrderItem(product, product.getName(), product.getSku(), ITEM_PRICE, 1));
        orderId = orderRepository.saveAndFlush(order).getId();
        entityManager.clear();
    }

    @Test
    void concurrentDifferentKeysCreateOnlyOneSuccessfulPayment() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> trySuccessfulPayment(start, "parallel-key-1"));
            Future<Boolean> second = executor.submit(() -> trySuccessfulPayment(start, "parallel-key-2"));
            start.countDown();

            boolean firstSucceeded = first.get();
            boolean secondSucceeded = second.get();
            assertTrue(firstSucceeded ^ secondSucceeded);
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();
        assertEquals(1, paymentRepository.findAllByOrder_Id(orderId).size());
        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findAllByOrder_Id(orderId).getFirst().getStatus());
        assertEquals(OrderStatus.PLACED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void transactionRollbackKeepsPaymentOrderAndStockConsistent() {
        product.setStockQuantity(Integer.MAX_VALUE);
        productRepository.saveAndFlush(product);
        entityManager.clear();

        assertThrows(ArithmeticException.class, () -> paymentService.processPayment(
                orderId, CUSTOMER_EMAIL, new MockPaymentRequest("FAILURE"), "rollback-overflow"
        ));

        entityManager.clear();
        assertTrue(paymentRepository.findAllByOrder_Id(orderId).isEmpty());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(
                Integer.MAX_VALUE,
                productRepository.findById(product.getId()).orElseThrow().getStockQuantity()
        );
    }

    private boolean trySuccessfulPayment(CountDownLatch start, String idempotencyKey) throws Exception {
        start.await();
        try {
            PaymentResponse response = paymentService.processPayment(
                    orderId, CUSTOMER_EMAIL, new MockPaymentRequest("SUCCESS"), idempotencyKey
            );
            return response.status() == PaymentStatus.SUCCESS;
        } catch (PaymentConflictException exception) {
            return false;
        }
    }
}
