package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.Dgii607ReportDto;
import com.happydev.prestockbackend.dto.Dgii607RowDto;
import com.happydev.prestockbackend.dto.Dgii608ReportDto;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.entity.CreditNote;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SalePayment;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.TipoIdentificacion;
import com.happydev.prestockbackend.dto.Dgii606ReportDto;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.repository.CreditNoteRepository;
import com.happydev.prestockbackend.repository.PurchaseOrderRepository;
import com.happydev.prestockbackend.repository.SalePaymentRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DgiiReportServiceImplTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SalePaymentRepository salePaymentRepository;

    @Mock
    private CreditNoteRepository creditNoteRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private CompanyConfigService companyConfigService;

    @InjectMocks
    private DgiiReportServiceImpl service;

    private final YearMonth period = YearMonth.of(2026, 6);
    private Sale sale;

    @BeforeEach
    void setUp() {
        CompanyConfig config = new CompanyConfig();
        config.setRnc("1-31-12345-6");
        lenient().when(companyConfigService.findCompanyConfig()).thenReturn(Optional.of(config));

        Customer customer = new Customer();
        customer.setRncCedula("101-23456-7");
        customer.setTipoIdentificacion(TipoIdentificacion.RNC);

        sale = new Sale();
        sale.setId(10L);
        sale.setCustomer(customer);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNcf("B0100000001");
        sale.setSaleDate(LocalDateTime.of(2026, 6, 5, 14, 30));
        sale.setMontoGravadoTotal(new BigDecimal("1000.00"));
        sale.setMontoExento(new BigDecimal("200.00"));
        sale.setTotalItbis(new BigDecimal("180.00"));
        sale.setMontoTotal(new BigDecimal("1380.00"));
    }

    private SalePayment payment(PaymentMethod method, String amount) {
        SalePayment p = new SalePayment();
        p.setSale(sale);
        p.setPaymentMethod(method);
        p.setAmount(new BigDecimal(amount));
        return p;
    }

    @Test
    void build607IncludesSalesAndCreditNotesWithPaymentBreakdown() {
        CreditNote creditNote = new CreditNote();
        creditNote.setNcf("B0400000001");
        creditNote.setNcfModificado("B0100000001");
        creditNote.setSale(sale);
        creditNote.setCreatedAt(LocalDateTime.of(2026, 6, 20, 9, 0));
        creditNote.setMontoGravadoTotal(new BigDecimal("100.00"));
        creditNote.setMontoExento(BigDecimal.ZERO);
        creditNote.setTotalItbis(new BigDecimal("18.00"));
        creditNote.setMontoTotal(new BigDecimal("118.00"));

        when(saleRepository.findWithNcfByStatusInRange(eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(sale));
        when(creditNoteRepository.findInRangeWithSale(any(), any()))
                .thenReturn(List.of(creditNote));
        when(salePaymentRepository.findBySaleIdIn(anyList()))
                .thenReturn(List.of(
                        payment(PaymentMethod.CASH, "1000.00"),
                        payment(PaymentMethod.CARD, "300.00")));

        Dgii607ReportDto report = service.build607(period);

        assertEquals("131123456", report.getRnc());
        assertEquals("202606", report.getPeriodo());
        assertEquals(2, report.getCantidadRegistros());
        assertEquals(new BigDecimal("1300.00"), report.getTotalMontoFacturado());
        assertEquals(new BigDecimal("198.00"), report.getTotalItbis());

        Dgii607RowDto saleRow = report.getRows().get(0);
        assertEquals("101234567", saleRow.getRncCedula());
        assertEquals("1", saleRow.getTipoIdentificacion());
        assertEquals("B0100000001", saleRow.getNcf());
        assertEquals("", saleRow.getNcfModificado());
        assertEquals("01", saleRow.getTipoIngreso());
        assertEquals("20260605", saleRow.getFechaComprobante());
        assertEquals(new BigDecimal("1200.00"), saleRow.getMontoFacturado());
        assertEquals(new BigDecimal("1000.00"), saleRow.getEfectivo());
        assertEquals(new BigDecimal("300.00"), saleRow.getTarjeta());
        assertEquals(new BigDecimal("80.00"), saleRow.getVentaCredito());

        Dgii607RowDto cnRow = report.getRows().get(1);
        assertEquals("B0400000001", cnRow.getNcf());
        assertEquals("B0100000001", cnRow.getNcfModificado());
        assertEquals("20260620", cnRow.getFechaComprobante());
        assertEquals(new BigDecimal("100.00"), cnRow.getMontoFacturado());
        assertEquals(BigDecimal.ZERO, cnRow.getVentaCredito());
    }

    @Test
    void render607TxtUsesDgiiPipeLayout() {
        when(saleRepository.findWithNcfByStatusInRange(eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(sale));
        when(creditNoteRepository.findInRangeWithSale(any(), any())).thenReturn(List.of());
        when(salePaymentRepository.findBySaleIdIn(anyList()))
                .thenReturn(List.of(payment(PaymentMethod.CASH, "1380.00")));

        String txt = service.render607Txt(period);
        String[] lines = txt.split("\r\n");

        assertEquals("607|131123456|202606|1", lines[0]);
        assertEquals(
                "101234567|1|B0100000001||01|20260605||1200.00|180.00||||||||1380.00|0.00|0.00|0.00|||0.00",
                lines[1]);
    }

    @Test
    void build608ListsCanceledSalesWithNcf() {
        sale.setStatus(SaleStatus.CANCELED);
        when(saleRepository.findWithNcfByStatusInRange(eq(SaleStatus.CANCELED), any(), any()))
                .thenReturn(List.of(sale));

        Dgii608ReportDto report = service.build608(period);

        assertEquals(1, report.getCantidadRegistros());
        assertEquals("B0100000001", report.getRows().get(0).getNcf());
        assertEquals("20260605", report.getRows().get(0).getFechaComprobante());
        assertEquals("04", report.getRows().get(0).getTipoAnulacion());

        String txt = service.render608Txt(period);
        String[] lines = txt.split("\r\n");
        assertEquals("608|131123456|202606|1", lines[0]);
        assertEquals("B0100000001|20260605|04", lines[1]);
    }

    @Test
    void build606SplitsGoodsAndServicesAndRendersTxt() {
        Supplier supplier = new Supplier();
        supplier.setRncCedula("401-50563-9");
        supplier.setTipoIdentificacion(TipoIdentificacion.RNC);

        Product bien = new Product();
        bien.setTipoBienServicio(TipoBienServicio.BIEN);
        Product servicio = new Product();
        servicio.setTipoBienServicio(TipoBienServicio.SERVICIO);

        PurchaseOrderItem itemBien = new PurchaseOrderItem();
        itemBien.setProduct(bien);
        itemBien.setQuantity(10);
        itemBien.setUnitPrice(50.0);
        PurchaseOrderItem itemServicio = new PurchaseOrderItem();
        itemServicio.setProduct(servicio);
        itemServicio.setQuantity(1);
        itemServicio.setUnitPrice(200.0);

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setNcfProveedor("B0100009999");
        order.setTipoBienesServicios("09");
        order.setOrderDate(java.time.LocalDate.of(2026, 6, 10));
        order.setFechaPago(java.time.LocalDate.of(2026, 6, 15));
        order.setPaymentMethod(PaymentMethod.TRANSFER);
        order.setTotalItbis(new BigDecimal("126.00"));
        order.setItems(new java.util.ArrayList<>(List.of(itemBien, itemServicio)));

        when(purchaseOrderRepository.findWithNcfInRange(any(), any()))
                .thenReturn(List.of(order));

        Dgii606ReportDto report = service.build606(period);

        assertEquals(1, report.getCantidadRegistros());
        assertEquals(new BigDecimal("700.00"), report.getTotalMontoFacturado());
        assertEquals(new BigDecimal("126.00"), report.getTotalItbis());
        assertEquals(new BigDecimal("200.00"), report.getRows().get(0).getMontoFacturadoServicios());
        assertEquals(new BigDecimal("500.00"), report.getRows().get(0).getMontoFacturadoBienes());
        assertEquals("02", report.getRows().get(0).getFormaPago());

        String txt = service.render606Txt(period);
        String[] lines = txt.split("\r\n");
        assertEquals("606|131123456|202606|1", lines[0]);
        assertEquals(
                "401505639|1|09|B0100009999||20260610|20260615|200.00|500.00|700.00|126.00||||126.00||||||||02",
                lines[1]);
    }

    @Test
    void build606MarksUnpaidPurchasesAsCredit() {
        PurchaseOrder order = new PurchaseOrder();
        order.setNcfProveedor("B0100008888");
        order.setOrderDate(java.time.LocalDate.of(2026, 6, 3));
        order.setTotalItbis(BigDecimal.ZERO);

        when(purchaseOrderRepository.findWithNcfInRange(any(), any()))
                .thenReturn(List.of(order));

        Dgii606ReportDto report = service.build606(period);

        assertEquals("04", report.getRows().get(0).getFormaPago());
        assertEquals("", report.getRows().get(0).getFechaPago());
        assertEquals("", report.getRows().get(0).getRncCedula());
    }

    @Test
    void build607HandlesConsumerWithoutDocument() {
        sale.setCustomer(null);
        when(saleRepository.findWithNcfByStatusInRange(eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(sale));
        when(creditNoteRepository.findInRangeWithSale(any(), any())).thenReturn(List.of());
        when(salePaymentRepository.findBySaleIdIn(anyList())).thenReturn(List.of());

        Dgii607ReportDto report = service.build607(period);

        Dgii607RowDto row = report.getRows().get(0);
        assertEquals("", row.getRncCedula());
        assertEquals("", row.getTipoIdentificacion());
        assertEquals(new BigDecimal("1380.00"), row.getVentaCredito());
    }
}
