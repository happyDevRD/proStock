package com.happydev.prestockbackend.security;

import com.happydev.prestockbackend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyServiceTest {

    private PasswordPolicyProperties props;
    private PasswordPolicyService service;

    @BeforeEach
    void setUp() {
        props = new PasswordPolicyProperties();
        service = new PasswordPolicyService(props);
    }

    @Test
    void validate_defaultPolicy_acceptsAnyEightCharPassword() {
        assertDoesNotThrow(() -> service.validate("abcdefgh"));
    }

    @Test
    void validate_tooShort_throws() {
        props.setMinLength(10);
        PasswordPolicyViolationException ex = assertThrows(
                PasswordPolicyViolationException.class, () -> service.validate("short"));
        assertTrue(ex.getMessage().contains("10 caracteres"));
    }

    @Test
    void validate_requireUppercase_throwsIfMissing() {
        props.setRequireUppercase(true);
        assertThrows(PasswordPolicyViolationException.class, () -> service.validate("alllower1"));
        assertDoesNotThrow(() -> service.validate("HasUpper1"));
    }

    @Test
    void validate_requireDigit_throwsIfMissing() {
        props.setRequireDigit(true);
        assertThrows(PasswordPolicyViolationException.class, () -> service.validate("NoDigitsHere"));
        assertDoesNotThrow(() -> service.validate("HasDigit1"));
    }

    @Test
    void validate_requireSpecial_throwsIfMissing() {
        props.setRequireSpecial(true);
        assertThrows(PasswordPolicyViolationException.class, () -> service.validate("NoSpecial1A"));
        assertDoesNotThrow(() -> service.validate("Has!Special1"));
    }

    @Test
    void isExpired_noMaxAge_returnsFalse() {
        props.setMaxAgeDays(0);
        User user = new User();
        user.setPasswordChangedAt(LocalDateTime.now().minusYears(10));
        assertFalse(service.isExpired(user));
    }

    @Test
    void isExpired_recentChange_returnsFalse() {
        props.setMaxAgeDays(90);
        User user = new User();
        user.setPasswordChangedAt(LocalDateTime.now().minusDays(30));
        assertFalse(service.isExpired(user));
    }

    @Test
    void isExpired_oldChange_returnsTrue() {
        props.setMaxAgeDays(90);
        User user = new User();
        user.setPasswordChangedAt(LocalDateTime.now().minusDays(91));
        assertTrue(service.isExpired(user));
    }

    @Test
    void isExpired_neverChanged_returnsTrue() {
        props.setMaxAgeDays(90);
        User user = new User();
        user.setPasswordChangedAt(null);
        assertTrue(service.isExpired(user));
    }
}
