package com.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO để serialize/deserialize qua RabbitMQ
 * Không dùng OrderEntity trực tiếp vì entity có lazy fields
 * và không serialize được qua JSON
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    private Long orderId;
    private String userEmail;
    private String type;          // "PAID" hoặc "FAILED"
    private BigDecimal totalPrice;
    private Instant createdAt;
    private String reason;        // chỉ dùng cho FAILED
    private List<OrderItemData> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemData {
        private String productName;
        private String productSku;
        private int quantity;
        private BigDecimal price;

        public BigDecimal subTotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }
}