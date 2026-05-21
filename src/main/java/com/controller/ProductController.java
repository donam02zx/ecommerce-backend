package com.controller;

import com.dto.request.ProductRequest;
import com.dto.response.ApiResponse;
import com.dto.response.ProductResponse;
import com.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new product")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(productService.create(request));
    }

    @GetMapping
    @Operation(summary = "Get all products")
    public ApiResponse<List<ProductResponse>> getAll() {
        return ApiResponse.success(productService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(productService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product by ID")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product by ID")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Product deleted successfully")
                .build();
    }
}