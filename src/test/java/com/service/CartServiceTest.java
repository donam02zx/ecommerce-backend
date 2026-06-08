package com.service;

import com.dto.request.AddToCartRequest;
import com.dto.request.UpdateCartItemRequest;
import com.dto.response.CartResponse;
import com.entity.*;
import com.entity.enums.CartStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService Tests")
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    private static final String USER_EMAIL = "customer@test.com";

    private UserEntity mockUser;
    private ProductEntity mockProduct;
    private CartEntity mockCart;
    private CartItemEntity mockCartItem;

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(1L).email(USER_EMAIL).fullName("Test User").build();

        mockProduct = ProductEntity.builder()
                .id(10L).name("Wireless Mouse").sku("ELEC-001")
                .price(new BigDecimal("250000")).active(true).build();

        mockCart = CartEntity.builder()
                .id(1L).user(mockUser).status(CartStatus.ACTIVE)
                .items(new ArrayList<>()).build();

        mockCartItem = CartItemEntity.builder()
                .id(1L).cart(mockCart).product(mockProduct)
                .quantity(2).unitPrice(new BigDecimal("250000")).build();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ADD ITEM
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("Should add new item to cart when product not already in cart")
        void addItem_success_newProduct() {
            // Arrange
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(10L);
            request.setQuantity(3);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(10L)).thenReturn(Optional.of(mockProduct));
            when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(mockCart));
            when(cartItemRepository.findByCartIdAndProductId(1L, 10L))
                    .thenReturn(Optional.empty()); // product not in cart yet
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            cartService.addItem(USER_EMAIL, request);

            // Assert — new cart item created with correct quantity and price
            ArgumentCaptor<CartItemEntity> itemCaptor =
                    ArgumentCaptor.forClass(CartItemEntity.class);
            verify(cartItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(3);
            assertThat(itemCaptor.getValue().getUnitPrice())
                    .isEqualByComparingTo(mockProduct.getPrice());
        }

        @Test
        @DisplayName("Should merge quantity when product already exists in cart")
        void addItem_shouldMergeQuantity_whenProductAlreadyInCart() {
            // Arrange: product already in cart with qty=2, adding qty=3 → should become 5
            mockCart.getItems().add(mockCartItem);

            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(10L);
            request.setQuantity(3);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(10L)).thenReturn(Optional.of(mockProduct));
            when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(mockCart));
            when(cartItemRepository.findByCartIdAndProductId(1L, 10L))
                    .thenReturn(Optional.of(mockCartItem));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            cartService.addItem(USER_EMAIL, request);

            // Assert — quantity merged: 2 + 3 = 5
            assertThat(mockCartItem.getQuantity()).isEqualTo(5);
            verify(cartItemRepository).save(mockCartItem);
        }

        @Test
        @DisplayName("Should create new cart if user has no active cart")
        void addItem_shouldCreateCart_whenNoActiveCart() {
            // Arrange
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(10L);
            request.setQuantity(1);

            CartEntity newCart = CartEntity.builder()
                    .id(2L).user(mockUser).status(CartStatus.ACTIVE)
                    .items(new ArrayList<>()).build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(10L)).thenReturn(Optional.of(mockProduct));
            when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty()) // no active cart
                    .thenReturn(Optional.of(newCart)); // after creation
            when(cartRepository.save(any())).thenReturn(newCart);
            when(cartItemRepository.findByCartIdAndProductId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act — should not throw
            assertThatNoException().isThrownBy(
                    () -> cartService.addItem(USER_EMAIL, request));

            // Assert — new cart saved
            verify(cartRepository, atLeastOnce()).save(any(CartEntity.class));
        }

        @Test
        @DisplayName("Should throw 404 when product not found")
        void addItem_shouldThrow404_whenProductNotFound() {
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(99L);
            request.setQuantity(1);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(USER_EMAIL, request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when product is not active")
        void addItem_shouldThrow400_whenProductInactive() {
            mockProduct.setActive(false);

            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(10L);
            request.setQuantity(1);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(productRepository.findById(10L)).thenReturn(Optional.of(mockProduct));

            assertThatThrownBy(() -> cartService.addItem(USER_EMAIL, request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // UPDATE ITEM
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("updateItem()")
    class UpdateItem {

        @Test
        @DisplayName("Should update cart item quantity successfully")
        void updateItem_success() {
            // Arrange
            mockCart.getItems().add(mockCartItem);
            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(5);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(mockCartItem));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(mockCart));

            // Act
            cartService.updateItem(USER_EMAIL, 1L, request);

            // Assert
            assertThat(mockCartItem.getQuantity()).isEqualTo(5);
            verify(cartItemRepository).save(mockCartItem);
        }

        @Test
        @DisplayName("Should throw 400 when item does not belong to current user")
        void updateItem_shouldThrow400_whenItemNotBelongToUser() {
            // Arrange: item belongs to another user
            UserEntity anotherUser = UserEntity.builder()
                    .id(99L).email("other@test.com").build();
            CartEntity anotherCart = CartEntity.builder()
                    .id(99L).user(anotherUser).status(CartStatus.ACTIVE)
                    .items(new ArrayList<>()).build();
            CartItemEntity anotherItem = CartItemEntity.builder()
                    .id(1L).cart(anotherCart).product(mockProduct)
                    .quantity(1).unitPrice(mockProduct.getPrice()).build();

            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(5);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(anotherItem));

            assertThatThrownBy(() -> cartService.updateItem(USER_EMAIL, 1L, request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));

            verify(cartItemRepository, never()).save(any());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // CLEAR CART
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("clearCart()")
    class ClearCart {

        @Test
        @DisplayName("Should clear all items from active cart")
        void clearCart_success() {
            mockCart.getItems().add(mockCartItem);
            assertThat(mockCart.getItems()).hasSize(1);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(mockCart));
            when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            cartService.clearCart(USER_EMAIL);

            // Assert
            assertThat(mockCart.getItems()).isEmpty();
            verify(cartRepository).save(mockCart);
        }

        @Test
        @DisplayName("Should throw 404 when no active cart found")
        void clearCart_shouldThrow404_whenNoActiveCart() {
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.clearCart(USER_EMAIL))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));

            verify(cartRepository, never()).save(any());
        }
    }
}