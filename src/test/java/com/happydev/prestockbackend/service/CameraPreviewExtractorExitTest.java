package com.happydev.prestockbackend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CameraPreviewExtractorExitTest {

    @Test
    void acceptsSuccessAndPerFileErrors() {
        assertDoesNotThrow(() -> CameraPreviewExtractor.checkExit(0));
        assertDoesNotThrow(() -> CameraPreviewExtractor.checkExit(1));
    }

    @Test
    void killedProcessFailsInsteadOfReturningPartialOutput() {
        // 137 = SIGKILL (OOM del contenedor): antes se seguía y las previas salían sin girar.
        IOException e = assertThrows(IOException.class, () -> CameraPreviewExtractor.checkExit(137));
        assertTrue(e.getMessage().contains("memoria"));
    }

    @Test
    void otherErrorsFail() {
        assertThrows(IOException.class, () -> CameraPreviewExtractor.checkExit(2));
    }
}
