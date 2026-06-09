package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.*;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
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

    public ServiceOrderServiceImpl(ServiceOrderRepository orderRepository,
                                   ServiceOrderStageRepository stageRepository,
                                   ServiceOrderNoteRepository noteRepository,
                                   ServiceOrderItemRepository itemRepository,
                                   CustomerRepository customerRepository,
                                   ProductRepository productRepository,
                                   AuditService auditService,
                                   SaleRepository saleRepository) {
        this.orderRepository = orderRepository;
        this.stageRepository = stageRepository;
        this.noteRepository = noteRepository;
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
        this.saleRepository = saleRepository;
    }

    @Override
    public List<ServiceOrderDto> findAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toListDto).toList();
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
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
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
        List<ServiceOrderItemDto> items = itemRepository
                .findByServiceOrderIdOrderByPositionAscCreatedAtAsc(id).stream()
                .map(this::toItemDto)
                .toList();
        return toDetailDto(order, stages, notes, linkedSales, items);
    }

    @Override
    public ServiceOrderDto create(@NonNull CreateServiceOrderRequest req, @Nullable String actor) {
        ServiceOrder order = new ServiceOrder();
        order.setOrderType(req.orderType());
        order.setTitle(req.title());
        order.setStatus(ServiceOrderStatus.OPEN);
        order.setCurrentStage(INITIAL_STAGE.getOrDefault(req.orderType(), "RECEIVED"));
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

        String finalStage = order.getOrderType() == ServiceOrderType.PHOTOGRAPHY ? "DELIVERED"
                : order.getOrderType() == ServiceOrderType.REPAIR ? "DELIVERED" : "DELIVERED";
        order.setCurrentStage(finalStage);

        ServiceOrderStage stageEntry = new ServiceOrderStage();
        stageEntry.setServiceOrder(order);
        stageEntry.setStageName(finalStage);
        stageEntry.setEnteredAt(LocalDateTime.now());
        stageEntry.setActor(actor);
        stageRepository.save(stageEntry);

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

        return toItemDto(itemRepository.save(item));
    }

    @Override
    public void removeItem(@NonNull Long orderId, @NonNull Long itemId) {
        ServiceOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrderItem", "id", itemId));
        if (!item.getServiceOrder().getId().equals(orderId)) {
            throw new ResourceNotFoundException("ServiceOrderItem", "id", itemId);
        }
        itemRepository.delete(item);
    }

    // ── helpers ───────────────────────────────────────────

    private ServiceOrder getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", "id", id));
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

    private ServiceOrderItemDto toItemDto(ServiceOrderItem i) {
        BigDecimal subtotal = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
        return new ServiceOrderItemDto(
                i.getId(),
                i.getProduct().getId(),
                i.getProduct().getName(),
                i.getProduct().getSku(),
                i.getQuantity(),
                i.getUnitPrice(),
                subtotal,
                i.getNotes()
        );
    }
}
