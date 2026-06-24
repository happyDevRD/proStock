package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.service.CustomerService;
import com.happydev.prestockbackend.service.EmailService;
import com.happydev.prestockbackend.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;
    private final SaleService saleService;
    private final CustomerService customerService;

    /**
     * Envía la factura de una venta por correo.
     * Body (JSON, opcional): { "recipientEmail": "cliente@empresa.do" }
     * Si no se indica, usa el email registrado del cliente de la venta.
     */
    @PostMapping("/invoice/{saleId}")
    @PreAuthorize("hasAnyAuthority('invoice.view', 'ROLE_GESTOR')")
    public ResponseEntity<Map<String, String>> sendInvoice(
            @PathVariable Long saleId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        var sale = saleService.findSaleById(saleId)
                .orElseThrow(() -> new RuntimeException("Factura #" + saleId + " no encontrada."));

        // Determine recipient: explicit > customer email > error
        String recipient = body != null ? body.get("recipientEmail") : null;
        if (recipient == null || recipient.isBlank()) {
            if (sale.getCustomerId() != null) {
                recipient = customerService.findCustomerById(sale.getCustomerId())
                        .map(c -> c.getEmail())
                        .orElse(null);
            }
        }
        if (recipient == null || recipient.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No hay email de destino. Indica un email o asigna uno al cliente."
            ));
        }

        try {
            emailService.sendInvoiceEmail(sale, recipient);
            return ResponseEntity.ok(Map.of("message", "Factura enviada a " + recipient));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
