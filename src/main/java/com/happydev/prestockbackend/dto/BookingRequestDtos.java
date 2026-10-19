package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Solicitudes de reserva desde la web. Ver documentacion/solicitudes-web.md. */
public final class BookingRequestDtos {

    private BookingRequestDtos() {
    }

    /** Lo que envía el formulario de la web. {@code website} es un campo trampa: las personas lo dejan vacío. */
    public record PublicBookingRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(max = 40) String telefono,
            @Email @Size(max = 160) String email,
            @Size(max = 120) String tipoEvento,
            LocalDate fecha,
            @Size(max = 2000) String mensaje,
            String website
    ) {}

    public record PublicBookingCreated(String code) {}

    public record SameDayOrder(Long id, String orderNumber, String title, String customerName,
                               LocalDateTime appointmentDate) {}

    public record MatchedCustomer(Long id, String name) {}

    public record BookingRequestView(
            Long id,
            String code,
            String name,
            String phone,
            String email,
            String eventType,
            LocalDate eventDate,
            String message,
            String status,
            LocalDateTime createdAt,
            /** Cliente que ya existe con ese teléfono o correo (se usará al convertir). */
            MatchedCustomer matchedCustomer,
            /** Otras órdenes agendadas ese día. */
            List<SameDayOrder> sameDayOrders,
            Long serviceOrderId,
            String serviceOrderNumber,
            String handledBy,
            LocalDateTime handledAt
    ) {}

    public record BookingRequestCount(long pending) {}

    public record ConvertBookingRequest(
            @NotBlank @Size(max = 255) String title,
            LocalDateTime appointmentDate
    ) {}
}
