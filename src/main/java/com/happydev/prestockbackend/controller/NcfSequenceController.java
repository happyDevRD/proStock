package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.NcfSequence;
import com.happydev.prestockbackend.service.SequenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/ncf-sequences")
public class NcfSequenceController {

    private final SequenceService sequenceService;

    public NcfSequenceController(SequenceService sequenceService) {
        this.sequenceService = sequenceService;
    }

    @GetMapping("/{tipoComprobante}")
    public ResponseEntity<NcfSequence> getSequence(@PathVariable String tipoComprobante) {
        return sequenceService.findByTipoComprobante(tipoComprobante)
                .map(sequence -> new ResponseEntity<>(sequence, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping
    public ResponseEntity<NcfSequence> saveOrUpdate(@Valid @RequestBody NcfSequence ncfSequence) {
        NcfSequence savedSequence = sequenceService.saveOrUpdate(
                Objects.requireNonNull(ncfSequence, "ncfSequence no puede ser null")
        );
        return new ResponseEntity<>(savedSequence, HttpStatus.OK);
    }

    @PostMapping("/{tipoComprobante}/next")
    public ResponseEntity<Map<String, String>> getNextNcf(@PathVariable String tipoComprobante) {
        String nextNcf = sequenceService.getNextSequence(tipoComprobante);
        return new ResponseEntity<>(Map.of("ncf", nextNcf), HttpStatus.OK);
    }
}
