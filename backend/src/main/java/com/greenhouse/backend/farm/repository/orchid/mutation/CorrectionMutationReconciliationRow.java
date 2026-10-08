package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import java.util.UUID;

public record CorrectionMutationReconciliationRow(
    Long id,
    UUID correlationId,
    OrchidGroupMutationSourceDomain sourceDomain,
    OrchidGroupMutationType mutationType,
    String sourceType,
    String sourceReferenceId) {}
