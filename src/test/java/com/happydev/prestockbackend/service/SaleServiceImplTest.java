package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.IndicadorFacturacion;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleItem;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.entity.TipoIngresos;
import com.happydev.prestockbackend.mapper.SaleMapper;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SaleItemRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
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

        SaleDto result = saleService.completeSale(99L);

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
    }
}
