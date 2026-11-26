package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AdvanceStageRequest;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyWorkServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ServiceOrderRepository orderRepository;
    @Mock private ServiceOrderItemRepository itemRepository;
    @Mock private ServiceOrderNoteRepository noteRepository;
    @Mock private ServiceOrderService serviceOrderService;
    @Mock private WorkFolderService workFolderService;

    private MyWorkService service;
    private Employee juan;   // fotógrafo de la orden
    private Employee ana;    // editora de la orden
    private ServiceOrder order;
    @Mock private AppointmentScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        service = new MyWorkService(userRepository, employeeRepository, orderRepository, itemRepository,
                noteRepository, serviceOrderService, workFolderService, scheduleService);

        juan = employee(1L, "Juan", "juan");
        ana = employee(2L, "Ana", "ana");

        order = new ServiceOrder();
        order.setId(10L);
        order.setOrderNumber("OS-2026-00010");
        order.setOrderType(ServiceOrderType.PHOTOGRAPHY);
        order.setStatus(ServiceOrderStatus.OPEN);
        order.setCurrentStage("SCHEDULED");
        order.setPhotographer(juan);
        order.setEditor(ana);
        given(orderRepository.findById(10L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(anyLong())).willReturn(List.of());
        given(noteRepository.findByServiceOrderIdOrderByCreatedAtAsc(anyLong())).willReturn(List.of());
        // Sin carpeta (función apagada) no se valida el material
        given(workFolderService.fileCounts(any())).willReturn(null);
    }

    private Employee employee(Long id, String name, String username) {
        Employee e = new Employee();
        e.setId(id);
        e.setNombreCompleto(name);
        e.setUserId(id + 100);
        User u = new User();
        u.setId(id + 100);
        u.setUsername(username);
        given(userRepository.findByUsername(username)).willReturn(Optional.of(u));
        given(employeeRepository.findByUserId(id + 100)).willReturn(Optional.of(e));
        return e;
    }

    @Test
    void photographerMarksSessionDone() {
        service.advance(10L, "SESSION", "juan");

        ArgumentCaptor<AdvanceStageRequest> req = ArgumentCaptor.forClass(AdvanceStageRequest.class);
        verify(serviceOrderService).advanceStage(eq(10L), req.capture(), eq("juan"));
        assertEquals("SESSION", req.getValue().stage());
        assertEquals(ServiceOrderStatus.IN_PROGRESS, req.getValue().status());
    }

    @Test
    void editorCannotAdvanceWhileOrderIsScheduled() {
        assertThrows(IllegalStateException.class, () -> service.advance(10L, null, "ana"));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());
    }

    @Test
    void editorFinishesEditing() {
        order.setCurrentStage("EDITING");
        service.advance(10L, "READY", "ana");
        verify(serviceOrderService).advanceStage(eq(10L), any(), eq("ana"));
    }

    @Test
    void photographerCannotSkipClientSelection() {
        order.setCurrentStage("SELECTION");
        assertThrows(IllegalStateException.class, () -> service.advance(10L, null, "juan"));
    }

    @Test
    void staleScreenIsRejected() {
        order.setCurrentStage("SESSION"); // otro ya marcó la sesión; la pantalla vieja pide SESSION otra vez
        assertThrows(IllegalStateException.class, () -> service.advance(10L, "SESSION", "juan"));
    }

    @Test
    void ordersOfOthersLookLikeTheyDoNotExist() {
        employee(3L, "Pedro", "pedro");
        assertThrows(ResourceNotFoundException.class, () -> service.advance(10L, null, "pedro"));
        assertThrows(ResourceNotFoundException.class, () -> service.addNote(10L, "hola", "pedro"));
    }

    @Test
    void userWithoutEmployeeCannotAdvanceAndSeesNothing() {
        User u = new User();
        u.setId(999L);
        u.setUsername("cajera");
        given(userRepository.findByUsername("cajera")).willReturn(Optional.of(u));
        given(employeeRepository.findByUserId(999L)).willReturn(Optional.empty());

        assertFalse(service.list("cajera").linked());
        assertThrows(AccessDeniedException.class, () -> service.advance(10L, null, "cajera"));
    }

    @Test
    void listShowsRolesAndOnlyTheActionThatBelongsToTheUser() {
        given(orderRepository.findAssignedTo(eq(1L), anyList())).willReturn(List.of(order));
        given(orderRepository.findAssignedTo(eq(2L), anyList())).willReturn(List.of(order));

        var forJuan = service.list("juan").orders().get(0);
        assertEquals(List.of("FOTOGRAFO"), forJuan.myRoles());
        assertEquals("SESSION", forJuan.nextAction().stage());

        var forAna = service.list("ana").orders().get(0);
        assertEquals(List.of("EDITOR"), forAna.myRoles());
        assertNull(forAna.nextAction());
    }

    @Test
    void cannotSendToSelectionWithEmptyCameraFolder() {
        order.setCurrentStage("SESSION");
        order.setWorkFolder("2 En sesion/OS-2026-00010 X");
        given(workFolderService.fileCounts("2 En sesion/OS-2026-00010 X"))
                .willReturn(java.util.Map.of("1 Camara", 0, "4 Final", 0));

        var e = assertThrows(IllegalStateException.class, () -> service.advance(10L, "SELECTION", "juan"));
        assertTrue(e.getMessage().contains("1 Camara"));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());
    }

    @Test
    void sendsToSelectionWhenCameraFolderHasFiles() {
        order.setCurrentStage("SESSION");
        order.setWorkFolder("2 En sesion/OS-2026-00010 X");
        given(workFolderService.fileCounts("2 En sesion/OS-2026-00010 X"))
                .willReturn(java.util.Map.of("1 Camara", 412, "4 Final", 0));

        service.advance(10L, "SELECTION", "juan");
        verify(serviceOrderService).advanceStage(eq(10L), any(), eq("juan"));
    }

    @Test
    void cannotFinishEditingWithEmptyFinalFolder() {
        order.setCurrentStage("EDITING");
        order.setWorkFolder("4 En edicion/OS-2026-00010 X");
        given(workFolderService.fileCounts("4 En edicion/OS-2026-00010 X"))
                .willReturn(java.util.Map.of("1 Camara", 412, "4 Final", 0));

        assertThrows(IllegalStateException.class, () -> service.advance(10L, "READY", "ana"));
    }

    @Test
    void calendar_onlyMyAppointmentsWithMyRoles() {
        java.time.LocalDate day = java.time.LocalDate.of(2026, 11, 7);
        order.setAppointmentDate(day.atTime(10, 0));
        order.setAppointmentEnd(day.atTime(12, 0));
        ServiceOrder other = new ServiceOrder();
        other.setId(11L);
        other.setOrderType(ServiceOrderType.PHOTOGRAPHY);
        other.setStatus(ServiceOrderStatus.OPEN);
        other.setCurrentStage("SCHEDULED");
        other.setAppointmentDate(day.atTime(15, 0));
        other.setAppointmentEnd(day.atTime(16, 0));
        given(orderRepository.findAppointmentsOverlapping(day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .willReturn(List.of(order, other));
        given(scheduleService.blocksFor(2L, day, day)).willReturn(List.of());

        var res = service.calendar("ana", day, day);

        assertTrue(res.linked());
        assertEquals(1, res.appointments().size());
        var item = res.appointments().get(0);
        assertEquals(10L, item.id());
        assertEquals(List.of("EDITOR"), item.myRoles());
        assertEquals(day.atTime(12, 0), item.end());
    }

    // --- Reparación: el «fotógrafo» es el técnico y el «editor» su apoyo ---

    private void repair(String stage) {
        order.setOrderType(ServiceOrderType.REPAIR);
        order.setCurrentStage(stage);
        order.setStatus(ServiceOrderStageCatalog.findStage(ServiceOrderType.REPAIR, stage).orElseThrow().status());
    }

    private AdvanceStageRequest advancedTo() {
        ArgumentCaptor<AdvanceStageRequest> req = ArgumentCaptor.forClass(AdvanceStageRequest.class);
        verify(serviceOrderService).advanceStage(eq(10L), req.capture(), anyString());
        return req.getValue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "RECEIVED, DIAGNOSIS, IN_PROGRESS",
            "APPROVED, IN_REPAIR, IN_PROGRESS",
            "QUALITY_CHECK, READY, READY"
    })
    void technicianAdvancesTechnicalStages(String from, String to, ServiceOrderStatus status) {
        repair(from);
        service.advance(10L, to, "juan");
        assertEquals(to, advancedTo().stage());
        assertEquals(status, advancedTo().status());
        verify(serviceOrderService, never()).addNote(anyLong(), any(), any());
    }

    @Test
    void supportTechnicianCanAlsoAdvance() {
        repair("APPROVED");
        service.advance(10L, "IN_REPAIR", "ana");
        assertEquals("IN_REPAIR", advancedTo().stage());
    }

    @Test
    void diagnosisNeedsTheDiagnosisAndSavesItAsInternalNote() {
        repair("DIAGNOSIS");
        var e = assertThrows(IllegalArgumentException.class, () -> service.advance(10L, "BUDGET_SENT", "  ", "juan"));
        assertTrue(e.getMessage().contains("diagnóstico"));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());

        service.advance(10L, "BUDGET_SENT", " Pantalla rota, cambiar display ", "juan");
        assertEquals("BUDGET_SENT", advancedTo().stage());
        assertEquals(ServiceOrderStatus.WAITING_CLIENT, advancedTo().status());
        ArgumentCaptor<com.happydev.prestockbackend.dto.AddNoteRequest> note =
                ArgumentCaptor.forClass(com.happydev.prestockbackend.dto.AddNoteRequest.class);
        verify(serviceOrderService).addNote(eq(10L), note.capture(), eq("juan"));
        assertEquals("Diagnóstico: Pantalla rota, cambiar display", note.getValue().content());
        assertTrue(note.getValue().internal());
    }

    @Test
    void finishingTheRepairNeedsWhatWasDone() {
        repair("IN_REPAIR");
        assertThrows(IllegalArgumentException.class, () -> service.advance(10L, "QUALITY_CHECK", "juan"));
        service.advance(10L, "QUALITY_CHECK", "Display cambiado", "juan");
        assertEquals("QUALITY_CHECK", advancedTo().stage());
    }

    @Test
    void tooLongNoteIsRejected() {
        repair("IN_REPAIR");
        String longNote = "x".repeat(MyWorkService.MAX_NOTE + 1);
        assertThrows(IllegalArgumentException.class, () -> service.advance(10L, "QUALITY_CHECK", longNote, "juan"));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());
    }

    @Test
    void technicianCannotApproveTheBudgetForTheClient() {
        repair("BUDGET_SENT");
        var e = assertThrows(IllegalStateException.class, () -> service.advance(10L, "APPROVED", "juan"));
        assertTrue(e.getMessage().contains("no aprobó el presupuesto"));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());
    }

    @Test
    void onceTheCounterApprovedTheBudgetTheTechnicianContinues() {
        repair("BUDGET_SENT");
        order.setBudgetApproved(true);
        service.advance(10L, "APPROVED", "juan");
        assertEquals("APPROVED", advancedTo().stage());
    }

    @Test
    void technicianNeverDelivers() {
        repair("READY");
        assertThrows(IllegalStateException.class, () -> service.advance(10L, "DELIVERED", "juan"));
        assertThrows(IllegalStateException.class, () -> service.advance(10L, null, "ana"));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());
        verify(serviceOrderService, never()).complete(anyLong(), any());
    }

    @Test
    void technicianCannotSkipStages() {
        repair("RECEIVED");
        // Pide saltar a «En reparación»: el único paso permitido es Diagnóstico
        assertThrows(IllegalStateException.class, () -> service.advance(10L, "IN_REPAIR", "juan"));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());
    }

    @Test
    void repairOrderOfAnotherTechnicianLooksLikeItDoesNotExist() {
        repair("RECEIVED");
        employee(3L, "Pedro", "pedro");
        assertThrows(ResourceNotFoundException.class, () -> service.advance(10L, "DIAGNOSIS", "pedro"));
    }

    @Test
    void closedRepairCannotBeAdvanced() {
        repair("QUALITY_CHECK");
        order.setStatus(ServiceOrderStatus.CANCELLED);
        assertThrows(ResourceNotFoundException.class, () -> service.advance(10L, "READY", "juan"));
    }

    @Test
    void readyRepairDoesNotAskForTheFinalFolder() {
        repair("QUALITY_CHECK");
        order.setWorkFolder("carpeta");
        given(workFolderService.fileCounts("carpeta")).willReturn(java.util.Map.of("4 Final", 0));
        service.advance(10L, "READY", "juan");
        assertEquals("READY", advancedTo().stage());
    }

    @Test
    void listOffersRepairStepsWithTypeAndNoteLabel() {
        repair("DIAGNOSIS");
        given(orderRepository.findAssignedTo(eq(1L), anyList())).willReturn(List.of(order));
        var dto = service.list("juan").orders().get(0);
        assertEquals(ServiceOrderType.REPAIR, dto.orderType());
        assertEquals("BUDGET_SENT", dto.nextAction().stage());
        assertEquals("Diagnóstico", dto.nextAction().noteLabel());

        repair("BUDGET_SENT");
        assertNull(service.list("juan").orders().get(0).nextAction());
        repair("READY");
        assertNull(service.list("juan").orders().get(0).nextAction());
    }

    @Test
    void generalOrderResponsibleAdvancesToReady() {
        order.setOrderType(ServiceOrderType.GENERAL);
        order.setCurrentStage("IN_PROGRESS");
        order.setStatus(ServiceOrderStatus.IN_PROGRESS);
        service.advance(10L, "READY", "juan");
        assertEquals("READY", advancedTo().stage());
        assertEquals(ServiceOrderStatus.READY, advancedTo().status());
    }
}
