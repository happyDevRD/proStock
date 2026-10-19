package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.AccAccountRepository;
import com.happydev.prestockbackend.repository.AccJournalEntryRepository;
import com.happydev.prestockbackend.repository.AccSyncLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Asiento de la mercancía recibida de una orden de compra:
 * Dr 1.1.3 Inventario (neto) + Dr 1.1.4 ITBIS pagado por anticipado / Cr 2.1.1 Cuentas por pagar.
 * <p>
 * Solo asienta lo recibido que aún no está contabilizado ({@code accountedReceivedNet}), así que
 * se puede llamar en cada recepción y desde la sincronización contable sin duplicar asientos.
 */
@Component
public class PurchaseReceptionAccounting {

    /** Tipo de entidad en acc_sync_log (el mismo que usa la sincronización contable). */
    public static final String ENTITY_PURCHASE = "PURCHASE_ORDER";

    private final AccAccountRepository accountRepository;
    private final AccJournalEntryRepository journalEntryRepository;
    private final AccSyncLogRepository syncLogRepository;

    public PurchaseReceptionAccounting(AccAccountRepository accountRepository,
                                       AccJournalEntryRepository journalEntryRepository,
                                       AccSyncLogRepository syncLogRepository) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.syncLogRepository = syncLogRepository;
    }

    /** ¿Hay mercancía recibida sin contabilizar? */
    public static boolean hasUnbooked(PurchaseOrder po) {
        return po.getStatus() != PurchaseOrderStatus.CANCELED
                && PurchaseOrderAmounts.receivedNet(po).compareTo(accounted(po)) > 0;
    }

    /**
     * Contabiliza lo recibido pendiente y actualiza {@code accountedReceivedNet} (el llamador guarda
     * la orden). Devuelve el asiento creado o null si no había nada pendiente.
     */
    @Nullable
    public AccJournalEntry bookPendingReception(PurchaseOrder po, String username) {
        if (!hasUnbooked(po)) return null;
        BigDecimal receivedNet = PurchaseOrderAmounts.receivedNet(po);
        BigDecimal alreadyNet = accounted(po);
        BigDecimal net = receivedNet.subtract(alreadyNet);
        BigDecimal itbis = PurchaseOrderAmounts.itbisFor(po, receivedNet)
                .subtract(PurchaseOrderAmounts.itbisFor(po, alreadyNet))
                .max(BigDecimal.ZERO);

        String supplierName = po.getSupplier() != null ? po.getSupplier().getName() : "Desconocido";
        AccJournalEntry entry = new AccJournalEntry();
        entry.setEntryDate(LocalDate.now());
        entry.setDescription("Recepción compra #" + po.getId() + " - Suplidor: " + supplierName);
        entry.setReference("CMP-" + po.getId());
        entry.setEntryType(AccEntryType.AUTO_PURCHASE);
        entry.setStatus(AccEntryStatus.POSTED);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setCreatedBy(username);
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedBy(username);

        addLine(entry, requireAccount("1.1.3"), net, BigDecimal.ZERO, "Ingreso de mercancía");
        if (itbis.signum() > 0) {
            addLine(entry, requireAccount("1.1.4"), itbis, BigDecimal.ZERO, "ITBIS facturado por el suplidor");
        }
        addLine(entry, requireAccount("2.1.1"), BigDecimal.ZERO, net.add(itbis), "Obligación con suplidor");

        AccJournalEntry saved = journalEntryRepository.save(entry);
        po.setAccountedReceivedNet(receivedNet);
        if (!syncLogRepository.existsByEntityTypeAndEntityId(ENTITY_PURCHASE, po.getId())) {
            AccSyncLog log = new AccSyncLog();
            log.setEntityType(ENTITY_PURCHASE);
            log.setEntityId(po.getId());
            log.setJournalEntryId(saved.getId());
            log.setSyncedAt(LocalDateTime.now());
            syncLogRepository.save(log);
        }
        return saved;
    }

    private static BigDecimal accounted(PurchaseOrder po) {
        return po.getAccountedReceivedNet() != null ? po.getAccountedReceivedNet() : BigDecimal.ZERO;
    }

    private Long requireAccount(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Required account not found in chart of accounts: " + code))
                .getId();
    }

    private static void addLine(AccJournalEntry entry, Long accountId, BigDecimal debit, BigDecimal credit,
                                String description) {
        AccJournalEntryLine line = new AccJournalEntryLine();
        line.setJournalEntry(entry);
        line.setAccountId(accountId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setDescription(description);
        entry.getLines().add(line);
    }
}
