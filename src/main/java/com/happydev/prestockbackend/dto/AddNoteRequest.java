package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record AddNoteRequest(
        @NotBlank String content,
        boolean internal
) {}
