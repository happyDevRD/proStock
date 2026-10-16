package com.happydev.prestockbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.AddServiceOrderItemRequest;
import com.happydev.prestockbackend.dto.AdvanceStageRequest;
import com.happydev.prestockbackend.dto.SelectionDtos.AdminSelection;
import com.happydev.prestockbackend.dto.SelectionDtos.PublicSelection;
import com.happydev.prestockbackend.dto.SelectionDtos.SelectionSuggestion;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.ServiceOrderItemRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import com.happydev.prestockbackend.repository.ServiceOrderSelectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Selección de fotos por el cliente desde la web (fase 3).
 *
 * <p>Administración genera un enlace privado (token aleatorio) cuando la orden está en
 * Selección. El cliente ve las previas de "2 Seleccion", marca sus favoritas (se guardan
 * como borrador) y confirma. Al confirmar: las fotos extra se agregan a la orden, los
 * originales de "1 Camara" con el mismo nombre se enlazan en "3 Edicion" (hardlinks, sin
 * ocupar espacio), se escribe SELECCION.txt y la orden pasa a Edición.
 */
@Service
public class SelectionService {

    private static final Logger log = LoggerFactory.getLogger(SelectionService.class);

    static final String PREVIEWS = "2 Seleccion";
    static final String CAMERA = "1 Camara";
    static final String EDITING = "3 Edicion";
    static final String CLIENT_ACTOR = "cliente (web)";
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp");
    /** "25 fotos digitales", "8 fotos impresas y digitales" → 25, 8 */
    private static final Pattern INCLUDED_PHOTOS = Pattern.compile("(\\d+)\\s+fotos?\\b", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", new Locale("es", "DO"));

    private final ServiceOrderSelectionRepository selectionRepository;
    private final ServiceOrderRepository orderRepository;
    private final ServiceOrderItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final CompanyConfigRepository companyConfigRepository;
    private final ServiceOrderService serviceOrderService;
    private final WorkFolderService workFolderService;
    private final ObjectMapper objectMapper;
    private final String publicUrl;
    private final String extraSku;
    private final String watermark;
    private final StudioNotifier notifier;

    public SelectionService(ServiceOrderSelectionRepository selectionRepository,
                            ServiceOrderRepository orderRepository,
                            ServiceOrderItemRepository itemRepository,
                            ProductRepository productRepository,
                            CompanyConfigRepository companyConfigRepository,
                            ServiceOrderService serviceOrderService,
                            WorkFolderService workFolderService,
                            ObjectMapper objectMapper,
                            @Value("${app.selection.public-url:}") String publicUrl,
                            @Value("${app.selection.extra-sku:ADI-FOTO-DIG}") String extraSku,
                            @Value("${app.selection.watermark:}") String watermark,
                            StudioNotifier notifier) {
        this.selectionRepository = selectionRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.serviceOrderService = serviceOrderService;
        this.workFolderService = workFolderService;
        this.objectMapper = objectMapper;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.extraSku = extraSku;
        this.watermark = watermark.trim();
        this.notifier = notifier;
    }

    /**
     * Texto de la marca de agua de las previas: el configurado, o el nombre del estudio.
     * "off" la desactiva.
     */
    @Transactional(readOnly = true)
    @Nullable
    public String watermarkText() {
        if ("off".equalsIgnoreCase(watermark)) return null;
        return watermark.isEmpty() ? companyName() : watermark;
    }

    // ── Administración ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SelectionSuggestion suggest(Long orderId) {
        ServiceOrder order = order(orderId);
        Path folder = workFolderService.resolve(order.getWorkFolder());
        return new SelectionSuggestion(suggestedQuota(orderId), folder == null ? 0 : previews(folder).size(),
                extraProduct().map(this::inclusivePrice).orElse(null));
    }

    @Transactional(readOnly = true)
    @Nullable
    public AdminSelection get(Long orderId) {
        return selectionRepository.findByServiceOrderId(orderId).map(this::toAdmin).orElse(null);
    }

