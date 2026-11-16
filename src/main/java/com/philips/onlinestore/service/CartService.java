package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.AddToCartRequest;
import com.philips.onlinestore.dto.CartItemResponse;
import com.philips.onlinestore.dto.CartResponse;
import com.philips.onlinestore.dto.UpdateCartItemRequest;

public interface CartService {

    CartItemResponse addItem(AddToCartRequest request);

    CartResponse getCart();

    CartItemResponse updateItem(Long itemId, UpdateCartItemRequest request);

    void removeItem(Long itemId);

    void clearCart();
}
