package com.stocksight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksight.model.Product;
import com.stocksight.repository.ProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityWorkflowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository products;
    @Value("${app.jwt.secret}") private String jwtSecret;

    @Test
    void anonymousAndExpiredJwtRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());

        long now = System.currentTimeMillis();
        String expired = Jwts.builder().subject("missing-test-user")
            .issuedAt(new Date(now - 60_000)).expiration(new Date(now - 1))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + expired))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void supplierRegistrationLoginAndProtectedReadUseRealJwtFlow() throws Exception {
        String username = "QA-LOGIN-" + UUID.randomUUID();
        String password = "qa-password-" + UUID.randomUUID();
        String registerBody = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        JsonNode registration = objectMapper.readTree(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(registerBody))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assert registration.path("role").asText().equals("SUPPLIER");

        String loginBody = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String token = login.path("token").asText();
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPPLIER")
    void supplierCannotMutateProductCatalog() throws Exception {
        mockMvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Denied\",\"sku\":\"DENIED-ROLE\",\"openingBalance\":1,\"safetyThreshold\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsInvalidProductAndMovementInputs() throws Exception {
        mockMvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Invalid\",\"sku\":\"INVALID-QA\",\"openingBalance\":-1,\"safetyThreshold\":1}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/movements").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":99999999,\"movementType\":\"INWARD\",\"quantity\":0,\"billDate\":\"2026-10-10\",\"deliveryDate\":\"2026-10-10\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void adminCanCreateListEditDeleteProductWithoutMovementHistory() throws Exception {
        String sku = "QA-CRUD-" + UUID.randomUUID();
        String createBody = "{\"name\":\"QA Original\",\"sku\":\"%s\",\"openingBalance\":12,\"safetyThreshold\":3}".formatted(sku);
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/products").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long id = created.path("id").asLong();

        mockMvc.perform(get("/api/products").param("q", sku))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].currentStock").value(12));
        mockMvc.perform(put("/api/products/{id}", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"QA Updated\",\"sku\":\"%s\",\"openingBalance\":12,\"safetyThreshold\":5}".formatted(sku)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("QA Updated"))
            .andExpect(jsonPath("$.safetyThreshold").value(5));
        mockMvc.perform(delete("/api/products/{id}", id).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void adminStockWorkflowReconcilesHistoryAndRejectsOversell() throws Exception {
        Product seed = products.save(new Product("QA Stock Product", "QA-STOCK-" + UUID.randomUUID(), 10, 4));
        LocalDate today = LocalDate.now();
        mockMvc.perform(post("/api/movements").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(movementBody(seed.getId(), "INWARD", 3, today)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.currentStock").value(13));
        mockMvc.perform(post("/api/movements").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(movementBody(seed.getId(), "OUTWARD", 10, today)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.currentStock").value(3))
            .andExpect(jsonPath("$.anomaly").value(true));
        mockMvc.perform(get("/api/movements").param("productId", seed.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[1].currentStock").value(3));
        mockMvc.perform(get("/api/products/{id}", seed.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lowStock").value(true));
        mockMvc.perform(get("/api/products/{id}/analytics", seed.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sufficientHistory").value(false))
            .andExpect(jsonPath("$.reorderRecommended").value(true));
        mockMvc.perform(post("/api/movements").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(movementBody(seed.getId(), "OUTWARD", 4, today)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPPLIER")
    @Transactional
    void supplierCanRecordStockMovement() throws Exception {
        Product seed = products.save(new Product("QA Supplier Product", "QA-SUPPLIER-" + UUID.randomUUID(), 5, 1));
        mockMvc.perform(post("/api/movements").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(movementBody(seed.getId(), "INWARD", 2, LocalDate.now())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.currentStock").value(7));
    }

    @Test
    void corsAllowsOnlyConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/products").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(options("/api/products").header("Origin", "https://untrusted.invalid")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private String movementBody(long productId, String type, int quantity, LocalDate date) {
        return "{\"productId\":%d,\"movementType\":\"%s\",\"quantity\":%d,\"billDate\":\"%s\",\"deliveryDate\":\"%s\"}"
            .formatted(productId, type, quantity, date, date);
    }
}