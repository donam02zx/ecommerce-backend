package com.service;

import com.dto.response.OrderResponse;
import com.dto.response.PaymentResponse;
import com.entity.*;
import com.entity.enums.OrderStatus;
import com.entity.enums.PaymentStatus;
import com.entity.enums.StockTransactionType;
import com.event.OrderFailedEvent;
import com.event.OrderPaidEvent;
import com.exception.AppException;
import com.exception.PaymentBusinessException;
import com.messaging.EmailProducer;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentGatewayClient paymentGatewayClient;
    private final EmailProducer emailProducer;

    /**
     * RESERVED → PAID
     * - Tạo payment record với status PENDING
     * - Gọi payment gateway (có retry)
     * - Nếu success: PAID, SUCCESS, gửi email
     * - Nếu fail sau retry: PAYMENT_FAILED, FAILED, gửi email thất bại
     */
    @Transactional
    public PaymentResponse pay(String email, Long orderId) {
        OrderEntity order = findOrderAndValidateOwner(email, orderId);

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw AppException.badRequest("Only RESERVED orders can be paid. Current status: " + order.getStatus());
        }

        // 👉 BƯỚC 1: Tạo payment với status PENDING
        PaymentEntity payment = PaymentEntity.builder()
                .order(order)
                .amount(order.getTotalPrice())
                .status(PaymentStatus.PENDING)
                .paymentMethod("MOCK")
                .transactionRef("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
        paymentRepository.save(payment);

        log.info("📝 Created payment with PENDING status for order {}", orderId);

        String transactionRef = null;

        try {
            // 👉 BƯỚC 2: Gọi payment gateway (tự động retry)
            transactionRef = paymentGatewayClient.callPaymentGateway(order);

            // ✅ SUCCESS: Update payment thành SUCCESS
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionRef(transactionRef);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);

            // Trừ stock thực sự
            for (OrderItemEntity item : order.getItems()) {
                InventoryEntity inventory = inventoryRepository.findByProductId(item.getProduct().getId())
                        .orElseThrow(() -> AppException.notFound("Inventory not found for product: " + item.getProduct().getId()));

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
                        .note("Sold in order #" + orderId)
                        .build());
            }

            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);

            log.info("✅ Order paid: orderId={}, ref={}", orderId, transactionRef);

            emailProducer.sendOrderPaidMessage(order, email);
            log.info("📤 OrderPaidEvent published for order {}", orderId);

            return PaymentResponse.from(payment);

        } catch (PaymentBusinessException e) {
            // ❌ FAIL - Lỗi nghiệp vụ hoặc retry failed sau 3 lần
            log.warn("❌ Payment failed for order {}: {}", orderId, e.getMessage());

            payment.setStatus(PaymentStatus.FAILED);
            payment.setTransactionRef("FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            paymentRepository.save(payment);

            releaseStock(order, orderId);

            order.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);

            String reason = e.getReason() != null ? e.getReason() : e.getMessage();
            emailProducer.sendOrderFailedMessage(order, email, reason);
            log.info("📤 OrderFailedEvent published for order {}", orderId);

            throw AppException.badRequest("Payment failed: " + reason);

        } catch (Exception e) {
            // ❌ Unexpected error
            log.error("❌ Unexpected error during payment for order {}: {}", orderId, e.getMessage(), e);

            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            throw AppException.internalServerError("Payment failed: " + e.getMessage());
        }
    }

    /**
     * RESERVED → PAYMENT_FAILED (Manual fail)
     */
    @Transactional
    public PaymentResponse paymentFail(String email, Long orderId) {
        OrderEntity order = findOrderAndValidateOwner(email, orderId);

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw AppException.badRequest("Only RESERVED orders can fail payment. Current status: " + order.getStatus());
        }

        PaymentEntity payment = PaymentEntity.builder()
                .order(order)
                .amount(order.getTotalPrice())
                .status(PaymentStatus.FAILED)
                .paymentMethod("MOCK")
                .transactionRef("FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
        paymentRepository.save(payment);

        releaseStock(order, orderId);

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);

        log.info("Order payment failed: orderId={}", orderId);

        String reason = "Thanh toán thất bại do lỗi từ cổng thanh toán (MOCK)";
        eventPublisher.publishEvent(new OrderFailedEvent(order, email, reason));
        log.info("📤 OrderFailedEvent published for order {}", orderId);

        // ✅ TRẢ VỀ RESPONSE
        return PaymentResponse.from(payment);
    }

    /**
     * RESERVED hoặc PAID → CANCELLED
     */
    @Transactional
    public OrderResponse cancel(String email, Long orderId) {
        OrderEntity order = findOrderAndValidateOwner(email, orderId);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw AppException.badRequest("COMPLETED orders cannot be cancelled");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw AppException.badRequest("Order is already cancelled");
        }
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            throw AppException.badRequest("PAYMENT_FAILED orders cannot be cancelled");
        }

        if (order.getStatus() == OrderStatus.RESERVED) {
            releaseStock(order, orderId);
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("Order cancelled: orderId={}", orderId);
        return OrderResponse.from(order);
    }

    /**
     * PAID → COMPLETED
     */
    @Transactional
    public OrderResponse complete(String email, Long orderId) {
        OrderEntity order = findOrderAndValidateOwner(email, orderId);

        if (order.getStatus() != OrderStatus.PAID) {
            throw AppException.badRequest("Only PAID orders can be completed. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        log.info("Order completed: orderId={}", orderId);
        return OrderResponse.from(order);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private OrderEntity findOrderAndValidateOwner(String email, Long orderId) {
        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> AppException.notFound("Order not found: " + orderId));
        if (!order.getUser().getEmail().equals(email)) {
            throw AppException.badRequest("Order does not belong to current user");
        }
        return order;
    }

    private void releaseStock(OrderEntity order, Long orderId) {
        for (OrderItemEntity item : order.getItems()) {
            InventoryEntity inventory = inventoryRepository.findByProductId(item.getProduct().getId())
                    .orElseThrow(() -> AppException.notFound("Inventory not found"));

            int before = inventory.getQuantity();
            inventory.setReservedQuantity(inventory.getReservedQuantity() - item.getQuantity());
            inventoryRepository.save(inventory);

            stockTransactionRepository.save(StockTransactionEntity.builder()
                    .product(item.getProduct())
                    .type(StockTransactionType.RELEASE)
                    .quantity(item.getQuantity())
                    .quantityBefore(before)
                    .quantityAfter(inventory.getQuantity())
                    .referenceType("ORDER")
                    .referenceId(orderId)
                    .note("Released from order #" + orderId)
                    .build());
        }
        log.info("Stock released for order: orderId={}", orderId);
    }

    /**
     * Lấy payment theo orderId
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> AppException.notFound("Payment not found for order: " + orderId));
        return PaymentResponse.from(payment);
    }
}