package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SaleItemDto;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SaleController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SaleService saleService;

    @Autowired
    private ObjectMapper objectMapper;

    private SaleDto requestDto;
    private SaleDto savedDto;

    @BeforeEach
    void setUp() {
        SaleItemDto item = new SaleItemDto();
        item.setProductId(1L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10.50"));

        requestDto = new SaleDto();
        requestDto.setSaleDate(LocalDateTime.of(2026, 5, 7, 12, 0));
        requestDto.setStatus(SaleStatus.PENDING);
        requestDto.setItems(List.of(item));

        savedDto = new SaleDto();
        savedDto.setId(99L);
        savedDto.setSaleDate(requestDto.getSaleDate());
        savedDto.setStatus(SaleStatus.PENDING);
        savedDto.setItems(requestDto.getItems());
    }

    @Test
    void createSale_ValidBody_Returns201() throws Exception {
        given(saleService.createSale(any(SaleDto.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willReturn(savedDto);

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(99)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }
}
