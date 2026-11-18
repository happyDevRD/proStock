package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.CustomerDataRequest;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.WhatsAppMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessageKind;
import com.happydev.prestockbackend.repository.CustomerDataRequestRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.service.CustomerServiceImpl;
import com.happydev.prestockbackend.service.StudioNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formulario de datos del cliente dentro de WhatsApp (WhatsApp Flows). El estudio lo envía desde la
 * orden con la plantilla {@code datos_cliente}; el cliente lo llena en el chat y la respuesta llega
 * por el webhook con el mismo flow_token, que completa su ficha. Ver documentacion/whatsapp-formulario-datos.md.
 */
@Service
public class CustomerDataRequestService {

    private static final Logger log = LoggerFactory.getLogger(CustomerDataRequestService.class);
    private static final WhatsAppMessageKind KIND = WhatsAppMessageKind.CUSTOMER_DATA_REQUEST;
    /** Evita dos envíos por doble clic. */
    private static final Duration RESEND_AFTER = Duration.ofMinutes(2);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final int NAME_MAX = 50;
    private static final int ADDRESS_MAX = 200;

    public record RequestView(Long id, String status, Instant createdAt, Instant completedAt, String resultDetail,
                              String createdBy, String messageStatus, String messageDetail) {
        static RequestView of(CustomerDataRequest r) {
            WhatsAppMessage m = r.getWhatsappMessage();
            return new RequestView(r.getId(), r.getStatus().name(), r.getCreatedAt(), r.getCompletedAt(),
                    r.getResultDetail(), r.getCreatedBy(), m == null ? null : m.getStatus().name(),
                    m == null ? null : m.getStatusDetail());
        }
    }

    private final CustomerDataRequestRepository repository;
    private final ServiceOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final WhatsAppOutbox outbox;
    private final StudioNotifier notifier;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @Autowired
    public CustomerDataRequestService(CustomerDataRequestRepository repository, ServiceOrderRepository orderRepository,
                                      CustomerRepository customerRepository, WhatsAppOutbox outbox,
                                      StudioNotifier notifier, ObjectMapper mapper) {
        this(repository, orderRepository, customerRepository, outbox, notifier, mapper, Clock.systemUTC());
    }

