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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private CartRepository cartRepository;

    @InjectMocks
    private OrderService orderService;

    // ── Test Fixtures ────────────────────────────────────────────────────────
    private static final String USER_EMAIL = "customer@test.com";
    private static final Long USER_ID      = 1L;
    private static final Long PRODUCT_ID   = 10L;
    private static final Long PRODUCT_ID_2 = 11L;

    private UserEntity mockUser;
    private ProductEntity mockProduct;
    private ProductEntity mockProduct2;
    private InventoryEntity mockInventory;
    private InventoryEntity mockInventory2;
    private CategoriesEntity mockCategory;

    @BeforeEach
    void setUp() {
        mockCategory = CategoriesEntity.builder()
                .id(1L).name("Electronics").active(true).build();

        mockUser = UserEntity.builder()
                .id(USER_ID).email(USER_EMAIL)
                .fullName("Test User").active(true)
                .userRoles(new HashSet<>()).build();

        mockProduct = ProductEntity.builder()
                .id(PRODUCT_ID).name("Wireless Mouse").sku("ELEC-001")
                .price(new BigDecimal("250000")).active(true)
                .category(mockCategory).build();

        mockProduct2 = ProductEntity.builder()
                .id(PRODUCT_ID_2).name("USB Hub").sku("ELEC-002")
                .price(new BigDecimal("450000")).active(true)
                .category(mockCategory).build();

        mockInventory = InventoryEntity.builder()
                .id(1L).product(mockProduct)
                .quantity(10).reservedQuantity(0).version(0L).build();

        mockInventory2 = InventoryEntity.builder()
                .id(2L).product(mockProduct2)
                .quantity(5).reservedQuantity(0).version(0L).build();
    }

    // ────────────────────────────────────────────────────────────────────────
    // HAPPY CASES
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Happy Cases")
    class HappyCases {

        @Test
        @DisplayName("Should create order successfully with valid user, active product, sufficient stock")
        void createOrder_success_singleItem() {
            // Arrange
            CreateOrderRequest request = buildRequest(PRODUCT_ID, 2);

            OrderEntity savedOrder = OrderEntity.builder()
                    .id(100L).user(mockUser).status(OrderStatus.RESERVED)
                    .totalPrice(new BigDecimal("500000")).items(new ArrayList<>()).build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(mockInventory));
            when(orderRepository.save(any(OrderEntity.class))).thenReturn(savedOrder);
            when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            // Act
            OrderResponse response = orderService.doCreateOrder(USER_EMAIL, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(OrderStatus.RESERVED);
            assertThat(response.getTotalPrice()).isEqualByComparingTo("500000");

            // Verify reserved_quantity was increased
            assertThat(mockInventory.getReservedQuantity()).isEqualTo(2);

            // Verify stock transaction was recorded
            ArgumentCaptor<StockTransactionEntity> txCaptor = ArgumentCaptor.forClass(StockTransactionEntity.class);
            verify(stockTransactionRepository).save(txCaptor.capture());
            StockTransactionEntity tx = txCaptor.getValue();
            assertThat(tx.getType()).isEqualTo(StockTransactionType.RESERVE);
            assertThat(tx.getQuantity()).isEqualTo(2);
            assertThat(tx.getQuantityBefore()).isEqualTo(10);
            assertThat(tx.getQuantityAfter()).isEqualTo(10); // quantity unchanged, only reserved increased

            // Verify order was saved
            verify(orderRepository).save(any(OrderEntity.class));
        }

        @Test
        @DisplayName("Should create order with multiple items and correct total price")
        void createOrder_success_multipleItems() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest();
            OrderItemRequest item1 = new OrderItemRequest();
            item1.setProductId(PRODUCT_ID);
            item1.setQuantity(2);
            OrderItemRequest item2 = new OrderItemRequest();
            item2.setProductId(PRODUCT_ID_2);
            item2.setQuantity(1);
            request.setItems(List.of(item1, item2));

            // expected total: 250000*2 + 450000*1 = 950000
            OrderEntity savedOrder = OrderEntity.builder()
                    .id(101L).user(mockUser).status(OrderStatus.RESERVED)
                    .totalPrice(new BigDecimal("950000")).items(new ArrayList<>()).build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(productRepository.findById(PRODUCT_ID_2)).thenReturn(Optional.of(mockProduct2));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(mockInventory));
            when(inventoryRepository.findByProductId(PRODUCT_ID_2)).thenReturn(Optional.of(mockInventory2));
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(orderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            // Act
            OrderResponse response = orderService.doCreateOrder(USER_EMAIL, request);

            // Assert
            assertThat(response.getTotalPrice()).isEqualByComparingTo("950000");
            assertThat(mockInventory.getReservedQuantity()).isEqualTo(2);
            assertThat(mockInventory2.getReservedQuantity()).isEqualTo(1);
            verify(stockTransactionRepository, times(2)).save(any(StockTransactionEntity.class));
        }

        @Test
        @DisplayName("Should clear active cart after successful order creation")
        void createOrder_shouldClearCart() {
            // Arrange
            CartEntity activeCart = CartEntity.builder()
                    .id(1L).user(mockUser).status(CartStatus.ACTIVE)
                    .items(new ArrayList<>()).build();
            activeCart.getItems().add(CartItemEntity.builder()
                    .id(1L).cart(activeCart).product(mockProduct).quantity(1)
                    .unitPrice(mockProduct.getPrice()).build());

            OrderEntity savedOrder = OrderEntity.builder()
                    .id(102L).user(mockUser).status(OrderStatus.RESERVED)
                    .totalPrice(new BigDecimal("250000")).items(new ArrayList<>()).build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(mockInventory));
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(orderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(activeCart));

            // Act
            orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 1));

            // Assert: cart items cleared and saved
            assertThat(activeCart.getItems()).isEmpty();
            verify(cartRepository).save(activeCart);
        }

        @Test
        @DisplayName("Should store price at purchase time, not current product price")
        void createOrder_shouldSnapshotPriceAtPurchaseTime() {
            // Arrange
            OrderEntity savedOrder = OrderEntity.builder()
                    .id(103L).user(mockUser).status(OrderStatus.RESERVED)
                    .totalPrice(new BigDecimal("250000")).items(new ArrayList<>()).build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(mockInventory));
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(orderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            // Act
            orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 1));

            // Assert: order item saved with product's price at time of order
            ArgumentCaptor<OrderItemEntity> itemCaptor = ArgumentCaptor.forClass(OrderItemEntity.class);
            verify(orderItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getPrice())
                    .isEqualByComparingTo(mockProduct.getPrice());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // EXCEPTION CASES
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Exception Cases")
    class ExceptionCases {

        @Test
        @DisplayName("Should throw 404 when user not found")
        void createOrder_shouldThrow404_whenUserNotFound() {
            // Arrange
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 1)))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(appEx.getMessage()).containsIgnoringCase("user not found");
                    });

            // Verify no order was created
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 404 when product not found")
        void createOrder_shouldThrow404_whenProductNotFound() {
            // Arrange
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 1)))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(appEx.getMessage()).containsIgnoringCase("product not found");
                    });

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when product is not active")
        void createOrder_shouldThrow400_whenProductInactive() {
            // Arrange
            mockProduct.setActive(false);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));

            // Act & Assert
            assertThatThrownBy(() -> orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 1)))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(appEx.getMessage()).containsIgnoringCase("not active");
                    });

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when inventory not found for product")
        void createOrder_shouldThrow400_whenInventoryNotFound() {
            // Arrange
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 1)))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when requested quantity exceeds available stock")
        void createOrder_shouldThrow400_whenInsufficientStock() {
            // Arrange: stock=10, reserved=8 → available=2, request=5
            mockInventory.setReservedQuantity(8);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(mockInventory));

            // Act & Assert
            assertThatThrownBy(() -> orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 5)))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(appEx.getMessage()).containsIgnoringCase("not enough stock");
                    });

            // Verify reserved_quantity was NOT changed
            assertThat(mockInventory.getReservedQuantity()).isEqualTo(8);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when stock is exactly 0 available")
        void createOrder_shouldThrow400_whenStockIsZero() {
            // Arrange: quantity=5, reserved=5 → available=0
            mockInventory.setQuantity(5);
            mockInventory.setReservedQuantity(5);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(mockInventory));

            // Act & Assert
            assertThatThrownBy(() -> orderService.doCreateOrder(USER_EMAIL, buildRequest(PRODUCT_ID, 1)))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should rollback: second product fails but first product stock should not be persisted")
        void createOrder_shouldNotReserveStock_whenLaterItemFails() {
            // Arrange: item1 valid, item2 product not found
            CreateOrderRequest request = new CreateOrderRequest();
            OrderItemRequest item1 = new OrderItemRequest();
            item1.setProductId(PRODUCT_ID); item1.setQuantity(1);
            OrderItemRequest item2 = new OrderItemRequest();
            item2.setProductId(PRODUCT_ID_2); item2.setQuantity(1);
            request.setItems(List.of(item1, item2));

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(mockInventory));
            when(productRepository.findById(PRODUCT_ID_2)).thenReturn(Optional.empty()); // product2 missing

            // Act & Assert
            assertThatThrownBy(() -> orderService.doCreateOrder(USER_EMAIL, request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));

            // No order persisted
            verify(orderRepository, never()).save(any());
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private CreateOrderRequest buildRequest(Long productId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }
}