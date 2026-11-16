package com.philips.onlinestore.service.impl;

import com.philips.onlinestore.dto.AddToCartRequest;
import com.philips.onlinestore.dto.CartItemResponse;
import com.philips.onlinestore.dto.CartResponse;
import com.philips.onlinestore.dto.UpdateCartItemRequest;
import com.philips.onlinestore.entity.Cart;
import com.philips.onlinestore.entity.CartItem;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.exception.InactiveProductException;
import com.philips.onlinestore.exception.InsufficientStockException;
import com.philips.onlinestore.exception.InvalidCartQuantityException;
import com.philips.onlinestore.repository.CartItemRepository;
import com.philips.onlinestore.repository.CartRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.repository.UserRepository;
import com.philips.onlinestore.service.CartService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public CartItemResponse addItem(AddToCartRequest request) {
        validateQuantity(request.quantity());
        User user = getAuthenticatedUser();
        Product product = findProduct(request.productId());
        validateProduct(product);

        Cart cart = findOrCreateCart(user);
        CartItem item = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), product.getId())
                .orElse(null);
        long updatedQuantity = (item == null ? 0L : item.getQuantity()) + request.quantity();
        validateStock(product, updatedQuantity);

        if (item == null) {
            item = new CartItem(product, request.quantity());
            cart.addItem(item);
        } else {
            item.setQuantity((int) updatedQuantity);
        }
        CartItem savedItem = cartItemRepository.save(item);
        return toItemResponse(savedItem);
    }

    @Override
    @Transactional
    public CartResponse getCart() {
        User user = getAuthenticatedUser();
        return toCartResponse(findOrCreateCart(user));
    }

    @Override
    @Transactional
    public CartItemResponse updateItem(Long itemId, UpdateCartItemRequest request) {
        validateQuantity(request.quantity());
        User user = getAuthenticatedUser();
        Cart cart = findOrCreateCart(user);
        CartItem item = cartItemRepository.findByIdAndCart_Id(itemId, cart.getId())
                .orElseThrow(() -> new EntityNotFoundException("Cart item not found with id: " + itemId));
        Product product = item.getProduct();
        validateProduct(product);
        validateStock(product, request.quantity());

        item.setQuantity(request.quantity());
        return toItemResponse(item);
    }

    @Override
    @Transactional
    public void removeItem(Long itemId) {
        Cart cart = findOrCreateCart(getAuthenticatedUser());
        CartItem item = cartItemRepository.findByIdAndCart_Id(itemId, cart.getId())
                .orElseThrow(() -> new EntityNotFoundException("Cart item not found with id: " + itemId));
        cart.removeItem(item);
    }

    @Override
    @Transactional
    public void clearCart() {
        Cart cart = findOrCreateCart(getAuthenticatedUser());
        cart.getItems().clear();
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

    private Cart findOrCreateCart(User user) {
        return cartRepository.findByUser_Id(user.getId())
                .orElseGet(() -> cartRepository.save(new Cart(user)));
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId));
    }

    private void validateProduct(Product product) {
        if (!product.isActive()) {
            throw new InactiveProductException("Product is inactive: " + product.getId());
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidCartQuantityException("Quantity must be greater than zero.");
        }
    }

    private void validateStock(Product product, long quantity) {
        if (quantity > product.getStockQuantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock for product " + product.getId()
                            + "; available quantity: " + product.getStockQuantity()
            );
        }
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        BigDecimal total = items.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), items, total);
    }

    private CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                item.getQuantity(),
                product.getPrice(),
                subtotal
        );
    }
}
