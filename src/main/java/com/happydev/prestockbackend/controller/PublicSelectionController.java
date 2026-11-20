package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SelectionDtos.AccessRequest;
import com.happydev.prestockbackend.dto.SelectionDtos.LoginRequest;
import com.happydev.prestockbackend.dto.SelectionDtos.LoginResponse;
import com.happydev.prestockbackend.dto.SelectionDtos.PublicSelection;
import com.happydev.prestockbackend.dto.SelectionDtos.SelectionSubmit;
import com.happydev.prestockbackend.dto.SelectionDtos.SelectionUpdate;
import com.happydev.prestockbackend.service.PreviewImageService;
import com.happydev.prestockbackend.service.SelectionAccessService;
import com.happydev.prestockbackend.service.SelectionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Portal de selección de fotos para clientes. Solo esta ruta se expone a internet, a través del
 * nginx de iridiscencia.com ({@code /api/seleccion/} → {@code /api/public/selection/}).
 *
 * <p>Se entra con el enlace directo del WhatsApp ({@code POST /acceso}) o con usuario y contraseña
 * ({@code POST /login}). En ambos casos la sesión firmada va en una cookie HttpOnly
 * ({@value #COOKIE}) y las demás llamadas usan {@code /me/...}: la sesión no aparece en ninguna URL
 * (ni en las {@code <img>}), así no queda en historiales ni en registros.
 *
 * <p>Las rutas antiguas con la sesión en la ruta ({@code /{session}/...}) siguen funcionando para
 * las pestañas que ya estaban abiertas al desplegar; se pueden quitar más adelante.
 */
@RestController
@RequestMapping("/api/public/selection")
public class PublicSelectionController {

    static final String COOKIE = "sel_session";

    private final SelectionService selectionService;
    private final PreviewImageService imageService;
    private final SelectionAccessService access;
    private final String cookiePath;
    private final boolean cookieSecure;

    public PublicSelectionController(SelectionService selectionService, PreviewImageService imageService,
                                     SelectionAccessService access,
                                     @Value("${app.selection.cookie-path:/api/seleccion}") String cookiePath,
                                     @Value("${app.selection.cookie-secure:true}") boolean cookieSecure) {
        this.selectionService = selectionService;
        this.imageService = imageService;
        this.access = access;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure;
    }

    // ── Entrar / salir ─────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body,
                                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withSession(access.login(body.username(), body.password(), userAgent));
    }

    /** Enlace directo: el portal manda la llave de la ruta y recibe la cookie. */
    @PostMapping("/acceso")
    public ResponseEntity<LoginResponse> accessWithKey(@Valid @RequestBody AccessRequest body,
                                                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withSession(access.loginWithKey(body.key(), userAgent));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    private ResponseEntity<LoginResponse> withSession(SelectionAccessService.Login login) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(login.session(), SelectionAccessService.SESSION).toString())
                .body(new LoginResponse(login.session(), login.username()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(maxAge)
                .build();
    }

    // ── Con la cookie ──────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<PublicSelection> viewMe(@CookieValue(value = COOKIE, required = false) String session) {
        return view(requireCookie(session));
    }

    @GetMapping("/me/photos/{name:.+}")
    public ResponseEntity<Resource> photoMe(@CookieValue(value = COOKIE, required = false) String session,
                                            @PathVariable String name,
                                            @RequestParam(defaultValue = "thumb") String size) {
        return photo(requireCookie(session), name, size);
    }

    @PutMapping("/me/selected")
    public ResponseEntity<PublicSelection> saveMe(@CookieValue(value = COOKIE, required = false) String session,
                                                  @Valid @RequestBody SelectionUpdate body) {
        return save(requireCookie(session), body);
    }

    @PostMapping("/me/submit")
    public ResponseEntity<PublicSelection> submitMe(@CookieValue(value = COOKIE, required = false) String session,
                                                    @Valid @RequestBody SelectionSubmit body) {
        return submit(requireCookie(session), body);
    }

    private static String requireCookie(String session) {
        if (session == null || session.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entra con el enlace que te enviamos o con tu usuario y contraseña.");
        }
        return session;
    }

    // ── Rutas antiguas (sesión en la ruta) ─────────────────────────────────

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
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
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
