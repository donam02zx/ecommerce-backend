package com.repository;

import com.entity.CategoriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoriesEntity, Long> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}