package com.service;

import com.dto.response.TopProductResponse;
import com.entity.ProductSalesSummaryEntity;
import com.repository.ProductSalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSalesSummaryService {

    private final ProductSalesSummaryRepository productSalesSummaryRepository;

    @Transactional(readOnly = true)
    public List<TopProductResponse> getTop10BestSellingProducts() {
        List<ProductSalesSummaryEntity> summaries =
                productSalesSummaryRepository.findTop10ByOrderByTotalQuantitySoldDesc();

        return summaries.stream()
                .map(this::toResponse)
                .toList();
    }

    private TopProductResponse toResponse(ProductSalesSummaryEntity s) {
        return TopProductResponse.builder()
                .productId(s.getProduct().getId())
                .name(s.getProduct().getName())
                .sku(s.getProduct().getSku())
                .price(s.getProduct().getPrice())
                .active(s.getProduct().getActive())
                .categoryId(s.getProduct().getCategory().getId())
                .categoryName(s.getProduct().getCategory().getName())
                .totalQuantitySold(s.getTotalQuantitySold())
                .build();
    }
}