package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AddNoteRequest;
import com.happydev.prestockbackend.dto.AdvanceStageRequest;
import com.happydev.prestockbackend.dto.MyWorkDtos.MyCalendarItem;
import com.happydev.prestockbackend.dto.MyWorkDtos.MyCalendarResponse;
import com.happydev.prestockbackend.dto.MyWorkDtos.MyWorkAction;
import com.happydev.prestockbackend.dto.MyWorkDtos.MyWorkNote;
import com.happydev.prestockbackend.dto.MyWorkDtos.MyWorkOrder;
import com.happydev.prestockbackend.dto.MyWorkDtos.MyWorkResponse;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.EmployeeRepository;
import com.happydev.prestockbackend.repository.ServiceOrderItemRepository;
import com.happydev.prestockbackend.repository.ServiceOrderNoteRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.repository.UserRepository;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Órdenes asignadas al usuario actual (por su empleado vinculado) y los pasos
 * que puede marcar según su función. Las reglas se validan aquí, no en la interfaz:
 *
 * <ul>
 *   <li>Fotógrafo: Agendada → Sesión ("sesión realizada") y Sesión → Selección ("fotos cargadas").</li>
 *   <li>Editor: Edición → Lista ("edición terminada").</li>
 * </ul>
 */
@Service
public class MyWorkService {

    private record Step(String role, String to, ServiceOrderStatus status, String label) {}

    /** Etapa actual → paso que puede dar quien tenga ese rol en la orden. */
    private static final Map<String, Step> STEPS = Map.of(
            "SCHEDULED", new Step("FOTOGRAFO", "SESSION", ServiceOrderStatus.IN_PROGRESS, "Sesión realizada"),
            "SESSION", new Step("FOTOGRAFO", "SELECTION", ServiceOrderStatus.WAITING_CLIENT, "Fotos cargadas: pasar a selección"),
            "EDITING", new Step("EDITOR", "READY", ServiceOrderStatus.READY, "Edición terminada")
    );

    private static final List<ServiceOrderStatus> CLOSED =
            List.of(ServiceOrderStatus.COMPLETED, ServiceOrderStatus.CANCELLED);

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderItemRepository itemRepository;
    private final ServiceOrderNoteRepository noteRepository;
    private final ServiceOrderService serviceOrderService;
    private final WorkFolderService workFolderService;
    private final AppointmentScheduleService scheduleService;

    public MyWorkService(UserRepository userRepository,
                         EmployeeRepository employeeRepository,
                         ServiceOrderRepository orderRepository,
                         ServiceOrderItemRepository itemRepository,
                         ServiceOrderNoteRepository noteRepository,
                         ServiceOrderService serviceOrderService,
                         WorkFolderService workFolderService,
                         AppointmentScheduleService scheduleService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.noteRepository = noteRepository;
        this.serviceOrderService = serviceOrderService;
        this.workFolderService = workFolderService;
        this.scheduleService = scheduleService;
    }

    @Transactional(readOnly = true)
    public MyWorkResponse list(String username) {
        Optional<Employee> employee = employeeOf(username);
        if (employee.isEmpty()) {
            return new MyWorkResponse(false, null, List.of());
        }
        Employee e = employee.get();
        List<MyWorkOrder> orders = orderRepository.findAssignedTo(e.getId(), CLOSED).stream()
                .map(o -> toDto(o, e))
                .toList();
        return new MyWorkResponse(true, e.getNombreCompleto(), orders);
    }

