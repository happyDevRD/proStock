package com.happydev.prestockbackend.calendar;

import com.happydev.prestockbackend.entity.CalendarBlock;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderItem;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.repository.CalendarBlockRepository;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.ServiceOrderItemRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.service.FeatureConfigService;
import com.happydev.prestockbackend.service.IntegrationCredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Copia las citas de las órdenes y los bloqueos de fechas al calendario de Google del estudio
 * (solo de ProStock a Calendar). Cada orden y cada bloqueo tiene un id de evento fijo
 * ({@code prostockos<id>}, {@code prostockbl<id>}): mover, cancelar o volver a agendar actualiza el
 * mismo evento.
 *
 * <p>Una tarea revisa cada minuto lo que cambió desde la última vez ({@code updated_at} posterior a
 * {@code calendar_synced_at}) y lo envía; si Google o la red fallan, se reintenta a los 5 minutos.
 * Así ningún cambio se pierde aunque la conexión del estudio se corte, y las pantallas nunca esperan
 * a Google.
 *
 * <p>Se activa con el módulo {@value #FEATURE} y las credenciales {@code google_calendar} /
 * {@code service_account_json} y {@code calendar_id} (Configuración → Integraciones).
 */
@Service
public class GoogleCalendarSync {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarSync.class);
    public static final String FEATURE = "integration.google_calendar";
    public static final String PROVIDER = "google_calendar";
    public static final String KEY_JSON = "service_account_json";
    public static final String KEY_CALENDAR = "calendar_id";
    static final String TIME_ZONE = "America/Santo_Domingo";
    /** Citas de hace más de esto no se suben al activar la integración. */
    static final int BACKFILL_DAYS = 30;
    static final int BATCH = 25;
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final GoogleCalendarClient client;
    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderItemRepository itemRepository;
    private final CalendarBlockRepository blockRepository;
    private final CompanyConfigRepository companyRepository;
    private final FeatureConfigService features;
    private final IntegrationCredentialService credentials;
    private final TransactionTemplate tx;
    private final String appUrl;

    private volatile String lastError;
    private volatile LocalDateTime lastSyncAt;

    public GoogleCalendarSync(GoogleCalendarClient client, ServiceOrderRepository orderRepository,
                              ServiceOrderItemRepository itemRepository, CalendarBlockRepository blockRepository,
                              CompanyConfigRepository companyRepository, FeatureConfigService features,
                              IntegrationCredentialService credentials, TransactionTemplate tx,
                              @Value("${app.calendar.app-url:}") String appUrl) {
        this.client = client;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.blockRepository = blockRepository;
        this.companyRepository = companyRepository;
        this.features = features;
        this.credentials = credentials;
        this.tx = tx;
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    record Config(String serviceAccountJson, String calendarId) {}

    /** Configuración completa y módulo encendido; si no, null. */
    @Nullable
    Config config() {
        if (!features.isEnabled(FEATURE)) return null;
        String json = credentials.get(PROVIDER, KEY_JSON).filter(s -> !s.isBlank()).orElse(null);
        String calendar = credentials.get(PROVIDER, KEY_CALENDAR).map(String::trim).filter(s -> !s.isBlank()).orElse(null);
        return json == null || calendar == null ? null : new Config(json, calendar);
    }

    public static String orderEventId(Long id) {
        return "prostockos" + id;
    }

    public static String blockEventId(Long id) {
        return "prostockbl" + id;
    }

    // ── Tarea periódica ────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${app.calendar.sync-delay-ms:60000}", initialDelayString = "${app.calendar.sync-initial-delay-ms:45000}")
    public void syncPending() {
        Config cfg = config();
        if (cfg == null) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(BACKFILL_DAYS);
        LocalDateTime retryBefore = now.minusMinutes(5);
        List<ServiceOrder> orders = tx.execute(s ->
                orderRepository.findCalendarPending(since, retryBefore, PageRequest.of(0, BATCH)).stream()
                        .peek(this::touchLazy).toList());
        for (ServiceOrder o : Objects.requireNonNull(orders)) {
            syncOrder(cfg, o);
        }
        List<CalendarBlock> blocks = tx.execute(s ->
                blockRepository.findCalendarPending(now.minusDays(7), retryBefore, PageRequest.of(0, BATCH)).stream()
                        .peek(b -> { if (b.getEmployee() != null) b.getEmployee().getNombreCompleto(); }).toList());
        for (CalendarBlock b : Objects.requireNonNull(blocks)) {
            syncBlock(cfg, b);
        }
        if (!orders.isEmpty() || !blocks.isEmpty()) lastSyncAt = LocalDateTime.now();
    }

    /** Carga dentro de la transacción lo que el evento necesita (cliente, equipo). */
    private void touchLazy(ServiceOrder o) {
        if (o.getCustomer() != null) o.getCustomer().getFirstName();
        if (o.getPhotographer() != null) o.getPhotographer().getNombreCompleto();
        if (o.getEditor() != null) o.getEditor().getNombreCompleto();
    }

    void syncOrder(Config cfg, ServiceOrder o) {
        String eventId = orderEventId(o.getId());
        try {
            if (wantsEvent(o)) {
                Map<String, Object> event = tx.execute(s -> orderEvent(o));
                client.upsert(cfg.serviceAccountJson(), cfg.calendarId(), eventId, Objects.requireNonNull(event));
            } else {
                client.delete(cfg.serviceAccountJson(), cfg.calendarId(), eventId);
            }
            tx.executeWithoutResult(s -> orderRepository.markCalendarSynced(o.getId(), o.getUpdatedAt()));
        } catch (RuntimeException e) {
            fail("orden " + o.getOrderNumber(), e);
            tx.executeWithoutResult(s -> orderRepository.markCalendarFailed(o.getId(), LocalDateTime.now(), truncate(e.getMessage())));
        }
    }

    void syncBlock(Config cfg, CalendarBlock b) {
        try {
            client.upsert(cfg.serviceAccountJson(), cfg.calendarId(), blockEventId(b.getId()), blockEvent(b));
            tx.executeWithoutResult(s -> blockRepository.markCalendarSynced(b.getId(), LocalDateTime.now()));
        } catch (RuntimeException e) {
            fail("bloqueo " + b.getId(), e);
            tx.executeWithoutResult(s -> blockRepository.markCalendarFailed(b.getId(), LocalDateTime.now(), truncate(e.getMessage())));
        }
    }

    private void fail(String what, RuntimeException e) {
        lastError = LocalDateTime.now().withNano(0) + " · " + what + ": " + e.getMessage();
        log.warn("Google Calendar: no se pudo sincronizar {}: {}", what, e.getMessage());
    }

    /** El bloqueo ya no existe: se borra su evento enseguida (en otro hilo, para no frenar la pantalla). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBlockDeleted(CalendarBlockDeletedEvent event) {
        Config cfg = config();
        if (cfg == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                client.delete(cfg.serviceAccountJson(), cfg.calendarId(), blockEventId(event.blockId()));
            } catch (RuntimeException e) {
                fail("bloqueo borrado " + event.blockId(), e);
            }
        });
    }

    // ── Contenido de los eventos ───────────────────────────────────────────

    /** Con cita y sin cancelar. Las entregadas conservan su evento (queda la historia en el calendario). */
    static boolean wantsEvent(ServiceOrder o) {
        return o.getAppointmentDate() != null && o.getStatus() != ServiceOrderStatus.CANCELLED;
    }

    Map<String, Object> orderEvent(ServiceOrder o) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("summary", summary(o));
        e.put("description", description(o));
        companyRepository.findAll().stream().findFirst()
                .map(c -> c.getDireccion())
                .filter(a -> a != null && !a.isBlank())
                .ifPresent(a -> e.put("location", a));
        putTimes(e, o.getAppointmentDate(), o.getAppointmentEnd(), o.isAppointmentAllDay());
        if (o.getPhotographer() != null) {
            // Un color por fotógrafo (Google tiene 11 colores de evento).
            e.put("colorId", String.valueOf(o.getPhotographer().getId() % 11 + 1));
        }
        e.put("status", "confirmed");
        e.put("extendedProperties", Map.of("private", Map.of("prostock", "order:" + o.getId())));
        e.put("reminders", Map.of("useDefault", true));
        return e;
    }

    /** «Ana Martínez · Combo XV Años 1»; sin cliente, el título de la orden. */
    String summary(ServiceOrder o) {
        String customer = customerName(o.getCustomer());
        String pkg = packageName(o);
        if (customer == null) return o.getTitle();
        return pkg == null ? customer : customer + " · " + pkg;
    }

    private String description(ServiceOrder o) {
        List<String> lines = new ArrayList<>();
        lines.add("Orden " + o.getOrderNumber() + (o.getTitle() != null ? " — " + o.getTitle() : ""));
        lines.add("Fotógrafo: " + staffName(o.getPhotographer()));
        lines.add("Editor: " + staffName(o.getEditor()));
        if (!appUrl.isEmpty()) lines.add("Abrir en ProStock: " + appUrl + "/service_orders/" + o.getId());
        lines.add("");
        lines.add("Creado por ProStock: los cambios se hacen en la orden (lo que se edite aquí se sobrescribe).");
        return String.join("\n", lines);
    }

    @Nullable
    private String packageName(ServiceOrder o) {
        List<ServiceOrderItem> items = itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(o.getId());
        return items.stream().map(i -> i.getProduct() == null ? null : i.getProduct().getName())
                .filter(n -> n != null && !n.startsWith("Foto Extra")).findFirst().orElse(null);
    }

    @Nullable
    private static String customerName(@Nullable Customer c) {
        if (c == null) return null;
        String name = (Optional.ofNullable(c.getFirstName()).orElse("") + " "
                + Optional.ofNullable(c.getLastName()).orElse("")).replace(" -", "").trim();
        return name.isEmpty() ? null : name;
    }

    private static String staffName(@Nullable Employee e) {
        return e == null ? "sin asignar" : e.getNombreCompleto();
    }

    Map<String, Object> blockEvent(CalendarBlock b) {
        Map<String, Object> e = new LinkedHashMap<>();
        String who = b.getEmployee() != null ? b.getEmployee().getNombreCompleto() : "todo el estudio";
        e.put("summary", "Bloqueado (" + who + "): " + b.getReason());
        e.put("description", "Bloqueo de agenda creado en ProStock. Se quita desde el calendario de órdenes.");
        putTimes(e, b.getStartsAt(), b.getEndsAt(), b.isAllDay());
        e.put("colorId", "8");
        e.put("status", "confirmed");
        e.put("extendedProperties", Map.of("private", Map.of("prostock", "block:" + b.getId())));
        return e;
    }

    static void putTimes(Map<String, Object> e, LocalDateTime start, @Nullable LocalDateTime end, boolean allDay) {
        if (allDay) {
            LocalDateTime endDay = end != null && end.toLocalDate().isAfter(start.toLocalDate())
                    ? end : start.plusDays(1);
            e.put("start", Map.of("date", start.toLocalDate().toString()));
            e.put("end", Map.of("date", endDay.toLocalDate().toString()));
        } else {
            LocalDateTime finish = end != null && end.isAfter(start) ? end : start.plusHours(1);
            e.put("start", Map.of("dateTime", start.withNano(0).format(LOCAL), "timeZone", TIME_ZONE));
            e.put("end", Map.of("dateTime", finish.withNano(0).format(LOCAL), "timeZone", TIME_ZONE));
        }
    }

    private static String truncate(@Nullable String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) + "…" : s;
    }

    // ── Estado y prueba (Configuración) ─────────────────────────────────────

    public record Status(boolean enabled, boolean configured, @Nullable String serviceAccountEmail,
                         @Nullable String calendarId, long pending, @Nullable String lastError,
                         @Nullable LocalDateTime lastSyncAt) {}

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Status status() {
        boolean enabled = features.isEnabled(FEATURE);
        Optional<String> json = credentials.get(PROVIDER, KEY_JSON).filter(s -> !s.isBlank());
        Optional<String> calendar = credentials.get(PROVIDER, KEY_CALENDAR).filter(s -> !s.isBlank());
        String email = null;
        if (json.isPresent()) {
            try {
                email = client.parse(json.get()).clientEmail();
            } catch (RuntimeException e) {
                email = null;
            }
        }
        long pending = orderRepository.countCalendarPending(LocalDateTime.now().minusDays(BACKFILL_DAYS));
        return new Status(enabled, json.isPresent() && calendar.isPresent(), email, calendar.orElse(null),
                pending, lastError, lastSyncAt);
    }

    /** Comprueba credenciales y acceso al calendario; devuelve su nombre. */
    public String test() {
        String json = credentials.get(PROVIDER, KEY_JSON).filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Falta la llave de la cuenta de servicio (service_account_json)."));
        String calendar = credentials.get(PROVIDER, KEY_CALENDAR).map(String::trim).filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Falta el id del calendario (calendar_id)."));
        try {
            return client.calendarSummary(json, calendar);
        } catch (GoogleCalendarClient.CalendarException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
