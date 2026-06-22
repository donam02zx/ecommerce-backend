package com.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "product_sales_summary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesSummaryEntity {

    @Id
    private Long productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    @Column(name = "total_quantity_sold", nullable = false)
    private Long totalQuantitySold;

    @Column(name = "updated_at")
    private Instant updatedAt;
}