package com.happydev.prestockbackend.service;

/** Llegó una solicitud nueva desde el formulario de la web (se procesa después del commit). */
public record BookingRequestCreatedEvent(Long bookingRequestId) {}
