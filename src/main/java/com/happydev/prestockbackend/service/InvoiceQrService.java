package com.happydev.prestockbackend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@Service
public class InvoiceQrService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public String buildPayloadUrl(String rncEmisor,
                                  String encf,
                                  String rncComprador,
                                  LocalDateTime fechaEmision,
                                  String montoTotal,
                                  LocalDateTime fechaFirma,
                                  String codigoSeguridad) {
        return "https://ecf.dgii.gov.do/consulta?"
                + "RncEmisor=" + safe(rncEmisor)
                + "&ENCF=" + safe(encf)
                + "&RncComprador=" + safe(rncComprador)
                + "&FechaEmision=" + formatDate(fechaEmision)
                + "&MontoTotal=" + safe(montoTotal)
                + "&FechaFirma=" + formatDate(fechaFirma)
                + "&CodigoSeguridad=" + safe(codigoSeguridad);
    }

    public String generateBase64Qr(String payload) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 280, 280, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (WriterException | java.io.IOException ex) {
            throw new IllegalStateException("No se pudo generar QR para la factura", ex);
        }
    }

    private String formatDate(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DATE_FORMAT);
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
