package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.UserPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {
    List<UserPermissionOverride> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
