package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "company_module_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyModuleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, unique = true, length = 30)
    private AppModule module;

    @Column(nullable = false)
    private boolean enabled = true;
}
