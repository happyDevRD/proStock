package com.happydev.prestockbackend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.happydev.prestockbackend.security.Authz;

import com.happydev.prestockbackend.dto.AccountSyncPreviewDto;
import com.happydev.prestockbackend.service.AccountingSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/accounting/sync")
public class AccSyncController {

    private final AccountingSyncService syncService;

    public AccSyncController(AccountingSyncService syncService) {
        this.syncService = syncService;
    }

    @PreAuthorize(Authz.ACCOUNTING_READ)
    @GetMapping("/preview")
    public ResponseEntity<AccountSyncPreviewDto> preview() {
        return ResponseEntity.ok(syncService.preview());
    }

    @PreAuthorize(Authz.ACCOUNTING_SYNC)
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Integer>> execute(
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails != null ? userDetails.getUsername() : "system";
        int count = syncService.sync(username);
        return ResponseEntity.ok(Map.of("synced", count));
    }
}
