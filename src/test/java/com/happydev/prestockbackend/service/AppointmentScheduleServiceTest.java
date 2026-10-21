package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ScheduleDtos.CreateCalendarBlockRequest;
import com.happydev.prestockbackend.entity.CalendarBlock;
import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.repository.CalendarBlockRepository;
import com.happydev.prestockbackend.repository.EmployeeRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentScheduleServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    @Mock private ServiceOrderRepository orderRepository;
    @Mock private CalendarBlockRepository blockRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AuditService auditService;

    private AppointmentScheduleService service;
    private Employee jocsan;
    private Employee anyara;

    @BeforeEach
    void setUp() {
        service = new AppointmentScheduleService(orderRepository, blockRepository, employeeRepository, auditService);
        jocsan = employee(1L, "Jocsan");
        anyara = employee(2L, "Anyara");
    }

    @Test
    void availability_separaChoquesDelFotografoDeOtrasSesiones() {
        LocalDateTime start = DAY.atTime(10, 0);
        LocalDateTime end = DAY.atTime(12, 0);
        ServiceOrder suya = order(5L, jocsan, DAY.atTime(11, 0), DAY.atTime(13, 0));
        ServiceOrder ajena = order(6L, anyara, DAY.atTime(10, 0), DAY.atTime(11, 0));
        ServiceOrder laMisma = order(7L, jocsan, start, end);
        CalendarBlock estudio = block(1L, null, "Feriado");
        CalendarBlock deOtro = block(2L, anyara, "Vacaciones");
        given(orderRepository.findAppointmentsOverlapping(start, end)).willReturn(List.of(suya, ajena, laMisma));
        given(blockRepository.findOverlapping(start, end)).willReturn(List.of(estudio, deOtro));

        var res = service.availability(start, end, false, 1L, 7L);

        assertEquals(List.of("BLOCK:1", "ORDER:5"), res.conflicts().stream().map(i -> i.kind() + ":" + i.id()).toList());
        assertEquals(List.of(6L), res.sameTime().stream().map(i -> i.id()).toList());
    }

    @Test
    void availability_sinFotografo_soloBloqueosDelEstudioChocan() {
        LocalDateTime start = DAY.atTime(10, 0);
        given(orderRepository.findAppointmentsOverlapping(start, DAY.atTime(11, 0)))
                .willReturn(List.of(order(5L, jocsan, start, DAY.atTime(11, 0))));
        given(blockRepository.findOverlapping(start, DAY.atTime(11, 0))).willReturn(List.of(block(2L, anyara, "Vacaciones")));

        var res = service.availability(start, null, null, null, null);

        assertTrue(res.conflicts().isEmpty());
        assertEquals(1, res.sameTime().size());
    }

    @Test
    void createBlock_diaCompletoDeUnaPersona() {
        given(employeeRepository.findById(2L)).willReturn(Optional.of(anyara));
        given(blockRepository.save(any())).willAnswer(inv -> {
            CalendarBlock b = inv.getArgument(0);
            b.setId(9L);
            return b;
        });

        var dto = service.createBlock(new CreateCalendarBlockRequest(
                DAY.atTime(8, 0), DAY.plusDays(3).atStartOfDay(), true, 2L, "  Vacaciones "), "admin");

        ArgumentCaptor<CalendarBlock> saved = ArgumentCaptor.forClass(CalendarBlock.class);
        verify(blockRepository).save(saved.capture());
        assertEquals(DAY.atStartOfDay(), saved.getValue().getStartsAt());
        assertEquals(DAY.plusDays(3).atStartOfDay(), saved.getValue().getEndsAt());
        assertEquals("Vacaciones", dto.reason());
        assertEquals("Anyara", dto.employeeName());
    }

    @Test
    void blocksFor_incluyeLosDelEstudio() {
        given(blockRepository.findOverlapping(DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay()))
                .willReturn(List.of(block(1L, null, "Feriado"), block(2L, anyara, "Vacaciones"), block(3L, jocsan, "Cita médica")));

        assertEquals(List.of(1L, 3L), service.blocksFor(1L, DAY, DAY).stream().map(b -> b.id()).toList());
    }

    private static Employee employee(Long id, String name) {
        Employee e = new Employee();
        e.setId(id);
        e.setNombreCompleto(name);
        return e;
    }

    private static ServiceOrder order(Long id, Employee photographer, LocalDateTime start, LocalDateTime end) {
        ServiceOrder o = new ServiceOrder();
        o.setId(id);
        o.setOrderNumber("OS-2026-0000" + id);
        o.setTitle("Sesión");
        o.setPhotographer(photographer);
        o.setAppointmentDate(start);
        o.setAppointmentEnd(end);
        return o;
    }

    private static CalendarBlock block(Long id, Employee employee, String reason) {
        CalendarBlock b = new CalendarBlock();
        b.setId(id);
        b.setEmployee(employee);
        b.setReason(reason);
        b.setStartsAt(DAY.atStartOfDay());
        b.setEndsAt(DAY.plusDays(1).atStartOfDay());
        b.setAllDay(true);
        return b;
    }
}
