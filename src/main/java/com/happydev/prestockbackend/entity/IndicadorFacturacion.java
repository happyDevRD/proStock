package com.happydev.prestockbackend.entity;

import java.math.BigDecimal;

public enum IndicadorFacturacion {
    ITBIS_18(1, new BigDecimal("0.18")),
    ITBIS_16(2, new BigDecimal("0.16")),
    ITBIS_0(3, BigDecimal.ZERO),
    EXENTO(4, BigDecimal.ZERO);

    private final int code;
    private final BigDecimal rate;

    IndicadorFacturacion(int code, BigDecimal rate) {
        this.code = code;
        this.rate = rate;
    }

    public int getCode() {
        return code;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public boolean isExento() {
        return this == EXENTO;
    }

    public static IndicadorFacturacion fromCode(Integer code) {
        if (code == null) {
            return null;
        }

        for (IndicadorFacturacion value : values()) {
            if (value.code == code) {
                return value;
            }
        }

        throw new IllegalArgumentException("Indicador de facturacion no valido: " + code);
    }
}
