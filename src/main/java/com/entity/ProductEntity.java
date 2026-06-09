package com.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

import java.math.BigDecimal;
import java.sql.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoriesEntity category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(name = "is_active", nullable = false)
    private Boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    @ToString.Exclude
    private InventoryEntity inventory;

    @OneToMany(mappedBy = "product")
    @Builder.Default
    @ToString.Exclude
    private List<CartItemEntity> cartItems = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    @Builder.Default
    @ToString.Exclude
    private List<OrderItemEntity> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    @Builder.Default
    @ToString.Exclude
    private List<StockTransactionEntity> stockTransactions = new ArrayList<>();
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (active == null) {
            active = true;
        }
    }
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
