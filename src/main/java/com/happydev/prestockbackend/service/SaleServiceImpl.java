package com.happydev.prestockbackend.service;


import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.SaleMapper;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SaleItemRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.util.DgiiTaxUtils;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;

    private final SaleItemRepository saleItemRepository; //Si fuera necesario.

    private final ProductRepository productRepository; // Para actualizar el stock

    private final CustomerRepository customerRepository; // Para actualizar el stock

    private final SaleMapper saleMapper;

    private final StockMovementService stockMovementService;

    private final SequenceService sequenceService;

    private final CompanyConfigRepository companyConfigRepository;

    private final InvoiceQrService invoiceQrService;

    public SaleServiceImpl(SaleRepository saleRepository,
                           SaleItemRepository saleItemRepository,
                           ProductRepository productRepository,
                           CustomerRepository customerRepository,
                           SaleMapper saleMapper,
                           StockMovementService stockMovementService,
                           SequenceService sequenceService,
                           CompanyConfigRepository companyConfigRepository,
                           InvoiceQrService invoiceQrService) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleMapper = saleMapper;
        this.stockMovementService = stockMovementService;
        this.sequenceService = sequenceService;
        this.companyConfigRepository = companyConfigRepository;
        this.invoiceQrService = invoiceQrService;
    }

    @Override
    public List<SaleDto> findAllSales() {
        return saleMapper.toDtoList(saleRepository.findAll());
    }

    @Override
    public Page<SaleDto> findAllSales(Pageable pageable) {
        Page<Sale> sales = saleRepository.findAll(pageable);
        return sales.map(saleMapper::toDto);
    }

    @Override
    public Optional<SaleDto> findSaleById(Long id) {
        return saleRepository.findById(id).map(saleMapper::toDto);
    }

    @Override
    public SaleDto createSale(SaleDto saleDto) {

        //Convertir a entidad.
        Sale sale = saleMapper.toEntity(saleDto);

        //Poner la fecha actual.
        sale.setSaleDate(LocalDateTime.now());
        //Establecer estado inicial
        sale.setStatus(SaleStatus.PENDING);
        sale.setTipoIngresos(TipoIngresos.OPERACIONES);

        //Si se cambia el customer
        if(saleDto.getCustomerId() != null){
            Customer customer = customerRepository.findById(saleDto.getCustomerId())
                    .orElseThrow(()-> new ResourceNotFoundException("Customer", "id", saleDto.getCustomerId()));
            sale.setCustomer(customer); //Asignamos el customer.
        }

        // Establecer la relación bidireccional con los ítems (MUY IMPORTANTE)
        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                item.setSale(sale); // Asigna la venta a cada ítem.
                //Validaciones
                if(!productRepository.existsById(item.getProduct().getId())){
                    throw new ResourceNotFoundException("Product", "id", item.getProduct().getId());
                }
            }
        }

        //Guardar en db
        Sale savedSale = saleRepository.save(sale);
        return saleMapper.toDto(savedSale);

    }

    @Override
    public SaleDto updateSale(Long id, SaleDto saleDto) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        // La fecha no se debería poder cambiar.

        // Si se cambia el customer (OBTENER EL CLIENTE)
        if (saleDto.getCustomerId() != null) {
            Customer customer = customerRepository.findById(saleDto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", saleDto.getCustomerId()));
            sale.setCustomer(customer);
        }

        // Si cambia el estado
        if (saleDto.getStatus() != null) {
            sale.setStatus(saleDto.getStatus());
        }

        // Actualizar Items:
        // 1. Eliminar Items Antiguos:
        sale.getItems().clear();

        // 2. Agregar y asociar nuevos items
        if (saleDto.getItems() != null) {
            List<SaleItem> newItems = saleMapper.toItemEntityList(saleDto.getItems());
            for (SaleItem item : newItems) {
                item.setSale(sale);
                if (!productRepository.existsById(item.getProduct().getId())) {
                    throw new ResourceNotFoundException("Product", "id", item.getProduct().getId());
                }
                sale.getItems().add(item);
            }
        }

        // 3. Persistir
        Sale updatedSale = saleRepository.save(sale);
        return saleMapper.toDto(updatedSale);
    }

    @Override
    public void deleteSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));
        saleRepository.delete(sale); // orphanRemoval=true se encarga de los ítems
    }

    // Método para finalizar una venta y descontar el stock
    @Override
    @Transactional // Importante para que la actualización del stock sea atómica
    public SaleDto completeSale(Long id) {
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

        LocalDateTime now = LocalDateTime.now();
        BigDecimal gravado18 = BigDecimal.ZERO;
        BigDecimal gravado16 = BigDecimal.ZERO;
        BigDecimal gravado0 = BigDecimal.ZERO;
        BigDecimal montoExento = BigDecimal.ZERO;
        BigDecimal totalItbis = BigDecimal.ZERO;

        // Registrar movimientos de stock y calcular desglose tributario
        for (SaleItem item : sale.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(()-> new ResourceNotFoundException("Product", "id", item.getProduct().getId()));

            //Verificar si hay stock
            if(product.getStock() < item.getQuantity()){
                throw new IllegalStateException("Not enough stock for product: " + product.getName());
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

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementDate(now);
            movement.setQuantityChange(-item.getQuantity());
            movement.setType(StockMovementType.OUT);
            movement.setReason("Sale completed");
            movement.setSale(sale);
            stockMovementService.createMovement(movement);

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
}
