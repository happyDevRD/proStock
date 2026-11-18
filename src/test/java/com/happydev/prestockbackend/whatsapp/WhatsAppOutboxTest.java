package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.WhatsAppMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessageKind;
import com.happydev.prestockbackend.entity.WhatsAppMessageStatus;
import com.happydev.prestockbackend.repository.WhatsAppMessageRepository;
import com.happydev.prestockbackend.service.FeatureConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppOutboxTest {

    @Mock private WhatsAppMessageRepository repository;
    @Mock private WhatsAppClient client;
    @Mock private FeatureConfigService features;

    private final Instant now = Instant.parse("2026-10-24T14:00:00Z");
    private WhatsAppProperties props;
    private WhatsAppOutbox outbox;

    @BeforeEach
    void setUp() {
        props = new WhatsAppProperties();
        props.setMode(WhatsAppProperties.Mode.LIVE);
        outbox = new WhatsAppOutbox(repository, client, props, features, new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC));
        given(features.isEnabled(anyString())).willReturn(true);
        given(repository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private static WhatsAppOutbox.Notice notice(boolean consent, String phone, String key) {
        return new WhatsAppOutbox.Notice(WhatsAppMessageKind.WORK_READY, phone, "Ana Pérez",
                List.of("Ana", "OS-2026-00042"), null, consent, null, null, null, key, null);
    }

    @Test
    void conConsentimientoYTelefonoQuedaPendienteConElNumeroNormalizado() {
        WhatsAppMessage m = outbox.enqueue(notice(true, "809-555-1234", "ready:42"));
        assertEquals(WhatsAppMessageStatus.PENDING, m.getStatus());
        assertEquals("18095551234", m.getRecipientPhone());
        assertEquals("trabajo_listo", m.getTemplateName());
        assertEquals("[\"Ana\",\"OS-2026-00042\"]", m.getBodyParams());
    }

    @Test
    void sinConsentimientoOSinTelefonoQuedaOmitido() {
        assertEquals(WhatsAppMessageStatus.SKIPPED, outbox.enqueue(notice(false, "8095551234", "a")).getStatus());
        WhatsAppMessage sinTelefono = outbox.enqueue(notice(true, "555", "b"));
        assertEquals(WhatsAppMessageStatus.SKIPPED, sinTelefono.getStatus());
        assertEquals("Sin un teléfono válido", sinTelefono.getStatusDetail());
    }

    @Test
    void noRepiteNiRegistraSiElModuloEstaApagado() {
        given(repository.existsByDedupeKey("ready:42")).willReturn(true);
        assertNull(outbox.enqueue(notice(true, "8095551234", "ready:42")));

        given(features.isEnabled("whatsapp.work_ready")).willReturn(false);
        assertNull(outbox.enqueue(notice(true, "8095551234", "ready:43")));

        props.setMode(WhatsAppProperties.Mode.OFF);
        given(features.isEnabled(anyString())).willReturn(true);
        assertNull(outbox.enqueue(notice(true, "8095551234", "ready:44")));
        verify(repository, never()).saveAndFlush(any());
    }

    private WhatsAppMessage pending() {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setKind(WhatsAppMessageKind.SELECTION_READY);
        m.setTemplateName("fotos_listas_seleccion");
        m.setLanguage("es");
        m.setRecipientPhone("18095551234");
        m.setBodyParams("[\"Ana\",\"Boda\"]");
        m.setButtonParam("tok123");
        m.setStatus(WhatsAppMessageStatus.PENDING);
        return m;
    }

    @Test
    void enviaYGuardaElIdDeMeta() {
        WhatsAppMessage m = pending();
        given(client.sendTemplate("18095551234", "fotos_listas_seleccion", "es", List.of("Ana", "Boda"), "tok123"))
                .willReturn(new WhatsAppClient.SendResult(true, "wamid.X", null));
        outbox.send(m);
        assertEquals(WhatsAppMessageStatus.SENT, m.getStatus());
        assertEquals("wamid.X", m.getWamid());
        assertEquals(now, m.getSentAt());
        assertEquals(1, m.getAttempts());
    }

    @Test
    void errorPasajeroReintentaMasTardeYAlFinalFalla() {
        WhatsAppMessage m = pending();
        given(client.sendTemplate(any(), any(), any(), any(), any()))
                .willReturn(new WhatsAppClient.SendResult(false, null, "sin conexión", true));
        outbox.send(m);
        assertEquals(WhatsAppMessageStatus.PENDING, m.getStatus());
        assertEquals(now.plusSeconds(120), m.getNextAttemptAt());

        m.setAttempts(WhatsAppOutbox.MAX_ATTEMPTS - 1);
        outbox.send(m);
        assertEquals(WhatsAppMessageStatus.FAILED, m.getStatus());
    }

    @Test
    void errorDefinitivoFallaSinReintentar() {
        WhatsAppMessage m = pending();
        given(client.sendTemplate(any(), any(), any(), any(), any()))
                .willReturn(new WhatsAppClient.SendResult(false, null, "132001: plantilla no existe", false));
        outbox.send(m);
        assertEquals(WhatsAppMessageStatus.FAILED, m.getStatus());
        assertEquals("132001: plantilla no existe", m.getStatusDetail());
    }

    @Test
    void enSimulacionNoLlamaAMeta() {
        props.setMode(WhatsAppProperties.Mode.SIMULATE);
        WhatsAppMessage m = pending();
        outbox.send(m);
        assertEquals(WhatsAppMessageStatus.SIMULATED, m.getStatus());
        verifyNoInteractions(client);
    }

    @Test
    void elFormularioDeDatosSaleConBotonDeFlowYNombrePrecargado() {
        var dataRequests = mock(com.happydev.prestockbackend.repository.CustomerDataRequestRepository.class);
        given(dataRequests.findNamesByToken("flowtok")).willReturn(java.util.List.<Object[]>of(new Object[]{"Ana", "-"}));
        outbox.setDataRequests(dataRequests);
        given(client.sendFlowTemplate(any(), any(), any(), any(), any(), any()))
                .willReturn(new WhatsAppClient.SendResult(true, "wamid.F", null));
        WhatsAppMessage m = pending();
        m.setKind(WhatsAppMessageKind.CUSTOMER_DATA_REQUEST);
        m.setTemplateName("datos_cliente");
        m.setBodyParams("[\"Ana\"]");
        m.setButtonParam("flowtok");

        outbox.send(m);

        verify(client).sendFlowTemplate("18095551234", "datos_cliente", "es", List.of("Ana"), "flowtok",
                java.util.Map.of("first_name", "Ana", "last_name", ""));
        verify(client, never()).sendTemplate(any(), any(), any(), any(), any());
        assertEquals(WhatsAppMessageStatus.SENT, m.getStatus());
    }
}
