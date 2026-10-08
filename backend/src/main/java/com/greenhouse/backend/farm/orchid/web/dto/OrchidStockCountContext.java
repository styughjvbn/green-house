package com.greenhouse.backend.farm.orchid.web.dto;

import java.time.LocalDate;

public record OrchidStockCountContext(
    Long orchidGroupId,
    int quantity,
    Long stateRevision,
    LocalDate businessDate,
    boolean adjustable) {}
