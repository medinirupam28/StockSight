
package com.stocksight.config;

import com.stocksight.model.Role;
import com.stocksight.model.User;
import com.stocksight.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminInitializer {

    @Bean
    ApplicationRunner createAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_USERNAME:}") String username,
            @Value("${ADMIN_PASSWORD:}") String password) {

        return args -> {
            if (username.isBlank() || password.isBlank()) {
                System.out.println("Admin creation skipped: credentials not configured.");
                return;
            }

            if (userRepository.findByUsername(username).isPresent()) {
                System.out.println("Admin username already exists.");
                return;
            }

            User admin = new User();
            admin.setUsername(username);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);

            userRepository.save(admin);
            System.out.println("Admin account created successfully.");
        };
    }
}
