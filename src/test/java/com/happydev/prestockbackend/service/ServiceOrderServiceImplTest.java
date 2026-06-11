package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AdvanceStageRequest;
import com.happydev.prestockbackend.dto.CreateServiceOrderRequest;
import com.happydev.prestockbackend.dto.ServiceOrderReportDto;
import com.happydev.prestockbackend.dto.ServiceOrderStatsDto;
import com.happydev.prestockbackend.dto.UpdateServiceOrderItemRequest;
import com.happydev.prestockbackend.dto.ServiceOrderDto;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleItem;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderItem;
import com.happydev.prestockbackend.entity.ServiceOrderStage;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceOrderServiceImplTest {

    @Mock private ServiceOrderRepository orderRepository;
    @Mock private ServiceOrderStageRepository stageRepository;
    @Mock private ServiceOrderNoteRepository noteRepository;
    @Mock private ServiceOrderItemRepository itemRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AuditService auditService;
    @Mock private SaleRepository saleRepository;
    @Mock private StockMovementService stockMovementService;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private CompanyConfigRepository companyConfigRepository;

    @InjectMocks
    private ServiceOrderServiceImpl service;

    private ServiceOrder repairOrder;

    @BeforeEach
    void setUp() {
        repairOrder = new ServiceOrder();
        repairOrder.setId(1L);
        repairOrder.setOrderNumber("OS-2026-00001");
        repairOrder.setOrderType(ServiceOrderType.REPAIR);
        repairOrder.setTitle("Reparación pantalla");
        repairOrder.setStatus(ServiceOrderStatus.OPEN);
        repairOrder.setCurrentStage("RECEIVED");
        repairOrder.setCreatedAt(LocalDateTime.now());
        repairOrder.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void advanceStage_validNextStage_updatesOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(repairOrder));
        when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.advanceStage(
                1L,
                new AdvanceStageRequest("DIAGNOSIS", ServiceOrderStatus.IN_PROGRESS, null),
                "tester"
        );

        assertEquals("DIAGNOSIS", result.currentStage());
        assertEquals(ServiceOrderStatus.IN_PROGRESS, result.status());
        verify(stageRepository).save(any());
    }

    @Test
    void advanceStage_skipsStage_throwsIllegalArgument() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(repairOrder));

        assertThrows(IllegalArgumentException.class, () ->
                service.advanceStage(
                        1L,
                        new AdvanceStageRequest("IN_REPAIR", ServiceOrderStatus.IN_PROGRESS, null),
                        "tester"
                ));
    }

    @Test
    void advanceStage_wrongStatusForStage_throwsIllegalArgument() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(repairOrder));

        assertThrows(IllegalArgumentException.class, () ->
                service.advanceStage(
                        1L,
                        new AdvanceStageRequest("DIAGNOSIS", ServiceOrderStatus.READY, null),
                        "tester"
                ));
    }

    @Test
    void create_persistsInitialStage() {
        CompanyConfig config = new CompanyConfig();
        config.setServiceOrderType("REPAIR");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(inv -> {
            ServiceOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(42L);
            }
            return o;
        });

        var request = new CreateServiceOrderRequest(
                null,
                ServiceOrderType.REPAIR,
                "Reparación laptop",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        var created = service.create(request, "admin");

        assertEquals("OS-2026-00042", created.orderNumber());
        assertEquals("RECEIVED", created.currentStage());
        ArgumentCaptor<ServiceOrderStage> stageCaptor = ArgumentCaptor.forClass(ServiceOrderStage.class);
        verify(stageRepository).save(stageCaptor.capture());
        assertEquals("RECEIVED", stageCaptor.getValue().getStageName());
    }

    @Test
    void getStats_aggregatesCountsForCompanyType() {
        CompanyConfig config = new CompanyConfig();
        config.setServiceOrderType("REPAIR");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.OPEN)).thenReturn(2L);
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.IN_PROGRESS)).thenReturn(3L);
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.WAITING_CLIENT)).thenReturn(1L);
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.READY)).thenReturn(4L);
        when(orderRepository.countCompletedSinceByOrderType(
                eq(ServiceOrderType.REPAIR), eq(YearMonth.now().atDay(1).atStartOfDay()))).thenReturn(5L);

        ServiceOrderStatsDto stats = service.getStats();

        assertEquals(10L, stats.active());
        assertEquals(1L, stats.waitingClient());
        assertEquals(4L, stats.ready());
        assertEquals(5L, stats.completedThisMonth());
    }

    @Test
    void complete_deductsStockWhenEnabled() {
        CompanyConfig config = new CompanyConfig();
        config.setServiceOrderDeductStock(true);
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        Product product = new Product();
        product.setId(10L);
        product.setName("Repuesto");
        product.setStock(5);
        product.setTipoBienServicio(TipoBienServicio.BIEN);

        ServiceOrderItem item = new ServiceOrderItem();
        item.setProduct(product);
        item.setQuantity(2);

        repairOrder.setStatus(ServiceOrderStatus.READY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(repairOrder));
        when(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(1L)).thenReturn(List.of(item));
        when(saleRepository.findByServiceOrderIdOrderBySaleDateDesc(1L)).thenReturn(Collections.emptyList());
        when(stockMovementRepository.existsByServiceOrder_IdAndProduct_Id(1L, 10L)).thenReturn(false);
        when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.complete(1L, "tester");

        verify(stockMovementService).createMovement(any(StockMovement.class));
    }

    @Test
    void create_usesCompanyConfigWhenOrderTypeOmitted() {
        CompanyConfig config = new CompanyConfig();
        config.setServiceOrderType("PHOTOGRAPHY");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(inv -> {
            ServiceOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(42L);
            }
            return o;
        });

        var request = new CreateServiceOrderRequest(
                null,
                null,
                "Sesión de prueba",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        var created = service.create(request, "admin");

        assertEquals(ServiceOrderType.PHOTOGRAPHY, created.orderType());
        assertEquals("SCHEDULED", created.currentStage());
    }

    @Test
    void create_rejectsMismatchedOrderType() {
        CompanyConfig config = new CompanyConfig();
        config.setServiceOrderType("REPAIR");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        var request = new CreateServiceOrderRequest(
                null,
                ServiceOrderType.PHOTOGRAPHY,
                "Sesión de prueba",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(request, "admin"));
    }

    @Test
    void complete_skipsStockDeductionWhenDisabled() {
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        repairOrder.setStatus(ServiceOrderStatus.READY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(repairOrder));
        when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.complete(1L, "tester");

        verify(stockMovementService, never()).createMovement(any());
    }

    @Test
    void getReport_aggregatesPeriodMetricsForCompanyType() {
        CompanyConfig config = new CompanyConfig();
        config.setServiceOrderType("REPAIR");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();

        when(orderRepository.countCreatedInPeriodByOrderType(ServiceOrderType.REPAIR, startDt, endDt)).thenReturn(12L);
        when(orderRepository.countByOrderTypeAndStatusUpdatedInPeriod(
                ServiceOrderType.REPAIR, ServiceOrderStatus.COMPLETED, startDt, endDt)).thenReturn(8L);
        when(orderRepository.countByOrderTypeAndStatusUpdatedInPeriod(
                ServiceOrderType.REPAIR, ServiceOrderStatus.CANCELLED, startDt, endDt)).thenReturn(1L);
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.OPEN)).thenReturn(2L);
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.IN_PROGRESS)).thenReturn(3L);
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.WAITING_CLIENT)).thenReturn(1L);
        when(orderRepository.countByOrderTypeAndStatus(ServiceOrderType.REPAIR, ServiceOrderStatus.READY)).thenReturn(2L);
        when(saleRepository.sumServiceOrderLinkedRevenueByOrderType(ServiceOrderType.REPAIR, startDt, endDt))
                .thenReturn(new BigDecimal("15000.00"));
        when(orderRepository.findCompletedInPeriodByOrderType(ServiceOrderType.REPAIR, startDt, endDt))
                .thenReturn(Collections.emptyList());

        ServiceOrderReportDto report = service.getReport(start, end);

        assertEquals(12L, report.created());
        assertEquals(8L, report.completed());
        assertEquals(1L, report.canceled());
        assertEquals(8L, report.activeNow());
        assertEquals(new BigDecimal("15000.00"), report.linkedSalesRevenue());
        assertEquals(1, report.byType().size());
        assertEquals(ServiceOrderType.REPAIR, report.byType().get(0).orderType());
    }

    @Test
    void findById_allocatesInvoicedQuantitiesAcrossItems() {
        Product product = new Product();
        product.setId(10L);
        product.setName("Repuesto");
        product.setSku("REP-01");

        ServiceOrderItem item1 = new ServiceOrderItem();
        item1.setId(100L);
        item1.setProduct(product);
        item1.setQuantity(3);
        item1.setUnitPrice(new BigDecimal("100.00"));
        item1.setPosition(0);

        ServiceOrderItem item2 = new ServiceOrderItem();
        item2.setId(101L);
        item2.setProduct(product);
        item2.setQuantity(2);
        item2.setUnitPrice(new BigDecimal("100.00"));
        item2.setPosition(1);

        Sale sale = new Sale();
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setServiceOrder(repairOrder);
        SaleItem saleItem = new SaleItem();
        saleItem.setProduct(product);
        saleItem.setQuantity(4);
        sale.setItems(new ArrayList<>(List.of(saleItem)));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(repairOrder));
        when(stageRepository.findByServiceOrderIdOrderByEnteredAtAsc(1L)).thenReturn(Collections.emptyList());
        when(noteRepository.findByServiceOrderIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(saleRepository.findByServiceOrderIdOrderBySaleDateDesc(1L)).thenReturn(List.of(sale));
        when(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(1L)).thenReturn(List.of(item1, item2));

        ServiceOrderDto detail = service.findById(1L);

        assertEquals(2, detail.items().size());
        assertEquals(3, detail.items().get(0).invoicedQuantity());
        assertEquals(0, detail.items().get(0).pendingQuantity());
        assertEquals(1, detail.items().get(1).invoicedQuantity());
        assertEquals(1, detail.items().get(1).pendingQuantity());
    }

    @Test
    void removeItem_invoicedLine_throwsIllegalState() {
        Product product = new Product();
        product.setId(10L);
        product.setName("Repuesto");

        ServiceOrderItem item = new ServiceOrderItem();
        item.setId(100L);
        item.setServiceOrder(repairOrder);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setPosition(0);

        Sale sale = new Sale();
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setServiceOrder(repairOrder);
        SaleItem saleItem = new SaleItem();
        saleItem.setProduct(product);
        saleItem.setQuantity(1);
        sale.setItems(new ArrayList<>(List.of(saleItem)));

        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(1L)).thenReturn(List.of(item));
        when(saleRepository.findByServiceOrderIdOrderBySaleDateDesc(1L)).thenReturn(List.of(sale));

        assertThrows(IllegalStateException.class, () -> service.removeItem(1L, 100L));
    }

    @Test
    void updateItem_reducesPendingQuantity_keepsInvoicedMinimum() {
        Product product = new Product();
        product.setId(10L);
        product.setName("Repuesto");

        ServiceOrderItem item = new ServiceOrderItem();
        item.setId(100L);
        item.setServiceOrder(repairOrder);
        item.setProduct(product);
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setPosition(0);

        Sale sale = new Sale();
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setServiceOrder(repairOrder);
        SaleItem saleItem = new SaleItem();
        saleItem.setProduct(product);
        saleItem.setQuantity(2);
        sale.setItems(new ArrayList<>(List.of(saleItem)));

        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(1L)).thenReturn(List.of(item));
        when(saleRepository.findByServiceOrderIdOrderBySaleDateDesc(1L)).thenReturn(List.of(sale));
        when(itemRepository.save(any(ServiceOrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.updateItem(1L, 100L, new UpdateServiceOrderItemRequest(3, null, null));

        assertEquals(3, updated.quantity());
        assertEquals(2, updated.invoicedQuantity());
        assertEquals(1, updated.pendingQuantity());
    }

    @Test
    void updateItem_belowInvoicedQuantity_throwsIllegalArgument() {
        Product product = new Product();
        product.setId(10L);
        product.setName("Repuesto");

        ServiceOrderItem item = new ServiceOrderItem();
        item.setId(100L);
        item.setServiceOrder(repairOrder);
        item.setProduct(product);
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setPosition(0);

        Sale sale = new Sale();
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setServiceOrder(repairOrder);
        SaleItem saleItem = new SaleItem();
        saleItem.setProduct(product);
        saleItem.setQuantity(3);
        sale.setItems(new ArrayList<>(List.of(saleItem)));

        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(1L)).thenReturn(List.of(item));
        when(saleRepository.findByServiceOrderIdOrderBySaleDateDesc(1L)).thenReturn(List.of(sale));

        assertThrows(IllegalArgumentException.class, () ->
                service.updateItem(1L, 100L, new UpdateServiceOrderItemRequest(2, null, null)));
    }
}
