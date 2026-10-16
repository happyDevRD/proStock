package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SelectionDtos.PublicSelection;
import com.happydev.prestockbackend.dto.SelectionDtos.SelectionSubmit;
import com.happydev.prestockbackend.dto.SelectionDtos.SelectionUpdate;
import com.happydev.prestockbackend.service.PreviewImageService;
import com.happydev.prestockbackend.service.SelectionService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Portal de selección de fotos para clientes (público, sin sesión). El acceso es el
 * token del enlace (32 bytes aleatorios). Solo esta ruta se expone a internet, a través
 * del nginx de iridiscencia.com ({@code /api/seleccion/} → {@code /api/public/selection/}).
 */
@RestController
@RequestMapping("/api/public/selection/{token}")
public class PublicSelectionController {

    private final SelectionService selectionService;
    private final PreviewImageService imageService;

    public PublicSelectionController(SelectionService selectionService, PreviewImageService imageService) {
        this.selectionService = selectionService;
        this.imageService = imageService;
    }

    @GetMapping
    public ResponseEntity<PublicSelection> view(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(selectionService.view(token));
    }

    /** {@code size}: thumb (cuadrícula) o view (ampliada). */
    @GetMapping("/photos/{name:.+}")
    public ResponseEntity<Resource> photo(@PathVariable String token,
                                          @PathVariable String name,
                                          @RequestParam(defaultValue = "thumb") String size) {
        Path original = selectionService.preview(token, name);
        Path file = imageService.resized(original,
                "view".equals(size) ? PreviewImageService.Size.VIEW : PreviewImageService.Size.THUMB);
        MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString()).orElse(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .contentType(type)
                .body(new FileSystemResource(file));
    }

    @PutMapping("/selected")
    public ResponseEntity<PublicSelection> save(@PathVariable String token, @Valid @RequestBody SelectionUpdate body) {
        return ResponseEntity.ok(selectionService.saveDraft(token, body.selected()));
    }

    @PostMapping("/submit")
    public ResponseEntity<PublicSelection> submit(@PathVariable String token, @Valid @RequestBody SelectionSubmit body) {
        return ResponseEntity.ok(selectionService.submit(token, body.selected(), body.acceptExtra()));
    }
}
