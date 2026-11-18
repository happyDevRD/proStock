package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Arma las previas del portal ("2 Seleccion") a partir de lo que el fotógrafo descargó
 * en "1 Camara", sin exportar nada a mano: de cada foto (mismo nombre base) se toma la JPG
 * de cámara si existe o, si solo hay RAW, la vista previa que el RAW lleva dentro.
 *
 * <p>Las previas salen derechas (se aplica la orientación EXIF) y a 2048 px como máximo;
 * la marca de agua la pone después {@link PreviewImageService} al servirlas. Si en
 * "2 Seleccion" ya hay una previa con ese nombre (exportada a mano), se respeta.
 */
@Service
public class SelectionPreviewGenerator {

    private static final Logger log = LoggerFactory.getLogger(SelectionPreviewGenerator.class);
    static final int MAX_SIDE = 2048;
    static final String WORK_DIR = ".generando";
    private static final Set<String> JPEG_EXT = Set.of("jpg", "jpeg");

    /** Avance para mostrar en la orden. */
    public record Progress(boolean running, int total, int done, int created, int failed,
                           @Nullable String error, LocalDateTime startedAt, @Nullable LocalDateTime finishedAt) {}

    private final ServiceOrderRepository orderRepository;
    private final WorkFolderService workFolderService;
    private final CameraPreviewExtractor extractor;
    private final Map<Long, Progress> progress = new ConcurrentHashMap<>();

    public SelectionPreviewGenerator(ServiceOrderRepository orderRepository,
                                     WorkFolderService workFolderService,
                                     CameraPreviewExtractor extractor) {
        this.orderRepository = orderRepository;
        this.workFolderService = workFolderService;
        this.extractor = extractor;
    }

    @Nullable
    public Progress progress(Long orderId) {
        return progress.get(orderId);
    }

    public boolean isRunning(Long orderId) {
        Progress p = progress.get(orderId);
        return p != null && p.running();
    }

    /**
     * Genera las previas que falten (bloqueante: llamarlo desde un hilo propio).
     * Devuelve cuántas previas hay en total en "2 Seleccion" al terminar.
     */
    public int generate(Long orderId) throws IOException {
        LocalDateTime started = LocalDateTime.now();
        if (progress.putIfAbsent(orderId, new Progress(true, 0, 0, 0, 0, null, started, null)) != null) {
            Progress current = progress.get(orderId);
            if (current != null && current.running()) throw new IllegalStateException("Ya se están generando las previas.");
            progress.put(orderId, new Progress(true, 0, 0, 0, 0, null, started, null));
        }
        int created = 0;
        int failed = 0;
        int total = 0;
        int done = 0;
        try {
            ServiceOrder order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException("La orden no existe."));
            Path folder = workFolderService.resolve(order.getWorkFolder());
            if (folder == null) throw new IllegalStateException("La orden no tiene carpeta en el NAS.");
            Path camera = folder.resolve(SelectionService.CAMERA);
            Path previews = Files.createDirectories(folder.resolve(SelectionService.PREVIEWS));
            Set<String> existing = SelectionService.previews(folder).stream()
                    .map(SelectionService::baseName).collect(Collectors.toSet());

            // Una entrada por nombre base: JPG de cámara si hay, si no el RAW.
            Map<String, Path> sources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Path file : cameraFiles(camera)) {
                String name = file.getFileName().toString();
                String base = SelectionService.baseName(name);
                if (existing.contains(base)) continue;
                Path current = sources.get(base);
                if (current == null || (isJpeg(file) && !isJpeg(current))) sources.put(base, file);
            }
            total = sources.size();
            progress.put(orderId, new Progress(true, total, 0, 0, 0, null, started, null));
            if (total == 0) return SelectionService.previews(folder).size();

            Path work = Files.createDirectories(previews.resolve(WORK_DIR));
            try {
                List<Path> raws = sources.values().stream().filter(p -> !isJpeg(p)).toList();
                List<Path> jpegs = sources.values().stream().filter(SelectionPreviewGenerator::isJpeg).toList();
                Map<Path, CameraPreviewExtractor.Extracted> extracted = extractor.extract(raws, work);
                Map<Path, Integer> jpegOrientation = extractor.orientations(jpegs, work);

                for (Map.Entry<String, Path> e : sources.entrySet()) {
                    Path src = e.getValue();
                    Path jpeg;
                    int orientation;
                    if (isJpeg(src)) {
                        jpeg = src;
                        orientation = jpegOrientation.getOrDefault(src, 1);
                    } else {
                        CameraPreviewExtractor.Extracted x = extracted.get(src);
                        jpeg = x == null ? null : x.jpeg();
                        orientation = x == null ? 1 : x.orientation();
                    }
                    String targetName = stripExtension(src.getFileName().toString()) + ".jpg";
                    if (jpeg != null && write(jpeg, orientation, previews.resolve(targetName), work)) {
                        created++;
                    } else {
                        failed++;
                        log.warn("Sin previa para {} ({})", src, order.getOrderNumber());
                    }
                    done++;
                    progress.put(orderId, new Progress(true, total, done, created, failed, null, started, null));
                }
            } finally {
                deleteTree(work);
            }
            log.info("Previas de {}: {} creadas, {} sin previa", order.getOrderNumber(), created, failed);
            return SelectionService.previews(folder).size();
        } catch (IOException | RuntimeException e) {
            progress.put(orderId, new Progress(false, total, done, created, failed, e.getMessage(), started, LocalDateTime.now()));
            throw e;
        } finally {
            Progress p = progress.get(orderId);
            if (p != null && p.running()) {
                progress.put(orderId, new Progress(false, p.total(), p.done(), p.created(), p.failed(),
                        failed > 0 ? failed + " foto(s) sin previa (¿archivo dañado o formato no soportado?)" : null,
                        started, LocalDateTime.now()));
            }
        }
    }

    /** Fotos de "1 Camara" (todas las subcarpetas), sin miniaturas de DSM, ocultos ni papelera. */
    static List<Path> cameraFiles(Path camera) throws IOException {
        if (!Files.isDirectory(camera)) return List.of();
        try (Stream<Path> paths = Files.walk(camera)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = camera.relativize(p).toString();
                        return !s.contains("@eaDir") && !s.contains("#recycle") && !s.startsWith(".") && !s.contains("/.");
                    })
                    .filter(p -> {
                        String ext = extension(p.getFileName().toString());
                        return JPEG_EXT.contains(ext) || CameraPreviewExtractor.RAW_EXT.contains(ext);
                    })
                    .sorted()
                    .toList();
        }
    }

    private static boolean write(Path jpeg, int orientation, Path target, Path work) {
        try {
            PreviewImageService.ONE_AT_A_TIME.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            BufferedImage img = PreviewImageService.readSubsampled(jpeg, MAX_SIDE);
            if (img == null) return false;
            BufferedImage out = PreviewImageService.orient(PreviewImageService.scale(img, MAX_SIDE), orientation);
            Path tmp = work.resolve(UUID.randomUUID() + ".jpg");
            PreviewImageService.writeJpeg(out, tmp, 0.88f);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo crear la previa {}: {}", target, e.toString());
            return false;
        } finally {
            PreviewImageService.ONE_AT_A_TIME.release();
        }
    }

    private static boolean isJpeg(Path p) {
        return JPEG_EXT.contains(extension(p.getFileName().toString()));
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // queda oculto; se limpia en la próxima generación
                }
            });
        } catch (IOException ignored) {
            // idem
        }
    }
}
