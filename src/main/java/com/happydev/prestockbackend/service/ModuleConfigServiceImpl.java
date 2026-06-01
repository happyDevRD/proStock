package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ModuleFlagDto;
import com.happydev.prestockbackend.entity.AppModule;
import com.happydev.prestockbackend.entity.CompanyModuleConfig;
import com.happydev.prestockbackend.repository.CompanyModuleConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ModuleConfigServiceImpl implements ModuleConfigService {

    private final CompanyModuleConfigRepository repository;

    public ModuleConfigServiceImpl(CompanyModuleConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModuleFlagDto> getAll() {
        Map<AppModule, Boolean> stored = repository.findAll()
                .stream()
                .collect(Collectors.toMap(CompanyModuleConfig::getModule, CompanyModuleConfig::isEnabled));

        return Arrays.stream(AppModule.values())
                .map(m -> new ModuleFlagDto(m.name(), stored.getOrDefault(m, true)))
                .toList();
    }

    @Override
    @Transactional
    public List<ModuleFlagDto> updateAll(List<ModuleFlagDto> flags) {
        for (ModuleFlagDto dto : flags) {
            AppModule module;
            try {
                module = AppModule.valueOf(dto.module().toUpperCase());
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompanyModuleConfig config = repository.findByModule(module)
                    .orElseGet(() -> new CompanyModuleConfig(null, module, true));
            config.setEnabled(dto.enabled());
            repository.save(config);
        }
        return getAll();
    }
}
