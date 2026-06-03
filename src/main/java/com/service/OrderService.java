package com.service;

import com.dto.request.CreateOrderRequest;
import com.dto.request.OrderItemRequest;
import com.dto.response.OrderResponse;
import com.dto.response.PageResponse;
import com.entity.*;
import com.entity.enums.CartStatus;
import com.entity.enums.OrderStatus;
import com.entity.enums.StockTransactionType;
import com.exception.AppException;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
        try {
            return doCreateOrder(email, request);
        } catch (OptimisticLockingFailureException e) {
            log.warn("[ORDER] Optimistic lock conflict for user={}, retrying...", email);
            try {
                return doCreateOrder(email, request);
            } catch (OptimisticLockingFailureException ex) {
                throw new AppException(HttpStatus.CONFLICT,
                        "Product is being ordered by another user, please try again");
            }
        }
    }

    @Transactional
    public OrderResponse doCreateOrder(String email, CreateOrderRequest request) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));

        List<OrderItemEntity> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            ProductEntity product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> AppException.notFound("Product not found: " + itemReq.getProductId()));
            if (!product.getActive()) throw AppException.badRequest("Product is not active: " + product.getName());

            InventoryEntity inventory = inventoryRepository.findByProductId(product.getId())
                    .orElseThrow(() -> AppException.badRequest("No inventory for product: " + product.getName()));
            int available = inventory.getQuantity() - inventory.getReservedQuantity();
            if (itemReq.getQuantity() > available)
                throw AppException.badRequest("Not enough stock for: " + product.getName()
                        + ". Available: " + available + ", requested: " + itemReq.getQuantity());

            int before = inventory.getQuantity();
            inventory.setReservedQuantity(inventory.getReservedQuantity() + itemReq.getQuantity());
            inventoryRepository.save(inventory);
            stockTransactionRepository.save(StockTransactionEntity.builder()
                    .product(product).type(StockTransactionType.RESERVE)
                    .quantity(itemReq.getQuantity()).quantityBefore(before)
                    .quantityAfter(inventory.getQuantity()).referenceType("ORDER")
                    .note("Reserved for order").build());

            orderItems.add(OrderItemEntity.builder()
                    .product(product).quantity(itemReq.getQuantity()).price(product.getPrice()).build());
            totalPrice = totalPrice.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        OrderEntity order = OrderEntity.builder()
                .user(user).status(OrderStatus.RESERVED)
                .totalPrice(totalPrice).items(new ArrayList<>()).build();
        orderRepository.save(order);

        for (OrderItemEntity item : orderItems) {
            OrderItemEntity saved = OrderItemEntity.builder()
                    .order(order).product(item.getProduct())
                    .quantity(item.getQuantity()).price(item.getPrice()).build();
            orderItemRepository.save(saved);
            order.getItems().add(saved);
        }

        cartRepository.findByUserIdAndStatus(user.getId(), CartStatus.ACTIVE)
                .ifPresent(cart -> { cart.getItems().clear(); cartRepository.save(cart); });

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
    public OrderResponse getOrderById(String email, Long orderId, boolean isAdminOrStaff) {
        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> AppException.notFound("Order not found: " + orderId));
        // CUSTOMER chỉ xem order của mình, ADMIN/STAFF xem được tất cả
        if (!isAdminOrStaff) {
            UserEntity user = userRepository.findByEmail(email)
                    .orElseThrow(() -> AppException.notFound("User not found"));
            if (!order.getUser().getId().equals(user.getId()))
                throw AppException.badRequest("Order does not belong to current user");
        }
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> searchOrders(String email, String status,
                                                    String fromDate, String toDate,
                                                    int page, int limit, boolean isAdminOrStaff) {
        OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            try { orderStatus = OrderStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException e) { throw AppException.badRequest("Invalid status: " + status); }
        }

        Instant from = fromDate != null && !fromDate.isBlank() ? Instant.parse(fromDate) : null;
        Instant to   = toDate   != null && !toDate.isBlank()   ? Instant.parse(toDate)   : null;

        PageRequest pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());

        Page<OrderEntity> result;
        if (isAdminOrStaff) {
            // ADMIN/STAFF xem tất cả order
            result = orderRepository.searchAll(orderStatus, from, to, pageable);
        } else {
            // CUSTOMER chỉ xem order của mình
            UserEntity user = userRepository.findByEmail(email)
                    .orElseThrow(() -> AppException.notFound("User not found"));
            result = orderRepository.searchByUser(user.getId(), orderStatus, from, to, pageable);
        }

        return PageResponse.<OrderResponse>builder()
                .content(result.getContent().stream()
                        .map(o -> OrderResponse.from(orderRepository.findByIdWithItems(o.getId()).orElse(o)))
                        .toList())
                .page(page).limit(limit)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .hasPrevious(result.hasPrevious())
                .build();
    }
}