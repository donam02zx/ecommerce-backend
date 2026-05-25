package com.dto.response;

import com.entity.CartItemEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subTotal;

    public static CartItemResponse from(CartItemEntity e) {
        return CartItemResponse.builder()
                .id(e.getId())
                .productId(e.getProduct().getId())
                .productName(e.getProduct().getName())
                .productSku(e.getProduct().getSku())
                .quantity(e.getQuantity())
                .unitPrice(e.getUnitPrice())
                .subTotal(e.getUnitPrice().multiply(BigDecimal.valueOf(e.getQuantity())))
                .build();
    }
}