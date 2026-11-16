package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.OrderResponse;
import com.philips.onlinestore.entity.Cart;
import com.philips.onlinestore.entity.CartItem;
import com.philips.onlinestore.entity.Order;
import com.philips.onlinestore.entity.OrderStatus;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.exception.EmptyCartException;
import com.philips.onlinestore.exception.InactiveProductException;
import com.philips.onlinestore.exception.InsufficientStockException;
import com.philips.onlinestore.repository.CartRepository;
import com.philips.onlinestore.repository.OrderRepository;
import com.philips.onlinestore.repository.UserRepository;
import com.philips.onlinestore.service.impl.OrderServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final String EMAIL = "customer@example.com";
    private static final Long USER_ID = 7L;
    private static final Long CART_ID = 11L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;

    @BeforeEach
    void setUp() {
        user = user();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, "ignored", List.of())
        );
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void placeOrderCreatesSnapshotsDecrementsStockAndClearsCart() {
        Product firstProduct = product(2L, "Headphones", "PH-100", "19.99", 5, true);
        Product secondProduct = product(3L, "Speaker", "PH-200", "7.50", 8, true);
        Cart cart = cart();
        cart.addItem(new CartItem(firstProduct, 2));
        cart.addItem(new CartItem(secondProduct, 3));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            ReflectionTestUtils.invokeMethod(order, "setCreatedAt");
            for (int index = 0; index < order.getItems().size(); index++) {
                ReflectionTestUtils.setField(order.getItems().get(index), "id", (long) index + 1);
            }
            return order;
        });

        OrderResponse response = orderService.placeOrder();

        assertEquals(100L, response.id());
        assertEquals(OrderStatus.PENDING_PAYMENT, response.status());
        assertEquals(new BigDecimal("62.48"), response.totalAmount());
        assertEquals(2, response.items().size());
        assertEquals("Headphones", response.items().getFirst().productName());
        assertEquals("PH-100", response.items().getFirst().sku());
        assertEquals(new BigDecimal("19.99"), response.items().getFirst().unitPrice());
        assertEquals(2, response.items().getFirst().quantity());
        assertEquals(new BigDecimal("39.98"), response.items().getFirst().subtotal());
        assertEquals(3, firstProduct.getStockQuantity());
        assertEquals(5, secondProduct.getStockQuantity());
        assertTrue(cart.getItems().isEmpty());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(user, orderCaptor.getValue().getUser());
        verify(entityManager).refresh(firstProduct, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(secondProduct, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void placeOrderRejectsMissingOrEmptyCart() {
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

        assertThrows(EmptyCartException.class, orderService::placeOrder);
        verify(orderRepository, never()).save(any(Order.class));

        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart()));
        assertThrows(EmptyCartException.class, orderService::placeOrder);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrderRejectsInactiveProductsWithoutChangingStock() {
        Product product = product(2L, "Headphones", "PH-100", "19.99", 5, false);
        Cart cart = cart();
        cart.addItem(new CartItem(product, 2));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));

        assertThrows(InactiveProductException.class, orderService::placeOrder);

        assertEquals(5, product.getStockQuantity());
        assertEquals(1, cart.getItems().size());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrderRejectsInsufficientStockBeforeChangingAnyProduct() {
        Product availableProduct = product(2L, "Headphones", "PH-100", "19.99", 5, true);
        Product unavailableProduct = product(3L, "Speaker", "PH-200", "7.50", 2, true);
        Cart cart = cart();
        cart.addItem(new CartItem(availableProduct, 3));
        cart.addItem(new CartItem(unavailableProduct, 3));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));

        assertThrows(InsufficientStockException.class, orderService::placeOrder);

        assertEquals(5, availableProduct.getStockQuantity());
        assertEquals(2, unavailableProduct.getStockQuantity());
        assertEquals(2, cart.getItems().size());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getMyOrderByIdOnlyLooksUpOrdersOwnedByAuthenticatedUser() {
        when(orderRepository.findByIdAndUser_Id(50L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> orderService.getMyOrderById(50L));

        verify(orderRepository).findByIdAndUser_Id(50L, USER_ID);
    }

    @Test
    void getMyOrderByIdReturnsAnOwnedOrder() {
        Product product = product(2L, "Headphones", "PH-100", "19.99", 5, true);
        Order order = new Order(user, OrderStatus.PLACED, new BigDecimal("19.99"));
        ReflectionTestUtils.setField(order, "id", 50L);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-01-02T03:04:05Z"));
        order.addItem(new com.philips.onlinestore.entity.OrderItem(
                product, "Purchased Headphones", "PH-100", new BigDecimal("19.99"), 1
        ));
        ReflectionTestUtils.setField(order.getItems().getFirst(), "id", 90L);
        when(orderRepository.findByIdAndUser_Id(50L, USER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getMyOrderById(50L);

        assertEquals(50L, response.id());
        assertEquals("Purchased Headphones", response.items().getFirst().productName());
        verify(orderRepository).findByIdAndUser_Id(50L, USER_ID);
    }

    @Test
    void orderOperationsRejectMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, orderService::placeOrder);
        assertThrows(AuthenticationCredentialsNotFoundException.class, orderService::getMyOrders);
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                () -> orderService.getMyOrderById(50L)
        );
    }

    private User user() {
        User authenticatedUser = new User("Customer", EMAIL, "encoded", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(authenticatedUser, "id", USER_ID);
        return authenticatedUser;
    }

    private Cart cart() {
        Cart cart = new Cart(user);
        ReflectionTestUtils.setField(cart, "id", CART_ID);
        return cart;
    }

    private Product product(
            Long id,
            String name,
            String sku,
            String price,
            int stock,
            boolean active
    ) {
        Product product = new Product(
                name,
                sku,
                null,
                new BigDecimal(price),
                stock,
                null
        );
        ReflectionTestUtils.setField(product, "id", id);
        product.setActive(active);
        return product;
    }
}
