package com.stocksight.controller;
import com.stocksight.dto.AuthDtos.*;
import com.stocksight.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }
    @PostMapping("/register") public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(r));
    }
    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest r) { return service.login(r); }
}
