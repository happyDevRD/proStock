package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.CreateJournalEntryRequest;
import com.happydev.prestockbackend.dto.JournalEntryDto;
import com.happydev.prestockbackend.service.JournalEntryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounting/journal-entries")
public class AccJournalEntryController {

    private final JournalEntryService journalEntryService;

    public AccJournalEntryController(JournalEntryService journalEntryService) {
        this.journalEntryService = journalEntryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('accounting.module.view')")
    public ResponseEntity<List<JournalEntryDto>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        LocalDate from = dateFrom != null && !dateFrom.isBlank() ? LocalDate.parse(dateFrom) : null;
        LocalDate to   = dateTo   != null && !dateTo.isBlank()   ? LocalDate.parse(dateTo)   : null;

        return ResponseEntity.ok(journalEntryService.findAll(status, from, to));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('accounting.module.view')")
    public ResponseEntity<JournalEntryDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(journalEntryService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('accounting.journal.create')")
    public ResponseEntity<JournalEntryDto> create(
            @RequestBody CreateJournalEntryRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(journalEntryService.create(req, username));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('accounting.journal.edit')")
    public ResponseEntity<JournalEntryDto> update(
            @PathVariable Long id,
            @RequestBody CreateJournalEntryRequest req) {
        return ResponseEntity.ok(journalEntryService.update(id, req));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('accounting.journal.post')")
    public ResponseEntity<JournalEntryDto> post(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(journalEntryService.post(id, username));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('accounting.journal.reverse')")
    public ResponseEntity<JournalEntryDto> reverse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(journalEntryService.reverse(id, username));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('accounting.journal.delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        journalEntryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
