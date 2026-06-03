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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
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

    /**
     * Transaction flow:
     * 1. Validate user
     * 2. Validate product (exists + active)
     * 3. Check inventory (enough stock)
     * 4. Reserve stock (update reserved_quantity + ghi stock_transaction)
     * 5. Create order
     * 6. Create order items (lưu price tại thời điểm mua)
     * 7. Clear cart
     *
     * Optimistic Locking: @Version trên InventoryEntity
     * Nếu 2 request cùng lúc reserve stock → chỉ 1 thành công,
     * cái còn lại bị OptimisticLockingFailureException → trả 409 CONFLICT
     */
    @Transactional
    public OrderResponse createOrder(String email, CreateOrderRequest request) {
        try {
            return doCreateOrder(email, request);
        } catch (OptimisticLockingFailureException e) {
            log.warn("[ORDER] Optimistic lock conflict for user={}, retrying...", email);
            // Retry 1 lần
            try {
                return doCreateOrder(email, request);
            } catch (OptimisticLockingFailureException ex) {
                log.error("[ORDER] Optimistic lock conflict after retry for user={}", email);
                throw new AppException(HttpStatus.CONFLICT,
                        "Product is being ordered by another user, please try again");
            }
        }
    }

    @Transactional
    public OrderResponse doCreateOrder(String email, CreateOrderRequest request) {
        // STEP 1: Validate user
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
        log.info("[ORDER] Step 1 - Validated user: {}", email);

        List<OrderItemEntity> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {

            // STEP 2: Validate product
            ProductEntity product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> AppException.notFound("Product not found: " + itemReq.getProductId()));
            if (!product.getActive()) {
                throw AppException.badRequest("Product is not active: " + product.getName());
            }
            log.info("[ORDER] Step 2 - Validated product: {}", product.getName());

            // STEP 3: Check inventory
            InventoryEntity inventory = inventoryRepository.findByProductId(product.getId())
                    .orElseThrow(() -> AppException.badRequest("No inventory for product: " + product.getName()));
            int available = inventory.getQuantity() - inventory.getReservedQuantity();
            if (itemReq.getQuantity() > available) {
                throw AppException.badRequest("Not enough stock for: " + product.getName()
                        + ". Available: " + available + ", requested: " + itemReq.getQuantity());
            }
            log.info("[ORDER] Step 3 - Inventory checked: available={}", available);

            // STEP 4: Reserve stock — @Version tự động tăng, nếu conflict → OptimisticLockingFailureException
            int before = inventory.getQuantity();
            inventory.setReservedQuantity(inventory.getReservedQuantity() + itemReq.getQuantity());
            inventoryRepository.save(inventory);
            stockTransactionRepository.save(StockTransactionEntity.builder()
                    .product(product)
                    .type(StockTransactionType.RESERVE)
                    .quantity(itemReq.getQuantity())
                    .quantityBefore(before)
                    .quantityAfter(inventory.getQuantity())
                    .referenceType("ORDER")
                    .note("Reserved for order")
                    .build());
            log.info("[ORDER] Step 4 - Stock reserved: productId={}, qty={}", product.getId(), itemReq.getQuantity());

            orderItems.add(OrderItemEntity.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .price(product.getPrice())
                    .build());
            totalPrice = totalPrice.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        // STEP 5: Create order
        OrderEntity order = OrderEntity.builder()
                .user(user)
                .status(OrderStatus.RESERVED)
                .totalPrice(totalPrice)
                .items(new ArrayList<>())
                .build();
        orderRepository.save(order);
        log.info("[ORDER] Step 5 - Order created: orderId={}", order.getId());

        // STEP 6: Create order items
        for (OrderItemEntity item : orderItems) {
            OrderItemEntity savedItem = OrderItemEntity.builder()
                    .order(order)
                    .product(item.getProduct())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .build();
            orderItemRepository.save(savedItem);
            order.getItems().add(savedItem);
        }
        log.info("[ORDER] Step 6 - Order items saved: count={}", orderItems.size());

        // STEP 7: Clear cart
        cartRepository.findByUserIdAndStatus(user.getId(), CartStatus.ACTIVE)
                .ifPresent(cart -> {
                    cart.getItems().clear();
                    cartRepository.save(cart);
                    log.info("[ORDER] Step 7 - Cart cleared: cartId={}", cart.getId());
                });

        log.info("[ORDER] Completed: orderId={}, total={}", order.getId(), totalPrice);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
        return orderRepository.findByUserId(user.getId()).stream()
                .map(o -> OrderResponse.from(orderRepository.findByIdWithItems(o.getId()).orElse(o)))
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