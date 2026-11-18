package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.repository.ServiceOrderItemRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SelectionPreviewGeneratorTest {

    @TempDir Path root;
    private Path folder;
    private SelectionPreviewGenerator generator;
    private final Map<String, Integer> rawOrientation = new HashMap<>();

    /** Simula exiftool: "extrae" de cada RAW una JPG apaisada de 1600×1000. */
    private final CameraPreviewExtractor fakeExiftool = new CameraPreviewExtractor("exiftool") {
        @Override
        public Map<Path, Extracted> extract(List<Path> raws, Path outDir) throws IOException {
            Map<Path, Extracted> out = new HashMap<>();
            for (Path raw : raws) {
                if (raw.getFileName().toString().startsWith("DANADA")) continue;
                Path jpg = outDir.resolve(raw.getFileName() + ".jpg");
                ImageIO.write(new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_RGB), "jpg", jpg.toFile());
                out.put(raw, new Extracted(jpg, rawOrientation.getOrDefault(raw.getFileName().toString(), 1)));
            }
            return out;
        }

        @Override
        public Map<Path, Integer> orientations(List<Path> files, Path workDir) {
            return Map.of();
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
        WorkFolderService folders = new WorkFolderService(orders, mock(ServiceOrderItemRepository.class), root.toString(), "");
        folder = Files.createDirectories(root.resolve("3 En seleccion/OS-2026-00013 Nathalie"));
        ServiceOrder order = new ServiceOrder();
        order.setId(13L);
        order.setOrderNumber("OS-2026-00013");
        order.setWorkFolder("3 En seleccion/OS-2026-00013 Nathalie");
        given(orders.findById(13L)).willReturn(Optional.of(order));
        generator = new SelectionPreviewGenerator(orders, folders, fakeExiftool);
    }

    @Test
    void buildsUprightPreviewsFromRawsAndKeepsManualOnes() throws Exception {
        Path cam = Files.createDirectories(folder.resolve("1 Camara/TARJETA 1"));
        Files.writeString(cam.resolve("DSC00001.ARW"), "raw");
        Files.writeString(cam.resolve("DSC00002.ARW"), "raw");
        Files.writeString(cam.resolve("DSC00003.ARW"), "raw");
        Files.writeString(cam.resolve("DANADA.ARW"), "raw");
        Files.createDirectories(cam.resolve("@eaDir/DSC00001.ARW"));
        Files.writeString(cam.resolve("@eaDir/DSC00001.ARW/SYNOPHOTO_THUMB_M.jpg"), "x");
        rawOrientation.put("DSC00002.ARW", 6); // foto vertical
        // El fotógrafo ya exportó a mano la 3: se respeta
        Path previews = Files.createDirectories(folder.resolve("2 Seleccion"));
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpg", previews.resolve("DSC00003.jpg").toFile());

        int total = generator.generate(13L);

        assertEquals(List.of("DSC00001.jpg", "DSC00002.jpg", "DSC00003.jpg"), SelectionService.previews(folder));
        assertEquals(3, total);
        BufferedImage landscape = ImageIO.read(previews.resolve("DSC00001.jpg").toFile());
        BufferedImage portrait = ImageIO.read(previews.resolve("DSC00002.jpg").toFile());
        assertEquals(1600, landscape.getWidth());
        assertEquals(1000, landscape.getHeight());
        assertEquals(1000, portrait.getWidth(), "orientación EXIF aplicada: queda vertical");
        assertEquals(1600, portrait.getHeight());
        assertEquals(10, ImageIO.read(previews.resolve("DSC00003.jpg").toFile()).getWidth(), "no pisa la exportada a mano");
        assertFalse(Files.exists(previews.resolve(SelectionPreviewGenerator.WORK_DIR)), "limpia la carpeta de trabajo");

        var progress = generator.progress(13L);
        assertFalse(progress.running());
        assertEquals(3, progress.total());
        assertEquals(2, progress.created());
        assertEquals(1, progress.failed(), "la dañada se reporta");
        assertNotNull(progress.error());
    }

    @Test
    void prefersTheCameraJpegWhenRawPlusJpeg() throws Exception {
        Path cam = Files.createDirectories(folder.resolve("1 Camara"));
        Files.writeString(cam.resolve("IMG_0001.CR3"), "raw");
        ImageIO.write(new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg", cam.resolve("IMG_0001.JPG").toFile());

        generator.generate(13L);

        BufferedImage p = ImageIO.read(folder.resolve("2 Seleccion/IMG_0001.jpg").toFile());
        assertEquals(SelectionPreviewGenerator.MAX_SIDE, p.getWidth(), "de la JPG de cámara, reducida a 2048 px");
    }

    @Test
    void withoutCameraPhotosThereIsNothingToDo() throws Exception {
        assertEquals(0, generator.generate(13L));
        assertEquals(0, generator.progress(13L).total());
    }

    @Test
    void orientRotatesAndMirrorsLikeExif() {
        BufferedImage src = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, 0xFF0000); // esquina superior izquierda roja
        BufferedImage r6 = PreviewImageService.orient(src, 6);
        assertEquals(2, r6.getWidth());
        assertEquals(4, r6.getHeight());
        assertEquals(0xFF0000, r6.getRGB(1, 0) & 0xFFFFFF, "90° a la derecha: la esquina va arriba a la derecha");
        BufferedImage r8 = PreviewImageService.orient(src, 8);
        assertEquals(0xFF0000, r8.getRGB(0, 3) & 0xFFFFFF, "90° a la izquierda: abajo a la izquierda");
        BufferedImage r3 = PreviewImageService.orient(src, 3);
        assertEquals(0xFF0000, r3.getRGB(3, 1) & 0xFFFFFF, "180°");
        assertSame(src, PreviewImageService.orient(src, 1));
    }
}
