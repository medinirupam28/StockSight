package com.stocksight.security;

import com.stocksight.model.Role;
import com.stocksight.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-with-more-than-32-bytes-long";

    @Test
    void acceptsSignedUnexpiredTokenAndRejectsExpiredOrMalformedTokens() {
        JwtService service = new JwtService(SECRET, 60_000);
        String valid = service.generate(new User("test-user", "unused", Role.SUPPLIER));
        long now = System.currentTimeMillis();
        String expired = Jwts.builder().subject("test-user")
            .issuedAt(new Date(now - 60_000)).expiration(new Date(now - 1))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        assertTrue(service.valid(valid));
        assertFalse(service.valid(expired));
        assertFalse(service.valid("not-a-jwt"));
    }

    @Test
    void rejectsWeakSecretAndNonpositiveExpiration() {
        IllegalArgumentException weakSecret = assertThrows(IllegalArgumentException.class,
            () -> new JwtService("too-short", 60_000));
        IllegalArgumentException zeroExpiration = assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 0));
        IllegalArgumentException negativeExpiration = assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, -1));
        assertTrue(weakSecret.getMessage().contains("at least 32 bytes"));
        assertTrue(zeroExpiration.getMessage().contains("must be positive"));
        assertTrue(negativeExpiration.getMessage().contains("must be positive"));
    }
}