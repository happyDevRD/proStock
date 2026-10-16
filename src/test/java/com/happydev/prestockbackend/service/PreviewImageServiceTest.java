package com.happydev.prestockbackend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PreviewImageServiceTest {

    @TempDir Path dir;
    private final PreviewImageService service = new PreviewImageService();

    private Path photo(String name, Color color) throws IOException {
        BufferedImage img = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 3000, 2000);
        g.dispose();
        Path p = dir.resolve(name);
        ImageIO.write(img, "jpg", p.toFile());
        return p;
    }

    @Test
    void reducesAndWatermarks() throws Exception {
        Path original = photo("IMG_0001.jpg", new Color(120, 150, 180));

        Path view = service.resized(original, PreviewImageService.Size.VIEW, "Iridiscencia");
        Path plain = service.resized(original, PreviewImageService.Size.VIEW, null);

        assertNotEquals(original, view, "nunca el original");
        BufferedImage v = ImageIO.read(view.toFile());
        assertEquals(1600, Math.max(v.getWidth(), v.getHeight()));
        assertTrue(view.getParent().getFileName().toString().startsWith(".vista-"), "carpeta oculta por marca");
        assertNotEquals(Files.size(plain), Files.size(view), "la marca cambia la imagen");
    }

    @Test
    void regeneratesWhenThePreviewIsReplaced() throws Exception {
        Path original = photo("IMG_0002.jpg", Color.DARK_GRAY);
        Path thumb = service.resized(original, PreviewImageService.Size.THUMB, "X");
        Files.setLastModifiedTime(thumb, FileTime.from(Instant.now().minusSeconds(3600)));
        photo("IMG_0002.jpg", Color.WHITE); // el fotógrafo reexporta la previa

        Path again = service.resized(original, PreviewImageService.Size.THUMB, "X");
        BufferedImage t = ImageIO.read(again.toFile());
        assertTrue(new Color(t.getRGB(2, 2)).getRed() > 200, "la miniatura refleja la previa nueva");
    }

    @Test
    void unreadableFileFailsInsteadOfServingTheOriginal() throws Exception {
        Path bad = dir.resolve("IMG_0003.jpg");
        Files.writeString(bad, "no es una imagen");
        assertThrows(IOException.class, () -> service.resized(bad, PreviewImageService.Size.VIEW, "X"));
    }
}
