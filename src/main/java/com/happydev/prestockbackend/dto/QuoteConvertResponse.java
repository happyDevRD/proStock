package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos para cargar una cotización en el POS. Convertir no cambia el estado de la
 * cotización: queda CONVERTED cuando la venta existe (POST /api/quotes/{id}/converted-sale).
 */
public record QuoteConvertResponse(
        Long quoteId,
        Long customerId,
        List<QuoteItemForPos> items,
        BigDecimal suggestedAmount
) {
    /**
     * Línea para el ticket. {@code productId} es null si la línea no es un artículo del
     * inventario (el POS la avisa con su {@code description}). {@code unitPrice} es el
     * precio base efectivo, con el descuento de la línea ya aplicado.
     */
    public record QuoteItemForPos(Long productId, String description, int quantity, BigDecimal unitPrice) {}
}
