package com.happydev.prestockbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path rootLocation; // La ruta base para los uploads

    // Inyecta la ruta desde application.properties (o .yml)
    public FileStorageServiceImpl(@Value("${storage.location}") String location) {
        this.rootLocation = Paths.get(location);
    }

    @PostConstruct
    public void ensureStorageReady() {
        try {
            init();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de almacenamiento: " + rootLocation, e);
        }
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(rootLocation); // Crea el directorio si no existe
    }

    @Override
    public String store(@NonNull MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Cannot store file with null original filename");
        }

        // Generar un nombre de archivo único (para evitar colisiones)
        String filename = UUID.randomUUID() + "_" + originalFilename;

        Path destinationFile = this.rootLocation.resolve(
                        Paths.get(filename))
                .normalize().toAbsolutePath(); // Ruta absoluta del archivo
        if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
            // Verificación de seguridad:  Asegurarse de que el archivo se guarde dentro del directorio de uploads
            throw new IllegalStateException("Cannot store file outside current directory.");
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile,
                    StandardCopyOption.REPLACE_EXISTING); // Guarda el archivo (reemplaza si existe)
        }
        return filename; // Devuelve el nombre del archivo generado
    }

    @Override
    public String storeCompanyLogo(@NonNull MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Archivo vacío.");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new IllegalArgumentException("Solo se admiten archivos de imagen (image/*).");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Nombre de archivo inválido.");
        }
        String base = sanitizeLogoBasename(originalFilename);
        if (base.isEmpty()) {
            base = "logo.png";
        }
        Path destinationFile = this.rootLocation.resolve(base).normalize().toAbsolutePath();
        if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
            throw new IllegalStateException("Nombre de archivo no permitido.");
        }
        String filename = base;
        if (Files.exists(destinationFile)) {
            int dot = base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String ext = dot > 0 ? base.substring(dot) : "";
            filename = stem + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
            destinationFile = this.rootLocation.resolve(filename).normalize().toAbsolutePath();
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return filename;
    }

    private static String sanitizeLogoBasename(String original) {
        String n = original.replace("\\", "/");
        int slash = n.lastIndexOf('/');
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (n.length() > 120) {
            n = n.substring(n.length() - 120);
        }
        if (n.isEmpty() || ".".equals(n) || "..".equals(n)) {
            return "";
        }
        return n;
    }

    @Override
    public Stream<Path> loadAll() {
        try{
            return Files.walk(this.rootLocation, 1) // 1 nivel de profundidad
                    .filter(path -> !path.equals(this.rootLocation))//Que no sea el root
                    .map(this.rootLocation::relativize); //Obtiene la ruta relativa
        } catch (IOException e){
            throw new RuntimeException("Failed to load stored files", e);
        }
    }

    @Override
    public Path load(@NonNull String filename) {
        return rootLocation.resolve(filename); // Construye la ruta completa
    }

    @Override
    public Resource loadAsResource(@NonNull String filename) throws IOException {
        Path file = load(filename); //Obtiene la ruta
        URI fileUri = Objects.requireNonNull(file.toUri(), "File URI cannot be null");
        Resource resource = new UrlResource(fileUri); //Crea el recurso
        if (resource.exists() || resource.isReadable()) { //Verifica
            return resource;
        } else {
            throw new MalformedURLException( //FileNotFoundException
                    "Could not read file: " + filename);
        }
    }

    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile()); // ¡Cuidado! Elimina todo
    }

    @Override
    public void delete(@NonNull String filename) throws IOException {
        Path file = load(filename);
        Files.deleteIfExists(file); // Elimina el archivo si existe
    }
}