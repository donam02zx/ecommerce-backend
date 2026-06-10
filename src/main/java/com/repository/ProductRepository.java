package com.repository;

import com.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    boolean existsByCategoryId(Long categoryId);


    @Query(value = """
        SELECT p FROM ProductEntity p
        JOIN FETCH p.category
        WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                               OR LOWER(p.sku)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND   (:categoryId IS NULL OR p.category.id = :categoryId)
        AND   (:minPrice IS NULL OR p.price >= :minPrice)
        AND   (:maxPrice IS NULL OR p.price <= :maxPrice)
        AND   p.active = true
    """,
            countQuery = """
        SELECT COUNT(p) FROM ProductEntity p
        WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                               OR LOWER(p.sku)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND   (:categoryId IS NULL OR p.category.id = :categoryId)
        AND   (:minPrice IS NULL OR p.price >= :minPrice)
        AND   (:maxPrice IS NULL OR p.price <= :maxPrice)
        AND   p.active = true
    """)
    Page<ProductEntity> search(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            Pageable pageable
    );
}