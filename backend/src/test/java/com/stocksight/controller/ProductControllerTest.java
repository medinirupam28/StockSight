package com.stocksight.controller;

import com.stocksight.dto.MovementDtos.MovementRequest;
import com.stocksight.dto.ProductDtos.ProductRequest;
import com.stocksight.model.MovementType;
import com.stocksight.model.Product;
import com.stocksight.model.StockMovement;
import com.stocksight.repository.ProductRepository;
import com.stocksight.repository.StockMovementRepository;
import com.stocksight.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {
    @Mock private ProductRepository products;
    @Mock private StockMovementRepository movements;

    @Test
    void getsProductDetailsById() throws Exception {
        when(products.findById(17L)).thenReturn(Optional.of(product(17L, "Tea", "TEA-1", 10, 3, 5)));

        mockMvc().perform(get("/api/products/17"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(17))
            .andExpect(jsonPath("$.sku").value("TEA-1"))
            .andExpect(jsonPath("$.currentStock").value(3))
            .andExpect(jsonPath("$.lowStock").value(true));
    }

    @Test
    void getsProductDetailsBySku() throws Exception {
        when(products.findBySku("TEA-1")).thenReturn(Optional.of(product(17L, "Tea", "TEA-1", 10, 8, 5)));

        mockMvc().perform(get("/api/products/sku/TEA-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(17))
            .andExpect(jsonPath("$.name").value("Tea"))
            .andExpect(jsonPath("$.lowStock").value(false));
    }

            @Test
            void createsProductFromQuantityAndMinimumStockAliases() throws Exception {
            when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            mockMvc().perform(post("/api/products").contentType(APPLICATION_JSON)
                .content("""
                    {"name":"HP Laptop","sku":"HP-LAP-TEST","quantity":50,"minimumStock":10}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(50))
                .andExpect(jsonPath("$.safetyThreshold").value(10))
                .andExpect(jsonPath("$.currentStock").value(50))
                .andExpect(jsonPath("$.lowStock").value(false));

            verify(products).save(org.mockito.ArgumentMatchers.argThat(product ->
                product.getOpeningBalance() == 50 && product.getSafetyThreshold() == 10));
            }

            @Test
            void createsProductWithExistingCanonicalRequestFields() throws Exception {
            when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            mockMvc().perform(post("/api/products").contentType(APPLICATION_JSON)
                .content("""
                    {"name":"Canonical Product","sku":"CANONICAL-TEST","openingBalance":12,"safetyThreshold":3}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(12))
                .andExpect(jsonPath("$.safetyThreshold").value(3))
                .andExpect(jsonPath("$.currentStock").value(12));
            }

        @Test
        void analyticsEndpointBindsSelectedDatesAndReturnsConsumptionEvidence() throws Exception {
            long productId = 31L;
            LocalDate from = LocalDate.of(2026, 9, 1);
            LocalDate to = LocalDate.of(2026, 9, 10);
            when(products.findById(productId)).thenReturn(Optional.of(product(productId, "Oil", "OIL-1", 100, 55, 10)));
            when(movements.totalOutwardBetween(productId, from, to)).thenReturn(50L);
            when(movements.countOutwardBetween(productId, from, to)).thenReturn(3L);
            when(movements.totalOutwardBetween(productId, to.minusDays(29), to)).thenReturn(50L);
            when(movements.countOutwardBetween(productId, to.minusDays(29), to)).thenReturn(3L);

            mockMvc().perform(get("/api/products/31/analytics")
                    .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisFrom[0]").value(2026))
                .andExpect(jsonPath("$.analysisFrom[1]").value(9))
                .andExpect(jsonPath("$.analysisFrom[2]").value(1))
                .andExpect(jsonPath("$.analysisTo[2]").value(10))
                .andExpect(jsonPath("$.totalOutwardQuantity").value(50))
                .andExpect(jsonPath("$.suggestedReorderQuantity").value(5));
        }

        @Test
        void reorderSuggestionsEndpointReturnsOnlyProductsNeedingReorder() throws Exception {
            LocalDate from = LocalDate.of(2026, 9, 1);
            LocalDate to = LocalDate.of(2026, 9, 10);
            Product product = product(41L, "Oil", "OIL-2", 100, 55, 10);
            when(products.findAll()).thenReturn(java.util.List.of(product));
            when(movements.totalOutwardBetween(41L, from, to)).thenReturn(50L);
            when(movements.countOutwardBetween(41L, from, to)).thenReturn(3L);
            when(movements.totalOutwardBetween(41L, to.minusDays(29), to)).thenReturn(50L);
            when(movements.countOutwardBetween(41L, to.minusDays(29), to)).thenReturn(3L);

            mockMvc().perform(get("/api/products/reorder-suggestions")
                    .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(41))
                .andExpect(jsonPath("$[0].suggestedReorderQuantity").value(5));
        }

        @Test
        void movementAnomalyEndpointReturnsTransactionAndReason() throws Exception {
            Product product = product(51L, "Sugar", "SUGAR-1", 100, 5, 10);
            LocalDate first = LocalDate.of(2026, 9, 1);
            when(movements.findAll()).thenReturn(java.util.List.of(
                movement(1L, product, MovementType.OUTWARD, 5, first),
                movement(2L, product, MovementType.OUTWARD, 5, first.plusDays(1)),
                movement(3L, product, MovementType.OUTWARD, 5, first.plusDays(2)),
                movement(4L, product, MovementType.OUTWARD, 30, first.plusDays(3))));
            MockMvc movementMvc = standaloneSetup(new StockMovementController(new InventoryService(products, movements))).build();

            movementMvc.perform(get("/api/movements/anomalies").param("productId", "51")
                    .param("from", first.plusDays(3).toString()).param("to", first.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(4))
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                .andExpect(jsonPath("$[0].stockAfter").value(55))
                .andExpect(jsonPath("$[0].reason").isNotEmpty());
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

    private MockMvc mockMvc() {
        return standaloneSetup(new ProductController(new InventoryService(products, movements))).build();
    }

    @Test
    void productWritesRemainAdminOnly() throws NoSuchMethodException {
        assertEquals("hasRole('ADMIN')", ProductController.class
            .getMethod("create", ProductRequest.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('ADMIN')", ProductController.class
            .getMethod("update", long.class, ProductRequest.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('ADMIN')", ProductController.class
            .getMethod("delete", long.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAnyRole('ADMIN','SUPPLIER')", StockMovementController.class
            .getMethod("create", MovementRequest.class).getAnnotation(PreAuthorize.class).value());
    }
}