package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User createUser(User user);
    List<User> getAllUsers();
    Optional<User> getUserById(Long id);
    User updateUser(Long id, User userDetails);
    void deleteUser(Long id);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

}
