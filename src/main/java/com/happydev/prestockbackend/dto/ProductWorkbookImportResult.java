package com.happydev.prestockbackend.dto;

import java.util.List;

public record ProductWorkbookImportResult(int imported, int skippedDuplicates, List<String> warnings) {
}
