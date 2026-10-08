package com.greenhouse.backend.sales.payment.web.dto;

import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentAllocationLine(
    @NotNull @Positive Long receiptId,
    @NotNull PaymentTargetType targetType,
    @NotNull @Positive Long targetId,
    @NotNull @Positive Long amount) {}
