package com.happydev.prestockbackend.entity;

public enum PurchaseOrderStatus {
    PENDING,         // Pendiente
    RECEIVED,        // Recibida (no pagada)
    PARTIALLY_PAID,  // Parcialmente pagada
    PAID,            // Completamente pagada
    CANCELED         // Cancelada
}
