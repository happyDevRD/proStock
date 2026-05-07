package com.happydev.prestockbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.AuditLog;
import com.happydev.prestockbackend.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(
            @Nullable String username,
            @NonNull String action,
            @Nullable String entityType,
            @Nullable Long entityId,
            @Nullable Object detailsPayload
    ) {
        AuditLog row = new AuditLog();
        row.setCreatedAt(LocalDateTime.now());
        row.setUsername(username);
        row.setAction(action);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        if (detailsPayload != null) {
            try {
                row.setDetails(objectMapper.writeValueAsString(detailsPayload));
            } catch (JsonProcessingException e) {
                log.warn("No se pudo serializar detalles de auditoria para accion {}: {}", action, e.getMessage());
                row.setDetails("{}");
            }
        }
        auditLogRepository.save(row);
    }
}
