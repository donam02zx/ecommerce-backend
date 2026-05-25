package com.service;

import com.dto.request.AddToCartRequest;
import com.dto.request.UpdateCartItemRequest;
import com.dto.response.CartResponse;
import com.entity.*;
import com.entity.enums.CartStatus;
import com.exception.AppException;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public CartResponse addItem(String email, AddToCartRequest request) {
        UserEntity user = findUser(email);
        ProductEntity product = findProduct(request.getProductId());

        CartEntity cart = getOrCreateActiveCart(user);

        // Nếu product đã có trong cart thì cộng quantity
        cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .ifPresentOrElse(
                        item -> {
                            item.setQuantity(item.getQuantity() + request.getQuantity());
                            cartItemRepository.save(item);
                            log.info("Cart item updated: cartId={}, productId={}, newQty={}",
                                    cart.getId(), product.getId(), item.getQuantity());
                        },
                        () -> {
                            CartItemEntity newItem = CartItemEntity.builder()
                                    .cart(cart)
                                    .product(product)
                                    .quantity(request.getQuantity())
                                    .unitPrice(product.getPrice())
                                    .build();
                            cartItemRepository.save(newItem);
                            log.info("Cart item added: cartId={}, productId={}", cart.getId(), product.getId());
                        }
                );

        return CartResponse.from(getCartWithItems(user.getId()));
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(String email) {
        UserEntity user = findUser(email);
        CartEntity cart = getCartWithItems(user.getId());
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse updateItem(String email, Long itemId, UpdateCartItemRequest request) {
        UserEntity user = findUser(email);
        CartItemEntity item = findCartItem(itemId, user.getId());
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        log.info("Cart item updated: itemId={}, qty={}", itemId, request.getQuantity());
        return CartResponse.from(getCartWithItems(user.getId()));
    }

    @Transactional
    public CartResponse removeItem(String email, Long itemId) {
        UserEntity user = findUser(email);
        CartItemEntity item = findCartItem(itemId, user.getId());
        cartItemRepository.delete(item);
        log.info("Cart item removed: itemId={}", itemId);
        return CartResponse.from(getCartWithItems(user.getId()));
    }

    @Transactional
    public void clearCart(String email) {
        UserEntity user = findUser(email);
        CartEntity cart = cartRepository.findByUserIdAndStatus(user.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> AppException.notFound("No active cart found"));
        cart.getItems().clear();
        cartRepository.save(cart);
        log.info("Cart cleared: cartId={}", cart.getId());
    }

    // --- helpers ---

    private UserEntity findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found: " + email));
    }

    private ProductEntity findProduct(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> AppException.notFound("Product not found: " + productId));
        if (!product.getActive()) {
            throw AppException.badRequest("Product is not active: " + productId);
        }
        return product;
    }

    private CartEntity getOrCreateActiveCart(UserEntity user) {
        return cartRepository.findByUserIdAndStatus(user.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> {
                    CartEntity cart = CartEntity.builder()
                            .user(user)
                            .status(CartStatus.ACTIVE)
                            .build();
                    CartEntity saved = cartRepository.save(cart);
                    log.info("New cart created: userId={}, cartId={}", user.getId(), saved.getId());
                    return saved;
                });
    }

    private CartEntity getCartWithItems(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> AppException.notFound("No active cart found"));
    }

    private CartItemEntity findCartItem(Long itemId, Long userId) {
        CartItemEntity item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> AppException.notFound("Cart item not found: " + itemId));
        // Đảm bảo item thuộc cart của user đang login
        if (!item.getCart().getUser().getId().equals(userId)) {
            throw AppException.badRequest("Cart item does not belong to current user");
        }
        return item;
    }
}