package com.service;

import com.dto.response.OrderResponse;
import com.dto.response.PaymentResponse;
import com.entity.*;
import com.entity.enums.OrderStatus;
import com.entity.enums.PaymentStatus;
import com.entity.enums.StockTransactionType;
import com.exception.AppException;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * RESERVED → PAID
     * - Tạo payment record với status SUCCESS
     * - Trừ thực sự vào quantity, giảm reserved_quantity
     * - Ghi stock_transaction EXPORT
     */
    @Transactional
    public PaymentResponse pay(String email, Long orderId) {
        OrderEntity order = findOrderAndValidateOwner(email, orderId);

        // Chỉ order RESERVED mới được pay
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw AppException.badRequest("Only RESERVED orders can be paid. Current status: " + order.getStatus());
        }

        // Tạo payment
        PaymentEntity payment = PaymentEntity.builder()
                .order(order)
                .amount(order.getTotalPrice())
                .status(PaymentStatus.SUCCESS)
                .paymentMethod("MOCK")
                .transactionRef("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .paidAt(Instant.now())
                .build();
        paymentRepository.save(payment);

        // Trừ stock thực sự cho từng order item
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

        // RESERVED → PAID
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        log.info("Order paid: orderId={}, ref={}", orderId, payment.getTransactionRef());
        return PaymentResponse.from(payment);
    }

    /**
     * RESERVED → PAYMENT_FAILED
     * - Tạo payment record với status FAILED
     * - Release reserved stock
     */
    @Transactional
    public PaymentResponse paymentFail(String email, Long orderId) {
        OrderEntity order = findOrderAndValidateOwner(email, orderId);

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw AppException.badRequest("Only RESERVED orders can fail payment. Current status: " + order.getStatus());
        }

        // Tạo payment failed record
        PaymentEntity payment = PaymentEntity.builder()
                .order(order)
                .amount(order.getTotalPrice())
                .status(PaymentStatus.FAILED)
                .paymentMethod("MOCK")
                .transactionRef("FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
        paymentRepository.save(payment);

        // Release reserved stock
        releaseStock(order, orderId);

        // RESERVED → PAYMENT_FAILED
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);

        log.info("Order payment failed: orderId={}", orderId);
        return PaymentResponse.from(payment);
    }

    /**
     * RESERVED hoặc PAID → CANCELLED
     * - Release reserved stock nếu còn RESERVED
     * - COMPLETED không được cancel
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

        // Chỉ release stock nếu đang RESERVED (tránh release 2 lần)
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
     * - Trạng thái cuối, không thể thay đổi sau đó
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

    // --- helpers ---

    private OrderEntity findOrderAndValidateOwner(String email, Long orderId) {
        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> AppException.notFound("Order not found: " + orderId));
        if (!order.getUser().getEmail().equals(email)) {
            throw AppException.badRequest("Order does not belong to current user");
        }
        return order;
    }

    /**
     * Release reserved stock — dùng khi cancel hoặc payment fail
     * Chỉ giảm reserved_quantity, không giảm quantity thực
     */
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
}