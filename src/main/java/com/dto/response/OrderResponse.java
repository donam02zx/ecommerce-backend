package com.dto.response;

import com.entity.OrderEntity;
import com.entity.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private Long userId;
    private OrderStatus status;
    private BigDecimal totalPrice;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderResponse from(OrderEntity e) {
        List<OrderItemResponse> items = e.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return OrderResponse.builder()
                .id(e.getId())
                .userId(e.getUser().getId())
                .status(e.getStatus())
                .totalPrice(e.getTotalPrice())
                .items(items)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}