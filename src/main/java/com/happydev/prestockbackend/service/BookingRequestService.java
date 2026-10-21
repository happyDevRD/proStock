package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.BookingRequestDtos.*;
import com.happydev.prestockbackend.dto.CreateServiceOrderRequest;
import com.happydev.prestockbackend.dto.QuickCustomerRequest;
import com.happydev.prestockbackend.dto.ServiceOrderDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.BookingRequestRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solicitudes de reserva del formulario de la web. Entran sin sesión, con límites por IP
 * y en total; el estudio las convierte en orden (cliente existente o nuevo) o las descarta.
 */
@Service
public class BookingRequestService {

    private static final Logger log = LoggerFactory.getLogger(BookingRequestService.class);

    static final int MAX_PER_IP_PER_HOUR = 5;
    static final int MAX_PER_DAY = 100;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BookingRequestRepository repository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderService serviceOrderService;
    private final StudioNotifier notifier;
    private final Clock clock;
    private final Map<String, Deque<LocalDateTime>> recentByIp = new ConcurrentHashMap<>();

    @Autowired
    public BookingRequestService(BookingRequestRepository repository,
                                 CustomerRepository customerRepository,
                                 CustomerService customerService,
                                 ServiceOrderRepository orderRepository,
                                 ServiceOrderService serviceOrderService,
                                 StudioNotifier notifier) {
        this(repository, customerRepository, customerService, orderRepository, serviceOrderService, notifier,
                Clock.systemDefaultZone());
    }

