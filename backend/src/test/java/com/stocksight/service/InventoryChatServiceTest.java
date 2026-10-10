package com.stocksight.service;

import com.stocksight.model.Product;
import com.stocksight.repository.ProductRepository;
import com.stocksight.repository.StockMovementRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryChatServiceTest {
    @Test
    void retrievesOnlyQuestionRelevantLiveRecordsForTheConfiguredChatProvider() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try (var requestStream = exchange.getRequestBody()) {
                requestBody.set(new String(requestStream.readAllBytes(), StandardCharsets.UTF_8));
            }
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"Use the retrieved product record.\"}}]}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var responseStream = exchange.getResponseBody()) {
                responseStream.write(response);
            }
        });
        server.start();
        try {
            ProductRepository products = mock(ProductRepository.class);
            StockMovementRepository movements = mock(StockMovementRepository.class);
            InventoryService inventory = new InventoryService(products, movements);
            Product tea = product(1L, "Green Tea", "TEA-001", 20, 4, 8);
            Product wire = product(2L, "Copper Wire", "WIRE-002", 300, 250, 20);
            when(products.findAll()).thenReturn(List.of(tea, wire));
            when(products.findById(1L)).thenReturn(Optional.of(tea));
            when(products.findById(2L)).thenReturn(Optional.of(wire));
            when(movements.totalOutwardBetween(eq(1L), any(LocalDate.class), any(LocalDate.class))).thenReturn(8L);
            when(movements.totalOutwardBetween(eq(2L), any(LocalDate.class), any(LocalDate.class))).thenReturn(0L);
            when(movements.countOutwardBetween(eq(1L), any(LocalDate.class), any(LocalDate.class))).thenReturn(2L);
            when(movements.countOutwardBetween(eq(2L), any(LocalDate.class), any(LocalDate.class))).thenReturn(0L);
            when(movements.findAll()).thenReturn(List.of());
            when(movements.findByProductIdOrderByBillDateAscIdAsc(1L)).thenReturn(List.of());
            when(movements.findByProductIdOrderByBillDateAscIdAsc(2L)).thenReturn(List.of());
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions";

            Map<String,String> response = new InventoryChatService(products, movements, inventory,
                url, "local-test-key", "test-model").ask("Should I reorder TEA-001?");

            assertEquals("Use the retrieved product record.", response.get("answer"));
            assertEquals("lexical-rag", response.get("retrievalMethod"));
            assertTrue(response.get("contextSummary").contains("product TEA-001"));
            assertTrue(requestBody.get().contains("TEA-001"));
            assertFalse(requestBody.get().contains("Copper Wire"));
            assertEquals("Bearer local-test-key", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emptyInventoryReturnsWithoutCallingAProvider() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.findAll()).thenReturn(List.of());
        InventoryChatService service = new InventoryChatService(products, mock(StockMovementRepository.class),
            new InventoryService(products, mock(StockMovementRepository.class)), "http://127.0.0.1:1/chat", "unused", "unused");

        Map<String,String> response = service.ask("What should I reorder?");

        assertTrue(response.get("answer").contains("No inventory records"));
        assertEquals("No relevant inventory context was retrieved.", response.get("contextSummary"));
    }

    @Test
    void providerConnectionFailureReturnsContextAndFriendlyStatus() {
        ProductRepository products = mock(ProductRepository.class);
        StockMovementRepository movements = mock(StockMovementRepository.class);
        InventoryService inventory = new InventoryService(products, movements);
        Product tea = product(1L, "Green Tea", "TEA-001", 20, 4, 8);
        when(products.findAll()).thenReturn(List.of(tea));
        when(products.findById(1L)).thenReturn(Optional.of(tea));
        when(movements.totalOutwardBetween(eq(1L), any(LocalDate.class), any(LocalDate.class))).thenReturn(0L);
        when(movements.countOutwardBetween(eq(1L), any(LocalDate.class), any(LocalDate.class))).thenReturn(0L);
        when(movements.findAll()).thenReturn(List.of());
        when(movements.findByProductIdOrderByBillDateAscIdAsc(1L)).thenReturn(List.of());
        InventoryChatService service = new InventoryChatService(products, movements, inventory,
            "http://127.0.0.1:1/chat", "local-test-key", "test-model");

        Map<String,String> response = service.ask("Should I reorder TEA-001?");

        assertTrue(response.get("answer").contains("temporarily unavailable"));
        assertTrue(response.get("contextSummary").contains("TEA-001"));
    }

    @Test
    void rejectsBlankAndOversizedQuestions() {
        InventoryChatService service = new InventoryChatService(mock(ProductRepository.class),
            mock(StockMovementRepository.class), new InventoryService(mock(ProductRepository.class),
                mock(StockMovementRepository.class)), "", "", "");

        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> service.ask("  "));
        IllegalArgumentException oversized = assertThrows(IllegalArgumentException.class, () -> service.ask("q".repeat(1001)));
        assertEquals("Question must be between 1 and 1000 characters.", blank.getMessage());
        assertEquals("Question must be between 1 and 1000 characters.", oversized.getMessage());
    }

    private Product product(long id, String name, String sku, int openingBalance, int currentStock, int safetyThreshold) {
        Product product = new Product(name, sku, openingBalance, safetyThreshold);
        ReflectionTestUtils.setField(product, "id", id);
        product.setCurrentStock(currentStock);
        return product;
    }
}