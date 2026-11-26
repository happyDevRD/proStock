package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.Dgii607RowDto;
import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SaleItemDto;
import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.entity.IndicadorFacturacion;
import com.happydev.prestockbackend.entity.NcfSequence;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.ProductStatus;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SalePayment;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.repository.NcfSequenceRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SalePaymentRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Completar una venta significa que está cobrada: la venta del POS sin conexión se sincroniza con
 * /complete sin registrar abonos, y antes quedaba sin pagos (el 607 la reportaba entera a crédito).
 */
@SpringBootTest
@Transactional
class CompleteSalePaymentIntegrationTest {

    @Autowired private SaleService saleService;
    @Autowired private DgiiReportService dgiiReportService;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SalePaymentRepository salePaymentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private NcfSequenceRepository ncfSequenceRepository;
    @Autowired private EntityManager entityManager;

    private Product servicio;

    @BeforeEach
    void seed() {
        Category category = new Category();
        category.setName("Cat prueba completar venta");
        category = categoryRepository.save(category);
        Supplier supplier = new Supplier();
        supplier.setName("Prov prueba completar venta");
        supplier = supplierRepository.save(supplier);

        Product p = new Product();
        p.setSku("TEST-COMPLETAR-PAGO");
        p.setName("Servicio prueba completar");
        p.setCategory(category);
        p.setSupplier(supplier);
        p.setCostPrice(new BigDecimal("1.00"));
        p.setSellingPrice(new BigDecimal("1000.00"));
        p.setStock(0);
        p.setMinStock(0);
        p.setForSale(true);
        p.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        p.setTipoBienServicio(TipoBienServicio.SERVICIO);
        p.setUnidadMedida(58);
        p.setStatus(ProductStatus.ACTIVE);
        p.setTaxRate(new BigDecimal("18"));
        p.setImages(new ArrayList<>());
        servicio = productRepository.save(p);

        // Secuencia 02 vigente (se revierte con la transacción del test).
        NcfSequence seq = ncfSequenceRepository.findByTipoComprobante("02").orElseGet(() -> {
            NcfSequence fresh = new NcfSequence();
            fresh.setTipoComprobante("02");
            fresh.setPrefijo("B");
            fresh.setValorActual(0L);
            return fresh;
        });
        seq.setValorFinal(99_999_999L);
        seq.setFechaVencimiento(LocalDate.now().plusYears(5));
        ncfSequenceRepository.save(seq);
    }

    @Test
    void ventaSinConexionCompletada_registraElPagoConLaFechaYElMetodoDeLaVenta() {
        LocalDateTime horaVenta = LocalDateTime.now().minusHours(3).truncatedTo(ChronoUnit.SECONDS);
        SaleDto draft = saleService.createSale(draftDto(horaVenta, PaymentMethod.CARD), "sale-offline-test-1");

        SaleDto completed = saleService.completeSale(draft.getId(), "cajera01");

        assertThat(completed.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        List<SalePayment> payments = salePaymentRepository.findBySaleIdOrderByPaymentDateAsc(draft.getId());
        assertThat(payments).hasSize(1);
        SalePayment pago = payments.get(0);
        assertThat(pago.getAmount()).isEqualByComparingTo("1180.00");
        assertThat(pago.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(pago.getPaymentDate()).isEqualTo(horaVenta);
        assertThat(pago.getNotes()).isEqualTo(SaleServiceImpl.OFFLINE_COMPLETION_PAYMENT_NOTE);
        assertThat(pago.getCreatedBy()).isEqualTo("cajera01");

        entityManager.flush();
        entityManager.clear();
        Sale reloaded = saleRepository.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getPaidAmount()).isEqualByComparingTo("1180.00");
        assertThat(salePaymentRepository.sumAmountBySaleId(draft.getId())).isEqualByComparingTo("1180.00");

        // En el 607 ya no sale a crédito.
        Dgii607RowDto row = dgiiReportService.build607(YearMonth.from(horaVenta)).getRows().stream()
                .filter(r -> reloaded.getNcf().equals(r.getNcf())).findFirst().orElseThrow();
        assertThat(row.getTarjeta()).isEqualByComparingTo("1180.00");
        assertThat(row.getVentaCredito()).isEqualByComparingTo("0.00");
    }

    @Test
    void ventaConAbonoParcial_registraSoloLoQueFalta() {
        SaleDto draft = saleService.createSale(draftDto(null, null), null);
        saleService.addPayment(draft.getId(), new BigDecimal("180.00"), PaymentMethod.TRANSFER, null, "cajera01");

        saleService.completeSale(draft.getId(), "cajera01");

        List<SalePayment> payments = salePaymentRepository.findBySaleIdOrderByPaymentDateAsc(draft.getId());
        assertThat(payments).hasSize(2);
        SalePayment resto = payments.stream()
                .filter(p -> SaleServiceImpl.COMPLETION_PAYMENT_NOTE.equals(p.getNotes())).findFirst().orElseThrow();
        assertThat(resto.getAmount()).isEqualByComparingTo("1000.00");
        // El método de la venta es el del último abono (addPayment lo actualiza).
        assertThat(resto.getPaymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(salePaymentRepository.sumAmountBySaleId(draft.getId())).isEqualByComparingTo("1180.00");
    }

    @Test
    void ventaSinMetodoDePago_registraElCobroEnEfectivo() {
        SaleDto draft = saleService.createSale(draftDto(null, null), null);

        saleService.completeSale(draft.getId(), "cajera01");

        List<SalePayment> payments = salePaymentRepository.findBySaleIdOrderByPaymentDateAsc(draft.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("1180.00");
    }

    @Test
    void ventaCobradaPorCompletoConAbonos_noDuplicaElPago() {
        SaleDto draft = saleService.createSale(draftDto(null, PaymentMethod.CASH), null);
        // Cobro completo por /payments: ya la completa y asigna NCF.
        saleService.addPayment(draft.getId(), new BigDecimal("1180.00"), PaymentMethod.CASH, null, "cajera01");

        // Un /complete repetido (reintento de sincronización) no agrega nada.
        SaleDto again = saleService.completeSale(draft.getId(), "cajera01");

        assertThat(again.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        List<SalePayment> payments = salePaymentRepository.findBySaleIdOrderByPaymentDateAsc(draft.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("1180.00");
    }

    private SaleDto draftDto(LocalDateTime saleDate, PaymentMethod method) {
        SaleItemDto item = new SaleItemDto();
        item.setProductId(servicio.getId());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("1000.00"));
        SaleDto dto = new SaleDto();
        dto.setSaleDate(saleDate);
        dto.setTipoComprobante("02");
        dto.setPaymentMethod(method);
        dto.setItems(List.of(item));
        return dto;
    }
}
