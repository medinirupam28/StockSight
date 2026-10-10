package com.stocksight.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
public final class ProductDtos {
    private ProductDtos() {}
    public record ProductRequest(@NotBlank String name, @NotBlank String sku,
                                 @JsonAlias("quantity") @Min(0) int openingBalance,
                                 @JsonAlias("minimumStock") @Min(0) int safetyThreshold) {}
    public record ProductResponse(Long id, String name, String sku, int openingBalance,
                                  int safetyThreshold, int currentStock, boolean lowStock) {}
}
