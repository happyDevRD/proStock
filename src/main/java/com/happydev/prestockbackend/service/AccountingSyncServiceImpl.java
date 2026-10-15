package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AccountSyncPreviewDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountingSyncServiceImpl implements AccountingSyncService {

    private static final String ENTITY_SALE = "SALE";
    private static final String ENTITY_PURCHASE = "PURCHASE_ORDER";
    private static final String ENTITY_CREDIT_NOTE = "CREDIT_NOTE";
    private static final String ACCOUNT_GOODS_SALES = "4.1.1";
    private static final String ACCOUNT_SERVICE_SALES = "4.1.2";

    private final SaleRepository saleRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AccSyncLogRepository syncLogRepository;
    private final AccAccountRepository accountRepository;
    private final AccJournalEntryRepository journalEntryRepository;
    private final CreditNoteRepository creditNoteRepository;

    public AccountingSyncServiceImpl(SaleRepository saleRepository,
                                     PurchaseOrderRepository purchaseOrderRepository,
                                     AccSyncLogRepository syncLogRepository,
                                     AccAccountRepository accountRepository,
                                     AccJournalEntryRepository journalEntryRepository,
                                     CreditNoteRepository creditNoteRepository) {
        this.saleRepository = saleRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.syncLogRepository = syncLogRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.creditNoteRepository = creditNoteRepository;
    }

    // -------------------------------------------------------
    // Preview
    // -------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AccountSyncPreviewDto preview() {
        List<Sale> completedSales = saleRepository.findAll().stream()
                .filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                .filter(s -> !syncLogRepository.existsByEntityTypeAndEntityId(ENTITY_SALE, s.getId()))
                .toList();

        List<PurchaseOrder> receivedOrders = purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getStatus() == PurchaseOrderStatus.RECEIVED
                           || po.getStatus() == PurchaseOrderStatus.PARTIALLY_PAID
                           || po.getStatus() == PurchaseOrderStatus.PAID)
                .filter(po -> !syncLogRepository.existsByEntityTypeAndEntityId(ENTITY_PURCHASE, po.getId()))
                .toList();

        BigDecimal totalSalesAmount = completedSales.stream()
                .map(s -> originalTotals(s).total())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPurchasesAmount = receivedOrders.stream()
                .map(po -> BigDecimal.valueOf(
                        po.getItems().stream()
                                .mapToDouble(i -> i.getUnitPrice() * i.getQuantity())
                                .sum()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CreditNote> pendingCreditNotes = pendingCreditNotes();
        BigDecimal totalCreditNotesAmount = pendingCreditNotes.stream()
                .map(cn -> safeVal(cn.getMontoTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AccountSyncPreviewDto(
                completedSales.size(),
                receivedOrders.size(),
                totalSalesAmount,
                totalPurchasesAmount,
                pendingCreditNotes.size(),
                totalCreditNotesAmount
        );
    }

    // -------------------------------------------------------
    // Sync
    // -------------------------------------------------------

    @Override
    @Transactional
    public int sync(String username) {
        int count = 0;

        // --- Sales ---
        List<Sale> completedSales = saleRepository.findAll().stream()
                .filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                .filter(s -> !syncLogRepository.existsByEntityTypeAndEntityId(ENTITY_SALE, s.getId()))
                .toList();

        for (Sale sale : completedSales) {
            AccJournalEntry entry = buildSaleEntry(sale, username);
            AccJournalEntry saved = journalEntryRepository.save(entry);
            recordSync(ENTITY_SALE, sale.getId(), saved.getId());
            count++;
        }

        // --- Credit notes (después de las ventas: revierten parte de un asiento ya registrado) ---
        for (CreditNote creditNote : pendingCreditNotes()) {
            AccJournalEntry saved = journalEntryRepository.save(buildCreditNoteEntry(creditNote, username));
            recordSync(ENTITY_CREDIT_NOTE, creditNote.getId(), saved.getId());
            count++;
        }

        // --- Purchase Orders ---
        List<PurchaseOrder> receivedOrders = purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getStatus() == PurchaseOrderStatus.RECEIVED
                           || po.getStatus() == PurchaseOrderStatus.PARTIALLY_PAID
                           || po.getStatus() == PurchaseOrderStatus.PAID)
                .filter(po -> !syncLogRepository.existsByEntityTypeAndEntityId(ENTITY_PURCHASE, po.getId()))
                .toList();

        for (PurchaseOrder po : receivedOrders) {
            AccJournalEntry entry = buildPurchaseEntry(po, username);
            AccJournalEntry saved = journalEntryRepository.save(entry);
            recordSync(ENTITY_PURCHASE, po.getId(), saved.getId());
            count++;
        }

        return count;
    }

    // -------------------------------------------------------
    // Builders
    // -------------------------------------------------------

    private AccJournalEntry buildSaleEntry(Sale sale, String username) {
        boolean isCash = sale.getPaymentMethod() == PaymentMethod.CASH;
        String cashBankCode = isCash ? "1.1.1.01" : "1.1.1.02";

        AccAccount cashBankAccount = requireAccount(cashBankCode);
        AccAccount itbisAccount    = requireAccount("2.1.2");

        String description = "Venta #" + sale.getId() + " - "
                + (sale.getNcf() != null ? sale.getNcf() : "Sin NCF");
        if (sale.getServiceOrder() != null && sale.getServiceOrder().getOrderNumber() != null) {
            description += " (OS " + sale.getServiceOrder().getOrderNumber() + ")";
        }
        String reference = sale.getNcf() != null ? sale.getNcf() : "VTA-" + sale.getId();

        AccJournalEntry entry = createBaseEntry(
                description, reference, AccEntryType.AUTO_SALE, username,
                sale.getSaleDate() != null ? sale.getSaleDate().toLocalDate() : LocalDate.now());

        // Totales originales: las notas de crédito reducen la venta, pero tienen su propio asiento.
        SaleTotals totals = originalTotals(sale);
        BigDecimal[] split = splitGoodsAndServices(totals.net(), sale.getItems().stream()
                .map(i -> new Line(i.getProduct(), i.getUnitPrice(), i.getQuantity()))
                .toList());

        // Dr cash/bank = montoTotal
        addLine(entry, cashBankAccount.getId(), totals.total(), BigDecimal.ZERO, "Cobro venta");
        // Cr ventas (neto de ITBIS), separando bienes y servicios
        if (split[0].signum() > 0) {
            addLine(entry, requireAccount(ACCOUNT_GOODS_SALES).getId(), BigDecimal.ZERO, split[0], "Venta de bienes");
        }
        if (split[1].signum() > 0) {
            addLine(entry, requireAccount(ACCOUNT_SERVICE_SALES).getId(), BigDecimal.ZERO, split[1], "Venta de servicios");
        }
        // Cr ITBIS por pagar (only if > 0)
        if (totals.itbis().compareTo(BigDecimal.ZERO) > 0) {
            addLine(entry, itbisAccount.getId(), BigDecimal.ZERO, totals.itbis(), "ITBIS repercutido");
        }

        return entry;
    }

    /** Reverso de una nota de crédito: Dr ventas (bienes/servicios) + Dr ITBIS, Cr caja/banco (devolución). */
    private AccJournalEntry buildCreditNoteEntry(CreditNote creditNote, String username) {
        Sale sale = creditNote.getSale();
        boolean isCash = sale.getPaymentMethod() == PaymentMethod.CASH;
        AccAccount cashBankAccount = requireAccount(isCash ? "1.1.1.01" : "1.1.1.02");
        AccAccount itbisAccount = requireAccount("2.1.2");

        String ncf = creditNote.getNcf() != null ? creditNote.getNcf() : "NC-" + creditNote.getId();
        String description = "Nota de crédito " + ncf + " sobre " + (sale.getNcf() != null ? sale.getNcf() : "venta #" + sale.getId());
        if (sale.getServiceOrder() != null && sale.getServiceOrder().getOrderNumber() != null) {
            description += " (OS " + sale.getServiceOrder().getOrderNumber() + ")";
        }
        AccJournalEntry entry = createBaseEntry(
                description, ncf, AccEntryType.AUTO_CREDIT_NOTE, username,
                creditNote.getCreatedAt() != null ? creditNote.getCreatedAt().toLocalDate() : LocalDate.now());

        BigDecimal net = safeVal(creditNote.getMontoGravadoTotal()).add(safeVal(creditNote.getMontoExento()));
        BigDecimal itbis = safeVal(creditNote.getTotalItbis());
        BigDecimal[] split = splitGoodsAndServices(net, creditNote.getItems().stream()
                .map(i -> new Line(i.getProduct(), i.getUnitPrice(), i.getQuantity()))
                .toList());

        if (split[0].signum() > 0) {
            addLine(entry, requireAccount(ACCOUNT_GOODS_SALES).getId(), split[0], BigDecimal.ZERO, "Devolución de bienes");
        }
        if (split[1].signum() > 0) {
            addLine(entry, requireAccount(ACCOUNT_SERVICE_SALES).getId(), split[1], BigDecimal.ZERO, "Devolución de servicios");
        }
        if (itbis.signum() > 0) {
            addLine(entry, itbisAccount.getId(), itbis, BigDecimal.ZERO, "Reverso ITBIS");
        }
        addLine(entry, cashBankAccount.getId(), BigDecimal.ZERO, safeVal(creditNote.getMontoTotal()), "Devolución al cliente");
        return entry;
    }

    record Line(Product product, BigDecimal unitPrice, int quantity) {}

    record SaleTotals(BigDecimal net, BigDecimal itbis, BigDecimal total) {}

    /** Totales de la venta tal como se emitió: los actuales más lo revertido por notas de crédito. */
    private SaleTotals originalTotals(Sale sale) {
        BigDecimal net = safeVal(sale.getMontoGravadoTotal()).add(safeVal(sale.getMontoExento()));
        BigDecimal itbis = safeVal(sale.getTotalItbis());
        BigDecimal total = safeVal(sale.getMontoTotal());
        for (CreditNote cn : creditNoteRepository.findBySale_IdOrderByCreatedAtDesc(sale.getId())) {
            net = net.add(safeVal(cn.getMontoGravadoTotal())).add(safeVal(cn.getMontoExento()));
            itbis = itbis.add(safeVal(cn.getTotalItbis()));
            total = total.add(safeVal(cn.getMontoTotal()));
        }
        return new SaleTotals(net, itbis, total);
    }

    /**
     * Reparte el neto (sin ITBIS, ya con descuentos) entre bienes [0] y servicios [1] en proporción
     * a la base de cada línea. Los servicios absorben el redondeo para que la suma cuadre exacta.
     */
    static BigDecimal[] splitGoodsAndServices(BigDecimal net, List<Line> lines) {
        BigDecimal goodsBase = BigDecimal.ZERO;
        BigDecimal servicesBase = BigDecimal.ZERO;
        for (Line line : lines) {
            BigDecimal base = safeStatic(line.unitPrice()).multiply(BigDecimal.valueOf(line.quantity()));
            if (line.product() != null && line.product().getTipoBienServicio() == TipoBienServicio.SERVICIO) {
                servicesBase = servicesBase.add(base);
            } else {
                goodsBase = goodsBase.add(base);
            }
        }
        BigDecimal totalBase = goodsBase.add(servicesBase);
        if (totalBase.signum() == 0 || servicesBase.signum() == 0) {
            return new BigDecimal[]{net, BigDecimal.ZERO};
        }
        if (goodsBase.signum() == 0) {
            return new BigDecimal[]{BigDecimal.ZERO, net};
        }
        BigDecimal goods = net.multiply(goodsBase).divide(totalBase, 2, java.math.RoundingMode.HALF_UP);
        return new BigDecimal[]{goods, net.subtract(goods)};
    }

    private List<CreditNote> pendingCreditNotes() {
        return creditNoteRepository.findAll().stream()
                .filter(cn -> cn.getSale() != null && cn.getSale().getStatus() == SaleStatus.COMPLETED)
                .filter(cn -> !syncLogRepository.existsByEntityTypeAndEntityId(ENTITY_CREDIT_NOTE, cn.getId()))
                .filter(cn -> !alreadyNetInSaleEntry(cn))
                .toList();
    }

    /**
     * Antes de registrar notas de crédito, el asiento de la venta se hacía con el total ya reducido.
     * Si la nota es anterior a la sincronización de la venta y ese asiento quedó por debajo del total
     * original, la nota ya está descontada y no se revierte otra vez.
     */
    private boolean alreadyNetInSaleEntry(CreditNote creditNote) {
        Sale sale = creditNote.getSale();
        return syncLogRepository.findFirstByEntityTypeAndEntityId(ENTITY_SALE, sale.getId())
                .filter(log -> log.getSyncedAt() != null && creditNote.getCreatedAt() != null
                        && creditNote.getCreatedAt().isBefore(log.getSyncedAt()))
                .flatMap(log -> journalEntryRepository.findById(log.getJournalEntryId()))
                .map(entry -> entry.getLines().stream()
                        .map(l -> safeVal(l.getDebit()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .compareTo(originalTotals(sale).total()) < 0)
                .orElse(false);
    }

    private AccJournalEntry buildPurchaseEntry(PurchaseOrder po, String username) {
        AccAccount inventoryAccount    = requireAccount("1.1.3");
        AccAccount cuentasPorPagarAccount = requireAccount("2.1.1");

        String supplierName = po.getSupplier() != null ? po.getSupplier().getName() : "Desconocido";
        String description = "Compra #" + po.getId() + " - Suplidor: " + supplierName;
        String reference   = "CMP-" + po.getId();

        LocalDate entryDate = po.getReceptionDate() != null
                ? po.getReceptionDate().toLocalDate()
                : (po.getOrderDate() != null ? po.getOrderDate() : LocalDate.now());

        AccJournalEntry entry = createBaseEntry(
                description, reference, AccEntryType.AUTO_PURCHASE, username, entryDate);

        BigDecimal total = BigDecimal.valueOf(
                po.getItems().stream()
                        .mapToDouble(i -> i.getUnitPrice() * i.getQuantity())
                        .sum());

        // Dr inventario
        addLine(entry, inventoryAccount.getId(), total, BigDecimal.ZERO, "Ingreso de mercancía");
        // Cr cuentas por pagar
        addLine(entry, cuentasPorPagarAccount.getId(), BigDecimal.ZERO, total, "Obligación con suplidor");

        return entry;
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

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

    private AccAccount requireAccount(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Required account not found in chart of accounts: " + code));
    }

    private void recordSync(String entityType, Long entityId, Long journalEntryId) {
        AccSyncLog log = new AccSyncLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setJournalEntryId(journalEntryId);
        log.setSyncedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    private BigDecimal safeVal(BigDecimal val) {
        return safeStatic(val);
    }

    private static BigDecimal safeStatic(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
