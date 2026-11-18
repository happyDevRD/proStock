package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderSelection;
import com.happydev.prestockbackend.repository.ServiceOrderSelectionRepository;
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
 * <p>Al entrar se entrega una sesión firmada (HMAC) que va en la ruta de las llamadas
 * (también en las {@code <img>}): {@code base64(token interno|vence).firma}. La firma
 * incluye el hash de la contraseña: al cambiarla, las sesiones anteriores dejan de servir.
 */
@Service
public class SelectionAccessService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK = Duration.ofMinutes(15);
    static final Duration SESSION = Duration.ofHours(12);
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ResponseStatusException.class)
    public String login(String username, String password) {
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
        sel.setFailedAttempts(0);
        sel.setLockedUntil(null);
        sel.setLastLoginAt(now);
        repository.save(sel);
        return session(sel);
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
