package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SalePaymentDto;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.IndicadorFacturacion;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleItem;
import com.happydev.prestockbackend.entity.SalePayment;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.entity.TipoIngresos;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.SaleMapper;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.LocationRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SaleItemRepository;
import com.happydev.prestockbackend.repository.SalePaymentRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.repository.StockLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceImplTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private SaleItemRepository saleItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private SaleMapper saleMapper;
    @Mock
    private StockMovementService stockMovementService;
    @Mock
    private SequenceService sequenceService;
    @Mock
    private CompanyConfigRepository companyConfigRepository;
    @Mock
    private InvoiceQrService invoiceQrService;
    @Mock
    private AuditService auditService;
    @Mock
    private SalePaymentRepository salePaymentRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private StockLocationRepository stockLocationRepository;

    @InjectMocks
    private SaleServiceImpl saleService;

    private Sale sale;
    private Product taxableProduct;
    private Product exentoProduct;

    @BeforeEach
    void setUp() {
        taxableProduct = new Product();
        taxableProduct.setId(10L);
        taxableProduct.setName("Producto ITBIS");
        taxableProduct.setStock(50);
        taxableProduct.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        taxableProduct.setTipoBienServicio(TipoBienServicio.BIEN);

        exentoProduct = new Product();
        exentoProduct.setId(20L);
        exentoProduct.setName("Producto Exento");
        exentoProduct.setStock(40);
        exentoProduct.setIndicadorFacturacion(IndicadorFacturacion.EXENTO);
        exentoProduct.setTipoBienServicio(TipoBienServicio.BIEN);

        SaleItem taxableItem = new SaleItem();
        taxableItem.setId(1L);
        taxableItem.setProduct(taxableProduct);
        taxableItem.setQuantity(2);
        taxableItem.setUnitPrice(new BigDecimal("100.00"));

        SaleItem exentoItem = new SaleItem();
        exentoItem.setId(2L);
        exentoItem.setProduct(exentoProduct);
        exentoItem.setQuantity(1);
        exentoItem.setUnitPrice(new BigDecimal("50.00"));

        sale = new Sale();
        sale.setId(99L);
        sale.setSaleDate(LocalDateTime.of(2026, 4, 27, 10, 30));
        sale.setStatus(SaleStatus.PENDING);
        sale.setTipoComprobante("31");
        sale.setItems(List.of(taxableItem, exentoItem));
        taxableItem.setSale(sale);
        exentoItem.setSale(sale);

        Customer customer = new Customer();
        customer.setId(5L);
        customer.setRncCedula("00112345678");
        sale.setCustomer(customer);
    }

    @Test
    void completeSale_ComputesBreakdownAndGeneratesQrData() {
        CompanyConfig config = new CompanyConfig();
        config.setRnc("101010101");
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(productRepository.findById(10L)).thenReturn(Optional.of(taxableProduct));
        when(productRepository.findById(20L)).thenReturn(Optional.of(exentoProduct));
        when(sequenceService.getNextSequence("31")).thenReturn("E310000000101");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(invoiceQrService.buildPayloadUrl(any(), any(), any(), any(), any(), any(), any())).thenReturn("https://qr.test/payload");
        when(invoiceQrService.generateBase64Qr("https://qr.test/payload")).thenReturn("base64qr");
        when(saleRepository.save(saleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleMapper.toDto(any(Sale.class))).thenAnswer(invocation -> {
            Sale saved = invocation.getArgument(0);
            SaleDto dto = new SaleDto();
            dto.setId(saved.getId());
            dto.setNcf(saved.getNcf());
            dto.setMontoGravadoTotal(saved.getMontoGravadoTotal());
            dto.setMontoExento(saved.getMontoExento());
            dto.setTotalItbis(saved.getTotalItbis());
            dto.setMontoTotal(saved.getMontoTotal());
            dto.setQrPayloadUrl(saved.getQrPayloadUrl());
            dto.setQrCodeBase64(saved.getQrCodeBase64());
            dto.setCodigoSeguridad(saved.getCodigoSeguridad());
            return dto;
        });
        when(stockMovementService.createMovement(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleDto result = saleService.completeSale(99L, "cashier");

        assertEquals("E310000000101", result.getNcf());
        assertEquals(new BigDecimal("200.00"), result.getMontoGravadoTotal());
        assertEquals(new BigDecimal("50.00"), result.getMontoExento());
        assertEquals(new BigDecimal("36.00"), result.getTotalItbis());
        assertEquals(new BigDecimal("286.00"), result.getMontoTotal());
        assertEquals("https://qr.test/payload", result.getQrPayloadUrl());
        assertEquals("base64qr", result.getQrCodeBase64());
        assertNotNull(result.getCodigoSeguridad());
        assertFalse(result.getCodigoSeguridad().isBlank());
        assertEquals(TipoIngresos.OPERACIONES, saleCaptor.getValue().getTipoIngresos());
        assertEquals(PaymentMethod.CASH, saleCaptor.getValue().getPaymentMethod());
        verify(stockMovementService, times(2)).createMovement(any());
    }

    @Test
    void completeSale_ComputesBreakdownAcrossAllItbisRates() {
        Product itbis16Product = new Product();
        itbis16Product.setId(11L);
        itbis16Product.setName("Producto ITBIS 16");
        itbis16Product.setStock(50);
        itbis16Product.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_16);
        itbis16Product.setTipoBienServicio(TipoBienServicio.BIEN);

        Product itbis0Product = new Product();
        itbis0Product.setId(12L);
        itbis0Product.setName("Producto ITBIS 0");
        itbis0Product.setStock(50);
        itbis0Product.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_0);
        itbis0Product.setTipoBienServicio(TipoBienServicio.BIEN);

        SaleItem itbis18Item = new SaleItem();
        itbis18Item.setId(31L);
        itbis18Item.setProduct(taxableProduct);
        itbis18Item.setQuantity(1);
        itbis18Item.setUnitPrice(new BigDecimal("100.00"));

        SaleItem itbis16Item = new SaleItem();
        itbis16Item.setId(32L);
        itbis16Item.setProduct(itbis16Product);
        itbis16Item.setQuantity(1);
        itbis16Item.setUnitPrice(new BigDecimal("100.00"));

        SaleItem itbis0Item = new SaleItem();
        itbis0Item.setId(33L);
        itbis0Item.setProduct(itbis0Product);
        itbis0Item.setQuantity(1);
        itbis0Item.setUnitPrice(new BigDecimal("100.00"));

        SaleItem exentoItem = new SaleItem();
        exentoItem.setId(34L);
        exentoItem.setProduct(exentoProduct);
        exentoItem.setQuantity(1);
        exentoItem.setUnitPrice(new BigDecimal("100.00"));

        Sale mixedSale = new Sale();
        mixedSale.setId(102L);
        mixedSale.setSaleDate(LocalDateTime.of(2026, 4, 27, 12, 0));
        mixedSale.setStatus(SaleStatus.PENDING);
        mixedSale.setTipoComprobante("31");
        mixedSale.setItems(List.of(itbis18Item, itbis16Item, itbis0Item, exentoItem));
        for (SaleItem item : mixedSale.getItems()) {
            item.setSale(mixedSale);
        }
        mixedSale.setPaymentMethod(PaymentMethod.CASH);

        CompanyConfig config = new CompanyConfig();
        config.setRnc("101010101");

        when(saleRepository.findById(102L)).thenReturn(Optional.of(mixedSale));
        when(productRepository.findById(10L)).thenReturn(Optional.of(taxableProduct));
        when(productRepository.findById(11L)).thenReturn(Optional.of(itbis16Product));
        when(productRepository.findById(12L)).thenReturn(Optional.of(itbis0Product));
        when(productRepository.findById(20L)).thenReturn(Optional.of(exentoProduct));
        when(sequenceService.getNextSequence("31")).thenReturn("E310000000160");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(invoiceQrService.buildPayloadUrl(any(), any(), any(), any(), any(), any(), any())).thenReturn("https://qr.test/mixed");
        when(invoiceQrService.generateBase64Qr("https://qr.test/mixed")).thenReturn("base64qrMixed");
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleMapper.toDto(any(Sale.class))).thenAnswer(invocation -> {
            Sale saved = invocation.getArgument(0);
            SaleDto dto = new SaleDto();
            dto.setId(saved.getId());
            dto.setMontoGravadoTotal(saved.getMontoGravadoTotal());
            dto.setMontoExento(saved.getMontoExento());
            dto.setTotalItbis(saved.getTotalItbis());
            dto.setMontoTotal(saved.getMontoTotal());
            return dto;
        });
        when(stockMovementService.createMovement(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleDto result = saleService.completeSale(102L, "cashier");

        // 3 líneas gravadas (18%, 16%, 0%) de 100 c/u + 1 línea exenta de 100
        assertEquals(new BigDecimal("300.00"), result.getMontoGravadoTotal());
        assertEquals(new BigDecimal("100.00"), result.getMontoExento());
        assertEquals(new BigDecimal("34.00"), result.getTotalItbis()); // 18 + 16 + 0
        assertEquals(new BigDecimal("434.00"), result.getMontoTotal());
        verify(stockMovementService, times(4)).createMovement(any());
    }

    @Test
    void completeSale_AppliesGlobalDiscountProportionallyAcrossGravadoAndExento() {
        sale.setDiscountAmount(new BigDecimal("25.00"));
        CompanyConfig config = new CompanyConfig();
        config.setRnc("101010101");

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(productRepository.findById(10L)).thenReturn(Optional.of(taxableProduct));
        when(productRepository.findById(20L)).thenReturn(Optional.of(exentoProduct));
        when(sequenceService.getNextSequence("31")).thenReturn("E310000000150");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(invoiceQrService.buildPayloadUrl(any(), any(), any(), any(), any(), any(), any())).thenReturn("https://qr.test/discount");
        when(invoiceQrService.generateBase64Qr("https://qr.test/discount")).thenReturn("base64qrDiscount");
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleMapper.toDto(any(Sale.class))).thenAnswer(invocation -> {
            Sale saved = invocation.getArgument(0);
            SaleDto dto = new SaleDto();
            dto.setId(saved.getId());
            dto.setMontoGravadoTotal(saved.getMontoGravadoTotal());
            dto.setMontoExento(saved.getMontoExento());
            dto.setTotalItbis(saved.getTotalItbis());
            dto.setMontoTotal(saved.getMontoTotal());
            return dto;
        });
        when(stockMovementService.createMovement(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleDto result = saleService.completeSale(99L, "cashier");

        // Base total 250.00 (200 gravado + 50 exento), descuento 25.00 -> factor 0.9
        assertEquals(new BigDecimal("180.00"), result.getMontoGravadoTotal());
        assertEquals(new BigDecimal("45.00"), result.getMontoExento());
        assertEquals(new BigDecimal("32.40"), result.getTotalItbis());
        assertEquals(new BigDecimal("257.40"), result.getMontoTotal());
    }

    @Test
    void completeSale_DoesNotMoveStockForServicio() {
        Product serviceProduct = new Product();
        serviceProduct.setId(30L);
        serviceProduct.setName("Mano de obra");
        serviceProduct.setStock(0);
        serviceProduct.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        serviceProduct.setTipoBienServicio(TipoBienServicio.SERVICIO);

        SaleItem item = new SaleItem();
        item.setId(8L);
        item.setProduct(serviceProduct);
        item.setQuantity(99);
        item.setUnitPrice(new BigDecimal("50.00"));

        Sale saleServicio = new Sale();
        saleServicio.setId(101L);
        saleServicio.setSaleDate(LocalDateTime.of(2026, 4, 27, 11, 0));
        saleServicio.setStatus(SaleStatus.PENDING);
        saleServicio.setTipoComprobante("31");
        saleServicio.setItems(List.of(item));
        item.setSale(saleServicio);
        saleServicio.setPaymentMethod(PaymentMethod.CASH);

        CompanyConfig config = new CompanyConfig();
        config.setRnc("101010101");

        when(saleRepository.findById(101L)).thenReturn(Optional.of(saleServicio));
        when(productRepository.findById(30L)).thenReturn(Optional.of(serviceProduct));
        when(sequenceService.getNextSequence("31")).thenReturn("E310000000102");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(invoiceQrService.buildPayloadUrl(any(), any(), any(), any(), any(), any(), any())).thenReturn("https://qr.test/svc");
        when(invoiceQrService.generateBase64Qr("https://qr.test/svc")).thenReturn("qrSvc");
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleMapper.toDto(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            SaleDto dto = new SaleDto();
            dto.setId(s.getId());
            dto.setNcf(s.getNcf());
            return dto;
        });

        saleService.completeSale(101L, "cashier");

        verify(stockMovementService, never()).createMovement(any());
    }

    @Test
    void completeSale_whenAlreadyCompleted_returnsExistingWithoutSideEffects() {
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNcf("E310000000099");
        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(saleMapper.toDto(sale)).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            SaleDto dto = new SaleDto();
            dto.setId(s.getId());
            dto.setStatus(s.getStatus());
            dto.setNcf(s.getNcf());
            return dto;
        });

        SaleDto result = saleService.completeSale(99L, "cashier");

        assertEquals(SaleStatus.COMPLETED, result.getStatus());
        assertEquals("E310000000099", result.getNcf());
        verify(stockMovementService, never()).createMovement(any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void addPayment_PartialAmount_SetsPartiallyPaidStatus() {
        sale.setMontoTotal(new BigDecimal("286.00"));
        sale.setPaidAmount(BigDecimal.ZERO);

        ArgumentCaptor<SalePayment> paymentCaptor = ArgumentCaptor.forClass(SalePayment.class);
        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.save(paymentCaptor.capture())).thenAnswer(invocation -> {
            SalePayment saved = invocation.getArgument(0);
            saved.setId(501L);
            return saved;
        });
        when(salePaymentRepository.sumAmountBySaleId(99L)).thenReturn(BigDecimal.ZERO, new BigDecimal("100.00"));

        SalePaymentDto result = saleService.addPayment(99L, new BigDecimal("100.00"), PaymentMethod.CASH, "Abono inicial", "cashier");

        assertEquals(SaleStatus.PARTIALLY_PAID, sale.getStatus());
        assertEquals(new BigDecimal("100.00"), sale.getPaidAmount());
        assertEquals(PaymentMethod.CASH, sale.getPaymentMethod());
        assertEquals("Abono inicial", paymentCaptor.getValue().getNotes());
        assertEquals(501L, result.getId());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        verify(saleRepository).save(sale);
        verify(stockMovementService, never()).createMovement(any());
        verify(auditService).record(eq("cashier"), eq("SALE_PAYMENT_ADDED"), eq("Sale"), eq(99L), any());
    }

    @Test
    void addPayment_AmountExceedsPendingBalance_ThrowsIllegalArgumentException() {
        sale.setMontoTotal(new BigDecimal("286.00"));
        sale.setPaidAmount(new BigDecimal("200.00"));

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.sumAmountBySaleId(99L)).thenReturn(new BigDecimal("200.00"));

        assertThrows(IllegalArgumentException.class,
                () -> saleService.addPayment(99L, new BigDecimal("100.00"), PaymentMethod.CASH, null, "cashier"));

        assertEquals(SaleStatus.PENDING, sale.getStatus());
        verify(salePaymentRepository, never()).save(any());
        verify(saleRepository, never()).save(any());
        verify(auditService, never()).record(any(), eq("SALE_PAYMENT_ADDED"), any(), any(), any());
    }

    @Test
    void addPayment_FullAmountWithComprobante_FinalizesSaleAsCompleted() {
        sale.setStatus(SaleStatus.PARTIALLY_PAID);
        sale.setMontoTotal(new BigDecimal("286.00"));
        sale.setPaidAmount(new BigDecimal("186.00"));

        CompanyConfig config = new CompanyConfig();
        config.setRnc("101010101");

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.save(any(SalePayment.class))).thenAnswer(invocation -> {
            SalePayment saved = invocation.getArgument(0);
            saved.setId(777L);
            return saved;
        });
        when(salePaymentRepository.sumAmountBySaleId(99L)).thenReturn(new BigDecimal("186.00"), new BigDecimal("286.00"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(taxableProduct));
        when(productRepository.findById(20L)).thenReturn(Optional.of(exentoProduct));
        when(sequenceService.getNextSequence("31")).thenReturn("E310000000200");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(invoiceQrService.buildPayloadUrl(any(), any(), any(), any(), any(), any(), any())).thenReturn("https://qr.test/payment");
        when(invoiceQrService.generateBase64Qr("https://qr.test/payment")).thenReturn("base64qrPayment");
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementService.createMovement(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SalePaymentDto result = saleService.addPayment(99L, new BigDecimal("100.00"), PaymentMethod.CARD, null, "cashier");

        assertEquals(SaleStatus.COMPLETED, sale.getStatus());
        assertEquals("E310000000200", sale.getNcf());
        assertEquals(new BigDecimal("286.00"), sale.getPaidAmount());
        assertEquals(PaymentMethod.CARD, sale.getPaymentMethod());
        assertEquals(777L, result.getId());
        verify(stockMovementService, times(2)).createMovement(any());
        verify(auditService).record(eq("cashier"), eq("SALE_PAYMENT_ADDED"), eq("Sale"), eq(99L), any());
        verify(auditService).record(eq("cashier"), eq("SALE_COMPLETED"), eq("Sale"), eq(99L), any());
    }

    @Test
    void addPayment_FullAmountWithoutComprobante_StaysPartiallyPaid() {
        sale.setStatus(SaleStatus.PARTIALLY_PAID);
        sale.setTipoComprobante(null);
        sale.setMontoTotal(new BigDecimal("286.00"));
        sale.setPaidAmount(new BigDecimal("186.00"));

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.save(any(SalePayment.class))).thenAnswer(invocation -> {
            SalePayment saved = invocation.getArgument(0);
            saved.setId(778L);
            return saved;
        });
        when(salePaymentRepository.sumAmountBySaleId(99L)).thenReturn(new BigDecimal("186.00"), new BigDecimal("286.00"));

        saleService.addPayment(99L, new BigDecimal("100.00"), PaymentMethod.CASH, null, "cashier");

        assertEquals(SaleStatus.PARTIALLY_PAID, sale.getStatus());
        assertEquals(new BigDecimal("286.00"), sale.getPaidAmount());
        verify(saleRepository).save(sale);
        verify(stockMovementService, never()).createMovement(any());
        verify(sequenceService, never()).getNextSequence(any());
    }

    @Test
    void addPayment_SaleNotFound_ThrowsResourceNotFoundException() {
        when(saleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> saleService.addPayment(404L, new BigDecimal("50.00"), PaymentMethod.CASH, null, "cashier"));

        verify(salePaymentRepository, never()).save(any());
    }

    @Test
    void addPayment_SaleCanceled_ThrowsIllegalStateException() {
        sale.setStatus(SaleStatus.CANCELED);
        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));

        assertThrows(IllegalStateException.class,
                () -> saleService.addPayment(99L, new BigDecimal("50.00"), PaymentMethod.CASH, null, "cashier"));

        verify(salePaymentRepository, never()).save(any());
    }

    @Test
    void getPaymentsForSale_ReturnsPaymentsOrderedByDate() {
        SalePayment first = new SalePayment();
        first.setId(1L);
        first.setSale(sale);
        first.setPaymentDate(LocalDateTime.of(2026, 6, 1, 9, 0));
        first.setAmount(new BigDecimal("100.00"));
        first.setPaymentMethod(PaymentMethod.CASH);
        first.setCreatedBy("cashier");
        first.setCreatedAt(first.getPaymentDate());

        SalePayment second = new SalePayment();
        second.setId(2L);
        second.setSale(sale);
        second.setPaymentDate(LocalDateTime.of(2026, 6, 3, 14, 0));
        second.setAmount(new BigDecimal("186.00"));
        second.setPaymentMethod(PaymentMethod.TRANSFER);
        second.setNotes("Saldo final");
        second.setCreatedBy("cashier");
        second.setCreatedAt(second.getPaymentDate());

        when(saleRepository.existsById(99L)).thenReturn(true);
        when(salePaymentRepository.findBySaleIdOrderByPaymentDateAsc(99L)).thenReturn(List.of(first, second));

        List<SalePaymentDto> result = saleService.getPaymentsForSale(99L);

        assertEquals(2, result.size());
        assertEquals(99L, result.get(0).getSaleId());
        assertEquals(new BigDecimal("100.00"), result.get(0).getAmount());
        assertEquals(PaymentMethod.TRANSFER, result.get(1).getPaymentMethod());
        assertEquals("Saldo final", result.get(1).getNotes());
    }

    @Test
    void getPaymentsForSale_SaleNotFound_ThrowsResourceNotFoundException() {
        when(saleRepository.existsById(404L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> saleService.getPaymentsForSale(404L));

        verify(salePaymentRepository, never()).findBySaleIdOrderByPaymentDateAsc(any());
    }

    @Test
    void voidPayment_BalanceRemains_KeepsSalePartiallyPaidWithRecalculatedAmount() {
        sale.setStatus(SaleStatus.PARTIALLY_PAID);
        sale.setMontoTotal(new BigDecimal("286.00"));
        sale.setPaidAmount(new BigDecimal("286.00"));

        SalePayment payment = new SalePayment();
        payment.setId(501L);
        payment.setSale(sale);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.findById(501L)).thenReturn(Optional.of(payment));
        when(salePaymentRepository.sumAmountBySaleId(99L)).thenReturn(new BigDecimal("186.00"));
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleMapper.toDto(sale)).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            SaleDto dto = new SaleDto();
            dto.setId(s.getId());
            dto.setStatus(s.getStatus());
            dto.setPaidAmount(s.getPaidAmount());
            return dto;
        });

        SaleDto result = saleService.voidPayment(99L, 501L, "cashier");

        assertEquals(SaleStatus.PARTIALLY_PAID, result.getStatus());
        assertEquals(new BigDecimal("186.00"), result.getPaidAmount());
        assertEquals(SaleStatus.PARTIALLY_PAID, sale.getStatus());
        assertEquals(new BigDecimal("186.00"), sale.getPaidAmount());
        verify(salePaymentRepository).delete(payment);
        verify(saleRepository).save(sale);
        verify(auditService).record(eq("cashier"), eq("SALE_PAYMENT_VOIDED"), eq("Sale"), eq(99L), any());
    }

    @Test
    void voidPayment_RemovingOnlyPayment_RevertsSaleToPending() {
        sale.setStatus(SaleStatus.PARTIALLY_PAID);
        sale.setMontoTotal(new BigDecimal("286.00"));
        sale.setPaidAmount(new BigDecimal("100.00"));

        SalePayment payment = new SalePayment();
        payment.setId(502L);
        payment.setSale(sale);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.findById(502L)).thenReturn(Optional.of(payment));
        when(salePaymentRepository.sumAmountBySaleId(99L)).thenReturn(BigDecimal.ZERO);
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleMapper.toDto(sale)).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            SaleDto dto = new SaleDto();
            dto.setId(s.getId());
            dto.setStatus(s.getStatus());
            dto.setPaidAmount(s.getPaidAmount());
            return dto;
        });

        SaleDto result = saleService.voidPayment(99L, 502L, "cashier");

        assertEquals(SaleStatus.PENDING, result.getStatus());
        assertEquals(new BigDecimal("0.00"), result.getPaidAmount());
        verify(salePaymentRepository).delete(payment);
    }

    @Test
    void voidPayment_SaleCompleted_ThrowsIllegalStateException() {
        sale.setStatus(SaleStatus.COMPLETED);
        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));

        assertThrows(IllegalStateException.class, () -> saleService.voidPayment(99L, 501L, "cashier"));

        verify(salePaymentRepository, never()).delete(any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void voidPayment_SaleNotFound_ThrowsResourceNotFoundException() {
        when(saleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> saleService.voidPayment(404L, 501L, "cashier"));

        verify(salePaymentRepository, never()).delete(any());
    }

    @Test
    void voidPayment_PaymentNotFound_ThrowsResourceNotFoundException() {
        sale.setStatus(SaleStatus.PARTIALLY_PAID);
        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> saleService.voidPayment(99L, 999L, "cashier"));

        verify(salePaymentRepository, never()).delete(any());
    }

    @Test
    void voidPayment_PaymentBelongsToDifferentSale_ThrowsIllegalArgumentException() {
        sale.setStatus(SaleStatus.PARTIALLY_PAID);

        Sale otherSale = new Sale();
        otherSale.setId(123L);

        SalePayment payment = new SalePayment();
        payment.setId(501L);
        payment.setSale(otherSale);
        payment.setAmount(new BigDecimal("50.00"));

        when(saleRepository.findById(99L)).thenReturn(Optional.of(sale));
        when(salePaymentRepository.findById(501L)).thenReturn(Optional.of(payment));

        assertThrows(IllegalArgumentException.class, () -> saleService.voidPayment(99L, 501L, "cashier"));

        verify(salePaymentRepository, never()).delete(any());
    }
}
