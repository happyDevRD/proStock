package com.happydev.prestockbackend.dto;

import java.util.List;

public record SetupSheetPreviewResult(
        String sheet,
        boolean canImport,
        int errorRowCount,
        int warningRowCount,
        List<SetupSheetRowPreview> rows,
        List<String> globalWarnings
) {
}
