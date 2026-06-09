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

    private final SaleRepository saleRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AccSyncLogRepository syncLogRepository;
    private final AccAccountRepository accountRepository;
    private final AccJournalEntryRepository journalEntryRepository;

    public AccountingSyncServiceImpl(SaleRepository saleRepository,
                                     PurchaseOrderRepository purchaseOrderRepository,
                                     AccSyncLogRepository syncLogRepository,
                                     AccAccountRepository accountRepository,
                                     AccJournalEntryRepository journalEntryRepository) {
        this.saleRepository = saleRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.syncLogRepository = syncLogRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
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
                .map(Sale::getMontoTotal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPurchasesAmount = receivedOrders.stream()
                .map(po -> BigDecimal.valueOf(
                        po.getItems().stream()
                                .mapToDouble(i -> i.getUnitPrice() * i.getQuantity())
                                .sum()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AccountSyncPreviewDto(
                completedSales.size(),
                receivedOrders.size(),
                totalSalesAmount,
                totalPurchasesAmount
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
        AccAccount ventasAccount   = requireAccount("4.1.1");
        AccAccount itbisAccount    = requireAccount("2.1.2");

        String description = "Venta #" + sale.getId() + " - "
                + (sale.getNcf() != null ? sale.getNcf() : "Sin NCF");
        String reference = sale.getNcf() != null ? sale.getNcf() : "VTA-" + sale.getId();

        AccJournalEntry entry = createBaseEntry(
                description, reference, AccEntryType.AUTO_SALE, username,
                sale.getSaleDate() != null ? sale.getSaleDate().toLocalDate() : LocalDate.now());

        BigDecimal montoTotal    = safeVal(sale.getMontoTotal());
        BigDecimal montoGravado  = safeVal(sale.getMontoGravadoTotal());
        BigDecimal montoExento   = safeVal(sale.getMontoExento());
        BigDecimal totalItbis    = safeVal(sale.getTotalItbis());
        BigDecimal netSales      = montoGravado.add(montoExento);

        // Dr cash/bank = montoTotal
        addLine(entry, cashBankAccount.getId(), montoTotal, BigDecimal.ZERO, "Cobro venta");
        // Cr ventas = net of ITBIS
        addLine(entry, ventasAccount.getId(), BigDecimal.ZERO, netSales, "Venta de bienes");
        // Cr ITBIS por pagar (only if > 0)
        if (totalItbis.compareTo(BigDecimal.ZERO) > 0) {
            addLine(entry, itbisAccount.getId(), BigDecimal.ZERO, totalItbis, "ITBIS repercutido");
        }

        return entry;
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
        return val != null ? val : BigDecimal.ZERO;
    }
}
