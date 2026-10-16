package com.happydev.prestockbackend.entity;

public enum UserRole {
    GESTOR,
    ADMIN,
    MANAGER,
    CONTADOR,
    CASHIER,
    USER,
    /** Fotógrafo o editor: solo ve "Mis trabajos" (órdenes asignadas) y avanza su parte. */
    PRODUCCION
}