    BookingRequestService(BookingRequestRepository repository,
                          CustomerRepository customerRepository,
                          CustomerService customerService,
                          ServiceOrderRepository orderRepository,
                          ServiceOrderService serviceOrderService,
                          StudioNotifier notifier,
                          Clock clock) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.orderRepository = orderRepository;
        this.serviceOrderService = serviceOrderService;
        this.notifier = notifier;
        this.clock = clock;
    }

    // ── Público ─────────────────────────────────────────────────────────────

    /**
     * Registra la solicitud y avisa al estudio. Si es un bot (campo trampa lleno) responde
     * como si todo fuera bien, sin guardar nada. Si el mismo teléfono ya envió la misma
     * solicitud en la última hora, devuelve esa en lugar de duplicarla.
     */
    @Transactional
    public PublicBookingCreated submit(PublicBookingRequest req, @Nullable String clientIp) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (req.website() != null && !req.website().isBlank()) {
            log.info("Solicitud web descartada por campo trampa (ip {})", clientIp);
            return new PublicBookingCreated("S-0000");
        }
        String phone = req.telefono().trim();
        if (digits(phone).length() < 7) {
            throw new IllegalArgumentException("Escribe un teléfono válido.");
        }

        for (BookingRequest prev : repository.findByPhoneAndCreatedAtAfterOrderByCreatedAtDesc(phone, now.minusHours(1))) {
            if (Objects.equals(prev.getEventType(), trimToNull(req.tipoEvento()))
                    && Objects.equals(prev.getEventDate(), req.fecha())) {
                return new PublicBookingCreated(prev.getCode());
            }
        }

        checkRateLimit(clientIp, now);

        BookingRequest r = new BookingRequest();
        r.setName(req.nombre().trim());
        r.setPhone(phone);
        r.setEmail(trimToNull(req.email()));
        r.setEventType(trimToNull(req.tipoEvento()));
        r.setEventDate(req.fecha());
        r.setMessage(trimToNull(req.mensaje()));
        r.setClientIp(clientIp);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        BookingRequest saved = repository.save(r);

        notifier.send("Nueva solicitud web " + saved.getCode() + ": " + saved.getName()
                + (saved.getEventType() != null ? " · " + saved.getEventType() : "")
                + (saved.getEventDate() != null ? " · " + DATE.format(saved.getEventDate()) : "")
                + " · " + saved.getPhone());
        return new PublicBookingCreated(saved.getCode());
    }

    private void checkRateLimit(@Nullable String ip, LocalDateTime now) {
        if (repository.countByCreatedAtAfter(now.minusDays(1)) >= MAX_PER_DAY) {
            log.warn("Solicitudes web: límite diario alcanzado ({})", MAX_PER_DAY);
            throw new TooManyBookingRequestsException();
        }
        if (ip == null) return;
        Deque<LocalDateTime> times = recentByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minusHours(1))) times.pollFirst();
            if (times.size() >= MAX_PER_IP_PER_HOUR) throw new TooManyBookingRequestsException();
            times.addLast(now);
        }
        if (recentByIp.size() > 10_000) recentByIp.clear();
    }

    // ── Estudio ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BookingRequestView> list(boolean pending) {
        List<BookingRequest> rows = pending
                ? repository.findByStatusOrderByCreatedAtDesc(BookingRequestStatus.NEW, PageRequest.of(0, 100))
                : repository.findByStatusNotOrderByHandledAtDesc(BookingRequestStatus.NEW, PageRequest.of(0, 50));
        return rows.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public BookingRequestCount count() {
        return new BookingRequestCount(repository.countByStatus(BookingRequestStatus.NEW));
    }

    /**
     * Crea la orden de la solicitud: usa el cliente que ya existe con ese teléfono o correo,
     * o crea uno nuevo. La orden entra en su etapa inicial (Agendada en fotografía).
     */
    @Transactional
    public BookingRequestView convert(Long id, ConvertBookingRequest req, @Nullable String actor) {
        BookingRequest r = pendingRequest(id);
        Customer customer = findCustomer(r).orElse(null);
        Long customerId = customer != null ? customer.getId() : createCustomer(r);

        ServiceOrderDto order = serviceOrderService.create(new CreateServiceOrderRequest(
                customerId, null, req.title().trim(), req.appointmentDate(), null, null, null,
                null, null, null, null, null, internalNotes(r), req.photographerId(), null,
                req.appointmentEnd(), req.appointmentAllDay()), actor);

        r.setCustomer(customerRepository.getReferenceById(customerId));
        r.setServiceOrder(orderRepository.getReferenceById(order.id()));
        markHandled(r, BookingRequestStatus.CONVERTED, actor);
        return toView(repository.save(r));
    }

    @Transactional
    public BookingRequestView discard(Long id, @Nullable String actor) {
        BookingRequest r = pendingRequest(id);
        markHandled(r, BookingRequestStatus.DISCARDED, actor);
        return toView(repository.save(r));
    }

    /** Deshace un descarte por error. */
    @Transactional
    public BookingRequestView reopen(Long id) {
        BookingRequest r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", "id", id));
        if (r.getStatus() != BookingRequestStatus.DISCARDED) {
            throw new IllegalStateException("Solo se puede reabrir una solicitud descartada.");
        }
        r.setStatus(BookingRequestStatus.NEW);
        r.setHandledBy(null);
        r.setHandledAt(null);
        return toView(repository.save(r));
    }

    private BookingRequest pendingRequest(Long id) {
        BookingRequest r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", "id", id));
        if (r.getStatus() != BookingRequestStatus.NEW) {
            throw new IllegalStateException("La solicitud " + r.getCode() + " ya fue atendida.");
        }
        return r;
    }

    private void markHandled(BookingRequest r, BookingRequestStatus status, @Nullable String actor) {
        r.setStatus(status);
        r.setHandledBy(actor);
        r.setHandledAt(LocalDateTime.now(clock));
    }

    private Optional<Customer> findCustomer(BookingRequest r) {
        if (r.getEmail() != null) {
            Optional<Customer> byEmail = customerRepository.findFirstByEmailIgnoreCase(r.getEmail());
            if (byEmail.isPresent()) return byEmail;
        }
        String d = digits(r.getPhone());
        if (d.length() < 7) return Optional.empty();
        return customerRepository.findFirstByPhoneDigits(d.length() > 10 ? d.substring(d.length() - 10) : d);
    }

    private Long createCustomer(BookingRequest r) {
        QuickCustomerRequest q = new QuickCustomerRequest();
        q.setName(r.getName().length() > 100 ? r.getName().substring(0, 100) : r.getName());
        q.setPhoneNumber(r.getPhone().length() > 20 ? digits(r.getPhone()) : r.getPhone());
        q.setEmail(r.getEmail());
        return customerService.createQuickCustomer(q).getId();
    }

    private static String internalNotes(BookingRequest r) {
        StringBuilder sb = new StringBuilder("Solicitud web ").append(r.getCode());
        if (r.getEventType() != null) sb.append(" · ").append(r.getEventType());
        if (r.getEventDate() != null) sb.append(" · fecha pedida ").append(DATE.format(r.getEventDate()));
        if (r.getMessage() != null) sb.append("\n").append(r.getMessage());
        return sb.toString();
    }

    private BookingRequestView toView(BookingRequest r) {
        MatchedCustomer matched = null;
        if (r.getStatus() == BookingRequestStatus.NEW) {
            matched = findCustomer(r).map(c -> new MatchedCustomer(c.getId(), fullName(c))).orElse(null);
        } else if (r.getCustomer() != null) {
            matched = new MatchedCustomer(r.getCustomer().getId(), fullName(r.getCustomer()));
        }
        List<SameDayOrder> sameDay = List.of();
        if (r.getStatus() == BookingRequestStatus.NEW && r.getEventDate() != null) {
            sameDay = sameDayOrders(r.getEventDate());
        }
        ServiceOrder order = r.getServiceOrder();
        return new BookingRequestView(
                r.getId(), r.getCode(), r.getName(), r.getPhone(), r.getEmail(), r.getEventType(),
                r.getEventDate(), r.getMessage(), r.getStatus().name(), r.getCreatedAt(), matched, sameDay,
                order != null ? order.getId() : null, order != null ? order.getOrderNumber() : null,
                r.getHandledBy(), r.getHandledAt());
    }

    private List<SameDayOrder> sameDayOrders(LocalDate day) {
        return orderRepository.findAppointmentsOverlapping(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream()
                .map(o -> new SameDayOrder(o.getId(), o.getOrderNumber(), o.getTitle(),
                        o.getCustomer() != null ? fullName(o.getCustomer()) : null, o.getAppointmentDate(),
                        o.getAppointmentEnd(), o.isAppointmentAllDay(),
                        o.getPhotographer() != null ? o.getPhotographer().getNombreCompleto() : null))
                .toList();
    }

    private static String fullName(Customer c) {
        String last = c.getLastName() == null || "-".equals(c.getLastName()) ? "" : " " + c.getLastName();
        return (c.getFirstName() + last).trim();
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static String trimToNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Demasiadas solicitudes desde la misma IP o en el día: 429. */
    public static class TooManyBookingRequestsException extends RuntimeException {
        public TooManyBookingRequestsException() {
            super("Recibimos muchas solicitudes seguidas. Escríbenos por WhatsApp.");
        }
    }
}
