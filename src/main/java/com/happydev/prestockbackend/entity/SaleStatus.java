package com.happydev.prestockbackend.entity;


public enum SaleStatus {
    PENDING,    // Pendiente (registrada, pero no finalizada)
    COMPLETED,  // Completada (stock descontado)
    CANCELED    // Cancelada (la venta no se concretó)
}
