package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Dgii608ReportDto {
    private String rnc;
    /** Formato AAAAMM. */
    private String periodo;
    private int cantidadRegistros;
    private List<Dgii608RowDto> rows = new ArrayList<>();
}
