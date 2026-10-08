package com.greenhouse.backend.work.correction.repository;

import java.util.Map;
import java.util.UUID;

public record WorkCorrectionReconciliationRow(
    Long id, Long mutationId, UUID correlationId, Map<String, Object> resultDetails) {}
