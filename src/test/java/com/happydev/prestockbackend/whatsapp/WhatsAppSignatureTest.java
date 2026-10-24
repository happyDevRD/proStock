package com.happydev.prestockbackend.whatsapp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppSignatureTest {

    private static final byte[] BODY = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

    private static String sign(byte[] body, String secret) {
        return "sha256=" + HexFormat.of().formatHex(WhatsAppSignature.hmac(body, secret));
    }

    @Test
    void aceptaLaFirmaCorrecta() {
        assertTrue(WhatsAppSignature.isValid(BODY, sign(BODY, "secreto"), "secreto"));
    }

    @Test
    void aceptaLaFirmaEnMayusculas() {
        String upper = "sha256=" + sign(BODY, "secreto").substring("sha256=".length()).toUpperCase();
        assertTrue(WhatsAppSignature.isValid(BODY, upper, "secreto"));
    }

    @Test
    void rechazaOtroSecretoOtroCuerpoOCabeceraMala() {
        assertFalse(WhatsAppSignature.isValid(BODY, sign(BODY, "otro"), "secreto"));
        assertFalse(WhatsAppSignature.isValid("{}".getBytes(StandardCharsets.UTF_8), sign(BODY, "secreto"), "secreto"));
        assertFalse(WhatsAppSignature.isValid(BODY, null, "secreto"));
        assertFalse(WhatsAppSignature.isValid(BODY, "sha1=abc", "secreto"));
        assertFalse(WhatsAppSignature.isValid(BODY, "sha256=zz", "secreto"));
    }

    @Test
    void sinSecretoConfiguradoNoAceptaNada() {
        assertFalse(WhatsAppSignature.isValid(BODY, sign(BODY, "x"), ""));
        assertFalse(WhatsAppSignature.isValid(BODY, sign(BODY, "x"), null));
    }
}
