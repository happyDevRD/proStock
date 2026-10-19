package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import com.happydev.prestockbackend.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importes y estado de una orden de compra. Reglas (práctica contable estándar):
 * <ul>
 *   <li>Valor de la orden = neto (precio sin ITBIS × cantidad) + ITBIS facturado por el suplidor.</li>
 *   <li>La deuda con el suplidor nace al <b>recibir</b> la mercancía: el valor recibido es el neto
 *       recibido más la parte proporcional del ITBIS (todo el ITBIS cuando la orden está completa).</li>
 *   <li>Saldo por pagar = valor recibido − pagado. Solo se paga lo recibido.</li>
 *   <li>El estado se deriva siempre de cantidades y pagos; nunca se fija a mano.</li>
 * </ul>
 */
public final class PurchaseOrderAmounts {

    private PurchaseOrderAmounts() {}

    /** Neto pedido (sin ITBIS). */
    public static BigDecimal orderNet(PurchaseOrder po) {
        return po.getItems().stream()
                .map(i -> lineValue(i, i.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Neto de lo ya recibido (sin ITBIS). */
    public static BigDecimal receivedNet(PurchaseOrder po) {
        return po.getItems().stream()
                .map(i -> lineValue(i, i.getQuantityReceived()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal itbis(PurchaseOrder po) {
        return po.getTotalItbis() != null ? po.getTotalItbis() : BigDecimal.ZERO;
    }

    /** ITBIS que corresponde a un neto recibido: proporcional al neto, completo si la orden está completa. */
    public static BigDecimal itbisFor(PurchaseOrder po, BigDecimal net) {
        BigDecimal orderNet = orderNet(po);
        if (orderNet.signum() == 0) return BigDecimal.ZERO;
        if (net.compareTo(orderNet) >= 0) return itbis(po);
        return itbis(po).multiply(net).divide(orderNet, 2, RoundingMode.HALF_UP);
    }

    /** Valor total de la orden: neto + ITBIS. */
    public static BigDecimal orderValue(PurchaseOrder po) {
        return orderNet(po).add(itbis(po));
    }

    /** Valor de lo recibido: neto recibido + su ITBIS. Es lo que se le debe al suplidor. */
    public static BigDecimal receivedValue(PurchaseOrder po) {
        BigDecimal net = receivedNet(po);
        return net.add(itbisFor(po, net));
    }

    public static BigDecimal paid(PurchaseOrder po) {
        return po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO;
    }

    /** Lo que se puede pagar hoy: valor recibido − pagado (nunca negativo). */
    public static BigDecimal balanceDue(PurchaseOrder po) {
        if (po.getStatus() == PurchaseOrderStatus.CANCELED) return BigDecimal.ZERO;
        return receivedValue(po).subtract(paid(po)).max(BigDecimal.ZERO);
    }

    public static int remainingUnits(PurchaseOrder po) {
        return po.getItems().stream()
                .mapToInt(i -> Math.max(0, i.getQuantity() - i.getQuantityReceived()))
                .sum();
    }

    public static boolean anyReceived(PurchaseOrder po) {
        return po.getItems().stream().anyMatch(i -> i.getQuantityReceived() > 0);
    }

    public static boolean fullyReceived(PurchaseOrder po) {
        return !po.getItems().isEmpty() && remainingUnits(po) == 0;
    }

    /**
     * Estado según cantidades y pagos. CANCELED se respeta. Con pagos: PAID si todo está
     * recibido y pagado, si no PARTIALLY_PAID (aunque falte mercancía, que se puede seguir
     * recibiendo). Sin pagos: RECEIVED, PARTIALLY_RECEIVED o PENDING según lo recibido.
     */
    public static PurchaseOrderStatus deriveStatus(PurchaseOrder po) {
        if (po.getStatus() == PurchaseOrderStatus.CANCELED) return PurchaseOrderStatus.CANCELED;
        if (paid(po).signum() > 0) {
            return fullyReceived(po) && paid(po).compareTo(orderValue(po)) >= 0
                    ? PurchaseOrderStatus.PAID
                    : PurchaseOrderStatus.PARTIALLY_PAID;
        }
        if (fullyReceived(po)) return PurchaseOrderStatus.RECEIVED;
        return anyReceived(po) ? PurchaseOrderStatus.PARTIALLY_RECEIVED : PurchaseOrderStatus.PENDING;
    }

    private static BigDecimal lineValue(PurchaseOrderItem item, int quantity) {
        return BigDecimal.valueOf(item.getUnitPrice())
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
