package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderItem;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.ServiceOrderItemRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carpeta de trabajo en el NAS por cada orden de fotografía:
 *
 * <pre>
 * {root}/{carpeta de etapa}/{OS-2026-00123 Nombre Cliente}/
 *     INFO.txt   1 Camara/   2 Seleccion/   3 Edicion/   4 Final/
 * </pre>
 *
 * Al cambiar de etapa la carpeta se mueve a la carpeta de la etapa nueva
 * (renombrado dentro del mismo volumen: instantáneo sin importar el tamaño).
 *
 * La función se activa solo si {@code app.work-folders.root} apunta a un
 * directorio existente. Cualquier error de disco se registra y se reintenta
 * en el siguiente cambio de la orden o con el botón del detalle; nunca
 * bloquea la orden.
 */
@Service
public class WorkFolderService {

    private static final Logger log = LoggerFactory.getLogger(WorkFolderService.class);

    /** Carpetas por etapa (fotografía). Numeradas para que el explorador las ordene. */
    static final Map<String, String> STAGE_DIRS = Map.of(
            "SCHEDULED", "1 Agendadas",
            "SESSION", "2 En sesion",
            "SELECTION", "3 En seleccion",
            "EDITING", "4 En edicion",
            "READY", "5 Listas para entregar",
            "DELIVERED", "6 Entregadas"
    );
    static final String CANCELLED_DIR = "7 Canceladas";
    static final List<String> SUBFOLDERS = List.of("1 Camara", "2 Seleccion", "3 Edicion", "4 Final");
    static final String INFO_FILE = "INFO.txt";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", new Locale("es", "DO"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderItemRepository itemRepository;
    private final Path root;
    private final String displayPrefix;

