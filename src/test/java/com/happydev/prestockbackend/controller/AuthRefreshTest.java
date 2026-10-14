package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRefreshTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void refreshWithoutSession_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithValidSession_issuesNewTokenWithRoleFromDatabase() throws Exception {
        // El token antiguo solo trae una autoridad; el renovado debe reconstruir rol y permisos desde la BD
        String oldToken = jwtService.generateToken(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        String body = mockMvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn().getResponse().getContentAsString();

        String newToken = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        assertThat(jwtService.parseToken(newToken))
                .hasValueSatisfying(p -> assertThat(p.authorities()).contains("ROLE_ADMIN"));
    }
}
