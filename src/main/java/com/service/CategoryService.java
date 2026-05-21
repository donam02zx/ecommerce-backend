package com.service;

import com.dto.request.CategoryRequest;
import com.dto.response.CategoryResponse;
import com.entity.CategoriesEntity;
import com.exception.AppException;
import com.repository.CategoryRepository;
import com.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw AppException.conflict("Category name already exists: " + request.getName());
        }
        CategoriesEntity entity = CategoriesEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
        CategoriesEntity saved = categoryRepository.save(entity);
        log.info("Category created: id={}, name={}", saved.getId(), saved.getName());
        return CategoryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return CategoryResponse.from(findById(id));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        CategoriesEntity entity = findById(id);
        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw AppException.conflict("Category name already exists: " + request.getName());
        }
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        if (request.getActive() != null) entity.setActive(request.getActive());
        CategoriesEntity saved = categoryRepository.save(entity);
        log.info("Category updated: id={}", saved.getId());
        return CategoryResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        findById(id); // check exists
        if (productRepository.existsByCategoryId(id)) {
            throw AppException.badRequest("Cannot delete category that still has products");
        }
        categoryRepository.deleteById(id);
        log.info("Category deleted: id={}", id);
    }

    private CategoriesEntity findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Category not found: " + id));
    }
}