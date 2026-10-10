package com.stocksight.service;

import com.stocksight.dto.AuthDtos.RegisterRequest;
import com.stocksight.model.Role;
import com.stocksight.model.User;
import com.stocksight.repository.UserRepository;
import com.stocksight.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    @Test
    void registrationStoresBcryptHashAndAssignsSupplierRole() {
        UserRepository users = mock(UserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtService jwt = new JwtService("unit-test-secret-with-more-than-32-bytes-long", 60_000);
        when(users.existsByUsername("supplier-test")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthService auth = new AuthService(users, encoder, mock(AuthenticationManager.class), jwt);

        var response = auth.register(new RegisterRequest("supplier-test", "test-password-123"));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(users).save(savedUser.capture());
        User stored = savedUser.getValue();
        assertNotEquals("test-password-123", stored.getPassword());
        assertTrue(stored.getPassword().startsWith("$2"));
        assertTrue(encoder.matches("test-password-123", stored.getPassword()));
        assertTrue(stored.getRole() == Role.SUPPLIER);
        assertTrue(jwt.valid(response.token()));
    }
}