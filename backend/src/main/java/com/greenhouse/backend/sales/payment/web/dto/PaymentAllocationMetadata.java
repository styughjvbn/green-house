package com.greenhouse.backend.sales.payment.web.dto;

import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import java.util.List;

public record PaymentAllocationMetadata(List<PaymentTargetType> targetTypes) {}
