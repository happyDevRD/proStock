package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.CompanyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Servicio de envío de correos electrónicos.
 * Construye un JavaMailSender dinámico desde las credenciales de la empresa
 * (provider "email", claves: host / port / username / password / from_name / from_address).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final IntegrationCredentialService credentials;
    private final CompanyConfigService companyConfigService;

    /**
     * Envía la factura de una venta al email indicado.
     * @throws IllegalStateException si no hay SMTP configurado o la venta no tiene email de cliente.
     */
    public void sendInvoiceEmail(SaleDto sale, String recipientEmail) {
        var sender = buildSender();
        var company = companyConfigService.findCompanyConfig().orElse(null);
        String companyName = company != null ? (company.getNombreComercial() != null ? company.getNombreComercial() : company.getRazonSocial()) : "ProStock";

        var from = credentials.get("email", "from_address").orElse(null);
        var fromName = credentials.get("email", "from_name").orElse(companyName);

        if (from == null || from.isBlank()) {
            throw new IllegalStateException("No hay dirección de remitente configurada en Ajustes > Integraciones > Email.");
        }

        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Factura #%s — %s".formatted(sale.getId(), companyName));
            helper.setText(buildHtmlBody(sale, company), true);

            sender.send(message);
            log.info("Factura #{} enviada a {}", sale.getId(), recipientEmail);
        } catch (Exception e) {
            log.error("Error enviando factura #{} a {}: {}", sale.getId(), recipientEmail, e.getMessage());
            throw new RuntimeException("No se pudo enviar el correo: " + e.getMessage(), e);
        }
    }

    private JavaMailSenderImpl buildSender() {
        var host     = credentials.get("email", "host").orElse(null);
        var portStr  = credentials.get("email", "port").orElse("587");
        var username = credentials.get("email", "username").orElse(null);
        var password = credentials.get("email", "password").orElse(null);

        if (host == null || host.isBlank()) {
            throw new IllegalStateException("SMTP no configurado. Ve a Ajustes > Seguridad > Credenciales de Integraciones > Email / SMTP.");
        }

        int port;
        try { port = Integer.parseInt(portStr.trim()); } catch (NumberFormatException e) { port = 587; }

        var sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(port);
        if (username != null && !username.isBlank()) sender.setUsername(username.trim());
        if (password != null && !password.isBlank()) sender.setPassword(password.trim());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", username != null && !username.isBlank() ? "true" : "false");
        props.put("mail.smtp.starttls.enable", port == 587 ? "true" : "false");
        props.put("mail.smtp.ssl.enable", port == 465 ? "true" : "false");
        props.put("mail.debug", "false");

        return sender;
    }

    private String buildHtmlBody(SaleDto sale, CompanyConfig company) {
        String companyName = company != null
                ? (company.getNombreComercial() != null ? company.getNombreComercial() : company.getRazonSocial())
                : "ProStock";
        String ncf = sale.getNcf() != null ? sale.getNcf() : "—";

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f8fafc;font-family:Inter,system-ui,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;margin:32px auto;background:#fff;border-radius:12px;border:1px solid #e2e8f0;overflow:hidden">
                <tr><td style="background:#0f766e;padding:24px 32px">
                  <h1 style="margin:0;color:#fff;font-size:20px;font-weight:700">%s</h1>
                  <p style="margin:4px 0 0;color:#99f6e4;font-size:13px">Comprobante fiscal electrónico</p>
                </td></tr>
                <tr><td style="padding:28px 32px">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td style="padding:8px 0;border-bottom:1px solid #f1f5f9">
                        <span style="font-size:12px;color:#94a3b8;font-weight:600;text-transform:uppercase">NCF</span>
                        <span style="float:right;font-size:13px;font-weight:700;color:#0f172a;font-family:monospace">%s</span>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:8px 0;border-bottom:1px solid #f1f5f9">
                        <span style="font-size:12px;color:#94a3b8;font-weight:600;text-transform:uppercase">Total</span>
                        <span style="float:right;font-size:15px;font-weight:800;color:#0f766e">RD$ %,.2f</span>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:8px 0">
                        <span style="font-size:12px;color:#94a3b8;font-weight:600;text-transform:uppercase">Estado</span>
                        <span style="float:right;font-size:13px;font-weight:700;color:#0f172a">%s</span>
                      </td>
                    </tr>
                  </table>
                  <p style="margin:24px 0 0;font-size:13px;color:#64748b;line-height:1.6">
                    Este es un comprobante generado por %s. Cualquier reclamación dentro de los 30 días de la compra.
                  </p>
                </td></tr>
                <tr><td style="background:#f8fafc;padding:16px 32px;text-align:center">
                  <p style="margin:0;font-size:11px;color:#94a3b8">Generado por ProStock · Sistema de gestión empresarial</p>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
                companyName,
                ncf,
                sale.getMontoTotal() != null ? sale.getMontoTotal().doubleValue() : 0.0,
                sale.getStatus() != null ? sale.getStatus().name() : "—",
                companyName
        );
    }
}
