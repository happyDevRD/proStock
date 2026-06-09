package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.dto.SupplierPaymentDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.PurchaseOrderMapper;
import com.happydev.prestockbackend.repository.*;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final StockMovementService stockMovementService;
    private final AuditService auditService;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final AccAccountRepository accAccountRepository;
    private final AccJournalEntryRepository accJournalEntryRepository;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    SupplierRepository supplierRepository,
                                    ProductRepository productRepository,
                                    PurchaseOrderMapper purchaseOrderMapper,
                                    StockMovementService stockMovementService,
                                    AuditService auditService,
                                    SupplierPaymentRepository supplierPaymentRepository,
                                    AccAccountRepository accAccountRepository,
                                    AccJournalEntryRepository accJournalEntryRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.stockMovementService = stockMovementService;
        this.auditService = auditService;
        this.supplierPaymentRepository = supplierPaymentRepository;
        this.accAccountRepository = accAccountRepository;
        this.accJournalEntryRepository = accJournalEntryRepository;
    }

    @Override
    public List<PurchaseOrderDto> findAllPurchaseOrders() {
        return purchaseOrderMapper.toDtoList(purchaseOrderRepository.findAll());
    }

    @Override
    public Page<PurchaseOrderDto> findAllPurchaseOrders(@NonNull Pageable pageable) {
        return purchaseOrderRepository.findAll(pageable).map(purchaseOrderMapper::toDto);
    }

    @Override
    public Optional<PurchaseOrderDto> findPurchaseOrderById(@NonNull Long id) {
        return purchaseOrderRepository.findById(id).map(purchaseOrderMapper::toDto);
    }

    @Override
    public PurchaseOrderDto createPurchaseOrder(@NonNull PurchaseOrderDto purchaseOrderDto) {
        Long supplierId = Objects.requireNonNull(purchaseOrderDto.getSupplierId(), "Supplier id is required");
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Supplier", "id", supplierId);
        }

        PurchaseOrder purchaseOrder = purchaseOrderMapper.toEntity(purchaseOrderDto);
        purchaseOrder.setStatus(PurchaseOrderStatus.PENDING);
        purchaseOrder.setPaidAmount(BigDecimal.ZERO);

        if (purchaseOrder.getItems() != null) {
            for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                item.setPurchaseOrder(purchaseOrder);
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                if (!productRepository.existsById(productId)) {
                    throw new ResourceNotFoundException("Product", "id", productId);
                }
            }
        }

        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);
        int itemCount = saved.getItems() != null ? saved.getItems().size() : 0;
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PURCHASE_ORDER_CREATED",
                "PurchaseOrder",
                saved.getId(),
                Map.of("supplierId", supplierId.toString(), "items", Integer.toString(itemCount))
        );
        return purchaseOrderMapper.toDto(saved);
    }

    @Override
    public PurchaseOrderDto updatePurchaseOrder(@NonNull Long id, @NonNull PurchaseOrderDto purchaseOrderDto) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        if (purchaseOrderDto.getSupplierId() != null) {
            Long supplierId = Objects.requireNonNull(purchaseOrderDto.getSupplierId());
            if (!supplierRepository.existsById(supplierId)) {
                throw new ResourceNotFoundException("Supplier", "id", supplierId);
            }
            purchaseOrder.setSupplier(supplierRepository.findById(supplierId)
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId)));
        }

        purchaseOrder.setOrderDate(purchaseOrderDto.getOrderDate());

        if (purchaseOrderDto.getStatus() != null) {
            purchaseOrder.setStatus(purchaseOrderDto.getStatus());
        }

        purchaseOrder.getItems().clear();

        if (purchaseOrderDto.getItems() != null) {
            List<PurchaseOrderItem> newItems = purchaseOrderMapper.toItemEntityList(purchaseOrderDto.getItems());
            for (PurchaseOrderItem item : newItems) {
                item.setPurchaseOrder(purchaseOrder);
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                if (!productRepository.existsById(productId)) {
                    throw new ResourceNotFoundException("Product", "id", productId);
                }
                purchaseOrder.getItems().add(item);
            }
        }

        PurchaseOrder updated = purchaseOrderRepository.save(purchaseOrder);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PURCHASE_ORDER_UPDATED",
                "PurchaseOrder",
                id,
                Map.of("status", updated.getStatus().name())
        );
        return purchaseOrderMapper.toDto(updated);
    }

    @Override
    public void deletePurchaseOrder(@NonNull Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));
        String status = purchaseOrder.getStatus().name();
        purchaseOrderRepository.delete(purchaseOrder);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PURCHASE_ORDER_DELETED",
                "PurchaseOrder",
                id,
                Map.of("previousStatus", status)
        );
    }

    @Override
    public PurchaseOrderDto receivePurchaseOrder(@NonNull Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        if (purchaseOrder.getStatus() != PurchaseOrderStatus.PENDING) {
            throw new IllegalStateException("Cannot receive a purchase order that is not in PENDING status.");
        }

        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            Product product = item.getProduct();
            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementDate(LocalDateTime.now());
            movement.setQuantityChange(item.getQuantity());
            movement.setType(StockMovementType.IN);
            movement.setReason("Purchase order received");
            movement.setPurchaseOrder(purchaseOrder);
            stockMovementService.createMovement(movement);
        }

        purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVED);
        purchaseOrder.setReceptionDate(LocalDateTime.now());
        PurchaseOrder updated = purchaseOrderRepository.save(purchaseOrder);

        int n = purchaseOrder.getItems() != null ? purchaseOrder.getItems().size() : 0;
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PURCHASE_ORDER_RECEIVED",
                "PurchaseOrder",
                updated.getId(),
                Map.of("items", Integer.toString(n))
        );
        return purchaseOrderMapper.toDto(updated);
    }

    // -------------------------------------------------------
    // Payment methods
    // -------------------------------------------------------

    @Override
    public SupplierPaymentDto addPayment(Long poId, BigDecimal amount, PaymentMethod paymentMethod,
                                         String notes, String actorUsername) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", poId));

        if (po.getStatus() != PurchaseOrderStatus.RECEIVED && po.getStatus() != PurchaseOrderStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Solo se pueden registrar pagos en órdenes en estado RECIBIDA o PARCIALMENTE PAGADA.");
        }

        BigDecimal poTotal = BigDecimal.valueOf(po.getTotal());
        BigDecimal pending = poTotal.subtract(po.getPaidAmount() == null ? BigDecimal.ZERO : po.getPaidAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor que cero.");
        }
        if (amount.compareTo(pending) > 0) {
            throw new IllegalArgumentException("El monto del pago (" + amount + ") excede el saldo pendiente (" + pending + ").");
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setPurchaseOrder(po);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setNotes(notes);
        payment.setCreatedBy(actorUsername);
        payment.setCreatedAt(LocalDateTime.now());
        SupplierPayment saved = supplierPaymentRepository.save(payment);

        BigDecimal newPaid = supplierPaymentRepository.sumAmountByPurchaseOrderId(poId);
        po.setPaidAmount(newPaid);
        po.setStatus(newPaid.compareTo(poTotal) >= 0
                ? PurchaseOrderStatus.PAID
                : PurchaseOrderStatus.PARTIALLY_PAID);
        purchaseOrderRepository.save(po);

        recordSupplierPaymentJournalEntry(po, saved, actorUsername);

        auditService.record(actorUsername, "PURCHASE_ORDER_PAYMENT_ADDED", "SupplierPayment",
                saved.getId(), Map.of("poId", poId.toString(), "amount", amount.toString()));

        return toPaymentDto(saved);
    }

    @Override
    public List<SupplierPaymentDto> getPaymentsForPurchaseOrder(Long poId) {
        if (!purchaseOrderRepository.existsById(poId)) {
            throw new ResourceNotFoundException("PurchaseOrder", "id", poId);
        }
        return supplierPaymentRepository.findByPurchaseOrderIdOrderByPaymentDateAsc(poId)
                .stream().map(this::toPaymentDto).toList();
    }

    @Override
    public PurchaseOrderDto voidPayment(Long poId, Long paymentId, String actorUsername) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", poId));

        if (po.getStatus() == PurchaseOrderStatus.PAID) {
            throw new IllegalStateException("No se pueden anular pagos de una orden completamente pagada.");
        }
        if (po.getStatus() == PurchaseOrderStatus.CANCELED) {
            throw new IllegalStateException("No se pueden anular pagos de una orden cancelada.");
        }

        SupplierPayment payment = supplierPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierPayment", "id", paymentId));

        if (!payment.getPurchaseOrder().getId().equals(poId)) {
            throw new ResourceNotFoundException("SupplierPayment", "id", paymentId);
        }

        supplierPaymentRepository.delete(payment);

        BigDecimal newPaid = supplierPaymentRepository.sumAmountByPurchaseOrderId(poId);
        po.setPaidAmount(newPaid);
        po.setStatus(newPaid.compareTo(BigDecimal.ZERO) == 0
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_PAID);
        PurchaseOrder updated = purchaseOrderRepository.save(po);

        auditService.record(actorUsername, "PURCHASE_ORDER_PAYMENT_VOIDED", "SupplierPayment",
                paymentId, Map.of("poId", poId.toString()));

        return purchaseOrderMapper.toDto(updated);
    }

    @Override
    public List<PurchaseOrderDto> findPayableOrders() {
        return purchaseOrderRepository.findByStatusIn(
                List.of(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PARTIALLY_PAID))
                .stream().map(purchaseOrderMapper::toDto).toList();
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private void recordSupplierPaymentJournalEntry(PurchaseOrder po, SupplierPayment payment, String username) {
        AccAccount apAccount = requireAccount("2.1.1");
        String cashBankCode = payment.getPaymentMethod() == PaymentMethod.CASH ? "1.1.1.01" : "1.1.1.02";
        AccAccount cashBankAccount = requireAccount(cashBankCode);

        String supplierName = po.getSupplier() != null ? po.getSupplier().getName() : "Desconocido";
        String description = "Pago a suplidor: " + supplierName + " - OC #" + po.getId();
        String reference = "PAG-" + po.getId() + "-" + payment.getId();

        AccJournalEntry entry = createBaseEntry(description, reference,
                AccEntryType.AUTO_SUPPLIER_PAYMENT, username,
                payment.getPaymentDate().toLocalDate());

        addLine(entry, apAccount.getId(), payment.getAmount(), BigDecimal.ZERO, "Cancelación CxP");
        addLine(entry, cashBankAccount.getId(), BigDecimal.ZERO, payment.getAmount(), "Pago a suplidor");

        accJournalEntryRepository.save(entry);
    }

    private AccAccount requireAccount(String code) {
        return accAccountRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Required account not found in chart of accounts: " + code));
    }

    private AccJournalEntry createBaseEntry(String description, String reference,
                                             AccEntryType type, String username, LocalDate entryDate) {
        AccJournalEntry entry = new AccJournalEntry();
        entry.setEntryDate(entryDate);
        entry.setDescription(description);
        entry.setReference(reference);
        entry.setEntryType(type);
        entry.setStatus(AccEntryStatus.POSTED);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setCreatedBy(username);
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedBy(username);
        return entry;
    }

    private void addLine(AccJournalEntry entry, Long accountId,
                         BigDecimal debit, BigDecimal credit, String description) {
        AccJournalEntryLine line = new AccJournalEntryLine();
        line.setJournalEntry(entry);
        line.setAccountId(accountId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setDescription(description);
        entry.getLines().add(line);
    }

    private SupplierPaymentDto toPaymentDto(SupplierPayment p) {
        SupplierPaymentDto dto = new SupplierPaymentDto();
        dto.setId(p.getId());
        dto.setPurchaseOrderId(p.getPurchaseOrder().getId());
        dto.setPaymentDate(p.getPaymentDate());
        dto.setAmount(p.getAmount());
        dto.setPaymentMethod(p.getPaymentMethod());
        dto.setNotes(p.getNotes());
        dto.setCreatedBy(p.getCreatedBy());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}
