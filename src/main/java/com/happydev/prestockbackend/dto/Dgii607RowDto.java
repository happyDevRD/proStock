package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Dgii607RowDto {
    private String rncCedula;
    /** 1 = RNC, 2 = Cédula, 3 = Pasaporte. Vacío para consumo sin documento. */
    private String tipoIdentificacion;
    private String ncf;
    private String ncfModificado;
    private String tipoIngreso;
    /** Formato AAAAMMDD. */
    private String fechaComprobante;
    /** Monto facturado sin ITBIS (gravado + exento). */
    private BigDecimal montoFacturado;
    private BigDecimal itbisFacturado;
    private BigDecimal efectivo;
    private BigDecimal chequeTransferencia;
    private BigDecimal tarjeta;
    private BigDecimal ventaCredito;
    private BigDecimal otrasFormas;
}