    /** Agenda del usuario entre {@code from} y {@code to} (inclusive), también de órdenes ya cerradas. */
    @Transactional(readOnly = true)
    public MyCalendarResponse calendar(String username, LocalDate from, LocalDate to) {
        AppointmentScheduleService.checkRange(from, to);
        Optional<Employee> employee = employeeOf(username);
        if (employee.isEmpty()) {
            return new MyCalendarResponse(false, List.of(), List.of());
        }
        Employee e = employee.get();
        List<MyCalendarItem> items = orderRepository
                .findAppointmentsOverlapping(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .filter(o -> !myRoles(o, e).isEmpty())
                .map(o -> new MyCalendarItem(o.getId(), o.getOrderNumber(), o.getTitle(), customerName(o.getCustomer()),
                        o.getAppointmentDate(),
                        o.getAppointmentEnd() != null ? o.getAppointmentEnd() : o.getAppointmentDate(),
                        o.isAppointmentAllDay(), o.getCurrentStage(), o.getStatus().name(), myRoles(o, e)))
                .toList();
        return new MyCalendarResponse(true, items, scheduleService.blocksFor(e.getId(), from, to));
    }

    /** Marca el siguiente paso que le corresponde al usuario; falla si no le toca. */
    public void advance(Long orderId, String expectedStage, String username) {
        Employee e = employeeOf(username).orElseThrow(() ->
                new AccessDeniedException("Tu usuario no está vinculado a un empleado."));
        ServiceOrder order = assignedOrder(orderId, e);
        Step step = allowedStep(order, e);
        if (step == null) {
            throw new IllegalStateException("En esta etapa no te toca avanzar la orden.");
        }
        // Evita doble clic o pantallas viejas: el paso debe ser el que vio el usuario.
        if (expectedStage != null && !expectedStage.equals(step.to())) {
            throw new IllegalStateException("La orden ya cambió de etapa. Actualiza la pantalla.");
        }
        requireMaterial(order, step);
        serviceOrderService.advanceStage(orderId, new AdvanceStageRequest(step.to(), step.status(), step.label()), username);
    }

    public void addNote(Long orderId, String content, String username) {
        Employee e = employeeOf(username).orElseThrow(() ->
                new AccessDeniedException("Tu usuario no está vinculado a un empleado."));
        assignedOrder(orderId, e);
        serviceOrderService.addNote(orderId, new AddNoteRequest(content.trim(), true), username);
    }

    /**
     * No deja avanzar sin el material en la carpeta: fotos en "1 Camara" para
     * pasar a selección y fotos en "4 Final" para dar la edición por terminada.
     * Si la orden no tiene carpeta (función apagada) no se valida.
     */
    private void requireMaterial(ServiceOrder order, Step step) {
        String sub = switch (step.to()) {
            case "SELECTION" -> "1 Camara";
            case "READY" -> "4 Final";
            default -> null;
        };
        if (sub == null) return;
        Map<String, Integer> counts = workFolderService.fileCounts(order.getWorkFolder());
        if (counts != null && counts.getOrDefault(sub, 0) == 0) {
            throw new IllegalStateException("La carpeta «" + sub + "» está vacía. Copia ahí las fotos antes de marcar «"
                    + step.label() + "».");
        }
    }

    private Optional<Employee> employeeOf(String username) {
        return userRepository.findByUsername(username)
                .flatMap(u -> employeeRepository.findByUserId(u.getId()));
    }

    /** La orden debe existir, estar abierta y tener a este empleado como fotógrafo o editor. */
    private ServiceOrder assignedOrder(Long orderId, Employee e) {
        ServiceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", "id", orderId));
        if (myRoles(order, e).isEmpty() || CLOSED.contains(order.getStatus())) {
            // Mismo mensaje que si no existiera: no revela órdenes ajenas.
            throw new ResourceNotFoundException("ServiceOrder", "id", orderId);
        }
        return order;
    }

    private static List<String> myRoles(ServiceOrder o, Employee e) {
        List<String> roles = new ArrayList<>();
        if (o.getPhotographer() != null && o.getPhotographer().getId().equals(e.getId())) roles.add("FOTOGRAFO");
        if (o.getEditor() != null && o.getEditor().getId().equals(e.getId())) roles.add("EDITOR");
        return roles;
    }

    @Nullable
    private static Step allowedStep(ServiceOrder o, Employee e) {
        Step step = STEPS.get(o.getCurrentStage());
        if (step == null || o.getOrderType() != ServiceOrderType.PHOTOGRAPHY) return null;
        return myRoles(o, e).contains(step.role()) ? step : null;
    }

    @Nullable
    private static String customerName(@Nullable Customer c) {
        return c != null ? (c.getFirstName() + " " + (c.getLastName() != null ? c.getLastName() : "")).trim() : null;
    }

    private MyWorkOrder toDto(ServiceOrder o, Employee e) {
        Customer c = o.getCustomer();
        Step step = allowedStep(o, e);
        List<String> services = itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(o.getId()).stream()
                .map(i -> i.getQuantity() + " x " + i.getProduct().getName()
                        + (i.getNotes() != null && !i.getNotes().isBlank() ? " (" + i.getNotes().trim() + ")" : ""))
                .toList();
        List<MyWorkNote> notes = noteRepository.findByServiceOrderIdOrderByCreatedAtAsc(o.getId()).stream()
                .map(n -> new MyWorkNote(n.getContent(), n.getAuthor(), n.getCreatedAt()))
                .toList();
        return new MyWorkOrder(
                o.getId(), o.getOrderNumber(), o.getTitle(),
                customerName(c),
                c != null ? c.getPhoneNumber() : null,
                o.getAppointmentDate(), o.getAppointmentEnd(), o.isAppointmentAllDay(),
                o.getEstimatedDelivery(), o.getCurrentStage(),
                myRoles(o, e),
                o.getPhotographer() != null ? o.getPhotographer().getNombreCompleto() : null,
                o.getEditor() != null ? o.getEditor().getNombreCompleto() : null,
                workFolderService.displayPath(o.getWorkFolder()),
                workFolderService.fileCounts(o.getWorkFolder()),
                o.getInternalNotes(),
                services, notes,
                step != null ? new MyWorkAction(step.to(), step.label()) : null
        );
    }
}
