package com.happydev.prestockbackend.entity;

public enum StockMovementType {
    IN,         // Entrada (compra, ajuste positivo)
    OUT,        // Salida (venta, ajuste negativo, devolución)
    ADJUSTMENT, // Ajuste manual (positivo o negativo)
    TRANSFER,   // Transferencia entre ubicaciones
    LOSS,      // Pérdida (robo, daño, etc.)
    RETURN     // Devolución de cliente/a proveedor
}