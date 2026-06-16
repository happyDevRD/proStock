package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.CreateQuoteRequest;
import com.happydev.prestockbackend.dto.QuoteConvertResponse;
import com.happydev.prestockbackend.dto.QuoteDto;
import com.happydev.prestockbackend.entity.QuoteStatus;
import com.happydev.prestockbackend.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'view.quotes')")
    public ResponseEntity<Page<QuoteDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId
    ) {
        return ResponseEntity.ok(quoteService.list(page, size, status, customerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'view.quotes')")
    public ResponseEntity<QuoteDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(quoteService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.create')")
    public ResponseEntity<QuoteDto> create(@Valid @RequestBody CreateQuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quoteService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.edit')")
    public ResponseEntity<QuoteDto> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateQuoteRequest request
    ) {
        return ResponseEntity.ok(quoteService.update(id, request));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.edit')")
    public ResponseEntity<QuoteDto> send(@PathVariable Long id) {
        quoteService.updateStatus(id, QuoteStatus.SENT);
        return ResponseEntity.ok(quoteService.findById(id));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.edit')")
    public ResponseEntity<QuoteDto> accept(@PathVariable Long id) {
        quoteService.updateStatus(id, QuoteStatus.ACCEPTED);
        return ResponseEntity.ok(quoteService.findById(id));
    }

    @PostMapping("/{id}/revert-draft")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.edit')")
    public ResponseEntity<QuoteDto> revertToDraft(@PathVariable Long id) {
        return ResponseEntity.ok(quoteService.revertToDraft(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.delete')")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        quoteService.updateStatus(id, QuoteStatus.CANCELED);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.convert')")
    public ResponseEntity<QuoteConvertResponse> convert(@PathVariable Long id) {
        return ResponseEntity.ok(quoteService.convert(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'quotes.delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        quoteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