    public WorkFolderService(ServiceOrderRepository orderRepository,
                             ServiceOrderItemRepository itemRepository,
                             @Value("${app.work-folders.root:}") String root,
                             @Value("${app.work-folders.display-prefix:}") String displayPrefix) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.root = root.isBlank() ? null : Path.of(root);
        this.displayPrefix = displayPrefix.isBlank() ? null : displayPrefix.replaceAll("[\\\\/]+$", "");
        if (this.root != null && !Files.isDirectory(this.root)) {
            log.warn("Carpetas de trabajo desactivadas: {} no existe o no es un directorio", this.root);
        }
    }

    public boolean isEnabled() {
        return root != null && Files.isDirectory(root);
    }

    /** Basura de sistemas que no cuenta como material (Mac, Windows, Synology). */
    private static final java.util.Set<String> IGNORED_FILES = java.util.Set.of(".DS_Store", "Thumbs.db", "desktop.ini");
    private static final int MAX_COUNT = 100_000;

    /**
     * Archivos por subcarpeta ("1 Camara" → 412…), contando también los de
     * subcarpetas (CAMARA UNO, CAMARA DOS…). null si la función está apagada o
     * la carpeta no existe. Ignora archivos ocultos y carpetas internas de Synology.
     */
    @Nullable
    public Map<String, Integer> fileCounts(@Nullable String workFolder) {
        if (!isEnabled() || workFolder == null) return null;
        Path folder = root.resolve(workFolder);
        if (!Files.isDirectory(folder)) return null;
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String sub : SUBFOLDERS) {
            counts.put(sub, countFiles(folder.resolve(sub)));
        }
        return counts;
    }

    private static int countFiles(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (var paths = Files.walk(dir)) {
            return (int) paths
                    .filter(p -> !p.toString().contains("@eaDir") && !p.toString().contains("#recycle"))
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return !name.startsWith(".") && !IGNORED_FILES.contains(name);
                    })
                    .limit(MAX_COUNT)
                    .count();
        } catch (IOException | java.io.UncheckedIOException e) {
            log.warn("No se pudieron contar los archivos de {}: {}", dir, e.getMessage());
            return 0;
        }
    }

    /** Ruta que ve el personal en su computadora, p. ej. \\ESTUDIO-NAS-01\Ordenes\1 Agendadas\OS-... */
    @Nullable
    public String displayPath(@Nullable String workFolder) {
        if (workFolder == null) return null;
        if (displayPrefix == null) return workFolder;
        String sep = displayPrefix.startsWith("\\\\") ? "\\" : "/";
        return displayPrefix + sep + workFolder.replace("/", sep);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderChanged(ServiceOrderChangedEvent event) {
        try {
            sync(event.orderId());
        } catch (Exception e) {
            log.warn("No se pudo actualizar la carpeta de la orden {}: {}", event.orderId(), e.getMessage());
        }
    }

    /**
     * Deja la carpeta de la orden donde corresponde: la crea si falta, la mueve
     * a la carpeta de su etapa actual y reescribe INFO.txt. Idempotente. Lanza
     * IOException (después de actualizar lo que sí pudo) para que el botón
     * manual muestre el error.
     */
    @Transactional
    public void sync(Long orderId) throws IOException {
        if (!isEnabled()) return;
        ServiceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", "id", orderId));
        if (order.getOrderType() != ServiceOrderType.PHOTOGRAPHY) return;

        Path targetDir = root.resolve(targetDir(order));
        Path folder = locate(order);
        IOException moveError = null;

        if (folder == null) {
            folder = uniqueChild(targetDir, folderName(order));
            Files.createDirectories(folder);
            log.info("Carpeta creada para {}: {}", order.getOrderNumber(), folder);
        } else if (!folder.getParent().equals(targetDir)) {
            Files.createDirectories(targetDir);
            Path dest = uniqueChild(targetDir, folder.getFileName().toString());
            try {
                Files.move(folder, dest, StandardCopyOption.ATOMIC_MOVE);
                log.info("Carpeta de {} movida a {}", order.getOrderNumber(), dest);
                folder = dest;
            } catch (IOException e) {
                // Típicamente un archivo abierto por SMB; se reintenta en el próximo cambio.
                moveError = new IOException("No se pudo mover la carpeta a \"" + targetDir.getFileName()
                        + "\": " + e.getMessage() + ". ¿Hay archivos abiertos en ella?", e);
                log.warn("No se pudo mover la carpeta de {} a {}: {}", order.getOrderNumber(), dest, e.toString());
            }
        }

        String relative = root.relativize(folder).toString().replace('\\', '/');
        if (!relative.equals(order.getWorkFolder())) {
            order.setWorkFolder(relative);
            orderRepository.save(order);
        }
        for (String sub : SUBFOLDERS) {
            Files.createDirectories(folder.resolve(sub));
        }
        writeAtomically(folder.resolve(INFO_FILE), info(order));
        if (moveError != null) throw moveError;
    }

    static String targetDir(ServiceOrder order) {
        if (order.getStatus() == ServiceOrderStatus.CANCELLED) return CANCELLED_DIR;
        return stageDir(order.getCurrentStage());
    }

    /**
     * La carpeta guardada si sigue existiendo; si no (alguien la movió a mano),
     * la busca por número de orden en las carpetas de etapa.
     */
    @Nullable
    private Path locate(ServiceOrder order) throws IOException {
        if (order.getWorkFolder() != null) {
            Path stored = root.resolve(order.getWorkFolder());
            if (Files.isDirectory(stored)) return stored;
        }
        String number = order.getOrderNumber();
        try (var stages = Files.list(root)) {
            for (Path stage : stages.filter(Files::isDirectory).toList()) {
                try (var folders = Files.list(stage)) {
                    var found = folders
                            .filter(Files::isDirectory)
                            .filter(f -> {
                                String name = f.getFileName().toString();
                                return name.equals(number) || name.startsWith(number + " ");
                            })
                            .findFirst();
                    if (found.isPresent()) return found.get();
                }
            }
        }
        return null;
    }

    /** dir/name, o "name (2)", "name (3)"… si ya existe otra carpeta con ese nombre. */
    private static Path uniqueChild(Path dir, String name) {
        Path candidate = dir.resolve(name);
        for (int n = 2; Files.exists(candidate); n++) {
            candidate = dir.resolve(name + " (" + n + ")");
        }
        return candidate;
    }

    static String stageDir(String stage) {
        return STAGE_DIRS.getOrDefault(stage, STAGE_DIRS.get("SCHEDULED"));
    }

    /** "OS-2026-00123 Maria Perez": sin teléfono ni caracteres que Windows no acepta. */
    static String folderName(ServiceOrder order) {
        String who = customerName(order.getCustomer());
        if (who == null || who.isBlank()) who = order.getTitle();
        String name = order.getOrderNumber() + (who == null || who.isBlank() ? "" : " " + who);
        name = Normalizer.normalize(name, Normalizer.Form.NFC)
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (name.length() > 80) name = name.substring(0, 80).trim();
        // Windows no admite nombres que terminen en punto o espacio
        return name.replaceAll("[. ]+$", "");
    }

    String info(ServiceOrder order) {
        Customer c = order.getCustomer();
        StringBuilder sb = new StringBuilder();
        line(sb, "Orden", order.getOrderNumber());
        line(sb, "Evento", order.getTitle());
        line(sb, "Cliente", customerName(c));
        line(sb, "Teléfono", c != null ? c.getPhoneNumber() : null);
        line(sb, "Correo", c != null ? c.getEmail() : null);
        line(sb, "Fecha de la sesión", order.getAppointmentDate() != null ? order.getAppointmentDate().format(DATE_TIME) : null);
        line(sb, "Entrega estimada", order.getEstimatedDelivery() != null ? order.getEstimatedDelivery().format(DATE) : null);
        line(sb, "Fotógrafo", employeeName(order.getPhotographer()));
        line(sb, "Editor", employeeName(order.getEditor()));

        List<ServiceOrderItem> items = itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(order.getId());
        if (!items.isEmpty()) {
            sb.append("\r\nServicios:\r\n");
            for (ServiceOrderItem i : items) {
                sb.append("  - ").append(i.getQuantity()).append(" x ").append(i.getProduct().getName());
                if (i.getNotes() != null && !i.getNotes().isBlank()) sb.append(" (").append(i.getNotes().trim()).append(")");
                sb.append("\r\n");
            }
        }
        if (order.getInternalNotes() != null && !order.getInternalNotes().isBlank()) {
            sb.append("\r\nNotas:\r\n").append(order.getInternalNotes().trim().replace("\n", "\r\n")).append("\r\n");
        }
        sb.append("\r\nCarpetas:\r\n")
                .append("  1 Camara     fotos tal como salen de la cámara\r\n")
                .append("  2 Seleccion  previas en JPG para que el cliente elija\r\n")
                .append("  3 Edicion    fotos elegidas por el cliente, para editar\r\n")
                .append("  4 Final      fotos editadas para entregar\r\n");
        sb.append("\r\nActualizado por ProStock: ").append(LocalDateTime.now().format(DATE_TIME)).append("\r\n")
                .append("Este archivo se reescribe solo; los cambios hechos aquí se pierden.\r\n");
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, @Nullable String value) {
        sb.append(label).append(": ").append(value == null || value.isBlank() ? "-" : value.trim()).append("\r\n");
    }

    @Nullable
    private static String customerName(@Nullable Customer c) {
        if (c == null) return null;
        return (c.getFirstName() + " " + (c.getLastName() != null ? c.getLastName() : "")).trim();
    }

    @Nullable
    private static String employeeName(@Nullable Employee e) {
        return e == null ? null : e.getNombreCompleto();
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        // BOM para que el Bloc de notas de Windows muestre bien los acentos
        Files.writeString(tmp, "\uFEFF" + content, StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
