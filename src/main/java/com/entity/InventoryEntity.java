package com.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private ProductEntity product;

    @Column(nullable = false)
    private Integer quantity;
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @PrePersist
    void prePersist() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (quantity == null) {
            quantity = 0;
        }
        if (reservedQuantity == null) {
            reservedQuantity = 0;
        }
        if (version == null) {
            version = 0L;
        }
    }
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
