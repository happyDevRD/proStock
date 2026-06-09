package com.happydev.prestockbackend.dto;

import java.time.LocalDateTime;

public record ServiceOrderNoteDto(
        Long id,
        String content,
        String author,
        boolean internal,
        LocalDateTime createdAt
) {}
