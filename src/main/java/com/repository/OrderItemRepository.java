package com.repository;

import com.entity.OrderItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {

    @Query("""
        SELECT oi.product.id as productId,
               oi.product.name as productName,
               oi.product.sku as productSku,
               oi.product.price as productPrice,
               oi.product.active as productActive,
               oi.product.category.id as categoryId,
               oi.product.category.name as categoryName,
               SUM(oi.quantity) as totalQuantity
        FROM OrderItemEntity oi
        JOIN oi.order o
        WHERE o.status IN ('PAID', 'COMPLETED')
        GROUP BY oi.product.id, oi.product.name, oi.product.sku, oi.product.price, 
                 oi.product.active, oi.product.category.id, oi.product.category.name
        ORDER BY totalQuantity DESC
    """)
    List<Object[]> findTop10BestSellingProducts();


    @Query("""
        SELECT oi.product.id as productId,
               oi.product.name as productName,
               oi.product.sku as productSku,
               oi.product.price as productPrice,
               oi.product.active as productActive,
               oi.product.category.id as categoryId,
               oi.product.category.name as categoryName,
               SUM(oi.quantity) as totalQuantity
        FROM OrderItemEntity oi
        JOIN oi.order o
        WHERE o.status IN ('PAID', 'COMPLETED')
        GROUP BY oi.product.id, oi.product.name, oi.product.sku, oi.product.price, 
                 oi.product.active, oi.product.category.id, oi.product.category.name
        ORDER BY totalQuantity DESC
    """)
    List<Object[]> findTopNBestSellingProducts(Pageable pageable);
}