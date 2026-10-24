package com.happydev.prestockbackend.entity;

/**
 * Tipos de aviso y su plantilla aprobada en Meta (documentacion/whatsapp-plantillas.md).
 * {@code feature} es el interruptor del Centro de Módulos que lo activa; null = solo manual.
 */
public enum WhatsAppMessageKind {
    BOOKING_RECEIVED("solicitud_recibida", "whatsapp.booking_received", "Solicitud recibida"),
    APPOINTMENT_CONFIRMED("cita_confirmada", "whatsapp.appointment_confirmed", "Cita confirmada"),
    APPOINTMENT_REMINDER("recordatorio_cita", "whatsapp.appointment_reminder", "Recordatorio de cita"),
    SELECTION_READY("fotos_listas_seleccion", "whatsapp.selection_ready", "Fotos listas para elegir"),
    WORK_READY("trabajo_listo", "whatsapp.work_ready", "Trabajo listo"),
    WORK_READY_WITH_BALANCE("trabajo_listo_con_saldo", "whatsapp.work_ready", "Trabajo listo (con saldo)"),
    PAYMENT_RECEIVED("pago_recibido", "whatsapp.payment_received", "Pago recibido"),
    BALANCE_REMINDER("recordatorio_saldo", null, "Recordatorio de saldo");

    private final String templateName;
    private final String feature;
    private final String label;

    WhatsAppMessageKind(String templateName, String feature, String label) {
        this.templateName = templateName;
        this.feature = feature;
        this.label = label;
    }

    public String templateName() {
        return templateName;
    }

    public String feature() {
        return feature;
    }

    public String label() {
        return label;
    }
}
