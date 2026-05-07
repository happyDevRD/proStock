package com.happydev.prestockbackend.service;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public interface AuditService {

    void record(
            @Nullable String username,
            @NonNull String action,
            @Nullable String entityType,
            @Nullable Long entityId,
            @Nullable Object detailsPayload
    );
}
