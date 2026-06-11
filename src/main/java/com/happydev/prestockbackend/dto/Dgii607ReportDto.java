package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Dgii607ReportDto {
    private String rnc;
    /** Formato AAAAMM. */
    private String periodo;
    private int cantidadRegistros;
    private BigDecimal totalMontoFacturado;
    private BigDecimal totalItbis;
    private List<Dgii607RowDto> rows = new ArrayList<>();
}
