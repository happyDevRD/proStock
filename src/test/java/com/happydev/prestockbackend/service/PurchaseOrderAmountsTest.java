package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import com.happydev.prestockbackend.entity.PurchaseOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurchaseOrderAmountsTest {

    /** Orden de 10 × 100 (neto 1,000) con 180 de ITBIS facturado. */
    private static PurchaseOrder order() {
        PurchaseOrder po = new PurchaseOrder();
        po.setStatus(PurchaseOrderStatus.PENDING);
        po.setTotalItbis(new BigDecimal("180.00"));
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setQuantity(10);
        item.setUnitPrice(100);
        item.setPurchaseOrder(po);
        po.getItems().add(item);
        return po;
    }

    private static void receive(PurchaseOrder po, int units) {
        PurchaseOrderItem item = po.getItems().get(0);
        item.setQuantityReceived(item.getQuantityReceived() + units);
        po.setStatus(PurchaseOrderAmounts.deriveStatus(po));
    }

    private static void pay(PurchaseOrder po, String amount) {
        po.setPaidAmount(PurchaseOrderAmounts.paid(po).add(new BigDecimal(amount)));
        po.setStatus(PurchaseOrderAmounts.deriveStatus(po));
    }

    @Test
    void sinRecibirNoSeDebeNada() {
        PurchaseOrder po = order();
        assertEquals(new BigDecimal("1180.00"), PurchaseOrderAmounts.orderValue(po));
        assertEquals(0, PurchaseOrderAmounts.balanceDue(po).signum());
        assertEquals(PurchaseOrderStatus.PENDING, PurchaseOrderAmounts.deriveStatus(po));
    }

    @Test
    void laDeudaNaceConLoRecibidoYSuItbisProporcional() {
        PurchaseOrder po = order();
        receive(po, 4);
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, po.getStatus());
        // 400 neto + 72 de ITBIS (40 % de 180)
        assertEquals(new BigDecimal("472.00"), PurchaseOrderAmounts.receivedValue(po));
        assertEquals(new BigDecimal("472.00"), PurchaseOrderAmounts.balanceDue(po));
    }

    @Test
    void pagadaEnParteSigueAdmitiendoRecepcionesYTerminaPagada() {
        PurchaseOrder po = order();
        receive(po, 4);
        pay(po, "472.00");
        assertEquals(PurchaseOrderStatus.PARTIALLY_PAID, po.getStatus());
        assertEquals(0, PurchaseOrderAmounts.balanceDue(po).signum());
        assertEquals(6, PurchaseOrderAmounts.remainingUnits(po));

        receive(po, 6);
        assertEquals(PurchaseOrderStatus.PARTIALLY_PAID, po.getStatus());
        // Al completarse, el ITBIS se toma entero: 1,180 − 472
        assertEquals(new BigDecimal("708.00"), PurchaseOrderAmounts.balanceDue(po));

        pay(po, "708.00");
        assertEquals(PurchaseOrderStatus.PAID, po.getStatus());
    }

    @Test
    void recibidaCompletaSinPagos() {
        PurchaseOrder po = order();
        receive(po, 10);
        assertEquals(PurchaseOrderStatus.RECEIVED, po.getStatus());
        assertEquals(new BigDecimal("1180.00"), PurchaseOrderAmounts.balanceDue(po));
    }

    @Test
    void canceladaNoDebeNiCambiaDeEstado() {
        PurchaseOrder po = order();
        po.setStatus(PurchaseOrderStatus.CANCELED);
        assertEquals(PurchaseOrderStatus.CANCELED, PurchaseOrderAmounts.deriveStatus(po));
        assertEquals(0, PurchaseOrderAmounts.balanceDue(po).signum());
    }
}
