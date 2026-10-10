package com.stocksight.controller;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stocksight.dto.ProductDtos.ProductRequest;
import com.stocksight.dto.ProductDtos.ProductResponse;
import com.stocksight.service.InventoryService;

import jakarta.validation.Valid;

@RestController @RequestMapping("/api/products")
public class ProductController {
    private final InventoryService service;
    public ProductController(InventoryService service) { this.service=service; }
    @GetMapping public Page<ProductResponse> list(@RequestParam(required=false) String q,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size,
        @RequestParam(defaultValue="name") String sort) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination values.");
        return service.listProducts(q, PageRequest.of(page,size,Sort.by(sort).ascending()));
    }
    @GetMapping("/sku/{sku}")
    public ProductResponse getBySku(@PathVariable String sku) { return service.getProductBySku(sku); }
    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable long id) { return service.getProductById(id); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody ProductRequest r) { return service.createProduct(r); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable long id,@Valid @RequestBody ProductRequest r) { return service.updateProduct(id,r); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> delete(@PathVariable long id) { service.deleteProduct(id); return Map.of("message","Product deleted."); }
    @GetMapping("/reorder-suggestions")
    public List<Map<String,Object>> reorderSuggestions(@RequestParam(required=false) LocalDate from,
                                                        @RequestParam(required=false) LocalDate to) {
        return service.reorderSuggestions(from, to);
    }
    @GetMapping("/{id}/analytics")
    public Map<String,Object> analytics(@PathVariable long id, @RequestParam(required=false) LocalDate from,
                                        @RequestParam(required=false) LocalDate to) {
        return service.analytics(id, from, to);
    }
}
