package com.happydev.prestockbackend.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface FileStorageService {
    void init() throws IOException; // Inicializa el directorio de uploads
    String store(MultipartFile file) throws IOException; // Guarda un archivo y devuelve su nombre
    Stream<Path> loadAll();      // Lista todos los archivos (opcional)
    Path load(String filename);   // Carga la ruta de un archivo
    Resource loadAsResource(String filename) throws IOException; // Carga un archivo como un Resource
    void deleteAll();           // Elimina todos los archivos (¡cuidado!)
    void delete(String filename) throws  IOException; //Eliminar un archivo
}
