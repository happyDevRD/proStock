package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import com.happydev.prestockbackend.repository.ServiceOrderItemRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkFolderServiceTest {

    @Mock private ServiceOrderRepository orderRepository;
    @Mock private ServiceOrderItemRepository itemRepository;
    @TempDir Path root;

    private WorkFolderService service;
    private ServiceOrder order;

    @BeforeEach
    void setUp() {
        service = new WorkFolderService(orderRepository, itemRepository, root.toString(), "\\\\ESTUDIO-NAS-01\\Ordenes");

        Customer customer = new Customer();
        customer.setFirstName("María");
        customer.setLastName("Pérez");
        customer.setPhoneNumber("809-555-1234");

        Employee photographer = new Employee();
        photographer.setNombreCompleto("Juan Fotógrafo");

        order = new ServiceOrder();
        order.setId(7L);
        order.setOrderNumber("OS-2026-00007");
        order.setOrderType(ServiceOrderType.PHOTOGRAPHY);
        order.setTitle("Combo XV Años 2");
        order.setCurrentStage("SCHEDULED");
        order.setCustomer(customer);
        order.setPhotographer(photographer);
        order.setAppointmentDate(LocalDateTime.of(2026, 11, 3, 15, 0));
    }

    @Test
    void createsFolderWithSubfoldersAndInfo() throws Exception {
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(7L)).willReturn(List.of());

        service.sync(7L);

        Path folder = root.resolve("1 Agendadas").resolve("OS-2026-00007 María Pérez");
        assertTrue(Files.isDirectory(folder));
        for (String sub : WorkFolderService.SUBFOLDERS) {
            assertTrue(Files.isDirectory(folder.resolve(sub)), sub);
        }
        assertEquals("1 Agendadas/OS-2026-00007 María Pérez", order.getWorkFolder());

        String info = Files.readString(folder.resolve("INFO.txt"), StandardCharsets.UTF_8);
        assertTrue(info.contains("Teléfono: 809-555-1234"));
        assertTrue(info.contains("Fotógrafo: Juan Fotógrafo"));
        assertTrue(info.contains("Editor: -"));
        assertFalse(Files.exists(folder.resolve("INFO.txt.tmp")));
    }

    @Test
    void secondSyncKeepsExistingFolderAndOnlyRewritesInfo() throws Exception {
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(7L)).willReturn(List.of());
        service.sync(7L);
        String first = order.getWorkFolder();

        order.getCustomer().setFirstName("Mariela");
        service.sync(7L);

        assertEquals(first, order.getWorkFolder());
        try (var dirs = Files.list(root.resolve("1 Agendadas"))) {
            assertEquals(1, dirs.count(), "no crea otra carpeta aunque cambie el nombre del cliente");
        }
        String info = Files.readString(root.resolve(first).resolve("INFO.txt"), StandardCharsets.UTF_8);
        assertTrue(info.contains("Cliente: Mariela Pérez"));
    }

    @Test
    void ignoresNonPhotographyOrders() throws Exception {
        order.setOrderType(ServiceOrderType.REPAIR);
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));

        service.sync(7L);

        try (var dirs = Files.list(root)) {
            assertEquals(0, dirs.count());
        }
    }

    @Test
    void disabledWhenRootIsBlank() throws Exception {
        WorkFolderService disabled = new WorkFolderService(orderRepository, itemRepository, "", "");
        assertFalse(disabled.isEnabled());
        disabled.sync(7L);
        verify(orderRepository, never()).findById(anyLong());
    }

    @Test
    void folderNameDropsCharactersWindowsRejects() {
        order.getCustomer().setFirstName("Ana: \"La/Reina\"");
        order.getCustomer().setLastName("Díaz.");
        assertEquals("OS-2026-00007 Ana La Reina Díaz", WorkFolderService.folderName(order));
    }

    @Test
    void displayPathUsesWindowsSeparators() {
        assertEquals("\\\\ESTUDIO-NAS-01\\Ordenes\\1 Agendadas\\OS-2026-00007 María Pérez",
                service.displayPath("1 Agendadas/OS-2026-00007 María Pérez"));
        assertNull(service.displayPath(null));
    }

    @Test
    void movesFolderWhenStageChanges() throws Exception {
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(7L)).willReturn(List.of());
        service.sync(7L);
        Files.writeString(root.resolve(order.getWorkFolder()).resolve("1 Camara").resolve("IMG_0001.CR3"), "raw");

        order.setCurrentStage("SELECTION");
        service.sync(7L);

        assertEquals("3 En seleccion/OS-2026-00007 María Pérez", order.getWorkFolder());
        Path moved = root.resolve("3 En seleccion").resolve("OS-2026-00007 María Pérez");
        assertTrue(Files.exists(moved.resolve("1 Camara").resolve("IMG_0001.CR3")), "el contenido se mueve con la carpeta");
        assertFalse(Files.exists(root.resolve("1 Agendadas").resolve("OS-2026-00007 María Pérez")));
    }

    @Test
    void cancelledOrdersGoToCancelledFolder() throws Exception {
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(7L)).willReturn(List.of());
        service.sync(7L);

        order.setStatus(ServiceOrderStatus.CANCELLED);
        order.setCurrentStage("CANCELLED");
        service.sync(7L);

        assertEquals("7 Canceladas/OS-2026-00007 María Pérez", order.getWorkFolder());
    }

    @Test
    void findsFolderMovedByHandInsteadOfCreatingAnother() throws Exception {
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(7L)).willReturn(List.of());
        service.sync(7L);
        // Alguien la arrastró a otra etapa desde el Explorador
        Files.createDirectories(root.resolve("4 En edicion"));
        Files.move(root.resolve(order.getWorkFolder()), root.resolve("4 En edicion").resolve("OS-2026-00007 María Pérez"));

        order.setCurrentStage("SESSION");
        service.sync(7L);

        assertEquals("2 En sesion/OS-2026-00007 María Pérez", order.getWorkFolder());
        try (var edicion = Files.list(root.resolve("4 En edicion"))) {
            assertEquals(0, edicion.count());
        }
    }

    @Test
    void doesNotOverwriteAnExistingFolderWithTheSameName() throws Exception {
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(7L)).willReturn(List.of());
        service.sync(7L);
        Files.createDirectories(root.resolve("2 En sesion").resolve("OS-2026-00007 María Pérez"));

        order.setCurrentStage("SESSION");
        service.sync(7L);

        assertEquals("2 En sesion/OS-2026-00007 María Pérez (2)", order.getWorkFolder());
    }

    @Test
    void countsFilesPerSubfolderIgnoringSystemJunk() throws Exception {
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(7L)).willReturn(List.of());
        service.sync(7L);
        Path folder = root.resolve(order.getWorkFolder());
        Path camUno = Files.createDirectories(folder.resolve("1 Camara").resolve("CAMARA UNO"));
        Files.writeString(camUno.resolve("IMG_0001.CR3"), "x");
        Files.writeString(camUno.resolve("IMG_0002.CR3"), "x");
        Files.writeString(folder.resolve("1 Camara").resolve(".DS_Store"), "x");
        Files.createDirectories(folder.resolve("1 Camara").resolve("@eaDir"));
        Files.writeString(folder.resolve("1 Camara").resolve("@eaDir").resolve("thumb.jpg"), "x");
        Files.writeString(folder.resolve("4 Final").resolve("final.jpg"), "x");

        var counts = service.fileCounts(order.getWorkFolder());

        assertEquals(2, counts.get("1 Camara"));
        assertEquals(0, counts.get("2 Seleccion"));
        assertEquals(1, counts.get("4 Final"));
        assertNull(service.fileCounts("no/existe"));
    }
}
