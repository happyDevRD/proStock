package com.happydev.prestockbackend.exception;

import org.springframework.security.access.AccessDeniedException;

public class FeatureDisabledException extends AccessDeniedException {

    public FeatureDisabledException(String featureCode) {
        super("La funcionalidad '" + featureCode + "' no está habilitada para esta empresa.");
    }
}
