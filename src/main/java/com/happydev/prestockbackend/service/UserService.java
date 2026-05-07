package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.User;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User createUser(@NonNull User user, @NonNull String actorUsername);
    List<User> getAllUsers();
    Optional<User> getUserById(@NonNull Long id);
    User updateUser(@NonNull Long id, @NonNull User userDetails, @NonNull String actorUsername);

    void deleteUser(@NonNull Long id, @NonNull String actorUsername);
    Optional<User> findByUsername(@NonNull String username);
    boolean existsByUsername(@NonNull String username);
    boolean existsByEmail(@NonNull String email);

}
