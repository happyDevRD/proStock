package com.happydev.prestockbackend.dto;

import java.util.List;

public record SetupSheetImportResult(
        String sheet,
        int affected,
        int skipped,
        List<String> warnings
) {
}
