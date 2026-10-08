package com.greenhouse.backend.farm.orchid.web.dto;

public record OrchidManagementSummaryResponse(
    long orchidGroupCount, long totalQuantity, long abnormalCount, long bedZoneCount) {}
