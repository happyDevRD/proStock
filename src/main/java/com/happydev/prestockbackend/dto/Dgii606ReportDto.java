package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Dgii606ReportDto {
    private String rnc;
    /** Formato AAAAMM. */
    private String periodo;
    private int cantidadRegistros;
    private BigDecimal totalMontoFacturado;
    private BigDecimal totalItbis;
    private List<Dgii606RowDto> rows = new ArrayList<>();
}
