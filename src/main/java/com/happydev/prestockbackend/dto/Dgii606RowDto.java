package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Dgii606RowDto {
    private String rncCedula;
    /** 1 = RNC, 2 = Cédula, 3 = Pasaporte. */
    private String tipoIdentificacion;
    /** Código DGII "tipo de bienes y servicios comprados" (01-11). */
    private String tipoBienesServicios;
    private String ncf;
    /** Formato AAAAMMDD. */
    private String fechaComprobante;
    /** Formato AAAAMMDD; vacío si no se ha pagado. */
    private String fechaPago;
    private BigDecimal montoFacturadoServicios;
    private BigDecimal montoFacturadoBienes;
    private BigDecimal totalMontoFacturado;
    private BigDecimal itbisFacturado;
    /** Código DGII forma de pago (01-07). */
    private String formaPago;
}
