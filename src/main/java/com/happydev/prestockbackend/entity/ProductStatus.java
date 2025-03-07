package com.happydev.prestockbackend.entity;


public enum ProductStatus {
    ACTIVE,      // Producto activo y disponible para la venta
    INACTIVE,    // Producto inactivo (no se muestra, no se puede vender)
    DISCONTINUED // Producto descontinuado (no se volverá a comprar)
}