package com.happydev.prestockbackend.service;


import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.SaleMapper;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.util.DgiiTaxUtils;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;

    private final ProductRepository productRepository; // Para actualizar el stock

    private final CustomerRepository customerRepository; // Para actualizar el stock

    private final SaleMapper saleMapper;

    private final StockMovementService stockMovementService;

    private final SequenceService sequenceService;

    private final CompanyConfigRepository companyConfigRepository;

    private final InvoiceQrService invoiceQrService;

    private final AuditService auditService;

    public SaleServiceImpl(SaleRepository saleRepository,
                           ProductRepository productRepository,
                           CustomerRepository customerRepository,
                           SaleMapper saleMapper,
                           StockMovementService stockMovementService,
                           SequenceService sequenceService,
                           CompanyConfigRepository companyConfigRepository,
                           InvoiceQrService invoiceQrService,
                           AuditService auditService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleMapper = saleMapper;
        this.stockMovementService = stockMovementService;
        this.sequenceService = sequenceService;
        this.companyConfigRepository = companyConfigRepository;
        this.invoiceQrService = invoiceQrService;
        this.auditService = auditService;
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
            LocalDate dateTo
    ) {
        LocalDateTime startDateTime = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime endDateTime = dateTo != null ? dateTo.atTime(LocalTime.of(23, 59, 59, 999_000_000)) : null;
        Specification<Sale> spec = buildInvoiceSpec(ncf, customerId, startDateTime, endDateTime);
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
            LocalDateTime endDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), SaleStatus.COMPLETED));
            predicates.add(cb.isNotNull(root.get("ncf")));
            if (ncf != null && !ncf.isBlank()) {
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

        //Convertir a entidad.
        Sale sale = saleMapper.toEntity(saleDto);

        //Poner la fecha actual.
        sale.setSaleDate(LocalDateTime.now());
        //Establecer estado inicial
        sale.setStatus(SaleStatus.PENDING);
        sale.setTipoIngresos(TipoIngresos.OPERACIONES);

        //Si se cambia el customer
        if(saleDto.getCustomerId() != null){
            Long customerId = Objects.requireNonNull(saleDto.getCustomerId());
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(()-> new ResourceNotFoundException("Customer", "id", customerId));
            sale.setCustomer(customer); //Asignamos el customer.
        }

        // Establecer la relación bidireccional con los ítems (MUY IMPORTANTE)
        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                item.setSale(sale); // Asigna la venta a cada ítem.
                //Validaciones
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                if(!productRepository.existsById(productId)){
                    throw new ResourceNotFoundException("Product", "id", productId);
                }
            }
        }

        ensureSaleMoneyColumnsForPersist(sale);

        //Guardar en db
        Sale savedSale = saleRepository.save(sale);
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

        // La fecha no se debería poder cambiar.

        // Si se cambia el customer (OBTENER EL CLIENTE)
        if (saleDto.getCustomerId() != null) {
            Long customerId = Objects.requireNonNull(saleDto.getCustomerId());
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
            sale.setCustomer(customer);
        } else {
            sale.setCustomer(null);
        }

        // Si cambia el estado
        if (saleDto.getStatus() != null) {
            sale.setStatus(saleDto.getStatus());
        }

        if (saleDto.getTipoComprobante() != null && !saleDto.getTipoComprobante().isBlank()) {
            sale.setTipoComprobante(saleDto.getTipoComprobante());
        }
        if (saleDto.getPaymentMethod() != null) {
            sale.setPaymentMethod(saleDto.getPaymentMethod());
        }

        // Actualizar Items:
        // 1. Eliminar Items Antiguos:
        sale.getItems().clear();

        // 2. Agregar y asociar nuevos items
        if (saleDto.getItems() != null) {
            List<SaleItem> newItems = saleMapper.toItemEntityList(saleDto.getItems());
            for (SaleItem item : newItems) {
                item.setSale(sale);
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                if (!productRepository.existsById(productId)) {
                    throw new ResourceNotFoundException("Product", "id", productId);
                }
                sale.getItems().add(item);
            }
        }

        if (sale.getStatus() == SaleStatus.PENDING) {
            ensureSaleMoneyColumnsForPersist(sale);
        }

        // 3. Persistir
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
        saleRepository.delete(Objects.requireNonNull(sale)); // orphanRemoval=true se encarga de los ítems
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "SALE_DELETED",
                "Sale",
                saleId,
                Map.of("status", statusBefore != null ? statusBefore.name() : "")
        );
    }

    // Método para finalizar una venta y descontar el stock
    @Override
    @Transactional // Importante para que la actualización del stock sea atómica
    public SaleDto completeSale(@NonNull Long id, @Nullable String actorUsername) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        // Verificar que la venta esté en estado PENDING
        if (sale.getStatus() != SaleStatus.PENDING) {
            throw new IllegalStateException("Cannot complete a sale that is not in PENDING status.");
        }

        if (sale.getTipoComprobante() == null || sale.getTipoComprobante().isBlank()) {
            throw new IllegalStateException("No se puede completar una venta sin tipoComprobante");
        }

        if (sale.getNcf() == null || sale.getNcf().isBlank()) {
            sale.setNcf(sequenceService.getNextSequence(sale.getTipoComprobante()));
        }

        if (sale.getPaymentMethod() == null) {
            sale.setPaymentMethod(PaymentMethod.CASH);
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal gravado18 = BigDecimal.ZERO;
        BigDecimal gravado16 = BigDecimal.ZERO;
        BigDecimal gravado0 = BigDecimal.ZERO;
        BigDecimal montoExento = BigDecimal.ZERO;
        BigDecimal totalItbis = BigDecimal.ZERO;

        // Registrar movimientos de stock y calcular desglose tributario
        for (SaleItem item : sale.getItems()) {
            Long productId = Objects.requireNonNull(item.getProduct().getId());
            Product product = productRepository.findById(productId)
                    .orElseThrow(()-> new ResourceNotFoundException("Product", "id", productId));

            boolean esServicio = product.getTipoBienServicio() == TipoBienServicio.SERVICIO;
            if (!esServicio && product.getStock() < item.getQuantity()) {
                throw new IllegalStateException("Existencias insuficientes para: " + product.getName());
            }

            BigDecimal lineBase = DgiiTaxUtils.roundMoney(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
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
                    default -> {
                    }
                }
                BigDecimal lineTax = DgiiTaxUtils.roundMoney(lineBase.multiply(indicador.getRate()));
                totalItbis = totalItbis.add(lineTax);
            }

            if (!esServicio) {
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
        BigDecimal montoGravadoTotal = DgiiTaxUtils.roundMoney(gravado18.add(gravado16).add(gravado0));
        BigDecimal montoExentoRounded = DgiiTaxUtils.roundMoney(montoExento);
        BigDecimal totalItbisRounded = DgiiTaxUtils.roundMoney(totalItbis);
        BigDecimal montoTotal = DgiiTaxUtils.roundMoney(montoGravadoTotal.add(montoExentoRounded).add(totalItbisRounded));

        String rncEmisor = companyConfigRepository.findFirstByOrderByIdAsc()
                .map(CompanyConfig::getRnc)
                .orElse("");
        String rncComprador = sale.getCustomer() != null && sale.getCustomer().getRncCedula() != null
                ? sale.getCustomer().getRncCedula()
                : "";
        String codigoSeguridad = createSecurityCode(rncEmisor, sale.getNcf(), rncComprador, sale.getSaleDate(), montoTotal, now);
        String qrPayloadUrl = invoiceQrService.buildPayloadUrl(
                rncEmisor,
                sale.getNcf(),
                rncComprador,
                sale.getSaleDate(),
                montoTotal.toPlainString(),
                now,
                codigoSeguridad
        );

        sale.setMontoGravadoTotal(montoGravadoTotal);
        sale.setMontoExento(montoExentoRounded);
        sale.setTotalItbis(totalItbisRounded);
        sale.setMontoTotal(montoTotal);
        sale.setTipoIngresos(TipoIngresos.OPERACIONES);
        sale.setFechaFirma(now);
        sale.setCodigoSeguridad(codigoSeguridad);
        sale.setQrPayloadUrl(qrPayloadUrl);
        sale.setQrCodeBase64(invoiceQrService.generateBase64Qr(qrPayloadUrl));
        sale.setStatus(SaleStatus.COMPLETED);
        Sale updatedSale = saleRepository.save(sale);

        Map<String, String> details = new HashMap<>();
        details.put("ncf", Optional.ofNullable(updatedSale.getNcf()).orElse(""));
        details.put("montoTotal", updatedSale.getMontoTotal() != null ? updatedSale.getMontoTotal().toPlainString() : "");
        details.put("paymentMethod", updatedSale.getPaymentMethod() != null ? updatedSale.getPaymentMethod().name() : "");
        auditService.record(actorUsername, "SALE_COMPLETED", "Sale", updatedSale.getId(), details);

        return saleMapper.toDto(updatedSale);
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

    /**
     * El POS crea borradores sin montos; MapStruct pone {@code null} y la columna en BD es NOT NULL.
     */
    private static void ensureSaleMoneyColumnsForPersist(Sale sale) {
        if (sale.getMontoGravadoTotal() == null) {
            sale.setMontoGravadoTotal(BigDecimal.ZERO);
        }
        if (sale.getMontoExento() == null) {
            sale.setMontoExento(BigDecimal.ZERO);
        }
        if (sale.getTotalItbis() == null) {
            sale.setTotalItbis(BigDecimal.ZERO);
        }
        if (sale.getMontoTotal() == null) {
            sale.setMontoTotal(BigDecimal.ZERO);
        }
        if (sale.getTipoIngresos() == null) {
            sale.setTipoIngresos(TipoIngresos.OPERACIONES);
        }
    }
}
