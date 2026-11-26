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
import java.util.Set;
import java.util.function.Predicate;

/**
 * Órdenes asignadas al usuario actual (por su empleado vinculado) y los pasos
 * que puede marcar según su función. Las reglas se validan aquí, no en la interfaz.
 *
 * <p>La asignación se guarda en los campos «fotógrafo» y «editor» de la orden para todos
 * los tipos; en un taller son el técnico y su apoyo.
 *
 * <ul>
 *   <li>Fotografía. Fotógrafo: Agendada → Sesión y Sesión → Selección. Editor: Edición → Lista.</li>
 *   <li>Reparación. Técnico o apoyo, solo etapas técnicas: Recibido → Diagnóstico → Presupuesto
 *       (con el diagnóstico), Presupuesto → Aprobado solo si el mostrador ya marcó el presupuesto
 *       como aprobado, Aprobado → En reparación → Calidad (con el trabajo realizado) → Listo.
 *       Entregar, cobrar y aprobar el presupuesto quedan para el mostrador.</li>
 *   <li>General. Responsable o apoyo: Recibido → En proceso → Listo.</li>
 * </ul>
 */
@Service
public class MyWorkService {

    static final String TECH = "FOTOGRAFO";
    static final String SUPPORT = "EDITOR";

    /**
     * Paso que se puede dar desde una etapa.
     *
     * @param roles     funciones en la orden que pueden darlo
     * @param noteLabel si no es null, el paso exige una nota (p. ej. el diagnóstico) con este título
     * @param when      condición extra de la orden (p. ej. presupuesto ya aprobado)
     * @param notYet    mensaje si la condición aún no se cumple
     */
    private record Step(Set<String> roles, String to, String label, @Nullable String noteLabel,
                        Predicate<ServiceOrder> when, @Nullable String notYet) {
        Step(Set<String> roles, String to, String label) {
            this(roles, to, label, null, o -> true, null);
        }

        Step(Set<String> roles, String to, String label, String noteLabel) {
            this(roles, to, label, noteLabel, o -> true, null);
        }
    }

    private static final Set<String> BOTH = Set.of(TECH, SUPPORT);

    /** Tipo de orden → etapa actual → paso que puede dar quien tenga ese rol en la orden. */
    private static final Map<ServiceOrderType, Map<String, Step>> STEPS = Map.of(
            ServiceOrderType.PHOTOGRAPHY, Map.of(
                    "SCHEDULED", new Step(Set.of(TECH), "SESSION", "Sesión realizada"),
                    "SESSION", new Step(Set.of(TECH), "SELECTION", "Fotos cargadas: pasar a selección"),
                    "EDITING", new Step(Set.of(SUPPORT), "READY", "Edición terminada")
            ),
            ServiceOrderType.REPAIR, Map.of(
                    "RECEIVED", new Step(BOTH, "DIAGNOSIS", "Empezar diagnóstico"),
                    "DIAGNOSIS", new Step(BOTH, "BUDGET_SENT", "Diagnóstico listo: pasar a presupuesto",
                            "Diagnóstico"),
                    "BUDGET_SENT", new Step(BOTH, "APPROVED", "Presupuesto aprobado: continuar",
                            null, ServiceOrder::isBudgetApproved,
                            "El cliente todavía no aprobó el presupuesto. Cuando el mostrador lo marque aprobado podrás seguir."),
                    "APPROVED", new Step(BOTH, "IN_REPAIR", "Empezar reparación"),
                    "IN_REPAIR", new Step(BOTH, "QUALITY_CHECK", "Reparación terminada: pasar a calidad",
                            "Trabajo realizado"),
                    "QUALITY_CHECK", new Step(BOTH, "READY", "Calidad revisada: listo para entregar")
            ),
            ServiceOrderType.GENERAL, Map.of(
                    "RECEIVED", new Step(BOTH, "IN_PROGRESS", "Empezar trabajo"),
                    "IN_PROGRESS", new Step(BOTH, "READY", "Trabajo terminado: listo para entregar")
            )
    );

