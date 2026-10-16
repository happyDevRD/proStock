package com.happydev.prestockbackend.dto;

/** Empleado asignable a una orden (fotógrafo, editor), sin datos de nómina. */
public record ServiceOrderStaffDto(Long id, String nombreCompleto, String cargo) {}
