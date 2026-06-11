package com.happydev.prestockbackend.entity;

public enum PurchaseOrderStatus {
    PENDING,         // Pendiente
    PARTIALLY_RECEIVED, // Recibida parcialmente (mercancía incompleta)
    RECEIVED,        // Recibida (no pagada)
    PARTIALLY_PAID,  // Parcialmente pagada
    PAID,            // Completamente pagada
    CANCELED         // Cancelada
}
