package com.stocksight.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.stocksight.dto.MovementDtos.MovementRequest;
import com.stocksight.dto.ProductDtos.ProductRequest;
import com.stocksight.dto.ProductDtos.ProductResponse;
import com.stocksight.error.NotFoundException;
import com.stocksight.model.MovementType;
import com.stocksight.model.Product;
import com.stocksight.model.StockMovement;
import com.stocksight.repository.ProductRepository;
import com.stocksight.repository.StockMovementRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock private ProductRepository products;
    @Mock private StockMovementRepository movements;
    @InjectMocks private InventoryService service;

    @Test
    void findsProductByIdAndProjectsLowStockFromCurrentStock() {
        Product product = product(17L, "Tea", "TEA-1", 10, 3, 5);
        when(products.findById(17L)).thenReturn(Optional.of(product));

        ProductResponse response = service.getProductById(17L);

        assertEquals(3, response.currentStock());
        assertEquals(5, response.safetyThreshold());
        assertTrue(response.lowStock());
    }

    @Test
    void listsProductsThroughThePaginatedRepositoryQuery() {
        Product product = product(17L, "Tea", "TEA-1", 10, 8, 5);
        var pageable = PageRequest.of(0, 10, Sort.by("name"));
        when(products.findAll(pageable)).thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        var response = service.listProducts(null, pageable);

        assertEquals(1, response.getTotalElements());
        assertEquals("TEA-1", response.getContent().getFirst().sku());
        assertEquals(8, response.getContent().getFirst().currentStock());
    }

    @Test
    void updateChangesProductFieldsAndRecalculatesStockFromMovementHistory() {
        Product product = product(17L, "Tea", "TEA-1", 10, 14, 5);
        when(products.findById(17L)).thenReturn(Optional.of(product));
        when(movements.calculateNetMovement(17L)).thenReturn(4L);
        when(products.save(product)).thenReturn(product);

        ProductResponse response = service.updateProduct(17L,
            new ProductRequest("Updated Tea", "TEA-2", 10, 15));

        assertEquals("Updated Tea", response.name());
        assertEquals("TEA-2", response.sku());
        assertEquals(15, response.safetyThreshold());
        assertEquals(14, response.currentStock());
        assertTrue(response.lowStock());
    }

    @Test
    void findsProductByTrimmedSkuAndUsesThresholdBoundary() {
        Product product = product(17L, "Tea", "TEA-1", 10, 5, 5);
        when(products.findBySku("TEA-1")).thenReturn(Optional.of(product));

        ProductResponse response = service.getProductBySku(" TEA-1 ");

        assertEquals("TEA-1", response.sku());
        assertTrue(response.lowStock());
    }

    @Test
    void missingSkuReturnsNotFound() {
        when(products.findBySku("MISSING")).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> service.getProductBySku("MISSING"));
        assertEquals("Product with SKU MISSING not found.", exception.getMessage());
    }

    @Test
    void inwardMovementRecalculatesCurrentStockFromOpeningBalanceAndLedger() {
        Product product = product(21L, "Rice", "RICE-1", 10, 10, 2);
        when(products.findById(21L)).thenReturn(Optional.of(product));
        when(movements.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movements.calculateNetMovement(21L)).thenReturn(4L);
        when(products.save(product)).thenReturn(product);

        var response = service.createMovement(new MovementRequest(21L, MovementType.INWARD, 4,
            LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10)));

        assertEquals(14, response.currentStock());
        assertEquals(14, product.getCurrentStock());
    }

    @Test
    void outwardMovementCannotExceedAvailableStock() {
        Product product = product(21L, "Rice", "RICE-1", 10, 5, 2);
        when(products.findById(21L)).thenReturn(Optional.of(product));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.createMovement(new MovementRequest(21L,
            MovementType.OUTWARD, 6, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10))));
        assertEquals("Insufficient stock for this outward movement.", exception.getMessage());
        verify(movements, never()).save(any(StockMovement.class));
    }

    @Test
    void analyticsUsesInclusiveDateRangeAndActualOutwardHistoryForReorderMath() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 10);
        Product product = product(31L, "Oil", "OIL-1", 100, 55, 10);
        when(products.findById(31L)).thenReturn(Optional.of(product));
        when(movements.totalOutwardBetween(31L, from, to)).thenReturn(50L);
        when(movements.countOutwardBetween(31L, from, to)).thenReturn(3L);

        var result = service.analytics(31L, from, to);

        assertEquals(10L, result.get("periodDays"));
        assertEquals(50L, result.get("totalOutwardQuantity"));
        assertEquals(3L, result.get("outwardMovementCount"));
        assertEquals(5.0, result.get("periodAverageDailyOutward"));
        assertEquals(true, result.get("sufficientHistory"));
        assertEquals(9.0, result.get("estimatedDaysUntilSafetyThreshold"));
        assertEquals(true, result.get("reorderRecommended"));
        assertEquals(5, result.get("suggestedReorderQuantity"));
        assertEquals(false, result.get("leadTimeAvailable"));
    }

    @Test
    void analyticsMarksSparseHistoryAndOnlyUsesTheSafetyThreshold() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 10);
        Product product = product(32L, "Beans", "BEANS-1", 100, 60, 10);
        when(products.findById(32L)).thenReturn(Optional.of(product));
        when(movements.totalOutwardBetween(32L, from, to)).thenReturn(40L);
        when(movements.countOutwardBetween(32L, from, to)).thenReturn(2L);
        when(movements.totalOutwardBetween(32L, to.minusDays(29), to)).thenReturn(40L);
        when(movements.countOutwardBetween(32L, to.minusDays(29), to)).thenReturn(2L);

        var result = service.analytics(32L, from, to);

        assertEquals(false, result.get("sufficientHistory"));
        assertNull(result.get("estimatedDaysUntilSafetyThreshold"));
        assertNull(result.get("estimatedDaysUntilStockout"));
        assertEquals(false, result.get("reorderRecommended"));
        assertEquals(0, result.get("suggestedReorderQuantity"));
    }

    @Test
    void rejectsAnalyticsDateRangeWithStartAfterEnd() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.analytics(31L,
            LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 10)));
        assertEquals("Analysis start date must be on or before end date.", exception.getMessage());
    }

    @Test
    void reorderSuggestionsIncludeOnlyProductsMeetingTheirCalculatedReorderPoint() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 10);
        Product reorder = product(41L, "Oil", "OIL-2", 100, 55, 10);
        Product healthy = product(42L, "Flour", "FLOUR-1", 100, 100, 10);
        when(products.findAll()).thenReturn(List.of(reorder, healthy));
        when(movements.totalOutwardBetween(41L, from, to)).thenReturn(50L);
        when(movements.countOutwardBetween(41L, from, to)).thenReturn(3L);
        when(movements.totalOutwardBetween(42L, from, to)).thenReturn(0L);
        when(movements.countOutwardBetween(42L, from, to)).thenReturn(0L);
        when(movements.totalOutwardBetween(41L, to.minusDays(29), to)).thenReturn(50L);
        when(movements.totalOutwardBetween(42L, to.minusDays(29), to)).thenReturn(0L);
        when(movements.countOutwardBetween(41L, to.minusDays(29), to)).thenReturn(3L);
        when(movements.countOutwardBetween(42L, to.minusDays(29), to)).thenReturn(0L);

        var suggestions = service.reorderSuggestions(from, to);

        assertEquals(1, suggestions.size());
        assertEquals(41L, suggestions.getFirst().get("productId"));
    }

    @Test
    void anomalyFeedExplainsConsumptionSpikeAndThresholdBreach() {
        Product product = product(51L, "Sugar", "SUGAR-1", 100, 5, 10);
        LocalDate first = LocalDate.of(2026, 9, 1);
        List<StockMovement> ledger = List.of(
            movement(1L, product, MovementType.OUTWARD, 5, first),
            movement(2L, product, MovementType.OUTWARD, 5, first.plusDays(1)),
            movement(3L, product, MovementType.OUTWARD, 5, first.plusDays(2)),
            movement(4L, product, MovementType.OUTWARD, 30, first.plusDays(3)),
            movement(5L, product, MovementType.OUTWARD, 50, first.plusDays(4)));
        when(movements.findAll()).thenReturn(ledger);

        var anomalies = service.listAnomalies(51L, first.plusDays(3), first.plusDays(4));

        assertEquals(3, anomalies.size());
        assertTrue(anomalies.stream().anyMatch(a -> a.transactionId().equals(4L)
            && a.type().equals("CONSUMPTION_SPIKE") && a.severity().equals("HIGH")));
        assertTrue(anomalies.stream().anyMatch(a -> a.transactionId().equals(5L)
            && a.type().equals("LOW_STOCK_MOVEMENT") && a.stockAfter() == 5));
        assertFalse(anomalies.stream().anyMatch(a -> a.transactionId().equals(1L)));
    }

    @Test
    void anomalyFeedSeparatesUnusualInwardChangesFromConsumptionSpikes() {
        Product product = product(52L, "Sugar", "SUGAR-2", 0, 45, 10);
        LocalDate first = LocalDate.of(2026, 9, 1);
        when(movements.findAll()).thenReturn(List.of(
            movement(11L, product, MovementType.INWARD, 5, first),
            movement(12L, product, MovementType.INWARD, 5, first.plusDays(1)),
            movement(13L, product, MovementType.INWARD, 5, first.plusDays(2)),
            movement(14L, product, MovementType.INWARD, 30, first.plusDays(3))));

        var anomalies = service.listAnomalies(52L, first.plusDays(3), first.plusDays(3));

        assertEquals(1, anomalies.size());
        assertEquals("UNUSUAL_INWARD", anomalies.getFirst().type());
        assertEquals("HIGH", anomalies.getFirst().severity());
    }

    private Product product(long id, String name, String sku, int openingBalance, int currentStock, int safetyThreshold) {
        Product product = new Product(name, sku, openingBalance, safetyThreshold);
        ReflectionTestUtils.setField(product, "id", id);
        product.setCurrentStock(currentStock);
        return product;
    }

    private StockMovement movement(long id, Product product, MovementType type, int quantity, LocalDate date) {
        StockMovement movement = new StockMovement(product, type, quantity, date, date);
        ReflectionTestUtils.setField(movement, "id", id);
        return movement;
    }
}