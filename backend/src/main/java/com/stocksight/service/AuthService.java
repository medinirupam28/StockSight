package com.stocksight.service;
import com.stocksight.dto.AuthDtos.*;
import com.stocksight.model.*;
import com.stocksight.repository.UserRepository;
import com.stocksight.security.JwtService;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class AuthService {
    private final UserRepository users; private final PasswordEncoder encoder;
    private final AuthenticationManager authManager; private final JwtService jwt;
    public AuthService(UserRepository users, PasswordEncoder encoder, AuthenticationManager authManager, JwtService jwt) {
        this.users=users; this.encoder=encoder; this.authManager=authManager; this.jwt=jwt;
    }
    public AuthResponse register(RegisterRequest r) {
        if (users.existsByUsername(r.username())) throw new IllegalArgumentException("Username already exists.");
        if (r.password().length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters.");
        User user = users.save(new User(r.username().trim(), encoder.encode(r.password()), Role.SUPPLIER));
        return new AuthResponse(jwt.generate(user), user.getUsername(), user.getRole().name());
    }
    public AuthResponse login(LoginRequest r) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(r.username(), r.password()));
        User user = users.findByUsername(r.username()).orElseThrow();
        return new AuthResponse(jwt.generate(user), user.getUsername(), user.getRole().name());
    }
}
