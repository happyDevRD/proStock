package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AuditLogDto {

    private Long id;

    private LocalDateTime createdAt;

    private String username;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;
}
