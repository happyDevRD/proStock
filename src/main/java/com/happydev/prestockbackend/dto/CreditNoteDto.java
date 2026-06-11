package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreditNoteDto {
    private Long id;
    private Long saleId;
    private String ncf;
    private String tipoComprobante;
    private String ncfModificado;
    private String reason;
    private BigDecimal montoGravadoTotal;
    private BigDecimal montoExento;
    private BigDecimal totalItbis;
    private BigDecimal montoTotal;
    private LocalDateTime createdAt;
    private String createdBy;
    private List<CreditNoteItemDto> items = new ArrayList<>();
}
