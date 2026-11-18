package com.happydev.prestockbackend.whatsapp;

import com.happydev.prestockbackend.dto.ServiceOrderDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.BookingRequestRepository;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.service.ServiceOrderPaymentEvent;
import com.happydev.prestockbackend.service.SelectionLinkCreatedEvent;
import com.happydev.prestockbackend.service.ServiceOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppTriggersTest {

    @Mock private WhatsAppOutbox outbox;
    @Mock private ServiceOrderRepository orderRepository;
    @Mock private ServiceOrderService orderService;
    @Mock private BookingRequestRepository bookingRepository;
    @Mock private CompanyConfigRepository companyRepository;

    private WhatsAppProperties props;
    private WhatsAppTriggers triggers;
    private ServiceOrder order;
    private Customer ana;

    /** 2026-10-24 10:00 en Santo Domingo (UTC-4). */
    private static final Instant NOW = Instant.parse("2026-10-24T14:00:00Z");

    @BeforeEach
    void setUp() {
        props = new WhatsAppProperties();
        props.setDefaultPlace("Plaza Dora");
        triggers = new WhatsAppTriggers(outbox, props, orderRepository, orderService, bookingRepository,
                companyRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        ana = new Customer();
        ana.setId(7L);
        ana.setFirstName("Ana");
        ana.setLastName("Pérez");
        ana.setPhoneNumber("809-555-1234");
        ana.setWhatsappOptIn(true);
        order = new ServiceOrder();
        order.setId(42L);
        order.setOrderNumber("OS-2026-00042");
        order.setTitle("Sesión de embarazo — Ana Pérez");
        order.setStatus(ServiceOrderStatus.OPEN);
        order.setCurrentStage("SCHEDULED");
        order.setCustomer(ana);
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));
        given(outbox.isActive(any())).willReturn(true);
        balance("0");
    }

    private void balance(String amount) {
        ServiceOrderDto dto = mock(ServiceOrderDto.class);
        given(dto.balance()).willReturn(new BigDecimal(amount));
        given(orderService.findById(42L)).willReturn(dto);
    }

    private WhatsAppOutbox.Notice captured() {
        ArgumentCaptor<WhatsAppOutbox.Notice> notice = ArgumentCaptor.forClass(WhatsAppOutbox.Notice.class);
        verify(outbox).enqueue(notice.capture());
        return notice.getValue();
    }

    @Test
    void citaFuturaAvisaConfirmacionConFechaYLugar() {
        order.setAppointmentDate(LocalDateTime.of(2026, 11, 15, 10, 0));
        triggers.orderChanged(42L);
        WhatsAppOutbox.Notice n = captured();
        assertEquals(WhatsAppMessageKind.APPOINTMENT_CONFIRMED, n.kind());
        assertEquals(List.of("Ana", "Sesión de embarazo", "el domingo 15 de noviembre a las 10:00 a. m.", "Plaza Dora"),
                n.params());
        assertEquals("appointment:42:2026-11-15T10:00", n.dedupeKey());
        assertTrue(n.consent());
    }

    @Test
    void citaPasadaOOrdenCanceladaNoAvisa() {
        order.setAppointmentDate(LocalDateTime.of(2026, 10, 24, 9, 0));
        triggers.orderChanged(42L);
        order.setAppointmentDate(LocalDateTime.of(2026, 11, 15, 10, 0));
        order.setStatus(ServiceOrderStatus.CANCELLED);
        triggers.orderChanged(42L);
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void listoConSaldoUsaLaPlantillaConSaldo() {
        order.setCurrentStage("READY");
        balance("2500");
        triggers.orderChanged(42L);
        WhatsAppOutbox.Notice n = captured();
        assertEquals(WhatsAppMessageKind.WORK_READY_WITH_BALANCE, n.kind());
        assertEquals(List.of("Ana", "OS-2026-00042", "RD$2,500.00"), n.params());
        assertEquals("ready:42", n.dedupeKey());
    }

    @Test
    void listoSinSaldoUsaLaPlantillaSimple() {
        order.setCurrentStage("READY");
        triggers.orderChanged(42L);
        assertEquals(WhatsAppMessageKind.WORK_READY, captured().kind());
    }

    @Test
    void clienteSinConsentimientoSeRegistraSinPermiso() {
        ana.setWhatsappOptIn(null);
        order.setCurrentStage("READY");
        triggers.orderChanged(42L);
        assertFalse(captured().consent());
    }

    @Test
    void pagoAvisaMontoYSaldoRestante() {
        balance("1500");
        triggers.onPayment(new ServiceOrderPaymentEvent(42L, 900L, new BigDecimal("3000")));
        WhatsAppOutbox.Notice n = captured();
        assertEquals(WhatsAppMessageKind.PAYMENT_RECEIVED, n.kind());
        assertEquals(List.of("Ana", "RD$3,000.00", "OS-2026-00042", "RD$1,500.00"), n.params());
        assertEquals("payment:900", n.dedupeKey());
    }

    @Test
    void usuarioDelPortalVaEnElBoton() {
        triggers.onSelectionLink(new SelectionLinkCreatedEvent(42L, "k3f9x2ab", "ana42"));
        WhatsAppOutbox.Notice n = captured();
        assertEquals(WhatsAppMessageKind.SELECTION_READY, n.kind());
        assertEquals(List.of("Ana", "Sesión de embarazo"), n.params());
        assertEquals("ana42", n.buttonParam());
        assertEquals("selection:k3f9x2ab", n.dedupeKey());
    }

    @Test
    void solicitudWebSoloAvisaSiMarcoLaCasilla() {
        BookingRequest booking = new BookingRequest();
        booking.setName("Luisa Gómez");
        booking.setPhone("829 555 0000");
        booking.setEventType("XV Años");
        booking.setEventStart(LocalDateTime.of(2026, 11, 20, 16, 30));
        given(bookingRepository.findById(5L)).willReturn(Optional.of(booking));

        triggers.bookingReceived(5L);
        verify(outbox, never()).enqueue(any());

        booking.setWhatsappOptIn(true);
        triggers.bookingReceived(5L);
        WhatsAppOutbox.Notice n = captured();
        assertEquals(List.of("Luisa", "XV Años", "el viernes 20 de noviembre a las 4:30 p. m."), n.params());
        assertEquals("booking:5", n.dedupeKey());
    }

    @Test
    void recordatorioSoloCitasDeMananaDentroDeLaFranja() {
        order.setAppointmentDate(LocalDateTime.of(2026, 10, 25, 9, 0));
        ServiceOrder pasadoManana = new ServiceOrder();
        pasadoManana.setId(43L);
        pasadoManana.setCustomer(ana);
        pasadoManana.setStatus(ServiceOrderStatus.OPEN);
        pasadoManana.setAppointmentDate(LocalDateTime.of(2026, 10, 26, 0, 30));
        given(orderRepository.findAppointmentsOverlapping(any(), any())).willReturn(List.of(order, pasadoManana));

        triggers.remindTomorrow();
        WhatsAppOutbox.Notice n = captured();
        assertEquals(WhatsAppMessageKind.APPOINTMENT_REMINDER, n.kind());
        assertEquals("mañana domingo 25 de octubre a las 9:00 a. m.", n.params().get(2));

        props.setReminderUntilHour(9); // ya pasó la franja (son las 10:00)
        triggers.remindTomorrow();
        verify(outbox, times(1)).enqueue(any());
    }

    @Test
    void recordatorioDeSaldoUnoPorDia() {
        balance("2500");
        given(outbox.enqueue(any())).willReturn(new WhatsAppMessage()).willReturn(null);
        assertNotNull(triggers.balanceReminder(42L, "admin"));
        assertEquals("balance-reminder:42:2026-10-24", captured().dedupeKey());
        assertThrows(IllegalStateException.class, () -> triggers.balanceReminder(42L, "admin"));

        balance("0");
        assertThrows(IllegalStateException.class, () -> triggers.balanceReminder(42L, "admin"));
    }
}
