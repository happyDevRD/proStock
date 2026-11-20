package com.happydev.prestockbackend.service;

/**
 * Se generó (o regeneró) el acceso del portal de selección de una orden.
 *
 * @param token     token interno (cambia en cada generación: sirve para no repetir el aviso)
 * @param username  usuario del cliente (para entrar con contraseña)
 * @param accessKey llave del enlace directo: va en el botón del WhatsApp (iridiscencia.com/seleccion/{accessKey})
 */
public record SelectionLinkCreatedEvent(Long orderId, String token, String username, String accessKey) {}
