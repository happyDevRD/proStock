package com.happydev.prestockbackend.entity;


public enum SaleStatus {
    PENDING,          // Pendiente (registrada, sin NCF ni stock descontado)
    PARTIALLY_PAID,   // Abonada parcialmente (sin NCF aún; falta completar el pago)
    COMPLETED,        // Completada (pago total recibido, NCF asignado, stock descontado)
    CANCELED          // Cancelada (la venta no se concretó)
}
