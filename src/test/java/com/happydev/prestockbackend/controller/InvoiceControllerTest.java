package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.exception.GlobalExceptionHandler;
import com.happydev.prestockbackend.service.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SaleService saleService;

    @Test
    void listInvoices_withDateFilters_returnsPage() throws Exception {
        SaleDto sale = new SaleDto();
        sale.setId(10L);
        sale.setSaleDate(LocalDateTime.of(2026, 5, 7, 12, 0));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNcf("B0100000001");
        sale.setMontoTotal(new BigDecimal("118.00"));
        sale.setItems(List.of());

        Page<SaleDto> page = new PageImpl<>(List.of(sale), PageRequest.of(0, 15), 1);
        given(saleService.findInvoices(
                any(Pageable.class),
                isNull(),
                isNull(),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalDate.of(2026, 5, 7))
        )).willReturn(page);

        mockMvc.perform(get("/api/invoices")
                        .param("page", "0")
                        .param("size", "15")
                        .param("sort", "saleDate,desc")
                        .param("dateFrom", "2026-05-01")
                        .param("dateTo", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ncf", is("B0100000001")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void getByNcf_whenMissing_returnsNotFound() throws Exception {
        given(saleService.findSaleByNcf("B0199999999")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/invoices/by-ncf/B0199999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByNcf_whenExists_returnsSale() throws Exception {
        SaleDto sale = new SaleDto();
        sale.setId(3L);
        sale.setSaleDate(LocalDateTime.now());
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNcf("B0100000099");
        sale.setItems(List.of());
        given(saleService.findSaleByNcf("B0100000099")).willReturn(Optional.of(sale));

        mockMvc.perform(get("/api/invoices/by-ncf/B0100000099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ncf", is("B0100000099")));
    }
}
