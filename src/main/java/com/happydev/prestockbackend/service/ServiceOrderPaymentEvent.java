package com.happydev.prestockbackend.service;

import java.math.BigDecimal;

/** Se cobró un anticipo o el saldo de una orden de servicio (se procesa después del commit). */
public record ServiceOrderPaymentEvent(Long orderId, Long saleId, BigDecimal amount) {}
