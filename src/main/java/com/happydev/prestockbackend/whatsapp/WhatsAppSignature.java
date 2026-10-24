package com.happydev.prestockbackend.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Meta firma el cuerpo de cada aviso del webhook con HMAC-SHA256 y el secreto de la app
 * (cabecera {@code X-Hub-Signature-256: sha256=<hex>}). Se comprueba sobre los bytes tal
 * cual llegaron, antes de interpretar el JSON.
 */
public final class WhatsAppSignature {

    private static final String PREFIX = "sha256=";

    private WhatsAppSignature() {
    }

    public static boolean isValid(byte[] body, String header, String appSecret) {
        if (appSecret == null || appSecret.isBlank() || header == null || !header.startsWith(PREFIX)) return false;
        byte[] expected = hmac(body, appSecret);
        byte[] received;
        try {
            received = HexFormat.of().parseHex(header.substring(PREFIX.length()).trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, received);
    }

    static byte[] hmac(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
