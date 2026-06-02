package com.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VNPayResponse {
    private String paymentUrl;
    private Long orderId;
    private String message;
}