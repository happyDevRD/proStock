package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.NcfSequence;
import com.happydev.prestockbackend.service.SequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NcfSequenceController.class)
@WithMockUser(username = "admin", roles = {"ADMIN"})
@SuppressWarnings("null")
class NcfSequenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SequenceService sequenceService;

    @Autowired
    private ObjectMapper objectMapper;

    private NcfSequence ncfSequence;

    @BeforeEach
    void setUp() {
        ncfSequence = new NcfSequence();
        ncfSequence.setId(1L);
        ncfSequence.setTipoComprobante("31");
        ncfSequence.setPrefijo("E");
        ncfSequence.setValorActual(100L);
        ncfSequence.setValorFinal(9999999999L);
        ncfSequence.setFechaVencimiento(LocalDate.now().plusMonths(6));
    }

    @Test
    void getSequence_WhenExists_ReturnsSequence() throws Exception {
        given(sequenceService.findByTipoComprobante("31")).willReturn(Optional.of(ncfSequence));

        mockMvc.perform(get("/api/ncf-sequences/31")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoComprobante", is("31")))
                .andExpect(jsonPath("$.prefijo", is("E")));
    }

    @Test
    void getSequence_WhenNotExists_ReturnsNotFound() throws Exception {
        given(sequenceService.findByTipoComprobante("31")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/ncf-sequences/31")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveOrUpdate_ValidData_ReturnsOk() throws Exception {
        given(sequenceService.saveOrUpdate(any(NcfSequence.class))).willReturn(ncfSequence);

        mockMvc.perform(put("/api/ncf-sequences")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ncfSequence)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorActual", is(100)));
    }

    @Test
    void getNextNcf_ReturnsFormattedNcf() throws Exception {
        given(sequenceService.getNextSequence("31")).willReturn("E310000000101");

        mockMvc.perform(post("/api/ncf-sequences/31/next")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ncf", is("E310000000101")));
    }
}
