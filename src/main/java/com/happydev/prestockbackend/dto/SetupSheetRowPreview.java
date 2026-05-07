package com.happydev.prestockbackend.dto;

import java.util.List;
import java.util.Map;

public record SetupSheetRowPreview(
        int excelRow,
        String status,
        Map<String, String> values,
        List<String> errors,
        List<String> warnings
) {
}
