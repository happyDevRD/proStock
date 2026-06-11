package com.happydev.prestockbackend.controller;

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

    public ServiceOrderController(ServiceOrderService serviceOrderService,
                                  ServiceOrderAccessGuard accessGuard) {
        this.serviceOrderService = serviceOrderService;
        this.accessGuard = accessGuard;
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
            @PageableDefault(size = 40, sort = "createdAt") @NonNull Pageable pageable
    ) {
        accessGuard.requireEnabled();
        String term = search != null && !search.isBlank() ? search : q;
        return ResponseEntity.ok(serviceOrderService.findPage(pageable, type, status, activeOnly, term));
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
            @RequestBody(required = false) Map<String, String> body,
            Principal principal
    ) {
        accessGuard.requireEnabled();
        String actor = principal != null ? principal.getName() : null;
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(serviceOrderService.cancel(id, notes, actor));
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
