package com.stocksight.dto;
import com.stocksight.model.MovementType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public final class MovementDtos {
    private MovementDtos() {}
    public record MovementRequest(@NotNull Long productId, @NotNull MovementType movementType,
                                  @Min(1) int quantity, @NotNull LocalDate billDate,
                                  @NotNull LocalDate deliveryDate) {}
    public record MovementResponse(Long id, Long productId, String productName, MovementType movementType,
                                   int quantity, LocalDate billDate, LocalDate deliveryDate,
                                   boolean anomaly, String anomalyReason, int currentStock) {}
    public record InventoryAnomalyResponse(Long transactionId, Long productId, String productName, String sku,
                                           String type, String severity, String reason, MovementType movementType,
                                           int quantity, LocalDate billDate, int stockBefore, int stockAfter) {}
}
