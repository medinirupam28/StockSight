
package com.stocksight.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank String companyName,
        @NotBlank String contactName,
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank String productCategory,
        @NotBlank String username,
        @NotBlank String password
    ) {}

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {}

    public record AuthResponse(
        String token,
        String username,
        String role
    ) {}

    public record RegistrationResponse(
        String message,
        String status
    ) {}
}
