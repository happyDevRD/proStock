package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.*;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import com.happydev.prestockbackend.service.ServiceOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-orders")
public class ServiceOrderController {

    private final ServiceOrderService serviceOrderService;

    public ServiceOrderController(ServiceOrderService serviceOrderService) {
        this.serviceOrderService = serviceOrderService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceOrderDto>> getAll(
            @RequestParam(required = false) ServiceOrderType type,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
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

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ServiceOrderDto>> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(serviceOrderService.findByCustomer(customerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOrderDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceOrderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ServiceOrderDto> create(
            @Valid @RequestBody CreateServiceOrderRequest request,
            Principal principal
    ) {
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
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.update(id, request, actor));
    }

    @PostMapping("/{id}/advance-stage")
    public ResponseEntity<ServiceOrderDto> advanceStage(
            @PathVariable Long id,
            @Valid @RequestBody AdvanceStageRequest request,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.advanceStage(id, request, actor));
    }

    @PostMapping("/{id}/approve-budget")
    public ResponseEntity<ServiceOrderDto> approveBudget(
            @PathVariable Long id,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.approveBudget(id, actor));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ServiceOrderDto> complete(
            @PathVariable Long id,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(serviceOrderService.complete(id, actor));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ServiceOrderDto> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal
    ) {
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
        String actor = principal != null ? principal.getName() : null;
        ServiceOrderNoteDto note = serviceOrderService.addNote(id, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    @DeleteMapping("/{orderId}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @PathVariable Long orderId,
            @PathVariable Long noteId
    ) {
        serviceOrderService.deleteNote(orderId, noteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ServiceOrderItemDto> addItem(
            @PathVariable Long id,
            @RequestBody AddServiceOrderItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOrderService.addItem(id, request));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId
    ) {
        serviceOrderService.removeItem(orderId, itemId);
        return ResponseEntity.noContent().build();
    }
}