    /** Crea el enlace o lo regenera (nuevo token: el anterior deja de servir). Conserva lo ya marcado. */
    @Transactional
    public AdminSelection create(Long orderId, int quota, int expiresInDays, @Nullable String actor) {
        ServiceOrder order = order(orderId);
        if (order.getOrderType() != ServiceOrderType.PHOTOGRAPHY || !"SELECTION".equals(order.getCurrentStage())) {
            throw new IllegalStateException("El enlace de selección se genera cuando la orden está en Selección.");
        }
        Path folder = workFolderService.resolve(order.getWorkFolder());
        if (folder == null || previews(folder).isEmpty()) {
            throw new IllegalStateException("No hay previas en «" + PREVIEWS + "». Exporta ahí las fotos en JPG para que el cliente las vea.");
        }
        ServiceOrderSelection sel = selectionRepository.findByServiceOrderId(orderId).orElseGet(() -> {
            ServiceOrderSelection s = new ServiceOrderSelection();
            s.setServiceOrder(order);
            return s;
        });
        if (sel.getSubmittedAt() != null) {
            throw new IllegalStateException("El cliente ya confirmó su selección.");
        }
        sel.setToken(newToken());
        sel.setCreatedAt(LocalDateTime.now());
        sel.setQuota(quota);
        sel.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        sel.setExtraProduct(extraProduct().orElse(null));
        sel.setCreatedBy(actor);
        return toAdmin(selectionRepository.save(sel));
    }

    @Transactional
    public void revoke(Long orderId) {
        selectionRepository.findByServiceOrderId(orderId).ifPresent(sel -> {
            if (sel.getSubmittedAt() != null) {
                throw new IllegalStateException("El cliente ya confirmó su selección; no se puede anular el enlace.");
            }
            selectionRepository.delete(sel);
        });
    }

