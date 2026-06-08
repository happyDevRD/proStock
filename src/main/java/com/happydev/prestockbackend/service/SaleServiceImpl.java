package com.happydev.prestockbackend.service;


import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SalePaymentDto;
import com.happydev.prestockbackend.dto.SaleSummaryDayDto;
import com.happydev.prestockbackend.dto.SaleSummaryDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.SaleMapper;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SalePaymentRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.util.DgiiTaxUtils;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleMapper saleMapper;
    private final StockMovementService stockMovementService;
    private final SequenceService sequenceService;
    private final CompanyConfigRepository companyConfigRepository;
    private final InvoiceQrService invoiceQrService;
    private final AuditService auditService;
    private final SalePaymentRepository salePaymentRepository;

    public SaleServiceImpl(SaleRepository saleRepository,
                           ProductRepository productRepository,
                           CustomerRepository customerRepository,
                           SaleMapper saleMapper,
                           StockMovementService stockMovementService,
                           SequenceService sequenceService,
                           CompanyConfigRepository companyConfigRepository,
                           InvoiceQrService invoiceQrService,
                           AuditService auditService,
                           SalePaymentRepository salePaymentRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleMapper = saleMapper;
        this.stockMovementService = stockMovementService;
        this.sequenceService = sequenceService;
        this.companyConfigRepository = companyConfigRepository;
        this.invoiceQrService = invoiceQrService;
        this.auditService = auditService;
        this.salePaymentRepository = salePaymentRepository;
    }

    @Override
    public List<SaleDto> findAllSales() {
        return saleMapper.toDtoList(saleRepository.findAll());
    }

    @Override
    public Page<SaleDto> findAllSales(@NonNull Pageable pageable) {
        Page<Sale> sales = saleRepository.findAll(pageable);
        return sales.map(saleMapper::toDto);
    }

    @Override
    public Page<SaleDto> findInvoices(
            @NonNull Pageable pageable,
            String ncf,
            Long customerId,
            LocalDate dateFrom,
            LocalDate dateTo,
            @Nullable SaleStatus statusFilter
    ) {
        LocalDateTime startDateTime = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime endDateTime = dateTo != null ? dateTo.atTime(LocalTime.of(23, 59, 59, 999_000_000)) : null;
        Specification<Sale> spec = buildInvoiceSpec(ncf, customerId, startDateTime, endDateTime, statusFilter);
        return saleRepository.findAll(spec, pageable).map(saleMapper::toDto);
    }

    @Override
    public Optional<SaleDto> findSaleByNcf(@NonNull String ncf) {
        return saleRepository.findByNcf(ncf.trim())
                .filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                .map(saleMapper::toDto);
    }

    private Specification<Sale> buildInvoiceSpec(
            String ncf,
            Long customerId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            @Nullable SaleStatus statusFilter
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            } else {
                // Default: include both COMPLETED and PARTIALLY_PAID
                predicates.add(cb.or(
                        cb.equal(root.get("status"), SaleStatus.COMPLETED),
                        cb.equal(root.get("status"), SaleStatus.PARTIALLY_PAID)
                ));
            }
            if (ncf != null && !ncf.isBlank()) {
                predicates.add(cb.isNotNull(root.get("ncf")));
                String pattern = "%" + ncf.trim().toUpperCase() + "%";
                predicates.add(cb.like(cb.upper(root.get("ncf")), pattern));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("saleDate"), endDate));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public Optional<SaleDto> findSaleById(@NonNull Long id) {
        return saleRepository.findById(id).map(saleMapper::toDto);
    }

    @Override
    public SaleDto createSale(@NonNull SaleDto saleDto) {
        return createSale(saleDto, null);
    }

    @Override
    public SaleDto createSale(@NonNull SaleDto saleDto, @Nullable String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            Optional<Sale> existing = saleRepository.findByIdempotencyKey(normalizedKey);
            if (existing.isPresent()) {
                return saleMapper.toDto(existing.get());
            }
        }

        Sale sale = saleMapper.toEntity(saleDto);
        sale.setSaleDate(LocalDateTime.now());
        sale.setStatus(SaleStatus.PENDING);
        sale.setTipoIngresos(TipoIngresos.OPERACIONES);

        if (saleDto.getCustomerId() != null) {
            Long customerId = Objects.requireNonNull(saleDto.getCustomerId());
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
            sale.setCustomer(customer);
        }

        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                item.setSale(sale);
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                enrichSaleItemFromProduct(item, productId);
            }
        }

        // Pre-calculate tax totals so the draft shows the correct amount for payment tracking
        recalculateTaxTotals(sale);
        ensureSaleMoneyColumnsForPersist(sale);

        if (normalizedKey != null) {
            sale.setIdempotencyKey(normalizedKey);
        }

        Sale savedSale;
        try {
            savedSale = saleRepository.save(sale);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedKey != null) {
                Optional<Sale> raced = saleRepository.findByIdempotencyKey(normalizedKey);
                if (raced.isPresent()) {
                    return saleMapper.toDto(raced.get());
                }
            }
            throw ex;
        }

        Map<String, String> createdDetails = new HashMap<>();
        createdDetails.put("status", savedSale.getStatus() != null ? savedSale.getStatus().name() : "");
        createdDetails.put("itemCount", String.valueOf(savedSale.getItems() != null ? savedSale.getItems().size() : 0));
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "SALE_CREATED",
                "Sale",
                savedSale.getId(),
                createdDetails
        );
        return saleMapper.toDto(savedSale);
    }

    @Override
    public SaleDto updateSale(@NonNull Long id, @NonNull SaleDto saleDto) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        if (saleDto.getCustomerId() != null) {
            Long customerId = Objects.requireNonNull(saleDto.getCustomerId());
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
            sale.setCustomer(customer);
        } else {
            sale.setCustomer(null);
        }

        if (saleDto.getStatus() != null) {
            sale.setStatus(saleDto.getStatus());
        }

        if (saleDto.getTipoComprobante() != null && !saleDto.getTipoComprobante().isBlank()) {
            sale.setTipoComprobante(saleDto.getTipoComprobante());
        }
        if (saleDto.getPaymentMethod() != null) {
            sale.setPaymentMethod(saleDto.getPaymentMethod());
        }

        sale.setDiscountAmount(saleDto.getDiscountAmount() != null ? saleDto.getDiscountAmount() : BigDecimal.ZERO);

        sale.getItems().clear();
        if (saleDto.getItems() != null) {
            List<SaleItem> newItems = saleMapper.toItemEntityList(saleDto.getItems());
            for (SaleItem item : newItems) {
                item.setSale(sale);
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                enrichSaleItemFromProduct(item, productId);
                sale.getItems().add(item);
            }
        }

        if (sale.getStatus() == SaleStatus.PENDING) {
            recalculateTaxTotals(sale);
            ensureSaleMoneyColumnsForPersist(sale);
        }

        Sale updatedSale = saleRepository.save(sale);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "SALE_UPDATED",
                "Sale",
                id,
                Map.of("status", updatedSale.getStatus() != null ? updatedSale.getStatus().name() : "")
        );
        return saleMapper.toDto(updatedSale);
    }

    @Override
    public void deleteSale(@NonNull Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));
        Long saleId = sale.getId();
        SaleStatus statusBefore = sale.getStatus();
        saleRepository.delete(Objects.requireNonNull(sale));
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "SALE_DELETED",
                "Sale",
                saleId,
                Map.of("status", statusBefore != null ? statusBefore.name() : "")
        );
    }

    @Override
    @Transactional
    public SaleDto completeSale(@NonNull Long id, @Nullable String actorUsername) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        if (sale.getStatus() == SaleStatus.COMPLETED) {
            return saleMapper.toDto(sale);
        }

        if (sale.getStatus() != SaleStatus.PENDING && sale.getStatus() != SaleStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Cannot complete a sale that is not in PENDING or PARTIALLY_PAID status.");
        }

        if (sale.getTipoComprobante() == null || sale.getTipoComprobante().isBlank()) {
            throw new IllegalStateException("No se puede completar una venta sin tipoComprobante");
        }

        if (sale.getPaymentMethod() == null) {
            sale.setPaymentMethod(PaymentMethod.CASH);
        }

        // Recalculate fresh totals (in case items changed since draft creation)
        recalculateTaxTotals(sale);

        Sale updatedSale = finalizeSaleAsCompleted(sale, actorUsername);
        return saleMapper.toDto(updatedSale);
    }

    @Override
    public SalePaymentDto addPayment(@NonNull Long saleId,
                                     @NonNull BigDecimal amount,
                                     @NonNull PaymentMethod paymentMethod,
                                     @Nullable String notes,
                                     @Nullable String actorUsername) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", saleId));

        if (sale.getStatus() != SaleStatus.PENDING && sale.getStatus() != SaleStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("No se puede abonar a una venta que no esté en estado PENDING o PARTIALLY_PAID.");
        }

        BigDecimal roundedAmount = DgiiTaxUtils.roundMoney(amount);
        BigDecimal alreadyPaid = salePaymentRepository.sumAmountBySaleId(saleId);
        BigDecimal pendingBalance = sale.getMontoTotal().subtract(alreadyPaid);
        if (roundedAmount.compareTo(pendingBalance) > 0) {
            throw new IllegalArgumentException(
                    "El monto del abono (" + roundedAmount.toPlainString()
                            + ") excede el saldo pendiente de la venta (" + pendingBalance.toPlainString() + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        SalePayment payment = new SalePayment();
        payment.setSale(sale);
        payment.setPaymentDate(now);
        payment.setAmount(roundedAmount);
        payment.setPaymentMethod(paymentMethod);
        payment.setNotes(notes);
        payment.setCreatedBy(actorUsername);
        payment.setCreatedAt(now);
        SalePayment savedPayment = salePaymentRepository.save(payment);

        // Recalculate total paid
        BigDecimal totalPaid = salePaymentRepository.sumAmountBySaleId(saleId);
        sale.setPaidAmount(DgiiTaxUtils.roundMoney(totalPaid));

        // Set paymentMethod on the sale (last payment method wins for display)
        sale.setPaymentMethod(paymentMethod);

        if (totalPaid.compareTo(sale.getMontoTotal()) >= 0
                && sale.getTipoComprobante() != null && !sale.getTipoComprobante().isBlank()) {
            // Fully paid with comprobante ready: fiscally finalize the sale
            recalculateTaxTotals(sale);
            finalizeSaleAsCompleted(sale, actorUsername);
        } else {
            sale.setStatus(SaleStatus.PARTIALLY_PAID);
            saleRepository.save(sale);
        }

        auditService.record(actorUsername, "SALE_PAYMENT_ADDED", "Sale", saleId,
                Map.of("amount", amount.toPlainString(), "method", paymentMethod.name()));

        SalePaymentDto dto = new SalePaymentDto();
        dto.setId(savedPayment.getId());
        dto.setSaleId(saleId);
        dto.setPaymentDate(savedPayment.getPaymentDate());
        dto.setAmount(savedPayment.getAmount());
        dto.setPaymentMethod(savedPayment.getPaymentMethod());
        dto.setNotes(savedPayment.getNotes());
        dto.setCreatedBy(savedPayment.getCreatedBy());
        dto.setCreatedAt(savedPayment.getCreatedAt());
        return dto;
    }

    @Override
    public List<SalePaymentDto> getPaymentsForSale(@NonNull Long saleId) {
        if (!saleRepository.existsById(saleId)) {
            throw new ResourceNotFoundException("Sale", "id", saleId);
        }
        return salePaymentRepository.findBySaleIdOrderByPaymentDateAsc(saleId)
                .stream()
                .map(p -> {
                    SalePaymentDto dto = new SalePaymentDto();
                    dto.setId(p.getId());
                    dto.setSaleId(saleId);
                    dto.setPaymentDate(p.getPaymentDate());
                    dto.setAmount(p.getAmount());
                    dto.setPaymentMethod(p.getPaymentMethod());
                    dto.setNotes(p.getNotes());
                    dto.setCreatedBy(p.getCreatedBy());
                    dto.setCreatedAt(p.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SaleDto voidPayment(@NonNull Long saleId, @NonNull Long paymentId, @Nullable String actorUsername) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", saleId));

        if (sale.getStatus() == SaleStatus.COMPLETED || sale.getStatus() == SaleStatus.CANCELED) {
            throw new IllegalStateException(
                    "No se puede anular un abono de una venta " +
                            (sale.getStatus() == SaleStatus.COMPLETED ? "ya completada (con NCF asignado)." : "cancelada."));
        }

        SalePayment payment = salePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("SalePayment", "id", paymentId));

        if (payment.getSale() == null || !saleId.equals(payment.getSale().getId())) {
            throw new IllegalArgumentException("El abono indicado no pertenece a esta venta.");
        }

        BigDecimal voidedAmount = payment.getAmount();
        salePaymentRepository.delete(payment);

        BigDecimal totalPaid = salePaymentRepository.sumAmountBySaleId(saleId);
        sale.setPaidAmount(DgiiTaxUtils.roundMoney(totalPaid));
        sale.setStatus(totalPaid.compareTo(BigDecimal.ZERO) > 0 ? SaleStatus.PARTIALLY_PAID : SaleStatus.PENDING);
        Sale updatedSale = saleRepository.save(sale);

        auditService.record(actorUsername, "SALE_PAYMENT_VOIDED", "Sale", saleId,
                Map.of("paymentId", paymentId.toString(), "amount", voidedAmount.toPlainString()));

        return saleMapper.toDto(updatedSale);
    }

    /**
     * Calculates ITBIS tax totals for all items in the sale and updates the sale's monetary fields.
     * Does NOT assign NCF, deduct stock, or generate QR. Safe to call on draft (PENDING) sales.
     */
    private void recalculateTaxTotals(Sale sale) {
        BigDecimal gravado18 = BigDecimal.ZERO;
        BigDecimal gravado16 = BigDecimal.ZERO;
        BigDecimal gravado0 = BigDecimal.ZERO;
        BigDecimal montoExento = BigDecimal.ZERO;
        BigDecimal totalItbis = BigDecimal.ZERO;

        for (SaleItem item : sale.getItems()) {
            Long productId = Objects.requireNonNull(item.getProduct().getId());
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

            BigDecimal lineBase = DgiiTaxUtils.roundMoney(
                    item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            IndicadorFacturacion indicador = product.getIndicadorFacturacion() == null
                    ? IndicadorFacturacion.EXENTO
                    : product.getIndicadorFacturacion();

            if (indicador.isExento()) {
                montoExento = montoExento.add(lineBase);
            } else {
                switch (indicador) {
                    case ITBIS_18 -> gravado18 = gravado18.add(lineBase);
                    case ITBIS_16 -> gravado16 = gravado16.add(lineBase);
                    case ITBIS_0 -> gravado0 = gravado0.add(lineBase);
                    default -> { }
                }
                BigDecimal lineTax = DgiiTaxUtils.roundMoney(lineBase.multiply(indicador.getRate()));
                totalItbis = totalItbis.add(lineTax);
            }
        }

        // Apply global discount proportionally to base amounts (before ITBIS)
        BigDecimal discount = sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalBase = gravado18.add(gravado16).add(gravado0).add(montoExento);
            if (totalBase.compareTo(BigDecimal.ZERO) > 0) {
                discount = discount.min(totalBase);
                BigDecimal factor = totalBase.subtract(discount).divide(totalBase, 10, java.math.RoundingMode.HALF_UP);
                gravado18 = DgiiTaxUtils.roundMoney(gravado18.multiply(factor));
                gravado16 = DgiiTaxUtils.roundMoney(gravado16.multiply(factor));
                gravado0 = DgiiTaxUtils.roundMoney(gravado0.multiply(factor));
                montoExento = DgiiTaxUtils.roundMoney(montoExento.multiply(factor));
                totalItbis = DgiiTaxUtils.roundMoney(totalItbis.multiply(factor));
            }
        }

        BigDecimal montoGravadoTotal = DgiiTaxUtils.roundMoney(gravado18.add(gravado16).add(gravado0));
        BigDecimal montoExentoRounded = DgiiTaxUtils.roundMoney(montoExento);
        BigDecimal totalItbisRounded = DgiiTaxUtils.roundMoney(totalItbis);
        BigDecimal montoTotal = DgiiTaxUtils.roundMoney(montoGravadoTotal.add(montoExentoRounded).add(totalItbisRounded));

        sale.setMontoGravadoTotal(montoGravadoTotal);
        sale.setMontoExento(montoExentoRounded);
        sale.setTotalItbis(totalItbisRounded);
        sale.setMontoTotal(montoTotal);
    }

    /**
     * Fiscally finalizes a sale: assigns NCF, deducts stock, generates QR and security code,
     * sets paidAmount = montoTotal, sets status = COMPLETED. Assumes recalculateTaxTotals()
     * has already been called. The sale must have tipoComprobante set.
     */
    private Sale finalizeSaleAsCompleted(Sale sale, @Nullable String actorUsername) {
        LocalDateTime now = LocalDateTime.now();

        if (sale.getNcf() == null || sale.getNcf().isBlank()) {
            sale.setNcf(sequenceService.getNextSequence(sale.getTipoComprobante()));
        }

        // Deduct stock for goods
        for (SaleItem item : sale.getItems()) {
            Long productId = Objects.requireNonNull(item.getProduct().getId());
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

            boolean esServicio = product.getTipoBienServicio() == TipoBienServicio.SERVICIO;
            if (!esServicio) {
                if (product.getStock() < item.getQuantity()) {
                    throw new IllegalStateException("Existencias insuficientes para: " + product.getName());
                }
                StockMovement movement = new StockMovement();
                movement.setProduct(product);
                movement.setMovementDate(now);
                movement.setQuantityChange(-item.getQuantity());
                movement.setType(StockMovementType.OUT);
                movement.setReason("Sale completed");
                movement.setSale(sale);
                stockMovementService.createMovement(movement);
            }
        }

        String rncEmisor = companyConfigRepository.findFirstByOrderByIdAsc()
                .map(CompanyConfig::getRnc)
                .orElse("");
        String rncComprador = sale.getCustomer() != null && sale.getCustomer().getRncCedula() != null
                ? sale.getCustomer().getRncCedula()
                : "";
        BigDecimal montoTotal = sale.getMontoTotal();
        String codigoSeguridad = createSecurityCode(rncEmisor, sale.getNcf(), rncComprador, sale.getSaleDate(), montoTotal, now);
        String qrPayloadUrl = invoiceQrService.buildPayloadUrl(
                rncEmisor, sale.getNcf(), rncComprador, sale.getSaleDate(),
                montoTotal.toPlainString(), now, codigoSeguridad
        );

        sale.setTipoIngresos(TipoIngresos.OPERACIONES);
        sale.setFechaFirma(now);
        sale.setCodigoSeguridad(codigoSeguridad);
        sale.setQrPayloadUrl(qrPayloadUrl);
        sale.setQrCodeBase64(invoiceQrService.generateBase64Qr(qrPayloadUrl));
        sale.setPaidAmount(montoTotal);
        sale.setStatus(SaleStatus.COMPLETED);

        Sale updatedSale = saleRepository.save(sale);

        Map<String, String> details = new HashMap<>();
        details.put("ncf", Optional.ofNullable(updatedSale.getNcf()).orElse(""));
        details.put("montoTotal", updatedSale.getMontoTotal() != null ? updatedSale.getMontoTotal().toPlainString() : "");
        details.put("paymentMethod", updatedSale.getPaymentMethod() != null ? updatedSale.getPaymentMethod().name() : "");
        auditService.record(actorUsername, "SALE_COMPLETED", "Sale", updatedSale.getId(), details);

        return updatedSale;
    }

    private String createSecurityCode(String rncEmisor,
                                      String ncf,
                                      String rncComprador,
                                      LocalDateTime fechaEmision,
                                      BigDecimal montoTotal,
                                      LocalDateTime fechaFirma) {
        String payload = String.join("|",
                rncEmisor == null ? "" : rncEmisor,
                ncf == null ? "" : ncf,
                rncComprador == null ? "" : rncComprador,
                fechaEmision == null ? "" : fechaEmision.toString(),
                montoTotal == null ? "0.00" : montoTotal.toPlainString(),
                fechaFirma == null ? "" : fechaFirma.toString()
        );
        return sha256Hex(payload).substring(0, 6).toUpperCase();
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    @Override
    public List<SaleDto> findSalesForExport(
            @Nullable LocalDateTime startDate,
            @Nullable LocalDateTime endDate,
            @Nullable SaleStatus status
    ) {
        Sort sort = Sort.by(Sort.Direction.DESC, "saleDate");
        return saleMapper.toDtoList(
                saleRepository.findAll(buildExportSpec(startDate, endDate, status), sort)
        );
    }

    private Specification<Sale> buildExportSpec(
            @Nullable LocalDateTime startDate,
            @Nullable LocalDateTime endDate,
            @Nullable SaleStatus status
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("saleDate"), endDate));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public SaleSummaryDto getSalesSummary(
            @Nullable LocalDateTime startDate,
            @Nullable LocalDateTime endDate,
            @Nullable SaleStatus statusFilter
    ) {
        long completedCount = saleRepository.countInRange(SaleStatus.COMPLETED, startDate, endDate);
        long pendingCount = saleRepository.countInRange(SaleStatus.PENDING, startDate, endDate);
        long canceledCount = saleRepository.countInRange(SaleStatus.CANCELED, startDate, endDate);

        if (statusFilter == SaleStatus.COMPLETED) {
            pendingCount = 0;
            canceledCount = 0;
        } else if (statusFilter == SaleStatus.PENDING) {
            completedCount = 0;
            canceledCount = 0;
        } else if (statusFilter == SaleStatus.CANCELED) {
            completedCount = 0;
            pendingCount = 0;
        }

        long totalCount = completedCount + pendingCount + canceledCount;

        boolean includeCompletedRevenue = statusFilter == null || statusFilter == SaleStatus.COMPLETED;
        BigDecimal completedRevenue = includeCompletedRevenue
                ? saleRepository.sumCompletedRevenue(startDate, endDate)
                : BigDecimal.ZERO;
        BigDecimal completedTax = includeCompletedRevenue
                ? saleRepository.sumCompletedTax(startDate, endDate)
                : BigDecimal.ZERO;

        BigDecimal averageTicket = completedCount > 0
                ? completedRevenue.divide(BigDecimal.valueOf(completedCount), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double completionRate = totalCount > 0
                ? (completedCount * 100.0) / totalCount
                : 0.0;

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        long todayCompletedCount = saleRepository.countCompletedBetween(todayStart, todayEnd);
        BigDecimal todayRevenue = saleRepository.sumCompletedRevenueBetween(todayStart, todayEnd);
        BigDecimal todayAverageTicket = todayCompletedCount > 0
                ? todayRevenue.divide(BigDecimal.valueOf(todayCompletedCount), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<SaleSummaryDayDto> topDays = saleRepository
                .findTopCompletedDaysByRevenue(startDate, endDate)
                .stream()
                .map(this::mapSummaryDayRow)
                .collect(Collectors.toList());

        LocalDateTime trendStart = LocalDate.now().minusDays(7).atStartOfDay();
        List<SaleSummaryDayDto> revenueTrend = saleRepository
                .findCompletedDailyTrend(trendStart)
                .stream()
                .map(this::mapSummaryDayRow)
                .collect(Collectors.toList());

        long partiallyPaidCount = saleRepository.countPartiallyPaid();
        BigDecimal totalPendingBalance = saleRepository.sumPendingBalance();

        return new SaleSummaryDto(
                totalCount,
                completedCount,
                pendingCount,
                canceledCount,
                partiallyPaidCount,
                completedRevenue,
                completedTax,
                averageTicket,
                completionRate,
                todayCompletedCount,
                todayRevenue,
                todayAverageTicket,
                totalPendingBalance,
                topDays,
                revenueTrend
        );
    }

    private SaleSummaryDayDto mapSummaryDayRow(Object[] row) {
        String date = row[0] != null ? row[0].toString() : "";
        long count = row[1] instanceof Number number ? number.longValue() : 0L;
        BigDecimal revenue = toBigDecimal(row[2]);
        BigDecimal tax = row.length > 3 ? toBigDecimal(row[3]) : BigDecimal.ZERO;
        return new SaleSummaryDayDto(date, count, revenue, tax);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    @Nullable
    private static String normalizeIdempotencyKey(@Nullable String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        String trimmed = idempotencyKey.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private void enrichSaleItemFromProduct(SaleItem item, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
    }

    private static void ensureSaleMoneyColumnsForPersist(Sale sale) {
        if (sale.getMontoGravadoTotal() == null) sale.setMontoGravadoTotal(BigDecimal.ZERO);
        if (sale.getMontoExento() == null) sale.setMontoExento(BigDecimal.ZERO);
        if (sale.getTotalItbis() == null) sale.setTotalItbis(BigDecimal.ZERO);
        if (sale.getMontoTotal() == null) sale.setMontoTotal(BigDecimal.ZERO);
        if (sale.getPaidAmount() == null) sale.setPaidAmount(BigDecimal.ZERO);
        if (sale.getTipoIngresos() == null) sale.setTipoIngresos(TipoIngresos.OPERACIONES);
    }
}