    /** Largo máximo de la nota que acompaña un paso. */
    static final int MAX_NOTE = 2000;

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
                        o.isAppointmentAllDay(), o.getCurrentStage(), o.getOrderType(), o.getStatus().name(),
                        myRoles(o, e)))
                .toList();
        return new MyCalendarResponse(true, items, scheduleService.blocksFor(e.getId(), from, to));
    }

    /** Marca el siguiente paso que le corresponde al usuario; falla si no le toca. */
    public void advance(Long orderId, String expectedStage, String username) {
        advance(orderId, expectedStage, null, username);
    }

    /**
     * Igual, con la nota que piden algunos pasos (diagnóstico, trabajo realizado). La nota
     * queda en las notas internas de la orden para que el mostrador la vea.
     */
    @Transactional
    public void advance(Long orderId, String expectedStage, @Nullable String note, String username) {
        Employee e = employeeOf(username).orElseThrow(() ->
                new AccessDeniedException("Tu usuario no está vinculado a un empleado."));
        ServiceOrder order = assignedOrder(orderId, e);
        Step step = allowedStep(order, e);
        if (step == null) {
            Step pending = stepFor(order);
            throw new IllegalStateException(pending != null && pending.notYet() != null && hasRole(order, e, pending)
                    ? pending.notYet()
                    : "En esta etapa no te toca avanzar la orden.");
        }
        // Evita doble clic o pantallas viejas: el paso debe ser el que vio el usuario.
        if (expectedStage != null && !expectedStage.equals(step.to())) {
            throw new IllegalStateException("La orden ya cambió de etapa. Actualiza la pantalla.");
        }
        String text = note != null ? note.trim() : "";
        if (step.noteLabel() != null && text.isEmpty()) {
            throw new IllegalArgumentException("Escribe el " + step.noteLabel().toLowerCase() + " antes de marcar «"
                    + step.label() + "».");
        }
        if (text.length() > MAX_NOTE) {
            throw new IllegalArgumentException("La nota es muy larga (máximo " + MAX_NOTE + " caracteres).");
        }
        requireMaterial(order, step);
        ServiceOrderStatus status = ServiceOrderStageCatalog.findStage(order.getOrderType(), step.to())
                .orElseThrow()
                .status();
        serviceOrderService.advanceStage(orderId, new AdvanceStageRequest(step.to(), status, step.label()), username);
        if (!text.isEmpty()) {
            String content = step.noteLabel() != null ? step.noteLabel() + ": " + text : text;
            serviceOrderService.addNote(orderId, new AddNoteRequest(content, true), username);
        }
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
        if (order.getOrderType() != ServiceOrderType.PHOTOGRAPHY) return;
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
    private static Step stepFor(ServiceOrder o) {
        return STEPS.getOrDefault(o.getOrderType(), Map.of()).get(o.getCurrentStage());
    }

    private static boolean hasRole(ServiceOrder o, Employee e, Step step) {
        return myRoles(o, e).stream().anyMatch(step.roles()::contains);
    }

    @Nullable
    private static Step allowedStep(ServiceOrder o, Employee e) {
        Step step = stepFor(o);
        if (step == null || !step.when().test(o)) return null;
        return hasRole(o, e, step) ? step : null;
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
                o.getEstimatedDelivery(), o.getCurrentStage(), o.getOrderType(),
                myRoles(o, e),
                o.getPhotographer() != null ? o.getPhotographer().getNombreCompleto() : null,
                o.getEditor() != null ? o.getEditor().getNombreCompleto() : null,
                workFolderService.displayPath(o.getWorkFolder()),
                workFolderService.fileCounts(o.getWorkFolder()),
                o.getInternalNotes(),
                services, notes,
                step != null ? new MyWorkAction(step.to(), step.label(), step.noteLabel()) : null
        );
    }
}
