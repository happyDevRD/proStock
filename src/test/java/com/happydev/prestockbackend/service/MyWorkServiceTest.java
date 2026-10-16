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

    @BeforeEach
    void setUp() {
        service = new MyWorkService(userRepository, employeeRepository, orderRepository, itemRepository,
                noteRepository, serviceOrderService, workFolderService);

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
}
