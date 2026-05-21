package com.repository;

import com.entity.StockTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransactionEntity, Long> {
    List<StockTransactionEntity> findByProductIdOrderByCreatedAtDesc(Long productId);
}