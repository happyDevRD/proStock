package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SelectionDtos.LoginRequest;
import com.happydev.prestockbackend.dto.SelectionDtos.LoginResponse;
import com.happydev.prestockbackend.dto.SelectionDtos.PublicSelection;
import com.happydev.prestockbackend.dto.SelectionDtos.SelectionSubmit;
import com.happydev.prestockbackend.dto.SelectionDtos.SelectionUpdate;
import com.happydev.prestockbackend.service.PreviewImageService;
import com.happydev.prestockbackend.service.SelectionAccessService;
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
 * Portal de selección de fotos para clientes. El cliente entra con usuario y contraseña
 * ({@code POST /login}) y recibe una sesión firmada que va en la ruta de las demás llamadas
 * (también en las {@code <img>}). Solo esta ruta se expone a internet, a través del nginx
 * de iridiscencia.com ({@code /api/seleccion/} → {@code /api/public/selection/}).
 */
@RestController
@RequestMapping("/api/public/selection")
public class PublicSelectionController {

    private final SelectionService selectionService;
    private final PreviewImageService imageService;
    private final SelectionAccessService access;

    public PublicSelectionController(SelectionService selectionService, PreviewImageService imageService,
                                     SelectionAccessService access) {
        this.selectionService = selectionService;
        this.imageService = imageService;
        this.access = access;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new LoginResponse(access.login(body.username(), body.password())));
    }

    @GetMapping("/{session}")
    public ResponseEntity<PublicSelection> view(@PathVariable String session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(selectionService.view(access.resolve(session)));
    }

    /** {@code size}: thumb (cuadrícula) o view (ampliada). */
    @GetMapping("/{session}/photos/{name:.+}")
    public ResponseEntity<Resource> photo(@PathVariable String session,
                                          @PathVariable String name,
                                          @RequestParam(defaultValue = "thumb") String size) {
        Path original = selectionService.preview(access.resolve(session), name);
        Path file;
        try {
            file = imageService.resized(original,
                    "view".equals(size) ? PreviewImageService.Size.VIEW : PreviewImageService.Size.THUMB,
                    selectionService.watermarkText());
        } catch (java.io.IOException e) {
            // Nunca se entrega el original: sin versión reducida, no hay foto.
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString()).orElse(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .contentType(type)
                .body(new FileSystemResource(file));
    }

    @PutMapping("/{session}/selected")
    public ResponseEntity<PublicSelection> save(@PathVariable String session, @Valid @RequestBody SelectionUpdate body) {
        return ResponseEntity.ok(selectionService.saveDraft(access.resolve(session), body.selected()));
    }

    @PostMapping("/{session}/submit")
    public ResponseEntity<PublicSelection> submit(@PathVariable String session, @Valid @RequestBody SelectionSubmit body) {
        return ResponseEntity.ok(selectionService.submit(access.resolve(session), body.selected(), body.acceptExtra()));
    }
}
