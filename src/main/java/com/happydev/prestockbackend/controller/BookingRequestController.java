package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.BookingRequestDtos.BookingRequestCount;
import com.happydev.prestockbackend.dto.BookingRequestDtos.BookingRequestView;
import com.happydev.prestockbackend.dto.BookingRequestDtos.ConvertBookingRequest;
import com.happydev.prestockbackend.service.BookingRequestService;
import com.happydev.prestockbackend.service.ServiceOrderAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Bandeja de solicitudes web del estudio: mismo acceso que las órdenes de servicio. */
@RestController
@RequestMapping("/api/booking-requests")
@PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'view.service_orders')")
public class BookingRequestController {

    private final BookingRequestService service;
    private final ServiceOrderAccessGuard accessGuard;

    public BookingRequestController(BookingRequestService service, ServiceOrderAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    /** {@code pending=true}: por atender; {@code false}: las últimas atendidas. */
    @GetMapping
    public ResponseEntity<List<BookingRequestView>> list(@RequestParam(defaultValue = "true") boolean pending) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(service.list(pending));
    }

    @GetMapping("/count")
    public ResponseEntity<BookingRequestCount> count() {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(service.count());
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<BookingRequestView> convert(@PathVariable Long id,
                                                      @Valid @RequestBody ConvertBookingRequest body,
                                                      Principal principal) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(service.convert(id, body, principal != null ? principal.getName() : null));
    }

    @PostMapping("/{id}/discard")
    public ResponseEntity<BookingRequestView> discard(@PathVariable Long id, Principal principal) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(service.discard(id, principal != null ? principal.getName() : null));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<BookingRequestView> reopen(@PathVariable Long id) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(service.reopen(id));
    }
}
