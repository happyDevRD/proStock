package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.JwtService;
import com.happydev.prestockbackend.security.LoginSecurityProperties;
import com.happydev.prestockbackend.service.IntegrationCredentialService;
import com.happydev.prestockbackend.service.IntegrationCredentialService.CredentialEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Excluimos JwtAuthenticationFilter del component scan para que no intente
 * registrarse como filtro, pero lo dejamos como @MockitoBean para que
 * SecurityConfig pueda construirse (y @EnableMethodSecurity sea efectivo).
 */
@WebMvcTest(
        controllers = IntegrationCredentialController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(LoginSecurityProperties.class)
class IntegrationCredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntegrationCredentialService service;

    // Necesarios para que SecurityConfig pueda construirse
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returnsCredentialsForProvider() throws Exception {
        given(service.listByProvider("whatsapp")).willReturn(List.of(
                new CredentialEntry("whatsapp", "access_token", "tok123", LocalDateTime.now())
        ));

        mockMvc.perform(get("/api/integrations/whatsapp/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("access_token"))
                .andExpect(jsonPath("$[0].value").value("tok123"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void set_upsertCredential_returns204() throws Exception {
        mockMvc.perform(put("/api/integrations/whatsapp/credentials/access_token")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"my-token"}
                                """))
                .andExpect(status().isNoContent());

        verify(service).set("whatsapp", "access_token", "my-token");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_removesCredential_returns204() throws Exception {
        mockMvc.perform(delete("/api/integrations/whatsapp/credentials/access_token")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).delete("whatsapp", "access_token");
    }

}
