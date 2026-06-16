package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.LoginRateLimitFilter;
import com.happydev.prestockbackend.security.TotpProperties;
import com.happydev.prestockbackend.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TwoFactorController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, LoginRateLimitFilter.class}
        )
)
@WithMockUser(username = "admin", roles = {"ADMIN"})
@EnableConfigurationProperties(TotpProperties.class)
class TwoFactorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TotpService totpService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("admin");
        user.setEmail("admin@prostock.local");
        user.setRole(UserRole.ADMIN);
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(user));
    }

    @Test
    void status_returnsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/api/auth/2fa/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.enforced").value(false));
    }

    @Test
    void setup_generatesAndPersistsEncryptedSecret() throws Exception {
        given(totpService.generateSecret()).willReturn("plain-secret");
        given(totpService.encrypt("plain-secret")).willReturn("encrypted-secret");
        given(totpService.buildQrDataUri("admin", "plain-secret")).willReturn("data:image/png;base64,abc");

        mockMvc.perform(post("/api/auth/2fa/setup")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("plain-secret"))
                .andExpect(jsonPath("$.qrCodeDataUri").value("data:image/png;base64,abc"));

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("encrypted-secret", captor.getValue().getTotpSecret());
        org.junit.jupiter.api.Assertions.assertFalse(captor.getValue().isTotpEnabled());
    }

    @Test
    void enable_withValidCode_enablesTotp() throws Exception {
        user.setTotpSecret("encrypted-secret");
        given(totpService.decrypt("encrypted-secret")).willReturn("plain-secret");
        given(totpService.verifyCode("plain-secret", "123456")).willReturn(true);

        mockMvc.perform(post("/api/auth/2fa/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .content("""
                                {"code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().isTotpEnabled());
    }

    @Test
    void enable_withInvalidCode_returns401TotpInvalid() throws Exception {
        user.setTotpSecret("encrypted-secret");
        given(totpService.decrypt("encrypted-secret")).willReturn("plain-secret");
        given(totpService.verifyCode("plain-secret", "000000")).willReturn(false);

        mockMvc.perform(post("/api/auth/2fa/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .content("""
                                {"code":"000000"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void enable_withoutPendingSecret_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/2fa/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .content("""
                                {"code":"123456"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disable_withValidCode_disablesAndClearsSecret() throws Exception {
        user.setTotpSecret("encrypted-secret");
        user.setTotpEnabled(true);
        given(totpService.decrypt("encrypted-secret")).willReturn("plain-secret");
        given(totpService.verifyCode("plain-secret", "123456")).willReturn(true);

        mockMvc.perform(post("/api/auth/2fa/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .content("""
                                {"code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertFalse(captor.getValue().isTotpEnabled());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getTotpSecret());
    }

    @Test
    void disable_whenNotEnabled_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/2fa/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .content("""
                                {"code":"123456"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
