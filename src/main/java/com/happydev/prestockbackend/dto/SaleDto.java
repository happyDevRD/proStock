package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.TipoIngresos;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class SaleDto {
    private Long id;

    @NotNull(message = "Sale date cannot be null")
    private LocalDateTime saleDate;

    private Long customerId; // Usamos el ID del cliente

    @NotEmpty(message = "Sale items cannot be empty")
    private List<SaleItemDto> items;

    @NotNull
    private SaleStatus status;

    @Pattern(regexp = "^\\d{2}$", message = "El tipo de comprobante debe tener 2 digitos")
    private String tipoComprobante;

    private String ncf;

    private BigDecimal montoGravadoTotal;
    private BigDecimal montoExento;
    private BigDecimal totalItbis;
    private BigDecimal montoTotal;
    private TipoIngresos tipoIngresos;
    private LocalDateTime fechaFirma;
    private String codigoSeguridad;
    private String qrPayloadUrl;
    private String qrCodeBase64;
}