package com.controller;

import com.dto.request.AddToCartRequest;
import com.dto.request.UpdateCartItemRequest;
import com.dto.response.ApiResponse;
import com.dto.response.CartResponse;
import com.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Cart management APIs")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    @Operation(summary = "Add item to cart")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddToCartRequest request) {
        return ApiResponse.success(cartService.addItem(getEmail(), request));
    }

    @GetMapping
    @Operation(summary = "Get current user cart")
    public ApiResponse<CartResponse> getCart() {
        return ApiResponse.success(cartService.getCart(getEmail()));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update cart item quantity")
    public ApiResponse<CartResponse> updateItem(@PathVariable Long itemId,
                                                @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.success(cartService.updateItem(getEmail(), itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove item from cart")
    public ApiResponse<CartResponse> removeItem(@PathVariable Long itemId) {
        return ApiResponse.success(cartService.removeItem(getEmail(), itemId));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Clear all items in cart")
    public ApiResponse<Void> clearCart() {
        cartService.clearCart(getEmail());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Cart cleared successfully")
                .build();
    }

    private String getEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}