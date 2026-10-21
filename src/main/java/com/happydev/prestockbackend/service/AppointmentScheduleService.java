package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.BookingRequestDtos.BusySlot;
import com.happydev.prestockbackend.dto.ScheduleDtos.AvailabilityResponse;
import com.happydev.prestockbackend.dto.ScheduleDtos.CalendarBlockDto;
import com.happydev.prestockbackend.dto.ScheduleDtos.CreateCalendarBlockRequest;
import com.happydev.prestockbackend.dto.ScheduleDtos.ScheduleItem;
import com.happydev.prestockbackend.entity.BookingRequest;
import com.happydev.prestockbackend.entity.BookingRequestStatus;
import com.happydev.prestockbackend.entity.CalendarBlock;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.BookingRequestRepository;
import com.happydev.prestockbackend.repository.CalendarBlockRepository;
import com.happydev.prestockbackend.repository.EmployeeRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Disponibilidad de la agenda y bloqueo de fechas. Una cita choca cuando el mismo
 * fotógrafo ya tiene otra sesión que se cruza, o cuando la fecha está bloqueada para
 * el estudio o para él. Solo se avisa: el estudio decide si agenda igual.
 */
@Service
public class AppointmentScheduleService {

    static final int MAX_RANGE_DAYS = 62;

    private final ServiceOrderRepository orderRepository;
    private final CalendarBlockRepository blockRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final BookingRequestRepository requestRepository;

    public AppointmentScheduleService(ServiceOrderRepository orderRepository,
                                      CalendarBlockRepository blockRepository,
                                      EmployeeRepository employeeRepository,
                                      AuditService auditService,
                                      BookingRequestRepository requestRepository) {
        this.orderRepository = orderRepository;
        this.blockRepository = blockRepository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.requestRepository = requestRepository;
    }

