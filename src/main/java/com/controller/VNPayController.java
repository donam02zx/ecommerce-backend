package com.controller;

import com.dto.response.ApiResponse;
import com.dto.response.VNPayResponse;
import com.entity.*;
import com.entity.enums.OrderStatus;
import com.entity.enums.PaymentStatus;
import com.entity.enums.StockTransactionType;
import com.exception.AppException;
import com.repository.*;
import com.service.VNPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "VNPay", description = "VNPay payment integration")
public class VNPayController {

    private final VNPayService vnPayService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;

    @PostMapping("/vnpay-create/{orderId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create VNPay payment URL for order")
    @Transactional(readOnly = true)
    public ApiResponse<VNPayResponse> createPayment(@PathVariable Long orderId,
                                                    HttpServletRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> AppException.notFound("Order not found: " + orderId));

        if (!order.getUser().getEmail().equals(email)) {
            throw AppException.badRequest("Order does not belong to current user");
        }
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw AppException.badRequest("Only RESERVED orders can be paid. Current: " + order.getStatus());
        }

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null) ipAddress = request.getRemoteAddr();

        long amountVnd = order.getTotalPrice().longValue();
        String orderInfo = "Thanh toan don hang " + orderId;
        String paymentUrl = vnPayService.createPaymentUrl(orderId, amountVnd, orderInfo, ipAddress);

        log.info("VNPay payment URL created: orderId={}", orderId);
        return ApiResponse.success(VNPayResponse.builder()
                .paymentUrl(paymentUrl)
                .orderId(orderId)
                .message("Open paymentUrl in browser to complete payment")
                .build());
    }

    @GetMapping("/vnpay-return")
    @Operation(summary = "VNPay payment callback (return URL)")
    @Transactional
    public ApiResponse<String> vnpayReturn(@RequestParam Map<String, String> params) {
        log.info("VNPay callback received: responseCode={}", params.get("vnp_ResponseCode"));

        if (!vnPayService.verifySignature(params)) {
            log.error("VNPay signature verification FAILED");
            return ApiResponse.<String>builder()
                    .success(false)
                    .message("Invalid signature")
                    .build();
        }

        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef");
        Long orderId = Long.parseLong(txnRef.split("_")[0]);

        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> AppException.notFound("Order not found: " + orderId));

        if ("00".equals(responseCode)) {
            PaymentEntity payment = PaymentEntity.builder()
                    .order(order)
                    .amount(order.getTotalPrice())
                    .status(PaymentStatus.SUCCESS)
                    .paymentMethod("VNPAY")
                    .transactionRef(params.get("vnp_TransactionNo"))
                    .paidAt(Instant.now())
                    .build();
            paymentRepository.save(payment);

            for (OrderItemEntity item : order.getItems()) {
                InventoryEntity inventory = inventoryRepository.findByProductId(item.getProduct().getId())
                        .orElseThrow(() -> AppException.notFound("Inventory not found"));
                int before = inventory.getQuantity();
                inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
                inventory.setReservedQuantity(inventory.getReservedQuantity() - item.getQuantity());
                inventoryRepository.save(inventory);

                stockTransactionRepository.save(StockTransactionEntity.builder()
                        .product(item.getProduct())
                        .type(StockTransactionType.EXPORT)
                        .quantity(item.getQuantity())
                        .quantityBefore(before)
                        .quantityAfter(inventory.getQuantity())
                        .referenceType("ORDER")
                        .referenceId(orderId)
                        .note("VNPay payment - order #" + orderId)
                        .build());
            }

            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            log.info("VNPay payment SUCCESS: orderId={}", orderId);
            return ApiResponse.success("Payment successful! Order #" + orderId + " is now PAID.");

        } else {
            PaymentEntity payment = PaymentEntity.builder()
                    .order(order)
                    .amount(order.getTotalPrice())
                    .status(PaymentStatus.FAILED)
                    .paymentMethod("VNPAY")
                    .transactionRef(params.get("vnp_TransactionNo"))
                    .build();
            paymentRepository.save(payment);

            for (OrderItemEntity item : order.getItems()) {
                InventoryEntity inventory = inventoryRepository.findByProductId(item.getProduct().getId())
                        .orElseThrow(() -> AppException.notFound("Inventory not found"));
                inventory.setReservedQuantity(inventory.getReservedQuantity() - item.getQuantity());
                inventoryRepository.save(inventory);
            }

            order.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
            log.info("VNPay payment FAILED: orderId={}, responseCode={}", orderId, responseCode);
            return ApiResponse.<String>builder()
                    .success(false)
                    .message("Payment failed! Response code: " + responseCode)
                    .build();
        }
    }
}