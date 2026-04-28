package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.User;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User createUser(@NonNull User user);
    List<User> getAllUsers();
    Optional<User> getUserById(@NonNull Long id);
    User updateUser(@NonNull Long id, @NonNull User userDetails);
    void deleteUser(@NonNull Long id);
    Optional<User> findByUsername(@NonNull String username);
    boolean existsByUsername(@NonNull String username);
    boolean existsByEmail(@NonNull String email);

}
