package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.requireText;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record CompensateTransformMutationsCommand(
    OrchidGroupMutationSource source,
    List<Long> mutationIds,
    LocalDate effectiveBusinessDate,
    String reason,
    Set<Long> creationCancellationOrchidGroupIds)
    implements OrchidGroupMutationCommand {

  public CompensateTransformMutationsCommand(
      OrchidGroupMutationSource source,
      List<Long> mutationIds,
      LocalDate effectiveBusinessDate,
      String reason) {
    this(source, mutationIds, effectiveBusinessDate, reason, Set.of());
  }

  public CompensateTransformMutationsCommand {
    if (source == null
        || effectiveBusinessDate == null
        || mutationIds == null
        || mutationIds.isEmpty()
        || mutationIds.stream().anyMatch(id -> id == null)) {
      throw new IllegalArgumentException("보상할 구조 변경 Mutation과 업무일이 필요합니다.");
    }
    mutationIds = mutationIds.stream().distinct().sorted().toList();
    creationCancellationOrchidGroupIds = Set.copyOf(creationCancellationOrchidGroupIds);
    reason = requireText(reason, "작업 취소 사유");
  }
}
