package com.happydev.prestockbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "company_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 11)
    @NotBlank
    @Pattern(regexp = "^\\d{9}(\\d{2})?$", message = "El RNC debe tener 9 u 11 digitos numericos")
    private String rnc;

    @Column(name = "razon_social", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 255)
    @Size(max = 255)
    private String nombreComercial;

    @Column(nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String direccion;

    @Column(name = "municipio_codigo", nullable = false, length = 6)
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "El codigo de municipio debe tener 6 digitos numericos")
    private String municipioCodigo;

    @Column(name = "provincia_codigo", nullable = false, length = 6)
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "El codigo de provincia debe tener 6 digitos numericos")
    private String provinciaCodigo;

    @Column(name = "actividad_economica", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String actividadEconomica;

    @Column(name = "numero_telefono", nullable = false, length = 20)
    @NotBlank
    @Size(max = 20)
    private String numeroTelefono;

    @Column(name = "correo_electronico", nullable = false, length = 255)
    @NotBlank
    @Email
    @Size(max = 255)
    private String correoElectronico;
}
