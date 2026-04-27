package com.happydev.prestockbackend.entity;

public enum TipoBienServicio {
    BIEN(1),
    SERVICIO(2);

    private final int code;

    TipoBienServicio(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TipoBienServicio fromCode(Integer code) {
        if (code == null) {
            return null;
        }

        for (TipoBienServicio value : values()) {
            if (value.code == code) {
                return value;
            }
        }

        throw new IllegalArgumentException("Tipo de bien/servicio no valido: " + code);
    }
}
