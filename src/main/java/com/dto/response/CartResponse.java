package com.dto.response;

import com.entity.CartEntity;
import com.entity.enums.CartStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CartResponse {
    private Long id;
    private Long userId;
    private CartStatus status;
    private List<CartItemResponse> items;
    private BigDecimal totalPrice;

    public static CartResponse from(CartEntity e) {
        List<CartItemResponse> items = e.getItems().stream()
                .map(CartItemResponse::from)
                .toList();
        BigDecimal total = items.stream()
                .map(CartItemResponse::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CartResponse.builder()
                .id(e.getId())
                .userId(e.getUser().getId())
                .status(e.getStatus())
                .items(items)
                .totalPrice(total)
                .build();
    }
}