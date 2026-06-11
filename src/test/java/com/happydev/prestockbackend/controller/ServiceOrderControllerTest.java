package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.CreateServiceOrderRequest;
import com.happydev.prestockbackend.dto.ServiceOrderDto;
import com.happydev.prestockbackend.dto.ServiceOrderReportDto;
import com.happydev.prestockbackend.dto.ServiceOrderStatsDto;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.service.ServiceOrderAccessGuard;
import com.happydev.prestockbackend.service.ServiceOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ServiceOrderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class ServiceOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ServiceOrderService serviceOrderService;

    @MockitoBean
    private ServiceOrderAccessGuard accessGuard;

    @Test
    void getStats_ReturnsAggregates() throws Exception {
        doNothing().when(accessGuard).requireEnabled();
        given(serviceOrderService.getStats()).willReturn(new ServiceOrderStatsDto(8, 2, 3, 5));

        mockMvc.perform(get("/api/service-orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(8)))
                .andExpect(jsonPath("$.waitingClient", is(2)))
                .andExpect(jsonPath("$.ready", is(3)))
                .andExpect(jsonPath("$.completedThisMonth", is(5)));

        verify(accessGuard).requireEnabled();
    }

    @Test
    void getReport_ReturnsPeriodMetrics() throws Exception {
        doNothing().when(accessGuard).requireEnabled();
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        given(serviceOrderService.getReport(start, end)).willReturn(new ServiceOrderReportDto(
                start,
                end,
                10,
                6,
                1,
                4,
                new BigDecimal("25000.00"),
                3.5,
                Collections.emptyList()
        ));

        mockMvc.perform(get("/api/service-orders/report")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(10)))
                .andExpect(jsonPath("$.completed", is(6)))
                .andExpect(jsonPath("$.linkedSalesRevenue", is(25000.00)));
    }

    @Test
    void getPage_ReturnsPaginatedList() throws Exception {
        doNothing().when(accessGuard).requireEnabled();
        ServiceOrderDto dto = sampleDto();
        given(serviceOrderService.findPage(any(), any(), any(), eq(false), any()))
                .willReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/service-orders/paginated?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber", is("OS-2026-00001")));
    }

    @Test
    void create_ValidBody_Returns201() throws Exception {
        doNothing().when(accessGuard).requireEnabled();
        ServiceOrderDto created = sampleDto();
        given(serviceOrderService.create(any(CreateServiceOrderRequest.class), nullable(String.class)))
                .willReturn(created);

        CreateServiceOrderRequest request = new CreateServiceOrderRequest(
                null,
                ServiceOrderType.GENERAL,
                "Servicio general",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Servicio general")));
    }

    private ServiceOrderDto sampleDto() {
        return new ServiceOrderDto(
                1L,
                "OS-2026-00001",
                null,
                null,
                ServiceOrderType.GENERAL,
                "Servicio general",
                ServiceOrderStatus.OPEN,
                "RECEIVED",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null
        );
    }
}
