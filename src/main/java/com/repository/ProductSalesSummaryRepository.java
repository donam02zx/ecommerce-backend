package com.repository;

import com.entity.ProductSalesSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSalesSummaryRepository extends JpaRepository<ProductSalesSummaryEntity, Long> {

    @Query("""
        SELECT s FROM ProductSalesSummaryEntity s
        JOIN FETCH s.product p
        JOIN FETCH p.category c
        ORDER BY s.totalQuantitySold DESC
    """)
    List<ProductSalesSummaryEntity> findTop10ByOrderByTotalQuantitySoldDesc();
}