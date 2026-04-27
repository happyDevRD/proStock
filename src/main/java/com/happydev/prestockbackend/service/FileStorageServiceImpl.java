package com.happydev.prestockbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

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