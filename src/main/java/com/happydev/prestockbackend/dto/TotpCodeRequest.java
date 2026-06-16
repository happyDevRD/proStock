package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpCodeRequest(@NotBlank String code) {
}
