package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.FeatureFlagDto;
import com.happydev.prestockbackend.entity.CompanyFeatureConfig;
import com.happydev.prestockbackend.feature.FeatureCatalog;
import com.happydev.prestockbackend.repository.CompanyFeatureConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class FeatureConfigServiceImplTest {

    @Mock
    private CompanyFeatureConfigRepository repository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private FeatureConfigServiceImpl featureConfigService;

    @Test
    void getAll_returnsCatalogDefaultsWhenNoOverridesExist() {
        given(repository.findAll()).willReturn(List.of());

        List<FeatureFlagDto> result = featureConfigService.getAll();

        assertEquals(FeatureCatalog.ALL.size(), result.size());
        assertTrue(findByCode(result, "module.pos").enabled());
        assertFalse(findByCode(result, "module.service_orders").enabled());
    }

    @Test
    void getAll_appliesStoredOverrides() {
        CompanyFeatureConfig override = new CompanyFeatureConfig();
        override.setFeatureCode("module.pos");
        override.setEnabled(false);
        given(repository.findAll()).willReturn(List.of(override));

        List<FeatureFlagDto> result = featureConfigService.getAll();

        assertFalse(findByCode(result, "module.pos").enabled());
        assertTrue(findByCode(result, "module.invoice").enabled());
    }

    @Test
    void getAll_marksDependenciesUnsatisfiedWhenParentDisabled() {
        CompanyFeatureConfig override = new CompanyFeatureConfig();
        override.setFeatureCode("module.invoice");
        override.setEnabled(false);
        given(repository.findAll()).willReturn(List.of(override));

        List<FeatureFlagDto> result = featureConfigService.getAll();

        assertFalse(findByCode(result, "invoice.ncf").dependenciesSatisfied());
        assertFalse(findByCode(result, "invoice.thermal_receipt").dependenciesSatisfied());
    }

    @Test
    void updateAll_rejectsEnablingFeatureWhenDependencyDisabled() {
        CompanyFeatureConfig override = new CompanyFeatureConfig();
        override.setFeatureCode("module.invoice");
        override.setEnabled(false);
        given(repository.findAll()).willReturn(List.of(override));

        List<FeatureFlagDto> updates = List.of(updateDto("invoice.ncf", true));

        assertThrows(IllegalArgumentException.class, () -> featureConfigService.updateAll(updates, "admin"));
    }

    @Test
    void updateAll_rejectsDisablingFeatureWhenDependentStillEnabled() {
        given(repository.findAll()).willReturn(List.of());

        List<FeatureFlagDto> updates = List.of(updateDto("module.invoice", false));

        assertThrows(IllegalArgumentException.class, () -> featureConfigService.updateAll(updates, "admin"));
    }

    @Test
    void updateAll_persistsValidChangesAndAudits() {
        CompanyFeatureConfig persisted = new CompanyFeatureConfig();
        persisted.setFeatureCode("module.service_orders");
        persisted.setEnabled(true);

        given(repository.findAll()).willReturn(List.of(), List.of(persisted));
        given(repository.findByFeatureCode("module.service_orders")).willReturn(Optional.empty());
        given(repository.save(any(CompanyFeatureConfig.class))).willAnswer(invocation -> invocation.getArgument(0));

        List<FeatureFlagDto> updates = List.of(updateDto("module.service_orders", true));

        List<FeatureFlagDto> result = featureConfigService.updateAll(updates, "admin");

        verify(repository).save(argThat(c -> "module.service_orders".equals(c.getFeatureCode())
                && c.isEnabled()
                && "admin".equals(c.getUpdatedBy())
                && c.getUpdatedAt() != null));
        verify(auditService).record(eq("admin"), eq("FEATURE_FLAGS_UPDATED"), eq("CompanyFeatureConfig"), isNull(), any());
        assertTrue(findByCode(result, "module.service_orders").enabled());
    }

    @Test
    void updateAll_ignoresUnknownFeatureCodes() {
        given(repository.findAll()).willReturn(List.of());

        List<FeatureFlagDto> result = featureConfigService.updateAll(List.of(updateDto("nonexistent.code", true)), "admin");

        verify(repository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
        assertEquals(FeatureCatalog.ALL.size(), result.size());
    }

    private FeatureFlagDto findByCode(List<FeatureFlagDto> list, String code) {
        return list.stream().filter(f -> f.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el código: " + code));
    }

    private FeatureFlagDto updateDto(String code, boolean enabled) {
        return new FeatureFlagDto(code, "X", "X", "X", enabled, List.of(), true);
    }
}
