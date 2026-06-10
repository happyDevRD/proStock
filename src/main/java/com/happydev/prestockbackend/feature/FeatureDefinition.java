package com.happydev.prestockbackend.feature;

import java.util.Set;

/**
 * Definición estática de una funcionalidad activable/desactivable por instancia.
 * El catálogo vive en código (ver {@link FeatureCatalog}); solo los overrides
 * (enabled distinto del default) se persisten en {@code company_feature_config}.
 */
public record FeatureDefinition(
        String code,
        String category,
        String name,
        String description,
        boolean defaultEnabled,
        Set<String> dependsOn
) {
    public FeatureDefinition {
        dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
    }
}
