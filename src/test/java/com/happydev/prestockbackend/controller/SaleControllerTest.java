package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.AddPaymentRequest;
import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SaleItemDto;
import com.happydev.prestockbackend.dto.SalePaymentDto;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.LoginRateLimitFilter;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SaleController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, LoginRateLimitFilter.class}
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

    @Test
    void addPayment_ValidBody_Returns201() throws Exception {
        AddPaymentRequest request = new AddPaymentRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setNotes("Abono inicial");

        SalePaymentDto responseDto = new SalePaymentDto();
        responseDto.setId(501L);
        responseDto.setSaleId(99L);
        responseDto.setAmount(new BigDecimal("100.00"));
        responseDto.setPaymentMethod(PaymentMethod.CASH);
        responseDto.setNotes("Abono inicial");

        given(saleService.addPayment(eq(99L), eq(new BigDecimal("100.00")), eq(PaymentMethod.CASH),
                eq("Abono inicial"), nullable(String.class)))
                .willReturn(responseDto);

        mockMvc.perform(post("/api/sales/99/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(501)))
                .andExpect(jsonPath("$.amount", is(100.00)))
                .andExpect(jsonPath("$.paymentMethod", is("CASH")));
    }

    @Test
    void addPayment_ExceedsPendingBalance_Returns400WithFriendlyMessage() throws Exception {
        AddPaymentRequest request = new AddPaymentRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setPaymentMethod(PaymentMethod.CASH);

        given(saleService.addPayment(eq(99L), eq(new BigDecimal("100.00")), eq(PaymentMethod.CASH),
                any(), nullable(String.class)))
                .willThrow(new IllegalArgumentException("El monto del abono (100.00) excede el saldo pendiente de la venta (86.00)."));

        mockMvc.perform(post("/api/sales/99/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ILLEGAL_ARGUMENT")))
                .andExpect(jsonPath("$.message", is("El monto del abono (100.00) excede el saldo pendiente de la venta (86.00).")));
    }

    @Test
    void addPayment_AmountIsZero_Returns400() throws Exception {
        AddPaymentRequest request = new AddPaymentRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setPaymentMethod(PaymentMethod.CASH);

        mockMvc.perform(post("/api/sales/99/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(saleService, never()).addPayment(any(), any(), any(), any(), any());
    }

    @Test
    void addPayment_MissingPaymentMethod_Returns400() throws Exception {
        AddPaymentRequest request = new AddPaymentRequest();
        request.setAmount(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/sales/99/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(saleService, never()).addPayment(any(), any(), any(), any(), any());
    }

    @Test
    void getPayments_ReturnsPaymentList() throws Exception {
        SalePaymentDto payment = new SalePaymentDto();
        payment.setId(1L);
        payment.setSaleId(99L);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        given(saleService.getPaymentsForSale(99L)).willReturn(List.of(payment));

        mockMvc.perform(get("/api/sales/99/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].amount", is(100.00)))
                .andExpect(jsonPath("$[0].paymentMethod", is("CASH")));
    }

    @Test
    void voidPayment_ValidIds_ReturnsUpdatedSale() throws Exception {
        SaleDto updatedSale = new SaleDto();
        updatedSale.setId(99L);
        updatedSale.setStatus(SaleStatus.PARTIALLY_PAID);
        updatedSale.setPaidAmount(new BigDecimal("186.00"));

        given(saleService.voidPayment(eq(99L), eq(501L), nullable(String.class))).willReturn(updatedSale);

        mockMvc.perform(delete("/api/sales/99/payments/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(99)))
                .andExpect(jsonPath("$.status", is("PARTIALLY_PAID")))
                .andExpect(jsonPath("$.paidAmount", is(186.00)));
    }

    @Test
    void voidPayment_SaleCompleted_Returns400WithFriendlyMessage() throws Exception {
        given(saleService.voidPayment(eq(99L), eq(501L), nullable(String.class)))
                .willThrow(new IllegalStateException("No se puede anular un abono de una venta ya completada (con NCF asignado)."));

        mockMvc.perform(delete("/api/sales/99/payments/501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ILLEGAL_STATE")))
                .andExpect(jsonPath("$.message", is("No se puede anular un abono de una venta ya completada (con NCF asignado).")));
    }
}
