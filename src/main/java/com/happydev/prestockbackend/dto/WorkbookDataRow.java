package com.happydev.prestockbackend.dto;

import java.util.Map;

public record WorkbookDataRow(int excelRow, Map<String, String> cells) {
    public WorkbookDataRow {
        cells = cells != null ? cells : Map.of();
    }
}
