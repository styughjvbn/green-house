package com.greenhouse.backend.farm.mutation.ledger.repository;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationType;
import java.util.UUID;

public record CorrectionMutationReconciliationRow(
    Long id,
    UUID correlationId,
    OrchidGroupMutationSourceDomain sourceDomain,
    OrchidGroupMutationType mutationType,
    String sourceType,
    String sourceReferenceId) {}
