package com.dto.response;

import com.entity.OrderItemEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subTotal;

    public static OrderItemResponse from(OrderItemEntity e) {
        return OrderItemResponse.builder()
                .id(e.getId())
                .productId(e.getProduct().getId())
                .productName(e.getProduct().getName())
                .quantity(e.getQuantity())
                .price(e.getPrice())
                .subTotal(e.getPrice().multiply(BigDecimal.valueOf(e.getQuantity())))
                .build();
    }
}