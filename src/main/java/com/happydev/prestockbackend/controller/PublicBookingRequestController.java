package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.BookingRequestDtos.PublicAvailability;
import com.happydev.prestockbackend.dto.BookingRequestDtos.PublicBookingCreated;
import com.happydev.prestockbackend.dto.BookingRequestDtos.PublicBookingRequest;
import com.happydev.prestockbackend.service.AppointmentScheduleService;
import com.happydev.prestockbackend.service.BookingRequestService;
import com.happydev.prestockbackend.service.ServiceOrderAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Formulario de reservas de la web (público, sin sesión). Llega por el nginx de
 * iridiscencia.com ({@code /api/solicitudes} → {@code /api/public/booking-requests}),
 * detrás de Cloudflare: la IP real viene en {@code CF-Connecting-IP}.
 */
@RestController
@RequestMapping("/api/public/booking-requests")
public class PublicBookingRequestController {

    private final BookingRequestService service;
    private final ServiceOrderAccessGuard accessGuard;
    private final AppointmentScheduleService scheduleService;

    public PublicBookingRequestController(BookingRequestService service, ServiceOrderAccessGuard accessGuard,
                                          AppointmentScheduleService scheduleService) {
        this.service = service;
        this.accessGuard = accessGuard;
        this.scheduleService = scheduleService;
    }

    /** Horarios ocupados entre {@code from} y {@code to} (máx. 62 días), sin datos de nadie. */
    @GetMapping("/availability")
    public ResponseEntity<PublicAvailability> availability(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PublicAvailability(scheduleService.publicBusy(from, to)));
    }

    @PostMapping
    public ResponseEntity<PublicBookingCreated> submit(@Valid @RequestBody PublicBookingRequest body,
                                                       HttpServletRequest request) {
        accessGuard.requireEnabled();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(body, clientIp(request)));
    }

    private static String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
