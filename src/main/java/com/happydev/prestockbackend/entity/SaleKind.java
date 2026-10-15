package com.happydev.prestockbackend.entity;

/** Origen de la venta: POS normal, anticipo de una orden de servicio o factura del saldo de la orden. */
public enum SaleKind {
    NORMAL,
    DEPOSIT,
    BALANCE
}
