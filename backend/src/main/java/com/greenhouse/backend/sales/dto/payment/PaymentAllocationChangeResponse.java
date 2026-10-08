package com.greenhouse.backend.sales.dto.payment;

import java.util.List;

/** Immutable command outcome; current cash and target state are queried separately. */
public record PaymentAllocationChangeResponse(
    List<Long> allocationIds, List<Long> cancellationIds) {}
