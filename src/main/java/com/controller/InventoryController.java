package com.controller;

import com.dto.request.InventoryExportRequest;
import com.dto.request.InventoryImportRequest;
import com.dto.response.ApiResponse;
import com.dto.response.InventoryResponse;
import com.dto.response.StockTransactionResponse;
import com.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management APIs")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Import stock [ADMIN, STAFF]")
    public ApiResponse<InventoryResponse> importStock(@Valid @RequestBody InventoryImportRequest request) {
        return ApiResponse.success(inventoryService.importStock(request));
    }

    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Export stock [ADMIN, STAFF]")
    public ApiResponse<InventoryResponse> exportStock(@Valid @RequestBody InventoryExportRequest request) {
        return ApiResponse.success(inventoryService.exportStock(request));
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Get inventory by product [ADMIN, STAFF]")
    public ApiResponse<InventoryResponse> getByProduct(@PathVariable Long productId) {
        return ApiResponse.success(inventoryService.getByProductId(productId));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Get stock transactions [ADMIN, STAFF]")
    public ApiResponse<List<StockTransactionResponse>> getTransactions(
            @RequestParam(required = false) Long productId) {
        if (productId != null) {
            return ApiResponse.success(inventoryService.getTransactionsByProduct(productId));
        }
        return ApiResponse.success(inventoryService.getAllTransactions());
    }
}