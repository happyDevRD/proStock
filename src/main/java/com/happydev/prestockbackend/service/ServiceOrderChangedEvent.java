package com.happydev.prestockbackend.service;

/**
 * Se publica cuando una orden de servicio se crea o cambia (datos, responsables
 * o etapa). Los listeners la procesan después del commit, así que nunca hacen
 * fallar la operación que la originó.
 */
public record ServiceOrderChangedEvent(Long orderId) {}
