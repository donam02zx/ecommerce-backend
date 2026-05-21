package com.controller;

import com.dto.request.InventoryExportRequest;
import com.dto.request.InventoryImportRequest;
import com.dto.response.ApiResponse;
import com.dto.response.InventoryResponse;
import com.dto.response.StockTransactionResponse;
import com.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management APIs")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/import")
    @Operation(summary = "Import stock for a product")
    public ApiResponse<InventoryResponse> importStock(@Valid @RequestBody InventoryImportRequest request) {
        return ApiResponse.success(inventoryService.importStock(request));
    }

    @PostMapping("/export")
    @Operation(summary = "Export stock for a product")
    public ApiResponse<InventoryResponse> exportStock(@Valid @RequestBody InventoryExportRequest request) {
        return ApiResponse.success(inventoryService.exportStock(request));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get inventory by product ID")
    public ApiResponse<InventoryResponse> getByProduct(@PathVariable Long productId) {
        return ApiResponse.success(inventoryService.getByProductId(productId));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get all stock transactions")
    public ApiResponse<List<StockTransactionResponse>> getTransactions(
            @RequestParam(required = false) Long productId) {
        if (productId != null) {
            return ApiResponse.success(inventoryService.getTransactionsByProduct(productId));
        }
        return ApiResponse.success(inventoryService.getAllTransactions());
    }
}