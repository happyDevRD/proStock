package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.Dgii606ReportDto;
import com.happydev.prestockbackend.dto.Dgii606RowDto;
import com.happydev.prestockbackend.dto.Dgii607ReportDto;
import com.happydev.prestockbackend.dto.Dgii607RowDto;
import com.happydev.prestockbackend.dto.Dgii608ReportDto;
import com.happydev.prestockbackend.dto.Dgii608RowDto;
import com.happydev.prestockbackend.entity.CreditNote;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Expense;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SalePayment;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.entity.TipoIdentificacion;
import com.happydev.prestockbackend.entity.TipoIngresos;
import com.happydev.prestockbackend.repository.CreditNoteRepository;
import com.happydev.prestockbackend.repository.ExpenseRepository;
import com.happydev.prestockbackend.repository.PurchaseOrderRepository;
import com.happydev.prestockbackend.repository.SalePaymentRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Layout según el "Formato de envío de datos" 607/608 de la DGII (Norma
 * 07-18). Validar la primera salida real con la Herramienta de Envío /
 * pre-validador de la DGII antes de la primera remisión oficial.
 */
@Service
public class DgiiReportServiceImpl implements DgiiReportService {

    private static final DateTimeFormatter FECHA_DGII = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter PERIODO_DGII = DateTimeFormatter.ofPattern("yyyyMM");

    /** Anulación por "corrección de la información" — default razonable para ventas canceladas. */
    private static final String TIPO_ANULACION_DEFAULT = "04";

    private final SaleRepository saleRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ExpenseRepository expenseRepository;
    private final CompanyConfigService companyConfigService;

    public DgiiReportServiceImpl(SaleRepository saleRepository,
                                 SalePaymentRepository salePaymentRepository,
                                 CreditNoteRepository creditNoteRepository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 ExpenseRepository expenseRepository,
                                 CompanyConfigService companyConfigService) {
        this.saleRepository = saleRepository;
        this.salePaymentRepository = salePaymentRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.expenseRepository = expenseRepository;
        this.companyConfigService = companyConfigService;
    }

