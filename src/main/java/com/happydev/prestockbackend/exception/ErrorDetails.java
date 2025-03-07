package com.happydev.prestockbackend.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ErrorDetails {
    private final LocalDateTime timestamp;
    private final String code;
    private final String message;
    private final Object errors;
    private final String details;

    public ErrorDetails(LocalDateTime timestamp, String code, String message, String details) {
        this.timestamp = timestamp;
        this.code = code;
        this.message = message;
        this.details = details;
        this.errors = null;
    }
}