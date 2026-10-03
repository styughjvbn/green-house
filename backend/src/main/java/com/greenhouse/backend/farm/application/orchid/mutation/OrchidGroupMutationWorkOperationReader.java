package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationWorkOperationResponse;
import com.greenhouse.backend.work.application.operation.WorkOperationMetadataReader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrchidGroupMutationWorkOperationReader {

  private static final String WORK_EFFECT = "WORK_EFFECT";

  private final WorkOperationMetadataReader workOperationMetadataReader;

  Map<Long, OrchidGroupMutationWorkOperationResponse> resolveByMutationId(
      Collection<OrchidGroupMutation> mutations) {
    Map<Long, Long> workOperationIdByMutationId = new LinkedHashMap<>();
    Set<Long> workOperationIds = new LinkedHashSet<>();
    for (OrchidGroupMutation mutation : mutations) {
      Long workOperationId = workOperationId(mutation);
      if (workOperationId != null) {
        workOperationIdByMutationId.put(mutation.getId(), workOperationId);
        workOperationIds.add(workOperationId);
      }
    }

    Map<Long, OrchidGroupMutationWorkOperationResponse> workOperationsById = new LinkedHashMap<>();
    (workOperationIds.isEmpty()
            ? List.<WorkOperationMetadataReader.WorkOperationMetadata>of()
            : workOperationMetadataReader.findByIds(workOperationIds))
        .forEach(
            workOperation ->
                workOperationsById.put(
                    workOperation.id(),
                    new OrchidGroupMutationWorkOperationResponse(
                        workOperation.id(),
                        workOperation.workTypeCode(),
                        workOperation.workType(),
                        workOperation.title())));

    Map<Long, OrchidGroupMutationWorkOperationResponse> result = new LinkedHashMap<>();
    workOperationIdByMutationId.forEach(
        (mutationId, workOperationId) -> {
          var workOperation = workOperationsById.get(workOperationId);
          if (workOperation != null) {
            result.put(mutationId, workOperation);
          }
        });
    var correctionMutationIds =
        mutations.stream()
            .filter(
                mutation ->
                    mutation.getSourceDomain() == OrchidGroupMutationSourceDomain.WORK
                        && "WORK_CORRECTION".equals(mutation.getSourceType()))
            .map(OrchidGroupMutation::getId)
            .toList();
    workOperationMetadataReader
        .findOriginalsByCorrectionMutationIds(correctionMutationIds)
        .forEach(
            (mutationId, operation) ->
                result.put(
                    mutationId,
                    new OrchidGroupMutationWorkOperationResponse(
                        operation.id(),
                        operation.workTypeCode(),
                        operation.workType(),
                        operation.title())));
    return result;
  }

  private Long workOperationId(OrchidGroupMutation mutation) {
    if (mutation.getSourceDomain() != OrchidGroupMutationSourceDomain.WORK
        || !WORK_EFFECT.equals(mutation.getSourceType())) {
      return null;
    }
    try {
      return Long.valueOf(mutation.getSourceReferenceId());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
