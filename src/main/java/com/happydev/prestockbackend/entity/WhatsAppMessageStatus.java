package com.happydev.prestockbackend.entity;

/** Estado de un aviso por WhatsApp en la bandeja de salida. */
public enum WhatsAppMessageStatus {
    /** Esperando su turno de envío (o un reintento). */
    PENDING,
    /** Tomado por el despachador; si el proceso se cae aquí, vuelve a PENDING al arrancar. */
    SENDING,
    /** Meta lo aceptó (tiene wamid). */
    SENT,
    /** Modo simulación: se registró pero no se envió. */
    SIMULATED,
    /** Meta lo rechazó o se agotaron los reintentos. */
    FAILED,
    /** No se envió por una regla (sin consentimiento, sin teléfono…). */
    SKIPPED
}
