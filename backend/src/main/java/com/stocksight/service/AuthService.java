
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
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthService(
        UserRepository users,
        PasswordEncoder encoder,
        AuthenticationManager authManager,
        JwtService jwt
    ) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
    }

    public RegistrationResponse register(RegisterRequest r) {
        String username = r.username().trim();

        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists.");
        }

        if (r.password().length() < 8) {
            throw new IllegalArgumentException(
                "Password must contain at least 8 characters.");
        }

        User user = new User(
            username, encoder.encode(r.password()), Role.SUPPLIER);

        user.setCompanyName(r.companyName().trim());
        user.setContactName(r.contactName().trim());
        user.setEmail(r.email().trim());
        user.setPhone(r.phone().trim());
        user.setProductCategory(r.productCategory().trim());
        user.setApprovalStatus("PENDING");

        users.save(user);

        return new RegistrationResponse(
            "Registration submitted. Wait for admin approval before logging in.",
            "PENDING"
        );
    }

    public AuthResponse login(LoginRequest r) {
        User user = users.findByUsername(r.username().trim())
            .orElseThrow(() ->
                new BadCredentialsException("Invalid username or password."));

        if ("PENDING".equals(user.getApprovalStatus())) {
            throw new DisabledException(
                "Your registration is pending admin approval.");
        }

        if (!"APPROVED".equals(user.getApprovalStatus())) {
            throw new DisabledException(
                "Your supplier account has not been approved.");
        }

        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                user.getUsername(), r.password()));

        return new AuthResponse(
            jwt.generate(user),
            user.getUsername(),
            user.getRole().name()
        );
    }
}
