package com.philips.onlinestore.controller;

import com.philips.onlinestore.dto.AddToCartRequest;
import com.philips.onlinestore.dto.CartItemResponse;
import com.philips.onlinestore.dto.CartResponse;
import com.philips.onlinestore.dto.UpdateCartItemRequest;
import com.philips.onlinestore.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@Validated
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    public CartItemResponse addItem(@Valid @RequestBody AddToCartRequest request) {
        return cartService.addItem(request);
    }

    @GetMapping
    public CartResponse getCart() {
        return cartService.getCart();
    }

    @PutMapping("/items/{itemId}")
    public CartItemResponse updateItem(
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable @Positive Long itemId) {
        cartService.removeItem(itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        cartService.clearCart();
        return ResponseEntity.noContent().build();
    }
}
