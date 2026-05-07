package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SetupSheetRowsRequest(
        @NotBlank String sheet,
        @NotNull List<WorkbookDataRow> rows
) {
}
