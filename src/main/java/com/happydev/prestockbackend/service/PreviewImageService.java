package com.happydev.prestockbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * Versiones reducidas y con marca de agua de las previas del portal de selección,
 * guardadas junto a ellas en carpetas ocultas de "2 Seleccion" (no cuentan como material).
 *
 * <p>El backend corre con poca memoria (NAS de 1,75 GB): se decodifica con submuestreo
 * (nunca la imagen completa) y de a una a la vez. Si no se puede generar la versión
 * reducida, falla: nunca se entrega el original al cliente.
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

    /**
     * Ruta de la versión reducida, generándola si falta o si la previa cambió.
     *
     * @param watermark texto de la marca de agua; null o vacío = sin marca
     * @throws IOException si no se pudo generar (el llamador no debe servir el original)
     */
    public Path resized(Path original, Size size, @Nullable String watermark) throws IOException {
        String mark = watermark == null ? "" : watermark.trim();
        // La carpeta depende del texto de la marca: si cambia, se regeneran todas.
        String dir = size.cacheDir + (mark.isEmpty() ? "" : "-" + Integer.toHexString(mark.hashCode()));
        Path cached = original.getParent().resolve(dir).resolve(original.getFileName() + ".jpg");
        if (isFresh(cached, original)) return cached;
        try {
            ONE_AT_A_TIME.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido", e);
        }
        try {
            if (isFresh(cached, original)) return cached; // otro hilo la generó mientras esperaba
            BufferedImage img = readSubsampled(original, size.maxSide);
            if (img == null) throw new IOException("Formato de imagen no soportado: " + original.getFileName());
            BufferedImage out = scale(img, size.maxSide);
            if (!mark.isEmpty()) watermark(out, mark, size);
            Files.createDirectories(cached.getParent());
            Path tmp = cached.resolveSibling(cached.getFileName() + ".tmp");
            writeJpeg(out, tmp, size == Size.THUMB ? 0.8f : 0.85f);
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return cached;
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo reducir {}: {}", original, e.toString());
            throw e instanceof IOException io ? io : new IOException(e);
        } finally {
            ONE_AT_A_TIME.release();
        }
    }

    private static boolean isFresh(Path cached, Path original) throws IOException {
        return Files.exists(cached)
                && Files.getLastModifiedTime(cached).compareTo(Files.getLastModifiedTime(original)) >= 0;
    }

    /** Decodifica saltando píxeles para quedar cerca del tamaño pedido sin cargar la imagen completa. */
    @Nullable
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

    /**
     * Texto del estudio repetido en diagonal, tenue: deja ver la foto para elegir pero
     * la inutiliza como entrega. En miniaturas, una sola vez y más pequeño.
     */
    static void watermark(BufferedImage img, String text, Size size) {
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = img.getWidth();
            int h = img.getHeight();
            int fontSize = Math.max(12, Math.min(w, h) / (size == Size.THUMB ? 9 : 14));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text);

            AffineTransform base = g.getTransform();
            g.rotate(Math.toRadians(-30), w / 2.0, h / 2.0);
            int stepX = tw + fontSize * 3;
            int stepY = fontSize * 5;
            int diag = (int) Math.hypot(w, h);
            for (int y = -diag / 2; y < h + diag / 2; y += stepY) {
                int offset = ((y / stepY) % 2 == 0) ? 0 : stepX / 2;
                for (int x = -diag / 2 - offset; x < w + diag / 2; x += stepX) {
                    // Contorno oscuro + relleno claro: visible en fotos claras y oscuras
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
                    g.setColor(Color.BLACK);
                    g.drawString(text, x + 1, y + 1);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f));
                    g.setColor(Color.WHITE);
                    g.drawString(text, x, y);
                }
            }
            g.setTransform(base);
        } finally {
            g.dispose();
        }
    }

    private static void writeJpeg(BufferedImage img, Path target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), p);
        } finally {
            writer.dispose();
        }
    }
}
