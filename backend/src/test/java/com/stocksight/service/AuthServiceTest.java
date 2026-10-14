package com.stocksight.service;
import com.stocksight.dto.AuthDtos.RegisterRequest;
import com.stocksight.dto.AuthDtos.RegistrationResponse;
import com.stocksight.model.Role;
import com.stocksight.model.User;
import com.stocksight.repository.UserRepository;
import com.stocksight.service.AuthService;
import com.stocksight.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Test
    void registrationStoresPendingSupplierWithEncodedPassword() {
        UserRepository users = mock(UserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtService jwt = new JwtService(
            "unit-test-secret-with-more-than-32-bytes-long",
            60000
        );
        AuthenticationManager auth = mock(AuthenticationManager.class);

        when(users.existsByUsername("supplier-test")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });

        AuthService service = new AuthService(users, encoder, auth, jwt);

        var response = service.register(new RegisterRequest(
            "ABC Supplies",
            "Test Contact",
            "supplier@example.com",
            "9876543210",
            "Electronics",
            "supplier-test",
            "test-password-123"
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("PENDING", response.status());
        assertEquals("supplier-test", saved.getUsername());
        assertEquals(Role.SUPPLIER, saved.getRole());
        assertEquals("PENDING", saved.getApprovalStatus());
        assertTrue(encoder.matches(
            "test-password-123", saved.getPassword()));
        verifyNoInteractions(auth);
    }
}
