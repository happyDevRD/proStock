package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.DgiiConsultaResultDto;
import com.happydev.prestockbackend.dto.DgiiNombresSearchDto;
import com.happydev.prestockbackend.service.dgii.DgiiConsultaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dgii")
public class DgiiConsultaController {

    private final DgiiConsultaService dgiiConsultaService;

    public DgiiConsultaController(DgiiConsultaService dgiiConsultaService) {
        this.dgiiConsultaService = dgiiConsultaService;
    }

    /**
     * Consulta RNC o cédula en la DGII vía MegaPlus API (proxy servidor).
     */
    @GetMapping("/consulta")
    public ResponseEntity<DgiiConsultaResultDto> consulta(@RequestParam String rnc) {
        return ResponseEntity.ok(dgiiConsultaService.consultarPorRncCedula(rnc));
    }

    @GetMapping("/consulta/nombres")
    public ResponseEntity<DgiiNombresSearchDto> consultaPorNombre(@RequestParam String buscar) {
        return ResponseEntity.ok(dgiiConsultaService.consultarPorNombre(buscar));
    }
}
