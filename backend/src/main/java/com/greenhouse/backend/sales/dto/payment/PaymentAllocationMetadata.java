package com.greenhouse.backend.sales.dto.payment;

import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import java.util.List;

public record PaymentAllocationMetadata(List<PaymentTargetType> targetTypes) {}