    // ── Cliente (público, por token) ────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicSelection view(String token) {
        ServiceOrderSelection sel = open(token, false);
        ServiceOrder order = sel.getServiceOrder();
        Path folder = folderOf(sel);
        Customer c = order.getCustomer();
        return new PublicSelection(
                companyName(),
                order.getTitle(),
                c != null && c.getFirstName() != null ? c.getFirstName().trim().split("\\s+")[0] : null,
                sel.getQuota(),
                sel.getExtraProduct() != null ? inclusivePrice(sel.getExtraProduct()) : null,
                previews(folder),
                selected(sel),
                sel.getSubmittedAt() != null,
                sel.getExpiresAt());
    }

    /** Archivo de previa para mostrar; valida que el nombre sea uno de la carpeta (sin rutas). */
    @Transactional(readOnly = true)
    public Path preview(String token, String name) {
        ServiceOrderSelection sel = open(token, false);
        Path folder = folderOf(sel);
        if (!previews(folder).contains(name)) {
            throw new ResourceNotFoundException("Foto", "nombre", name);
        }
        return folder.resolve(PREVIEWS).resolve(name);
    }

    @Transactional
    public PublicSelection saveDraft(String token, List<String> names) {
        ServiceOrderSelection sel = open(token, true);
        sel.setSelectedFiles(json(validNames(sel, names)));
        selectionRepository.save(sel);
        return view(token);
    }

    /**
     * Confirma la selección. Si hay más fotos que el cupo, exige {@code acceptExtra}
     * (el cliente vio el costo) y agrega las extra a la orden.
     */
    @Transactional
    public PublicSelection submit(String token, List<String> names, boolean acceptExtra) {
        ServiceOrderSelection sel = open(token, true);
        List<String> chosen = validNames(sel, names);
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una foto.");
        }
        int extra = Math.max(0, chosen.size() - sel.getQuota());
        if (extra > 0 && sel.getExtraProduct() == null) {
            throw new IllegalArgumentException("Puedes elegir hasta " + sel.getQuota() + " fotos.");
        }
        if (extra > 0 && !acceptExtra) {
            throw new IllegalStateException("Elegiste " + extra + " foto(s) extra. Confirma que aceptas el costo adicional.");
        }
        ServiceOrder order = sel.getServiceOrder();
        sel.setSelectedFiles(json(chosen));
        sel.setSubmittedAt(LocalDateTime.now());
        sel.setExtraCount(extra);
        selectionRepository.save(sel);

        if (extra > 0) {
            Product p = sel.getExtraProduct();
            serviceOrderService.addItem(order.getId(), new AddServiceOrderItemRequest(
                    p.getId(), extra, basePrice(p), "Fotos extra elegidas por el cliente en la web"));
        }
        prepareEditingFolder(folderOf(sel), chosen, sel, extra);
        Customer customer = order.getCustomer();
        notifier.send("Selección confirmada: " + order.getOrderNumber()
                + (customer != null ? " (" + (customer.getFirstName() + " " + Optional.ofNullable(customer.getLastName()).orElse("")).trim() + ")" : "")
                + " eligió " + chosen.size() + " foto(s)" + (extra > 0 ? ", " + extra + " extra" : "")
                + ". Ya está en «4 En edicion».");
        serviceOrderService.advanceStage(order.getId(), new AdvanceStageRequest("EDITING", ServiceOrderStatus.IN_PROGRESS,
                "Selección confirmada por el cliente: " + chosen.size() + " foto(s)" + (extra > 0 ? ", " + extra + " extra" : "")),
                CLIENT_ACTOR);
        return view(token);
    }

    // ── Carpeta ─────────────────────────────────────────────────────────────

    /**
     * Enlaza en "3 Edicion" los originales de cámara con el mismo nombre que cada previa
     * elegida (IMG_0001.jpg → IMG_0001.CR3 y IMG_0001.JPG); si no hay original, la previa.
     * Errores de disco no deshacen la confirmación: quedan en el log y en SELECCION.txt.
     */
    void prepareEditingFolder(Path folder, List<String> chosen, ServiceOrderSelection sel, int extra) {
        Path editing = folder.resolve(EDITING);
        List<String> missing = new ArrayList<>();
        try {
            Files.createDirectories(editing);
            Map<String, List<Path>> originals = indexByBaseName(folder.resolve(CAMERA));
            for (String name : chosen) {
                List<Path> sources = originals.getOrDefault(baseName(name), List.of());
                if (sources.isEmpty()) {
                    missing.add(name);
                    sources = List.of(folder.resolve(PREVIEWS).resolve(name));
                }
                boolean ambiguous = sources.stream().map(Path::getFileName).distinct().count() < sources.size();
                for (Path src : sources) {
                    String target = ambiguous ? src.getParent().getFileName() + " - " + src.getFileName() : src.getFileName().toString();
                    link(src, editing.resolve(target));
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo preparar {} para {}: {}", editing, sel.getServiceOrder().getOrderNumber(), e.getMessage());
        }
        writeSummary(editing, chosen, sel, extra, missing);
    }

    private static Map<String, List<Path>> indexByBaseName(Path camera) throws IOException {
        if (!Files.isDirectory(camera)) return Map.of();
        try (var paths = Files.walk(camera)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("@eaDir") && !p.getFileName().toString().startsWith("."))
                    .collect(Collectors.groupingBy(p -> baseName(p.getFileName().toString())));
        }
    }

    private static void link(Path src, Path target) throws IOException {
        if (Files.exists(target)) return;
        try {
            Files.createLink(target, src);
        } catch (UnsupportedOperationException | IOException e) {
            Files.copy(src, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private void writeSummary(Path editing, List<String> chosen, ServiceOrderSelection sel, int extra, List<String> missing) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Selección del cliente — ").append(sel.getServiceOrder().getOrderNumber()).append("\r\n");
        sb.append("Confirmada: ").append(sel.getSubmittedAt().format(DATE_TIME)).append("\r\n");
        sb.append("Fotos elegidas: ").append(chosen.size()).append(" (incluidas: ").append(sel.getQuota())
                .append(extra > 0 ? ", extra: " + extra : "").append(")\r\n\r\n");
        chosen.forEach(n -> sb.append("  ").append(n).append("\r\n"));
        if (!missing.isEmpty()) {
            sb.append("\r\nSin original en «").append(CAMERA).append("» (se copió la previa):\r\n");
            missing.forEach(n -> sb.append("  ").append(n).append("\r\n"));
        }
        try {
            Files.writeString(editing.resolve("SELECCION.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("No se pudo escribir SELECCION.txt en {}: {}", editing, e.getMessage());
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    /** Fotos incluidas según los combos de la orden: "25 fotos digitales" × cantidad. */
    int suggestedQuota(Long orderId) {
        int total = 0;
        for (ServiceOrderItem item : itemRepository.findByServiceOrderIdOrderByPositionAscCreatedAtAsc(orderId)) {
            String text = Optional.ofNullable(item.getProduct().getDescription()).orElse("") + " " + item.getProduct().getName();
            Matcher m = INCLUDED_PHOTOS.matcher(text);
            if (m.find() && !extraSku.equalsIgnoreCase(item.getProduct().getSku())) {
                total += Integer.parseInt(m.group(1)) * item.getQuantity();
            }
        }
        return total;
    }

    static List<String> previews(Path folder) {
        Path dir = folder.resolve(PREVIEWS);
        if (!Files.isDirectory(dir)) return List.of();
        try (var files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !n.startsWith(".") && IMAGE_EXT.contains(extension(n)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private ServiceOrderSelection open(String token, boolean forWriting) {
        ServiceOrderSelection sel = selectionRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Selección", "enlace", "-"));
        if (sel.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Este enlace venció. Pide uno nuevo al estudio.");
        }
        if (forWriting && sel.getSubmittedAt() != null) {
            throw new IllegalStateException("Ya confirmaste tu selección. Si necesitas cambiarla, escribe al estudio.");
        }
        return sel;
    }

    private Path folderOf(ServiceOrderSelection sel) {
        Path folder = workFolderService.resolve(sel.getServiceOrder().getWorkFolder());
        if (folder == null) throw new IllegalStateException("Las fotos no están disponibles en este momento.");
        return folder;
    }

    private List<String> validNames(ServiceOrderSelection sel, List<String> names) {
        Set<String> available = new HashSet<>(previews(folderOf(sel)));
        return names.stream().distinct().filter(available::contains).toList();
    }

    private List<String> selected(ServiceOrderSelection sel) {
        try {
            return objectMapper.readValue(sel.getSelectedFiles(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String json(List<String> names) {
        try {
            return objectMapper.writeValueAsString(names);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Optional<Product> extraProduct() {
        return productRepository.findBySku(extraSku)
                .filter(p -> p.getStatus() == null || p.getStatus() == ProductStatus.ACTIVE);
    }

    private boolean pricesIncludeItbis() {
        return companyConfigRepository.findFirstByOrderByIdAsc().map(CompanyConfig::isPreciosIncluyenItbis).orElse(false);
    }

    private BigDecimal rate(Product p) {
        return p.getIndicadorFacturacion() != null ? p.getIndicadorFacturacion().getRate() : BigDecimal.ZERO;
    }

    /** Precio que paga el cliente (con ITBIS). */
    BigDecimal inclusivePrice(Product p) {
        BigDecimal price = p.getSellingPrice();
        return pricesIncludeItbis() ? price : price.multiply(BigDecimal.ONE.add(rate(p))).setScale(2, RoundingMode.HALF_UP);
    }

    /** Precio base que guardan las líneas de la orden (sin ITBIS), como hace la interfaz. */
    BigDecimal basePrice(Product p) {
        BigDecimal price = p.getSellingPrice();
        return pricesIncludeItbis() ? price.divide(BigDecimal.ONE.add(rate(p)), 2, RoundingMode.HALF_UP) : price;
    }

    private String companyName() {
        return companyConfigRepository.findFirstByOrderByIdAsc()
                .map(c -> c.getNombreComercial() != null && !c.getNombreComercial().isBlank() ? c.getNombreComercial() : c.getRazonSocial())
                .orElse("El estudio");
    }

    private AdminSelection toAdmin(ServiceOrderSelection sel) {
        Path folder = workFolderService.resolve(sel.getServiceOrder().getWorkFolder());
        return new AdminSelection(
                publicUrl.isEmpty() ? sel.getToken() : publicUrl + "/" + sel.getToken(),
                sel.getQuota(),
                sel.getExpiresAt(),
                sel.getExpiresAt().isBefore(LocalDateTime.now()),
                folder == null ? 0 : previews(folder).size(),
                selected(sel).size(),
                sel.getSubmittedAt(),
                sel.getExtraCount(),
                sel.getExtraProduct() != null ? inclusivePrice(sel.getExtraProduct()) : null,
                selected(sel),
                sel.getCreatedAt(),
                sel.getUpdatedAt());
    }

    private ServiceOrder order(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", "id", id));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 ? fileName.substring(0, dot) : fileName).toLowerCase(Locale.ROOT);
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
