package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SelectionDtos.PublicSelection;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.LoginRateLimitFilter;
import com.happydev.prestockbackend.service.PreviewImageService;
import com.happydev.prestockbackend.service.SelectionAccessService;
import com.happydev.prestockbackend.service.SelectionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PublicSelectionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, LoginRateLimitFilter.class}
        )
)
@WithMockUser
class PublicSelectionControllerTest {

    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) Safari/604.1";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SelectionService selectionService;
    @MockitoBean private PreviewImageService imageService;
    @MockitoBean private SelectionAccessService access;

    @Test
    void elEnlaceDirectoDejaLaSesionEnUnaCookieSeguraYNoEnLaRespuestaVisible() throws Exception {
        given(access.loginWithKey("k_7_firma", IPHONE)).willReturn(new SelectionAccessService.Login("SES.firma", "ana7"));

        mockMvc.perform(post("/api/public/selection/acceso").with(csrf())
                        .header("User-Agent", IPHONE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"k_7_firma\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana7"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("sel_session=SES.firma"),
                        containsString("Path=/api/seleccion"),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"))));
    }

    @Test
    void conLaCookieSeVeLaSeleccionSinSesionEnLaRuta() throws Exception {
        given(access.resolve("SES.firma")).willReturn("tok-interno");
        given(selectionService.view("tok-interno")).willReturn(mock(PublicSelection.class));

        mockMvc.perform(get("/api/public/selection/me").cookie(new Cookie("sel_session", "SES.firma")))
                .andExpect(status().isOk());
        verify(selectionService).view("tok-interno");
    }

    @Test
    void sinCookieRespondeNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/public/selection/me")).andExpect(status().isUnauthorized());
        verify(access, never()).resolve(anyString());
    }

    @Test
    void conContrasenaTambienDejaLaCookieYRegistraElDispositivo() throws Exception {
        given(access.login(eq("ana7"), eq("123456"), eq(IPHONE)))
                .willReturn(new SelectionAccessService.Login("SES.firma", "ana7"));

        mockMvc.perform(post("/api/public/selection/login").with(csrf())
                        .header("User-Agent", IPHONE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana7\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("sel_session=SES.firma")));
    }

    @Test
    void salirBorraLaCookie() throws Exception {
        mockMvc.perform(post("/api/public/selection/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", allOf(containsString("sel_session="), containsString("Max-Age=0"))));
        verify(access, never()).loginWithKey(any(), any());
    }
}
