package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.*;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class ServiceOrderServiceImpl implements ServiceOrderService {

    private static final Map<ServiceOrderType, String> INITIAL_STAGE = Map.of(
            ServiceOrderType.PHOTOGRAPHY, "SCHEDULED",
            ServiceOrderType.REPAIR, "RECEIVED",
            ServiceOrderType.GENERAL, "RECEIVED"
    );

    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderStageRepository stageRepository;
    private final ServiceOrderNoteRepository noteRepository;
    private final ServiceOrderItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;
    private final SaleRepository saleRepository;
    private final StockMovementService stockMovementService;
    private final StockMovementRepository stockMovementRepository;
    private final CompanyConfigRepository companyConfigRepository;

    public ServiceOrderServiceImpl(ServiceOrderRepository orderRepository,
                                   ServiceOrderStageRepository stageRepository,
                                   ServiceOrderNoteRepository noteRepository,
                                   ServiceOrderItemRepository itemRepository,
                                   CustomerRepository customerRepository,
                                   ProductRepository productRepository,
                                   AuditService auditService,
                                   SaleRepository saleRepository,
                                   StockMovementService stockMovementService,
                                   StockMovementRepository stockMovementRepository,
                                   CompanyConfigRepository companyConfigRepository) {
        this.orderRepository = orderRepository;
        this.stageRepository = stageRepository;
        this.noteRepository = noteRepository;
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
        this.saleRepository = saleRepository;
        this.stockMovementService = stockMovementService;
        this.stockMovementRepository = stockMovementRepository;
        this.companyConfigRepository = companyConfigRepository;
    }

    @Override
    public List<ServiceOrderDto> findAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toListDto).toList();
    }

    @Override
    public Page<ServiceOrderDto> findPage(
            @NonNull Pageable pageable,
            @Nullable ServiceOrderType type,
            @Nullable ServiceOrderStatus status,
            @Nullable Boolean activeOnly,
            @Nullable String search
    ) {
        Specification<ServiceOrder> spec = ServiceOrderSpecifications.withFilters(type, status, activeOnly, search);
        return orderRepository.findAll(spec, pageable).map(this::toListDto);
    }

    @Override
    public ServiceOrderStatsDto getStats() {
        ServiceOrderType companyType = resolveCompanyOrderType();
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        long active = countActiveByType(companyType);
        return new ServiceOrderStatsDto(
                active,
                orderRepository.countByOrderTypeAndStatus(companyType, ServiceOrderStatus.WAITING_CLIENT),
                orderRepository.countByOrderTypeAndStatus(companyType, ServiceOrderStatus.READY),
                orderRepository.countCompletedSinceByOrderType(companyType, monthStart)
        );
    }

    @Override
    public ServiceOrderReportDto getReport(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        ServiceOrderType companyType = resolveCompanyOrderType();
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        long created = orderRepository.countCreatedInPeriodByOrderType(companyType, start, end);
        long completed = orderRepository.countByOrderTypeAndStatusUpdatedInPeriod(
                companyType, ServiceOrderStatus.COMPLETED, start, end);
        long canceled = orderRepository.countByOrderTypeAndStatusUpdatedInPeriod(
                companyType, ServiceOrderStatus.CANCELLED, start, end);
        long activeNow = countActiveByType(companyType);

        BigDecimal linkedRevenue = saleRepository.sumServiceOrderLinkedRevenueByOrderType(companyType, start, end);

        List<ServiceOrder> completedOrders = orderRepository.findCompletedInPeriodByOrderType(companyType, start, end);
        double avgDays = completedOrders.stream()
                .mapToLong(o -> ChronoUnit.DAYS.between(o.getCreatedAt(), o.getUpdatedAt()))
                .average()
                .orElse(0.0);

        List<ServiceOrderReportDto.ServiceOrderTypeReportRow> byType = List.of();
        if (created > 0 || completed > 0) {
            byType = List.of(new ServiceOrderReportDto.ServiceOrderTypeReportRow(companyType, created, completed));
        }

        return new ServiceOrderReportDto(
                startDate,
                endDate,
                created,
                completed,
                canceled,
                activeNow,
                linkedRevenue,
                avgDays,
                byType
        );
    }

    private long countActiveByType(ServiceOrderType type) {
        return orderRepository.countByOrderTypeAndStatus(type, ServiceOrderStatus.OPEN)
                + orderRepository.countByOrderTypeAndStatus(type, ServiceOrderStatus.IN_PROGRESS)
                + orderRepository.countByOrderTypeAndStatus(type, ServiceOrderStatus.WAITING_CLIENT)
                + orderRepository.countByOrderTypeAndStatus(type, ServiceOrderStatus.READY);
    }

    @Override
    public List<ServiceOrderDto> findByType(@NonNull ServiceOrderType type) {
        return orderRepository.findByOrderTypeOrderByCreatedAtDesc(type).stream()
                .map(this::toListDto).toList();
    }

    @Override
    public List<ServiceOrderDto> findActiveOrders() {
        return orderRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(ServiceOrderStatus.OPEN, ServiceOrderStatus.IN_PROGRESS,
                        ServiceOrderStatus.WAITING_CLIENT, ServiceOrderStatus.READY)
        ).stream().map(this::toListDto).toList();
    }

    @Override
    public List<ServiceOrderDto> findByCustomer(@NonNull Long customerId) {
        ServiceOrderType companyType = resolveCompanyOrderType();
        return orderRepository.findByCustomerIdAndOrderTypeOrderByCreatedAtDesc(customerId, companyType).stream()
                .map(this::toListDto).toList();
    }

    @Override
    public ServiceOrderDto findById(@NonNull Long id) {
        ServiceOrder order = getOrThrow(id);
        List<ServiceOrderStageDto> stages = stageRepository
                .findByServiceOrderIdOrderByEnteredAtAsc(id).stream()
                .map(s -> new ServiceOrderStageDto(s.getId(), s.getStageName(), s.getEnteredAt(), s.getNotes(), s.getActor()))
                .toList();
        List<ServiceOrderNoteDto> notes = noteRepository
                .findByServiceOrderIdOrderByCreatedAtAsc(id).stream()
                .map(n -> new ServiceOrderNoteDto(n.getId(), n.getContent(), n.getAuthor(), n.isInternal(), n.getCreatedAt()))
                .toList();
        List<LinkedSaleDto> linkedSales = saleRepository.findByServiceOrderIdOrderBySaleDateDesc(id).stream()
                .map(s -> new LinkedSaleDto(s.getId(), s.getNcf(), s.getSaleDate(), s.getStatus(), s.getMontoTotal(), s.getPaidAmount()))
                .toList();
        List<ServiceOrderItem> rawItems = itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(id);
        List<ServiceOrderItemDto> items = toItemDtosWithBilling(id, rawItems);
        return toDetailDto(order, stages, notes, linkedSales, items);
    }

    @Override
    public ServiceOrderDto create(@NonNull CreateServiceOrderRequest req, @Nullable String actor) {
        ServiceOrderType orderType = resolveOrderType(req.orderType());

        ServiceOrder order = new ServiceOrder();
        order.setOrderType(orderType);
        order.setTitle(req.title());
        order.setStatus(ServiceOrderStatus.OPEN);
        order.setCurrentStage(INITIAL_STAGE.getOrDefault(orderType, "RECEIVED"));
        order.setAppointmentDate(req.appointmentDate());
        order.setEstimatedDelivery(req.estimatedDelivery());
        order.setEstimatedAmount(req.estimatedAmount());
        order.setDepositAmount(req.depositAmount() != null ? req.depositAmount() : BigDecimal.ZERO);
        order.setDeviceBrand(req.deviceBrand());
        order.setDeviceModel(req.deviceModel());
        order.setDeviceSerial(req.deviceSerial());
        order.setDeviceCondition(req.deviceCondition());
        order.setProblemDescription(req.problemDescription());
        order.setInternalNotes(req.internalNotes());
        order.setCreatedBy(actor);

        if (req.customerId() != null) {
            Customer customer = customerRepository.findById(req.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", req.customerId()));
            order.setCustomer(customer);
        }

        // Temporary order number satisfies NOT NULL before we know the ID
        order.setOrderNumber("OS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ServiceOrder saved = orderRepository.save(order);
        // Overwrite with the stable sequential number now that we have the ID
        saved.setOrderNumber("OS-" + Year.now().getValue() + "-" + String.format("%05d", saved.getId()));
        saved = orderRepository.save(saved);

        // Record initial stage entry
        ServiceOrderStage initialStage = new ServiceOrderStage();
        initialStage.setServiceOrder(saved);
        initialStage.setStageName(saved.getCurrentStage());
        initialStage.setEnteredAt(LocalDateTime.now());
        initialStage.setActor(actor);
        stageRepository.save(initialStage);

        auditService.record(actor, "SERVICE_ORDER_CREATED", "ServiceOrder", saved.getId(),
                "OS " + saved.getOrderNumber() + " creada");

        return toListDto(saved);
    }

    @Override
    public ServiceOrderDto update(@NonNull Long id, @NonNull UpdateServiceOrderRequest req, @Nullable String actor) {
        ServiceOrder order = getOrThrow(id);

        if (req.title() != null) order.setTitle(req.title());
        order.setAppointmentDate(req.appointmentDate());
        order.setEstimatedDelivery(req.estimatedDelivery());
        order.setEstimatedAmount(req.estimatedAmount());
        if (req.depositAmount() != null) order.setDepositAmount(req.depositAmount());
        order.setDeviceBrand(req.deviceBrand());
        order.setDeviceModel(req.deviceModel());
        order.setDeviceSerial(req.deviceSerial());
        order.setDeviceCondition(req.deviceCondition());
        order.setProblemDescription(req.problemDescription());
        order.setInternalNotes(req.internalNotes());

        if (req.customerId() != null) {
            Customer customer = customerRepository.findById(req.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", req.customerId()));
            order.setCustomer(customer);
        }

        auditService.record(actor, "SERVICE_ORDER_UPDATED", "ServiceOrder", id,
                "OS " + order.getOrderNumber() + " actualizada");

        return toListDto(orderRepository.save(order));
    }

    @Override
    public ServiceOrderDto advanceStage(@NonNull Long id, @NonNull AdvanceStageRequest req, @Nullable String actor) {
        ServiceOrder order = getOrThrow(id);

        if (order.getStatus() == ServiceOrderStatus.COMPLETED || order.getStatus() == ServiceOrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede avanzar etapa en una orden " + order.getStatus().name().toLowerCase());
        }

        ServiceOrderStageCatalog.validateAdvance(
                order.getOrderType(),
                order.getCurrentStage(),
                req.stage(),
                req.status()
        );

        order.setCurrentStage(req.stage());
        order.setStatus(req.status());

        ServiceOrderStage stageEntry = new ServiceOrderStage();
        stageEntry.setServiceOrder(order);
        stageEntry.setStageName(req.stage());
        stageEntry.setEnteredAt(LocalDateTime.now());
        stageEntry.setNotes(req.notes());
        stageEntry.setActor(actor);
        stageRepository.save(stageEntry);

        auditService.record(actor, "SERVICE_ORDER_STAGE_ADVANCED", "ServiceOrder", id,
                "OS " + order.getOrderNumber() + " → " + req.stage());

        return toListDto(orderRepository.save(order));
    }

    @Override
    public ServiceOrderDto approveBudget(@NonNull Long id, @Nullable String actor) {
        ServiceOrder order = getOrThrow(id);
        if (order.isBudgetApproved()) {
            throw new IllegalStateException("El presupuesto ya fue aprobado.");
        }
        order.setBudgetApproved(true);
        order.setBudgetApprovedAt(LocalDateTime.now());

        auditService.record(actor, "SERVICE_ORDER_BUDGET_APPROVED", "ServiceOrder", id,
                "Presupuesto aprobado para OS " + order.getOrderNumber());

        return toListDto(orderRepository.save(order));
    }

    @Override
    public ServiceOrderDto cancel(@NonNull Long id, @Nullable String notes, @Nullable String actor) {
        ServiceOrder order = getOrThrow(id);
        if (order.getStatus() == ServiceOrderStatus.COMPLETED || order.getStatus() == ServiceOrderStatus.CANCELLED) {
            throw new IllegalStateException("La orden ya está " + order.getStatus().name().toLowerCase());
        }
        order.setStatus(ServiceOrderStatus.CANCELLED);
        order.setCurrentStage("CANCELLED");

        ServiceOrderStage stageEntry = new ServiceOrderStage();
        stageEntry.setServiceOrder(order);
        stageEntry.setStageName("CANCELLED");
        stageEntry.setEnteredAt(LocalDateTime.now());
        stageEntry.setNotes(notes);
        stageEntry.setActor(actor);
        stageRepository.save(stageEntry);

        auditService.record(actor, "SERVICE_ORDER_CANCELLED", "ServiceOrder", id,
                "OS " + order.getOrderNumber() + " cancelada");

        return toListDto(orderRepository.save(order));
    }

    @Override
    public ServiceOrderDto complete(@NonNull Long id, @Nullable String actor) {
        ServiceOrder order = getOrThrow(id);
        if (order.getStatus() == ServiceOrderStatus.COMPLETED) {
            throw new IllegalStateException("La orden ya está completada.");
        }
        if (order.getStatus() == ServiceOrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede completar una orden cancelada.");
        }
        order.setStatus(ServiceOrderStatus.COMPLETED);

        String finalStage = "DELIVERED";
        order.setCurrentStage(finalStage);

        ServiceOrderStage stageEntry = new ServiceOrderStage();
        stageEntry.setServiceOrder(order);
        stageEntry.setStageName(finalStage);
        stageEntry.setEnteredAt(LocalDateTime.now());
        stageEntry.setActor(actor);
        stageRepository.save(stageEntry);

        deductStockOnComplete(order);

        auditService.record(actor, "SERVICE_ORDER_COMPLETED", "ServiceOrder", id,
                "OS " + order.getOrderNumber() + " completada");

        return toListDto(orderRepository.save(order));
    }

    @Override
    public ServiceOrderNoteDto addNote(@NonNull Long id, @NonNull AddNoteRequest req, @Nullable String actor) {
        ServiceOrder order = getOrThrow(id);

        ServiceOrderNote note = new ServiceOrderNote();
        note.setServiceOrder(order);
        note.setContent(req.content());
        note.setAuthor(actor);
        note.setInternal(req.internal());

        ServiceOrderNote saved = noteRepository.save(note);
        return new ServiceOrderNoteDto(saved.getId(), saved.getContent(), saved.getAuthor(),
                saved.isInternal(), saved.getCreatedAt());
    }

    @Override
    public void deleteNote(@NonNull Long orderId, @NonNull Long noteId) {
        ServiceOrderNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrderNote", "id", noteId));
        if (!note.getServiceOrder().getId().equals(orderId)) {
            throw new ResourceNotFoundException("ServiceOrderNote", "id", noteId);
        }
        noteRepository.delete(note);
    }

    @Override
    public ServiceOrderItemDto addItem(@NonNull Long orderId, @NonNull AddServiceOrderItemRequest req) {
        ServiceOrder order = getOrThrow(orderId);
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", req.productId()));

        BigDecimal unitPrice = req.unitPrice() != null ? req.unitPrice() : product.getSellingPrice();
        int position = itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(orderId).size();

        ServiceOrderItem item = new ServiceOrderItem();
        item.setServiceOrder(order);
        item.setProduct(product);
        item.setQuantity(req.quantity() != null ? req.quantity() : 1);
        item.setUnitPrice(unitPrice);
        item.setNotes(req.notes());
        item.setPosition(position);

        return toItemDtosWithBilling(orderId, List.of(itemRepository.save(item))).get(0);
    }

    @Override
    public ServiceOrderItemDto updateItem(
            @NonNull Long orderId,
            @NonNull Long itemId,
            @NonNull UpdateServiceOrderItemRequest req
    ) {
        ServiceOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrderItem", "id", itemId));
        if (!item.getServiceOrder().getId().equals(orderId)) {
            throw new ResourceNotFoundException("ServiceOrderItem", "id", itemId);
        }

        int invoiced = invoicedQuantityForItem(orderId, item);

        if (req.quantity() != null) {
            if (req.quantity() < invoiced) {
                throw new IllegalArgumentException(
                        "La cantidad no puede ser menor que lo ya facturado (" + invoiced + ").");
            }
            item.setQuantity(req.quantity());
        }
        if (req.unitPrice() != null) {
            if (invoiced > 0) {
                throw new IllegalStateException("No se puede cambiar el precio de un producto ya facturado.");
            }
            item.setUnitPrice(req.unitPrice());
        }
        if (req.notes() != null) {
            item.setNotes(req.notes().isBlank() ? null : req.notes().trim());
        }

        return toItemDtosWithBilling(orderId, List.of(itemRepository.save(item))).get(0);
    }

    @Override
    public void removeItem(@NonNull Long orderId, @NonNull Long itemId) {
        ServiceOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrderItem", "id", itemId));
        if (!item.getServiceOrder().getId().equals(orderId)) {
            throw new ResourceNotFoundException("ServiceOrderItem", "id", itemId);
        }
        int invoiced = invoicedQuantityForItem(orderId, item);
        if (invoiced > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar un producto que ya fue facturado (" + invoiced + " unidad"
                            + (invoiced == 1 ? "" : "es") + ").");
        }
        itemRepository.delete(item);
    }

    // ── helpers ───────────────────────────────────────────

    private ServiceOrder getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", "id", id));
    }

    private void deductStockOnComplete(ServiceOrder order) {
        boolean enabled = companyConfigRepository.findFirstByOrderByIdAsc()
                .map(CompanyConfig::isServiceOrderDeductStock)
                .orElse(false);
        if (!enabled) {
            return;
        }

        Long orderId = Objects.requireNonNull(order.getId());
        List<ServiceOrderItem> items = itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(orderId);
        if (items.isEmpty()) {
            return;
        }

        Map<Long, Integer> soldPool = soldQtyByProductForOrder(orderId);
        Map<Long, Integer> pendingByProduct = new HashMap<>();
        Map<Long, Product> productById = new HashMap<>();

        for (ServiceOrderItem item : items) {
            Product product = item.getProduct();
            Long productId = Objects.requireNonNull(product.getId());
            if (product.getTipoBienServicio() == TipoBienServicio.SERVICIO) {
                continue;
            }
            productById.putIfAbsent(productId, product);
            int invoiced = allocateFromPool(soldPool, productId, item.getQuantity());
            int pending = item.getQuantity() - invoiced;
            if (pending > 0) {
                pendingByProduct.merge(productId, pending, Integer::sum);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Long, Integer> entry : pendingByProduct.entrySet()) {
            Long productId = entry.getKey();
            int toDeduct = entry.getValue();
            if (stockMovementRepository.existsByServiceOrder_IdAndProduct_Id(orderId, productId)) {
                continue;
            }
            Product product = productById.get(productId);
            if (product.getStock() < toDeduct) {
                throw new IllegalStateException("Existencias insuficientes para: " + product.getName());
            }

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementDate(now);
            movement.setQuantityChange(-toDeduct);
            movement.setType(StockMovementType.OUT);
            movement.setReason("Orden de servicio completada: " + order.getOrderNumber());
            movement.setServiceOrder(order);
            stockMovementService.createMovement(movement);
        }
    }

    private Map<Long, Integer> soldQtyByProductForOrder(Long orderId) {
        Map<Long, Integer> soldQtyByProduct = new HashMap<>();
        for (Sale sale : saleRepository.findByServiceOrderIdOrderBySaleDateDesc(orderId)) {
            if (sale.getStatus() != SaleStatus.COMPLETED && sale.getStatus() != SaleStatus.PARTIALLY_PAID) {
                continue;
            }
            for (SaleItem saleItem : sale.getItems()) {
                Long productId = saleItem.getProduct().getId();
                soldQtyByProduct.merge(productId, saleItem.getQuantity(), Integer::sum);
            }
        }
        return soldQtyByProduct;
    }

    private List<ServiceOrderItemDto> toItemDtosWithBilling(Long orderId, List<ServiceOrderItem> items) {
        Map<Long, Integer> soldPool = soldQtyByProductForOrder(orderId);
        return items.stream()
                .map(item -> {
                    Long productId = Objects.requireNonNull(item.getProduct().getId());
                    int invoiced = allocateFromPool(soldPool, productId, item.getQuantity());
                    return toItemDto(item, invoiced);
                })
                .toList();
    }

    private int invoicedQuantityForItem(Long orderId, ServiceOrderItem target) {
        List<ServiceOrderItem> items = itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(orderId);
        Map<Long, Integer> soldPool = soldQtyByProductForOrder(orderId);
        for (ServiceOrderItem item : items) {
            Long productId = Objects.requireNonNull(item.getProduct().getId());
            int invoiced = allocateFromPool(soldPool, productId, item.getQuantity());
            if (item.getId().equals(target.getId())) {
                return invoiced;
            }
        }
        return 0;
    }

    private static int allocateFromPool(Map<Long, Integer> pool, Long productId, int quantity) {
        int available = pool.getOrDefault(productId, 0);
        int allocated = Math.min(quantity, available);
        pool.put(productId, Math.max(0, available - allocated));
        return allocated;
    }

    private static Map<ServiceOrderType, Long> toTypeCountMap(List<Object[]> rows) {
        Map<ServiceOrderType, Long> map = new EnumMap<>(ServiceOrderType.class);
        for (Object[] row : rows) {
            map.put((ServiceOrderType) row[0], (Long) row[1]);
        }
        return map;
    }

    private String customerName(ServiceOrder o) {
        if (o.getCustomer() == null) return null;
        Customer c = o.getCustomer();
        return (c.getFirstName() + " " + (c.getLastName() != null ? c.getLastName() : "")).trim();
    }

    private ServiceOrderDto toListDto(ServiceOrder o) {
        return new ServiceOrderDto(
                o.getId(), o.getOrderNumber(),
                o.getCustomer() != null ? o.getCustomer().getId() : null,
                customerName(o),
                o.getOrderType(), o.getTitle(), o.getStatus(), o.getCurrentStage(),
                o.getAppointmentDate(), o.getEstimatedDelivery(),
                o.getEstimatedAmount(), o.isBudgetApproved(), o.getBudgetApprovedAt(),
                o.getDepositAmount(),
                o.getDeviceBrand(), o.getDeviceModel(), o.getDeviceSerial(),
                o.getDeviceCondition(), o.getProblemDescription(),
                o.getInternalNotes(), o.getCreatedBy(), o.getCreatedAt(), o.getUpdatedAt(),
                null, null, null, null
        );
    }

    private ServiceOrderDto toDetailDto(ServiceOrder o, List<ServiceOrderStageDto> stages,
                                        List<ServiceOrderNoteDto> notes, List<LinkedSaleDto> linkedSales,
                                        List<ServiceOrderItemDto> items) {
        return new ServiceOrderDto(
                o.getId(), o.getOrderNumber(),
                o.getCustomer() != null ? o.getCustomer().getId() : null,
                customerName(o),
                o.getOrderType(), o.getTitle(), o.getStatus(), o.getCurrentStage(),
                o.getAppointmentDate(), o.getEstimatedDelivery(),
                o.getEstimatedAmount(), o.isBudgetApproved(), o.getBudgetApprovedAt(),
                o.getDepositAmount(),
                o.getDeviceBrand(), o.getDeviceModel(), o.getDeviceSerial(),
                o.getDeviceCondition(), o.getProblemDescription(),
                o.getInternalNotes(), o.getCreatedBy(), o.getCreatedAt(), o.getUpdatedAt(),
                stages, notes, linkedSales, items
        );
    }

    private ServiceOrderItemDto toItemDto(ServiceOrderItem i, int invoicedQuantity) {
        int pendingQuantity = Math.max(0, i.getQuantity() - invoicedQuantity);
        BigDecimal subtotal = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
        BigDecimal pendingSubtotal = i.getUnitPrice().multiply(BigDecimal.valueOf(pendingQuantity));
        return new ServiceOrderItemDto(
                i.getId(),
                i.getProduct().getId(),
                i.getProduct().getName(),
                i.getProduct().getSku(),
                i.getQuantity(),
                invoicedQuantity,
                pendingQuantity,
                i.getUnitPrice(),
                subtotal,
                pendingSubtotal,
                i.getNotes()
        );
    }

    private ServiceOrderType resolveOrderType(@Nullable ServiceOrderType requested) {
        ServiceOrderType companyType = resolveCompanyOrderType();
        if (requested == null) {
            return companyType;
        }
        if (requested != companyType) {
            throw new IllegalArgumentException(
                    "El tipo de orden no coincide con el modelo configurado para la empresa (" + companyType + ").");
        }
        return requested;
    }

    private ServiceOrderType resolveCompanyOrderType() {
        // Si no existe company_config (instalación nueva, antes del wizard de configuración),
        // usar PHOTOGRAPHY: coincide con el default del campo en CompanyConfig y con el
        // fallback del frontend (companyConfig?.serviceOrderType ?? "PHOTOGRAPHY"). Si no
        // coinciden, las órdenes creadas no aparecen en el Kanban (filtrado por tipo).
        return companyConfigRepository.findFirstByOrderByIdAsc()
                .map(CompanyConfig::getServiceOrderType)
                .map(this::parseServiceOrderType)
                .orElse(ServiceOrderType.PHOTOGRAPHY);
    }

    private ServiceOrderType parseServiceOrderType(String raw) {
        try {
            return ServiceOrderType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ServiceOrderType.GENERAL;
        }
    }
}
