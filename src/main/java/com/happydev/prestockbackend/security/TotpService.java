package com.happydev.prestockbackend.security;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

import java.util.Base64;

/** Fase Q3: generación/validación de códigos TOTP y cifrado del secreto en reposo. */
@Service
public class TotpService {

    private final TotpProperties properties;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final TextEncryptor encryptor;

    public TotpService(TotpProperties properties) {
        this.properties = properties;
        this.encryptor = Encryptors.text(properties.getEncryptionKey(), properties.getEncryptionSalt());
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String encrypt(String secret) {
        return encryptor.encrypt(secret);
    }

    public String decrypt(String encryptedSecret) {
        return encryptor.decrypt(encryptedSecret);
    }

    public boolean verifyCode(String secret, String code) {
        return code != null && !code.isBlank() && codeVerifier.isValidCode(secret, code.trim());
    }

    /** Genera un QR (data URI) para escanear con Google Authenticator/Authy/etc. */
    public String buildQrDataUri(String accountName, String secret) {
        QrData data = new QrData.Builder()
                .label(accountName)
                .secret(secret)
                .issuer(properties.getIssuer())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            byte[] imageData = qrGenerator.generate(data);
            return "data:" + qrGenerator.getImageMimeType() + ";base64," + Base64.getEncoder().encodeToString(imageData);
        } catch (QrGenerationException ex) {
            throw new IllegalStateException("No se pudo generar el código QR de 2FA", ex);
        }
    }
}