    /**
     * Horarios ocupados para la web, sin datos de nadie: citas no canceladas, bloqueos de
     * todo el estudio y solicitudes web pendientes con hora. El estudio atiende una sesión
     * a la vez; los bloqueos de una sola persona no cierran la agenda de la web.
     */
    @Transactional(readOnly = true)
    public List<BusySlot> publicBusy(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return busy(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    /** ¿Se cruza con algo de la agenda? Lo usa la web al recibir una solicitud con hora. */
    @Transactional(readOnly = true)
    public boolean isTaken(AppointmentWindow w) {
        return !busy(w.start(), w.end()).isEmpty();
    }

    private List<BusySlot> busy(LocalDateTime start, LocalDateTime end) {
        List<BusySlot> raw = new ArrayList<>();
        for (ServiceOrder o : orderRepository.findAppointmentsOverlapping(start, end)) {
            LocalDateTime e = o.getAppointmentEnd() != null ? o.getAppointmentEnd() : o.getAppointmentDate().plus(AppointmentWindow.DEFAULT_DURATION);
            raw.add(new BusySlot(o.getAppointmentDate(), e));
        }
        for (CalendarBlock b : blockRepository.findOverlapping(start, end)) {
            if (b.getEmployee() == null) raw.add(new BusySlot(b.getStartsAt(), b.getEndsAt()));
        }
        for (BookingRequest r : requestRepository.findByStatusAndEventStartLessThanAndEventEndGreaterThan(
                BookingRequestStatus.NEW, end, start)) {
            raw.add(new BusySlot(r.getEventStart(), r.getEventEnd()));
        }
        raw.sort(Comparator.comparing(BusySlot::start));
        // Une los tramos que se tocan o solapan: la web no necesita más detalle
        List<BusySlot> merged = new ArrayList<>();
        for (BusySlot s : raw) {
            BusySlot last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && !s.start().isAfter(last.end())) {
                if (s.end().isAfter(last.end())) merged.set(merged.size() - 1, new BusySlot(last.start(), s.end()));
            } else {
                merged.add(s);
            }
        }
        return merged;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(LocalDateTime start, @Nullable LocalDateTime end, @Nullable Boolean allDay,
                                             @Nullable Long photographerId, @Nullable Long excludeOrderId) {
        AppointmentWindow w = Objects.requireNonNull(AppointmentWindow.of(start, end, allDay));
        List<ScheduleItem> conflicts = new ArrayList<>();
        List<ScheduleItem> sameTime = new ArrayList<>();

        for (CalendarBlock b : blockRepository.findOverlapping(w.start(), w.end())) {
            if (b.getEmployee() == null || b.getEmployee().getId().equals(photographerId)) {
                conflicts.add(toItem(b));
            }
        }
        for (ServiceOrder o : orderRepository.findAppointmentsOverlapping(w.start(), w.end())) {
            if (o.getId().equals(excludeOrderId)) continue;
            boolean samePhotographer = photographerId != null && o.getPhotographer() != null
                    && photographerId.equals(o.getPhotographer().getId());
            (samePhotographer ? conflicts : sameTime).add(toItem(o));
        }
        return new AvailabilityResponse(conflicts, sameTime);
    }

    @Transactional(readOnly = true)
    public List<CalendarBlockDto> blocks(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return blockRepository.findOverlapping(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .map(AppointmentScheduleService::toDto)
                .toList();
    }

    /** Bloqueos que afectan a un empleado: los suyos y los de todo el estudio. */
    @Transactional(readOnly = true)
    public List<CalendarBlockDto> blocksFor(Long employeeId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        return blockRepository.findOverlapping(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .filter(b -> b.getEmployee() == null || b.getEmployee().getId().equals(employeeId))
                .map(AppointmentScheduleService::toDto)
                .toList();
    }

    @Transactional
    public CalendarBlockDto createBlock(CreateCalendarBlockRequest req, @Nullable String actor) {
        AppointmentWindow w = Objects.requireNonNull(AppointmentWindow.of(req.start(), req.end(), req.allDay()));
        CalendarBlock b = new CalendarBlock();
        b.setStartsAt(w.start());
        b.setEndsAt(w.end());
        b.setAllDay(w.allDay());
        b.setReason(req.reason().trim());
        b.setCreatedBy(actor);
        if (req.employeeId() != null) {
            b.setEmployee(employeeRepository.findById(req.employeeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", req.employeeId())));
        }
        CalendarBlock saved = blockRepository.save(b);
        auditService.record(actor, "CALENDAR_BLOCK_CREATED", "CalendarBlock", saved.getId(),
                "Bloqueo de agenda: " + saved.getReason());
        return toDto(saved);
    }

    @Transactional
    public void deleteBlock(Long id, @Nullable String actor) {
        CalendarBlock b = blockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CalendarBlock", "id", id));
        blockRepository.delete(b);
        auditService.record(actor, "CALENDAR_BLOCK_DELETED", "CalendarBlock", id,
                "Bloqueo de agenda quitado: " + b.getReason());
    }

    static void checkRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("La fecha final es anterior a la inicial.");
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("El rango máximo es de " + MAX_RANGE_DAYS + " días.");
        }
    }

    private static ScheduleItem toItem(CalendarBlock b) {
        return new ScheduleItem("BLOCK", b.getId(), b.getReason(), null, b.getStartsAt(), b.getEndsAt(),
                b.isAllDay(), b.getEmployee() != null ? b.getEmployee().getNombreCompleto() : null);
    }

    private static ScheduleItem toItem(ServiceOrder o) {
        Customer c = o.getCustomer();
        String customer = c == null ? null
                : (c.getFirstName() + " " + (c.getLastName() == null || "-".equals(c.getLastName()) ? "" : c.getLastName())).trim();
        Employee p = o.getPhotographer();
        return new ScheduleItem("ORDER", o.getId(), o.getOrderNumber(),
                customer != null ? customer + " · " + o.getTitle() : o.getTitle(),
                o.getAppointmentDate(),
                o.getAppointmentEnd() != null ? o.getAppointmentEnd() : o.getAppointmentDate(),
                o.isAppointmentAllDay(), p != null ? p.getNombreCompleto() : null);
    }

    private static CalendarBlockDto toDto(CalendarBlock b) {
        Employee e = b.getEmployee();
        return new CalendarBlockDto(b.getId(), b.getStartsAt(), b.getEndsAt(), b.isAllDay(),
                e != null ? e.getId() : null, e != null ? e.getNombreCompleto() : null,
                b.getReason(), b.getCreatedBy());
    }
}
