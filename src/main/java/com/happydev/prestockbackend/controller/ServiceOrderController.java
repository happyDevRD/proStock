package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.ServiceOrderStaffDto;
import com.happydev.prestockbackend.dto.QuickStaffRequest;
import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.EmployeeDepartment;
import com.happydev.prestockbackend.entity.EmployeeStatus;
import com.happydev.prestockbackend.repository.EmployeeRepository;
import com.happydev.prestockbackend.service.WorkFolderService;
import com.happydev.prestockbackend.service.SelectionService;
import com.happydev.prestockbackend.dto.SelectionDtos;

import com.happydev.prestockbackend.dto.*;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import com.happydev.prestockbackend.service.ServiceOrderAccessGuard;
import com.happydev.prestockbackend.service.ServiceOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-orders")
@PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'view.service_orders')")
public class ServiceOrderController {

    private final ServiceOrderService serviceOrderService;
    private final ServiceOrderAccessGuard accessGuard;
    private final WorkFolderService workFolderService;
    private final EmployeeRepository employeeRepository;
    private final SelectionService selectionService;

    public ServiceOrderController(ServiceOrderService serviceOrderService,
                                  ServiceOrderAccessGuard accessGuard,
                                  WorkFolderService workFolderService,
                                  EmployeeRepository employeeRepository,
                                  SelectionService selectionService) {
        this.serviceOrderService = serviceOrderService;
        this.accessGuard = accessGuard;
        this.workFolderService = workFolderService;
        this.employeeRepository = employeeRepository;
        this.selectionService = selectionService;
    }

    // ── Selección de fotos por el cliente (enlace público) ─────────────────

