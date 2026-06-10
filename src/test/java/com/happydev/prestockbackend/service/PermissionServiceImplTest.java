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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Test
    void getAllPermissions_mapsEntitiesToDtos() {
        given(permissionRepository.findAllByOrderByCategoryAscCodeAsc())
                .willReturn(List.of(permission(1L, "view.dashboard", "VIEW", "Ver dashboard", "desc")));

        List<PermissionDto> result = permissionService.getAllPermissions();

        assertEquals(1, result.size());
        assertEquals("view.dashboard", result.get(0).code());
        assertEquals("VIEW", result.get(0).category());
        assertEquals("desc", result.get(0).description());
    }

    @Test
    void getRolePermissionMatrix_returnsAllRolesWithSortedCodes() {
        Permission viewDashboard = permission(1L, "view.dashboard", "VIEW", "Ver dashboard", null);
        Permission viewUsers = permission(2L, "view.users", "VIEW", "Usuarios", null);

        given(rolePermissionRepository.findAll()).willReturn(List.of(
                new RolePermission(1L, UserRole.ADMIN, viewUsers),
                new RolePermission(2L, UserRole.ADMIN, viewDashboard)
        ));

        List<RolePermissionsDto> matrix = permissionService.getRolePermissionMatrix();

        assertEquals(UserRole.values().length, matrix.size());
        RolePermissionsDto adminRow = findRole(matrix, "ADMIN");
        assertEquals(List.of("view.dashboard", "view.users"), adminRow.permissionCodes());

        RolePermissionsDto userRow = findRole(matrix, "USER");
        assertTrue(userRow.permissionCodes().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRolePermissionMatrix_skipsGestorAndPersistsOtherRoles() {
        Permission viewDashboard = permission(1L, "view.dashboard", "VIEW", "Ver dashboard", null);
        Permission viewUsers = permission(2L, "view.users", "VIEW", "Usuarios", null);
        given(permissionRepository.findAll()).willReturn(List.of(viewDashboard, viewUsers));
        given(rolePermissionRepository.findAll()).willReturn(List.of());

        List<RolePermissionsDto> updates = List.of(
                new RolePermissionsDto("GESTOR", List.of("view.dashboard")),
                new RolePermissionsDto("MANAGER", List.of("view.dashboard", "view.users", "unknown.code")),
                new RolePermissionsDto("NOT_A_ROLE", List.of("view.dashboard"))
        );

        permissionService.updateRolePermissionMatrix(updates, "admin");

        verify(rolePermissionRepository, never()).deleteByRole(UserRole.GESTOR);
        verify(rolePermissionRepository).deleteByRole(UserRole.MANAGER);
        verify(rolePermissionRepository).flush();

        ArgumentCaptor<List<RolePermission>> captor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(captor.capture());
        List<RolePermission> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(rp -> rp.getRole() == UserRole.MANAGER));

        verify(auditService).record(eq("admin"), eq("ROLE_PERMISSIONS_UPDATED"), eq("RolePermission"), isNull(), any());
    }

    @Test
    void getEffectivePermissions_gestorBypassesRoleTableAndReturnsAllCodes() {
        User gestor = new User();
        gestor.setId(1L);
        gestor.setRole(UserRole.GESTOR);

        given(permissionRepository.findAll()).willReturn(List.of(
                permission(1L, "view.dashboard", "VIEW", "Ver dashboard", null),
                permission(2L, "settings.manage_permissions", "SETTINGS", "Permisos", null)
        ));

        List<String> result = permissionService.getEffectivePermissions(gestor);

        assertEquals(List.of("settings.manage_permissions", "view.dashboard"), result);
        verify(rolePermissionRepository, never()).findByRole(any());
    }

    @Test
    void getEffectivePermissions_appliesGrantAndRevokeOverridesOverRoleBase() {
        User cashier = new User();
        cashier.setId(5L);
        cashier.setRole(UserRole.CASHIER);

        Permission viewPos = permission(1L, "view.pos", "VIEW", "POS", null);
        Permission viewInvoice = permission(2L, "view.invoice", "VIEW", "Facturas", null);
        Permission viewReports = permission(3L, "view.reports", "VIEW", "Reportes", null);

        given(rolePermissionRepository.findByRole(UserRole.CASHIER)).willReturn(List.of(
                new RolePermission(10L, UserRole.CASHIER, viewPos),
                new RolePermission(11L, UserRole.CASHIER, viewInvoice)
        ));
        given(userPermissionOverrideRepository.findByUserId(5L)).willReturn(List.of(
                new UserPermissionOverride(20L, cashier, viewReports, true),
                new UserPermissionOverride(21L, cashier, viewInvoice, false)
        ));

        List<String> result = permissionService.getEffectivePermissions(cashier);

        assertEquals(List.of("view.pos", "view.reports"), result);
    }

    private RolePermissionsDto findRole(List<RolePermissionsDto> matrix, String role) {
        return matrix.stream().filter(r -> r.role().equals(role)).findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el rol: " + role));
    }

    private Permission permission(Long id, String code, String category, String name, String description) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setCategory(category);
        permission.setName(name);
        permission.setDescription(description);
        return permission;
    }
}
