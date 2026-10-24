package com.happydev.prestockbackend.whatsapp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con WhatsApp Cloud API. El token y el id del número también pueden guardarse
 * cifrados en Ajustes → Integraciones (proveedor "whatsapp"); las variables de entorno mandan.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {

    public enum Mode {
        /** No se registra ni se envía nada. */
        OFF,
        /** Se registra cada aviso como "simulado", sin llamar a Meta. */
        SIMULATE,
        /** Envío real. */
        LIVE
    }

    private Mode mode = Mode.SIMULATE;

    /** Id del número emisor en Meta (no es el número de teléfono). */
    private String phoneNumberId = "";

    /** Token permanente del System User. */
    private String accessToken = "";

    /**
     * Secreto de la app de Meta: firma cada aviso del webhook (cabecera X-Hub-Signature-256).
     * Sin él, el webhook rechaza todo lo que llega.
     */
    private String appSecret = "";

    /** Texto que se le pide a Meta repetir al dar de alta la URL del webhook. */
    private String verifyToken = "";

    /**
     * Respuesta automática a quien escribe al número de la API (una vez cada 24 h por persona);
     * vacío = no responder. Se cambia con APP_WHATSAPP_AUTO_REPLY (aquí y no en
     * application.properties para no depender de la codificación de ese archivo).
     */
    private String autoReply = "¡Hola! Este número de Iridiscencia Film solo envía avisos automáticos y aquí"
            + " no podemos atenderte. Escríbenos al WhatsApp del estudio: +1 849-631-8221"
            + " (https://wa.me/18496318221).";

    private String graphUrl = "https://graph.facebook.com";

    private String apiVersion = "v23.0";

    /** Idioma con que se aprobaron las plantillas. */
    private String language = "es";

    /** Zona del estudio: las citas se guardan en hora local. */
    private String zone = "America/Santo_Domingo";

    /** Lugar de la cita cuando la orden no tiene uno propio; vacío = dirección de la empresa. */
    private String defaultPlace = "";

    /** Franja (hora local) en que salen los recordatorios automáticos. */
    private int reminderFromHour = 8;
    private int reminderUntilHour = 20;
}
