package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PermissionDto;
import com.happydev.prestockbackend.dto.RolePermissionsDto;
import com.happydev.prestockbackend.entity.User;

import java.util.List;

public interface PermissionService {
    List<PermissionDto> getAllPermissions();

    List<RolePermissionsDto> getRolePermissionMatrix();

    List<RolePermissionsDto> updateRolePermissionMatrix(List<RolePermissionsDto> matrix, String actorUsername);

    List<String> getEffectivePermissions(User user);
}
