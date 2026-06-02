package com.dto.response;

import com.entity.PaymentEntity;
import com.entity.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String paymentMethod;
    private String transactionRef;
    private Instant paidAt;
    private Instant createdAt;

    public static PaymentResponse from(PaymentEntity e) {
        return PaymentResponse.builder()
                .id(e.getId())
                .orderId(e.getOrder().getId())
                .amount(e.getAmount())
                .status(e.getStatus())
                .paymentMethod(e.getPaymentMethod())
                .transactionRef(e.getTransactionRef())
                .paidAt(e.getPaidAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}