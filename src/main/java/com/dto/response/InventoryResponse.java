package com.dto.response;

import com.entity.InventoryEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class InventoryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Instant updatedAt;

    public static InventoryResponse from(InventoryEntity e) {
        return InventoryResponse.builder()
                .id(e.getId())
                .productId(e.getProduct().getId())
                .productName(e.getProduct().getName())
                .productSku(e.getProduct().getSku())
                .quantity(e.getQuantity())
                .reservedQuantity(e.getReservedQuantity())
                .availableQuantity(e.getQuantity() - e.getReservedQuantity())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}