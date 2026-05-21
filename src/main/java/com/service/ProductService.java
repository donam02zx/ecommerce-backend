package com.service;

import com.dto.request.ProductRequest;
import com.dto.response.ProductResponse;
import com.entity.CategoriesEntity;
import com.entity.ProductEntity;
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
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw AppException.conflict("SKU already exists: " + request.getSku());
        }
        CategoriesEntity category = findCategory(request.getCategoryId());
        ProductEntity entity = ProductEntity.builder()
                .name(request.getName())
                .sku(request.getSku())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(category)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
        ProductEntity saved = productRepository.save(entity);
        log.info("Product created: id={}, sku={}", saved.getId(), saved.getSku());
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return productRepository.findAll()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findById(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        ProductEntity entity = findById(id);
        if (productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
            throw AppException.conflict("SKU already exists: " + request.getSku());
        }
        CategoriesEntity category = findCategory(request.getCategoryId());
        entity.setName(request.getName());
        entity.setSku(request.getSku());
        entity.setDescription(request.getDescription());
        entity.setPrice(request.getPrice());
        entity.setCategory(category);
        if (request.getActive() != null) entity.setActive(request.getActive());
        ProductEntity saved = productRepository.save(entity);
        log.info("Product updated: id={}", saved.getId());
        return ProductResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        findById(id); // check exists
        productRepository.deleteById(id);
        log.info("Product deleted: id={}", id);
    }

    private ProductEntity findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Product not found: " + id));
    }

    private CategoriesEntity findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> AppException.notFound("Category not found: " + categoryId));
    }
}