package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.CustomerDataRequestRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.service.StudioNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerDataRequestServiceTest {

    @Mock private CustomerDataRequestRepository repository;
    @Mock private ServiceOrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private WhatsAppOutbox outbox;
    @Mock private StudioNotifier notifier;

    private CustomerDataRequestService service;
    private Customer customer;
    private ServiceOrder order;

    @BeforeEach
    void setUp() {
        service = new CustomerDataRequestService(repository, orderRepository, customerRepository, outbox, notifier,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-11-18T20:00:00Z"), ZoneOffset.UTC));
        customer = new Customer();
        customer.setId(7L);
        customer.setFirstName("Ana");
        customer.setLastName("-");
        customer.setPhoneNumber("809-555-1234");
        order = new ServiceOrder();
        order.setOrderNumber("OS-0042");
        order.setCustomer(customer);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(customerRepository.findFirstByEmailIgnoreCase(any())).willReturn(Optional.empty());
    }

    private CustomerDataRequest pending(String token) {
        CustomerDataRequest r = new CustomerDataRequest();
        r.setToken(token);
        r.setCustomer(customer);
        r.setServiceOrder(order);
        given(repository.findByToken(token)).willReturn(Optional.of(r));
        return r;
    }

    @Test
    void completaLaFichaConLoQueEnvioElCliente() {
        CustomerDataRequest request = pending("tok1");

        Optional<String> name = service.complete("""
                {"flow_token":"tok1","first_name":" Ana  María ","last_name":"Pérez","email":"Ana@Correo.com",
                 "cedula":"001-1234567-8","address":"Calle 1, SPM","whatsapp_opt_in":true}""", "18095551234");

        assertThat(name).contains("Ana María Pérez");
        assertThat(customer.getFirstName()).isEqualTo("Ana María");
        assertThat(customer.getLastName()).isEqualTo("Pérez");
        assertThat(customer.getEmail()).isEqualTo("ana@correo.com");
        assertThat(customer.getRncCedula()).isEqualTo("00112345678");
        assertThat(customer.getTipoIdentificacion()).isEqualTo(TipoIdentificacion.CEDULA);
        assertThat(customer.getAddress()).isEqualTo("Calle 1, SPM");
        assertThat(customer.getWhatsappOptIn()).isTrue();
        assertThat(customer.getWhatsappOptInSource()).isEqualTo("whatsapp");
        assertThat(request.getStatus()).isEqualTo(CustomerDataRequest.Status.COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
        verify(customerRepository).save(customer);
        verify(notifier).send(org.mockito.ArgumentMatchers.contains("OS-0042"));
    }

    @Test
    void camposVaciosNoBorranNadaYElCorreoDeOtroClienteSeOmite() {
        customer.setAddress("Dirección anterior");
        CustomerDataRequest request = pending("tok2");
        Customer other = new Customer();
        other.setId(99L);
        given(customerRepository.findFirstByEmailIgnoreCase("ya@usado.com")).willReturn(Optional.of(other));

        service.complete("""
                {"flow_token":"tok2","first_name":"Ana","last_name":"Gómez","email":"ya@usado.com","cedula":"123",
                 "address":"","whatsapp_opt_in":false}""", "18095551234");

        assertThat(customer.getEmail()).isNull();
        assertThat(customer.getRncCedula()).isNull();
        assertThat(customer.getAddress()).isEqualTo("Dirección anterior");
        assertThat(customer.getWhatsappOptIn()).isNull();
        assertThat(request.getResultDetail()).contains("correo ya registrado").contains("cédula/RNC");
    }

    @Test
    void respuestaRepetidaOTokenDesconocidoNoHaceNada() {
        CustomerDataRequest request = pending("tok3");
        request.setStatus(CustomerDataRequest.Status.COMPLETED);
        given(repository.findByToken("otro")).willReturn(Optional.empty());

        assertThat(service.complete("{\"flow_token\":\"tok3\",\"first_name\":\"X\"}", "1")).isEmpty();
        assertThat(service.complete("{\"flow_token\":\"otro\",\"first_name\":\"X\"}", "1")).isEmpty();
        assertThat(service.complete("no es json", "1")).isEmpty();
        assertThat(customer.getFirstName()).isEqualTo("Ana");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void enviaElFormularioConTokenYSinExigirConsentimientoPrevio() {
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));
        given(outbox.isActive(WhatsAppMessageKind.CUSTOMER_DATA_REQUEST)).willReturn(true);
        WhatsAppMessage message = new WhatsAppMessage();
        message.setStatus(WhatsAppMessageStatus.PENDING);
        given(outbox.enqueue(any())).willReturn(message);

        CustomerDataRequestService.RequestView view = service.request(42L, "secretaria");

        ArgumentCaptor<WhatsAppOutbox.Notice> notice = ArgumentCaptor.forClass(WhatsAppOutbox.Notice.class);
        verify(outbox).enqueue(notice.capture());
        assertThat(notice.getValue().consent()).isTrue();
        assertThat(notice.getValue().buttonParam()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(notice.getValue().params()).containsExactly("Ana");
        assertThat(view.status()).isEqualTo("SENT");
        assertThat(view.messageStatus()).isEqualTo("PENDING");
    }

    @Test
    void noEnviaSiEstaApagadoOSinTelefono() {
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));
        given(outbox.isActive(WhatsAppMessageKind.CUSTOMER_DATA_REQUEST)).willReturn(false);
        assertThatThrownBy(() -> service.request(42L, null)).hasMessageContaining("apagado");

        given(outbox.isActive(WhatsAppMessageKind.CUSTOMER_DATA_REQUEST)).willReturn(true);
        customer.setPhoneNumber("123");
        assertThatThrownBy(() -> service.request(42L, null)).hasMessageContaining("teléfono");
        verify(outbox, never()).enqueue(any());
    }
}
