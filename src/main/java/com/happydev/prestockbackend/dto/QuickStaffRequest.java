package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta rápida de fotógrafo/editor desde la orden: solo nombre y funciones. */
public record QuickStaffRequest(@NotBlank @Size(max = 200) String nombreCompleto, boolean fotografo, boolean editor) {}
