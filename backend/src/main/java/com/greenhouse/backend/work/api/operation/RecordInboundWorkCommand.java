package com.greenhouse.backend.work.api.operation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record RecordInboundWorkCommand(
    Long inboundRecordId,
    LocalDate workDate,
    Long varietyId,
    String varietyName,
    Integer quantity,
    String potSize,
    Map<String, Object> locationSnapshot,
    List<Long> createdOrchidGroupIds,
    String worker,
    String memo,
    Map<String, Object> details) {}
