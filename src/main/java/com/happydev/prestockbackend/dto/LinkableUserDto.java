package com.happydev.prestockbackend.dto;

/** Usuario que se puede vincular a un empleado; linkedEmployee = nombre del empleado si ya lo está. */
public record LinkableUserDto(Long id, String username, String fullName, String role, String linkedEmployee) {}
