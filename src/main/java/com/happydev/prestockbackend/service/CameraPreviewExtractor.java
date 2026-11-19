package com.happydev.prestockbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Saca de los archivos de cámara la JPG con que se arman las previas del portal.
 *
 * <p>Los RAW (Sony ARW, Canon CR3/CR2, Nikon NEF, …) llevan dentro una JPG de vista previa
 * (~1600 px): exiftool la extrae sin revelar el RAW, rápido y con poca memoria, que es lo
 * que aguanta el NAS. Se usa {@code PreviewImage} y, si no existe, {@code JpgFromRaw}.
 * Una sola llamada a exiftool por carpeta (arrancar perl por foto sería lentísimo en el NAS).
 */
@Component
public class CameraPreviewExtractor {

    private static final Logger log = LoggerFactory.getLogger(CameraPreviewExtractor.class);

    public static final List<String> RAW_EXT = List.of("arw", "cr3", "cr2", "nef", "nrw", "raf", "dng", "orf", "rw2", "srw", "pef");

    /** Orientación EXIF (1 = normal, 6 = girar 90° a la derecha, 8 = a la izquierda, 3 = 180°). */
    public record Extracted(Path jpeg, int orientation) {}

    private final String exiftool;

    public CameraPreviewExtractor(@Value("${app.selection.exiftool:exiftool}") String exiftool) {
        this.exiftool = exiftool;
    }

    /**
     * Extrae a {@code outDir} la vista previa de cada RAW de {@code raws} (rutas absolutas).
     * Devuelve, por cada RAW que la tenía, la JPG extraída y su orientación. Los que no se
     * pudieron extraer no aparecen (el llamador los reporta).
     */
    public Map<Path, Extracted> extract(List<Path> raws, Path outDir) throws IOException {
        Map<Path, Extracted> result = new HashMap<>();
        if (raws.isEmpty()) return result;
        Files.createDirectories(outDir);
        Map<Path, Integer> orientation = orientations(raws, outDir);
        Map<Path, Path> targets = new HashMap<>();
        for (int i = 0; i < raws.size(); i++) {
            targets.put(raws.get(i), outDir.resolve(i + ".jpg"));
        }
        for (String tag : List.of("PreviewImage", "JpgFromRaw")) {
            List<Path> pending = raws.stream().filter(r -> !isJpeg(targets.get(r))).toList();
            if (pending.isEmpty()) break;
            // Un comando por RAW (-execute) dentro de una sola llamada: -W fija el nombre de salida.
            List<String> args = new ArrayList<>();
            for (Path raw : pending) {
                args.add("-W");
                args.add(targets.get(raw).toString().replace("%", "%%"));
                args.add(raw.toString());
                args.add("-execute");
            }
            run(args, outDir, "-q", "-q", "-b", "-" + tag);
        }
        for (Path raw : raws) {
            Path jpg = targets.get(raw);
            if (isJpeg(jpg)) {
                result.put(raw, new Extracted(jpg, orientation.getOrDefault(raw, 1)));
            } else {
                Files.deleteIfExists(jpg);
            }
        }
        return result;
    }

    /** Orientación EXIF de cada archivo (RAW o JPG); 1 si no la tiene. */
    public Map<Path, Integer> orientations(List<Path> files, Path workDir) throws IOException {
        Map<Path, Integer> result = new HashMap<>();
        if (files.isEmpty()) return result;
        List<String> args = new ArrayList<>();
        files.forEach(f -> args.add(f.toString()));
        String out = run(args, workDir, "-q", "-q", "-n", "-T", "-FilePath", "-Orientation");
        for (String line : out.split("\\R")) {
            int tab = line.lastIndexOf('\t');
            if (tab <= 0) continue;
            String value = line.substring(tab + 1).trim();
            if (value.matches("[1-8]")) result.put(Path.of(line.substring(0, tab)), Integer.parseInt(value));
        }
        return result;
    }

    /**
     * exiftool sale con 0, o con 1 si algún archivo dio error (lo esperable: esos quedan
     * sin previa). 128+ es una señal: el kernel lo mató, casi siempre por memoria. Seguir
     * con la salida a medias dejaba las previas sin girar (todas horizontales).
     */
    static void checkExit(int code) throws IOException {
        if (code >= 128) {
            throw new IOException("exiftool se interrumpió (código " + code
                    + "; probablemente el servidor se quedó sin memoria). Vuelve a pulsar «Generar previas».");
        }
        if (code > 1) throw new IOException("exiftool falló (código " + code + ")");
    }

    private String run(List<String> args, Path workDir, String... common) throws IOException {
        // Los argumentos van por archivo (-@): con cientos de fotos la línea de comandos se pasa del límite.
        Path argfile = Files.createTempFile(workDir, ".exiftool-", ".args");
        try {
            Files.write(argfile, args, StandardCharsets.UTF_8);
            List<String> cmd = new ArrayList<>(List.of(exiftool, "-@", argfile.toString(), "-common_args", "-charset", "filename=utf8"));
            cmd.addAll(List.of(common));
            Process p = new ProcessBuilder(cmd)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(30, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IOException("exiftool tardó demasiado");
            }
            checkExit(p.exitValue());
            return new String(out, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido", e);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("error=2")) {
                // error=2 (ENOENT) también sale si la carpeta de trabajo ya no existe.
                if (!Files.isDirectory(workDir)) {
                    throw new IOException("la carpeta de trabajo desapareció (¿se movió la carpeta de la orden?)", e);
                }
                throw new IOException("exiftool no está instalado en el servidor", e);
            }
            throw e;
        } finally {
            Files.deleteIfExists(argfile);
        }
    }

    /** JPG válida: empieza con FFD8 y no está vacía. */
    static boolean isJpeg(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file) || Files.size(file) < 1024) return false;
            try (var in = Files.newInputStream(file)) {
                return in.read() == 0xFF && in.read() == 0xD8;
            }
        } catch (IOException e) {
            log.debug("No se pudo leer {}: {}", file, e.getMessage());
            return false;
        }
    }
}
