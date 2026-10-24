package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.WhatsAppInboundMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessageStatus;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.WhatsAppInboundMessageRepository;
import com.happydev.prestockbackend.repository.WhatsAppMessageRepository;
import com.happydev.prestockbackend.service.StudioNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppWebhookServiceTest {

    @Mock private WhatsAppClient client;
    @Mock private WhatsAppInboundMessageRepository inboundRepository;
    @Mock private WhatsAppMessageRepository messageRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private StudioNotifier notifier;

    private WhatsAppProperties props;
    private WhatsAppWebhookService service;

    @BeforeEach
    void setUp() {
        props = new WhatsAppProperties();
        props.setPhoneNumberId("1357718120763107");
        service = new WhatsAppWebhookService(props, client, inboundRepository, messageRepository,
                customerRepository, notifier, new ObjectMapper());
        given(inboundRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(customerRepository.findFirstByPhoneDigits(any())).willReturn(Optional.empty());
        given(client.sendText(any(), any())).willReturn(new WhatsAppClient.SendResult(true, "wamid.reply", null));
    }

    private static byte[] payload(String numberId, String valueBody) {
        return payload("messages", numberId, valueBody);
    }

    private static byte[] payload(String field, String numberId, String valueBody) {
        return ("""
                {"object":"whatsapp_business_account","entry":[{"id":"1859146558594612","changes":[{"field":"%s",
                 "value":{"messaging_product":"whatsapp","metadata":{"display_phone_number":"18295468012","phone_number_id":"%s"},
                 %s}}]}]}""").formatted(field, numberId, valueBody).getBytes(StandardCharsets.UTF_8);
    }

    private static final String TEXT_MESSAGE = """
            "contacts":[{"profile":{"name":"Ana Pérez"},"wa_id":"18497639736"}],
            "messages":[{"from":"18497639736","id":"wamid.IN1","timestamp":"1761264000","type":"text","text":{"body":"Hola"}}]""";

    @Test
    void guardaElMensajeConNombreYClienteYAvisaAlEstudio() {
        Customer ana = new Customer();
        ana.setFirstName("Ana");
        ana.setLastName("Pérez");
        given(customerRepository.findFirstByPhoneDigits("8497639736")).willReturn(Optional.of(ana));

        service.process(payload("1357718120763107", TEXT_MESSAGE));

        ArgumentCaptor<WhatsAppInboundMessage> saved = ArgumentCaptor.forClass(WhatsAppInboundMessage.class);
        verify(inboundRepository, atLeastOnce()).save(saved.capture());
        WhatsAppInboundMessage message = saved.getValue();
        assertEquals("wamid.IN1", message.getWamid());
        assertEquals("18497639736", message.getFromPhone());
        assertEquals("Ana Pérez", message.getProfileName());
        assertEquals("text", message.getType());
        assertEquals("Hola", message.getBody());
        assertEquals(Instant.ofEpochSecond(1761264000), message.getSentAt());
        assertSame(ana, message.getCustomer());
        verify(notifier).send(contains("Ana Pérez (+18497639736): Hola"));
    }

    @Test
    void respondeAutomaticamenteUnaVezCada24Horas() {
        service.process(payload("1357718120763107", TEXT_MESSAGE));
        verify(client).sendText(eq("18497639736"), contains("849-631-8221"));

        given(inboundRepository.existsByFromPhoneAndAutoRepliedTrueAndReceivedAtAfter(eq("18497639736"), any()))
                .willReturn(true);
        service.process(payload("1357718120763107", TEXT_MESSAGE.replace("wamid.IN1", "wamid.IN2")));
        verify(client, times(1)).sendText(any(), any());
    }

    @Test
    void noRespondeSiLaRespuestaAutomaticaEstaVacia() {
        props.setAutoReply("");
        service.process(payload("1357718120763107", TEXT_MESSAGE));
        verify(client, never()).sendText(any(), any());
    }

    @Test
    void ignoraAvisosRepetidos() {
        given(inboundRepository.existsByWamid("wamid.IN1")).willReturn(true);
        service.process(payload("1357718120763107", TEXT_MESSAGE));
        verify(inboundRepository, never()).save(any());
        verify(notifier, never()).send(any());
    }

    @Test
    void ignoraMensajesDeOtroNumero() {
        service.process(payload("999", TEXT_MESSAGE));
        verify(inboundRepository, never()).save(any());
    }

    @Test
    void guardaFotosConPieYIdDeMedia() {
        service.process(payload("1357718120763107", """
                "messages":[{"from":"18497639736","id":"wamid.IMG","timestamp":"1761264000","type":"image",
                 "image":{"id":"MEDIA1","mime_type":"image/jpeg","caption":"Esta pose"}}]"""));
        ArgumentCaptor<WhatsAppInboundMessage> saved = ArgumentCaptor.forClass(WhatsAppInboundMessage.class);
        verify(inboundRepository, atLeastOnce()).save(saved.capture());
        assertEquals("MEDIA1", saved.getValue().getMediaId());
        assertEquals("image/jpeg", saved.getValue().getMediaMime());
        assertEquals("Esta pose", saved.getValue().getBody());
        assertNull(saved.getValue().getProfileName());
    }

    @Test
    void actualizaEntregadoLeidoYFallido() {
        WhatsAppMessage sent = new WhatsAppMessage();
        sent.setStatus(WhatsAppMessageStatus.SENT);
        given(messageRepository.findByWamid("wamid.OUT")).willReturn(Optional.of(sent));

        service.process(payload("1357718120763107", """
                "statuses":[{"id":"wamid.OUT","status":"delivered","timestamp":"1761264000","recipient_id":"18497639736"}]"""));
        assertEquals(Instant.ofEpochSecond(1761264000), sent.getDeliveredAt());

        service.process(payload("1357718120763107", """
                "statuses":[{"id":"wamid.OUT","status":"read","timestamp":"1761264100","recipient_id":"18497639736"}]"""));
        assertEquals(Instant.ofEpochSecond(1761264000), sent.getDeliveredAt());
        assertEquals(Instant.ofEpochSecond(1761264100), sent.getReadAt());

        service.process(payload("1357718120763107", """
                "statuses":[{"id":"wamid.OUT","status":"failed","timestamp":"1761264200","recipient_id":"18497639736",
                 "errors":[{"code":131026,"title":"Message undeliverable","error_data":{"details":"No es usuario de WhatsApp"}}]}]"""));
        assertEquals(WhatsAppMessageStatus.FAILED, sent.getStatus());
        assertEquals("131026: No es usuario de WhatsApp", sent.getStatusDetail());
        verify(messageRepository, times(3)).save(sent);
    }

    @Test
    void leeEstadosDelEventoMessageStatuses() {
        WhatsAppMessage sent = new WhatsAppMessage();
        sent.setStatus(WhatsAppMessageStatus.SENT);
        given(messageRepository.findByWamid("wamid.OUT")).willReturn(Optional.of(sent));

        service.process(payload("message_statuses", "1357718120763107", """
                "statuses":[{"id":"wamid.OUT","status":"delivered","timestamp":"1761264000","recipient_id":"18497639736"}]"""));
        assertEquals(Instant.ofEpochSecond(1761264000), sent.getDeliveredAt());
    }

    @Test
    void jsonInvalidoNoRevienta() {
        assertDoesNotThrow(() -> service.process("no es json".getBytes(StandardCharsets.UTF_8)));
    }
}
