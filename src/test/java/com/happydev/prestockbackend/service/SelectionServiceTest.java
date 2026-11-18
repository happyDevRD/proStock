package com.happydev.prestockbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.AddServiceOrderItemRequest;
import com.happydev.prestockbackend.dto.AdvanceStageRequest;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelectionServiceTest {

    @Mock private ServiceOrderSelectionRepository selectionRepository;
    @Mock private ServiceOrderRepository orderRepository;
    @Mock private ServiceOrderItemRepository itemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CompanyConfigRepository companyConfigRepository;
    @Mock private ServiceOrderService serviceOrderService;
    @Mock private StudioNotifier notifier;
    @TempDir Path root;

    private SelectionService service;
    private ServiceOrder order;
    private ServiceOrderSelection sel;
    private Product extra;
    private Path folder;

    @BeforeEach
    void setUp() throws Exception {
        WorkFolderService folders = new WorkFolderService(orderRepository, itemRepository, root.toString(), "");
        service = new SelectionService(selectionRepository, orderRepository, itemRepository, productRepository,
                companyConfigRepository, serviceOrderService, folders, new ObjectMapper(),
                "https://iridiscencia.com/seleccion/", "ADI-FOTO-DIG", "", notifier);
        service.setAccess(new SelectionAccessService(selectionRepository,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4), "secreto", java.time.Clock.systemUTC()));

        folder = Files.createDirectories(root.resolve("3 En seleccion").resolve("OS-2026-00009 Ana"));
        Path previews = Files.createDirectories(folder.resolve("2 Seleccion"));
        Path cam = Files.createDirectories(folder.resolve("1 Camara").resolve("CAMARA UNO"));
        for (String n : List.of("IMG_0001", "IMG_0002", "IMG_0003")) {
            Files.writeString(previews.resolve(n + ".jpg"), "jpg");
            Files.writeString(cam.resolve(n + ".CR3"), "raw-" + n);
        }
        Files.writeString(previews.resolve(".DS_Store"), "x");

        Customer c = new Customer();
        c.setFirstName("Ana María");
        order = new ServiceOrder();
        order.setId(9L);
        order.setOrderNumber("OS-2026-00009");
        order.setOrderType(ServiceOrderType.PHOTOGRAPHY);
        order.setCurrentStage("SELECTION");
        order.setStatus(ServiceOrderStatus.WAITING_CLIENT);
        order.setTitle("Combo XV Años 2");
        order.setCustomer(c);
        order.setWorkFolder("3 En seleccion/OS-2026-00009 Ana");
        given(orderRepository.findById(9L)).willReturn(Optional.of(order));

        extra = new Product();
        extra.setId(44L);
        extra.setSku("ADI-FOTO-DIG");
        extra.setName("Foto Extra Digital");
        extra.setSellingPrice(new BigDecimal("300.00"));
        extra.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        given(productRepository.findBySku("ADI-FOTO-DIG")).willReturn(Optional.of(extra));

        CompanyConfig cfg = new CompanyConfig();
        cfg.setNombreComercial("Iridiscencia");
        cfg.setPreciosIncluyenItbis(true);
        given(companyConfigRepository.findFirstByOrderByIdAsc()).willReturn(Optional.of(cfg));

        sel = new ServiceOrderSelection();
        sel.setServiceOrder(order);
        sel.setToken("tok");
        sel.setQuota(2);
        sel.setExtraProduct(extra);
        sel.setExpiresAt(LocalDateTime.now().plusDays(10));
        given(selectionRepository.findByToken("tok")).willReturn(Optional.of(sel));
        given(selectionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void clientSeesOnlyImagePreviewsAndFirstName() {
        var view = service.view("tok");
        assertEquals(List.of("IMG_0001.jpg", "IMG_0002.jpg", "IMG_0003.jpg"), view.photos());
        assertEquals("Ana", view.customerFirstName());
        assertEquals("Iridiscencia", view.studio());
        assertEquals(new BigDecimal("300.00"), view.extraUnitPrice(), "precio con ITBIS: el catálogo ya lo incluye");
    }

    @Test
    void cannotReadFilesOutsideThePreviewFolder() {
        assertThrows(ResourceNotFoundException.class, () -> service.preview("tok", "../1 Camara/CAMARA UNO/IMG_0001.CR3"));
        assertThrows(ResourceNotFoundException.class, () -> service.preview("tok", ".DS_Store"));
    }

    @Test
    void draftKeepsOnlyRealPreviews() {
        var view = service.saveDraft("tok", List.of("IMG_0002.jpg", "../../etc/passwd", "IMG_0002.jpg"));
        assertEquals(List.of("IMG_0002.jpg"), view.selected());
    }

    @Test
    void expiredLinkIsRejected() {
        sel.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalStateException.class, () -> service.view("tok"));
    }

    @Test
    void extraPhotosNeedExplicitAcceptance() {
        assertThrows(IllegalStateException.class,
                () -> service.submit("tok", List.of("IMG_0001.jpg", "IMG_0002.jpg", "IMG_0003.jpg"), false));
        verify(serviceOrderService, never()).advanceStage(anyLong(), any(), any());
    }

    @Test
    void submitSendsIncludedToEditingAndLeavesExtrasPending() throws Exception {
        // Marcó en este orden: las 2 primeras son del paquete (cupo 2), la tercera es adicional
        var view = service.submit("tok", List.of("IMG_0001.jpg", "IMG_0003.jpg", "IMG_0002.jpg"), true);

        // Solo los originales de las incluidas pasan a "3 Edicion" (enlazados, sin copiar)
        Path editing = folder.resolve("3 Edicion");
        assertEquals("raw-IMG_0003", Files.readString(editing.resolve("IMG_0003.CR3")));
        assertTrue(Files.isSameFile(editing.resolve("IMG_0001.CR3"), folder.resolve("1 Camara/CAMARA UNO/IMG_0001.CR3")));
        assertFalse(Files.exists(editing.resolve("IMG_0002.CR3")), "la adicional espera autorización");
        assertFalse(Files.exists(editing.resolve("Adicionales")));
        String summary = Files.readString(editing.resolve("SELECCION.txt"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("Fotos del paquete: 2 de 2"));
        assertTrue(summary.contains("Fotos adicionales: 1 (PENDIENTES"));

        assertEquals(SelectionService.EXTRA_PENDING, sel.getExtraStatus());
        assertEquals(List.of("IMG_0002.jpg"), view.extraFiles());
        assertEquals("PENDING", view.extraStatus());

        // 1 foto adicional cobrada en la orden a precio base (sin ITBIS): 300 / 1.18
        ArgumentCaptor<AddServiceOrderItemRequest> item = ArgumentCaptor.forClass(AddServiceOrderItemRequest.class);
        verify(serviceOrderService).addItem(eq(9L), item.capture());
        assertEquals(1, item.getValue().quantity());
        assertEquals(new BigDecimal("254.24"), item.getValue().unitPrice());

        ArgumentCaptor<AdvanceStageRequest> adv = ArgumentCaptor.forClass(AdvanceStageRequest.class);
        verify(serviceOrderService).advanceStage(eq(9L), adv.capture(), eq(SelectionService.CLIENT_ACTOR));
        assertEquals("EDITING", adv.getValue().stage());
        assertNotNull(sel.getSubmittedAt());
        verify(notifier).send(contains("eligió 2 foto(s) del paquete y 1 adicional(es)"));
        assertThrows(IllegalStateException.class, () -> service.saveDraft("tok", List.of()), "ya confirmada");
    }

    private void owes(String balance) {
        com.happydev.prestockbackend.dto.ServiceOrderDto dto = org.mockito.Mockito.mock(com.happydev.prestockbackend.dto.ServiceOrderDto.class);
        given(dto.balance()).willReturn(new BigDecimal(balance));
        given(serviceOrderService.findById(9L)).willReturn(dto);
    }

    private void submittedWithOneExtra() {
        given(selectionRepository.findByServiceOrderId(9L)).willReturn(Optional.of(sel));
        service.submit("tok", List.of("IMG_0001.jpg", "IMG_0003.jpg", "IMG_0002.jpg"), true);
    }

    @Test
    void extrasCannotBeApprovedWhileTheOrderOwes() {
        submittedWithOneExtra();
        owes("254.24");
        var e = assertThrows(IllegalStateException.class, () -> service.approveExtras(9L, false, "secretaria"));
        assertTrue(e.getMessage().contains("RD$254.24"));
        assertEquals(SelectionService.EXTRA_PENDING, sel.getExtraStatus());
        assertFalse(Files.exists(folder.resolve("3 Edicion/Adicionales/IMG_0002.CR3")));
    }

    @Test
    void approvedExtrasGoToTheirOwnEditingFolder() throws Exception {
        submittedWithOneExtra();
        owes("0.00");
        var admin = service.approveExtras(9L, false, "secretaria");
        assertEquals(SelectionService.EXTRA_APPROVED, admin.extraStatus());
        assertTrue(Files.isSameFile(folder.resolve("3 Edicion/Adicionales/IMG_0002.CR3"),
                folder.resolve("1 Camara/CAMARA UNO/IMG_0002.CR3")));
        assertTrue(Files.readString(folder.resolve("3 Edicion/SELECCION.txt"), StandardCharsets.UTF_8).contains("AUTORIZADAS"));
        assertEquals("secretaria", sel.getExtraDecidedBy());
        verify(notifier).send(contains("Adicionales autorizadas"));
        assertThrows(IllegalStateException.class, () -> service.approveExtras(9L, false, "secretaria"), "ya no están pendientes");
    }

    @Test
    void adminCanForceApprovalWithBalanceAndItIsRecorded() {
        submittedWithOneExtra();
        owes("254.24");
        service.approveExtras(9L, true, "admin");
        assertEquals(SelectionService.EXTRA_APPROVED, sel.getExtraStatus());
        assertTrue(sel.getExtraNotes().contains("saldo pendiente de RD$254.24"));
    }

    @Test
    void rejectingExtrasRemovesTheirCharge() {
        com.happydev.prestockbackend.dto.ServiceOrderItemDto added = org.mockito.Mockito.mock(com.happydev.prestockbackend.dto.ServiceOrderItemDto.class);
        given(added.id()).willReturn(77L);
        given(serviceOrderService.addItem(eq(9L), any())).willReturn(added);
        submittedWithOneExtra();
        service.rejectExtras(9L, "No las va a pagar", "secretaria");
        verify(serviceOrderService).removeItem(9L, 77L);
        assertEquals(SelectionService.EXTRA_REJECTED, sel.getExtraStatus());
        assertFalse(Files.exists(folder.resolve("3 Edicion/Adicionales/IMG_0002.CR3")));
    }

    @Test
    void withinQuotaNoExtraIsCharged() {
        service.submit("tok", List.of("IMG_0001.jpg"), false);
        verify(serviceOrderService, never()).addItem(anyLong(), any());
        verify(serviceOrderService).advanceStage(eq(9L), any(), any());
    }

    @Test
    void linkCanOnlyBeCreatedInSelectionWithPreviews() {
        given(selectionRepository.findByServiceOrderId(9L)).willReturn(Optional.empty());
        order.setCurrentStage("SESSION");
        assertThrows(IllegalStateException.class, () -> service.create(9L, 25, 15, "admin"));

        order.setCurrentStage("SELECTION");
        var created = service.create(9L, 25, 15, "admin");
        assertEquals("ana9", created.username(), "primer nombre sin tildes + número de la orden");
        assertEquals("https://iridiscencia.com/seleccion/ana9", created.url());
        assertTrue(created.password().matches("\\d{6}"), "contraseña de 6 dígitos, visible solo al generarla");
        ArgumentCaptor<ServiceOrderSelection> saved = ArgumentCaptor.forClass(ServiceOrderSelection.class);
        verify(selectionRepository).save(saved.capture());
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches(created.password(), saved.getValue().getPasswordHash()), "se guarda solo el hash");
        assertEquals(3, created.previewCount());
    }

    @Test
    void watermarkDefaultsToStudioNameAndCanBeTurnedOff() {
        assertEquals("Iridiscencia", service.watermarkText());
        SelectionService off = new SelectionService(selectionRepository, orderRepository, itemRepository, productRepository,
                companyConfigRepository, serviceOrderService,
                new WorkFolderService(orderRepository, itemRepository, root.toString(), ""), new ObjectMapper(),
                "", "ADI-FOTO-DIG", "off", notifier);
        assertNull(off.watermarkText());
    }

    @Test
    void suggestedQuotaComesFromComboDescriptions() {
        Product combo = new Product();
        combo.setSku("SES-XV-2");
        combo.setName("Combo XV Años 2");
        combo.setDescription("Álbum 6x6 de 10 lienzos · 25 fotos digitales · 1 foto 10x13 enmarcada");
        ServiceOrderItem i = new ServiceOrderItem();
        i.setProduct(combo);
        i.setQuantity(1);
        ServiceOrderItem e = new ServiceOrderItem();
        e.setProduct(extra);
        e.setQuantity(3);
        given(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(9L)).willReturn(List.of(i, e));

        assertEquals(25, service.suggest(9L).suggestedQuota());
    }
}
