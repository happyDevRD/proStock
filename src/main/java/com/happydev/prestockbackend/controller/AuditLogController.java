package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.AuditLogDto;
import com.happydev.prestockbackend.entity.AuditLog;
import com.happydev.prestockbackend.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public Page<AuditLogDto> list(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return auditLogRepository.findAll(pageable).map(this::toDto);
    }

    private AuditLogDto toDto(AuditLog row) {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(row.getId());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUsername(row.getUsername());
        dto.setAction(row.getAction());
        dto.setEntityType(row.getEntityType());
        dto.setEntityId(row.getEntityId());
        dto.setDetails(row.getDetails());
        return dto;
    }
}
