package com.happydev.prestockbackend.config;

import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBootstrapConfig {

    @Bean
    public CommandLineRunner seedDefaultSecurityUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.bootstrap.username:admin}") String bootstrapUsername,
            @Value("${app.security.bootstrap.password:admin1234}") String bootstrapPassword,
            @Value("${app.security.bootstrap.email:admin@prostock.local}") String bootstrapEmail
    ) {
        return args -> {
            if (userRepository.existsByUsername(bootstrapUsername)) {
                return;
            }
            User user = new User();
            user.setUsername(bootstrapUsername);
            user.setPassword(passwordEncoder.encode(bootstrapPassword));
            user.setEmail(bootstrapEmail);
            user.setFirstName("System");
            user.setLastName("Admin");
            userRepository.save(user);
        };
    }
}