    @Override
    @Transactional(readOnly = true)
    public Dgii606ReportDto build606(@NonNull YearMonth period) {
        Dgii606ReportDto report = new Dgii606ReportDto();
        report.setRnc(companyRnc());
        report.setPeriodo(period.format(PERIODO_DGII));

        List<PurchaseOrder> orders = purchaseOrderRepository.findWithNcfInRange(
                period.atDay(1), period.atEndOfMonth());
        for (PurchaseOrder order : orders) {
            report.getRows().add(toRow(order));
        }

        // Incluir gastos directos con NCF de proveedor en el 606
        List<Expense> expenses = expenseRepository.findWithNcfInRange(
                period.atDay(1), period.atEndOfMonth());
        for (Expense expense : expenses) {
            report.getRows().add(toRow(expense));
        }

        report.setCantidadRegistros(report.getRows().size());
        report.setTotalMontoFacturado(report.getRows().stream()
                .map(Dgii606RowDto::getTotalMontoFacturado)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalItbis(report.getRows().stream()
                .map(Dgii606RowDto::getItbisFacturado)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public String render606Txt(@NonNull YearMonth period) {
        Dgii606ReportDto report = build606(period);
        StringBuilder txt = new StringBuilder();
        txt.append(String.join("|",
                "606", report.getRnc(), report.getPeriodo(),
                String.valueOf(report.getCantidadRegistros())));
        for (Dgii606RowDto row : report.getRows()) {
            txt.append("\r\n").append(String.join("|",
                    blankIfNull(row.getRncCedula()),
                    blankIfNull(row.getTipoIdentificacion()),
                    blankIfNull(row.getTipoBienesServicios()),
                    blankIfNull(row.getNcf()),
                    "",                                  // NCF o documento modificado
                    blankIfNull(row.getFechaComprobante()),
                    blankIfNull(row.getFechaPago()),
                    money(row.getMontoFacturadoServicios()),
                    money(row.getMontoFacturadoBienes()),
                    money(row.getTotalMontoFacturado()),
                    money(row.getItbisFacturado()),
                    "",                                  // ITBIS retenido
                    "",                                  // ITBIS sujeto a proporcionalidad
                    "",                                  // ITBIS llevado al costo
                    money(row.getItbisFacturado()),      // ITBIS por adelantar
                    "",                                  // ITBIS percibido en compras
                    "",                                  // tipo de retención en ISR
                    "",                                  // monto retención renta
                    "",                                  // ISR percibido en compras
                    "",                                  // impuesto selectivo al consumo
                    "",                                  // otros impuestos/tasas
                    "",                                  // monto propina legal
                    blankIfNull(row.getFormaPago())));
        }
        return txt.toString();
    }

    private Dgii606RowDto toRow(PurchaseOrder order) {
        Dgii606RowDto row = new Dgii606RowDto();
        if (order.getSupplier() != null) {
            String doc = trimToNull(order.getSupplier().getRncCedula());
            row.setRncCedula(doc != null ? doc.replaceAll("[^0-9A-Za-z]", "") : "");
            row.setTipoIdentificacion(doc != null
                    ? tipoIdentificacionCode(order.getSupplier().getTipoIdentificacion())
                    : "");
        } else {
            row.setRncCedula("");
            row.setTipoIdentificacion("");
        }
        row.setTipoBienesServicios(order.getTipoBienesServicios() != null
                ? order.getTipoBienesServicios() : "09");
        row.setNcf(order.getNcfProveedor());
        row.setFechaComprobante(order.getOrderDate().format(FECHA_DGII));
        row.setFechaPago(order.getFechaPago() != null
                ? order.getFechaPago().format(FECHA_DGII) : "");

        BigDecimal servicios = BigDecimal.ZERO;
        BigDecimal bienes = BigDecimal.ZERO;
        if (order.getItems() != null) {
            for (PurchaseOrderItem item : order.getItems()) {
                BigDecimal lineTotal = BigDecimal.valueOf(item.getUnitPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity()));
                boolean isServicio = item.getProduct() != null
                        && item.getProduct().getTipoBienServicio() == TipoBienServicio.SERVICIO;
                if (isServicio) {
                    servicios = servicios.add(lineTotal);
                } else {
                    bienes = bienes.add(lineTotal);
                }
            }
        }
        row.setMontoFacturadoServicios(servicios.setScale(2, RoundingMode.HALF_UP));
        row.setMontoFacturadoBienes(bienes.setScale(2, RoundingMode.HALF_UP));
        row.setTotalMontoFacturado(servicios.add(bienes).setScale(2, RoundingMode.HALF_UP));
        row.setItbisFacturado(nz(order.getTotalItbis()));
        row.setFormaPago(formaPago606(order));
        return row;
    }

    private Dgii606RowDto toRow(Expense expense) {
        Dgii606RowDto row = new Dgii606RowDto();
        // Priorizar RNC/tipo del suplidor enlazado; si no, usar los del gasto directamente
        if (expense.getSupplier() != null) {
            String doc = trimToNull(expense.getSupplier().getRncCedula());
            row.setRncCedula(doc != null ? doc.replaceAll("[^0-9A-Za-z]", "") : "");
            row.setTipoIdentificacion(doc != null
                    ? tipoIdentificacionCode(expense.getSupplier().getTipoIdentificacion())
                    : "");
        } else {
            String doc = trimToNull(expense.getRncCedula());
            row.setRncCedula(doc != null ? doc.replaceAll("[^0-9A-Za-z]", "") : "");
            row.setTipoIdentificacion(expense.getTipoIdentificacion() != null
                    ? tipoIdentificacionCode(expense.getTipoIdentificacion())
                    : "");
        }
        row.setTipoBienesServicios(
                expense.getTipoBienesServicios() != null ? expense.getTipoBienesServicios() : "09");
        row.setNcf(expense.getNcfProveedor());
        row.setFechaComprobante(expense.getExpenseDate().format(FECHA_DGII));
        row.setFechaPago(expense.getPaymentDate() != null
                ? expense.getPaymentDate().format(FECHA_DGII) : "");

        // Los gastos directos son principalmente servicios (09); el monto va a bienes o servicios
        // según el tipo declarado en el gasto
        BigDecimal total = nz(expense.getAmount());
        boolean isServicio = !"01".equals(expense.getTipoBienesServicios())
                && !"02".equals(expense.getTipoBienesServicios())
                && !"03".equals(expense.getTipoBienesServicios())
                && !"04".equals(expense.getTipoBienesServicios())
                && !"05".equals(expense.getTipoBienesServicios());
        row.setMontoFacturadoServicios(isServicio ? total : BigDecimal.ZERO);
        row.setMontoFacturadoBienes(isServicio ? BigDecimal.ZERO : total);
        row.setTotalMontoFacturado(total.setScale(2, RoundingMode.HALF_UP));
        row.setItbisFacturado(nz(expense.getItbis()).setScale(2, RoundingMode.HALF_UP));
        row.setFormaPago(formaPago606Expense(expense));
        return row;
    }

    private String formaPago606Expense(Expense expense) {
        if (expense.getPaymentDate() == null) {
            return "04"; // crédito
        }
        PaymentMethod method = expense.getPaymentMethod();
        if (method == null) return "01";
        return switch (method) {
            case CASH -> "01";
            case CHECK, TRANSFER -> "02";
            case CARD -> "03";
            case OTHER -> "07";
        };
    }

    private String formaPago606(PurchaseOrder order) {
        if (order.getFechaPago() == null) {
            return "04"; // compra a crédito
        }
        PaymentMethod method = order.getPaymentMethod();
        if (method == null) {
            return "01";
        }
        return switch (method) {
            case CASH -> "01";
            case CHECK, TRANSFER -> "02";
            case CARD -> "03";
            case OTHER -> "07";
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Dgii607ReportDto build607(@NonNull YearMonth period) {
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.atEndOfMonth().atTime(23, 59, 59);

        List<Sale> sales = saleRepository.findWithNcfByStatusInRange(SaleStatus.COMPLETED, start, end);
        List<CreditNote> creditNotes = creditNoteRepository.findInRangeWithSale(start, end);

        Map<Long, List<SalePayment>> paymentsBySale = sales.isEmpty()
                ? Map.of()
                : salePaymentRepository.findBySaleIdIn(sales.stream().map(Sale::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(p -> p.getSale().getId()));

        Dgii607ReportDto report = new Dgii607ReportDto();
        report.setRnc(companyRnc());
        report.setPeriodo(period.format(PERIODO_DGII));

        for (Sale sale : sales) {
            report.getRows().add(toRow(sale, paymentsBySale.getOrDefault(sale.getId(), List.of())));
        }
        for (CreditNote creditNote : creditNotes) {
            report.getRows().add(toRow(creditNote));
        }

        report.setCantidadRegistros(report.getRows().size());
        report.setTotalMontoFacturado(report.getRows().stream()
                .map(Dgii607RowDto::getMontoFacturado)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalItbis(report.getRows().stream()
                .map(Dgii607RowDto::getItbisFacturado)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public Dgii608ReportDto build608(@NonNull YearMonth period) {
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.atEndOfMonth().atTime(23, 59, 59);

        Dgii608ReportDto report = new Dgii608ReportDto();
        report.setRnc(companyRnc());
        report.setPeriodo(period.format(PERIODO_DGII));

        for (Sale sale : saleRepository.findWithNcfByStatusInRange(SaleStatus.CANCELED, start, end)) {
            Dgii608RowDto row = new Dgii608RowDto();
            row.setNcf(sale.getNcf());
            row.setFechaComprobante(sale.getSaleDate().format(FECHA_DGII));
            row.setTipoAnulacion(TIPO_ANULACION_DEFAULT);
            report.getRows().add(row);
        }

        report.setCantidadRegistros(report.getRows().size());
        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public String render607Txt(@NonNull YearMonth period) {
        Dgii607ReportDto report = build607(period);
        StringBuilder txt = new StringBuilder();
        txt.append(String.join("|",
                "607", report.getRnc(), report.getPeriodo(),
                String.valueOf(report.getCantidadRegistros())));
        for (Dgii607RowDto row : report.getRows()) {
            txt.append("\r\n").append(String.join("|",
                    blankIfNull(row.getRncCedula()),
                    blankIfNull(row.getTipoIdentificacion()),
                    blankIfNull(row.getNcf()),
                    blankIfNull(row.getNcfModificado()),
                    blankIfNull(row.getTipoIngreso()),
                    blankIfNull(row.getFechaComprobante()),
                    "",                                  // fecha de retención
                    money(row.getMontoFacturado()),
                    money(row.getItbisFacturado()),
                    "",                                  // ITBIS retenido por terceros
                    "",                                  // ITBIS percibido
                    "",                                  // retención renta por terceros
                    "",                                  // ISR percibido
                    "",                                  // impuesto selectivo al consumo
                    "",                                  // otros impuestos/tasas
                    "",                                  // monto propina legal
                    money(row.getEfectivo()),
                    money(row.getChequeTransferencia()),
                    money(row.getTarjeta()),
                    money(row.getVentaCredito()),
                    "",                                  // bonos o certificados de regalo
                    "",                                  // permuta
                    money(row.getOtrasFormas())));
        }
        return txt.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public String render608Txt(@NonNull YearMonth period) {
        Dgii608ReportDto report = build608(period);
        StringBuilder txt = new StringBuilder();
        txt.append(String.join("|",
                "608", report.getRnc(), report.getPeriodo(),
                String.valueOf(report.getCantidadRegistros())));
        for (Dgii608RowDto row : report.getRows()) {
            txt.append("\r\n").append(String.join("|",
                    blankIfNull(row.getNcf()),
                    blankIfNull(row.getFechaComprobante()),
                    blankIfNull(row.getTipoAnulacion())));
        }
        return txt.toString();
    }

    private Dgii607RowDto toRow(Sale sale, List<SalePayment> payments) {
        Dgii607RowDto row = baseRow(sale.getCustomer());
        row.setNcf(sale.getNcf());
        row.setNcfModificado("");
        row.setTipoIngreso(sale.getTipoIngresos() != null
                ? sale.getTipoIngresos().getCode()
                : TipoIngresos.OPERACIONES.getCode());
        row.setFechaComprobante(sale.getSaleDate().format(FECHA_DGII));
        row.setMontoFacturado(nz(sale.getMontoGravadoTotal()).add(nz(sale.getMontoExento())));
        row.setItbisFacturado(nz(sale.getTotalItbis()));

        Map<PaymentMethod, BigDecimal> byMethod = new EnumMap<>(PaymentMethod.class);
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (SalePayment payment : payments) {
            PaymentMethod method = payment.getPaymentMethod() != null
                    ? payment.getPaymentMethod()
                    : PaymentMethod.OTHER;
            byMethod.merge(method, nz(payment.getAmount()), BigDecimal::add);
            totalPaid = totalPaid.add(nz(payment.getAmount()));
        }
        row.setEfectivo(byMethod.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO));
        row.setChequeTransferencia(byMethod.getOrDefault(PaymentMethod.CHECK, BigDecimal.ZERO)
                .add(byMethod.getOrDefault(PaymentMethod.TRANSFER, BigDecimal.ZERO)));
        row.setTarjeta(byMethod.getOrDefault(PaymentMethod.CARD, BigDecimal.ZERO));
        row.setOtrasFormas(byMethod.getOrDefault(PaymentMethod.OTHER, BigDecimal.ZERO));
        BigDecimal credito = nz(sale.getMontoTotal()).subtract(totalPaid);
        row.setVentaCredito(credito.compareTo(BigDecimal.ZERO) > 0 ? credito : BigDecimal.ZERO);
        return row;
    }

    private Dgii607RowDto toRow(CreditNote creditNote) {
        Customer customer = creditNote.getSale() != null ? creditNote.getSale().getCustomer() : null;
        Dgii607RowDto row = baseRow(customer);
        row.setNcf(creditNote.getNcf());
        row.setNcfModificado(blankIfNull(creditNote.getNcfModificado()));
        TipoIngresos tipoIngresos = creditNote.getSale() != null
                ? creditNote.getSale().getTipoIngresos()
                : null;
        row.setTipoIngreso(tipoIngresos != null
                ? tipoIngresos.getCode()
                : TipoIngresos.OPERACIONES.getCode());
        row.setFechaComprobante(creditNote.getCreatedAt().format(FECHA_DGII));
        row.setMontoFacturado(nz(creditNote.getMontoGravadoTotal()).add(nz(creditNote.getMontoExento())));
        row.setItbisFacturado(nz(creditNote.getTotalItbis()));
        row.setEfectivo(BigDecimal.ZERO);
        row.setChequeTransferencia(BigDecimal.ZERO);
        row.setTarjeta(BigDecimal.ZERO);
        row.setVentaCredito(BigDecimal.ZERO);
        row.setOtrasFormas(BigDecimal.ZERO);
        return row;
    }

    private Dgii607RowDto baseRow(Customer customer) {
        Dgii607RowDto row = new Dgii607RowDto();
        String doc = customer != null ? trimToNull(customer.getRncCedula()) : null;
        row.setRncCedula(doc != null ? doc.replaceAll("[^0-9A-Za-z]", "") : "");
        row.setTipoIdentificacion(doc != null
                ? tipoIdentificacionCode(customer.getTipoIdentificacion())
                : "");
        return row;
    }

    private String tipoIdentificacionCode(TipoIdentificacion tipo) {
        if (tipo == null) {
            return "";
        }
        return switch (tipo) {
            case RNC -> "1";
            case CEDULA -> "2";
            case PASAPORTE_EXTRANJERO -> "3";
        };
    }

    private String companyRnc() {
        return companyConfigService.findCompanyConfig()
                .map(config -> trimToNull(config.getRnc()))
                .map(rnc -> rnc.replaceAll("[^0-9]", ""))
                .orElse("");
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String blankIfNull(String value) {
        return value != null ? value : "";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
