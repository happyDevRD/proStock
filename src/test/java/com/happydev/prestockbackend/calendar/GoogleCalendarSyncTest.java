package com.happydev.prestockbackend.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.CalendarBlock;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderItem;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.repository.CalendarBlockRepository;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.ServiceOrderItemRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.service.FeatureConfigService;
import com.happydev.prestockbackend.service.IntegrationCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoogleCalendarSyncTest {

    private final GoogleCalendarClient client = mock(GoogleCalendarClient.class);
    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final ServiceOrderItemRepository items = mock(ServiceOrderItemRepository.class);
    private final CalendarBlockRepository blocks = mock(CalendarBlockRepository.class);
    private final CompanyConfigRepository company = mock(CompanyConfigRepository.class);
    private final FeatureConfigService features = mock(FeatureConfigService.class);
    private final IntegrationCredentialService credentials = mock(IntegrationCredentialService.class);
    private GoogleCalendarSync sync;
    private ServiceOrder order;

    @BeforeEach
    void setUp() {
        sync = new GoogleCalendarSync(client, orders, items, blocks, company, features, credentials,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), "https://prostock.iridiscencia.com:8443/");
        when(features.isEnabled(GoogleCalendarSync.FEATURE)).thenReturn(true);
        when(credentials.get("google_calendar", "service_account_json")).thenReturn(Optional.of("{json}"));
        when(credentials.get("google_calendar", "calendar_id")).thenReturn(Optional.of("estudio@group.calendar.google.com"));
        CompanyConfig cfg = new CompanyConfig();
        cfg.setDireccion("Plaza Dora, 2do nivel, San Pedro de Macorís");
        when(company.findAll()).thenReturn(List.of(cfg));

        Customer c = new Customer();
        c.setFirstName("Ana");
        c.setLastName("Martínez");
        Employee photographer = new Employee();
        photographer.setId(3L);
        photographer.setNombreCompleto("Jonathan");
        order = new ServiceOrder();
        order.setId(34L);
        order.setOrderNumber("OS-2026-00034");
        order.setTitle("sesión prueba");
        order.setCustomer(c);
        order.setPhotographer(photographer);
        order.setStatus(ServiceOrderStatus.OPEN);
        order.setAppointmentDate(LocalDateTime.of(2026, 11, 21, 14, 30));
        order.setAppointmentEnd(LocalDateTime.of(2026, 11, 21, 15, 30));
        order.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 13, 20));
        Product combo = new Product();
        combo.setName("Combo XV Años 1");
        Product extra = new Product();
        extra.setName("Foto Extra Digital");
        ServiceOrderItem i1 = new ServiceOrderItem();
        i1.setProduct(extra);
        ServiceOrderItem i2 = new ServiceOrderItem();
        i2.setProduct(combo);
        when(items.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(34L)).thenReturn(List.of(i1, i2));
    }

    @SuppressWarnings("unchecked")
    @Test
    void ordenConCitaCreaOActualizaSuEventoConClientePaqueteYEquipo() {
        when(orders.findCalendarPending(any(), any(), any())).thenReturn(List.of(order));
        when(blocks.findCalendarPending(any(), any(), any())).thenReturn(List.of());

        sync.syncPending();

        ArgumentCaptor<Map<String, Object>> event = ArgumentCaptor.forClass(Map.class);
        verify(client).upsert(eq("{json}"), eq("estudio@group.calendar.google.com"), eq("prostockos34"), event.capture());
        Map<String, Object> e = event.getValue();
        assertEquals("Ana Martínez · Combo XV Años 1", e.get("summary"), "el paquete, no la foto extra");
        assertEquals(Map.of("dateTime", "2026-11-21T14:30:00", "timeZone", "America/Santo_Domingo"), e.get("start"));
        assertEquals(Map.of("dateTime", "2026-11-21T15:30:00", "timeZone", "America/Santo_Domingo"), e.get("end"));
        assertEquals("Plaza Dora, 2do nivel, San Pedro de Macorís", e.get("location"));
        String description = (String) e.get("description");
        assertTrue(description.contains("Fotógrafo: Jonathan"));
        assertTrue(description.contains("Editor: sin asignar"));
        assertTrue(description.contains("https://prostock.iridiscencia.com:8443/service_orders/34"));
        assertFalse(description.matches("(?s).*\\d{3}-?\\d{3}-?\\d{4}.*"), "sin teléfono del cliente");
        assertEquals("4", e.get("colorId"), "color fijo por fotógrafo");
        verify(orders).markCalendarSynced(34L, order.getUpdatedAt());
    }

    @Test
    void ordenCanceladaOSinCitaBorraElEvento() {
        order.setStatus(ServiceOrderStatus.CANCELLED);
        when(orders.findCalendarPending(any(), any(), any())).thenReturn(List.of(order));
        when(blocks.findCalendarPending(any(), any(), any())).thenReturn(List.of());

        sync.syncPending();

        verify(client).delete("{json}", "estudio@group.calendar.google.com", "prostockos34");
        verify(client, never()).upsert(any(), any(), any(), any());
        verify(orders).markCalendarSynced(34L, order.getUpdatedAt());
    }

    @Test
    void siGoogleFallaSeGuardaElErrorYSeReintentaLuego() {
        when(orders.findCalendarPending(any(), any(), any())).thenReturn(List.of(order));
        when(blocks.findCalendarPending(any(), any(), any())).thenReturn(List.of());
        doThrow(new GoogleCalendarClient.CalendarException("Sin conexión con Google", true))
                .when(client).upsert(any(), any(), any(), any());

        sync.syncPending();

        verify(orders, never()).markCalendarSynced(any(), any());
        verify(orders).markCalendarFailed(eq(34L), any(), contains("Sin conexión"));
        assertTrue(sync.status().lastError().contains("OS-2026-00034"));
    }

    @Test
    void apagadoOSinCredencialesNoHaceNada() {
        when(features.isEnabled(GoogleCalendarSync.FEATURE)).thenReturn(false);
        sync.syncPending();
        when(features.isEnabled(GoogleCalendarSync.FEATURE)).thenReturn(true);
        when(credentials.get("google_calendar", "calendar_id")).thenReturn(Optional.empty());
        sync.syncPending();
        verifyNoInteractions(client);
        verify(orders, never()).findCalendarPending(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void bloqueoDeDiaCompletoVaComoEventoDeTodoElDia() {
        CalendarBlock b = new CalendarBlock();
        b.setId(9L);
        b.setReason("Vacaciones");
        b.setAllDay(true);
        b.setStartsAt(LocalDateTime.of(2026, 12, 24, 0, 0));
        b.setEndsAt(LocalDateTime.of(2026, 12, 26, 0, 0));
        when(orders.findCalendarPending(any(), any(), any())).thenReturn(List.of());
        when(blocks.findCalendarPending(any(), any(), any())).thenReturn(List.of(b));

        sync.syncPending();

        ArgumentCaptor<Map<String, Object>> event = ArgumentCaptor.forClass(Map.class);
        verify(client).upsert(any(), any(), eq("prostockbl9"), event.capture());
        assertEquals("Bloqueado (todo el estudio): Vacaciones", event.getValue().get("summary"));
        assertEquals(Map.of("date", "2026-12-24"), event.getValue().get("start"));
        assertEquals(Map.of("date", "2026-12-26"), event.getValue().get("end"), "fin exclusivo, como pide Google");
        verify(blocks).markCalendarSynced(eq(9L), any());
    }

    @Test
    void laLlaveDeLaCuentaDeServicioSeLeeYDaElCorreo() throws Exception {
        var kp = KeyPairGenerator.getInstance("RSA");
        kp.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "type", "service_account", "client_email", "prostock@proyecto.iam.gserviceaccount.com",
                "private_key", pem, "token_uri", "https://oauth2.googleapis.com/token"));
        var sa = new GoogleCalendarClient(new ObjectMapper()).parse(json);
        assertEquals("prostock@proyecto.iam.gserviceaccount.com", sa.clientEmail());
        assertNotNull(sa.key());
        assertThrows(GoogleCalendarClient.CalendarException.class,
                () -> new GoogleCalendarClient(new ObjectMapper()).parse("{\"client_email\":\"x\"}"));
    }
}
