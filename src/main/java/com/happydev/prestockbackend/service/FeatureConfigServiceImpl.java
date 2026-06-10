package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.FeatureFlagDto;
import com.happydev.prestockbackend.entity.CompanyFeatureConfig;
import com.happydev.prestockbackend.feature.FeatureCatalog;
import com.happydev.prestockbackend.feature.FeatureDefinition;
import com.happydev.prestockbackend.repository.CompanyFeatureConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeatureConfigServiceImpl implements FeatureConfigService {

    private final CompanyFeatureConfigRepository repository;
    private final AuditService auditService;

    public FeatureConfigServiceImpl(CompanyFeatureConfigRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeatureFlagDto> getAll() {
        return toDtos(resolveEnabled(loadOverrides()));
    }

    @Override
    @Transactional
    public List<FeatureFlagDto> updateAll(List<FeatureFlagDto> updates, String actorUsername) {
        Map<String, Boolean> resolved = resolveEnabled(loadOverrides());

        Map<String, Boolean> requested = new LinkedHashMap<>();
        for (FeatureFlagDto update : updates) {
            if (update.code() == null || !FeatureCatalog.exists(update.code())) {
                continue;
            }
            requested.put(update.code(), update.enabled());
        }

        // Estado resultante si se aplicaran todos los cambios solicitados, usado para validar dependencias.
        Map<String, Boolean> next = new LinkedHashMap<>(resolved);
        next.putAll(requested);

        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            String code = entry.getKey();
            boolean enabled = entry.getValue();
            FeatureDefinition definition = FeatureCatalog.byCode(code).orElseThrow();

            if (enabled) {
                for (String dependency : definition.dependsOn()) {
                    if (!Boolean.TRUE.equals(next.get(dependency))) {
                        throw new IllegalArgumentException(
                                "No se puede activar '" + code + "': requiere que '" + dependency
                                        + "' esté activo primero.");
                    }
                }
            } else {
                for (FeatureDefinition other : FeatureCatalog.ALL) {
                    if (other.dependsOn().contains(code) && Boolean.TRUE.equals(next.get(other.code()))) {
                        throw new IllegalArgumentException(
                                "No se puede desactivar '" + code + "': '" + other.code()
                                        + "' depende de esta funcionalidad. Desactívala primero.");
                    }
                }
            }
        }

        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            CompanyFeatureConfig config = repository.findByFeatureCode(entry.getKey())
                    .orElseGet(() -> {
                        CompanyFeatureConfig created = new CompanyFeatureConfig();
                        created.setFeatureCode(entry.getKey());
                        return created;
                    });
            config.setEnabled(entry.getValue());
            config.setUpdatedAt(LocalDateTime.now());
            config.setUpdatedBy(actorUsername);
            repository.save(config);
        }

        if (!requested.isEmpty()) {
            auditService.record(actorUsername, "FEATURE_FLAGS_UPDATED", "CompanyFeatureConfig", null, requested);
        }

        return getAll();
    }

    private Map<String, Boolean> loadOverrides() {
        Map<String, Boolean> overrides = new HashMap<>();
        for (CompanyFeatureConfig config : repository.findAll()) {
            overrides.put(config.getFeatureCode(), config.isEnabled());
        }
        return overrides;
    }

    private Map<String, Boolean> resolveEnabled(Map<String, Boolean> overrides) {
        Map<String, Boolean> resolved = new LinkedHashMap<>();
        for (FeatureDefinition feature : FeatureCatalog.ALL) {
            resolved.put(feature.code(), overrides.getOrDefault(feature.code(), feature.defaultEnabled()));
        }
        return resolved;
    }

    private List<FeatureFlagDto> toDtos(Map<String, Boolean> resolved) {
        return FeatureCatalog.ALL.stream()
                .map(feature -> new FeatureFlagDto(
                        feature.code(),
                        feature.category(),
                        feature.name(),
                        feature.description(),
                        resolved.get(feature.code()),
                        feature.dependsOn().stream().sorted().toList(),
                        feature.dependsOn().stream().allMatch(dep -> resolved.getOrDefault(dep, true))
                ))
                .toList();
    }
}
