package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderSelection;
import com.happydev.prestockbackend.repository.ServiceOrderSelectionRepository;
import org.springframework.lang.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

/**
 * Acceso del cliente al portal de selección con usuario y contraseña.
 *
 * <p>Usuario: primer nombre + número de la orden ("nathalie13"). Contraseña: 6 dígitos
 * aleatorios, guardada con BCrypt (en claro solo se muestra al generarla). Tras
 * {@value #MAX_ATTEMPTS} intentos fallidos el acceso se bloquea {@link #LOCK} minutos.
 *
 * <p><b>Enlace directo</b> (botón del WhatsApp): {@code k_<id>_<firma>}, firma HMAC del id y del
 * token interno. No se guarda en ningún sitio: se recalcula, y deja de servir al regenerar el
 * acceso (token nuevo), al vencer, al revocarlo o cuando la orden se entrega o se cancela.
 * El usuario y la contraseña quedan de respaldo (otro dispositivo, enlace vencido).
 *
 * <p>Al entrar se entrega una sesión firmada (HMAC), {@code base64(token interno|vence).firma},
 * que el portal guarda en una cookie HttpOnly. La firma incluye el hash de la contraseña: al
 * cambiarla, las sesiones anteriores dejan de servir.
 */
@Service
public class SelectionAccessService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK = Duration.ofMinutes(15);
    public static final Duration SESSION = Duration.ofHours(12);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final ServiceOrderSelectionRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final byte[] secret;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SelectionAccessService(ServiceOrderSelectionRepository repository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${app.security.jwt.secret}") String secret) {
        this(repository, passwordEncoder, secret, Clock.systemDefaultZone());
    }

    SelectionAccessService(ServiceOrderSelectionRepository repository, PasswordEncoder passwordEncoder,
                           String secret, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.secret = ("selection:" + secret).getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public record Credentials(String username, String password) {}

    /** Resultado de entrar: sesión para la cookie y usuario (para la ruta /seleccion/{usuario}). */
    public record Login(String session, String username) {}

    public static final String METHOD_LINK = "ENLACE";
    public static final String METHOD_PASSWORD = "CONTRASENA";
    private static final String KEY_PREFIX = "k_";

    private StudioNotifier notifier;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNotifier(StudioNotifier notifier) {
        this.notifier = notifier;
    }

    /** Llave del enlace directo de esta selección ({@code k_<id>_<firma>}). */
    public String accessKey(ServiceOrderSelection sel) {
        return KEY_PREFIX + sel.getId() + "_" + keySignature(sel.getId(), sel.getToken());
    }

    private String keySignature(Long id, String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(("access|" + id + "|" + token).getBytes(StandardCharsets.UTF_8));
            byte[] full = mac.doFinal();
            return B64.encodeToString(java.util.Arrays.copyOf(full, 24));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Entra con el enlace directo. Cualquier fallo responde igual (no revela si la selección existe),
     * salvo el vencimiento y la orden cerrada, que se explican al cliente.
     */
    @Transactional
    public Login loginWithKey(String key, @Nullable String userAgent) {
        String k = key == null ? "" : key.trim();
        ServiceOrderSelection sel = null;
        if (k.startsWith(KEY_PREFIX)) {
            int sep = k.indexOf('_', KEY_PREFIX.length());
            if (sep > KEY_PREFIX.length()) {
                try {
                    long id = Long.parseLong(k.substring(KEY_PREFIX.length(), sep));
                    sel = repository.findById(id).orElse(null);
                } catch (NumberFormatException ignored) {
                    sel = null;
                }
                if (sel != null) {
                    byte[] expected = accessKey(sel).getBytes(StandardCharsets.US_ASCII);
                    if (!MessageDigest.isEqual(expected, k.getBytes(StandardCharsets.US_ASCII))) sel = null;
                }
            }
        }
        if (sel == null || sel.getPasswordHash() == null) {
            throw unauthorized("Este enlace no es válido. Entra con tu usuario y contraseña o escribe al estudio.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (sel.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Tu acceso venció. Pide uno nuevo al estudio.");
        }
        if (closed(sel.getServiceOrder())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Esta selección ya se cerró porque la orden fue entregada o cancelada. Escribe al estudio si necesitas algo.");
        }
        return new Login(recordLogin(sel, METHOD_LINK, userAgent, now), sel.getUsername());
    }

    /** Orden entregada o cancelada: el enlace directo deja de servir. */
    static boolean closed(@Nullable ServiceOrder order) {
        if (order == null) return false;
        return "DELIVERED".equals(order.getCurrentStage())
                || order.getStatus() == com.happydev.prestockbackend.entity.ServiceOrderStatus.CANCELLED;
    }

    private String recordLogin(ServiceOrderSelection sel, String method, @Nullable String userAgent, LocalDateTime now) {
        boolean first = sel.getLastLoginAt() == null;
        String device = describeDevice(userAgent);
        sel.setFailedAttempts(0);
        sel.setLockedUntil(null);
        sel.setLastLoginAt(now);
        sel.setLastLoginMethod(method);
        sel.setLastLoginDevice(device);
        repository.save(sel);
        if (first && notifier != null) {
            ServiceOrder order = sel.getServiceOrder();
            String who = order != null && order.getCustomer() != null
                    ? (order.getCustomer().getFirstName() + " " + java.util.Objects.toString(order.getCustomer().getLastName(), "")).trim()
                    : "El cliente";
            notifier.send("👀 " + who + " abrió su selección de fotos"
                    + (order != null ? " (" + order.getOrderNumber() + ")" : "")
                    + " " + (METHOD_LINK.equals(method) ? "con el enlace del WhatsApp" : "con usuario y contraseña")
                    + (device != null ? " · " + device : "") + ".");
        }
        return session(sel);
    }

    /** "Mozilla/5.0 (iPhone; …) … Safari/…" → "iPhone · Safari". Sin User-Agent: null. */
    static @Nullable String describeDevice(@Nullable String ua) {
        if (ua == null || ua.isBlank()) return null;
        String u = ua.toLowerCase(Locale.ROOT);
        String os = u.contains("iphone") ? "iPhone"
                : u.contains("ipad") ? "iPad"
                : u.contains("android") ? "Android"
                : u.contains("windows") ? "Windows"
                : u.contains("mac os") || u.contains("macintosh") ? "Mac"
                : u.contains("linux") ? "Linux" : "Otro";
        String browser = u.contains("whatsapp") ? "WhatsApp"
                : u.contains("instagram") ? "Instagram"
                : u.contains("fban") || u.contains("fbav") ? "Facebook"
                : u.contains("edg/") ? "Edge"
                : u.contains("samsungbrowser") ? "Samsung Internet"
                : u.contains("crios") || (u.contains("chrome/") && !u.contains("chromium")) ? "Chrome"
                : u.contains("fxios") || u.contains("firefox/") ? "Firefox"
                : u.contains("safari/") ? "Safari" : "Navegador";
        String out = os + " · " + browser;
        return out.length() > 80 ? out.substring(0, 80) : out;
    }

    /** Asigna usuario (si no tiene) y una contraseña nueva; devuelve la contraseña en claro. */
    public Credentials assign(ServiceOrderSelection sel) {
        if (sel.getUsername() == null || sel.getUsername().isBlank()) {
            sel.setUsername(uniqueUsername(sel.getServiceOrder()));
        }
        String password = String.format("%06d", RANDOM.nextInt(1_000_000));
        sel.setPasswordHash(passwordEncoder.encode(password));
        sel.setFailedAttempts(0);
        sel.setLockedUntil(null);
        return new Credentials(sel.getUsername(), password);
    }

    /**
     * Comprueba usuario y contraseña y devuelve la sesión. Los intentos fallidos se guardan
     * aunque la llamada termine en error (transacción propia).
     */
    /** Atajo sin dispositivo (pruebas). Llamado desde otro bean pasa por el proxy transaccional. */
    public String login(String username, String password) {
        return login(username, password, null).session();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ResponseStatusException.class)
    public Login login(String username, String password, @Nullable String userAgent) {
        String user = username == null ? "" : username.trim();
        String pass = password == null ? "" : password.replaceAll("\\s+", "");
        ServiceOrderSelection sel = user.isEmpty() ? null : repository.findByUsernameIgnoreCase(user).orElse(null);
        if (sel == null || sel.getPasswordHash() == null) {
            // Mismo costo que una comprobación real: no revela qué usuarios existen.
            passwordEncoder.matches(pass, dummyHash());
            throw unauthorized("Usuario o contraseña incorrectos.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (sel.getLockedUntil() != null && sel.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, sel.getLockedUntil()).toMinutes() + 1);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Vuelve a probar en " + minutes + " minuto(s) o pide ayuda al estudio.");
        }
        if (!passwordEncoder.matches(pass, sel.getPasswordHash())) {
            int attempts = sel.getFailedAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                sel.setFailedAttempts(0);
                sel.setLockedUntil(now.plus(LOCK));
            } else {
                sel.setFailedAttempts(attempts);
            }
            repository.save(sel);
            throw unauthorized("Usuario o contraseña incorrectos.");
        }
        if (sel.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Tu acceso venció. Pide uno nuevo al estudio.");
        }
        return new Login(recordLogin(sel, METHOD_PASSWORD, userAgent, now), sel.getUsername());
    }

    /** Token interno de la selección a partir de la sesión; 401 si no es válida o venció. */
    @Transactional(readOnly = true)
    public String resolve(String session) {
        int dot = session == null ? -1 : session.lastIndexOf('.');
        if (dot <= 0) throw expired();
        String payload;
        try {
            payload = new String(B64D.decode(session.substring(0, dot)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw expired();
        }
        int bar = payload.lastIndexOf('|');
        if (bar <= 0) throw expired();
        String token = payload.substring(0, bar);
        long until;
        try {
            until = Long.parseLong(payload.substring(bar + 1));
        } catch (NumberFormatException e) {
            throw expired();
        }
        if (Instant.now(clock).getEpochSecond() > until) throw expired();
        ServiceOrderSelection sel = repository.findByToken(token).orElseThrow(this::expired);
        if (sel.getPasswordHash() == null) throw expired();
        byte[] expected = sign(payload, sel.getPasswordHash()).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, session.substring(dot + 1).getBytes(StandardCharsets.US_ASCII))) {
            throw expired();
        }
        return token;
    }

    String session(ServiceOrderSelection sel) {
        long until = Instant.now(clock).plus(SESSION).getEpochSecond();
        String payload = sel.getToken() + "|" + until;
        return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload, sel.getPasswordHash());
    }

    private String sign(String payload, String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(payload.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return B64.encodeToString(mac.doFinal(passwordHash.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** "Nathalie Félix" + OS-2026-00013 → "nathalie13" (sin tildes ni espacios); único. */
    String uniqueUsername(ServiceOrder order) {
        String first = order.getCustomer() != null && order.getCustomer().getFirstName() != null
                ? order.getCustomer().getFirstName().trim().split("\\s+")[0] : "";
        String name = Normalizer.normalize(first, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (name.isEmpty()) name = "cliente";
        if (name.length() > 20) name = name.substring(0, 20);
        String digits = order.getOrderNumber() == null ? "" : order.getOrderNumber().replaceAll(".*?(\\d+)$", "$1");
        String number = digits.matches("\\d+") ? String.valueOf(Long.parseLong(digits)) : String.valueOf(order.getId());
        String candidate = name + number;
        for (int i = 2; repository.existsByUsernameIgnoreCase(candidate); i++) {
            candidate = name + number + "-" + i;
        }
        return candidate;
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) dummyHash = passwordEncoder.encode("000000");
        return dummyHash;
    }

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private ResponseStatusException expired() {
        return unauthorized("Tu sesión terminó. Vuelve a entrar con tu usuario y contraseña.");
    }
}
