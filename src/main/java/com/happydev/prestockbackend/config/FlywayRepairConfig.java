package com.happydev.prestockbackend.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class FlywayRepairConfig {

    // Activar con APP_FLYWAY_REPAIR_ON_MIGRATE=true en Cloud Run.
    // Corrige checksums desincronizados (e.g. CRLF vs LF) sin tocar datos.
    // Desactivar después del primer deploy exitoso.
    @Bean
    @ConditionalOnProperty(name = "app.flyway.repair-on-migrate", havingValue = "true")
    public FlywayMigrationStrategy repairAndMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
