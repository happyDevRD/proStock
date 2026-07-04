package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Column(length = 20, unique = true)
    private String cedula;

    @Column(length = 20)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(length = 100)
    private String cargo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmployeeDepartment departamento = EmployeeDepartment.OTRO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_empleo", nullable = false, length = 30)
    private EmployeeType tipoEmpleo = EmployeeType.PERMANENTE;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_salida")
    private LocalDate fechaSalida;

    @Column(name = "salario_base", nullable = false, precision = 18, scale = 2)
    private BigDecimal salarioBase = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_salario", nullable = false, length = 20)
    private EmployeeSalaryType tipoSalario = EmployeeSalaryType.MENSUAL;

    @Column(name = "comision_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal comisionPorcentaje = BigDecimal.ZERO;

    @Column(name = "num_tss", length = 30)
    private String numTss;

    @Column(length = 100)
    private String banco;

    @Column(name = "cuenta_bancaria", length = 50)
    private String cuentaBancaria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVO;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
