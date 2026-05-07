package com.happydev.prestockbackend.dto;

import java.util.Locale;

public enum SetupSheetType {
    EMPRESA("empresa"),
    USUARIOS("usuarios"),
    CATEGORIAS("categorias"),
    SUPLIDORES("suplidores"),
    PRODUCTOS("productos");

    private final String fileToken;

    SetupSheetType(String fileToken) {
        this.fileToken = fileToken;
    }

    public String getFileToken() {
        return fileToken;
    }

    public static SetupSheetType fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Parámetro sheet es obligatorio.");
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "empresa", "company" -> EMPRESA;
            case "usuarios", "users" -> USUARIOS;
            case "categorias", "categories" -> CATEGORIAS;
            case "suplidores", "proveedores", "suppliers" -> SUPLIDORES;
            case "productos", "products" -> PRODUCTOS;
            default -> throw new IllegalArgumentException(
                    "Hoja no reconocida: " + raw + ". Valores: empresa, usuarios, categorias, suplidores, productos."
            );
        };
    }
}
