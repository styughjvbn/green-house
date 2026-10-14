package com.greenhouse.backend.farm.api.orchid.mutation;

import static com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;
import static com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupQuantityMutationCommandNormalizer.normalizeItems;

import java.time.LocalDate;
import java.util.List;

public record ConsumeOrchidGroupReservationsMutationCommand(
    OrchidGroupMutationSource source,
    List<OrchidGroupQuantityMutationItem> items,
    LocalDate effectiveBusinessDate,
    String reason)
    implements OrchidGroupMutationCommand {

  public ConsumeOrchidGroupReservationsMutationCommand {
    if (source == null || effectiveBusinessDate == null) {
      throw new IllegalArgumentException("예약 출고 Mutation의 source와 업무일이 필요합니다.");
    }
    items = normalizeItems(items, "예약 출고 Mutation");
    reason = normalizeText(reason);
  }
}
