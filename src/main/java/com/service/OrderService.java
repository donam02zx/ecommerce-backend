package com.service;

import com.dto.request.CreateOrderRequest;
import com.dto.request.OrderItemRequest;
import com.dto.response.OrderResponse;
import com.entity.*;
import com.entity.enums.CartStatus;
import com.entity.enums.OrderStatus;
import com.entity.enums.StockTransactionType;
import com.exception.AppException;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final CartRepository cartRepository;

    @Transactional
    public OrderResponse createOrder(String email, CreateOrderRequest request) {
        // 1. Validate user
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));

        // 2. Validate products và check inventory
        List<OrderItemEntity> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            // Validate product
            ProductEntity product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> AppException.notFound("Product not found: " + itemReq.getProductId()));
            if (!product.getActive()) {
                throw AppException.badRequest("Product is not active: " + product.getName());
            }

            // Check inventory
            InventoryEntity inventory = inventoryRepository.findByProductId(product.getId())
                    .orElseThrow(() -> AppException.badRequest("No inventory found for product: " + product.getName()));
            int available = inventory.getQuantity() - inventory.getReservedQuantity();
            if (itemReq.getQuantity() > available) {
                throw AppException.badRequest("Not enough stock for: " + product.getName()
                        + ". Available: " + available + ", requested: " + itemReq.getQuantity());
            }

            // 3. Reserve stock
            int before = inventory.getQuantity();
            inventory.setReservedQuantity(inventory.getReservedQuantity() + itemReq.getQuantity());
            inventoryRepository.save(inventory);

            // Ghi stock transaction
            stockTransactionRepository.save(StockTransactionEntity.builder()
                    .product(product)
                    .type(StockTransactionType.RESERVE)
                    .quantity(itemReq.getQuantity())
                    .quantityBefore(before)
                    .quantityAfter(inventory.getQuantity())
                    .referenceType("ORDER")
                    .note("Reserved for order")
                    .build());

            // Build order item — lưu price tại thời điểm mua
            OrderItemEntity orderItem = OrderItemEntity.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .price(product.getPrice())
                    .build();
            orderItems.add(orderItem);
            totalPrice = totalPrice.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        // 4. Create order
        OrderEntity order = OrderEntity.builder()
                .user(user)
                .status(OrderStatus.RESERVED)
                .totalPrice(totalPrice)
                .items(new ArrayList<>())
                .build();
        orderRepository.save(order);

        // 5. Save order items
        for (OrderItemEntity item : orderItems) {
            item = OrderItemEntity.builder()
                    .order(order)
                    .product(item.getProduct())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .build();
            orderItemRepository.save(item);
            order.getItems().add(item);
        }

        // 6. Clear cart
        cartRepository.findByUserIdAndStatus(user.getId(), CartStatus.ACTIVE)
                .ifPresent(cart -> {
                    cart.getItems().clear();
                    cartRepository.save(cart);
                    log.info("Cart cleared after order: cartId={}", cart.getId());
                });

        log.info("Order created: orderId={}, userId={}, total={}", order.getId(), user.getId(), totalPrice);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
        return orderRepository.findByUserId(user.getId()).stream()
                .map(o -> {
                    OrderEntity withItems = orderRepository.findByIdWithItems(o.getId()).orElse(o);
                    return OrderResponse.from(withItems);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String email, Long orderId) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> AppException.notFound("Order not found: " + orderId));
        if (!order.getUser().getId().equals(user.getId())) {
            throw AppException.badRequest("Order does not belong to current user");
        }
        return OrderResponse.from(order);
    }
}