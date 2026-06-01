package com.happydev.prestockbackend.config;

import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Crea el usuario de gestión del proveedor del software al arrancar.
 * Solo actúa si PROSTOCK_GESTOR_PASSWORD está definido como variable de entorno.
 * Si el usuario ya existe, no lo modifica.
 */
@Configuration
public class GestorBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(GestorBootstrapConfig.class);

    @Bean
    public CommandLineRunner seedGestorUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.gestor.username:gestor}") String gestorUsername,
            @Value("${app.gestor.password:}") String gestorPassword,
            @Value("${app.gestor.email:gestor@prostock.app}") String gestorEmail,
            @Value("${app.gestor.first-name:Gestión}") String gestorFirstName,
            @Value("${app.gestor.last-name:ProStock}") String gestorLastName
    ) {
        return args -> {
            if (gestorPassword == null || gestorPassword.isBlank()) {
                log.debug("GESTOR bootstrap omitido: PROSTOCK_GESTOR_PASSWORD no definido.");
                return;
            }

            userRepository.findByUsername(gestorUsername).ifPresentOrElse(
                existing -> {
                    // Sincroniza la contraseña con el valor actual del secreto en cada arranque
                    existing.setPassword(passwordEncoder.encode(gestorPassword));
                    existing.setRole(UserRole.GESTOR);
                    existing.setEmail(gestorEmail);
                    userRepository.save(existing);
                    log.info("Contraseña del usuario GESTOR '{}' sincronizada con el secreto.", gestorUsername);
                },
                () -> {
                    User gestor = new User();
                    gestor.setUsername(gestorUsername);
                    gestor.setPassword(passwordEncoder.encode(gestorPassword));
                    gestor.setEmail(gestorEmail);
                    gestor.setFirstName(gestorFirstName);
                    gestor.setLastName(gestorLastName);
                    gestor.setRole(UserRole.GESTOR);
                    userRepository.save(gestor);
                    log.info("Usuario GESTOR '{}' creado correctamente.", gestorUsername);
                }
            );
        };
    }
}
