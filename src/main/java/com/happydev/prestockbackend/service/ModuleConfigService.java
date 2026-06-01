package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ModuleFlagDto;

import java.util.List;

public interface ModuleConfigService {
    List<ModuleFlagDto> getAll();
    List<ModuleFlagDto> updateAll(List<ModuleFlagDto> flags);
}
