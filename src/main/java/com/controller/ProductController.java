package com.controller;

import com.dto.request.ProductRequest;
import com.dto.response.ApiResponse;
import com.dto.response.PageResponse;
import com.dto.response.ProductResponse;
import com.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Create product [ADMIN, STAFF]")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(productService.create(request));
    }


    @GetMapping
    @Operation(summary = "Search/filter products (legacy params)")
    public ApiResponse<PageResponse<ProductResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(productService.search(search, categoryId, minPrice, maxPrice, page, limit));
    }


    @GetMapping("/filter")
    @Operation(summary = "RSQL filter products",
            description = "Operators: ==, !=, =gt=, =lt=, =ge=, =le=, =in=, =out=, =like=. AND: ; OR: ,")
    public ApiResponse<PageResponse<ProductResponse>> rsqlFilter(
            @Parameter(description = "RSQL filter, e.g. price>=100000;category.id==1")
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Sort: field,asc|desc e.g. price,asc")
            @RequestParam(required = false) String sort) {
        return ApiResponse.success(productService.rsqlSearch(filter, page, limit, sort));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID [PUBLIC]")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(productService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Update product [ADMIN, STAFF]")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete product [ADMIN]")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.<Void>builder().success(true).message("Product deleted successfully").build();
    }
}