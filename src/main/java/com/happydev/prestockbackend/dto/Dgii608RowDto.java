package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Dgii608RowDto {
    private String ncf;
    /** Formato AAAAMMDD. */
    private String fechaComprobante;
    /** Código DGII de tipo de anulación (01-10). */
    private String tipoAnulacion;
}
