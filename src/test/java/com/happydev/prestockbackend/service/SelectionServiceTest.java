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
    void submitLinksOriginalsAddsExtrasAndMovesToEditing() throws Exception {
        service.submit("tok", List.of("IMG_0001.jpg", "IMG_0003.jpg", "IMG_0002.jpg"), true);

        // Originales de cámara enlazados en "3 Edicion" (mismo contenido, sin copiar)
        Path editing = folder.resolve("3 Edicion");
        assertEquals("raw-IMG_0003", Files.readString(editing.resolve("IMG_0003.CR3")));
        assertTrue(Files.isSameFile(editing.resolve("IMG_0001.CR3"), folder.resolve("1 Camara/CAMARA UNO/IMG_0001.CR3")));
        String summary = Files.readString(editing.resolve("SELECCION.txt"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("Fotos elegidas: 3 (incluidas: 2, extra: 1)"));

        // 1 foto extra a precio base (sin ITBIS): 300 / 1.18
        ArgumentCaptor<AddServiceOrderItemRequest> item = ArgumentCaptor.forClass(AddServiceOrderItemRequest.class);
        verify(serviceOrderService).addItem(eq(9L), item.capture());
        assertEquals(1, item.getValue().quantity());
        assertEquals(new BigDecimal("254.24"), item.getValue().unitPrice());

        ArgumentCaptor<AdvanceStageRequest> adv = ArgumentCaptor.forClass(AdvanceStageRequest.class);
        verify(serviceOrderService).advanceStage(eq(9L), adv.capture(), eq(SelectionService.CLIENT_ACTOR));
        assertEquals("EDITING", adv.getValue().stage());
        assertNotNull(sel.getSubmittedAt());
        verify(notifier).send(contains("eligió 3 foto(s), 1 extra"));
        assertThrows(IllegalStateException.class, () -> service.saveDraft("tok", List.of()), "ya confirmada");
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
        assertTrue(created.url().startsWith("https://iridiscencia.com/seleccion/"));
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
