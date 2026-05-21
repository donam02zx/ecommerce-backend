package com.service;

import com.dto.request.InventoryExportRequest;
import com.dto.request.InventoryImportRequest;
import com.dto.response.InventoryResponse;
import com.dto.response.StockTransactionResponse;
import com.entity.InventoryEntity;
import com.entity.ProductEntity;
import com.entity.StockTransactionEntity;
import com.entity.enums.StockTransactionType;
import com.exception.AppException;
import com.repository.InventoryRepository;
import com.repository.ProductRepository;
import com.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;

    @Transactional
    public InventoryResponse importStock(InventoryImportRequest request) {
        ProductEntity product = findProduct(request.getProductId());
        InventoryEntity inventory = getOrCreateInventory(product);

        int before = inventory.getQuantity();
        int after = before + request.getQuantity();
        inventory.setQuantity(after);
        inventoryRepository.save(inventory);

        recordTransaction(product, StockTransactionType.IMPORT,
                request.getQuantity(), before, after, "MANUAL", null, request.getNote());

        log.info("Stock imported: productId={}, qty={}, totalNow={}", product.getId(), request.getQuantity(), after);
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse exportStock(InventoryExportRequest request) {
        ProductEntity product = findProduct(request.getProductId());
        InventoryEntity inventory = getOrCreateInventory(product);

        int available = inventory.getQuantity() - inventory.getReservedQuantity();
        if (request.getQuantity() > available) {
            throw AppException.badRequest(
                    "Not enough stock. Available: " + available + ", requested: " + request.getQuantity());
        }

        int before = inventory.getQuantity();
        int after = before - request.getQuantity();
        inventory.setQuantity(after);
        inventoryRepository.save(inventory);

        recordTransaction(product, StockTransactionType.EXPORT,
                request.getQuantity(), before, after, "MANUAL", null, request.getNote());

        log.info("Stock exported: productId={}, qty={}, totalNow={}", product.getId(), request.getQuantity(), after);
        return InventoryResponse.from(inventory);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getByProductId(Long productId) {
        findProduct(productId); // validate product exists
        InventoryEntity inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> AppException.notFound("Inventory not found for product: " + productId));
        return InventoryResponse.from(inventory);
    }

    @Transactional(readOnly = true)
    public List<StockTransactionResponse> getAllTransactions() {
        return stockTransactionRepository.findAll()
                .stream()
                .map(StockTransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockTransactionResponse> getTransactionsByProduct(Long productId) {
        findProduct(productId);
        return stockTransactionRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(StockTransactionResponse::from)
                .toList();
    }

    // --- helpers ---

    private ProductEntity findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> AppException.notFound("Product not found: " + productId));
    }

    private InventoryEntity getOrCreateInventory(ProductEntity product) {
        return inventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> {
                    InventoryEntity inv = InventoryEntity.builder()
                            .product(product)
                            .quantity(0)
                            .reservedQuantity(0)
                            .build();
                    return inventoryRepository.save(inv);
                });
    }

    private void recordTransaction(ProductEntity product, StockTransactionType type,
                                   int quantity, int before, int after,
                                   String refType, Long refId, String note) {
        StockTransactionEntity tx = StockTransactionEntity.builder()
                .product(product)
                .type(type)
                .quantity(quantity)
                .quantityBefore(before)
                .quantityAfter(after)
                .referenceType(refType)
                .referenceId(refId)
                .note(note)
                .build();
        stockTransactionRepository.save(tx);
    }
}