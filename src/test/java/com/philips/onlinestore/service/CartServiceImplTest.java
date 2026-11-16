package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.AddToCartRequest;
import com.philips.onlinestore.dto.CartItemResponse;
import com.philips.onlinestore.dto.CartResponse;
import com.philips.onlinestore.dto.UpdateCartItemRequest;
import com.philips.onlinestore.entity.Cart;
import com.philips.onlinestore.entity.CartItem;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.exception.InactiveProductException;
import com.philips.onlinestore.exception.InsufficientStockException;
import com.philips.onlinestore.exception.InvalidCartQuantityException;
import com.philips.onlinestore.repository.CartItemRepository;
import com.philips.onlinestore.repository.CartRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.repository.UserRepository;
import com.philips.onlinestore.service.impl.CartServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final String EMAIL = "customer@example.com";
    private static final Long USER_ID = 7L;
    private static final Long CART_ID = 11L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private User user;

    @BeforeEach
    void setUp() {
        user = user();
        authenticate();
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addItemCreatesCartAndReturnsCalculatedSubtotal() {
        Product product = product(2L, 5, true);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            ReflectionTestUtils.setField(cart, "id", CART_ID);
            return cart;
        });
        when(cartItemRepository.findByCart_IdAndProduct_Id(CART_ID, 2L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 31L);
            return item;
        });

        CartItemResponse response = cartService.addItem(new AddToCartRequest(2L, 3));

        assertEquals(31L, response.id());
        assertEquals(3, response.quantity());
        assertEquals(new BigDecimal("30.00"), response.subtotal());
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addItemIncrementsExistingProductQuantity() {
        Product product = product(2L, 5, true);
        Cart cart = cart();
        CartItem item = new CartItem(product, 2);
        ReflectionTestUtils.setField(item, "id", 31L);
        cart.addItem(item);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_IdAndProduct_Id(CART_ID, 2L)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(item)).thenReturn(item);

        CartItemResponse response = cartService.addItem(new AddToCartRequest(2L, 2));

        assertEquals(4, response.quantity());
        assertEquals(new BigDecimal("40.00"), response.subtotal());
    }

    @Test
    void getCartReturnsItemsAndCorrectTotal() {
        Cart cart = cart();
        cart.addItem(new CartItem(product(2L, 5, true), 2));
        cart.addItem(new CartItem(product(3L, 5, true, "7.50"), 3));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));

        CartResponse response = cartService.getCart();

        assertEquals(2, response.items().size());
        assertEquals(new BigDecimal("42.50"), response.total());
    }

    @Test
    void updateItemChecksStockAndReturnsUpdatedSubtotal() {
        Product product = product(2L, 5, true);
        Cart cart = cart();
        CartItem item = new CartItem(product, 1);
        ReflectionTestUtils.setField(item, "id", 31L);
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart_Id(31L, CART_ID)).thenReturn(Optional.of(item));

        CartItemResponse response = cartService.updateItem(31L, new UpdateCartItemRequest(4));

        assertEquals(4, response.quantity());
        assertEquals(new BigDecimal("40.00"), response.subtotal());
    }

    @Test
    void removeItemRemovesOnlyItemFromAuthenticatedUsersCart() {
        Cart cart = cart();
        CartItem item = new CartItem(product(2L, 5, true), 1);
        ReflectionTestUtils.setField(item, "id", 31L);
        cart.addItem(item);
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart_Id(31L, CART_ID)).thenReturn(Optional.of(item));

        cartService.removeItem(31L);

        assertEquals(0, cart.getItems().size());
    }

    @Test
    void clearCartRemovesAllItems() {
        Cart cart = cart();
        cart.addItem(new CartItem(product(2L, 5, true), 1));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));

        cartService.clearCart();

        assertEquals(List.of(), cart.getItems());
    }

    @Test
    void addItemRejectsUnknownProduct() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> cartService.addItem(new AddToCartRequest(404L, 1))
        );
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void addItemRejectsNonPositiveQuantity() {
        assertThrows(
                InvalidCartQuantityException.class,
                () -> cartService.addItem(new AddToCartRequest(2L, 0))
        );
        verify(userRepository, never()).findByEmail(EMAIL);
    }

    @Test
    void addItemRejectsInsufficientStock() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L, 2, true)));
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            ReflectionTestUtils.setField(cart, "id", CART_ID);
            return cart;
        });
        when(cartItemRepository.findByCart_IdAndProduct_Id(CART_ID, 2L)).thenReturn(Optional.empty());

        assertThrows(
                InsufficientStockException.class,
                () -> cartService.addItem(new AddToCartRequest(2L, 3))
        );
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void addItemRejectsInactiveProduct() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L, 5, false)));

        assertThrows(
                InactiveProductException.class,
                () -> cartService.addItem(new AddToCartRequest(2L, 1))
        );
    }

    @Test
    void updateItemRejectsInsufficientStock() {
        Cart cart = cart();
        CartItem item = new CartItem(product(2L, 2, true), 1);
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart_Id(31L, CART_ID)).thenReturn(Optional.of(item));

        assertThrows(
                InsufficientStockException.class,
                () -> cartService.updateItem(31L, new UpdateCartItemRequest(3))
        );
    }

    @Test
    void updateItemRejectsItemOutsideAuthenticatedUsersCart() {
        when(cartRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByIdAndCart_Id(404L, CART_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> cartService.updateItem(404L, new UpdateCartItemRequest(1))
        );
    }

    @Test
    void cartOperationsRejectMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, cartService::getCart);
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

    private Product product(Long id, int stock, boolean active) {
        return product(id, stock, active, "10.00");
    }

    private Product product(Long id, int stock, boolean active, String price) {
        Product product = new Product(
                "Product " + id,
                "SKU-" + id,
                "Description",
                new BigDecimal(price),
                stock,
                null
        );
        ReflectionTestUtils.setField(product, "id", id);
        product.setActive(active);
        return product;
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, "ignored", List.of())
        );
    }
}
