package com.greenhouse.backend.farm.api.orchid.mutation;

import static com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationCommandNormalizer.requireText;

import java.time.LocalDate;
import java.util.List;

public record CompensateCreateMutationsCommand(
    OrchidGroupMutationSource source,
    List<Long> mutationIds,
    LocalDate effectiveBusinessDate,
    String reason)
    implements OrchidGroupMutationCommand {

  public CompensateCreateMutationsCommand {
    if (source == null
        || effectiveBusinessDate == null
        || mutationIds == null
        || mutationIds.isEmpty()
        || mutationIds.stream().anyMatch(id -> id == null)) {
      throw new IllegalArgumentException("보상할 생성 Mutation과 업무일이 필요합니다.");
    }
    mutationIds = mutationIds.stream().distinct().sorted().toList();
    reason = requireText(reason, "작업 취소 사유");
  }
}
