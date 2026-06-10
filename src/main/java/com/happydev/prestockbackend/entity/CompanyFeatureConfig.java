package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Override por instancia del estado enabled/disabled de una {@code FeatureDefinition}.
 * Solo existe una fila cuando el estado difiere del {@code defaultEnabled} del catálogo.
 */
@Entity
@Table(name = "company_feature_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyFeatureConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_code", nullable = false, unique = true, length = 80)
    private String featureCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
