package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PermissionDto;
import com.happydev.prestockbackend.dto.RolePermissionsDto;
import com.happydev.prestockbackend.dto.UserPermissionOverrideDto;
import com.happydev.prestockbackend.dto.UserPermissionStateDto;
import com.happydev.prestockbackend.entity.Permission;
import com.happydev.prestockbackend.entity.RolePermission;
import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserPermissionOverride;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.PermissionRepository;
import com.happydev.prestockbackend.repository.RolePermissionRepository;
import com.happydev.prestockbackend.repository.UserPermissionOverrideRepository;
import com.happydev.prestockbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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
    private UserRepository userRepository;

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

    @Test
    void getUserPermissionOverrides_userNotFound_throwsResourceNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> permissionService.getUserPermissionOverrides(99L));
    }

    @Test
    void getUserPermissionOverrides_marksFromRoleAndAppliedOverrides() {
        User cashier = new User();
        cashier.setId(5L);
        cashier.setRole(UserRole.CASHIER);

        Permission viewPos = permission(1L, "view.pos", "VIEW", "POS", null);
        Permission viewInvoice = permission(2L, "view.invoice", "VIEW", "Facturas", null);
        Permission viewReports = permission(3L, "view.reports", "VIEW", "Reportes", null);

        given(userRepository.findById(5L)).willReturn(Optional.of(cashier));
        given(rolePermissionRepository.findByRole(UserRole.CASHIER)).willReturn(List.of(
                new RolePermission(10L, UserRole.CASHIER, viewPos),
                new RolePermission(11L, UserRole.CASHIER, viewInvoice)
        ));
        given(userPermissionOverrideRepository.findByUserId(5L)).willReturn(List.of(
                new UserPermissionOverride(20L, cashier, viewReports, true),
                new UserPermissionOverride(21L, cashier, viewInvoice, false)
        ));
        given(permissionRepository.findAllByOrderByCategoryAscCodeAsc())
                .willReturn(List.of(viewInvoice, viewPos, viewReports));

        List<UserPermissionStateDto> result = permissionService.getUserPermissionOverrides(5L);

        UserPermissionStateDto invoice = findCode(result, "view.invoice");
        assertTrue(invoice.fromRole());
        assertEquals(Boolean.FALSE, invoice.override());
        assertFalse(invoice.effective());

        UserPermissionStateDto pos = findCode(result, "view.pos");
        assertTrue(pos.fromRole());
        assertNull(pos.override());
        assertTrue(pos.effective());

        UserPermissionStateDto reports = findCode(result, "view.reports");
        assertFalse(reports.fromRole());
        assertEquals(Boolean.TRUE, reports.override());
        assertTrue(reports.effective());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateUserPermissionOverrides_replacesExistingOverridesAndRecordsAudit() {
        User cashier = new User();
        cashier.setId(5L);
        cashier.setRole(UserRole.CASHIER);

        Permission viewReports = permission(3L, "view.reports", "VIEW", "Reportes", null);

        given(userRepository.findById(5L)).willReturn(Optional.of(cashier));
        given(permissionRepository.findAll()).willReturn(List.of(viewReports));
        given(rolePermissionRepository.findByRole(UserRole.CASHIER)).willReturn(List.of());
        given(permissionRepository.findAllByOrderByCategoryAscCodeAsc()).willReturn(List.of(viewReports));

        permissionService.updateUserPermissionOverrides(
                5L, List.of(new UserPermissionOverrideDto("view.reports", true), new UserPermissionOverrideDto("unknown.code", true)), "admin");

        verify(userPermissionOverrideRepository).deleteByUserId(5L);
        verify(userPermissionOverrideRepository).flush();

        ArgumentCaptor<List<UserPermissionOverride>> captor = ArgumentCaptor.forClass(List.class);
        verify(userPermissionOverrideRepository).saveAll(captor.capture());
        List<UserPermissionOverride> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("view.reports", saved.get(0).getPermission().getCode());
        assertTrue(saved.get(0).isGranted());

        verify(auditService).record(eq("admin"), eq("USER_PERMISSION_OVERRIDES_UPDATED"), eq("User"), eq(5L), any());
    }

    @Test
    void updateUserPermissionOverrides_gestorUser_doesNotPersistOverrides() {
        User gestor = new User();
        gestor.setId(1L);
        gestor.setRole(UserRole.GESTOR);

        given(userRepository.findById(1L)).willReturn(Optional.of(gestor));
        given(permissionRepository.findAll()).willReturn(List.of());
        given(permissionRepository.findAllByOrderByCategoryAscCodeAsc()).willReturn(List.of());

        permissionService.updateUserPermissionOverrides(1L, List.of(new UserPermissionOverrideDto("view.dashboard", false)), "admin");

        verify(userPermissionOverrideRepository, never()).deleteByUserId(any());
        verify(userPermissionOverrideRepository, never()).saveAll(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    private UserPermissionStateDto findCode(List<UserPermissionStateDto> states, String code) {
        return states.stream().filter(s -> s.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el permiso: " + code));
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
