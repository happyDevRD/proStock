package com.happydev.prestockbackend.entity;

public enum TipoIngresos {
    OPERACIONES("01");

    private final String code;

    TipoIngresos(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static TipoIngresos fromCode(String code) {
        if (code == null) {
            return null;
        }

        for (TipoIngresos value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Tipo de ingresos no valido: " + code);
    }
}
