package com.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TopProductResponse {
    private Long productId;
    private String name;
    private String sku;
    private BigDecimal price;
    private Boolean active;
    private Long categoryId;
    private String categoryName;
    private Long totalQuantitySold;  // Tổng số lượng đã bán


//    public static TopProductResponse fromObjectArray(Object[] row) {
//        return TopProductResponse.builder()
//                .productId((Long) row[0])
//                .name((String) row[1])
//                .sku((String) row[2])
//                .price((BigDecimal) row[3])
//                .active((Boolean) row[4])
//                .categoryId((Long) row[5])
//                .categoryName((String) row[6])
//                .totalQuantitySold(((Number) row[7]).longValue())
//                .build();
//    }
}