package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeDto {
    private Long id;
    private String nombreCompleto;
    private String cedula;
    private String telefono;
    private String email;
    private String cargo;
    private EmployeeDepartment departamento;
    private EmployeeType tipoEmpleo;
    private LocalDate fechaIngreso;
    private LocalDate fechaSalida;
    private BigDecimal salarioBase;
    private EmployeeSalaryType tipoSalario;
    private BigDecimal comisionPorcentaje;
    private String numTss;
    private String banco;
    private String cuentaBancaria;
    private EmployeeStatus status;
    private String fotoUrl;
    private String notas;
    private String createdBy;
    private LocalDateTime createdAt;
}
