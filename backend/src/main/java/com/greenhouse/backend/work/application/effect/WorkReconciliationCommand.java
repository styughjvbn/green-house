package com.greenhouse.backend.work.application.effect;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Retains the original request fields and text for compatibility with immediate-work receipts. */
public record WorkReconciliationCommand(
    String idempotencyKey,
    String title,
    LocalDate workDate,
    String worker,
    String memo,
    String reason,
    Integer actualQuantity,
    String actualStatus,
    Long actualBedZoneId,
    BigDecimal actualStartPosition,
    BigDecimal actualEndPosition)
    implements WorkEffectPayload {}
