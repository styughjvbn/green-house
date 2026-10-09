package com.greenhouse.backend.farm.api.orchid.mutation;

import static com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import java.time.LocalDate;

public record CancelOrchidGroupCreationMutationCommand(
    OrchidGroupMutationSource source,
    Long orchidGroupId,
    RelatedOrchidGroupMutations correctedMutations,
    LocalDate effectiveBusinessDate,
    String reason)
    implements OrchidGroupMutationCommand {

  public CancelOrchidGroupCreationMutationCommand(
      OrchidGroupMutationSource source,
      Long orchidGroupId,
      LocalDate effectiveBusinessDate,
      String reason) {
    this(source, orchidGroupId, null, effectiveBusinessDate, reason);
  }

  public CancelOrchidGroupCreationMutationCommand {
    if (source == null || orchidGroupId == null || effectiveBusinessDate == null) {
      throw new IllegalArgumentException("생성 취소 Mutation의 source, 난 묶음과 업무일이 필요합니다.");
    }
    reason = normalizeText(reason);
  }
}
