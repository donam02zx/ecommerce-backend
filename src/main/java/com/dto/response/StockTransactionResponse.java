package com.dto.response;

import com.entity.StockTransactionEntity;
import com.entity.enums.StockTransactionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class StockTransactionResponse {
    private Long id;
    private Long productId;
    private String productName;
    private StockTransactionType type;
    private Integer quantity;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private String referenceType;
    private Long referenceId;
    private String note;
    private Instant createdAt;

    public static StockTransactionResponse from(StockTransactionEntity e) {
        return StockTransactionResponse.builder()
                .id(e.getId())
                .productId(e.getProduct().getId())
                .productName(e.getProduct().getName())
                .type(e.getType())
                .quantity(e.getQuantity())
                .quantityBefore(e.getQuantityBefore())
                .quantityAfter(e.getQuantityAfter())
                .referenceType(e.getReferenceType())
                .referenceId(e.getReferenceId())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .build();
    }
}