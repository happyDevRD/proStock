package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.AuthCookieSupport;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.JwtService;
import com.happydev.prestockbackend.security.LoginSecurityProperties;
import com.happydev.prestockbackend.security.TotpProperties;
import com.happydev.prestockbackend.security.TotpService;
import com.happydev.prestockbackend.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({LoginSecurityProperties.class, TotpProperties.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthCookieSupport authCookieSupport;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private TotpService totpService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("admin");
        user.setEmail("admin@prostock.local");
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setRole(UserRole.ADMIN);

        var authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                "secret",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(jwtService.generateToken(any())).willReturn("signed-jwt");
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(user));
        given(permissionService.getEffectivePermissions(any())).willReturn(List.of("view.dashboard", "view.users"));
    }

    @Test
    void login_validCredentials_setsCookieAndReturnsProfile() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.permissions[0]").value("view.dashboard"))
                .andExpect(jsonPath("$.permissions[1]").value("view.users"))
                .andExpect(jsonPath("$.accessToken").value("signed-jwt"));

        verify(authCookieSupport).writeAccessToken(any(), org.mockito.ArgumentMatchers.eq("signed-jwt"));
    }

    @Test
    void logout_clearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());

        verify(authCookieSupport).clearAccessToken(any());
    }

    @Test
    void login_badCredentials_returns401AndRegistersFailedAttempt() throws Exception {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, captor.getValue().getFailedLoginAttempts());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getLockedUntil());
    }

    @Test
    void login_accountLocked_returns423WithoutAuthenticating() throws Exception {
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234"}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_validCredentials_resetsPreviousFailedAttempts() throws Exception {
        user.setFailedLoginAttempts(3);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234"}
                                """))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, captor.getValue().getFailedLoginAttempts());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getLockedUntil());
    }

    @Test
    void login_totpEnabledWithoutCode_returns401TotpRequired() throws Exception {
        user.setTotpEnabled(true);
        user.setTotpSecret("encrypted-secret");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_REQUIRED"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_totpEnabledWithInvalidCode_returns401TotpInvalidAndRegistersFailedAttempt() throws Exception {
        user.setTotpEnabled(true);
        user.setTotpSecret("encrypted-secret");
        given(totpService.decrypt("encrypted-secret")).willReturn("plain-secret");
        given(totpService.verifyCode("plain-secret", "000000")).willReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234","totpCode":"000000"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, captor.getValue().getFailedLoginAttempts());
    }

    @Test
    void login_totpEnabledWithValidCode_returnsProfile() throws Exception {
        user.setTotpEnabled(true);
        user.setTotpSecret("encrypted-secret");
        given(totpService.decrypt("encrypted-secret")).willReturn("plain-secret");
        given(totpService.verifyCode("plain-secret", "123456")).willReturn(true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234","totpCode":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.accessToken").value("signed-jwt"))
                .andExpect(jsonPath("$.totpSetupRequired").value(false));
    }
}
