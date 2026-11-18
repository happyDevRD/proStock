package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SelectionDtos.AdminSelection;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.repository.ServiceOrderSelectionRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cuando una orden de fotografía pasa a Selección, sin que nadie tenga que hacer nada:
 * <ol>
 *   <li>arma las previas en "2 Seleccion" desde "1 Camara" ({@link SelectionPreviewGenerator});</li>
 *   <li>genera el acceso del cliente (usuario y contraseña) con el cupo de su paquete;</li>
 *   <li>el cliente recibe el WhatsApp con el botón al portal, y el grupo del estudio el usuario y la contraseña.</li>
 * </ol>
 * Si algo falta (sin fotos, paquete sin «N fotos»), avisa al grupo para hacerlo a mano.
 * Corre en un hilo propio, de a una orden a la vez (el NAS tiene poca memoria).
 */
@Service
public class SelectionAutomation {

    private static final Logger log = LoggerFactory.getLogger(SelectionAutomation.class);
    static final String ACTOR = "sistema";
    static final int DEFAULT_DAYS = 15;

    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderSelectionRepository selectionRepository;
    private final SelectionPreviewGenerator generator;
    private final SelectionService selectionService;
    private final StudioNotifier notifier;
    private final boolean enabled;
    private final TransactionTemplate tx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "previas-seleccion");
        t.setDaemon(true);
        return t;
    });

    public SelectionAutomation(ServiceOrderRepository orderRepository,
                               ServiceOrderSelectionRepository selectionRepository,
                               SelectionPreviewGenerator generator,
                               SelectionService selectionService,
                               StudioNotifier notifier,
                               PlatformTransactionManager transactionManager,
                               @Value("${app.selection.auto:true}") boolean enabled) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setReadOnly(true);
        this.orderRepository = orderRepository;
        this.selectionRepository = selectionRepository;
        this.generator = generator;
        this.selectionService = selectionService;
        this.notifier = notifier;
        this.enabled = enabled;
    }

    /** Después de mover la carpeta (WorkFolderService): por eso va al final. */
    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(ServiceOrderChangedEvent event) {
        if (!enabled) return;
        ServiceOrder order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null || order.getOrderType() != ServiceOrderType.PHOTOGRAPHY
                || !"SELECTION".equals(order.getCurrentStage())
                || selectionRepository.findByServiceOrderId(order.getId()).isPresent()
                || generator.isRunning(order.getId())) {
            return;
        }
        start(order.getId(), true);
    }

    /** Genera las previas en segundo plano; con {@code createAccess}, después el acceso del cliente. */
    public void start(Long orderId, boolean createAccess) {
        if (generator.isRunning(orderId)) throw new IllegalStateException("Ya se están generando las previas.");
        generator.markQueued(orderId);
        executor.submit(() -> run(orderId, createAccess));
    }

    void run(Long orderId, boolean createAccess) {
        String label = tx.execute(status -> orderRepository.findById(orderId)
                .map(o -> o.getOrderNumber() + customer(o)).orElse("#" + orderId));
        int previews;
        try {
            previews = generator.generate(orderId);
        } catch (Exception e) {
            log.warn("Previas de {}: {}", label, e.getMessage());
            notifier.send("⚠️ No se pudieron preparar las fotos de " + label + " para el cliente: " + e.getMessage());
            return;
        }
        if (!createAccess) return;
        try {
            if (selectionRepository.findByServiceOrderId(orderId).isPresent()) return;
            if (previews == 0) {
                notifier.send("⚠️ " + label + " pasó a Selección pero no hay fotos en «1 Camara». "
                        + "Cuando el fotógrafo las descargue, pulsa «Generar previas» en la orden.");
                return;
            }
            int quota = selectionService.suggest(orderId).suggestedQuota();
            if (quota <= 0) {
                notifier.send("⚠️ " + label + ": " + previews + " foto(s) listas, pero el paquete no dice cuántas fotos incluye. "
                        + "Genera el acceso del cliente a mano en la orden (indicando el cupo).");
                return;
            }
            AdminSelection sel = selectionService.create(orderId, quota, DEFAULT_DAYS, ACTOR);
            notifier.send("📸 Fotos de " + label + " listas para que el cliente elija (" + previews + " fotos, paquete de "
                    + quota + ").\nAcceso: " + sel.url() + "\nUsuario: " + sel.username() + "\nContraseña: " + sel.password()
                    + "\nEl cliente recibe el enlace por WhatsApp; pásale la contraseña por el WhatsApp del estudio.");
        } catch (Exception e) {
            log.warn("Acceso de selección de {}: {}", label, e.getMessage());
            notifier.send("⚠️ Las fotos de " + label + " están listas, pero no se pudo generar el acceso del cliente: "
                    + e.getMessage() + ". Hazlo a mano en la orden.");
        }
    }

    private static String customer(ServiceOrder order) {
        return order.getCustomer() == null ? ""
                : " (" + (order.getCustomer().getFirstName() + " "
                + (order.getCustomer().getLastName() == null ? "" : order.getCustomer().getLastName())).trim() + ")";
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
