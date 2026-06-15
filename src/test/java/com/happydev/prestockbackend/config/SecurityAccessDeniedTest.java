package com.happydev.prestockbackend.config;

import com.happydev.prestockbackend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que las respuestas de autenticación/autorización usen los códigos HTTP correctos:
 * 401 cuando no hay sesión, 403 cuando hay sesión pero faltan permisos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessDeniedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String tokenFor(String... authorities) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "someuser",
                null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
        return jwtService.generateToken(authentication);
    }

    @Test
    void anonymousRequestToProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedWithoutRequiredAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor("ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void preAuthorizeDenial_returns403WithErrorBody() throws Exception {
        mockMvc.perform(post("/api/accounting/accounts")
                        .header("Authorization", "Bearer " + tokenFor("ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
