package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record TopCustomerDto(Long customerId, String customerName, BigDecimal revenue, long count) {}
