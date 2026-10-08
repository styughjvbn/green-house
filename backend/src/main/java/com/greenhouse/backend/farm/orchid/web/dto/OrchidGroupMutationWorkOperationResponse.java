package com.greenhouse.backend.farm.orchid.web.dto;

public record OrchidGroupMutationWorkOperationResponse(
    Long id, String workTypeCode, String workType, String title) {}
