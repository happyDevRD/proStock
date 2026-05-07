package com.happydev.prestockbackend.dto;

public record SetupWizardStatusDto(
        boolean empresaStepComplete,
        boolean usuariosStepComplete,
        boolean categoriasStepComplete,
        boolean suplidoresStepComplete,
        boolean productosStepComplete,
        boolean ncfConsumoFinalComplete,
        boolean ncfCreditoFiscalComplete,
        long userCount,
        long categoryCount,
        long supplierCount,
        long productCount,
        int checklistCompleted,
        int checklistTotal
) {
}
