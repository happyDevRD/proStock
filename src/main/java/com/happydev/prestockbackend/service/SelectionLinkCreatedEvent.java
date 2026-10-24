package com.happydev.prestockbackend.service;

/** Se generó (o regeneró) el enlace del portal de selección de una orden. */
public record SelectionLinkCreatedEvent(Long orderId, String token) {}
