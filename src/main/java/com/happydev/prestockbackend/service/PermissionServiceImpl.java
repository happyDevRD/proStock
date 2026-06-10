package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PermissionDto;
import com.happydev.prestockbackend.dto.RolePermissionsDto;
import com.happydev.prestockbackend.entity.Permission;
import com.happydev.prestockbackend.entity.RolePermission;
import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserPermissionOverride;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.PermissionRepository;
import com.happydev.prestockbackend.repository.RolePermissionRepository;
import com.happydev.prestockbackend.repository.UserPermissionOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final AuditService auditService;

    public PermissionServiceImpl(
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            UserPermissionOverrideRepository userPermissionOverrideRepository,
            AuditService auditService
    ) {
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAllByOrderByCategoryAscCodeAsc().stream()
                .map(p -> new PermissionDto(p.getCode(), p.getCategory(), p.getName(), p.getDescription()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionsDto> getRolePermissionMatrix() {
        Map<UserRole, List<RolePermission>> byRole = rolePermissionRepository.findAll().stream()
                .collect(Collectors.groupingBy(RolePermission::getRole));

        List<RolePermissionsDto> matrix = new ArrayList<>();
        for (UserRole role : UserRole.values()) {
            List<String> codes = byRole.getOrDefault(role, List.of()).stream()
                    .map(rp -> rp.getPermission().getCode())
                    .sorted()
                    .toList();
            matrix.add(new RolePermissionsDto(role.name(), codes));
        }
        return matrix;
    }

    @Override
    @Transactional
    public List<RolePermissionsDto> updateRolePermissionMatrix(List<RolePermissionsDto> matrix, String actorUsername) {
        Map<String, Permission> permissionsByCode = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getCode, p -> p));

        Map<String, Object> changes = new LinkedHashMap<>();

        for (RolePermissionsDto entry : matrix) {
            UserRole role;
            try {
                role = UserRole.valueOf(entry.role());
            } catch (IllegalArgumentException | NullPointerException ex) {
                continue;
            }

            // GESTOR es superusuario por código (bypass en getEffectivePermissions); su fila no se edita.
            if (role == UserRole.GESTOR) {
                continue;
            }

            List<Permission> resolved = entry.permissionCodes() == null
                    ? List.of()
                    : entry.permissionCodes().stream()
                            .distinct()
                            .map(permissionsByCode::get)
                            .filter(Objects::nonNull)
                            .toList();

            rolePermissionRepository.deleteByRole(role);
            rolePermissionRepository.flush();
            rolePermissionRepository.saveAll(
                    resolved.stream().map(permission -> new RolePermission(null, role, permission)).toList()
            );

            changes.put(role.name(), resolved.stream().map(Permission::getCode).sorted().toList());
        }

        if (!changes.isEmpty()) {
            auditService.record(actorUsername, "ROLE_PERMISSIONS_UPDATED", "RolePermission", null, changes);
        }

        return getRolePermissionMatrix();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getEffectivePermissions(User user) {
        if (user.getRole() == UserRole.GESTOR) {
            return permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .sorted()
                    .toList();
        }

        Set<String> effective = new LinkedHashSet<>(
                rolePermissionRepository.findByRole(user.getRole()).stream()
                        .map(rp -> rp.getPermission().getCode())
                        .toList()
        );

        for (UserPermissionOverride override : userPermissionOverrideRepository.findByUserId(user.getId())) {
            String code = override.getPermission().getCode();
            if (override.isGranted()) {
                effective.add(code);
            } else {
                effective.remove(code);
            }
        }

        return effective.stream().sorted().toList();
    }
}
