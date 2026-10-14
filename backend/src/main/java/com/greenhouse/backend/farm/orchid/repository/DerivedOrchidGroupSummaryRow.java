package com.greenhouse.backend.farm.orchid.repository;

import com.greenhouse.backend.farm.orchid.domain.PotSizeCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DerivedOrchidGroupSummaryRow(
    Long varietyId,
    String varietyName,
    String genus,
    Integer baseAgeYear,
    LocalDate inboundDate,
    LocalDateTime createdAt,
    PotSizeCode potSizeCode,
    String potSize,
    Integer quantity,
    Long bedZoneId) {}
