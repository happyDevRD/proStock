package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountingSyncServiceImplTest {

    @Mock private SaleRepository saleRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private AccSyncLogRepository syncLogRepository;
    @Mock private AccAccountRepository accountRepository;
    @Mock private AccJournalEntryRepository journalEntryRepository;
    @Mock private CreditNoteRepository creditNoteRepository;

    @InjectMocks
    private AccountingSyncServiceImpl service;

    private static final Map<String, Long> ACCOUNT_IDS = Map.of(
            "1.1.1.01", 1L, "1.1.1.02", 2L, "2.1.2", 3L, "4.1.1", 4L, "4.1.2", 5L);

    @BeforeEach
    void setUp() {
        ACCOUNT_IDS.forEach((code, id) -> {
            AccAccount account = new AccAccount();
            account.setId(id);
            account.setCode(code);
            when(accountRepository.findByCode(code)).thenReturn(Optional.of(account));
        });
        when(syncLogRepository.existsByEntityTypeAndEntityId(anyString(), anyLong())).thenReturn(false);
        when(syncLogRepository.findFirstByEntityTypeAndEntityId(anyString(), anyLong())).thenReturn(Optional.empty());
        when(purchaseOrderRepository.findAll()).thenReturn(List.of());
        when(journalEntryRepository.save(any(AccJournalEntry.class))).thenAnswer(inv -> {
            AccJournalEntry e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
    }

    @Test
    void sync_mixedSale_splitsGoodsAndServicesRevenue() {
        Sale sale = sale(10L, PaymentMethod.CARD,
                item(TipoBienServicio.SERVICIO, "1000.00", 1),
                item(TipoBienServicio.BIEN, "500.00", 2));
        sale.setMontoGravadoTotal(new BigDecimal("2000.00"));
        sale.setTotalItbis(new BigDecimal("360.00"));
        sale.setMontoTotal(new BigDecimal("2360.00"));
        when(saleRepository.findAll()).thenReturn(List.of(sale));
        when(creditNoteRepository.findBySale_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(creditNoteRepository.findAll()).thenReturn(List.of());

        service.sync("tester");

        Map<Long, BigDecimal[]> lines = linesByAccount(capturedEntries(1).get(0));
        assertMoney("2360.00", lines.get(2L)[0]);   // Dr banco
        assertMoney("1000.00", lines.get(4L)[1]);   // Cr ventas de bienes
        assertMoney("1000.00", lines.get(5L)[1]);   // Cr ventas de servicios
        assertMoney("360.00", lines.get(3L)[1]);    // Cr ITBIS
    }

    @Test
    void sync_voidedDeposit_recordsOriginalSaleAndCreditNoteReversal() {
        // Anticipo de RD$ 2,000 anulado con nota de crédito: la venta queda en 0.
        Sale deposit = sale(20L, PaymentMethod.CASH, item(TipoBienServicio.SERVICIO, "1694.92", 1));
        deposit.setSaleKind(SaleKind.DEPOSIT);
        deposit.setNcf("E320000000044");

        CreditNote creditNote = new CreditNote();
        creditNote.setId(7L);
        creditNote.setSale(deposit);
        creditNote.setNcf("E340000000002");
        creditNote.setCreatedAt(LocalDateTime.now());
        creditNote.setMontoGravadoTotal(new BigDecimal("1694.92"));
        creditNote.setTotalItbis(new BigDecimal("305.08"));
        creditNote.setMontoTotal(new BigDecimal("2000.00"));
        CreditNoteItem cnItem = new CreditNoteItem();
        cnItem.setProduct(deposit.getItems().get(0).getProduct());
        cnItem.setUnitPrice(new BigDecimal("1694.92"));
        cnItem.setQuantity(1);
        creditNote.getItems().add(cnItem);

        when(saleRepository.findAll()).thenReturn(List.of(deposit));
        when(creditNoteRepository.findBySale_IdOrderByCreatedAtDesc(20L)).thenReturn(List.of(creditNote));
        when(creditNoteRepository.findAll()).thenReturn(List.of(creditNote));

        int count = service.sync("tester");

        assertEquals(2, count);
        List<AccJournalEntry> entries = capturedEntries(2);

        Map<Long, BigDecimal[]> saleLines = linesByAccount(entries.get(0));
        assertMoney("2000.00", saleLines.get(1L)[0]);  // Dr caja: total original
        assertMoney("1694.92", saleLines.get(5L)[1]);  // Cr ventas de servicios
        assertMoney("305.08", saleLines.get(3L)[1]);   // Cr ITBIS

        AccJournalEntry reversal = entries.get(1);
        assertEquals(AccEntryType.AUTO_CREDIT_NOTE, reversal.getEntryType());
        Map<Long, BigDecimal[]> cnLines = linesByAccount(reversal);
        assertMoney("1694.92", cnLines.get(5L)[0]);    // Dr ventas de servicios
        assertMoney("305.08", cnLines.get(3L)[0]);     // Dr ITBIS
        assertMoney("2000.00", cnLines.get(1L)[1]);    // Cr caja: devolución
    }

    @Test
    void splitGoodsAndServices_absorbsRoundingInServices() {
        Product good = new Product();
        good.setTipoBienServicio(TipoBienServicio.BIEN);
        Product service = new Product();
        service.setTipoBienServicio(TipoBienServicio.SERVICIO);

        BigDecimal[] split = AccountingSyncServiceImpl.splitGoodsAndServices(new BigDecimal("100.00"), List.of(
                new AccountingSyncServiceImpl.Line(good, new BigDecimal("1.00"), 1),
                new AccountingSyncServiceImpl.Line(service, new BigDecimal("2.00"), 1)));

        assertMoney("33.33", split[0]);
        assertMoney("66.67", split[1]);
    }

    // ── helpers ──────────────────────────────────────────

    private static Sale sale(Long id, PaymentMethod method, SaleItem... items) {
        Sale sale = new Sale();
        sale.setId(id);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setPaymentMethod(method);
        sale.setSaleDate(LocalDateTime.now());
        for (SaleItem item : items) {
            item.setSale(sale);
            sale.getItems().add(item);
        }
        return sale;
    }

    private static SaleItem item(TipoBienServicio tipo, String unitPrice, int qty) {
        Product product = new Product();
        product.setTipoBienServicio(tipo);
        SaleItem item = new SaleItem();
        item.setProduct(product);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(qty);
        return item;
    }

    private List<AccJournalEntry> capturedEntries(int expected) {
        ArgumentCaptor<AccJournalEntry> captor = ArgumentCaptor.forClass(AccJournalEntry.class);
        verify(journalEntryRepository, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    /** accountId → [debit, credit] */
    private static Map<Long, BigDecimal[]> linesByAccount(AccJournalEntry entry) {
        BigDecimal debit = entry.getLines().stream().map(AccJournalEntryLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = entry.getLines().stream().map(AccJournalEntryLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debit.compareTo(credit), "El asiento debe cuadrar: débito " + debit + " / crédito " + credit);
        return entry.getLines().stream().collect(Collectors.toMap(
                AccJournalEntryLine::getAccountId, l -> new BigDecimal[]{l.getDebit(), l.getCredit()}));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " pero fue " + actual);
    }
}
