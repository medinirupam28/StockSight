package com.stocksight.controller;
import com.stocksight.dto.MovementDtos.*;
import com.stocksight.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDate;

@RestController @RequestMapping("/api/movements")
public class StockMovementController {
    private final InventoryService service;
    public StockMovementController(InventoryService service) { this.service=service; }
    @GetMapping public List<MovementResponse> list(@RequestParam(required=false) Long productId) {
        return service.listMovements(productId);
    }
    @GetMapping("/anomalies")
    public List<InventoryAnomalyResponse> anomalies(@RequestParam(required=false) Long productId,
        @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to) {
        return service.listAnomalies(productId, from, to);
    }
    @PostMapping @PreAuthorize("hasAnyRole('ADMIN','SUPPLIER')")
    public MovementResponse create(@Valid @RequestBody MovementRequest r) { return service.createMovement(r); }
}
