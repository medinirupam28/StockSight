package com.stocksight.dto;
import jakarta.validation.constraints.NotBlank;
public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record AuthResponse(String token, String username, String role) {}
}
