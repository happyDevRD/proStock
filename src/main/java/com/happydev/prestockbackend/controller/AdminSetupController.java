package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SetupSheetImportResult;
import com.happydev.prestockbackend.dto.SetupSheetPreviewResult;
import com.happydev.prestockbackend.dto.SetupSheetRowsRequest;
import com.happydev.prestockbackend.dto.SetupSheetType;
import com.happydev.prestockbackend.dto.SetupWizardStatusDto;
import com.happydev.prestockbackend.dto.SetupWorkbookResult;
import com.happydev.prestockbackend.service.FileStorageService;
import com.happydev.prestockbackend.service.SetupWizardService;
import com.happydev.prestockbackend.service.SetupWorkbookImportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/setup")
public class AdminSetupController {

    private final SetupWorkbookImportService setupWorkbookImportService;
    private final SetupWizardService setupWizardService;
    private final FileStorageService fileStorageService;

    public AdminSetupController(
            SetupWorkbookImportService setupWorkbookImportService,
            SetupWizardService setupWizardService,
            FileStorageService fileStorageService
    ) {
        this.setupWorkbookImportService = setupWorkbookImportService;
        this.setupWizardService = setupWizardService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/wizard-status")
    public ResponseEntity<SetupWizardStatusDto> wizardStatus() {
        return ResponseEntity.ok(setupWizardService.getStatus());
    }

    @GetMapping(value = "/sheet-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<ByteArrayResource> sheetTemplate(@RequestParam("sheet") String sheet) throws IOException {
        SetupSheetType type = SetupSheetType.fromParam(sheet);
        byte[] bytes = setupWorkbookImportService.buildSingleSheetWorkbook(type);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        String filename = "prostock-hoja-" + type.getFileToken() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(resource);
    }

    @PostMapping(value = "/sheet/preview-rows", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SetupSheetPreviewResult> previewSheetRows(@Valid @RequestBody SetupSheetRowsRequest body) {
        SetupSheetType type = SetupSheetType.fromParam(body.sheet());
        return ResponseEntity.ok(setupWorkbookImportService.previewFromDataRows(type, body.rows()));
    }

    @PostMapping(value = "/sheet/import-rows", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SetupSheetImportResult> importSheetRows(
            @Valid @RequestBody SetupSheetRowsRequest body,
            Principal principal
    ) {
        SetupSheetType type = SetupSheetType.fromParam(body.sheet());
        String actor = principal != null ? principal.getName() : "system";
        return ResponseEntity.ok(setupWorkbookImportService.importSingleSheetFromDataRows(type, body.rows(), actor));
    }

    @PostMapping(value = "/sheet/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SetupSheetPreviewResult> previewSheet(
            @RequestParam("sheet") String sheet,
            @RequestParam("file") MultipartFile file
    ) {
        SetupSheetType type = SetupSheetType.fromParam(sheet);
        return ResponseEntity.ok(setupWorkbookImportService.previewSingleSheet(file, type));
    }

    @PostMapping(value = "/company-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadCompanyLogo(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = fileStorageService.storeCompanyLogo(file);
        return ResponseEntity.ok(Map.of("fileName", fileName));
    }

    @PostMapping(value = "/sheet", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SetupSheetImportResult> importSheet(
            @RequestParam("sheet") String sheet,
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        SetupSheetType type = SetupSheetType.fromParam(sheet);
        String actor = principal != null ? principal.getName() : "system";
        return ResponseEntity.ok(setupWorkbookImportService.importSingleSheet(file, type, actor));
    }

    @GetMapping(value = "/workbook-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<ByteArrayResource> downloadTemplate() throws IOException {
        byte[] bytes = setupWorkbookImportService.buildTemplateWorkbook();
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"prostock-setup-plantilla.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(resource);
    }

    @PostMapping(value = "/workbook", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SetupWorkbookResult> importWorkbook(
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : "system";
        return ResponseEntity.ok(setupWorkbookImportService.importWorkbook(file, actor));
    }
}
