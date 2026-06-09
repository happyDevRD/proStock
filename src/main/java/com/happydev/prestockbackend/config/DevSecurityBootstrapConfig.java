package com.happydev.prestockbackend.config;

import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
@Profile("local")
public class DevSecurityBootstrapConfig {

    private record DevUserSeed(
            String username,
            String password,
            String email,
            String firstName,
            String lastName,
            UserRole role
    ) {}

    @Bean
    public CommandLineRunner seedDevUsers(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            alignKnownDevRoles(userRepository);

            List<DevUserSeed> usersToSeed = List.of(
                    new DevUserSeed("manager", "manager1234", "manager@prostock.local", "Ana", "Manager", UserRole.MANAGER),
                    new DevUserSeed("cashier", "cashier1234", "cashier@prostock.local", "Luis", "Cashier", UserRole.CASHIER),
                    new DevUserSeed("user", "user1234", "user@prostock.local", "Invitado", "User", UserRole.USER)
            );

            for (DevUserSeed seed : usersToSeed) {
                if (userRepository.existsByUsername(seed.username()) || userRepository.existsByEmail(seed.email())) {
                    continue;
                }
                User user = new User();
                user.setUsername(seed.username());
                user.setPassword(passwordEncoder.encode(seed.password()));
                user.setEmail(seed.email());
                user.setFirstName(seed.firstName());
                user.setLastName(seed.lastName());
                user.setRole(seed.role());
                userRepository.save(user);
            }
        };
    }

    private static void alignKnownDevRoles(UserRepository userRepository) {
        patchRole(userRepository, "manager", UserRole.MANAGER);
        patchRole(userRepository, "cashier", UserRole.CASHIER);
        patchRole(userRepository, "user", UserRole.USER);
    }

    private static void patchRole(UserRepository userRepository, String username, UserRole role) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getRole() != role) {
                user.setRole(role);
                userRepository.save(user);
            }
        });
    }
}
