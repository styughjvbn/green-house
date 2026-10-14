package com.greenhouse.backend.sales.payment.api;

import java.math.BigDecimal;

public record PaymentAllocationTargetOption(
    Long id,
    PaymentTargetType targetType,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String sourceReference,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) Long receivableAmount,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) BigDecimal paidAmount,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) BigDecimal availableAmount,
    boolean allocationAllowed,
    boolean correctionAllowed,
    boolean reviewRequired) {}
