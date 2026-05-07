package com.happydev.prestockbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Definido fuera de {@link SecurityConfig} para que el bean exista estable incluso con
 * reinicios de Spring DevTools (evita fallos al inyectar {@link PasswordEncoder} en el seed).
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PasswordEncoderConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
