package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.RolePermission;
import com.happydev.prestockbackend.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole(UserRole role);

    void deleteByRole(UserRole role);
}
