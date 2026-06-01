package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.ModuleFlagDto;
import com.happydev.prestockbackend.service.ModuleConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/modules/flags")
public class ModuleConfigController {

    private final ModuleConfigService moduleConfigService;

    public ModuleConfigController(ModuleConfigService moduleConfigService) {
        this.moduleConfigService = moduleConfigService;
    }

    @GetMapping
    public ResponseEntity<List<ModuleFlagDto>> getFlags() {
        return ResponseEntity.ok(moduleConfigService.getAll());
    }

    @PutMapping
    public ResponseEntity<List<ModuleFlagDto>> updateFlags(@RequestBody List<ModuleFlagDto> flags) {
        return ResponseEntity.ok(moduleConfigService.updateAll(flags));
    }
}
