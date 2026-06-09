package com.service;

import com.dto.request.ProductRequest;
import com.dto.response.PageResponse;
import com.dto.response.ProductResponse;
import com.entity.CategoriesEntity;
import com.entity.ProductEntity;
import com.exception.AppException;
import com.repository.CategoryRepository;
import com.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                .name(request.getName()).sku(request.getSku())
                .description(request.getDescription()).price(request.getPrice())
                .category(category)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
        ProductEntity saved = productRepository.save(entity);
        log.info("Product created: id={}, sku={}", saved.getId(), saved.getSku());
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findById(id));
    }


    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String search, Long categoryId,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                int page, int limit) {
        // Bước 1: lấy IDs theo filter + pagination
        PageRequest pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        Page<Long> idPage = productRepository.searchIds(search, categoryId, minPrice, maxPrice, pageable);

        List<Long> ids = idPage.getContent();

        if (ids.isEmpty()) {
            return PageResponse.<ProductResponse>builder()
                    .content(List.of()).page(page).limit(limit)
                    .totalElements(0).totalPages(0)
                    .hasNext(false).hasPrevious(false).build();
        }

        // Bước 2: batch load products với category (1 JOIN FETCH query)
        List<ProductEntity> products = productRepository.findAllByIdWithCategory(ids);

        // Giữ đúng thứ tự sort từ pagination
        Map<Long, ProductEntity> productMap = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, p -> p));
        List<ProductResponse> content = ids.stream()
                .map(id -> ProductResponse.from(productMap.get(id)))
                .toList();

        return PageResponse.<ProductResponse>builder()
                .content(content).page(page).limit(limit)
                .totalElements(idPage.getTotalElements())
                .totalPages(idPage.getTotalPages())
                .hasNext(idPage.hasNext())
                .hasPrevious(idPage.hasPrevious())
                .build();
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        ProductEntity entity = findById(id);
        if (productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
            throw AppException.conflict("SKU already exists: " + request.getSku());
        }
        CategoriesEntity category = findCategory(request.getCategoryId());
        entity.setName(request.getName()); entity.setSku(request.getSku());
        entity.setDescription(request.getDescription()); entity.setPrice(request.getPrice());
        entity.setCategory(category);
        if (request.getActive() != null) entity.setActive(request.getActive());
        ProductEntity saved = productRepository.save(entity);
        log.info("Product updated: id={}", saved.getId());
        return ProductResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        findById(id);
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