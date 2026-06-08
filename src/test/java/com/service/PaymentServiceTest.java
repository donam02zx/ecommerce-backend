package com.service;

import com.dto.response.OrderResponse;
import com.dto.response.PaymentResponse;
import com.entity.*;
import com.entity.enums.OrderStatus;
import com.entity.enums.PaymentStatus;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;

    @InjectMocks
    private PaymentService paymentService;

    private static final String USER_EMAIL = "customer@test.com";
    private static final Long ORDER_ID     = 1L;

    private UserEntity mockUser;
    private ProductEntity mockProduct;
    private InventoryEntity mockInventory;
    private OrderItemEntity mockOrderItem;
    private OrderEntity mockOrder;

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(1L).email(USER_EMAIL).fullName("Test User").build();

        mockProduct = ProductEntity.builder()
                .id(10L).name("Wireless Mouse").sku("ELEC-001")
                .price(new BigDecimal("250000")).active(true).build();

        mockInventory = InventoryEntity.builder()
                .id(1L).product(mockProduct)
                .quantity(10).reservedQuantity(2).version(0L).build();

        mockOrderItem = OrderItemEntity.builder()
                .id(1L).product(mockProduct)
                .quantity(2).price(new BigDecimal("250000")).build();

        mockOrder = OrderEntity.builder()
                .id(ORDER_ID).user(mockUser)
                .status(OrderStatus.RESERVED)
                .totalPrice(new BigDecimal("500000"))
                .items(new ArrayList<>(List.of(mockOrderItem)))
                .build();
        mockOrderItem.setOrder(mockOrder);
    }

    // ────────────────────────────────────────────────────────────────────────
    // PAY
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("pay()")
    class Pay {

        @Test
        @DisplayName("Should pay successfully: RESERVED → PAID, deduct quantity, clear reserved")
        void pay_success() {
            // Arrange
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));
            when(inventoryRepository.findByProductId(mockProduct.getId()))
                    .thenReturn(Optional.of(mockInventory));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            PaymentResponse response = paymentService.pay(USER_EMAIL, ORDER_ID);

            // Assert — payment record
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(response.getPaymentMethod()).isEqualTo("MOCK");
            assertThat(response.getTransactionRef()).startsWith("PAY-");

            // Assert — stock deducted: quantity 10→8, reserved 2→0
            assertThat(mockInventory.getQuantity()).isEqualTo(8);
            assertThat(mockInventory.getReservedQuantity()).isEqualTo(0);

            // Assert — stock transaction recorded as EXPORT
            ArgumentCaptor<StockTransactionEntity> txCaptor =
                    ArgumentCaptor.forClass(StockTransactionEntity.class);
            verify(stockTransactionRepository).save(txCaptor.capture());
            assertThat(txCaptor.getValue().getType()).isEqualTo(StockTransactionType.EXPORT);
            assertThat(txCaptor.getValue().getQuantityBefore()).isEqualTo(10);
            assertThat(txCaptor.getValue().getQuantityAfter()).isEqualTo(8);

            // Assert — order status changed to PAID
            assertThat(mockOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("Should throw 400 when order is not RESERVED")
        void pay_shouldThrow400_whenOrderNotReserved() {
            mockOrder.setStatus(OrderStatus.PAID);
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.pay(USER_EMAIL, ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(appEx.getMessage()).containsIgnoringCase("RESERVED");
                    });

            verify(paymentRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when trying to pay CANCELLED order")
        void pay_shouldThrow400_whenOrderCancelled() {
            mockOrder.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.pay(USER_EMAIL, ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when order does not belong to user")
        void pay_shouldThrow400_whenOrderNotBelongToUser() {
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.pay("other@test.com", ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PAYMENT FAIL
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("paymentFail()")
    class PaymentFail {

        @Test
        @DisplayName("Should fail payment: RESERVED → PAYMENT_FAILED, release reserved stock")
        void paymentFail_success() {
            // Arrange
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));
            when(inventoryRepository.findByProductId(mockProduct.getId()))
                    .thenReturn(Optional.of(mockInventory));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            PaymentResponse response = paymentService.paymentFail(USER_EMAIL, ORDER_ID);

            // Assert — payment record FAILED
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);

            // Assert — reserved released: reserved 2→0, quantity unchanged at 10
            assertThat(mockInventory.getQuantity()).isEqualTo(10); // quantity NOT changed
            assertThat(mockInventory.getReservedQuantity()).isEqualTo(0); // reserved released

            // Assert — stock transaction RELEASE
            ArgumentCaptor<StockTransactionEntity> txCaptor =
                    ArgumentCaptor.forClass(StockTransactionEntity.class);
            verify(stockTransactionRepository).save(txCaptor.capture());
            assertThat(txCaptor.getValue().getType()).isEqualTo(StockTransactionType.RELEASE);

            // Assert — order status PAYMENT_FAILED
            assertThat(mockOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("Should throw 400 when order is not RESERVED")
        void paymentFail_shouldThrow400_whenNotReserved() {
            mockOrder.setStatus(OrderStatus.PAID);
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.paymentFail(USER_EMAIL, ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));

            verify(paymentRepository, never()).save(any());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // CANCEL
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("Should cancel RESERVED order and release reserved stock")
        void cancel_reserved_shouldReleaseStock() {
            // Arrange
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));
            when(inventoryRepository.findByProductId(mockProduct.getId()))
                    .thenReturn(Optional.of(mockInventory));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            OrderResponse response = paymentService.cancel(USER_EMAIL, ORDER_ID);

            // Assert — reserved released
            assertThat(mockInventory.getReservedQuantity()).isEqualTo(0);
            assertThat(mockInventory.getQuantity()).isEqualTo(10); // quantity unchanged

            // Assert — RELEASE transaction recorded
            ArgumentCaptor<StockTransactionEntity> txCaptor =
                    ArgumentCaptor.forClass(StockTransactionEntity.class);
            verify(stockTransactionRepository).save(txCaptor.capture());
            assertThat(txCaptor.getValue().getType()).isEqualTo(StockTransactionType.RELEASE);

            assertThat(mockOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should cancel PAID order without releasing stock again (prevent double release)")
        void cancel_paid_shouldNotReleaseStock() {
            // Arrange — PAID order: stock already deducted, no reserved left
            mockOrder.setStatus(OrderStatus.PAID);
            mockInventory.setQuantity(8);
            mockInventory.setReservedQuantity(0); // already cleared when paid

            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            paymentService.cancel(USER_EMAIL, ORDER_ID);

            // Assert — stock NOT touched (no RELEASE transaction)
            verify(stockTransactionRepository, never()).save(any());
            verify(inventoryRepository, never()).save(any());
            assertThat(mockOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should throw 400 when trying to cancel COMPLETED order")
        void cancel_shouldThrow400_whenCompleted() {
            mockOrder.setStatus(OrderStatus.COMPLETED);
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.cancel(USER_EMAIL, ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(appEx.getMessage()).containsIgnoringCase("COMPLETED");
                    });

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when trying to cancel already CANCELLED order")
        void cancel_shouldThrow400_whenAlreadyCancelled() {
            mockOrder.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.cancel(USER_EMAIL, ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // COMPLETE
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        @DisplayName("Should complete PAID order successfully")
        void complete_success() {
            mockOrder.setStatus(OrderStatus.PAID);
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = paymentService.complete(USER_EMAIL, ORDER_ID);

            assertThat(mockOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should throw 400 when completing non-PAID order")
        void complete_shouldThrow400_whenNotPaid() {
            // RESERVED order cannot be completed directly
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.complete(USER_EMAIL, ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(appEx.getMessage()).containsIgnoringCase("PAID");
                    });

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when completing COMPLETED order")
        void complete_shouldThrow400_whenAlreadyCompleted() {
            mockOrder.setStatus(OrderStatus.COMPLETED);
            when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(mockOrder));

            assertThatThrownBy(() -> paymentService.complete(USER_EMAIL, ORDER_ID))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}