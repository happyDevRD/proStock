package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.LoginRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CspReportController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, LoginRateLimitFilter.class}))
@WithMockUser
class CspReportControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void aceptaElFormatoAntiguoYElNuevo() throws Exception {
        mockMvc.perform(post("/api/public/csp-report").with(csrf()).contentType("application/csp-report")
                        .content("{\"csp-report\":{\"document-uri\":\"https://x/\",\"effective-directive\":\"script-src-elem\",\"blocked-uri\":\"https://cdn.x/a.js\"}}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/public/csp-report").with(csrf()).contentType("application/reports+json")
                        .content("[{\"type\":\"csp-violation\",\"body\":{\"documentURL\":\"https://x/\",\"effectiveDirective\":\"img-src\",\"blockedURL\":\"https://img/x.png\"}}]"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/public/csp-report").with(csrf()).contentType("application/csp-report").content("no es json"))
                .andExpect(status().isNoContent());
    }
}
