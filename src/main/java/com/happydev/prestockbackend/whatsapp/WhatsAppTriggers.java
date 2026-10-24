package com.happydev.prestockbackend.whatsapp;

import com.happydev.prestockbackend.dto.ServiceOrderDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.BookingRequestRepository;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.service.BookingRequestCreatedEvent;
import com.happydev.prestockbackend.service.SelectionLinkCreatedEvent;
import com.happydev.prestockbackend.service.ServiceOrderChangedEvent;
import com.happydev.prestockbackend.service.ServiceOrderPaymentEvent;
import com.happydev.prestockbackend.service.ServiceOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Cuándo sale cada aviso. Escucha los hechos del sistema después del commit (un fallo aquí
 * nunca deshace la operación del estudio) y deja el aviso en la bandeja de salida. Cada hecho
 * tiene una clave fija, así que repetirlo (p. ej. guardar la orden otra vez) no avisa de nuevo.
 */
@Service
public class WhatsAppTriggers {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTriggers.class);
    private static final BigDecimal MIN_BALANCE = new BigDecimal("0.01");
    /** Si no hay WHATSAPP_DEFAULT_PLACE ni dirección de la empresa. */
    private static final String STUDIO_PLACE = "el estudio";

    private final WhatsAppOutbox outbox;
    private final WhatsAppProperties props;
    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderService orderService;
    private final BookingRequestRepository bookingRepository;
    private final CompanyConfigRepository companyRepository;
    private final Clock clock;

    @Autowired
    public WhatsAppTriggers(WhatsAppOutbox outbox, WhatsAppProperties props, ServiceOrderRepository orderRepository,
                            ServiceOrderService orderService, BookingRequestRepository bookingRepository,
                            CompanyConfigRepository companyRepository) {
        this(outbox, props, orderRepository, orderService, bookingRepository, companyRepository, Clock.systemUTC());
    }

    WhatsAppTriggers(WhatsAppOutbox outbox, WhatsAppProperties props, ServiceOrderRepository orderRepository,
                     ServiceOrderService orderService, BookingRequestRepository bookingRepository,
                     CompanyConfigRepository companyRepository, Clock clock) {
        this.outbox = outbox;
        this.props = props;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.bookingRepository = bookingRepository;
        this.companyRepository = companyRepository;
        this.clock = clock;
    }

    // ── Solicitud web ──────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingCreated(BookingRequestCreatedEvent event) {
        safely("solicitud " + event.bookingRequestId(), () -> bookingReceived(event.bookingRequestId()));
    }

    void bookingReceived(Long bookingId) {
        BookingRequest booking = bookingRepository.findById(bookingId).orElse(null);
        // Sin la casilla marcada no se registra nada: aún no es cliente y no pidió avisos.
        if (booking == null || !booking.isWhatsappOptIn()) return;
        String when = booking.getEventStart() != null
                ? WhatsAppText.when(booking.getEventStart(), booking.isEventAllDay())
                : booking.getEventDate() != null ? WhatsAppText.date(booking.getEventDate())
                : "la fecha que nos indiques";
        outbox.enqueue(new WhatsAppOutbox.Notice(WhatsAppMessageKind.BOOKING_RECEIVED, booking.getPhone(),
                booking.getName(),
                List.of(WhatsAppText.firstName(booking.getName()),
                        WhatsAppText.param(booking.getEventType(), "una sesión de fotos"),
                        WhatsAppText.param(when, "la fecha que nos indiques")),
                null, true, null, null, booking, "booking:" + bookingId, null));
    }

    // ── Cambios de la orden: cita agendada y trabajo listo ─────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderChanged(ServiceOrderChangedEvent event) {
        safely("orden " + event.orderId(), () -> orderChanged(event.orderId()));
    }

    void orderChanged(Long orderId) {
        ServiceOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getCustomer() == null || closed(order)) return;
        if (order.getAppointmentDate() != null && order.getAppointmentDate().isAfter(now())
                && !"DELIVERED".equals(order.getCurrentStage())) {
            appointment(order, WhatsAppMessageKind.APPOINTMENT_CONFIRMED,
                    "appointment:" + orderId + ":" + order.getAppointmentDate(),
                    WhatsAppText.when(order.getAppointmentDate(), order.isAppointmentAllDay()));
        }
        if ("READY".equals(order.getCurrentStage())) {
            BigDecimal balance = balance(orderId);
            boolean owes = balance.compareTo(MIN_BALANCE) > 0;
            Customer customer = order.getCustomer();
            List<String> params = owes
                    ? List.of(WhatsAppText.firstName(customer.getFirstName()), order.getOrderNumber(), WhatsAppText.money(balance))
                    : List.of(WhatsAppText.firstName(customer.getFirstName()), order.getOrderNumber());
            enqueueForCustomer(owes ? WhatsAppMessageKind.WORK_READY_WITH_BALANCE : WhatsAppMessageKind.WORK_READY,
                    order, params, null, "ready:" + orderId, null);
        }
    }

    // ── Recordatorio del día anterior ──────────────────────────────────────

    /** Cada 15 minutos, dentro de la franja permitida, avisa las citas de mañana que falten. */
    @Scheduled(cron = "${app.whatsapp.reminder-cron:0 */15 * * * *}")
    @Transactional
    public void remindTomorrow() {
        if (!outbox.isActive(WhatsAppMessageKind.APPOINTMENT_REMINDER)) return;
        LocalDateTime now = now();
        if (now.getHour() < props.getReminderFromHour() || now.getHour() >= props.getReminderUntilHour()) return;
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        for (ServiceOrder order : orderRepository.findAppointmentsOverlapping(
                tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay())) {
            if (order.getCustomer() == null || closed(order)
                    || !order.getAppointmentDate().toLocalDate().equals(tomorrow)) continue;
            safely("recordatorio " + order.getId(), () -> appointment(order, WhatsAppMessageKind.APPOINTMENT_REMINDER,
                    "reminder:" + order.getId() + ":" + order.getAppointmentDate(),
                    WhatsAppText.whenRelative(order.getAppointmentDate(), order.isAppointmentAllDay(), now.toLocalDate())));
        }
    }

    // ── Portal de selección ────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSelectionLink(SelectionLinkCreatedEvent event) {
        safely("selección " + event.orderId(), () -> {
            ServiceOrder order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null || order.getCustomer() == null) return;
            enqueueForCustomer(WhatsAppMessageKind.SELECTION_READY, order,
                    List.of(WhatsAppText.firstName(order.getCustomer().getFirstName()),
                            WhatsAppText.param(WhatsAppText.serviceLabel(order.getTitle()), "fotos")),
                    event.token(), "selection:" + event.token(), null);
        });
    }

    // ── Pagos ──────────────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPayment(ServiceOrderPaymentEvent event) {
        safely("pago " + event.saleId(), () -> {
            ServiceOrder order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null || order.getCustomer() == null) return;
            enqueueForCustomer(WhatsAppMessageKind.PAYMENT_RECEIVED, order,
                    List.of(WhatsAppText.firstName(order.getCustomer().getFirstName()),
                            WhatsAppText.money(event.amount()), order.getOrderNumber(),
                            WhatsAppText.money(balance(event.orderId()).max(BigDecimal.ZERO))),
                    null, "payment:" + event.saleId(), null);
        });
    }

    // ── Manual: recordatorio de saldo ──────────────────────────────────────

    /** Lo pide el estudio desde la orden; como mucho uno por orden y día. */
    @Transactional
    @Nullable
    public WhatsAppMessage balanceReminder(Long orderId, @Nullable String actor) {
        ServiceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe."));
        if (order.getCustomer() == null) throw new IllegalStateException("La orden no tiene cliente.");
        BigDecimal balance = balance(orderId);
        if (balance.compareTo(MIN_BALANCE) <= 0) throw new IllegalStateException("La orden no tiene saldo pendiente.");
        if (!outbox.isActive(WhatsAppMessageKind.BALANCE_REMINDER)) {
            throw new IllegalStateException("Los avisos por WhatsApp están apagados en el Centro de Módulos.");
        }
        WhatsAppMessage message = enqueueForCustomer(WhatsAppMessageKind.BALANCE_REMINDER, order,
                List.of(WhatsAppText.firstName(order.getCustomer().getFirstName()), order.getOrderNumber(),
                        WhatsAppText.money(balance)),
                null, "balance-reminder:" + orderId + ":" + now().toLocalDate(), actor);
        if (message == null) throw new IllegalStateException("Ya se envió un recordatorio de saldo hoy.");
        return message;
    }

    // ── Comunes ────────────────────────────────────────────────────────────

    private void appointment(ServiceOrder order, WhatsAppMessageKind kind, String dedupeKey, String when) {
        enqueueForCustomer(kind, order,
                List.of(WhatsAppText.firstName(order.getCustomer().getFirstName()),
                        WhatsAppText.param(WhatsAppText.serviceLabel(order.getTitle()), "fotos"),
                        WhatsAppText.param(when, "la fecha acordada"),
                        place()),
                null, dedupeKey, null);
    }

    @Nullable
    private WhatsAppMessage enqueueForCustomer(WhatsAppMessageKind kind, ServiceOrder order, List<String> params,
                                               @Nullable String buttonParam, String dedupeKey, @Nullable String actor) {
        Customer customer = order.getCustomer();
        String name = (customer.getFirstName() + " " + customer.getLastName()).replace(" -", "").trim();
        return outbox.enqueue(new WhatsAppOutbox.Notice(kind, customer.getPhoneNumber(), name, params, buttonParam,
                Boolean.TRUE.equals(customer.getWhatsappOptIn()), customer, order, null, dedupeKey, actor));
    }

    private BigDecimal balance(Long orderId) {
        ServiceOrderDto dto = orderService.findById(orderId);
        return dto.balance() == null ? BigDecimal.ZERO : dto.balance();
    }

    private String place() {
        if (!props.getDefaultPlace().isBlank()) return WhatsAppText.param(props.getDefaultPlace(), STUDIO_PLACE);
        return companyRepository.findFirstByOrderByIdAsc()
                .map(c -> WhatsAppText.param(c.getDireccion(), STUDIO_PLACE))
                .orElse(STUDIO_PLACE);
    }

    private static boolean closed(ServiceOrder order) {
        return order.getStatus() == ServiceOrderStatus.CANCELLED || order.getStatus() == ServiceOrderStatus.COMPLETED;
    }

    /** Las citas se guardan en la hora del estudio, no en la del servidor. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.of(props.getZone())));
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("WhatsApp: no se pudo preparar el aviso de {}: {}", what, e.getMessage());
        }
    }
}
