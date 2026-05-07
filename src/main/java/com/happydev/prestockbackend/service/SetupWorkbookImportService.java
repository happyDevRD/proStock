package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ProductWorkbookImportResult;
import com.happydev.prestockbackend.dto.SetupSheetImportResult;
import com.happydev.prestockbackend.dto.SetupSheetPreviewResult;
import com.happydev.prestockbackend.dto.SetupSheetRowPreview;
import com.happydev.prestockbackend.dto.SetupSheetType;
import com.happydev.prestockbackend.dto.SetupWorkbookResult;
import com.happydev.prestockbackend.dto.WorkbookDataRow;
import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.util.ByteArrayMultipartFile;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import com.happydev.prestockbackend.util.WorkbookHeaderUtils;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class SetupWorkbookImportService {

    private final CompanyConfigService companyConfigService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductService productService;
    private final AuditService auditService;

    private final FileStorageService fileStorageService;

    private final Validator validator;

    public SetupWorkbookImportService(
            CompanyConfigService companyConfigService,
            UserService userService,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            ProductService productService,
            AuditService auditService,
            FileStorageService fileStorageService,
            Validator validator
    ) {
        this.companyConfigService = companyConfigService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.productService = productService;
        this.auditService = auditService;
        this.fileStorageService = fileStorageService;
        this.validator = validator;
    }

    public byte[] buildTemplateWorkbook() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            appendInstruccionesSheet(wb);
            appendEmpresaSheet(wb);
            appendUsuariosSheet(wb);
            appendCategoriasSheet(wb);
            appendSuplidoresSheet(wb);
            appendProductosSheet(wb);
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] buildSingleSheetWorkbook(SetupSheetType type) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            switch (type) {
                case EMPRESA -> appendEmpresaSheet(wb);
                case USUARIOS -> appendUsuariosSheet(wb);
                case CATEGORIAS -> appendCategoriasSheet(wb);
                case SUPLIDORES -> appendSuplidoresSheet(wb);
                case PRODUCTOS -> appendProductosSheet(wb);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public SetupSheetImportResult importSingleSheet(
            @NonNull MultipartFile file,
            @NonNull SetupSheetType type,
            @NonNull String actorUsername
    ) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actorUsername, "actorUsername");
        assertWorkbookFile(file);
        List<String> warnings = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            if (wb.getNumberOfSheets() < 1) {
                throw new IllegalArgumentException("El libro no contiene hojas.");
            }
            Sheet sheet = wb.getSheetAt(0);
            int affected = 0;
            int skipped = 0;
            switch (type) {
                case EMPRESA -> {
                    boolean ok = processEmpresa(sheet, warnings);
                    affected = ok ? 1 : 0;
                }
                case USUARIOS -> {
                    int[] u = processUsuarios(sheet, actorUsername, warnings);
                    affected = u[0];
                    skipped = u[1];
                }
                case CATEGORIAS -> affected = processCategorias(sheet, warnings);
                case SUPLIDORES -> affected = processSuplidores(sheet, warnings);
                case PRODUCTOS -> {
                    ProductWorkbookImportResult pr = processProductos(sheet, warnings);
                    affected = pr.imported();
                    skipped = pr.skippedDuplicates();
                    warnings.addAll(pr.warnings());
                }
            }
            auditService.record(
                    SecurityAuditUtils.currentUsernameOrNull(),
                    "SETUP_SHEET_IMPORTED",
                    "SetupWorkbook",
                    null,
                    Map.of(
                            "sheet", type.name(),
                            "affected", Integer.toString(affected),
                            "skipped", Integer.toString(skipped)
                    )
            );
            return new SetupSheetImportResult(type.getFileToken(), affected, skipped, warnings);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo Excel.", e);
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public SetupSheetPreviewResult previewSingleSheet(@NonNull MultipartFile file, @NonNull SetupSheetType type) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(type, "type");
        assertWorkbookFile(file);
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            if (wb.getNumberOfSheets() < 1) {
                throw new IllegalArgumentException("El libro no contiene hojas.");
            }
            return toPreviewResult(wb.getSheetAt(0), type);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo Excel.", e);
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public SetupSheetPreviewResult previewFromDataRows(@NonNull SetupSheetType type, @NonNull List<WorkbookDataRow> rows) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No hay filas para validar.");
        }
        try {
            byte[] xlsx = buildXlsxFromDataRows(type, rows);
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
                return toPreviewResult(wb.getSheetAt(0), type);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo construir el libro temporal.", e);
        }
    }

    public SetupSheetImportResult importSingleSheetFromDataRows(
            @NonNull SetupSheetType type,
            @NonNull List<WorkbookDataRow> rows,
            @NonNull String actorUsername
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(actorUsername, "actorUsername");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No hay filas para importar.");
        }
        try {
            byte[] xlsx = buildXlsxFromDataRows(type, rows);
            MultipartFile file = new ByteArrayMultipartFile(xlsx, "import.xlsx");
            return importSingleSheet(file, type, actorUsername);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo construir el libro temporal.", e);
        }
    }

    private SetupSheetPreviewResult toPreviewResult(Sheet sheet, SetupSheetType type) {
        List<String> globalWarnings = new ArrayList<>();
        List<SetupSheetRowPreview> rows = switch (type) {
            case EMPRESA -> previewEmpresa(sheet, globalWarnings);
            case USUARIOS -> previewUsuarios(sheet, globalWarnings);
            case CATEGORIAS -> previewCategorias(sheet, globalWarnings);
            case SUPLIDORES -> previewSuplidores(sheet, globalWarnings);
            case PRODUCTOS -> previewProductos(sheet, globalWarnings);
        };
        int errorRowCount = (int) rows.stream().filter(r -> !r.errors().isEmpty()).count();
        int warningRowCount = (int) rows.stream().filter(r -> r.errors().isEmpty() && !r.warnings().isEmpty()).count();
        boolean canImport = rows.stream().allMatch(r -> r.errors().isEmpty());
        return new SetupSheetPreviewResult(
                type.getFileToken(),
                canImport,
                errorRowCount,
                warningRowCount,
                rows,
                globalWarnings
        );
    }

    private byte[] buildXlsxFromDataRows(SetupSheetType type, List<WorkbookDataRow> dataRows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            switch (type) {
                case EMPRESA -> appendEmpresaSheet(wb);
                case USUARIOS -> appendUsuariosSheet(wb);
                case CATEGORIAS -> appendCategoriasSheet(wb);
                case SUPLIDORES -> appendSuplidoresSheet(wb);
                case PRODUCTOS -> appendProductosSheet(wb);
            }
            Sheet sheet = wb.getSheetAt(0);
            writeDataForTemplate(sheet, headerLabelsForType(type), dataRows);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private List<String> headerLabelsForType(SetupSheetType type) {
        return switch (type) {
            case EMPRESA -> List.of(
                    "rnc", "razon_social", "nombre_comercial", "direccion", "municipio_codigo", "provincia_codigo",
                    "actividad_economica", "numero_telefono", "correo_electronico",
                    "logo_file_name", "invoice_header_note", "invoice_footer_text"
            );
            case USUARIOS -> List.of("username", "password", "email", "first_name", "last_name", "role");
            case CATEGORIAS -> List.of("name");
            case SUPLIDORES -> List.of("name", "contact_name", "contact_email", "phone", "address");
            case PRODUCTOS -> List.of(
                    "sku", "name", "sellingPrice", "stock", "minStock", "category", "supplier",
                    "costPrice", "barcode", "unidadMedida", "description"
            );
        };
    }

    private void writeDataForTemplate(Sheet sheet, List<String> headerLabels, List<WorkbookDataRow> dataRows) {
        int r = 1;
        for (WorkbookDataRow dr : dataRows) {
            Map<String, String> norm = normalizeCells(dr.cells());
            Row row = sheet.createRow(r++);
            for (int c = 0; c < headerLabels.size(); c++) {
                String key = WorkbookHeaderUtils.normalizeHeaderKey(headerLabels.get(c));
                String val = norm.getOrDefault(key, "");
                row.createCell(c).setCellValue(val);
            }
        }
    }

    private static Map<String, String> normalizeCells(Map<String, String> cells) {
        if (cells == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : cells.entrySet()) {
            out.put(WorkbookHeaderUtils.normalizeHeaderKey(e.getKey()), e.getValue() != null ? e.getValue() : "");
        }
        return out;
    }

    private void assertWorkbookFile(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Solo se admiten archivos .xlsx (Excel).");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }
    }

    private record IndexedRow(int excelRow, Map<String, String> cells) {
    }

    private List<SetupSheetRowPreview> previewEmpresa(Sheet sheet, List<String> globalWarnings) {
        List<SetupSheetRowPreview> out = new ArrayList<>();
        if (sheet == null) {
            globalWarnings.add("Hoja Empresa no encontrada.");
            return out;
        }
        List<IndexedRow> indexed = readIndexedDataRows(sheet);
        if (indexed.isEmpty()) {
            globalWarnings.add("Hoja Empresa sin filas de datos.");
            return out;
        }
        if (indexed.size() > 1) {
            globalWarnings.add("Solo se usará la primera fila de datos; hay filas extras que se ignorarán al importar.");
        }
        IndexedRow ir = indexed.get(0);
        Map<String, String> row = ir.cells();
        CompanyConfig target = new CompanyConfig();
        mergeEmpresaRow(target, row);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        addCompanyConfigBeanViolations(target, errors);
        String logoRaw = cell(row, "logofilename", "logo_file_name", "logo");
        if (!isBlank(logoRaw)) {
            String safe = sanitizeLogoFileName(logoRaw);
            if (safe.isEmpty()) {
                errors.add("Nombre de logo inválido (no use rutas con ..).");
            } else if (!Files.exists(fileStorageService.load(safe))) {
                errors.add(
                        "No se encontró \"" + safe + "\" en el servidor. Use «Subir logo» en el asistente o vincule el nombre exacto del archivo en /uploads."
                );
            }
        }
        String rnc = target.getRnc();
        if (!isBlank(rnc)) {
            String d = rnc.replaceAll("\\D", "");
            if (d.length() != 9 && d.length() != 11) {
                warnings.add("El RNC suele tener 9 u 11 dígitos (solo comprobación de forma).");
            }
        }
        String status = errors.isEmpty() ? (warnings.isEmpty() ? "OK" : "WARNING") : "ERROR";
        out.add(new SetupSheetRowPreview(ir.excelRow(), status, new LinkedHashMap<>(row), errors, warnings));
        return out;
    }

    private List<SetupSheetRowPreview> previewUsuarios(Sheet sheet, List<String> globalWarnings) {
        List<SetupSheetRowPreview> out = new ArrayList<>();
        if (sheet == null) {
            globalWarnings.add("Hoja Usuarios no encontrada.");
            return out;
        }
        for (IndexedRow ir : readIndexedDataRows(sheet)) {
            Map<String, String> row = ir.cells();
            if (!rowHasAnyValue(row)) {
                continue;
            }
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            String username = cell(row, "username", "usuario");
            String password = cell(row, "password", "contrasena", "contraseña");
            String email = cell(row, "email", "correo");
            if (isBlank(username) && isBlank(email)) {
                continue;
            }
            if (isBlank(username) || isBlank(password) || isBlank(email)) {
                errors.add("username, password y email son obligatorios.");
            }
            if (!isBlank(password) && password.length() < 6) {
                errors.add("La contraseña debe tener al menos 6 caracteres.");
            }
            if (!isBlank(username) && userRepository.existsByUsername(username.trim())) {
                warnings.add("El usuario ya existe; la fila se omitirá al importar.");
            }
            if (!isBlank(email) && userRepository.existsByEmail(email.trim())) {
                errors.add("El correo ya está registrado.");
            }
            String roleRaw = cell(row, "role", "rol");
            if (!isBlank(roleRaw)) {
                try {
                    UserRole.valueOf(roleRaw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warnings.add("Rol desconocido \"" + roleRaw + "\"; se usará USER al importar.");
                }
            }
            String status = errors.isEmpty() ? (warnings.isEmpty() ? "OK" : "WARNING") : "ERROR";
            out.add(new SetupSheetRowPreview(ir.excelRow(), status, new LinkedHashMap<>(row), errors, warnings));
        }
        return out;
    }

    private List<SetupSheetRowPreview> previewCategorias(Sheet sheet, List<String> globalWarnings) {
        List<SetupSheetRowPreview> out = new ArrayList<>();
        if (sheet == null) {
            globalWarnings.add("Hoja Categorias no encontrada.");
            return out;
        }
        for (IndexedRow ir : readIndexedDataRows(sheet)) {
            Map<String, String> row = ir.cells();
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            String name = cell(row, "name", "nombre", "categoria");
            if (isBlank(name)) {
                if (rowHasAnyValue(row)) {
                    errors.add("Falta el nombre de la categoría.");
                    out.add(new SetupSheetRowPreview(ir.excelRow(), "ERROR", new LinkedHashMap<>(row), errors, warnings));
                }
                continue;
            }
            if (categoryRepository.findByNameIgnoreCase(name.trim()).isPresent()) {
                warnings.add("La categoría \"" + name.trim() + "\" ya existe; la fila se omitirá al importar.");
            }
            String status = errors.isEmpty() ? (warnings.isEmpty() ? "OK" : "WARNING") : "ERROR";
            out.add(new SetupSheetRowPreview(ir.excelRow(), status, new LinkedHashMap<>(row), errors, warnings));
        }
        return out;
    }

    private List<SetupSheetRowPreview> previewSuplidores(Sheet sheet, List<String> globalWarnings) {
        List<SetupSheetRowPreview> out = new ArrayList<>();
        if (sheet == null) {
            globalWarnings.add("Hoja Suplidores no encontrada.");
            return out;
        }
        for (IndexedRow ir : readIndexedDataRows(sheet)) {
            Map<String, String> row = ir.cells();
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            String name = cell(row, "name", "nombre", "suplidor", "proveedor");
            if (isBlank(name)) {
                if (rowHasAnyValue(row)) {
                    errors.add("Falta el nombre del suplidor.");
                    out.add(new SetupSheetRowPreview(ir.excelRow(), "ERROR", new LinkedHashMap<>(row), errors, warnings));
                }
                continue;
            }
            if (supplierRepository.findByNameIgnoreCase(name.trim()).isPresent()) {
                warnings.add("El suplidor \"" + name.trim() + "\" ya existe; la fila se omitirá al importar.");
            }
            String status = errors.isEmpty() ? (warnings.isEmpty() ? "OK" : "WARNING") : "ERROR";
            out.add(new SetupSheetRowPreview(ir.excelRow(), status, new LinkedHashMap<>(row), errors, warnings));
        }
        return out;
    }

    private List<SetupSheetRowPreview> previewProductos(Sheet sheet, List<String> globalWarnings) {
        if (sheet == null) {
            globalWarnings.add("Hoja Productos no encontrada.");
            return List.of();
        }
        List<WorkbookDataRow> data = readIndexedDataRows(sheet).stream()
                .map(ir -> new WorkbookDataRow(ir.excelRow(), ir.cells()))
                .toList();
        return productService.previewProductsFromWorkbook(data);
    }

    private static String sanitizeLogoFileName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replace("\\", "/");
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        if (s.contains("..")) {
            return "";
        }
        return s;
    }

    private static void appendInstruccionesSheet(Workbook wb) {
        Sheet sh = wb.createSheet("Instrucciones");
        sh.setColumnWidth(0, 115 * 256);
        String text = """
                PROSTOCK — PLANTILLA DE CARGA INICIAL

                Como usar este archivo
                — Complete los datos en las hojas Empresa, Usuarios, Categorias, Suplidores y Productos.
                — Deje la fila 1 de cada hoja tal cual (son los encabezados que lee la aplicacion).
                — Puede agregar mas filas debajo de los encabezados; las filas completamente vacias se ignoran.
                — Guarde como .xlsx y subalo desde Ajustes (admin) o desde el asistente "Puesta en marcha" (barra lateral).
                — En el asistente puede descargar o subir una sola hoja (empresa, usuarios, categorias, suplidores o productos) sin usar el libro completo.
                — Logo: en el asistente use «Subir logo de empresa» (API /api/admin/setup/company-logo); luego copie el nombre devuelto en la columna logo_file_name, o suba manualmente a la carpeta uploads del servidor.

                Hoja EMPRESA (una sola fila de datos bajo el encabezado)
                rnc — RNC 9 u 11 digitos.
                razon_social — Nombre legal.
                nombre_comercial — Opcional; nombre de marca o negocio.
                direccion — Direccion fiscal.
                municipio_codigo y provincia_codigo — 6 digitos cada uno (codigos DGII).
                actividad_economica — Descripcion de la actividad.
                numero_telefono y correo_electronico — Contacto.
                logo_file_name — Opcional; nombre de archivo ya servido en /uploads/.
                invoice_header_note — Texto libre que aparece en la factura debajo del nombre (cabecera).
                invoice_footer_text — Texto libre al pie de la factura (terminos, cuenta bancaria, etc.).

                Hoja USUARIOS (una fila por usuario)
                username — Unico.
                password — Minimo 6 caracteres (se guarda cifrada).
                email — Unico, formato correo valido.
                first_name y last_name — Opcional.
                role — ADMIN, MANAGER, CASHIER o USER. Si lo deja vacio se usa USER.

                Hoja CATEGORIAS
                name — Nombre de la categoria (una por fila). Si ya existe, no se duplica.

                Hoja SUPLIDORES
                name — Nombre del suplidor o proveedor (obligatorio en la fila).
                contact_name, contact_email, phone, address — Opcional.

                Hoja PRODUCTOS (mismas reglas que el CSV de productos)
                sku — Codigo interno unico (si ya existe en el sistema, esa fila se omite al importar).
                name — Nombre del articulo.
                sellingPrice — Precio de venta (use punto decimal, ej. 150.00).
                stock y minStock — Cantidades enteras.
                category — Nombre de categoria; si no existe se crea.
                supplier — Nombre de suplidor; si no existe se crea.
                costPrice — Opcional; si vacio se estima al 70 porciento del precio de venta.
                barcode — Opcional; debe ser unico si lo informa.
                unidadMedida — Codigo numerico DGII unidad de medida (ej. 58). Si vacio se usa 58.
                description — Opcional.

                Notas
                — Puede borrar esta hoja Instrucciones antes de subir; no es obligatorio. La importacion solo usa las hojas Empresa, Usuarios, Categorias, Suplidores y Productos.
                — Nombres de hoja alternativos aceptados al subir: company, users, categories, suppliers, products (en ingles).
                """;
        String[] lines = text.split("\n");
        int rowIdx = 0;
        for (String line : lines) {
            Row row = sh.createRow(rowIdx++);
            row.createCell(0).setCellValue(line);
        }
    }

    private static void appendEmpresaSheet(Workbook wb) {
        Sheet sh = wb.createSheet("Empresa");
        Row h = sh.createRow(0);
        String[] cols = {
                "rnc", "razon_social", "nombre_comercial", "direccion", "municipio_codigo", "provincia_codigo",
                "actividad_economica", "numero_telefono", "correo_electronico",
                "logo_file_name", "invoice_header_note", "invoice_footer_text"
        };
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
        }
    }

    private static void appendUsuariosSheet(Workbook wb) {
        Sheet sh = wb.createSheet("Usuarios");
        Row h = sh.createRow(0);
        String[] cols = {"username", "password", "email", "first_name", "last_name", "role"};
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
        }
    }

    private static void appendCategoriasSheet(Workbook wb) {
        Sheet sh = wb.createSheet("Categorias");
        sh.createRow(0).createCell(0).setCellValue("name");
    }

    private static void appendSuplidoresSheet(Workbook wb) {
        Sheet sh = wb.createSheet("Suplidores");
        Row h = sh.createRow(0);
        String[] cols = {"name", "contact_name", "contact_email", "phone", "address"};
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
        }
    }

    private static void appendProductosSheet(Workbook wb) {
        Sheet sh = wb.createSheet("Productos");
        Row h = sh.createRow(0);
        String[] cols = {"sku", "name", "sellingPrice", "stock", "minStock", "category", "supplier", "costPrice", "barcode", "unidadMedida", "description"};
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
        }
    }

    public SetupWorkbookResult importWorkbook(@NonNull MultipartFile file, @NonNull String actorUsername) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(actorUsername, "actorUsername");
        assertWorkbookFile(file);

        List<String> warnings = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            boolean companyUpdated = processEmpresa(findSheet(wb, "empresa", "company"), warnings);
            int[] users = processUsuarios(findSheet(wb, "usuarios", "users"), actorUsername, warnings);
            int categoriesCreated = processCategorias(findSheet(wb, "categorias", "categories"), warnings);
            int suppliersCreated = processSuplidores(findSheet(wb, "suplidores", "proveedores", "suppliers"), warnings);
            ProductWorkbookImportResult productResult = processProductos(findSheet(wb, "productos", "products"), warnings);

            warnings.addAll(productResult.warnings());

            auditService.record(
                    SecurityAuditUtils.currentUsernameOrNull(),
                    "SETUP_WORKBOOK_IMPORTED",
                    "SetupWorkbook",
                    null,
                    Map.of(
                            "companyUpdated", Boolean.toString(companyUpdated),
                            "usersCreated", Integer.toString(users[0]),
                            "categoriesCreated", Integer.toString(categoriesCreated),
                            "suppliersCreated", Integer.toString(suppliersCreated),
                            "productsImported", Integer.toString(productResult.imported()),
                            "productsSkipped", Integer.toString(productResult.skippedDuplicates())
                    )
            );

            return new SetupWorkbookResult(
                    companyUpdated,
                    users[0],
                    users[1],
                    categoriesCreated,
                    suppliersCreated,
                    productResult.imported(),
                    productResult.skippedDuplicates(),
                    warnings
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el libro de Excel.", e);
        }
    }

    private Sheet findSheet(Workbook wb, String... names) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String sheetNorm = WorkbookHeaderUtils.normalizeSheetName(wb.getSheetName(i));
            for (String n : names) {
                if (sheetNorm.equals(WorkbookHeaderUtils.normalizeSheetName(n))) {
                    return wb.getSheetAt(i);
                }
            }
        }
        return null;
    }

    private boolean processEmpresa(Sheet sheet, List<String> warnings) {
        if (sheet == null) {
            warnings.add("Hoja Empresa no encontrada; se omitió actualización de compañía.");
            return false;
        }
        List<Map<String, String>> rows = readDataRows(sheet);
        if (rows.isEmpty()) {
            warnings.add("Hoja Empresa sin filas de datos.");
            return false;
        }
        Map<String, String> row = rows.get(0);
        Optional<CompanyConfig> existingOpt = companyConfigService.findCompanyConfig();
        CompanyConfig target = existingOpt.orElseGet(CompanyConfig::new);
        mergeEmpresaRow(target, row);
        List<String> violations = new ArrayList<>();
        addCompanyConfigBeanViolations(target, violations);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", violations));
        }
        companyConfigService.saveOrUpdate(target);
        return true;
    }

    private void mergeEmpresaRow(CompanyConfig target, Map<String, String> row) {
        putIfPresent(target::setRnc, row, "rnc");
        putIfPresent(target::setRazonSocial, row, "razonsocial", "razon_social", "razon social");
        putIfPresent(target::setNombreComercial, row, "nombrecomercial", "nombre_comercial");
        putIfPresent(target::setDireccion, row, "direccion");
        putIfPresent(target::setMunicipioCodigo, row, "municipiocodigo", "municipio_codigo", "municipio");
        putIfPresent(target::setProvinciaCodigo, row, "provinciacodigo", "provincia_codigo", "provincia");
        putIfPresent(target::setActividadEconomica, row, "actividadeconomica", "actividad_economica");
        putIfPresent(target::setNumeroTelefono, row, "numerotelefono", "numero_telefono", "telefono");
        putIfPresent(target::setCorreoElectronico, row, "correoelectronico", "correo_electronico", "email");
        putIfPresent(target::setLogoFileName, row, "logofilename", "logo_file_name", "logo");
        putIfPresent(target::setInvoiceHeaderNote, row, "invoiceheadernote", "invoice_header_note", "nota_factura_cabecera");
        putIfPresent(target::setInvoiceFooterText, row, "invoicefootertext", "invoice_footer_text", "pie_factura");
    }

    @FunctionalInterface
    private interface StringSetter {
        void set(String v);
    }

    private void putIfPresent(StringSetter setter, Map<String, String> row, String... aliases) {
        String v = cell(row, aliases);
        if (v != null && !v.isBlank()) {
            setter.set(v.trim());
        }
    }

    private void addCompanyConfigBeanViolations(CompanyConfig c, List<String> errors) {
        Set<String> unique = new LinkedHashSet<>();
        for (ConstraintViolation<CompanyConfig> v : validator.validate(c)) {
            unique.add(v.getMessage());
        }
        errors.addAll(unique);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private int[] processUsuarios(Sheet sheet, String actorUsername, List<String> warnings) {
        int created = 0;
        int skipped = 0;
        if (sheet == null) {
            warnings.add("Hoja Usuarios no encontrada.");
            return new int[]{created, skipped};
        }
        List<Map<String, String>> rows = readDataRows(sheet);
        int rowIx = 1;
        for (Map<String, String> row : rows) {
            rowIx++;
            String username = cell(row, "username", "usuario");
            String password = cell(row, "password", "contrasena", "contraseña");
            String email = cell(row, "email", "correo");
            if (isBlank(username) && isBlank(email)) {
                continue;
            }
            if (isBlank(username) || isBlank(password) || isBlank(email)) {
                warnings.add("Usuarios fila " + rowIx + ": username, password y email son obligatorios.");
                continue;
            }
            if (password.length() < 6) {
                warnings.add("Usuarios fila " + rowIx + ": la contraseña debe tener al menos 6 caracteres.");
                continue;
            }
            if (userRepository.existsByUsername(username.trim())) {
                skipped++;
                continue;
            }
            if (userRepository.existsByEmail(email.trim())) {
                warnings.add("Usuarios fila " + rowIx + ": el correo ya está registrado (" + email + ").");
                skipped++;
                continue;
            }
            User u = new User();
            u.setUsername(username.trim());
            u.setPassword(password);
            u.setEmail(email.trim());
            u.setFirstName(cell(row, "firstname", "first_name", "nombre"));
            u.setLastName(cell(row, "lastname", "last_name", "apellido"));
            u.setRole(parseRole(cell(row, "role", "rol")));
            userService.createUser(u, actorUsername);
            created++;
        }
        return new int[]{created, skipped};
    }

    private UserRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return UserRole.USER;
        }
        try {
            return UserRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UserRole.USER;
        }
    }

    private int processCategorias(Sheet sheet, List<String> warnings) {
        int created = 0;
        if (sheet == null) {
            warnings.add("Hoja Categorias no encontrada.");
            return created;
        }
        List<Map<String, String>> rows = readDataRows(sheet);
        int rowIx = 1;
        for (Map<String, String> row : rows) {
            rowIx++;
            String name = cell(row, "name", "nombre", "categoria");
            if (isBlank(name)) {
                if (rowHasAnyValue(row)) {
                    warnings.add(
                            "Categorias fila " + rowIx
                                    + ": falta el nombre de la categoría (columnas aceptadas: name, nombre, categoria)."
                    );
                }
                continue;
            }
            if (categoryRepository.findByNameIgnoreCase(name.trim()).isPresent()) {
                warnings.add(
                        "Categorias fila " + rowIx + ": la categoría \"" + name.trim() + "\" ya existe (fila omitida)."
                );
                continue;
            }
            Category c = new Category();
            c.setName(name.trim());
            categoryRepository.save(c);
            created++;
        }
        return created;
    }

    private int processSuplidores(Sheet sheet, List<String> warnings) {
        int created = 0;
        if (sheet == null) {
            warnings.add("Hoja Suplidores no encontrada.");
            return created;
        }
        List<Map<String, String>> rows = readDataRows(sheet);
        int rowIx = 1;
        for (Map<String, String> row : rows) {
            rowIx++;
            String name = cell(row, "name", "nombre", "suplidor", "proveedor");
            if (isBlank(name)) {
                if (rowHasAnyValue(row)) {
                    warnings.add(
                            "Suplidores fila " + rowIx
                                    + ": falta el nombre del suplidor (columnas: name, nombre, suplidor, proveedor)."
                    );
                }
                continue;
            }
            if (supplierRepository.findByNameIgnoreCase(name.trim()).isPresent()) {
                warnings.add(
                        "Suplidores fila " + rowIx + ": el suplidor \"" + name.trim() + "\" ya existe (fila omitida)."
                );
                continue;
            }
            Supplier s = new Supplier();
            s.setName(name.trim());
            String contact = cell(row, "contactname", "contact_name", "contacto");
            s.setContactName(isBlank(contact) ? "N/D" : contact.trim());
            s.setContactEmail(cell(row, "contactemail", "contact_email", "email"));
            s.setPhone(cell(row, "phone", "telefono", "tel"));
            s.setAddress(cell(row, "address", "direccion"));
            supplierRepository.save(s);
            created++;
        }
        return created;
    }

    private ProductWorkbookImportResult processProductos(Sheet sheet, List<String> warnings) {
        if (sheet == null) {
            warnings.add("Hoja Productos no encontrada.");
            return new ProductWorkbookImportResult(0, 0, List.of());
        }
        List<Map<String, String>> rows = readDataRows(sheet);
        return productService.importProductsFromWorkbookRows(rows);
    }

    private List<Map<String, String>> readDataRows(Sheet sheet) {
        return readIndexedDataRows(sheet).stream().map(IndexedRow::cells).toList();
    }

    private List<IndexedRow> readIndexedDataRows(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        int firstRow = sheet.getFirstRowNum();
        Row header = sheet.getRow(firstRow);
        if (header == null) {
            return List.of();
        }
        List<String> headers = new ArrayList<>();
        for (int i = header.getFirstCellNum(); i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            headers.add(WorkbookHeaderUtils.normalizeHeaderKey(cell == null ? "" : formatter.formatCellValue(cell)));
        }
        List<IndexedRow> out = new ArrayList<>();
        for (int r = firstRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, String> map = new LinkedHashMap<>();
            boolean any = false;
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c);
                if (key.isEmpty()) {
                    continue;
                }
                Cell cell = row.getCell(c);
                String val = cell == null ? "" : formatter.formatCellValue(cell).trim();
                if (!val.isEmpty()) {
                    any = true;
                }
                map.put(key, val);
            }
            if (any) {
                out.add(new IndexedRow(row.getRowNum() + 1, map));
            }
        }
        return out;
    }

    private static boolean rowHasAnyValue(Map<String, String> row) {
        for (String v : row.values()) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String cell(Map<String, String> row, String... aliases) {
        for (String a : aliases) {
            String k = WorkbookHeaderUtils.normalizeHeaderKey(a);
            if (row.containsKey(k)) {
                String v = row.get(k);
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
        }
        return "";
    }
}
