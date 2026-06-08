package com.service;

import com.dto.request.InventoryExportRequest;
import com.dto.request.InventoryImportRequest;
import com.dto.response.InventoryResponse;
import com.entity.*;
import com.entity.enums.StockTransactionType;
import com.exception.AppException;
import com.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Tests")
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private ProductEntity mockProduct;
    private InventoryEntity mockInventory;

    @BeforeEach
    void setUp() {
        mockProduct = ProductEntity.builder()
                .id(1L).name("Wireless Mouse").sku("ELEC-001")
                .price(new BigDecimal("250000")).active(true).build();

        mockInventory = InventoryEntity.builder()
                .id(1L).product(mockProduct)
                .quantity(10).reservedQuantity(2).version(0L).build();
    }

    // ────────────────────────────────────────────────────────────────────────
    // IMPORT
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("importStock()")
    class ImportStock {

        @Test
        @DisplayName("Should import stock and increase quantity correctly")
        void importStock_success() {
            // Arrange
            InventoryImportRequest request = new InventoryImportRequest();
            request.setProductId(1L);
            request.setQuantity(20);
            request.setNote("Monthly restock");

            when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(mockInventory));
            when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            InventoryResponse response = inventoryService.importStock(request);

            // Assert — quantity increased from 10 to 30
            assertThat(mockInventory.getQuantity()).isEqualTo(30);

            // Assert — stock transaction IMPORT recorded with correct before/after
            ArgumentCaptor<StockTransactionEntity> txCaptor =
                    ArgumentCaptor.forClass(StockTransactionEntity.class);
            verify(stockTransactionRepository).save(txCaptor.capture());
            StockTransactionEntity tx = txCaptor.getValue();
            assertThat(tx.getType()).isEqualTo(StockTransactionType.IMPORT);
            assertThat(tx.getQuantity()).isEqualTo(20);
            assertThat(tx.getQuantityBefore()).isEqualTo(10);
            assertThat(tx.getQuantityAfter()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should create new inventory if product has no inventory yet")
        void importStock_shouldCreateInventory_whenNotExists() {
            // Arrange
            InventoryImportRequest request = new InventoryImportRequest();
            request.setProductId(1L);
            request.setQuantity(50);

            when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.empty());
            when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            inventoryService.importStock(request);

            // Assert — new inventory saved
            ArgumentCaptor<InventoryEntity> invCaptor =
                    ArgumentCaptor.forClass(InventoryEntity.class);
            verify(inventoryRepository, atLeastOnce()).save(invCaptor.capture());
        }

        @Test
        @DisplayName("Should throw 404 when product not found")
        void importStock_shouldThrow404_whenProductNotFound() {
            InventoryImportRequest request = new InventoryImportRequest();
            request.setProductId(99L);
            request.setQuantity(10);

            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.importStock(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));

            verify(inventoryRepository, never()).save(any());
            verify(stockTransactionRepository, never()).save(any());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // EXPORT
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("exportStock()")
    class ExportStock {

        @Test
        @DisplayName("Should export stock and decrease available quantity correctly")
        void exportStock_success() {
            // Arrange: quantity=10, reserved=2 → available=8, export 3
            InventoryExportRequest request = new InventoryExportRequest();
            request.setProductId(1L);
            request.setQuantity(3);
            request.setNote("Manual export");

            when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(mockInventory));
            when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            InventoryResponse response = inventoryService.exportStock(request);

            // Assert — quantity decreased from 10 to 7
            assertThat(mockInventory.getQuantity()).isEqualTo(7);

            // Assert — EXPORT transaction recorded
            ArgumentCaptor<StockTransactionEntity> txCaptor =
                    ArgumentCaptor.forClass(StockTransactionEntity.class);
            verify(stockTransactionRepository).save(txCaptor.capture());
            StockTransactionEntity tx = txCaptor.getValue();
            assertThat(tx.getType()).isEqualTo(StockTransactionType.EXPORT);
            assertThat(tx.getQuantity()).isEqualTo(3);
            assertThat(tx.getQuantityBefore()).isEqualTo(10);
            assertThat(tx.getQuantityAfter()).isEqualTo(7);
        }

        @Test
        @DisplayName("Should throw 400 when export quantity exceeds available stock")
        void exportStock_shouldThrow400_whenInsufficientStock() {
            // quantity=10, reserved=2 → available=8, try to export 9
            InventoryExportRequest request = new InventoryExportRequest();
            request.setProductId(1L);
            request.setQuantity(9);

            when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(mockInventory));

            assertThatThrownBy(() -> inventoryService.exportStock(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(appEx.getMessage()).containsIgnoringCase("not enough stock");
                    });

            // Verify quantity NOT changed
            assertThat(mockInventory.getQuantity()).isEqualTo(10);
            verify(inventoryRepository, never()).save(any());
            verify(stockTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw 400 when available stock is exactly 0")
        void exportStock_shouldThrow400_whenAvailableIsZero() {
            // quantity=5, reserved=5 → available=0
            mockInventory.setQuantity(5);
            mockInventory.setReservedQuantity(5);

            InventoryExportRequest request = new InventoryExportRequest();
            request.setProductId(1L);
            request.setQuantity(1);

            when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(mockInventory));

            assertThatThrownBy(() -> inventoryService.exportStock(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));

            verify(inventoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow export exactly equal to available stock")
        void exportStock_success_whenQuantityEqualsAvailable() {
            // quantity=10, reserved=2 → available=8, export exactly 8
            InventoryExportRequest request = new InventoryExportRequest();
            request.setProductId(1L);
            request.setQuantity(8);

            when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(mockInventory));
            when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stockTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should NOT throw
            assertThatNoException().isThrownBy(() -> inventoryService.exportStock(request));
            assertThat(mockInventory.getQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should throw 404 when product not found")
        void exportStock_shouldThrow404_whenProductNotFound() {
            InventoryExportRequest request = new InventoryExportRequest();
            request.setProductId(99L);
            request.setQuantity(1);

            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.exportStock(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}