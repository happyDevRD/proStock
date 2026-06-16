package com.happydev.prestockbackend.security;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        TotpProperties properties = new TotpProperties();
        totpService = new TotpService(properties);
    }

    @Test
    void encryptAndDecrypt_roundTrip() {
        String secret = totpService.generateSecret();

        String encrypted = totpService.encrypt(secret);

        assertNotEquals(secret, encrypted);
        assertEquals(secret, totpService.decrypt(encrypted));
    }

    @Test
    void verifyCode_validCode_returnsTrue() throws Exception {
        String secret = totpService.generateSecret();
        String code = new DefaultCodeGenerator().generate(secret, new SystemTimeProvider().getTime() / 30);

        assertTrue(totpService.verifyCode(secret, code));
    }

    @Test
    void verifyCode_invalidCode_returnsFalse() {
        String secret = totpService.generateSecret();

        assertFalse(totpService.verifyCode(secret, "000000"));
    }

    @Test
    void verifyCode_blankCode_returnsFalse() {
        String secret = totpService.generateSecret();

        assertFalse(totpService.verifyCode(secret, ""));
        assertFalse(totpService.verifyCode(secret, null));
    }

    @Test
    void buildQrDataUri_returnsPngDataUri() {
        String secret = totpService.generateSecret();

        String qr = totpService.buildQrDataUri("admin", secret);

        assertNotNull(qr);
        assertTrue(qr.startsWith("data:image/png;base64,"));
    }
}
