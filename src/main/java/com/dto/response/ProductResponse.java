package com.dto.response;

import com.entity.ProductEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private Boolean active;
    private Long categoryId;
    private String categoryName;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductResponse from(ProductEntity e) {
        return ProductResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .sku(e.getSku())
                .description(e.getDescription())
                .price(e.getPrice())
                .active(e.getActive())
                .categoryId(e.getCategory().getId())
                .categoryName(e.getCategory().getName())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}