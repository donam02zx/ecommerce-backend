package com.repository;

import com.entity.OrderEntity;
import com.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT o FROM OrderEntity o WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<OrderEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.user.id = :userId
        AND (CAST(:status as string) IS NULL OR o.status = :status)
        AND (CAST(:fromDate as TIMESTAMP) IS NULL OR o.createdAt >= :fromDate)
        AND (CAST(:toDate as TIMESTAMP) IS NULL OR o.createdAt <= :toDate)
        ORDER BY o.createdAt DESC
    """)
    Page<OrderEntity> searchByUser(
            @Param("userId") Long userId,
            @Param("status") OrderStatus status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );
}