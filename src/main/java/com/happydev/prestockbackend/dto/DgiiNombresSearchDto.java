package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DgiiNombresSearchDto {

    private List<DgiiNombreHitDto> resultados = new ArrayList<>();

    private Integer paginaActual;

    private Integer paginasTotales;

    private Integer totalEnEstaPagina;
}
