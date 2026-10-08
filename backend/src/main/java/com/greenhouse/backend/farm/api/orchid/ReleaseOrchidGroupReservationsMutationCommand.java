package com.greenhouse.backend.farm.api.orchid;

import static com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationCommandNormalizer.normalizeText;
import static com.greenhouse.backend.farm.api.orchid.OrchidGroupQuantityMutationCommandNormalizer.normalizeItems;

import java.time.LocalDate;
import java.util.List;

public record ReleaseOrchidGroupReservationsMutationCommand(
    OrchidGroupMutationSource source,
    List<OrchidGroupQuantityMutationItem> items,
    LocalDate effectiveBusinessDate,
    String reason)
    implements OrchidGroupMutationCommand {

  public ReleaseOrchidGroupReservationsMutationCommand {
    if (source == null || effectiveBusinessDate == null) {
      throw new IllegalArgumentException("예약 해제 Mutation의 source와 업무일이 필요합니다.");
    }
    items = normalizeItems(items, "예약 해제 Mutation");
    reason = normalizeText(reason);
  }
}
