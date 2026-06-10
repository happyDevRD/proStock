package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.PermissionDto;
import com.happydev.prestockbackend.dto.RolePermissionsDto;
import com.happydev.prestockbackend.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<List<PermissionDto>> getPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @GetMapping("/role-matrix")
    public ResponseEntity<List<RolePermissionsDto>> getRoleMatrix() {
        return ResponseEntity.ok(permissionService.getRolePermissionMatrix());
    }

    @PutMapping("/role-matrix")
    public ResponseEntity<List<RolePermissionsDto>> updateRoleMatrix(@RequestBody List<RolePermissionsDto> matrix,
                                                                       Principal principal) {
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(permissionService.updateRolePermissionMatrix(matrix, actor));
    }
}
