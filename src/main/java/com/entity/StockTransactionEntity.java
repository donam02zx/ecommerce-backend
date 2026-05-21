package com.entity;

import com.entity.enums.StockTransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stock_transactions")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockTransactionType type;
    @Column(nullable = false)
    private Integer quantity;
    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;
    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;
    @Column(name = "reference_type", length = 50)
    private String referenceType;
    @Column(name = "reference_id")
    private Long referenceId;
    @Column(length = 255)
    private String note;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}