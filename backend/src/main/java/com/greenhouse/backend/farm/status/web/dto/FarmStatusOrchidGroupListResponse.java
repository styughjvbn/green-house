package com.greenhouse.backend.farm.status.web.dto;

import com.greenhouse.backend.farm.status.domain.FarmStatusTargetType;
import java.util.List;

public record FarmStatusOrchidGroupListResponse(
    FarmStatusTargetType targetType,
    Long targetId,
    String targetName,
    List<FarmStatusOrchidGroupItemResponse> items) {}
