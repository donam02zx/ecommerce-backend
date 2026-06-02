package com.controller;

import com.dto.response.ApiResponse;
import com.dto.response.OrderResponse;
import com.dto.response.PaymentResponse;
import com.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment and order status APIs")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pay order - RESERVED → PAID")
    public ApiResponse<PaymentResponse> pay(@PathVariable Long id) {
        return ApiResponse.success(paymentService.pay(getEmail(), id));
    }

    @PostMapping("/{id}/payment-fail")
    @Operation(summary = "Simulate payment failure - RESERVED → PAYMENT_FAILED")
    public ApiResponse<PaymentResponse> paymentFail(@PathVariable Long id) {
        return ApiResponse.success(paymentService.paymentFail(getEmail(), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order - RESERVED/PAID → CANCELLED")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(paymentService.cancel(getEmail(), id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete order - PAID → COMPLETED")
    public ApiResponse<OrderResponse> complete(@PathVariable Long id) {
        return ApiResponse.success(paymentService.complete(getEmail(), id));
    }

    private String getEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}