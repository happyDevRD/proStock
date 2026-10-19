package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.BookingRequestDtos.*;
import com.happydev.prestockbackend.dto.CreateServiceOrderRequest;
import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.dto.QuickCustomerRequest;
import com.happydev.prestockbackend.dto.ServiceOrderDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.BookingRequestRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingRequestServiceTest {

    @Mock private BookingRequestRepository repository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerService customerService;
    @Mock private ServiceOrderRepository orderRepository;
    @Mock private ServiceOrderService serviceOrderService;
    @Mock private StudioNotifier notifier;

    private BookingRequestService service;
    private final LocalDate fecha = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("America/Santo_Domingo"));
        service = new BookingRequestService(repository, customerRepository, customerService, orderRepository,
                serviceOrderService, notifier, clock);
        given(repository.save(any())).willAnswer(inv -> {
            BookingRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(12L);
            return r;
        });
        given(repository.findByPhoneAndCreatedAtAfterOrderByCreatedAtDesc(any(), any())).willReturn(List.of());
        given(customerRepository.findFirstByEmailIgnoreCase(any())).willReturn(Optional.empty());
        given(customerRepository.findFirstByPhoneDigits(any())).willReturn(Optional.empty());
        given(orderRepository.findByAppointmentDateBetweenAndStatusNotOrderByAppointmentDateAsc(any(), any(), any()))
                .willReturn(List.of());
    }

    private PublicBookingRequest form(String website) {
        return new PublicBookingRequest("Eleazar Garcia Sierra", "+18097572408", "me@elgarcia.org",
                "Graduación universitaria", fecha, "holaaaa", website);
    }

    @Test
    void submitSavesAndNotifies() {
        PublicBookingCreated created = service.submit(form(null), "1.2.3.4");

        assertEquals("S-0012", created.code());
        ArgumentCaptor<BookingRequest> saved = ArgumentCaptor.forClass(BookingRequest.class);
        verify(repository).save(saved.capture());
        assertEquals("Graduación universitaria", saved.getValue().getEventType());
        assertEquals(fecha, saved.getValue().getEventDate());
        assertEquals(BookingRequestStatus.NEW, saved.getValue().getStatus());
        verify(notifier).send(contains("S-0012"));
    }

    @Test
    void honeypotIsSilentlyIgnored() {
        PublicBookingCreated created = service.submit(form("http://spam"), "1.2.3.4");

        assertNotNull(created.code());
        verify(repository, never()).save(any());
        verify(notifier, never()).send(any());
    }

    @Test
    void duplicateWithinTheHourReturnsSameCode() {
        BookingRequest prev = new BookingRequest();
        prev.setId(7L);
        prev.setEventType("Graduación universitaria");
        prev.setEventDate(fecha);
        given(repository.findByPhoneAndCreatedAtAfterOrderByCreatedAtDesc(eq("+18097572408"), any()))
                .willReturn(List.of(prev));

        assertEquals("S-0007", service.submit(form(null), "1.2.3.4").code());
        verify(repository, never()).save(any());
    }

    @Test
    void rateLimitPerIp() {
        for (int i = 0; i < BookingRequestService.MAX_PER_IP_PER_HOUR; i++) {
            service.submit(new PublicBookingRequest("Ana", "80955500" + i + "0", null, "XV", null, null, null), "9.9.9.9");
        }
        assertThrows(BookingRequestService.TooManyBookingRequestsException.class,
                () -> service.submit(form(null), "9.9.9.9"));
        // Otra IP sigue pudiendo
        assertDoesNotThrow(() -> service.submit(form(null), "8.8.8.8"));
    }

    @Test
    void rejectsPhoneWithoutDigits() {
        assertThrows(IllegalArgumentException.class, () -> service.submit(
                new PublicBookingRequest("Ana", "abc", null, null, null, null, null), "1.1.1.1"));
    }

    private BookingRequest pending() {
        BookingRequest r = new BookingRequest();
        r.setId(12L);
        r.setName("Eleazar Garcia Sierra");
        r.setPhone("+18097572408");
        r.setEmail("me@elgarcia.org");
        r.setEventType("Graduación universitaria");
        r.setEventDate(fecha);
        r.setMessage("holaaaa");
        given(repository.findById(12L)).willReturn(Optional.of(r));
        return r;
    }

    private void orderCreated() {
        ServiceOrderDto dto = mock(ServiceOrderDto.class);
        given(dto.id()).willReturn(40L);
        given(serviceOrderService.create(any(), any())).willReturn(dto);
        ServiceOrder order = new ServiceOrder();
        order.setId(40L);
        order.setOrderNumber("OS-2026-00040");
        given(orderRepository.getReferenceById(40L)).willReturn(order);
    }

    @Test
    void convertReusesCustomerMatchedByPhone() {
        BookingRequest r = pending();
        Customer existing = new Customer();
        existing.setId(5L);
        existing.setFirstName("Eleazar");
        existing.setLastName("Garcia");
        given(customerRepository.findFirstByPhoneDigits("8097572408")).willReturn(Optional.of(existing));
        given(customerRepository.getReferenceById(5L)).willReturn(existing);
        orderCreated();

        BookingRequestView view = service.convert(12L,
                new ConvertBookingRequest("Graduación — Eleazar", fecha.atTime(10, 0)), "admin");

        ArgumentCaptor<CreateServiceOrderRequest> req = ArgumentCaptor.forClass(CreateServiceOrderRequest.class);
        verify(serviceOrderService).create(req.capture(), eq("admin"));
        assertEquals(5L, req.getValue().customerId());
        assertEquals(fecha.atTime(10, 0), req.getValue().appointmentDate());
        assertTrue(req.getValue().internalNotes().contains("S-0012"));
        assertTrue(req.getValue().internalNotes().contains("holaaaa"));
        verify(customerService, never()).createQuickCustomer(any());
        assertEquals(BookingRequestStatus.CONVERTED, r.getStatus());
        assertEquals("OS-2026-00040", view.serviceOrderNumber());
        assertEquals("admin", r.getHandledBy());
    }

    @Test
    void convertCreatesCustomerWhenNoMatch() {
        pending();
        CustomerDto created = new CustomerDto();
        created.setId(9L);
        given(customerService.createQuickCustomer(any())).willReturn(created);
        given(customerRepository.getReferenceById(9L)).willReturn(new Customer());
        orderCreated();

        service.convert(12L, new ConvertBookingRequest("Graduación", null), "admin");

        ArgumentCaptor<QuickCustomerRequest> q = ArgumentCaptor.forClass(QuickCustomerRequest.class);
        verify(customerService).createQuickCustomer(q.capture());
        assertEquals("Eleazar Garcia Sierra", q.getValue().getName());
        assertEquals("me@elgarcia.org", q.getValue().getEmail());
    }

    @Test
    void cannotConvertTwice() {
        BookingRequest r = pending();
        r.setStatus(BookingRequestStatus.DISCARDED);

        assertThrows(IllegalStateException.class,
                () -> service.convert(12L, new ConvertBookingRequest("X", null), "admin"));
        verify(serviceOrderService, never()).create(any(), any());
    }

    @Test
    void discardAndReopen() {
        BookingRequest r = pending();
        service.discard(12L, "admin");
        assertEquals(BookingRequestStatus.DISCARDED, r.getStatus());
        service.reopen(12L);
        assertEquals(BookingRequestStatus.NEW, r.getStatus());
        assertNull(r.getHandledBy());
    }
}
