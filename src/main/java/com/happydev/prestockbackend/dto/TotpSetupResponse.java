package com.happydev.prestockbackend.dto;

public record TotpSetupResponse(String secret, String qrCodeDataUri) {
}
