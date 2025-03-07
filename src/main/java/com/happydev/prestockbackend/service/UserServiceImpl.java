package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public User createUser(User user) {
        // Validaciones (ej: que el username y email sean únicos)
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("El nombre de usuario ya existe.");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }

        // Encriptar la contraseña (¡IMPORTANTE!)  Debe hacerse aquí, antes de guardar.
        // user.setPassword(passwordEncoder.encode(user.getPassword())); // Necesitarás un PasswordEncoder

        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional
    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setUsername(userDetails.getUsername());
        user.setEmail(userDetails.getEmail());
        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        // Actualiza otros campos...

        // Si cambias la contraseña, ¡encriptarla!
        // if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
        //     user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        // }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userRepository.delete(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