    /** Enlace actual (o null) y valores sugeridos: cupo según los combos, previas disponibles. */
    @GetMapping("/{id}/selection")
    public ResponseEntity<java.util.Map<String, Object>> getSelection(@PathVariable Long id) {
        accessGuard.requireEnabled();
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("selection", selectionService.get(id));
        body.put("suggestion", selectionService.suggest(id));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/selection")
    public ResponseEntity<SelectionDtos.AdminSelection> createSelection(
            @PathVariable Long id,
            @Valid @RequestBody SelectionDtos.CreateSelectionRequest request,
            Principal principal) {
        accessGuard.requireEnabled();
        int days = request.expiresInDays() != null ? request.expiresInDays() : 15;
        return ResponseEntity.ok(selectionService.create(id, request.quota(), days,
                principal != null ? principal.getName() : null));
    }

    @DeleteMapping("/{id}/selection")
    public ResponseEntity<Void> revokeSelection(@PathVariable Long id) {
        accessGuard.requireEnabled();
        selectionService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    /** Empleados activos para asignar como fotógrafo o editor (solo id, nombre y cargo). */
    @GetMapping("/staff")
    public ResponseEntity<List<ServiceOrderStaffDto>> staff() {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(employeeRepository.findAll().stream()
                .filter(e -> e.getStatus() == EmployeeStatus.ACTIVO)
                .sorted(java.util.Comparator.comparing(Employee::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
                .map(this::toStaffDto)
                .toList());
    }

    /**
     * Crea un empleado con lo mínimo (nombre y funciones) desde el formulario de la orden.
     * El resto de datos (cédula, salario…) se completa luego en Empleados.
     */
    @PostMapping("/staff")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'employees.create')")
    public ResponseEntity<ServiceOrderStaffDto> createStaff(@Valid @RequestBody QuickStaffRequest request,
                                                            Principal principal) {
        accessGuard.requireEnabled();
        if (!request.fotografo() && !request.editor()) {
            throw new IllegalArgumentException("Marca al menos una función: fotógrafo o editor.");
        }
        Employee e = new Employee();
        e.setNombreCompleto(request.nombreCompleto().trim());
        e.setCargo(request.fotografo() && request.editor() ? "Fotógrafo y editor"
                : request.fotografo() ? "Fotógrafo" : "Editor");
        e.setDepartamento(EmployeeDepartment.PRODUCCION);
        e.setFechaIngreso(java.time.LocalDate.now());
        e.setEsFotografo(request.fotografo());
        e.setEsEditor(request.editor());
        e.setCreatedBy(principal != null ? principal.getName() : null);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(toStaffDto(employeeRepository.save(e)));
    }

    private ServiceOrderStaffDto toStaffDto(Employee e) {
        return new ServiceOrderStaffDto(e.getId(), e.getNombreCompleto(), e.getCargo(), e.isEsFotografo(), e.isEsEditor());
    }

    @GetMapping("/work-folders/status")
    public ResponseEntity<java.util.Map<String, Boolean>> workFoldersStatus() {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(java.util.Map.of("enabled", workFolderService.isEnabled()));
    }

    /** Crea la carpeta en el NAS si falta (o reescribe INFO.txt). Para reintentar o para órdenes antiguas. */
    @PostMapping("/{id}/work-folder")
    public ResponseEntity<ServiceOrderDto> syncWorkFolder(@PathVariable Long id) throws java.io.IOException {
        accessGuard.requireEnabled();
        if (!workFolderService.isEnabled()) {
            throw new IllegalStateException("Las carpetas de trabajo no están configuradas en este servidor.");
        }
        workFolderService.sync(id);
        return ResponseEntity.ok(serviceOrderService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<ServiceOrderDto>> getAll(
            @RequestParam(required = false) ServiceOrderType type,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        accessGuard.requireEnabled();
        List<ServiceOrderDto> result;
        if (type != null) {
            result = serviceOrderService.findByType(type);
        } else if (activeOnly) {
            result = serviceOrderService.findActiveOrders();
        } else {
            result = serviceOrderService.findAll();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/paginated")
    public ResponseEntity<Page<ServiceOrderDto>> getPage(
            @RequestParam(required = false) ServiceOrderType type,
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long staffId,
            @PageableDefault(size = 40, sort = "createdAt") @NonNull Pageable pageable
    ) {
        accessGuard.requireEnabled();
        String term = search != null && !search.isBlank() ? search : q;
        return ResponseEntity.ok(serviceOrderService.findPage(pageable, type, status, activeOnly, term, staffId));
    }

    /** Calendario: citas del rango (máx. 62 días), sin las canceladas. */
    @GetMapping("/calendar")
    public ResponseEntity<List<ServiceOrderDto>> calendar(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) ServiceOrderType type,
            @RequestParam(required = false) Long staffId
    ) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(serviceOrderService.findScheduled(from, to, type, staffId));
    }

    @GetMapping("/stats")
    public ResponseEntity<ServiceOrderStatsDto> getStats() {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(serviceOrderService.getStats());
    }

    @GetMapping("/report")
    public ResponseEntity<ServiceOrderReportDto> getReport(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(serviceOrderService.getReport(startDate, endDate));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ServiceOrderDto>> getByCustomer(@PathVariable Long customerId) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(serviceOrderService.findByCustomer(customerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOrderDto> getById(@PathVariable Long id) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(serviceOrderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ServiceOrderDto> create(
            @Valid @RequestBody CreateServiceOrderRequest request,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        ServiceOrderDto created = serviceOrderService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceOrderDto> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceOrderRequest request,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.update(id, request, actor));
    }

    @PostMapping("/{id}/advance-stage")
    public ResponseEntity<ServiceOrderDto> advanceStage(
            @PathVariable Long id,
            @Valid @RequestBody AdvanceStageRequest request,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.advanceStage(id, request, actor));
    }

    @PostMapping("/{id}/approve-budget")
    public ResponseEntity<ServiceOrderDto> approveBudget(
            @PathVariable Long id,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.approveBudget(id, actor));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ServiceOrderDto> complete(
            @PathVariable Long id,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.complete(id, actor));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ServiceOrderDto> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
        boolean refundDeposits = body != null && Boolean.parseBoolean(String.valueOf(body.get("refundDeposits")));
        return ResponseEntity.ok(serviceOrderService.cancel(id, notes, refundDeposits, actor));
    }

    /** Cobra un anticipo (monto libre): factura con NCF + pago, vinculada a la orden. */
    @PostMapping("/{id}/deposits")
    public ResponseEntity<SaleDto> registerDeposit(
            @PathVariable Long id,
            @Valid @RequestBody ServiceOrderPaymentRequest request,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOrderService.registerDeposit(id, request, actor));
    }

    /** Anula un anticipo con nota de crédito (corrección o devolución). */
    @PostMapping("/{id}/deposits/{saleId}/void")
    public ResponseEntity<ServiceOrderDto> voidDeposit(
            @PathVariable Long id,
            @PathVariable Long saleId,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(serviceOrderService.voidDeposit(id, saleId, reason, actor));
    }

    /** Factura y cobra el saldo pendiente de la orden (total − anticipos). */
    @PostMapping("/{id}/balance-invoice")
    public ResponseEntity<SaleDto> billBalance(
            @PathVariable Long id,
            @Valid @RequestBody ServiceOrderPaymentRequest request,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOrderService.billBalance(id, request, actor));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<ServiceOrderNoteDto> addNote(
            @PathVariable Long id,
            @Valid @RequestBody AddNoteRequest request,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        ServiceOrderNoteDto note = serviceOrderService.addNote(id, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    @DeleteMapping("/{orderId}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @PathVariable Long orderId,
            @PathVariable Long noteId
    ) {
        accessGuard.requireEnabled();
        serviceOrderService.deleteNote(orderId, noteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ServiceOrderItemDto> addItem(
            @PathVariable Long id,
            @Valid @RequestBody AddServiceOrderItemRequest request
    ) {
        accessGuard.requireEnabled();
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOrderService.addItem(id, request));
    }

    @PutMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<ServiceOrderItemDto> updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateServiceOrderItemRequest request
    ) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(serviceOrderService.updateItem(orderId, itemId, request));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId
    ) {
        accessGuard.requireEnabled();
        serviceOrderService.removeItem(orderId, itemId);
        return ResponseEntity.noContent().build();
    }
}
