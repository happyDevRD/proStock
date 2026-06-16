package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.PasswordPolicyService;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PasswordPolicyService passwordPolicyService;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            PasswordPolicyService passwordPolicyService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.passwordPolicyService = passwordPolicyService;
    }

    @Override
    @Transactional
    public User createUser(@NonNull User user, @NonNull String actorUsername) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("El nombre de usuario ya existe.");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria.");
        }
        passwordPolicyService.validate(user.getPassword());
        if (user.getRole() == null) {
            user.setRole(UserRole.USER);
        }
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepository.save(Objects.requireNonNull(user));
        auditService.record(actorUsername, "USER_CREATED", "User", saved.getId(),
                Map.of("username", saved.getUsername(), "role", saved.getRole() != null ? saved.getRole().name() : ""));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getUserById(@NonNull Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional
    public User updateUser(@NonNull Long id, @NonNull User userDetails, @NonNull String actorUsername) {
        User actor = userRepository.findByUsername(actorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", actorUsername));
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (actor.getId().equals(user.getId())
                && userDetails.getRole() != null
                && user.getRole() != userDetails.getRole()) {
            throw new IllegalArgumentException("No puedes cambiar tu propio rol. Pide a otro administrador que lo haga.");
        }

        // Solo GESTOR puede modificar usuarios con rol GESTOR
        if (user.getRole() == UserRole.GESTOR && actor.getRole() != UserRole.GESTOR) {
            throw new IllegalArgumentException("No se puede modificar un usuario de gestión sin tener el rol de gestor.");
        }

        // Evitar que un actor no-GESTOR promueva a otro usuario al rol GESTOR
        if (userDetails.getRole() == UserRole.GESTOR && actor.getRole() != UserRole.GESTOR) {
            throw new IllegalArgumentException("Solo el proveedor del software puede asignar el rol de gestión.");
        }

        if (user.getRole() == UserRole.ADMIN
                && userDetails.getRole() != null
                && userDetails.getRole() != UserRole.ADMIN
                && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("No se puede quitar el rol ADMIN al unico administrador del sistema.");
        }

        if (userDetails.getUsername() != null && !userDetails.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(userDetails.getUsername())) {
                throw new IllegalArgumentException("El nombre de usuario ya existe.");
            }
            user.setUsername(userDetails.getUsername());
        }

        if (userDetails.getEmail() != null && !userDetails.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(userDetails.getEmail())) {
                throw new IllegalArgumentException("El correo electrónico ya está registrado.");
            }
            user.setEmail(userDetails.getEmail());
        }

        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());

        if (userDetails.getRole() != null) {
            user.setRole(userDetails.getRole());
        }

        if (userDetails.getPassword() != null && !userDetails.getPassword().isBlank()) {
            passwordPolicyService.validate(userDetails.getPassword());
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
            user.setPasswordChangedAt(LocalDateTime.now());
        }

        User saved = userRepository.save(Objects.requireNonNull(user));
        auditService.record(actorUsername, "USER_UPDATED", "User", saved.getId(),
                Map.of("username", saved.getUsername(), "role", saved.getRole() != null ? saved.getRole().name() : ""));
        return saved;
    }

    @Override
    @Transactional
    public void deleteUser(@NonNull Long id, @NonNull String actorUsername) {
        User actor = userRepository.findByUsername(actorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", actorUsername));
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (actor.getId().equals(user.getId())) {
            throw new IllegalArgumentException("No puedes eliminar tu propio usuario.");
        }

        // Solo GESTOR puede eliminar usuarios con rol GESTOR
        if (user.getRole() == UserRole.GESTOR && actor.getRole() != UserRole.GESTOR) {
            throw new IllegalArgumentException("No se puede eliminar un usuario de gestión sin tener el rol de gestor.");
        }
        if (user.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("No se puede eliminar el unico administrador del sistema.");
        }

        Long targetId = user.getId();
        String targetUsername = user.getUsername();
        userRepository.delete(Objects.requireNonNull(user));
        auditService.record(actorUsername, "USER_DELETED", "User", targetId,
                Map.of("username", targetUsername));
    }

    @Override
    public Optional<User> findByUsername(@NonNull String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(@NonNull String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(@NonNull String email) {
        return userRepository.existsByEmail(email);
    }
}
