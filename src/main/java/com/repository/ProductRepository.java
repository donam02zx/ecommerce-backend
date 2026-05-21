package com.repository;

import com.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    boolean existsByCategoryId(Long categoryId);
}