    CustomerDataRequestService(CustomerDataRequestRepository repository, ServiceOrderRepository orderRepository,
                               CustomerRepository customerRepository, WhatsAppOutbox outbox,
                               StudioNotifier notifier, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.outbox = outbox;
        this.notifier = notifier;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Envía el formulario al cliente de la orden. Se envía aunque el cliente no haya aceptado los
     * avisos: lo pide él mismo al hablar con el estudio, y en el formulario puede aceptarlos.
     */
    @Transactional
    public RequestView request(Long orderId, @Nullable String actor) {
        ServiceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe."));
        Customer customer = order.getCustomer();
        if (customer == null) throw new IllegalStateException("La orden no tiene cliente.");
        if (!outbox.isActive(KIND)) {
            throw new IllegalStateException(
                    "El formulario de datos por WhatsApp está apagado en el Centro de Módulos.");
        }
        if (WhatsAppText.phone(customer.getPhoneNumber()) == null) {
            throw new IllegalStateException("El cliente no tiene un teléfono válido para WhatsApp.");
        }
        Instant now = Instant.now(clock);
        if (repository.existsByCustomerIdAndStatusAndCreatedAtAfter(customer.getId(), CustomerDataRequest.Status.SENT,
                now.minus(RESEND_AFTER))) {
            throw new IllegalStateException("Ya se le envió el formulario hace un momento.");
        }

        CustomerDataRequest request = new CustomerDataRequest();
        request.setToken(newToken());
        request.setCustomer(customer);
        request.setServiceOrder(order);
        request.setCreatedBy(actor);
        request.setCreatedAt(now);
        request = repository.save(request);

        String name = (customer.getFirstName() + " " + customer.getLastName()).replace(" -", "").trim();
        WhatsAppMessage message = outbox.enqueue(new WhatsAppOutbox.Notice(KIND, customer.getPhoneNumber(), name,
                List.of(WhatsAppText.firstName(customer.getFirstName())), request.getToken(), true, customer, order,
                null, "data-request:" + request.getToken(), actor));
        if (message == null) throw new IllegalStateException("No se pudo registrar el envío.");
        request.setWhatsappMessage(message);
        return RequestView.of(repository.save(request));
    }

    @Transactional(readOnly = true)
    public List<RequestView> forOrder(Long orderId) {
        return repository.findByServiceOrderIdOrderByCreatedAtDesc(orderId).stream().map(RequestView::of).toList();
    }

    /**
     * Respuesta del formulario (llega por el webhook). Devuelve el nombre del cliente si se aplicó;
     * vacío si el token no existe o ya se había respondido (Meta puede repetir el aviso).
     */
    @Transactional
    public Optional<String> complete(String responseJson, String fromPhone) {
        JsonNode response;
        try {
            response = mapper.readTree(responseJson);
        } catch (java.io.IOException e) {
            log.warn("Respuesta de formulario de WhatsApp con JSON inválido: {}", e.getMessage());
            return Optional.empty();
        }
        String token = response.path("flow_token").asText("");
        Optional<CustomerDataRequest> found = token.isBlank() ? Optional.empty() : repository.findByToken(token);
        if (found.isEmpty()) {
            log.info("Respuesta de formulario de WhatsApp sin solicitud conocida (de {})", fromPhone);
            return Optional.empty();
        }
        CustomerDataRequest request = found.get();
        if (request.getStatus() == CustomerDataRequest.Status.COMPLETED) return Optional.empty();

        Customer customer = request.getCustomer();
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        String firstName = text(response, "first_name", NAME_MAX);
        String lastName = text(response, "last_name", NAME_MAX);
        if (firstName != null) {
            customer.setFirstName(firstName);
            applied.add("nombre");
        }
        if (lastName != null) {
            customer.setLastName(lastName);
            applied.add("apellido");
        }

        String email = text(response, "email", 255);
        if (email != null) {
            Optional<Customer> owner = customerRepository.findFirstByEmailIgnoreCase(email);
            if (!EMAIL.matcher(email).matches()) {
                skipped.add("correo no válido");
            } else if (owner.isPresent() && !owner.get().getId().equals(customer.getId())) {
                skipped.add("correo ya registrado en otro cliente");
            } else {
                customer.setEmail(email.toLowerCase(java.util.Locale.ROOT));
                applied.add("correo");
            }
        }

        String idText = text(response, "cedula", 30);
        if (idText != null) {
            String digits = idText.replaceAll("\\D", "");
            if (digits.length() == 9 || digits.length() == 11) {
                customer.setRncCedula(digits);
                customer.setTipoIdentificacion(CustomerServiceImpl.resolveTipo(digits, null));
                applied.add(digits.length() == 11 ? "cédula" : "RNC");
            } else {
                skipped.add("cédula/RNC con cantidad de dígitos incorrecta");
            }
        }

        String address = text(response, "address", ADDRESS_MAX);
        if (address != null) {
            customer.setAddress(address);
            applied.add("dirección");
        }

        if (isTrue(response.path("whatsapp_opt_in")) && !Boolean.TRUE.equals(customer.getWhatsappOptIn())) {
            customer.setWhatsappOptIn(true);
            customer.setWhatsappOptInAt(Instant.now(clock));
            customer.setWhatsappOptInSource("whatsapp");
            applied.add("acepta avisos");
        }
        customerRepository.save(customer);

        String detail = (applied.isEmpty() ? "Sin cambios" : "Actualizado: " + String.join(", ", applied))
                + (skipped.isEmpty() ? "" : ". Omitido: " + String.join(", ", skipped));
        request.setStatus(CustomerDataRequest.Status.COMPLETED);
        request.setCompletedAt(Instant.now(clock));
        request.setResponse(responseJson.length() > 4000 ? responseJson.substring(0, 4000) : responseJson);
        request.setResultDetail(detail.length() > 500 ? detail.substring(0, 500) : detail);
        repository.save(request);

        String name = (customer.getFirstName() + " " + customer.getLastName()).replace(" -", "").trim();
        ServiceOrder order = request.getServiceOrder();
        notifier.send("Formulario de datos completado por " + name
                + (order != null ? " (orden " + order.getOrderNumber() + ")" : "") + ". " + detail + ".");
        return Optional.of(name);
    }

    @Nullable
    private static String text(JsonNode response, String field, int max) {
        JsonNode node = response.path(field);
        if (node.isMissingNode() || node.isNull()) return null;
        String value = node.asText("").trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static boolean isTrue(JsonNode node) {
        return node.isBoolean() ? node.asBoolean() : "true".equalsIgnoreCase(node.asText(""));
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
