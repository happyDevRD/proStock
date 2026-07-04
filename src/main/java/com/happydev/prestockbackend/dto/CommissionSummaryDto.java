package com.happydev.prestockbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommissionSummaryDto {
    private Long employeeId;
    private String employeeName;
    private BigDecimal comisionPorcentaje;
    private BigDecimal totalVentas;
    private BigDecimal montoComision;
    private long cantidadVentas;
    private String periodoDesde;
    private String periodoHasta;
}
