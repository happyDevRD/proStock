package com.happydev.prestockbackend.dto;

import java.util.List;

public record RolePermissionsDto(
        String role,
        List<String> permissionCodes
) {}
