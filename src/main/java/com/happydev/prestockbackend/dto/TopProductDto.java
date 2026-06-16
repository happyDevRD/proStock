package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record TopProductDto(Long productId, String productName, BigDecimal revenue, long quantity) {}
