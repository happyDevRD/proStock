package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderSelection;
import com.happydev.prestockbackend.repository.ServiceOrderSelectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelectionAccessServiceTest {

    @Mock private ServiceOrderSelectionRepository repository;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T15:00:00Z"));
    private SelectionAccessService access;
    private ServiceOrderSelection sel;
    private String password;

    @BeforeEach
    void setUp() {
        access = new SelectionAccessService(repository, new BCryptPasswordEncoder(4), "secreto-de-pruebas", clock);
        Customer c = new Customer();
        c.setFirstName("Nathalie Andrea");
        ServiceOrder order = new ServiceOrder();
        order.setId(13L);
        order.setOrderNumber("OS-2026-00013");
        order.setCustomer(c);
        sel = new ServiceOrderSelection();
        sel.setServiceOrder(order);
        sel.setToken("tok-interno");
        sel.setExpiresAt(LocalDateTime.now(clock).plusDays(15));
        password = access.assign(sel).password();
        given(repository.findByUsernameIgnoreCase(anyString())).willReturn(Optional.empty());
        given(repository.findByUsernameIgnoreCase("nathalie13")).willReturn(Optional.of(sel));
        given(repository.findByToken("tok-interno")).willReturn(Optional.of(sel));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void usernameIsFirstNameWithoutAccentsPlusOrderNumber() {
        assertEquals("nathalie13", sel.getUsername());
        assertTrue(password.matches("\\d{6}"));

        Customer c = new Customer();
        c.setFirstName("José Ángel");
        ServiceOrder o = new ServiceOrder();
        o.setOrderNumber("OS-2026-00105");
        o.setCustomer(c);
        given(repository.existsByUsernameIgnoreCase("jose105")).willReturn(true);
        assertEquals("jose105-2", access.uniqueUsername(o), "si ya existe, sufijo");
    }

    @Test
    void loginWithRightPasswordGivesASessionForThatSelection() {
        String session = access.login(" Nathalie13 ".trim().toLowerCase(), password.substring(0, 3) + " " + password.substring(3));
        assertEquals("tok-interno", access.resolve(session));
        assertNotNull(sel.getLastLoginAt());
    }

    @Test
    void wrongPasswordOrUnknownUserGive401WithTheSameMessage() {
        var bad = assertThrows(ResponseStatusException.class, () -> access.login("nathalie13", "000000".equals(password) ? "111111" : "000000"));
        var unknown = assertThrows(ResponseStatusException.class, () -> access.login("nadie1", "123456"));
        assertEquals(HttpStatus.UNAUTHORIZED, bad.getStatusCode());
        assertEquals(bad.getReason(), unknown.getReason(), "no revela qué usuarios existen");
        assertEquals(1, sel.getFailedAttempts());
    }

    @Test
    void fiveFailuresLockTheAccessEvenWithTheRightPassword() {
        String wrong = "000000".equals(password) ? "111111" : "000000";
        for (int i = 0; i < SelectionAccessService.MAX_ATTEMPTS; i++) {
            assertThrows(ResponseStatusException.class, () -> access.login("nathalie13", wrong));
        }
        var locked = assertThrows(ResponseStatusException.class, () -> access.login("nathalie13", password));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.getStatusCode());

        clock.advance(SelectionAccessService.LOCK.plusSeconds(1));
        assertNotNull(access.login("nathalie13", password), "pasado el bloqueo vuelve a entrar");
    }

    @Test
    void sessionExpiresAndDiesWhenThePasswordChanges() {
        String session = access.login("nathalie13", password);
        access.assign(sel); // la secretaria restablece la contraseña
        assertThrows(ResponseStatusException.class, () -> access.resolve(session));

        String fresh = access.login("nathalie13", access.assign(sel).password());
        clock.advance(SelectionAccessService.SESSION.plusSeconds(1));
        assertThrows(ResponseStatusException.class, () -> access.resolve(fresh));
    }

    @Test
    void tamperedSessionIsRejected() {
        String session = access.login("nathalie13", password);
        assertThrows(ResponseStatusException.class, () -> access.resolve(session + "x"));
        assertThrows(ResponseStatusException.class, () -> access.resolve("abc"));
        assertThrows(ResponseStatusException.class, () -> access.resolve("tok-interno"));
    }

    @Test
    void expiredAccessCannotLogIn() {
        sel.setExpiresAt(LocalDateTime.now(clock).minusMinutes(1));
        var e = assertThrows(ResponseStatusException.class, () -> access.login("nathalie13", password));
        assertEquals(HttpStatus.GONE, e.getStatusCode());
    }

    /** Reloj que se puede adelantar. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(java.time.Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Santo_Domingo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
