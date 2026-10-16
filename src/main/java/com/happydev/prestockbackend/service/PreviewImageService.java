package com.happydev.prestockbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * Versiones reducidas de las previas para el portal de selección, guardadas junto a
 * ellas en "2 Seleccion/.miniaturas" (carpeta oculta: no cuenta como material).
 *
 * <p>El backend corre con poca memoria (NAS de 1,75 GB): se decodifica con submuestreo
 * (nunca la imagen completa) y de a una a la vez.
 */
@Service
public class PreviewImageService {

    private static final Logger log = LoggerFactory.getLogger(PreviewImageService.class);
    private static final Semaphore ONE_AT_A_TIME = new Semaphore(1);

    public enum Size {
        THUMB(480, ".miniaturas"),
        VIEW(1600, ".vista");

        final int maxSide;
        final String cacheDir;

        Size(int maxSide, String cacheDir) {
            this.maxSide = maxSide;
            this.cacheDir = cacheDir;
        }
    }

    /** Ruta de la versión reducida (la genera si falta); si no se puede, la original. */
    public Path resized(Path original, Size size) {
        Path cached = original.getParent().resolve(size.cacheDir).resolve(original.getFileName() + ".jpg");
        try {
            if (Files.exists(cached) && Files.getLastModifiedTime(cached).compareTo(Files.getLastModifiedTime(original)) >= 0) {
                return cached;
            }
            ONE_AT_A_TIME.acquire();
            try {
                if (!Files.exists(cached)) {
                    BufferedImage img = readSubsampled(original, size.maxSide);
                    if (img == null) return original;
                    Files.createDirectories(cached.getParent());
                    Path tmp = cached.resolveSibling(cached.getFileName() + ".tmp");
                    ImageIO.write(scale(img, size.maxSide), "jpg", tmp.toFile());
                    Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            } finally {
                ONE_AT_A_TIME.release();
            }
            return cached;
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo reducir {}: {}", original, e.getMessage());
            return original;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return original;
        }
    }

    /** Decodifica saltando píxeles para quedar cerca del tamaño pedido sin cargar la imagen completa. */
    private static BufferedImage readSubsampled(Path file, int maxSide) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                int step = Math.max(1, Math.max(w, h) / (maxSide * 2));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scale(BufferedImage src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        double f = Math.min(1.0, (double) maxSide / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * f));
        int nh = Math.max(1, (int) Math.round(h * f));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
